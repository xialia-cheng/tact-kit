package com.xialia.tactkit.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.data.repository.ReleaseInfo
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.AppHaptics

@Composable
fun NewVersionUpdateDialog(
  releaseInfo: ReleaseInfo,
  currentVersionName: String,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "发现新版本 ${releaseInfo.tagName}",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "当前版本 v$currentVersionName · 点击下方线路立即下载 APK",
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
        if (releaseInfo.body.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
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
          onClick = {
            openDownloadUrl(context, releaseInfo.officialDownloadUrl)
            onDismiss()
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.fillMaxWidth().testTag("update_btn_official"),
        ) {
          Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("官方直连", fontWeight = FontWeight.SemiBold)
        }

        TactileActionButton(
          onClick = {
            openDownloadUrl(context, releaseInfo.fastestMirrorDownloadUrl)
            onDismiss()
          },
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.fillMaxWidth().testTag("update_btn_fastest_mirror"),
        ) {
          Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("最快镜像 (ghfast.top)", fontWeight = FontWeight.SemiBold)
        }

        TactileActionButton(
          onClick = {
            openDownloadUrl(context, releaseInfo.stableMirrorDownloadUrl)
            onDismiss()
          },
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier.fillMaxWidth().testTag("update_btn_stable_mirror"),
        ) {
          Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("最稳定镜像 (gh-proxy)", fontWeight = FontWeight.SemiBold)
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(
        onClick = {
          AppHaptics.click(view)
          onDismiss()
        },
        modifier = Modifier.testTag("update_btn_dismiss"),
      ) {
        Text("暂不更新")
      }
    },
  )
}

private fun openDownloadUrl(context: Context, url: String) {
  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
  runCatching { context.startActivity(intent) }.onFailure {
    Toast.makeText(context, "未找到可用的浏览器或下载器", Toast.LENGTH_SHORT).show()
  }
}
