package com.xialia.tactkit.ui.media

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.MediaEngine
import com.xialia.tactkit.engine.MediaInfo
import com.xialia.tactkit.engine.VoiceMemoManager
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWorkshopScreen(
  initialUriString: String?,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var currentUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
  var trimRange by remember { mutableStateOf(0f..1f) }
  var isBusy by remember { mutableStateOf(false) }

  val pickVideoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) currentUri = uri
    }

  val exportAudioLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { outUri ->
      val src = currentUri
      if (outUri != null && src != null) {
        scope.launch {
          isBusy = true
          runCatching { MediaEngine.extractAudioLossless(context, src, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已无损提取 .m4a 音轨", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "提取失败：${it.message}", Toast.LENGTH_LONG).show()
            }
          isBusy = false
        }
      }
    }

  val exportTrimLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { outUri ->
      val src = currentUri
      val info = mediaInfo
      if (outUri != null && src != null && info != null) {
        val startMs = (trimRange.start * info.durationMs).toLong()
        val endMs = (trimRange.endInclusive * info.durationMs).toLong()
        scope.launch {
          isBusy = true
          runCatching { MediaEngine.trimVideoLossless(context, src, startMs, endMs, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已完成关键帧无损裁剪导出", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "裁剪失败：${it.message}", Toast.LENGTH_LONG).show()
            }
          isBusy = false
        }
      }
    }

  LaunchedEffect(currentUri) {
    val uri = currentUri ?: return@LaunchedEffect
    isBusy = true
    runCatching {
      val info = MediaEngine.inspectMedia(context, uri)
      mediaInfo = info
      trimRange = 0f..1f
    }.onFailure {
      Toast.makeText(context, "无法解析该媒体轨道", Toast.LENGTH_SHORT).show()
    }
    isBusy = false
  }

  if (currentUri == null) {
    ExpressiveEmptyFileScreen(
      title = "音视频无损轻处理",
      icon = Icons.Rounded.AudioFile,
      onPickFile = {
        haptics.tick()
        pickVideoLauncher.launch(
          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
      },
      onNavigateBack = onNavigateBack,
      fabText = "选择视频",
    )
    return
  }

  PredictiveBackContainer(onBack = onNavigateBack) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        LargeTopAppBar(
          colors =
            TopAppBarDefaults.largeTopAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
          title = { Text("音视频无损轻处理", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                pickVideoLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              }
            ) {
              Icon(Icons.Rounded.VideoFile, contentDescription = "切换视频")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
      ) {
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        val info = mediaInfo
        if (info != null) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Text("媒体轨道信息（零重编码）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                text = "总时长：${VoiceMemoManager.formatDuration(info.durationMs)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
              )
              Text(
                text = "视频轨：${info.videoMime ?: "无"} · 音频轨：${info.audioMime ?: "无"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Text("MP4 / MOV 无损提取 .m4a 音轨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                text = "直接透传 AAC 音频帧流到独立 .m4a 容器，零音质损失、瞬间完成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                enabled = info.hasAudio && !isBusy,
                onClick = {
                  haptics.tick()
                  exportAudioLauncher.launch("TactKit_Audio_${System.currentTimeMillis()}.m4a")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("media_extract_audio_btn"),
              ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("导出 .m4a 音频")
              }
            }
          }

          if (info.durationMs > 1000L) {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Text("关键帧无损视频裁剪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val startMs = (trimRange.start * info.durationMs).toLong()
                val endMs = (trimRange.endInclusive * info.durationMs).toLong()
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = "起点 ${VoiceMemoManager.formatDuration(startMs)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TactKitMono),
                  )
                  Text(
                    text = "终点 ${VoiceMemoManager.formatDuration(endMs)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TactKitMono),
                  )
                }
                RangeSlider(
                  value = trimRange,
                  onValueChange = {
                    if (it.endInclusive - it.start >= 0.02f) trimRange = it
                  },
                )
                FilledTonalButton(
                  enabled = !isBusy && endMs > startMs,
                  onClick = {
                    haptics.tick()
                    exportTrimLauncher.launch("TactKit_Trim_${System.currentTimeMillis()}.mp4")
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("media_trim_video_btn"),
                ) {
                  Icon(Icons.Rounded.ContentCut, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("导出裁剪片段 (.mp4)")
                }
              }
            }
          }
        }
      }
    }
  }
}
