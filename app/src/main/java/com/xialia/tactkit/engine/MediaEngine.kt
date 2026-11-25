package com.xialia.tactkit.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class MediaInfo(
  val durationMs: Long,
  val hasVideo: Boolean,
  val hasAudio: Boolean,
  val videoMime: String?,
  val audioMime: String?,
  val frameCount: Int? = null,
  val width: Int = 0,
  val height: Int = 0,
)

enum class BatchExtractMode {
  EVERY_FRAME,
  FIXED_FPS,
}

object MediaEngine {

  private const val BUFFER_CAPACITY = 1024 * 1024

  suspend fun inspectMedia(context: Context, uri: Uri): MediaInfo =
    withContext(Dispatchers.IO) {
      val retriever = MediaMetadataRetriever()
      var durationMs = 0L
      var frameCount: Int? = null
      var width = 0
      var height = 0
      try {
        retriever.setDataSource(context, uri)
        durationMs =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        width =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        height =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          frameCount =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
              ?.toIntOrNull()
              ?.takeIf { it > 0 }
        }
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
        frameCount = frameCount,
        width = width,
        height = height,
      )
    }

  suspend fun extractSingleFrame(
    context: Context,
    uri: Uri,
    frameIndex: Int? = null,
    totalFrames: Int? = null,
    timeUs: Long = 0L,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(context, uri)
        if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            frameIndex != null &&
            totalFrames != null &&
            totalFrames > 0
        ) {
          val clamped = frameIndex.coerceIn(0, totalFrames - 1)
          retriever.getFrameAtIndex(clamped)
            ?: retriever.getFrameAtTime(timeUs.coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST)
        } else {
          retriever.getFrameAtTime(timeUs.coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST)
        }
      } finally {
        runCatching { retriever.release() }
      }
    }

  suspend fun saveFrameToUri(
    context: Context,
    bitmap: Bitmap,
    outputUri: Uri,
    asPng: Boolean,
  ) =
    withContext(Dispatchers.IO) {
      val format = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
      val quality = if (asPng) 100 else 95
      context.contentResolver.openOutputStream(outputUri)?.use { out ->
        bitmap.compress(format, quality, out)
      } ?: error("无法保存视频帧文件")
    }

  suspend fun cacheFrameForTransfer(context: Context, bitmap: Bitmap): Uri =
    withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "extracted_frames").apply { mkdirs() }
      // Prune stale cached transfers so cache stays compact
      dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
      val file = File(dir, "frame_${System.currentTimeMillis()}.png")
      file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      Uri.fromFile(file)
    }

  suspend fun batchExtractFramesToTree(
    context: Context,
    inputUri: Uri,
    mediaInfo: MediaInfo,
    treeUri: Uri,
    startMs: Long,
    endMs: Long,
    mode: BatchExtractMode,
    fps: Int,
    asPng: Boolean,
    onProgress: (done: Int, total: Int) -> Unit,
  ): Int =
    withContext(Dispatchers.IO) {
      val safeStartMs = startMs.coerceAtLeast(0L)
      val safeEndMs = endMs.coerceAtLeast(safeStartMs + 50L)
      val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
      val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
      val mime = if (asPng) "image/png" else "image/jpeg"
      val ext = if (asPng) "png" else "jpg"
      val compressFormat = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
      val quality = if (asPng) 100 else 94

      val retriever = MediaMetadataRetriever()
      var exportedCount = 0
      try {
        retriever.setDataSource(context, inputUri)
        val totalFrames = mediaInfo.frameCount
        val useIndexApi =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            mode == BatchExtractMode.EVERY_FRAME &&
            totalFrames != null &&
            totalFrames > 0 &&
            mediaInfo.durationMs > 0L

        if (useIndexApi) {
          val startIdx =
            ((safeStartMs.toDouble() / mediaInfo.durationMs) * totalFrames!!)
              .roundToInt()
              .coerceIn(0, totalFrames - 1)
          val endIdx =
            ((safeEndMs.toDouble() / mediaInfo.durationMs) * totalFrames)
              .roundToInt()
              .coerceIn(startIdx, totalFrames - 1)
          val total = (endIdx - startIdx + 1).coerceAtLeast(1)

          for (idx in startIdx..endIdx) {
            coroutineContext.ensureActive()
            val bmp = retriever.getFrameAtIndex(idx)
            if (bmp != null) {
              try {
                val fileName = String.format(Locale.ROOT, "frame_%05d.%s", idx + 1, ext)
                val outDocUri =
                  DocumentsContract.createDocument(context.contentResolver, parentDocUri, mime, fileName)
                if (outDocUri != null) {
                  context.contentResolver.openOutputStream(outDocUri)?.use { out ->
                    bmp.compress(compressFormat, quality, out)
                  }
                  exportedCount++
                }
              } finally {
                bmp.recycle()
              }
            }
            onProgress(idx - startIdx + 1, total)
          }
        } else {
          val stepMs =
            if (mode == BatchExtractMode.EVERY_FRAME) {
              33L // ~30 fps fallback for API 26-27
            } else {
              (1000L / fps.coerceIn(1, 30)).coerceAtLeast(33L)
            }
          val timestampsUs = mutableListOf<Long>()
          var curMs = safeStartMs
          while (curMs <= safeEndMs) {
            timestampsUs += curMs * 1000L
            curMs += stepMs
          }
          val total = timestampsUs.size.coerceAtLeast(1)
          timestampsUs.forEachIndexed { i, tUs ->
            coroutineContext.ensureActive()
            val bmp = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bmp != null) {
              try {
                val fileName = String.format(Locale.ROOT, "frame_%05d.%s", i + 1, ext)
                val outDocUri =
                  DocumentsContract.createDocument(context.contentResolver, parentDocUri, mime, fileName)
                if (outDocUri != null) {
                  context.contentResolver.openOutputStream(outDocUri)?.use { out ->
                    bmp.compress(compressFormat, quality, out)
                  }
                  exportedCount++
                }
              } finally {
                bmp.recycle()
              }
            }
            onProgress(i + 1, total)
          }
        }
      } finally {
        runCatching { retriever.release() }
      }
      exportedCount
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
          val m = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
          muxer = m
          val dstTrack = m.addTrack(audioFormat)
          m.start()

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
            m.writeSampleData(dstTrack, buffer, info)
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
          val m = MediaMuxer(fd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
          muxer = m
          for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
              extractor.selectTrack(i)
              val dstIdx = m.addTrack(format)
              trackMap[i] = dstIdx
              if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxBuf = maxOf(maxBuf, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
              }
            }
          }
          require(trackMap.isNotEmpty()) { "未找到可裁剪的音视频轨道" }

          m.start()
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
            if (sampleTime > endUs) break
            val dstTrack = trackMap[srcTrack]
            if (dstTrack != null) {
              val basePts = basePtsByTrack.getOrPut(srcTrack) { sampleTime }
              info.presentationTimeUs = (sampleTime - basePts).coerceAtLeast(0L)
              info.flags = mapExtractorFlagsToCodecFlags(extractor.sampleFlags)
              m.writeSampleData(dstTrack, buffer, info)
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

  fun formatFrameTimestampMs(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSec = clamped / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val millis = clamped % 1000L
    return String.format(Locale.ROOT, "%02d:%02d.%03d", min, sec, millis)
  }
}
