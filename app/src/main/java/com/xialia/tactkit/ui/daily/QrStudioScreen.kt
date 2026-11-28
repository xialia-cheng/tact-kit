package com.xialia.tactkit.ui.daily

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SaveAlt
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xialia.tactkit.engine.QrCodeEngine
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.TactileModeChip
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class QrTab(val label: String) {
  GENERATE("生成二维码"),
  SCAN("扫码与相册识别"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrStudioScreen(
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var activeTab by remember { mutableStateOf(QrTab.GENERATE) }
  var qrInput by remember { mutableStateOf("https://github.com/xialia-cheng/tact-kit") }
  var scannedResult by remember { mutableStateOf<String?>(null) }
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
      if (!granted) {
        Toast.makeText(context, "未授予相机权限，可使用下方相册图片识别", Toast.LENGTH_SHORT).show()
      }
    }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val decoded =
            withContext(Dispatchers.IO) {
              runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                  val bmp = BitmapFactory.decodeStream(stream) ?: return@use null
                  QrCodeEngine.decodeBitmap(bmp)
                }
              }.getOrNull()
            }
          if (decoded != null) {
            haptics.confirm()
            scannedResult = decoded
          } else {
            Toast.makeText(context, "未在所选图片中识别到有效二维码", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }

  val generatedBitmap: Bitmap? =
    remember(qrInput) {
      if (qrInput.isBlank()) null
      else runCatching { QrCodeEngine.generateQrBitmap(qrInput, sizePx = 720) }.getOrNull()
    }

  val exportPngLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { outUri ->
      val bmp = generatedBitmap ?: return@rememberLauncherForActivityResult
      if (outUri != null) {
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              context.contentResolver.openOutputStream(outUri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
              }
            }
          }.onSuccess {
            com.xialia.tactkit.data.repository.FileCacheRepository.markDirty()
            haptics.confirm()
            Toast.makeText(context, "二维码图片已保存", Toast.LENGTH_SHORT).show()
          }
        }
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
              "二维码工坊",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("qr_studio"),
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("qr_back_button"),
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
        item(key = "qr_tabs") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QrTab.entries, key = { it.name }) { tab ->
              TactileModeChip(
                selected = activeTab == tab,
                label = tab.label,
                onClick = { activeTab = tab },
                modifier = Modifier.testTag("qr_tab_${tab.name}"),
              )
            }
          }
        }

        if (activeTab == QrTab.GENERATE) {
          item(key = "qr_generator_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  PetalIconBadge(icon = Icons.Rounded.QrCode2, size = 44.dp)
                  Text("纯离线高清二维码生成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                  value = qrInput,
                  onValueChange = { qrInput = it },
                  label = { Text("链接、文本或 Wi-Fi 配置") },
                  minLines = 2,
                  maxLines = 5,
                  textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("qr_content_input"),
                )

                generatedBitmap?.let { bmp ->
                  Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                  ) {
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = "生成的二维码",
                      modifier = Modifier.size(220.dp).padding(12.dp),
                    )
                  }
                }

                TactileActionButton(
                  enabled = generatedBitmap != null,
                  onClick = { exportPngLauncher.launch("tactkit_qr_${System.currentTimeMillis()}.png") },
                  modifier = Modifier.fillMaxWidth().testTag("qr_export_png_btn"),
                ) {
                  Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("保存 PNG 高清二维码")
                }
              }
            }
          }
        } else {
          item(key = "qr_scanner_card") {
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
              ) {
                if (hasCameraPermission) {
                  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                  DisposableEffect(Unit) {
                    onDispose { cameraExecutor.shutdown() }
                  }

                  Box(
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(18.dp)),
                  ) {
                    AndroidView(
                      factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener(
                          {
                            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                              ?: return@addListener
                            val preview =
                              Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                              }
                            val analysis =
                              ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { imageAnalysis ->
                                  imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    try {
                                      val plane = imageProxy.planes.firstOrNull()
                                      if (plane != null) {
                                        val buffer = plane.buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val decoded =
                                          QrCodeEngine.decodeYuvPlane(
                                            yPlane = bytes,
                                            width = imageProxy.width,
                                            height = imageProxy.height,
                                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                                          )
                                        if (!decoded.isNullOrBlank() && decoded != scannedResult) {
                                          previewView.post {
                                            haptics.confirm()
                                            scannedResult = decoded
                                          }
                                        }
                                      }
                                    } finally {
                                      imageProxy.close()
                                    }
                                  }
                                }
                            runCatching {
                              cameraProvider.unbindAll()
                              cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                              )
                            }
                          },
                          ContextCompat.getMainExecutor(ctx),
                        )
                        previewView
                      },
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                } else {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                  ) {
                    PetalIconBadge(icon = Icons.Rounded.QrCodeScanner, size = 64.dp)
                    Text("开启相机权限即可实时扫描二维码", style = MaterialTheme.typography.bodyMedium)
                    TactileActionButton(
                      onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                      modifier = Modifier.fillMaxWidth().testTag("qr_request_cam_btn"),
                    ) {
                      Text("授权相机实时扫码")
                    }
                  }
                }

                TactileActionButton(
                  onClick = {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.fillMaxWidth().testTag("qr_pick_photo_btn"),
                ) {
                  Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("从相册图片识别二维码")
                }

                scannedResult?.let { text ->
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.padding(14.dp),
                    ) {
                      Text(
                        text = "识别结果",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                      Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = TactKitMono,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("qr_scan_result"),
                      )
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TactileActionButton(
                          onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("QR", text))
                            Toast.makeText(context, "已复制结果", Toast.LENGTH_SHORT).show()
                          },
                          modifier = Modifier.weight(1f),
                        ) {
                          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("复制")
                        }
                        if (text.startsWith("http://", ignoreCase = true) ||
                          text.startsWith("https://", ignoreCase = true)
                        ) {
                          TactileActionButton(
                            onClick = {
                              runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
                              }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                          ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("打开链接")
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
}
