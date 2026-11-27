package com.xialia.tactkit.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.HashAndCryptoEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VaultTab(val label: String) {
  FILE_LOCK("文件加密锁"),
  NOTE_LOCK("私密文本锁"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoVaultScreen(
  initialUriString: String? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var activeTab by remember { mutableStateOf(VaultTab.FILE_LOCK) }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  // File mode
  var selectedUri by remember { mutableStateOf<Uri?>(null) }
  var selectedFileName by remember { mutableStateOf<String?>(null) }
  var fileStatusMsg by remember { mutableStateOf<String?>(null) }
  var pendingEncrypt by remember { mutableStateOf(true) }

  // Note mode
  var noteInput by remember { mutableStateOf("") }
  var noteOutput by remember { mutableStateOf("") }
  var noteError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(initialUriString) {
    val raw = initialUriString ?: return@LaunchedEffect
    val uri = Uri.parse(raw)
    selectedUri = uri
    selectedFileName = FileInspectionHelper.queryNameAndSize(context, uri).first
    fileStatusMsg = null
  }

  val pickFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        selectedUri = uri
        selectedFileName = FileInspectionHelper.queryNameAndSize(context, uri).first
        fileStatusMsg = null
        haptics.tick()
      }
    }

  val saveOutputLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { outUri ->
      val inUri = selectedUri ?: return@rememberLauncherForActivityResult
      if (outUri == null || password.isEmpty()) return@rememberLauncherForActivityResult
      scope.launch {
        isBusy = true
        fileStatusMsg = null
        val passChars = password.toCharArray()
        runCatching {
          withContext(Dispatchers.IO) {
            if (pendingEncrypt) {
              HashAndCryptoEngine.encryptUriToUri(context, inUri, outUri, passChars)
            } else {
              HashAndCryptoEngine.decryptUriToUri(context, inUri, outUri, passChars)
            }
          }
        }.onSuccess {
          haptics.confirm()
          fileStatusMsg = if (pendingEncrypt) "文件已采用 AES-256-GCM 加密导出" else "文件已成功解密导出"
        }.onFailure { err ->
          haptics.reject()
          fileStatusMsg = "操作失败：${err.message ?: "口令错误或文件损坏"}"
        }
        passChars.fill('\u0000')
        isBusy = false
      }
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
          title = {
            Text(
              "AES-256 加密锁",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("crypto_vault"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("crypto_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (isBusy) {
          TactKitBusyStatusBar(
            message = "正在执行 AES-256 加密/解密处理...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
        }

        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          item(key = "vault_tabs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(VaultTab.entries, key = { it.name }) { tab ->
                TactileModeChip(
                  selected = activeTab == tab,
                  label = tab.label,
                  onClick = { activeTab = tab },
                  modifier = Modifier.testTag("vault_tab_${tab.name}"),
                )
              }
            }
          }

          item(key = "vault_password_card") {
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
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.EnhancedEncryption, size = 46.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "AES-256-GCM + PBKDF2 口令派生",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "口令仅存在于本次操作内存中，绝不写入任何本地存储",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                OutlinedTextField(
                  value = password,
                  onValueChange = { password = it },
                  label = { Text("加解密口令") },
                  placeholder = { Text("请输入自定义强口令") },
                  singleLine = true,
                  visualTransformation =
                    if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                      Icon(
                        imageVector =
                          if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "显示或隐藏口令",
                      )
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("vault_password_input"),
                )
              }
            }
          }

          if (activeTab == VaultTab.FILE_LOCK) {
            item(key = "vault_file_actions") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text(
                    text = selectedFileName?.let { "已选文件：$it" } ?: "尚未选择文件",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )

                  TactileActionButton(
                    onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().testTag("vault_pick_file_btn"),
                  ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择本地文件")
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileActionButton(
                      enabled = selectedUri != null && password.isNotEmpty() && !isBusy,
                      onClick = {
                        pendingEncrypt = true
                        val base = selectedFileName ?: "secret"
                        saveOutputLauncher.launch("$base.tkenc")
                      },
                      modifier = Modifier.weight(1f).testTag("vault_encrypt_file_btn"),
                    ) {
                      Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("加密导出")
                    }

                    TactileActionButton(
                      enabled = selectedUri != null && password.isNotEmpty() && !isBusy,
                      onClick = {
                        pendingEncrypt = false
                        val base = (selectedFileName ?: "decrypted.bin").removeSuffix(".tkenc")
                        saveOutputLauncher.launch(base)
                      },
                      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.weight(1f).testTag("vault_decrypt_file_btn"),
                    ) {
                      Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("解密导出")
                    }
                  }

                  fileStatusMsg?.let { msg ->
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = MaterialTheme.colorScheme.surfaceContainer,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                      )
                    }
                  }
                }
              }
            }
          } else {
            item(key = "vault_note_card") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("明文便签或 TACTKIT_AES1 密文") },
                    minLines = 4,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("vault_note_input"),
                  )

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileActionButton(
                      enabled = noteInput.isNotEmpty() && password.isNotEmpty(),
                      onClick = {
                        val chars = password.toCharArray()
                        runCatching { HashAndCryptoEngine.encryptTextWithPassword(noteInput, chars) }
                          .onSuccess {
                            haptics.confirm()
                            noteOutput = it
                            noteError = null
                          }
                          .onFailure { noteError = it.message }
                        chars.fill('\u0000')
                      },
                      modifier = Modifier.weight(1f).testTag("vault_encrypt_note_btn"),
                    ) {
                      Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("加密便签")
                    }

                    TactileActionButton(
                      enabled = noteInput.isNotEmpty() && password.isNotEmpty(),
                      onClick = {
                        val chars = password.toCharArray()
                        runCatching { HashAndCryptoEngine.decryptTextWithPassword(noteInput, chars) }
                          .onSuccess {
                            haptics.confirm()
                            noteOutput = it
                            noteError = null
                          }
                          .onFailure {
                            haptics.reject()
                            noteError = "解密失败：口令不匹配或密文不完整"
                          }
                        chars.fill('\u0000')
                      },
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f).testTag("vault_decrypt_note_btn"),
                    ) {
                      Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("解密便签")
                    }
                  }

                  noteError?.let { err ->
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = MaterialTheme.colorScheme.errorContainer,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                      )
                    }
                  }

                  if (noteOutput.isNotEmpty()) {
                    Surface(
                      shape = RoundedCornerShape(16.dp),
                      color = MaterialTheme.colorScheme.surfaceContainer,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                      ) {
                        Text(
                          text = noteOutput,
                          style = MaterialTheme.typography.bodySmall,
                          fontFamily = TactKitMono,
                          modifier = Modifier.weight(1f).testTag("vault_note_output"),
                        )
                        IconButton(
                          onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Vault", noteOutput))
                            Toast.makeText(context, "已复制结果", Toast.LENGTH_SHORT).show()
                          },
                        ) {
                          Icon(Icons.Rounded.ContentCopy, contentDescription = "复制结果")
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
