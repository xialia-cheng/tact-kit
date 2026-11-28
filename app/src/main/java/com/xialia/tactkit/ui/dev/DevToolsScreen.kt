package com.xialia.tactkit.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.DevToolkitEngine
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics

private enum class DevToolTab(val label: String) {
  BASE64("Base64"),
  URL_CLEAN("URL 与去追踪"),
  JWT("JWT 解析"),
  REGEX("正则测试"),
  RADIX("进制转换"),
  CIDR("IPv4 / CIDR"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  var activeTab by remember { mutableStateOf(DevToolTab.BASE64) }

  // Base64 state
  var b64Input by remember { mutableStateOf("") }
  var b64Output by remember { mutableStateOf("") }
  var b64UrlSafe by remember { mutableStateOf(false) }

  // URL state
  var urlInput by remember { mutableStateOf("") }
  var urlOutput by remember { mutableStateOf("") }
  var urlNote by remember { mutableStateOf<String?>(null) }

  // JWT state
  var jwtInput by remember { mutableStateOf("") }

  // Regex state
  var regexPattern by remember { mutableStateOf("""\b\d{1,3}(?:\.\d{1,3}){3}\b""") }
  var regexSample by remember { mutableStateOf("Server 192.168.1.10 -> 10.0.0.1") }
  var regexIgnoreCase by remember { mutableStateOf(false) }
  var regexMultiline by remember { mutableStateOf(true) }

  // Radix state
  var radixInput by remember { mutableStateOf("255") }
  var radixBase by remember { mutableIntStateOf(10) }

  // CIDR state
  var cidrInput by remember { mutableStateOf("192.168.1.42/24") }

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
          title = { Text("开发实用小工具", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("dev_tools_back_button"),
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
        item(key = "dev_tabs") {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            items(DevToolTab.entries, key = { it.name }) { tab ->
              TactileModeChip(
                selected = activeTab == tab,
                label = tab.label,
                onClick = { activeTab = tab },
                modifier = Modifier.testTag("dev_tab_${tab.name}"),
              )
            }
          }
        }

        when (activeTab) {
          DevToolTab.BASE64 -> {
            item(key = "base64_card") {
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
                    value = b64Input,
                    onValueChange = { b64Input = it },
                    label = { Text("输入原文或 Base64") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("b64_input"),
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text("URL-Safe 无填充模式", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = b64UrlSafe, onCheckedChange = { b64UrlSafe = it })
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileActionButton(
                      onClick = {
                        b64Output = DevToolkitEngine.encodeBase64(b64Input, b64UrlSafe)
                      },
                      modifier = Modifier.weight(1f).testTag("b64_encode_btn"),
                    ) { Text("编码") }
                    TactileActionButton(
                      onClick = {
                        b64Output =
                          runCatching { DevToolkitEngine.decodeBase64(b64Input, b64UrlSafe) }
                            .getOrElse { "解码失败：${it.message}" }
                      },
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f).testTag("b64_decode_btn"),
                    ) { Text("解码") }
                  }
                  if (b64Output.isNotEmpty()) {
                    CopyableMonoCard(label = "输出结果", content = b64Output, context = context)
                  }
                }
              }
            }
          }

