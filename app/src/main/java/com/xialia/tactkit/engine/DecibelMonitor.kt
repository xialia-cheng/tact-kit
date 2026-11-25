package com.xialia.tactkit.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure Android AudioRecord-based real-time decibel (dB SPL approximate) meter with RMS waveform feed.
 */
class DecibelMonitor {

  private var audioRecord: AudioRecord? = null
  private var readJob: Job? = null

  @SuppressLint("MissingPermission")
  fun start(
    scope: CoroutineScope,
    onSample: (db: Float) -> Unit,
    onError: (String) -> Unit,
  ) {
    stop()
    val sampleRate = 44100
    val minBuf = AudioRecord.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
    )
    if (minBuf <= 0) {
      onError("当前设备不支持音频采样")
      return
    }
    val bufferSize = maxOf(minBuf, 4096)
    val recorder = runCatching {
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
      )
    }.getOrElse {
      onError("无法初始化麦克风：${it.message ?: "硬件被占用"}")
      return
    }

    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
      recorder.release()
      onError("麦克风初始化失败")
      return
    }

    audioRecord = recorder
    runCatching { recorder.startRecording() }.onFailure {
      recorder.release()
      audioRecord = null
      onError("启动录音采样失败：${it.message}")
      return
    }

    readJob =
      scope.launch(Dispatchers.IO) {
        val pcm = ShortArray(2048)
        while (isActive) {
          val read = recorder.read(pcm, 0, pcm.size)
          if (read > 0) {
            val db = computeDbFromPcm16(pcm, read)
            onSample(db)
          }
        }
      }
  }

  fun stop() {
    readJob?.cancel()
    readJob = null
    val rec = audioRecord
    audioRecord = null
    if (rec != null) {
      runCatching { rec.stop() }
      runCatching { rec.release() }
    }
  }

  companion object {
    fun computeDbFromPcm16(samples: ShortArray, count: Int = samples.size): Float {
      if (count <= 0) return 25f
      var sumSq = 0.0
      for (i in 0 until count) {
        val s = samples[i].toDouble()
        sumSq += s * s
      }
      val rms = sqrt(sumSq / count).coerceAtLeast(1.0)
      // Calibrated relative offset for typical smartphone MEMS mic PCM16 range
      val dbFs = 20.0 * log10(rms / 32767.0)
      return (dbFs + 98.0).toFloat().coerceIn(20f, 120f)
    }
  }
}
