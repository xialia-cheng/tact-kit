package com.xialia.tactkit.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaInfo(
  val durationMs: Long,
  val hasVideo: Boolean,
  val hasAudio: Boolean,
  val videoMime: String?,
  val audioMime: String?,
)

object MediaEngine {

  private const val BUFFER_CAPACITY = 1024 * 1024

  suspend fun inspectMedia(context: Context, uri: Uri): MediaInfo =
    withContext(Dispatchers.IO) {
      val retriever = MediaMetadataRetriever()
      var durationMs = 0L
      try {
        retriever.setDataSource(context, uri)
        durationMs =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
      } finally {
        runCatching { retriever.release() }
      }

      val extractor = MediaExtractor()
      var videoMime: String? = null
      var audioMime: String? = null
      try {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
          if (mime.startsWith("video/") && videoMime == null) videoMime = mime
          if (mime.startsWith("audio/") && audioMime == null) audioMime = mime
        }
      } finally {
        extractor.release()
      }

      MediaInfo(
        durationMs = durationMs,
        hasVideo = videoMime != null,
        hasAudio = audioMime != null,
        videoMime = videoMime,
        audioMime = audioMime,
      )
    }

  suspend fun extractAudioLossless(context: Context, inputUri: Uri, outputUri: Uri) =
    withContext(Dispatchers.IO) {
      val extractor = MediaExtractor()
      var muxer: MediaMuxer? = null
      try {
        extractor.setDataSource(context, inputUri, null)
        var audioTrackIdx = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
          if (mime.startsWith("audio/")) {
            audioTrackIdx = i
            audioFormat = format
            break
          }
        }
        require(audioTrackIdx >= 0 && audioFormat != null) { "当前媒体文件不包含音轨" }

        val pfd =
          context.contentResolver.openFileDescriptor(outputUri, "rw")
            ?: error("无法创建输出音频文件")
        pfd.use { fd ->
          muxer = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
          val dstTrack = muxer!!.addTrack(audioFormat)
          muxer!!.start()

          extractor.selectTrack(audioTrackIdx)
          val maxBuffer =
            if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
              audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(BUFFER_CAPACITY)
            } else {
              BUFFER_CAPACITY
            }
          val buffer = ByteBuffer.allocate(maxBuffer)
          val info = MediaCodec.BufferInfo()
          var firstPtsUs = -1L

          while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            val sampleTime = extractor.sampleTime
            if (firstPtsUs < 0) firstPtsUs = sampleTime
            info.presentationTimeUs = (sampleTime - firstPtsUs).coerceAtLeast(0L)
            info.flags = mapExtractorFlagsToCodecFlags(extractor.sampleFlags)
            muxer!!.writeSampleData(dstTrack, buffer, info)
            extractor.advance()
          }
        }
      } finally {
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        extractor.release()
      }
    }

  suspend fun trimVideoLossless(
    context: Context,
    inputUri: Uri,
    startMs: Long,
    endMs: Long,
    outputUri: Uri,
  ) =
    withContext(Dispatchers.IO) {
      require(endMs > startMs) { "结束时间必须大于开始时间" }
      val startUs = startMs.coerceAtLeast(0L) * 1000L
      val endUs = endMs * 1000L

      val extractor = MediaExtractor()
      var muxer: MediaMuxer? = null
      try {
        extractor.setDataSource(context, inputUri, null)
        val trackMap = mutableMapOf<Int, Int>()
        var maxBuf = BUFFER_CAPACITY

        val pfd =
          context.contentResolver.openFileDescriptor(outputUri, "rw")
            ?: error("无法创建输出视频文件")
        pfd.use { fd ->
          muxer = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
          for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
              extractor.selectTrack(i)
              val dstIdx = muxer!!.addTrack(format)
              trackMap[i] = dstIdx
              if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxBuf = maxOf(maxBuf, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
              }
            }
          }
          require(trackMap.isNotEmpty()) { "未找到可裁剪的音视频轨道" }

          muxer!!.start()
          extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
          val buffer = ByteBuffer.allocate(maxBuf)
          val info = MediaCodec.BufferInfo()
          val basePtsByTrack = mutableMapOf<Int, Long>()

          while (true) {
            val srcTrack = extractor.sampleTrackIndex
            if (srcTrack < 0) break
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) {
              // All tracks are interleaved near endUs; stop once we pass endUs cleanly
              break
            }
            val dstTrack = trackMap[srcTrack]
            if (dstTrack != null) {
              val basePts = basePtsByTrack.getOrPut(srcTrack) { sampleTime }
              info.presentationTimeUs = (sampleTime - basePts).coerceAtLeast(0L)
              info.flags = mapExtractorFlagsToCodecFlags(extractor.sampleFlags)
              muxer!!.writeSampleData(dstTrack, buffer, info)
            }
            extractor.advance()
          }
        }
      } finally {
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        extractor.release()
      }
    }

  private fun mapExtractorFlagsToCodecFlags(sampleFlags: Int): Int {
    var codecFlags = 0
    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
      codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
      codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
    }
    return codecFlags
  }
}