          DevToolTab.URL_CLEAN -> {
            item(key = "url_card") {
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
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("输入链接或文本") },
                    placeholder = { Text("https://example.com/item?id=12&utm_source=wechat&spm=1.2") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("url_input"),
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileActionButton(
                      onClick = {
                        runCatching { DevToolkitEngine.stripTrackingParams(urlInput) }
                          .onSuccess { res ->
                            urlOutput = res.cleanedUrl
                            urlNote =
                              if (res.removedParams.isEmpty()) "未发现常见追踪参数"
                              else "已移除 ${res.removedParams.size} 个参数：${res.removedParams.joinToString(", ")}"
                          }
                          .onFailure { urlNote = "链接格式无效：${it.message}" }
                      },
                      modifier = Modifier.weight(1.3f).testTag("url_strip_btn"),
                    ) { Text("清理追踪参数") }

                    TactileActionButton(
                      onClick = {
                        urlOutput = DevToolkitEngine.urlEncode(urlInput)
                        urlNote = "已完成 URL 编码"
                      },
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f),
                    ) { Text("编码") }

                    TactileActionButton(
                      onClick = {
                        urlOutput =
                          runCatching { DevToolkitEngine.urlDecode(urlInput) }
                            .getOrElse { "解码失败" }
                        urlNote = "已完成 URL 解码"
                      },
                      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.weight(1f),
                    ) { Text("解码") }
                  }
                  urlNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                  }
                  if (urlOutput.isNotEmpty()) {
                    CopyableMonoCard(label = "处理结果", content = urlOutput, context = context)
                  }
                }
              }
            }
          }

          DevToolTab.JWT -> {
            item(key = "jwt_card") {
              val decoded =
                remember(jwtInput) {
                  if (jwtInput.isBlank()) null
                  else runCatching { DevToolkitEngine.decodeJwt(jwtInput) }
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
                  OutlinedTextField(
                    value = jwtInput,
                    onValueChange = { jwtInput = it },
                    label = { Text("粘贴 JWT Token (eyJ...)") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("jwt_input"),
                  )
                  decoded?.onSuccess { res ->
                    res.expiresAtFormatted?.let { exp ->
                      Text(
                        text = "过期时间 (exp)：$exp · ${if (res.isExpired == true) "已过期" else "有效期内"}",
                        style = MaterialTheme.typography.labelMedium,
                        color =
                          if (res.isExpired == true) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.primary,
                      )
                    }
                    CopyableMonoCard("Header", res.headerPretty, context)
                    CopyableMonoCard("Payload", res.payloadPretty, context)
                  }?.onFailure { err ->
                    Text(
                      text = "无法解析 JWT：${err.message}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
          }

          DevToolTab.REGEX -> {
            item(key = "regex_card") {
              val result =
                remember(regexPattern, regexSample, regexIgnoreCase, regexMultiline) {
                  DevToolkitEngine.testRegex(regexPattern, regexSample, regexIgnoreCase, regexMultiline)
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
                  OutlinedTextField(
                    value = regexPattern,
                    onValueChange = { regexPattern = it },
                    label = { Text("正则表达式") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("regex_pattern_input"),
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                      selected = regexIgnoreCase,
                      onClick = { regexIgnoreCase = !regexIgnoreCase },
                      label = { Text("忽略大小写 (i)", maxLines = 1, softWrap = false) },
                    )
                    FilterChip(
                      selected = regexMultiline,
                      onClick = { regexMultiline = !regexMultiline },
                      label = { Text("多行模式 (m)", maxLines = 1, softWrap = false) },
                    )
                  }
                  OutlinedTextField(
                    value = regexSample,
                    onValueChange = { regexSample = it },
                    label = { Text("测试文本") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("regex_sample_input"),
                  )

                  if (!result.isValid) {
                    Text(
                      text = result.errorMessage ?: "正则无效",
                      color = MaterialTheme.colorScheme.error,
                      style = MaterialTheme.typography.bodySmall,
                    )
                  } else {
                    Text(
                      text = "命中 ${result.matches.size} 处",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
                    val highlighted =
                      remember(regexSample, result.matches, primaryContainer, onPrimaryContainer) {
                        buildAnnotatedString {
                          var cursor = 0
                          result.matches.forEach { m ->
                            if (m.start > cursor) append(regexSample.substring(cursor, m.start))
                            withStyle(
                              SpanStyle(
                                background = primaryContainer,
                                color = onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                              )
                            ) {
                              append(regexSample.substring(m.start, m.end))
                            }
                            cursor = m.end
                          }
                          if (cursor < regexSample.length) append(regexSample.substring(cursor))
                        }
                      }
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = MaterialTheme.colorScheme.surfaceContainer,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = highlighted,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = TactKitMono,
                        modifier = Modifier.padding(12.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          DevToolTab.RADIX -> {
            item(key = "radix_card") {
              val converted =
                remember(radixInput, radixBase) {
                  runCatching { DevToolkitEngine.convertRadix(radixInput, radixBase) }
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
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                      items = listOf(2 to "二进制", 8 to "八进制", 10 to "十进制", 16 to "十六进制"),
                      key = { it.first },
                      contentType = { "radix_chip" },
                    ) { (base, label) ->
                      FilterChip(
                        selected = radixBase == base,
                        onClick = { radixBase = base },
                        label = { Text(label, maxLines = 1, softWrap = false) },
                      )
                    }
                  }
                  OutlinedTextField(
                    value = radixInput,
                    onValueChange = { radixInput = it },
                    label = { Text("输入数值 ($radixBase 进制)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("radix_input"),
                  )
                  converted.onSuccess { r ->
                    CopyableMonoCard("BIN (2)", r.binary, context)
                    CopyableMonoCard("OCT (8)", r.octal, context)
                    CopyableMonoCard("DEC (10)", r.decimal, context)
                    CopyableMonoCard("HEX (16)", r.hex, context)
                  }.onFailure {
                    Text(
                      text = "请输入合法的 $radixBase 进制整数",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
          }

          DevToolTab.CIDR -> {
            item(key = "cidr_card") {
              val cidrRes =
                remember(cidrInput) {
                  runCatching { DevToolkitEngine.calculateIpv4Cidr(cidrInput) }
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
                  OutlinedTextField(
                    value = cidrInput,
                    onValueChange = { cidrInput = it },
                    label = { Text("IPv4 / 前缀长度") },
                    placeholder = { Text("192.168.1.42/24") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("cidr_input"),
                  )
                  cidrRes.onSuccess { c ->
                    CopyableMonoCard(
                      label = "子网详情 (${c.cidr})",
                      content =
                        buildString {
                          appendLine("网络地址：${c.networkAddress}")
                          appendLine("广播地址：${c.broadcastAddress}")
                          appendLine("子网掩码：${c.netmask}")
                          appendLine("通配掩码：${c.wildcardMask}")
                          appendLine("可用范围：${c.firstUsableHost} ~ ${c.lastUsableHost}")
                          appendLine("可用主机：${c.usableHosts}（总地址 ${c.totalAddresses}）")
                          append("二进制掩码：${c.binaryMask}")
                        },
                      context = context,
                    )
                  }.onFailure { err ->
                    Text(
                      text = err.message ?: "CIDR 格式无效",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error,
                    )
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

@Composable
private fun CopyableMonoCard(
  label: String,
  content: String,
  context: Context,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = content,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = TactKitMono,
        )
      }
      Spacer(modifier = Modifier.width(4.dp))
      IconButton(
        onClick = {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText(label, content))
          Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
        },
      ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制 $label")
      }
    }
  }
}
