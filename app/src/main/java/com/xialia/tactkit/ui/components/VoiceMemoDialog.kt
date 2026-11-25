package com.xialia.tactkit.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xialia.tactkit.engine.VoiceMemoManager
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.delay

@Composable
fun VoiceMemoDialog(
  voiceMemoManager: VoiceMemoManager,
  onDismiss: () -> Unit,
  onSaveMemo: (title: String, filePath: String, durationMs: Long, note: String) -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var permissionDenied by remember { mutableStateOf(false) }
  var isRecording by remember { mutableStateOf(false) }
  var elapsedMs by remember { mutableLongStateOf(0L) }
  var title by remember { mutableStateOf("") }
  var noteText by remember { mutableStateOf("") }
  var recordedResult by remember { mutableStateOf<Pair<String, Long>?>(null) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      permissionDenied = !granted
    }

  LaunchedEffect(isRecording) {
    if (isRecording) {
      elapsedMs = 0L
      while (isRecording) {
        delay(200L)
        elapsedMs += 200L
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      if (isRecording) {
        voiceMemoManager.cancelRecording()
      }
    }
  }

  AlertDialog(
    onDismissRequest = {
      if (isRecording) voiceMemoManager.cancelRecording()
      onDismiss()
    },
    title = { Text("原生语音备忘录 (AAC)") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("备忘标题") },
          placeholder = { Text("例如：灵感口述记录") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("voice_memo_title"),
        )

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (isRecording) {
                Box(
                  modifier =
                    Modifier.size(10.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.error)
                )
              }
              Text(
                text =
                  VoiceMemoManager.formatDuration(
                    recordedResult?.second ?: elapsedMs
                  ),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TactKitMono),
                fontWeight = FontWeight.Bold,
              )
            }

            if (!hasPermission) {
              FilledTonalButton(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.testTag("grant_audio_permission_btn"),
              ) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("授予麦克风权限")
              }
              if (permissionDenied) {
                Text(
                  text = "需要麦克风权限才能录制本地 .m4a 音频。",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            } else {
              Button(
                colors =
                  if (isRecording) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                  } else {
                    ButtonDefaults.buttonColors()
                  },
                onClick = {
                  if (isRecording) {
                    isRecording = false
                    recordedResult = voiceMemoManager.stopRecording()
                    haptics.confirm()
                  } else {
                    recordedResult = null
                    voiceMemoManager.startRecording()
                    isRecording = true
                    haptics.tick()
                  }
                },
                modifier = Modifier.testTag("voice_memo_record_toggle"),
              ) {
                Icon(
                  imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                  contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  if (isRecording) "停止录音"
                  else if (recordedResult != null) "重新录制"
                  else "开始录制"
                )
              }
            }
          }
        }

        OutlinedTextField(
          value = noteText,
          onValueChange = { noteText = it },
          label = { Text("补充文本备注（可选）") },
          minLines = 2,
          maxLines = 4,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        enabled = !isRecording && recordedResult != null,
        onClick = {
          val (path, dur) = recordedResult ?: return@Button
          haptics.confirm()
          onSaveMemo(title, path, dur, noteText)
        },
        modifier = Modifier.testTag("voice_memo_save_btn"),
      ) {
        Text("保存到桌面")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          if (isRecording) voiceMemoManager.cancelRecording()
          onDismiss()
        },
      ) {
        Text("取消")
      }
    },
  )
}
