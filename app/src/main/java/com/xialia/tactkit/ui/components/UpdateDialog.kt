package com.xialia.tactkit.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xialia.tactkit.data.repository.ApkDownloadState
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.data.repository.ReleaseInfo
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.AppHaptics
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun NewVersionUpdateDialog(
  releaseInfo: ReleaseInfo,
  currentVersionName: String,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val updateRepo = remember { AppUpdateRepository() }

  var downloadState by remember { mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle) }
  var downloadJob by remember { mutableStateOf<Job?>(null) }
  var needsInstallPermission by remember { mutableStateOf(false) }

  fun cancelActiveDownload(resetToIdle: Boolean = true) {
    downloadJob?.cancel()
    downloadJob = null
    AppUpdateRepository.cleanupPartialDownloads(context)
    needsInstallPermission = false
    if (resetToIdle) {
      downloadState = ApkDownloadState.Idle
    }
  }

  fun startSourceDownload(url: String, sourceLabel: String) {
    downloadJob?.cancel()
    needsInstallPermission = false
    downloadState =
      ApkDownloadState.Downloading(
        sourceLabel = sourceLabel,
        url = url,
        downloadedBytes = 0L,
        totalBytes = -1L,
        progress = 0f,
        speedBytesPerSec = 0L,
      )
    downloadJob =
      scope.launch {
        val result =
          updateRepo.downloadApk(
            context = context,
            url = url,
            sourceLabel = sourceLabel,
            onProgress = { progressState ->
              downloadState = progressState
            },
          )
        result
          .onSuccess { apkFile ->
            downloadState = ApkDownloadState.Completed(apkFile = apkFile, totalBytes = apkFile.length())
            if (canInstallPackages(context)) {
              needsInstallPermission = false
              launchApkInstaller(context, apkFile)
            } else {
              needsInstallPermission = true
            }
          }
          .onFailure { err ->
            if (err !is kotlinx.coroutines.CancellationException) {
              downloadState =
                ApkDownloadState.Failed(
                  sourceLabel = sourceLabel,
                  url = url,
                  message = err.message ?: "网络连接中断或下载失败",
                )
            }
          }
      }
  }

  DisposableEffect(lifecycleOwner, needsInstallPermission, downloadState) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          val completed = downloadState as? ApkDownloadState.Completed
          if (completed != null && needsInstallPermission && canInstallPackages(context)) {
            needsInstallPermission = false
            launchApkInstaller(context, completed.apkFile)
          }
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  DisposableEffect(Unit) {
    onDispose {
      downloadJob?.cancel()
      AppUpdateRepository.cleanupPartialDownloads(context)
    }
  }

  val activeState = downloadState
  val isDownloading = activeState is ApkDownloadState.Downloading

  AlertDialog(
    onDismissRequest = {
      if (!isDownloading) {
        onDismiss()
      }
    },
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text =
            when (activeState) {
              is ApkDownloadState.Downloading -> "正在下载更新 ${releaseInfo.tagName}"
              is ApkDownloadState.Completed ->
                if (needsInstallPermission) "需要授权安装权限" else "更新安装包已就绪"
              else -> "发现新版本 ${releaseInfo.tagName}"
            },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text =
            when (activeState) {
              is ApkDownloadState.Downloading ->
                "线路：${activeState.sourceLabel} · 保存至缓存更新目录"
              is ApkDownloadState.Completed ->
                "文件：${activeState.apkFile.name} (${formatDownloadSize(activeState.totalBytes)})"
              else -> "当前版本 v$currentVersionName · 点击下方线路立即应用内下载"
            },
          style = MaterialTheme.typography.labelMedium,
          fontFamily = TactKitMono,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      ) {
        when (activeState) {
          is ApkDownloadState.Downloading -> {
            Surface(
              shape = themeShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth().testTag("update_downloading_panel"),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text =
                      if (activeState.totalBytes > 0L) "${(activeState.progress * 100f).toInt().coerceIn(0, 100)}%"
                      else "正在建立流式连接...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TactKitMono,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = "${formatSpeed(activeState.speedBytesPerSec)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = TactKitMono,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                TactKitThemedProgressBar(
                  progress = activeState.progress,
                  modifier = Modifier.fillMaxWidth().testTag("update_download_progress_bar"),
                )

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text =
                      if (activeState.totalBytes > 0L) {
                        "${formatDownloadSize(activeState.downloadedBytes)} / ${formatDownloadSize(activeState.totalBytes)}"
                      } else {
                        "已下载 ${formatDownloadSize(activeState.downloadedBytes)}"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TactKitMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = activeState.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          is ApkDownloadState.Completed -> {
            TactKitThemedProgressBar(
              progress = 1f,
              animateProgress = false,
              modifier = Modifier.fillMaxWidth(),
            )
            Surface(
              shape = themeShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text =
                  if (needsInstallPermission) {
                    "安装包已下载完成，但系统尚未开启「安装未知应用」权限。点击下方按钮前往系统设置授权后，返回即可自动拉起安装。"
                  } else {
                    "安装包校验完毕，若系统安装界面未自动弹出，可点击下方按钮手动拉起安装。"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
              )
            }
            if (needsInstallPermission) {
              TactileActionButton(
                onClick = { openUnknownSourcesSettings(context) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().testTag("update_btn_grant_install_perm"),
              ) {
                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("前往开启安装未知应用权限", fontWeight = FontWeight.SemiBold)
              }
            } else {
              TactileActionButton(
                onClick = { launchApkInstaller(context, activeState.apkFile) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().testTag("update_btn_install_now"),
              ) {
                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("立即安装更新", fontWeight = FontWeight.SemiBold)
              }
            }
          }

          is ApkDownloadState.Idle,
          is ApkDownloadState.Failed -> {
            if (activeState is ApkDownloadState.Failed) {
              Surface(
                shape = themeShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().testTag("update_download_error"),
              ) {
                Text(
                  text = "${activeState.sourceLabel}下载失败：${activeState.message}。可换用下方镜像线路重试。",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
            if (releaseInfo.body.isNotBlank()) {
              Surface(
                shape = themeShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = releaseInfo.body,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            TactileActionButton(
              onClick = { startSourceDownload(releaseInfo.officialDownloadUrl, "官方直连") },
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.fillMaxWidth().testTag("update_btn_official"),
            ) {
              Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("官方直连", fontWeight = FontWeight.SemiBold)
            }

            TactileActionButton(
              onClick = { startSourceDownload(releaseInfo.fastestMirrorDownloadUrl, "最快镜像 (ghfast.top)") },
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.fillMaxWidth().testTag("update_btn_fastest_mirror"),
            ) {
              Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("最快镜像 (ghfast.top)", fontWeight = FontWeight.SemiBold)
            }

            TactileActionButton(
              onClick = { startSourceDownload(releaseInfo.stableMirrorDownloadUrl, "最稳定镜像 (gh-proxy.com)") },
              containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.fillMaxWidth().testTag("update_btn_stable_mirror"),
            ) {
              Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("最稳定镜像 (gh-proxy.com)", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      when (activeState) {
        is ApkDownloadState.Downloading -> {
          TextButton(
            onClick = {
              AppHaptics.click(view)
              cancelActiveDownload(resetToIdle = true)
            },
            modifier = Modifier.testTag("update_btn_cancel_download"),
          ) {
            Text("取消下载")
          }
        }
        is ApkDownloadState.Completed -> {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = {
                AppHaptics.click(view)
                cancelActiveDownload(resetToIdle = true)
              },
            ) {
              Text("重选线路")
            }
            TextButton(
              onClick = {
                AppHaptics.click(view)
                onDismiss()
              },
              modifier = Modifier.testTag("update_btn_dismiss"),
            ) {
              Text("关闭")
            }
          }
        }
        else -> {
          TextButton(
            onClick = {
              AppHaptics.click(view)
              cancelActiveDownload(resetToIdle = false)
              onDismiss()
            },
            modifier = Modifier.testTag("update_btn_dismiss"),
          ) {
            Text("暂不更新")
          }
        }
      }
    },
  )
}

private fun canInstallPackages(context: Context): Boolean {
  return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
}

private fun openUnknownSourcesSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
      Uri.parse("package:${context.packageName}"),
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  runCatching { context.startActivity(intent) }.onFailure {
    Toast.makeText(context, "无法打开应用安装权限设置，请手动前往系统设置开启", Toast.LENGTH_LONG).show()
  }
}

private fun launchApkInstaller(context: Context, apkFile: File) {
  runCatching {
    val uri =
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile,
      )
    val installIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(installIntent)
  }.onFailure { err ->
    Toast.makeText(context, "无法拉起系统安装器：${err.message ?: "请检查安装包"}", Toast.LENGTH_LONG).show()
  }
}

private fun formatDownloadSize(bytes: Long): String {
  if (bytes <= 0L) return "0.0 MB"
  val mb = bytes.toDouble() / (1024.0 * 1024.0)
  return if (mb >= 0.1) {
    String.format(Locale.US, "%.2f MB", mb)
  } else {
    String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024.0)
  }
}

private fun formatSpeed(bytesPerSec: Long): String {
  if (bytesPerSec <= 0L) return "计算网速..."
  return when {
    bytesPerSec >= 1024L * 1024L ->
      String.format(Locale.US, "%.2f MB/s", bytesPerSec.toDouble() / (1024.0 * 1024.0))
    bytesPerSec >= 1024L ->
      String.format(Locale.US, "%.1f KB/s", bytesPerSec.toDouble() / 1024.0)
    else -> "$bytesPerSec B/s"
  }
}
