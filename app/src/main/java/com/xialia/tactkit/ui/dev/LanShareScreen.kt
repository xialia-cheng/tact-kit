package com.xialia.tactkit.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xialia.tactkit.engine.LanReceivedFile
import com.xialia.tactkit.engine.LanReceivedText
import com.xialia.tactkit.engine.LanShareServer
import com.xialia.tactkit.engine.LanSharedOutFile
import com.xialia.tactkit.engine.QrCodeEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.rememberAppHaptics
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanShareScreen(
  initialUriString: String? = null,
  onSaveNoteCard: (String, String) -> Unit = { _, _ -> },
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  val receivedFiles = remember { mutableStateListOf<LanReceivedFile>() }
  val receivedTexts = remember { mutableStateListOf<LanReceivedText>() }
  val sharedOutFiles = remember { mutableStateListOf<LanSharedOutFile>() }
  val logs = remember { mutableStateListOf<String>() }

  var isRunning by remember { mutableStateOf(false) }
  var serverPort by remember { mutableIntStateOf(-1) }
  var ipList by remember { mutableStateOf(LanShareServer.resolveLocalIpv4Addresses()) }
  var selectedIp by remember { mutableStateOf(ipList.firstOrNull() ?: "127.0.0.1") }
  var pendingSaveFile by remember { mutableStateOf<LanReceivedFile?>(null) }

  val uploadDir = remember { File(context.cacheDir, "lan_share_uploads") }
  val stagedOutDir = remember { File(context.cacheDir, "lan_share_out") }

  val server =
    remember {
      LanShareServer(
        uploadDir = uploadDir,
        onFileReceived = { rec ->
          scope.launch(Dispatchers.Main) {
            receivedFiles.add(0, rec)
          }
        },
        onLog = { msg ->
          scope.launch(Dispatchers.Main) {
            logs.add(0, msg)
          }
        },
        onTextReceived = { textItem ->
          scope.launch(Dispatchers.Main) {
            receivedTexts.add(0, textItem)
          }
        },
      )
    }

  DisposableEffect(Unit) {
    onDispose {
      server.stop()
      stagedOutDir.deleteRecursively()
    }
  }

  LaunchedEffect(initialUriString) {
    val raw = initialUriString ?: return@LaunchedEffect
    val uri = android.net.Uri.parse(raw)
    stagedOutDir.mkdirs()
    runCatching {
      withContext(Dispatchers.IO) {
        val (displayName, _) = FileInspectionHelper.queryNameAndSize(context, uri)
        val dest = File(stagedOutDir, "${System.currentTimeMillis()}_$displayName")
        context.contentResolver.openInputStream(uri)?.use { input ->
          FileOutputStream(dest).use { out -> input.copyTo(out) }
        }
        LanSharedOutFile(
          displayName = displayName,
          byteSize = dest.length(),
          stagedFile = dest,
        )
      }
    }.onSuccess { staged ->
      sharedOutFiles.add(staged)
      server.sharedOutFiles.add(staged)
    }
  }

  val stageOutFilePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        scope.launch {
          stagedOutDir.mkdirs()
          uris.forEach { uri ->
            runCatching {
              withContext(Dispatchers.IO) {
                val (displayName, _) = FileInspectionHelper.queryNameAndSize(context, uri)
                val dest = File(stagedOutDir, "${System.currentTimeMillis()}_$displayName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                  FileOutputStream(dest).use { out -> input.copyTo(out) }
                }
                LanSharedOutFile(
                  displayName = displayName,
                  byteSize = dest.length(),
                  stagedFile = dest,
                )
              }
            }.onSuccess { staged ->
              sharedOutFiles.add(staged)
              server.sharedOutFiles.add(staged)
            }
          }
          haptics.confirm()
        }
      }
    }

  val exportReceivedLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { outUri ->
      val target = pendingSaveFile ?: return@rememberLauncherForActivityResult
      if (outUri != null) {
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              target.savedFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(outUri)?.use { out -> input.copyTo(out) }
              }
            }
          }.onSuccess {
            haptics.confirm()
            Toast.makeText(context, "已导出：${target.fileName}", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }

  val serverUrl = if (isRunning && serverPort > 0) "http://$selectedIp:$serverPort" else ""
  val qrBitmap: Bitmap? =
    remember(serverUrl) {
      if (serverUrl.isBlank()) null
      else runCatching { QrCodeEngine.generateQrBitmap(serverUrl, sizePx = 480) }.getOrNull()
    }

  PredictiveBackContainer(onBack = onNavigateBack) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
          title = { Text("局域网 HTTP 快传", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                server.stop()
                onNavigateBack()
              },
              modifier = Modifier.testTag("lan_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding).widthIn(max = 600.dp),
      ) {
        item(key = "lan_control_card") {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                PetalIconBadge(icon = Icons.Rounded.WifiTethering, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (isRunning) "服务运行中 · 端口 $serverPort" else "临时局域网文件服务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "同 Wi-Fi 下电脑或手机浏览器扫码直连，退出或点击停止即关闭端口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              if (ipList.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(ipList, key = { it }) { ip ->
                    FilterChip(
                      selected = selectedIp == ip,
                      onClick = { selectedIp = ip },
                      label = { Text(ip, maxLines = 1, softWrap = false) },
                    )
                  }
                }
              }

              TactileActionButton(
                onClick = {
                  if (!isRunning) {
                    ipList = LanShareServer.resolveLocalIpv4Addresses()
                    if (ipList.isNotEmpty() && selectedIp !in ipList) {
                      selectedIp = ipList.first()
                    }
                    scope.launch {
                      val startedPort = withContext(Dispatchers.IO) { server.start(8765) }
                      serverPort = startedPort
                      isRunning = true
                      haptics.confirm()
                    }
                  } else {
                    server.stop()
                    isRunning = false
                    serverPort = -1
                    haptics.tick()
                  }
                },
                containerColor =
                  if (isRunning) MaterialTheme.colorScheme.errorContainer
                  else MaterialTheme.colorScheme.primary,
                contentColor =
                  if (isRunning) MaterialTheme.colorScheme.onErrorContainer
                  else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().testTag("lan_toggle_btn"),
              ) {
                Icon(
                  imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunning) "一键停止 HTTP 服务" else "启动局域网 HTTP 服务")
              }

              if (isRunning && serverUrl.isNotEmpty()) {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  shape = RoundedCornerShape(18.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                  ) {
                    Text(
                      text = serverUrl,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      fontFamily = TactKitMono,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.testTag("lan_server_url"),
                    )
                    qrBitmap?.let { bmp ->
                      Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "局域网服务二维码",
                        modifier = Modifier.size(180.dp).clip(RoundedCornerShape(14.dp)),
                      )
                    }
                  }
                }
              }
            }
          }
        }

        item(key = "lan_send_card") {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "共享到网页的文件 (${sharedOutFiles.size})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                TactileActionButton(
                  onClick = { stageOutFilePicker.launch(arrayOf("*/*")) },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("添加文件")
                }
              }
              sharedOutFiles.forEach { f ->
                Text(
                  text = "• ${f.displayName} (${FileInspectionHelper.formatBytes(f.byteSize)})",
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        if (receivedTexts.isNotEmpty()) {
          item(key = "lan_received_texts_header") {
            Text(
              text = "已接收的文字 (${receivedTexts.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 4.dp),
            )
          }
          items(receivedTexts, key = { "txt_${it.id}" }) { txt ->
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("lan_received_text_card"),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp),
              ) {
                Text(
                  text = txt.content,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 6,
                  overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TactileActionButton(
                    onClick = {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      cm.setPrimaryClip(ClipData.newPlainText("LAN Text", txt.content))
                      haptics.confirm()
                      Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制文字")
                  }
                  TactileActionButton(
                    onClick = {
                      val title = txt.content.lineSequence().firstOrNull()?.take(24)?.ifBlank { "局域网接收便签" }
                        ?: "局域网接收便签"
                      onSaveNoteCard(title, txt.content)
                      haptics.confirm()
                      Toast.makeText(context, "已保存为桌面便签", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("存为便签")
                  }
                }
              }
            }
          }
        }

        item(key = "lan_received_header") {
          Text(
            text = "已接收的文件 (${receivedFiles.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }

        items(receivedFiles, key = { it.id }) { rec ->
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(14.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = rec.fileName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = FileInspectionHelper.formatBytes(rec.byteSize),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = {
                  runCatching {
                    val uri =
                      FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        rec.savedFile,
                      )
                    context.startActivity(
                      Intent(context, QuickLookActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      }
                    )
                  }.onFailure {
                    Toast.makeText(context, "请使用右侧按钮导出保存", Toast.LENGTH_SHORT).show()
                  }
                },
              ) {
                Icon(Icons.Rounded.Preview, contentDescription = "预览")
              }
              IconButton(
                onClick = {
                  pendingSaveFile = rec
                  exportReceivedLauncher.launch(rec.fileName)
                },
              ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = "保存到本地")
              }
            }
          }
        }
      }
    }
  }
}
