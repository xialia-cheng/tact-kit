package com.xialia.tactkit.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xialia.tactkit.engine.DocScannerEngine
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.engine.NormalizedQuad
import com.xialia.tactkit.engine.ScanFilter
import com.xialia.tactkit.ui.components.PetalIconBadge
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.util.rememberAppHaptics
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ScannedPage(
  val id: String,
  val originalBitmap: Bitmap,
  val processedBitmap: Bitmap,
  val quad: NormalizedQuad = NormalizedQuad(),
  val filter: ScanFilter = ScanFilter.ENHANCED,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocScannerScreen(
  onRegisterPdfFile: (File, String, String, String) -> Unit,
  onOpenQrStudio: () -> Unit,
  onOpenPdfToolkit: (String) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
    }

  var isCameraMode by remember { mutableStateOf(true) }
  var isTorchOn by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }
  var boundCamera by remember { mutableStateOf<Camera?>(null) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

  val pages = remember { mutableStateListOf<ScannedPage>() }
  var selectedPageIndex by remember { mutableIntStateOf(0) }
  var draggingCornerIndex by remember { mutableIntStateOf(-1) }
  var pendingExportFile by remember { mutableStateOf<File?>(null) }

  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  DisposableEffect(Unit) {
    onDispose { cameraExecutor.shutdown() }
  }

  val pickPhotosLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(16)) { uris ->
      if (uris.isNotEmpty()) {
        scope.launch {
          isBusy = true
          uris.forEach { uri ->
            val bmp = ImageEngine.loadOrientedBitmap(context, uri, maxDimension = 1800)
            if (bmp != null) {
              val quad = NormalizedQuad()
              val enhanced =
                withContext(Dispatchers.Default) {
                  DocScannerEngine.warpPerspectiveAndFilter(bmp, quad, ScanFilter.ENHANCED)
                }
              pages.add(
                ScannedPage(
                  id = "${System.nanoTime()}",
                  originalBitmap = bmp,
                  processedBitmap = enhanced,
                  quad = quad,
                  filter = ScanFilter.ENHANCED,
                )
              )
            }
          }
          if (pages.isNotEmpty()) {
            selectedPageIndex = pages.lastIndex
            isCameraMode = false
          }
          haptics.confirm()
          isBusy = false
        }
      }
    }

  val exportPdfSafLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { outUri ->
      val localFile = pendingExportFile ?: return@rememberLauncherForActivityResult
      pendingExportFile = null
      if (outUri != null) {
        scope.launch {
          runCatching { DocScannerEngine.copyFileToSafUri(context, localFile, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "扫描 PDF 已导出保存", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
      }
    }

  fun captureFromCamera() {
    val capture = imageCapture ?: return
    isBusy = true
    val tempPhoto = File.createTempFile("doc_scan_raw_", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempPhoto).build()
    capture.takePicture(
      outputOptions,
      cameraExecutor,
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
          scope.launch {
            val bmp = ImageEngine.loadOrientedBitmap(context, Uri.fromFile(tempPhoto), 1800)
            tempPhoto.delete()
            if (bmp != null) {
              val quad = NormalizedQuad()
              val processed =
                withContext(Dispatchers.Default) {
                  DocScannerEngine.warpPerspectiveAndFilter(bmp, quad, ScanFilter.ENHANCED)
                }
              pages.add(
                ScannedPage(
                  id = "${System.nanoTime()}",
                  originalBitmap = bmp,
                  processedBitmap = processed,
                  quad = quad,
                  filter = ScanFilter.ENHANCED,
                )
              )
              selectedPageIndex = pages.lastIndex
              haptics.confirm()
            }
            isBusy = false
          }
        }

        override fun onError(exception: ImageCaptureException) {
          tempPhoto.delete()
          scope.launch {
            isBusy = false
            Toast.makeText(context, "拍照失败：${exception.message}", Toast.LENGTH_SHORT).show()
          }
        }
      },
    )
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
          title = { Text("文档扫描仪", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(
              onClick = {
                haptics.tick()
                onNavigateBack()
              },
              modifier = Modifier.testTag("scanner_back_btn"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            if (isCameraMode && boundCamera != null) {
              IconButton(
                onClick = {
                  isTorchOn = !isTorchOn
                  boundCamera?.cameraControl?.enableTorch(isTorchOn)
                },
              ) {
                Icon(
                  imageVector = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                  contentDescription = "切换补光灯",
                )
              }
            }
            IconButton(
              onClick = {
                haptics.tick()
                onOpenQrStudio()
              },
            ) {
              Icon(Icons.Rounded.QrCodeScanner, contentDescription = "前往扫码")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          FilterChip(
            selected = isCameraMode,
            onClick = { isCameraMode = true },
            label = { Text("连续拍摄", maxLines = 1, softWrap = false) },
            modifier = Modifier.testTag("scanner_mode_camera"),
          )
          FilterChip(
            selected = !isCameraMode,
            enabled = pages.isNotEmpty(),
            onClick = { isCameraMode = false },
            label = { Text("透视校色 (${pages.size})", maxLines = 1, softWrap = false) },
            modifier = Modifier.testTag("scanner_mode_edit"),
          )
          Spacer(modifier = Modifier.weight(1f))
          FilledTonalButton(
            onClick = {
              pickPhotosLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("相册选图", maxLines = 1, softWrap = false)
          }
        }

        if (isCameraMode) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            if (hasCameraPermission) {
              AndroidView(
                factory = { ctx ->
                  val previewView = PreviewView(ctx)
                  val providerFuture = ProcessCameraProvider.getInstance(ctx)
                  providerFuture.addListener(
                    {
                      val provider = runCatching { providerFuture.get() }.getOrNull()
                        ?: return@addListener
                      val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                      }
                      val capture =
                        ImageCapture.Builder()
                          .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                          .build()
                      imageCapture = capture
                      runCatching {
                        provider.unbindAll()
                        boundCamera =
                          provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                          )
                      }
                    },
                    ContextCompat.getMainExecutor(ctx),
                  )
                  previewView
                },
                modifier = Modifier.fillMaxSize(),
              )

              // Framing corner guide
              Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = size.width * 0.08f
                val padY = size.height * 0.08f
                drawRect(
                  color = Color.White.copy(alpha = 0.55f),
                  topLeft = Offset(padX, padY),
                  size = androidx.compose.ui.geometry.Size(size.width - padX * 2, size.height - padY * 2),
                  style = Stroke(width = 3f),
                )
              }
            } else {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
              ) {
                PetalIconBadge(icon = Icons.Rounded.DocumentScanner, size = 64.dp)
                Text("需要相机权限以拍摄纸质文档", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "也可直接点击右上「相册选图」导入现有文档照片。",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TactileActionButton(
                  onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                  modifier = Modifier.testTag("scanner_grant_camera_btn"),
                ) {
                  Text("授权相机拍摄")
                }
              }
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            TactileActionButton(
              enabled = hasCameraPermission && !isBusy,
              onClick = { captureFromCamera() },
              modifier = Modifier.weight(1f).testTag("scanner_shutter_btn"),
            ) {
              Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("拍摄一页 (${pages.size})")
            }

            if (pages.isNotEmpty()) {
              FilledTonalButton(
                onClick = { isCameraMode = false },
                shape = RoundedCornerShape(16.dp),
              ) {
                Text("整理与生成 PDF")
              }
            }
          }
        } else {
          val activePage = pages.getOrNull(selectedPageIndex)
          if (activePage != null) {
            // 4-Corner Perspective Crop Preview Canvas
            val ratio =
              (activePage.originalBitmap.width.toFloat() /
                  activePage.originalBitmap.height.coerceAtLeast(1))
                .coerceIn(0.4f, 2.2f)
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(22.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerLow)
                  .padding(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(activePage.id, selectedPageIndex) {
                      detectDragGestures(
                        onDragStart = { start ->
                          val corners = pages[selectedPageIndex].quad.corners()
                          val nx = start.x / size.width
                          val ny = start.y / size.height
                          var bestIdx = 0
                          var bestDist = Float.MAX_VALUE
                          corners.forEachIndexed { idx, (cx, cy) ->
                            val d = hypot(cx - nx, cy - ny)
                            if (d < bestDist) {
                              bestDist = d
                              bestIdx = idx
                            }
                          }
                          draggingCornerIndex = bestIdx
                        },
                        onDrag = { change, _ ->
                          change.consume()
                          if (draggingCornerIndex in 0..3) {
                            val nx = (change.position.x / size.width).coerceIn(0.01f, 0.99f)
                            val ny = (change.position.y / size.height).coerceIn(0.01f, 0.99f)
                            val cur = pages[selectedPageIndex]
                            pages[selectedPageIndex] =
                              cur.copy(quad = cur.quad.withUpdatedCorner(draggingCornerIndex, nx, ny))
                          }
                        },
                        onDragEnd = {
                          draggingCornerIndex = -1
                          val cur = pages[selectedPageIndex]
                          scope.launch {
                            val warped =
                              withContext(Dispatchers.Default) {
                                DocScannerEngine.warpPerspectiveAndFilter(
                                  cur.originalBitmap,
                                  cur.quad,
                                  cur.filter,
                                )
                              }
                            pages[selectedPageIndex] = cur.copy(processedBitmap = warped)
                          }
                        },
                        onDragCancel = { draggingCornerIndex = -1 },
                      )
                    },
              ) {
                Image(
                  bitmap = activePage.originalBitmap.asImageBitmap(),
                  contentDescription = "文档四角透视调整画布",
                  contentScale = ContentScale.FillBounds,
                  modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val pts =
                    activePage.quad.corners().map { (cx, cy) ->
                      Offset(cx * size.width, cy * size.height)
                    }
                  val path =
                    Path().apply {
                      moveTo(pts[0].x, pts[0].y)
                      lineTo(pts[1].x, pts[1].y)
                      lineTo(pts[2].x, pts[2].y)
                      lineTo(pts[3].x, pts[3].y)
                      close()
                    }
                  drawPath(path = path, color = primaryColor, style = Stroke(width = 5f))
                  pts.forEach { pt ->
                    drawCircle(color = Color.White, radius = 16f, center = pt)
                    drawCircle(color = primaryColor, radius = 11f, center = pt)
                  }
                }
              }
            }

            // Filter chips + Reorder + Export Panel
            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp),
              ) {
                // Page thumbnails + reorder
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  itemsIndexed(pages, key = { _, p -> p.id }) { idx, page ->
                    val selected = idx == selectedPageIndex
                    Box(
                      modifier =
                        Modifier.size(width = 56.dp, height = 74.dp)
                          .clip(RoundedCornerShape(10.dp))
                          .border(
                            width = if (selected) 2.dp else 1.dp,
                            color =
                              if (selected) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp),
                          )
                          .clickable { selectedPageIndex = idx },
                    ) {
                      Image(
                        bitmap = page.processedBitmap.asImageBitmap(),
                        contentDescription = "第 ${idx + 1} 页",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                      Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                          Modifier.align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                      ) {
                        Text(
                          text = "${idx + 1}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onPrimary,
                        )
                      }
                    }
                  }
                }

                // Filters & page controls
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  ScanFilter.entries.forEach { filter ->
                    FilterChip(
                      selected = activePage.filter == filter,
                      onClick = {
                        haptics.tick()
                        scope.launch {
                          val warped =
                            withContext(Dispatchers.Default) {
                              DocScannerEngine.warpPerspectiveAndFilter(
                                activePage.originalBitmap,
                                activePage.quad,
                                filter,
                              )
                            }
                          pages[selectedPageIndex] =
                            activePage.copy(filter = filter, processedBitmap = warped)
                        }
                      },
                      label = { Text(filter.label, maxLines = 1, softWrap = false) },
                    )
                  }
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                      enabled = selectedPageIndex > 0,
                      onClick = {
                        val item = pages.removeAt(selectedPageIndex)
                        selectedPageIndex -= 1
                        pages.add(selectedPageIndex, item)
                      },
                    ) {
                      Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "前移一页")
                    }
                    IconButton(
                      enabled = selectedPageIndex < pages.lastIndex,
                      onClick = {
                        val item = pages.removeAt(selectedPageIndex)
                        selectedPageIndex += 1
                        pages.add(selectedPageIndex, item)
                      },
                    ) {
                      Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "后移一页")
                    }
                    IconButton(
                      onClick = {
                        pages.removeAt(selectedPageIndex)
                        if (pages.isEmpty()) {
                          isCameraMode = true
                        } else {
                          selectedPageIndex = selectedPageIndex.coerceAtMost(pages.lastIndex)
                        }
                      },
                    ) {
                      Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除此页")
                    }
                    IconButton(
                      onClick = {
                        scope.launch {
                          val warped =
                            withContext(Dispatchers.Default) {
                              DocScannerEngine.warpPerspectiveAndFilter(
                                activePage.originalBitmap,
                                activePage.quad,
                                activePage.filter,
                              )
                            }
                          pages[selectedPageIndex] = activePage.copy(processedBitmap = warped)
                          haptics.confirm()
                          Toast.makeText(context, "已应用透视校正与滤镜", Toast.LENGTH_SHORT).show()
                        }
                      },
                    ) {
                      Icon(Icons.Rounded.AutoFixHigh, contentDescription = "重新透视校正")
                    }
                  }

                  Button(
                    enabled = !isBusy && pages.isNotEmpty(),
                    onClick = {
                      scope.launch {
                        isBusy = true
                        runCatching {
                          val outDir = File(context.filesDir, "scanned_docs")
                          val outFile = File(outDir, "TactKit_Scan_${System.currentTimeMillis()}.pdf")
                          DocScannerEngine.buildMultiPagePdfToFile(
                            pages = pages.map { it.processedBitmap },
                            destFile = outFile,
                          )
                          val fileUri =
                            FileProvider.getUriForFile(
                              context,
                              "${context.packageName}.fileprovider",
                              outFile,
                            )
                          onRegisterPdfFile(
                            outFile,
                            fileUri.toString(),
                            "application/pdf",
                            "文档扫描合成 (${pages.size} 页)",
                          )
                          pendingExportFile = outFile
                          fileUri
                        }.onSuccess {
                          haptics.confirm()
                          exportPdfSafLauncher.launch("TactKit_Scan_${System.currentTimeMillis()}.pdf")
                        }.onFailure {
                          Toast.makeText(context, "合成失败：${it.message}", Toast.LENGTH_LONG).show()
                        }
                        isBusy = false
                      }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("scanner_export_pdf_btn"),
                  ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("合成 PDF")
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
