package com.xialia.tactkit.ui.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.DnsSslReport
import com.xialia.tactkit.engine.NetworkDiagEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSslScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var domainInput by remember { mutableStateOf("github.com") }
  var isInspecting by remember { mutableStateOf(false) }
  var report by remember { mutableStateOf<DnsSslReport?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  fun runInspect(domain: String = domainInput) {
    scope.launch {
      isInspecting = true
      errorMsg = null
      runCatching { NetworkDiagEngine.inspectDnsAndSsl(domain) }
        .onSuccess { report = it }
        .onFailure { errorMsg = it.message ?: "检测失败" }
      isInspecting = false
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
              "DNS 与 SSL 证书检查",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("dns_ssl"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("dns_ssl_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
        )
      },
    ) { innerPadding ->
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          item(key = "dns_query_card") {
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
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.Dns, size = 44.dp)
                  Column {
                    Text("域名解析与 TLS 证书体检", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("直连探测 A/AAAA 记录、颁发机构与剩余天数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(
                    items = listOf("github.com", "google.com", "deepseek.com", "cloudflare.com"),
                    key = { it },
                    contentType = { "dns_preset_chip" },
                  ) { preset ->
                    FilterChip(
                      selected = domainInput.equals(preset, ignoreCase = true),
                      onClick = {
                        domainInput = preset
                        runInspect(preset)
                      },
                      label = { Text(preset) },
                    )
                  }
                }

                OutlinedTextField(
                  value = domainInput,
                  onValueChange = { domainInput = it },
                  label = { Text("输入域名") },
                  placeholder = { Text("例如：github.com") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("dns_domain_input"),
                )

                TactileActionButton(
                  enabled = !isInspecting && domainInput.isNotBlank(),
                  onClick = { runInspect() },
                  modifier = Modifier.fillMaxWidth().testTag("dns_inspect_button"),
                ) {
                  Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("立即解析与查验证书", fontWeight = FontWeight.SemiBold)
                }

                if (isInspecting) {
                  TactKitBusyStatusBar(message = "正在解析 DNS 记录与 TLS 握手证书链...")
                }
              }
            }
          }

          errorMsg?.let { msg ->
            item(key = "dns_error") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
              }
            }
          }

          report?.let { res ->
            item(key = "dns_ips_card") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().testTag("dns_ips_card"),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text("DNS 解析地址 (${res.domain})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  if (res.resolvedIps.isEmpty()) {
                    Text("未解析到 IP 地址", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                  } else {
                    com.xialia.tactkit.ui.components.SimpleFlowRow(
                      horizontalSpacing = 8.dp,
                      verticalSpacing = 8.dp,
                    ) {
                      res.resolvedIps.forEach { ip ->
                        Surface(
                          onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("IP", ip))
                            Toast.makeText(context, "已复制 $ip", Toast.LENGTH_SHORT).show()
                          },
                          shape = RoundedCornerShape(12.dp),
                          color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                          ) {
                            Text(ip, fontFamily = TactKitMono, style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", modifier = Modifier.size(14.dp))
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            item(key = "ssl_cert_card") {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().testTag("ssl_cert_card"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("SSL/TLS 证书信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    res.daysRemaining?.let { days ->
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color =
                          if (days <= 14L) MaterialTheme.colorScheme.errorContainer
                          else MaterialTheme.colorScheme.primaryContainer,
                      ) {
                        Text(
                          text = if (days >= 0L) "剩余 $days 天" else "已过期 ${-days} 天",
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          color =
                            if (days <= 14L) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                      }
                    }
                  }

                  if (res.sslError != null) {
                    Text(res.sslError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                  } else {
                    CertField("主体 (Subject)", res.certSubject.orEmpty())
                    CertField("颁发机构 (Issuer)", res.certIssuer.orEmpty())
                    CertField("有效期", "${res.validFrom.orEmpty()} ~ ${res.validUntil.orEmpty()}")
                    CertField("SHA-256 指纹", res.sha256Fingerprint.orEmpty())
                    if (res.sanList.isNotEmpty()) {
                      CertField("备用域名 (SAN)", res.sanList.joinToString(", "))
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

@Composable
private fun CertField(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontFamily = TactKitMono, style = MaterialTheme.typography.bodySmall)
  }
}
