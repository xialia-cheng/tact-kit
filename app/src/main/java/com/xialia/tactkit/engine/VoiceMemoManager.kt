package com.xialia.tactkit.engine

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioPlaybackState(
  val playingCardId: Long? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
)

class VoiceMemoManager(private val context: Context) {

  private var recorder: MediaRecorder? = null
  private var activeRecordingFile: File? = null
  private var recordingStartTimeMs: Long = 0L
  private var amplitudeJob: Job? = null

  private var player: MediaPlayer? = null
  private var progressJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.Main)

  private val _playbackState = MutableStateFlow(AudioPlaybackState())
  val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

  private val _amplitudeHistory = MutableStateFlow<List<Float>>(emptyList())
  val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

  fun startRecording(): File {
    stopPlayback()
    val dir = File(context.filesDir, "voice_memos").apply { mkdirs() }
    val outFile = File(dir, "memo_${System.currentTimeMillis()}.m4a")
    val rec =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }
    try {
      rec.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128_000)
        setAudioSamplingRate(44_100)
        setOutputFile(outFile.absolutePath)
        prepare()
        start()
      }
    } catch (e: Exception) {
      runCatching { rec.release() }
      runCatching { outFile.delete() }
      throw e
    }
    recorder = rec
    activeRecordingFile = outFile
    recordingStartTimeMs = System.currentTimeMillis()
    startAmplitudeSampler(rec)
    return outFile
  }

  private fun startAmplitudeSampler(rec: MediaRecorder) {
    amplitudeJob?.cancel()
    _amplitudeHistory.value = List(MAX_WAVE_BARS) { 0.08f }
    amplitudeJob =
      scope.launch {
        while (recorder === rec) {
          val raw = runCatching { rec.maxAmplitude }.getOrDefault(0)
          val normalized = sqrt((raw / 32767f).coerceIn(0f, 1f)).coerceIn(0.08f, 1f)
          val next = (_amplitudeHistory.value + normalized).takeLast(MAX_WAVE_BARS)
          _amplitudeHistory.value = next
          delay(65L)
        }
      }
  }

  fun stopRecording(): Pair<String, Long>? {
    amplitudeJob?.cancel()
    amplitudeJob = null
    _amplitudeHistory.value = emptyList()

    val rec = recorder ?: return null
    val file = activeRecordingFile ?: return null
    val elapsed = (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(500L)
    return try {
      rec.stop()
      rec.release()
      recorder = null
      activeRecordingFile = null
      file.absolutePath to elapsed
    } catch (_: Exception) {
      runCatching { rec.release() }
      recorder = null
      activeRecordingFile = null
      runCatching { file.delete() }
      null
    }
  }

  fun cancelRecording() {
    amplitudeJob?.cancel()
    amplitudeJob = null
    _amplitudeHistory.value = emptyList()

    val rec = recorder
    val file = activeRecordingFile
    recorder = null
    activeRecordingFile = null
    runCatching { rec?.stop() }
    runCatching { rec?.release() }
    runCatching { file?.delete() }
  }

  fun togglePlayback(cardId: Long, filePath: String) {
    val current = _playbackState.value
    if (current.playingCardId == cardId && player != null) {
      if (current.isPlaying) {
        player?.pause()
        progressJob?.cancel()
        _playbackState.value = current.copy(isPlaying = false)
      } else {
        player?.start()
        startProgressTicker(cardId)
        _playbackState.value = current.copy(isPlaying = true)
      }
      return
    }

    stopPlayback()
    val file = File(filePath)
    if (!file.exists()) return

    val mp = MediaPlayer().apply {
      setDataSource(file.absolutePath)
      prepare()
      setOnCompletionListener {
        progressJob?.cancel()
        _playbackState.value =
          AudioPlaybackState(
            playingCardId = cardId,
            isPlaying = false,
            positionMs = duration.toLong(),
            durationMs = duration.toLong(),
          )
      }
      start()
    }
    player = mp
    _playbackState.value =
      AudioPlaybackState(
        playingCardId = cardId,
        isPlaying = true,
        positionMs = 0L,
        durationMs = mp.duration.toLong().coerceAtLeast(1L),
      )
    startProgressTicker(cardId)
  }

  fun seekTo(cardId: Long, positionMs: Long) {
    if (_playbackState.value.playingCardId != cardId) return
    val mp = player ?: return
    val target = positionMs.toInt().coerceIn(0, mp.duration)
    mp.seekTo(target)
    _playbackState.value = _playbackState.value.copy(positionMs = target.toLong())
  }

  fun stopPlayback() {
    progressJob?.cancel()
    progressJob = null
    runCatching { player?.stop() }
    runCatching { player?.release() }
    player = null
    _playbackState.value = AudioPlaybackState()
  }

  private fun startProgressTicker(cardId: Long) {
    progressJob?.cancel()
    progressJob =
      scope.launch {
        while (true) {
          val mp = player ?: break
          if (!mp.isPlaying) break
          _playbackState.value =
            AudioPlaybackState(
              playingCardId = cardId,
              isPlaying = true,
              positionMs = mp.currentPosition.toLong(),
              durationMs = mp.duration.toLong().coerceAtLeast(1L),
            )
          delay(120L)
        }
      }
  }

  companion object {
    const val MAX_WAVE_BARS = 40

    fun formatDuration(ms: Long): String {
      val totalSec = (ms / 1000L).coerceAtLeast(0L)
      val min = totalSec / 60L
      val sec = totalSec % 60L
      return String.format(Locale.ROOT, "%02d:%02d", min, sec)
    }
  }
}
