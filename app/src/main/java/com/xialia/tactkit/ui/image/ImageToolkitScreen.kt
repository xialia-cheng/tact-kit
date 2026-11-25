package com.xialia.tactkit.ui.image

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.engine.SampledPixelColor
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class ImageToolTab(val label: String) {
  MOSAIC_WATERMARK("马赛克水印"),
  CROP_COMPRESS("裁剪压缩"),
  STITCH_PDF("长图与 PDF"),
  COLOR_PICKER("像素取色"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolkitScreen(
  initialUriString: String?,
  initialTabName: String? = null,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var currentUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var workingBitmap by remember { mutableStateOf<Bitmap?>(null) }
  val undoStack = remember { mutableStateListOf<Bitmap>() }
  var exifSummary by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var activeTab by remember(initialTabName) {
    mutableStateOf(
      ImageToolTab.entries.firstOrNull { it.name == initialTabName } ?: ImageToolTab.MOSAIC_WATERMARK
    )
  }
  var isBusy by remember { mutableStateOf(false) }

  // Mosaic / Zoom preview interaction states
  var isZoomPreviewMode by remember { mutableStateOf(false) }
  var zoomScale by remember { mutableFloatStateOf(1f) }
  var zoomOffset by remember { mutableStateOf(Offset.Zero) }
  var mosaicBlockSize by remember { mutableIntStateOf(24) }
  var mosaicStart by remember { mutableStateOf<Offset?>(null) }
  var mosaicEnd by remember { mutableStateOf<Offset?>(null) }

  // Watermark & EXIF dialogs
  var showWatermarkDialog by remember { mutableStateOf(false) }
  var watermarkText by remember { mutableStateOf("仅用于本人业务核验 · TactKit") }
  var showExifDialog by remember { mutableStateOf(false) }

  // Compress, Stitch, Color picker states
  var targetKb by remember { mutableFloatStateOf(300f) }
  var useTargetKb by remember { mutableStateOf(false) }
  var sampledColor by remember { mutableStateOf<SampledPixelColor?>(null) }
  var multiImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

  fun pushUndoAndUpdate(nextBitmap: Bitmap) {
    workingBitmap?.let { prev ->
      if (undoStack.size >= 8) undoStack.removeAt(0)
      undoStack.add(prev)
    }
    workingBitmap = nextBitmap
  }

  val pickSingleImage =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        currentUri = uri
      }
    }

  val pickMultipleImages =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
      if (uris.isNotEmpty()) {
        multiImageUris = uris
        if (currentUri == null) currentUri = uris.first()
      }
    }

  val saveImageLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { outUri ->
      val bmp = workingBitmap
      if (outUri != null && bmp != null) {
        scope.launch {
          isBusy = true
          runCatching {
            ImageEngine.saveCleanBitmap(
              context = context,
              bitmap = bmp,
              outputUri = outUri,
              targetKb = if (useTargetKb) targetKb.roundToInt() else null,
            )
          }.onSuccess {
            haptics.confirm()
            Toast.makeText(context, "已剥离 EXIF 并保存图片", Toast.LENGTH_SHORT).show()
          }.onFailure {
            Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
          }
          isBusy = false
        }
      }
    }

  val saveImagesToPdfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { outUri ->
      val targets = multiImageUris.ifEmpty { listOfNotNull(currentUri) }
      if (outUri != null && targets.isNotEmpty()) {
        scope.launch {
          isBusy = true
          runCatching { PdfEngine.imagesToPdf(context, targets, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已将 ${targets.size} 张图片合成为 PDF", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "转换失败：${it.message}", Toast.LENGTH_LONG).show()
            }
          isBusy = false
        }
      }
    }

  LaunchedEffect(currentUri) {
    val uri = currentUri ?: return@LaunchedEffect
    isBusy = true
    undoStack.clear()
    zoomScale = 1f
    zoomOffset = Offset.Zero
    runCatching {
      workingBitmap = ImageEngine.loadOrientedBitmap(context, uri)
      exifSummary = ImageEngine.readExifSummary(context, uri)
      sampledColor = null
    }
    isBusy = false
  }

  if (currentUri == null) {
    ExpressiveEmptyFileScreen(
      title = "图片隐私与工坊",
      icon = Icons.Rounded.HideImage,
      onPickFile = {
        haptics.tick()
        pickSingleImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      onNavigateBack = onNavigateBack,
      fabText = "选择图片",
    )
    return
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
          title = { Text("图片隐私与工坊", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                pickSingleImage.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
            ) {
              Icon(Icons.Rounded.PhotoLibrary, contentDescription = "重新选图")
            }
          },
        )
      },
    ) { innerPadding ->
      // Full-screen fixed 3-part layout (zero outer verticalScroll -> zero mosaic mis-touch)
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        // Clean full-label tab strip
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
          ImageToolTab.entries.forEach { tab ->
            FilterChip(
              selected = activeTab == tab,
              onClick = {
                haptics.tick()
                activeTab = tab
              },
              label = { Text(tab.label, maxLines = 1, softWrap = false) },
              modifier = Modifier.testTag("image_tab_${tab.name}"),
            )
          }
        }

        // Middle Adaptive Image Canvas (weight(1f))
        val bmp = workingBitmap
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .padding(10.dp),
        ) {
          if (bmp != null) {
            val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4f)
            Box(
              modifier =
                Modifier.aspectRatio(ratio)
                  .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomOffset.x
                    translationY = zoomOffset.y
                  }
                  .clip(RoundedCornerShape(14.dp))
                  .pointerInput(activeTab, bmp, isZoomPreviewMode, mosaicBlockSize) {
                    if (isZoomPreviewMode) {
                      detectTransformGestures { _, pan, zoom, _ ->
                        val next = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = next
                        zoomOffset = if (next > 1.02f) zoomOffset + pan else Offset.Zero
                      }
                    } else if (activeTab == ImageToolTab.MOSAIC_WATERMARK) {
                      detectDragGestures(
                        onDragStart = { start ->
                          mosaicStart =
                            Offset(
                              (start.x / size.width).coerceIn(0f, 1f),
                              (start.y / size.height).coerceIn(0f, 1f),
                            )
                          mosaicEnd = mosaicStart
                        },
                        onDrag = { change, _ ->
                          change.consume()
                          mosaicEnd =
                            Offset(
                              (change.position.x / size.width).coerceIn(0f, 1f),
                              (change.position.y / size.height).coerceIn(0f, 1f),
                            )
                        },
                        onDragEnd = {
                          val s = mosaicStart
                          val e = mosaicEnd
                          if (s != null && e != null) {
                            pushUndoAndUpdate(
                              ImageEngine.applyMosaic(
                                bmp,
                                min(s.x, e.x),
                                min(s.y, e.y),
                                max(s.x, e.x),
                                max(s.y, e.y),
                                blockSizePx = mosaicBlockSize,
                              )
                            )
                            haptics.confirm()
                          }
                          mosaicStart = null
                          mosaicEnd = null
                        },
                        onDragCancel = {
                          mosaicStart = null
                          mosaicEnd = null
                        },
                      )
                    } else if (activeTab == ImageToolTab.COLOR_PICKER) {
                      detectTapGestures { tap ->
                        val nx = (tap.x / size.width).coerceIn(0f, 1f)
                        val ny = (tap.y / size.height).coerceIn(0f, 1f)
                        sampledColor = ImageEngine.samplePixelColor(bmp, nx, ny)
                        haptics.tick()
                      }
                    }
                  },
            ) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "图片画布预览",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
              )
              if (mosaicStart != null && mosaicEnd != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val s = mosaicStart!!
                  val e = mosaicEnd!!
                  val left = min(s.x, e.x) * size.width
                  val top = min(s.y, e.y) * size.height
                  val w = (max(s.x, e.x) - min(s.x, e.x)) * size.width
                  val h = (max(s.y, e.y) - min(s.y, e.y)) * size.height
                  drawRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                  )
                  drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 3f),
                  )
                }
              }
            }

            // Floating Mode Toggle Pill (涂抹 / 缩放预览) + Resolution Chip
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text(
                  text = "${bmp.width}×${bmp.height}",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = TactKitMono),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
              }
              FilterChip(
                selected = isZoomPreviewMode,
                onClick = {
                  haptics.tick()
                  isZoomPreviewMode = !isZoomPreviewMode
                  if (!isZoomPreviewMode && zoomScale <= 1.01f) {
                    zoomOffset = Offset.Zero
                  }
                },
                leadingIcon = {
                  Icon(
                    imageVector = if (isZoomPreviewMode) Icons.Rounded.Pinch else Icons.Rounded.Draw,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = { Text(if (isZoomPreviewMode) "缩放预览" else "涂抹/操作") },
                modifier = Modifier.testTag("image_zoom_mode_toggle"),
              )
            }
          }
        }

        // Fixed Bottom Persistent Action Panel (always visible without scrolling)
        if (bmp != null) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(12.dp),
            ) {
              when (activeTab) {
                ImageToolTab.MOSAIC_WATERMARK -> {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  ) {
                    listOf(14 to "细码", 24 to "中码", 40 to "粗码").forEach { (px, label) ->
                      FilterChip(
                        selected = mosaicBlockSize == px,
                        onClick = {
                          haptics.tick()
                          mosaicBlockSize = px
                        },
                        label = { Text(label) },
                      )
                    }
                    FilledTonalButton(
                      onClick = { showWatermarkDialog = true },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.BrandingWatermark, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("水印")
                    }
                    FilledTonalButton(
                      onClick = { showExifDialog = true },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("EXIF(${exifSummary.size})")
                    }
                  }
                }

                ImageToolTab.CROP_COMPRESS -> {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  ) {
                    FilledTonalButton(
                      onClick = {
                        haptics.tick()
                        pushUndoAndUpdate(ImageEngine.rotate90(bmp))
                      },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("旋转90°")
                    }
                    FilledTonalButton(
                      onClick = {
                        haptics.tick()
                        pushUndoAndUpdate(ImageEngine.cropNormalized(bmp, 0.1f, 0.1f, 0.9f, 0.9f))
                      },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("裁切80%")
                    }
                    FilterChip(
                      selected = useTargetKb,
                      onClick = { useTargetKb = !useTargetKb },
                      label = { Text("限重 ${targetKb.roundToInt()}KB") },
                    )
                    FilledTonalButton(
                      onClick = { showExifDialog = true },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("EXIF(${exifSummary.size})")
                    }
                  }
                  if (useTargetKb) {
                    Slider(
                      value = targetKb,
                      onValueChange = { targetKb = it },
                      valueRange = 50f..1500f,
                    )
                  }
                }

                ImageToolTab.STITCH_PDF -> {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  ) {
                    FilledTonalButton(
                      onClick = {
                        pickMultipleImages.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("多选(${multiImageUris.size.coerceAtLeast(1)})")
                    }
                    FilledTonalButton(
                      enabled = multiImageUris.size >= 2,
                      onClick = {
                        scope.launch {
                          isBusy = true
                          val loaded =
                            multiImageUris.mapNotNull {
                              ImageEngine.loadOrientedBitmap(context, it, maxDimension = 1280)
                            }
                          ImageEngine.stitchVertical(loaded)?.let { stitched ->
                            pushUndoAndUpdate(stitched)
                            haptics.confirm()
                            Toast.makeText(context, "长图拼接完成", Toast.LENGTH_SHORT).show()
                          }
                          isBusy = false
                        }
                      },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.ViewColumn, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("拼长图")
                    }
                    FilledTonalButton(
                      onClick = { saveImagesToPdfLauncher.launch("TactKit_Images.pdf") },
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("转 PDF")
                    }
                  }
                }

                ImageToolTab.COLOR_PICKER -> {
                  val color = sampledColor
                  if (color == null) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(Icons.Rounded.Colorize, contentDescription = null, modifier = Modifier.size(18.dp))
                      Text(
                        text = if (isZoomPreviewMode) "切回「涂抹/操作」即可点击取色" else "点击上方图片任意位置取色",
                        style = MaterialTheme.typography.bodySmall,
                      )
                    }
                  } else {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(color.argb))
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = color.hex,
                          style = MaterialTheme.typography.titleSmall.copy(fontFamily = TactKitMono),
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = "RGB(${color.r}, ${color.g}, ${color.b})",
                          style = MaterialTheme.typography.labelSmall.copy(fontFamily = TactKitMono),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      FilledTonalButton(
                        onClick = {
                          val cm =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                          cm.setPrimaryClip(ClipData.newPlainText("HEX", color.hex))
                          haptics.confirm()
                          Toast.makeText(context, "已复制 ${color.hex}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                      ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制 HEX")
                      }
                    }
                  }
                }
              }

              // Common Bottom Bar: Undo + Save Clean Export
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                FilledTonalButton(
                  enabled = undoStack.isNotEmpty(),
                  onClick = {
                    val prev = undoStack.removeLastOrNull()
                    if (prev != null) {
                      workingBitmap = prev
                      haptics.tick()
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.testTag("image_undo_btn"),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("撤销")
                }

                Button(
                  onClick = {
                    haptics.tick()
                    saveImageLauncher.launch("TactKit_Clean_${System.currentTimeMillis()}.jpg")
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f).testTag("image_save_clean_btn"),
                ) {
                  Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "剥离 EXIF 并保存导出",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showWatermarkDialog && workingBitmap != null) {
    AlertDialog(
      onDismissRequest = { showWatermarkDialog = false },
      title = { Text("添加防盗斜向水印") },
      text = {
        OutlinedTextField(
          value = watermarkText,
          onValueChange = { watermarkText = it },
          label = { Text("水印文字") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            workingBitmap?.let { pushUndoAndUpdate(ImageEngine.applyTextWatermark(it, watermarkText)) }
            haptics.confirm()
            showWatermarkDialog = false
          }
        ) {
          Text("应用水印")
        }
      },
      dismissButton = {
        TextButton(onClick = { showWatermarkDialog = false }) { Text("取消") }
      },
    )
  }

  if (showExifDialog) {
    AlertDialog(
      onDismissRequest = { showExifDialog = false },
      title = { Text("EXIF 隐私元数据检测") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (exifSummary.isEmpty()) {
            Text("未检测到敏感设备型号、拍摄时间或 GPS 定位信息。")
          } else {
            Text(
              text = "检测到 ${exifSummary.size} 项元数据，点击「剥离 EXIF 并保存导出」将自动清空：",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exifSummary.forEach { (k, v) ->
              Text("$k：$v", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showExifDialog = false }) { Text("知道了") }
      },
    )
  }
}
