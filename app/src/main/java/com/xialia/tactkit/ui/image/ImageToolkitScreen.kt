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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.engine.SampledPixelColor
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.tactileClickable
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ImageToolTab(val label: String) {
  MOSAIC_WATERMARK("马赛克水印"),
  CROP_COMPRESS("裁剪压缩"),
  STITCH_PDF("长图与 PDF"),
  COLOR_PICKER("像素取色"),
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
  FREE("自由", null),
  SQUARE("1:1", 1f),
  RATIO_4_3("4:3", 4f / 3f),
  RATIO_16_9("16:9", 16f / 9f),
  RATIO_3_4("3:4", 3f / 4f),
}

private enum class CropHandle {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  TOP,
  BOTTOM,
  LEFT,
  RIGHT,
  MOVE,
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

  // Zoom / Pan & Mosaic interaction states
  var zoomScale by remember { mutableFloatStateOf(1f) }
  var zoomOffset by remember { mutableStateOf(Offset.Zero) }
  var zoomAnimJob by remember { mutableStateOf<Job?>(null) }
  var mosaicBlockSize by remember { mutableIntStateOf(24) }
  var mosaicStart by remember { mutableStateOf<Offset?>(null) }
  var mosaicEnd by remember { mutableStateOf<Offset?>(null) }

  // Free-drag Crop states
  var cropRect by remember { mutableStateOf(Rect(0.08f, 0.08f, 0.92f, 0.92f)) }
  var cropAspect by remember { mutableStateOf(CropAspectRatio.FREE) }
  var cropDirty by remember { mutableStateOf(false) }

  // Watermark & EXIF dialogs
  var showWatermarkDialog by remember { mutableStateOf(false) }
  var watermarkText by remember { mutableStateOf("仅用于本人业务核验 · TactKit") }
  var showExifDialog by remember { mutableStateOf(false) }

  // Compress, Stitch, Color picker states
  var targetKb by remember { mutableFloatStateOf(300f) }
  var useTargetKb by remember { mutableStateOf(false) }
  var sampledColor by remember { mutableStateOf<SampledPixelColor?>(null) }
  var multiImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

  fun resetCropForBitmap(bitmap: Bitmap?, aspect: CropAspectRatio = cropAspect) {
    val base = Rect(0.08f, 0.08f, 0.92f, 0.92f)
    cropRect =
      if (bitmap != null && aspect.ratio != null) {
        val imgAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        fitCropRectToAspect(base, aspect.ratio, imgAspect)
      } else {
        base
      }
    cropDirty = false
  }

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
          val exportBitmap =
            if (activeTab == ImageToolTab.CROP_COMPRESS && cropDirty) {
              ImageEngine.cropNormalized(
                bmp,
                cropRect.left,
                cropRect.top,
                cropRect.right,
                cropRect.bottom,
              ).also { cropped ->
                pushUndoAndUpdate(cropped)
                resetCropForBitmap(cropped)
              }
            } else {
              bmp
            }
          runCatching {
            ImageEngine.saveCleanBitmap(
              context = context,
              bitmap = exportBitmap,
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
    zoomAnimJob?.cancel()
    zoomScale = 1f
    zoomOffset = Offset.Zero
    runCatching {
      val loaded = ImageEngine.loadOrientedBitmap(context, uri)
      workingBitmap = loaded
      resetCropForBitmap(loaded)
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
                  .pointerInput(activeTab, bmp, mosaicBlockSize, cropAspect) {
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero

                    fun toImageLocal(pScreen: Offset, boxSize: IntSize): Offset {
                      val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                      return center + (pScreen - center - zoomOffset) / zoomScale
                    }

                    awaitEachGesture {
                      val firstDown = awaitFirstDown(requireUnconsumed = false)
                      zoomAnimJob?.cancel()

                      var isMultiTouch = false
                      var hasPrevMulti = false
                      var prevCentroid = Offset.Zero
                      var prevSpread = 0f
                      var moved = false
                      val startLocal = toImageLocal(firstDown.position, size)
                      var prevLocal = startLocal
                      var activeCropHandle: CropHandle? =
                        if (activeTab == ImageToolTab.CROP_COMPRESS) {
                          hitTestCropHandle(
                            localPos = startLocal,
                            canvasSize = size,
                            rect = cropRect,
                            hitRadiusPx = (26.dp.toPx() / zoomScale),
                          )
                        } else {
                          null
                        }

                      do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                          if (!isMultiTouch) {
                            isMultiTouch = true
                            mosaicStart = null
                            mosaicEnd = null
                            activeCropHandle = null
                            hasPrevMulti = false
                          }
                          val centroid =
                            pressed.fold(Offset.Zero) { acc, c -> acc + c.position } /
                              pressed.size.toFloat()
                          val spread =
                            pressed
                              .fold(0f) { acc, c -> acc + (c.position - centroid).getDistance() } /
                              pressed.size.toFloat()

                          if (hasPrevMulti) {
                            val zoomChange =
                              if (prevSpread > 8f && spread > 8f) spread / prevSpread else 1f
                            val newScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                            val scaleRatio = newScale / zoomScale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val rawOffset =
                              centroid - center - (prevCentroid - center - zoomOffset) * scaleRatio
                            zoomScale = newScale
                            zoomOffset = clampZoomOffset(rawOffset, newScale, size)
                          }
                          prevCentroid = centroid
                          prevSpread = spread
                          hasPrevMulti = true
                          pressed.forEach { it.consume() }
                        } else if (pressed.size == 1 && !isMultiTouch) {
                          val change = pressed.first()
                          val distFromStart = (change.position - firstDown.position).getDistance()
                          if (!moved && distFromStart > viewConfiguration.touchSlop) {
                            moved = true
                            if (activeTab == ImageToolTab.MOSAIC_WATERMARK) {
                              mosaicStart =
                                Offset(
                                  (startLocal.x / size.width).coerceIn(0f, 1f),
                                  (startLocal.y / size.height).coerceIn(0f, 1f),
                                )
                              mosaicEnd = mosaicStart
                            }
                          }
                          if (moved) {
                            val currLocal = toImageLocal(change.position, size)
                            when (activeTab) {
                              ImageToolTab.MOSAIC_WATERMARK -> {
                                mosaicEnd =
                                  Offset(
                                    (currLocal.x / size.width).coerceIn(0f, 1f),
                                    (currLocal.y / size.height).coerceIn(0f, 1f),
                                  )
                                change.consume()
                              }
                              ImageToolTab.CROP_COMPRESS -> {
                                val handle = activeCropHandle
                                if (handle != null) {
                                  val dxNorm = (currLocal.x - prevLocal.x) / size.width
                                  val dyNorm = (currLocal.y - prevLocal.y) / size.height
                                  val imgAspect =
                                    bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                                  cropRect =
                                    updateCropRect(
                                      rect = cropRect,
                                      handle = handle,
                                      dxNorm = dxNorm,
                                      dyNorm = dyNorm,
                                      targetRatio = cropAspect.ratio,
                                      imageAspect = imgAspect,
                                    )
                                  cropDirty = true
                                  change.consume()
                                } else if (zoomScale > 1.02f) {
                                  val pan = change.position - change.previousPosition
                                  zoomOffset = clampZoomOffset(zoomOffset + pan, zoomScale, size)
                                  change.consume()
                                }
                              }
                              ImageToolTab.COLOR_PICKER -> {
                                val nx = (currLocal.x / size.width).coerceIn(0f, 1f)
                                val ny = (currLocal.y / size.height).coerceIn(0f, 1f)
                                sampledColor = ImageEngine.samplePixelColor(bmp, nx, ny)
                                change.consume()
                              }
                              ImageToolTab.STITCH_PDF -> {
                                if (zoomScale > 1.02f) {
                                  val pan = change.position - change.previousPosition
                                  zoomOffset = clampZoomOffset(zoomOffset + pan, zoomScale, size)
                                  change.consume()
                                }
                              }
                            }
                            prevLocal = currLocal
                          }
                        }
                      } while (event.changes.any { it.pressed })

                      if (!isMultiTouch && moved && activeTab == ImageToolTab.MOSAIC_WATERMARK) {
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
                      } else if (!isMultiTouch && !moved) {
                        if (activeTab == ImageToolTab.COLOR_PICKER) {
                          val nx = (startLocal.x / size.width).coerceIn(0f, 1f)
                          val ny = (startLocal.y / size.height).coerceIn(0f, 1f)
                          sampledColor = ImageEngine.samplePixelColor(bmp, nx, ny)
                          haptics.tick()
                        }
                        val now = firstDown.uptimeMillis
                        val isDoubleTap =
                          now - lastTapTime < 280L &&
                            (firstDown.position - lastTapPos).getDistance() <
                              viewConfiguration.touchSlop * 4f
                        if (isDoubleTap) {
                          lastTapTime = 0L
                          val startScale = zoomScale
                          val startOffset = zoomOffset
                          val targetScale = if (startScale > 1.05f) 1f else 2.5f
                          val center = Offset(size.width / 2f, size.height / 2f)
                          val targetOffset =
                            if (targetScale <= 1.05f) {
                              Offset.Zero
                            } else {
                              clampZoomOffset(
                                (center - firstDown.position) * (targetScale - 1f),
                                targetScale,
                                size,
                              )
                            }
                          haptics.tick()
                          zoomAnimJob =
                            scope.launch {
                              animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec =
                                  spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                  ),
                              ) { progress, _ ->
                                zoomScale = startScale + (targetScale - startScale) * progress
                                zoomOffset =
                                  startOffset + (targetOffset - startOffset) * progress
                              }
                            }
                        } else {
                          lastTapTime = now
                          lastTapPos = firstDown.position
                        }
                      }
                    }
                  },
            ) {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .graphicsLayer {
                      scaleX = zoomScale
                      scaleY = zoomScale
                      translationX = zoomOffset.x
                      translationY = zoomOffset.y
                    }
                    .clip(RoundedCornerShape(14.dp)),
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
                if (activeTab == ImageToolTab.CROP_COMPRESS) {
                  Canvas(modifier = Modifier.fillMaxSize()) {
                    val l = cropRect.left * size.width
                    val t = cropRect.top * size.height
                    val r = cropRect.right * size.width
                    val b = cropRect.bottom * size.height
                    val w = (r - l).coerceAtLeast(1f)
                    val h = (b - t).coerceAtLeast(1f)

                    // Outer semi-transparent dark mask
                    val maskColor = Color.Black.copy(alpha = 0.56f)
                    drawRect(maskColor, Offset.Zero, Size(size.width, t))
                    drawRect(maskColor, Offset(0f, b), Size(size.width, size.height - b))
                    drawRect(maskColor, Offset(0f, t), Size(l, h))
                    drawRect(maskColor, Offset(r, t), Size(size.width - r, h))

                    // Rule-of-thirds grid lines
                    val gridColor = Color.White.copy(alpha = 0.45f)
                    val gridStroke = 1.dp.toPx() / zoomScale
                    drawLine(gridColor, Offset(l + w / 3f, t), Offset(l + w / 3f, b), gridStroke)
                    drawLine(
                      gridColor,
                      Offset(l + 2f * w / 3f, t),
                      Offset(l + 2f * w / 3f, b),
                      gridStroke,
                    )
                    drawLine(gridColor, Offset(l, t + h / 3f), Offset(r, t + h / 3f), gridStroke)
                    drawLine(
                      gridColor,
                      Offset(l, t + 2f * h / 3f),
                      Offset(r, t + 2f * h / 3f),
                      gridStroke,
                    )

                    // Crop box border
                    drawRect(
                      color = Color.White,
                      topLeft = Offset(l, t),
                      size = Size(w, h),
                      style = Stroke(width = 2.dp.toPx() / zoomScale),
                    )

                    // Corner L-handles & edge midpoint bars
                    val handleLen = min(20.dp.toPx() / zoomScale, min(w, h) * 0.3f)
                    val handleStroke = 3.5.dp.toPx() / zoomScale
                    val edgeLen = min(18.dp.toPx() / zoomScale, min(w, h) * 0.28f)
                    // Top-left
                    drawLine(Color.White, Offset(l, t), Offset(l + handleLen, t), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(l, t), Offset(l, t + handleLen), handleStroke, StrokeCap.Round)
                    // Top-right
                    drawLine(Color.White, Offset(r, t), Offset(r - handleLen, t), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(r, t), Offset(r, t + handleLen), handleStroke, StrokeCap.Round)
                    // Bottom-left
                    drawLine(Color.White, Offset(l, b), Offset(l + handleLen, b), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(l, b), Offset(l, b - handleLen), handleStroke, StrokeCap.Round)
                    // Bottom-right
                    drawLine(Color.White, Offset(r, b), Offset(r - handleLen, b), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(r, b), Offset(r, b - handleLen), handleStroke, StrokeCap.Round)
                    // Edge midpoint bars
                    val cx = (l + r) / 2f
                    val cy = (t + b) / 2f
                    drawLine(Color.White, Offset(cx - edgeLen / 2f, t), Offset(cx + edgeLen / 2f, t), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(cx - edgeLen / 2f, b), Offset(cx + edgeLen / 2f, b), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(l, cy - edgeLen / 2f), Offset(l, cy + edgeLen / 2f), handleStroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(r, cy - edgeLen / 2f), Offset(r, cy + edgeLen / 2f), handleStroke, StrokeCap.Round)
                  }
                }
              }
            }

            // Floating Resolution Chip (+ Zoom indicator when zoomed)
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
              if (zoomScale > 1.05f) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Text(
                    text = "${(zoomScale * 10f).roundToInt() / 10f}x",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TactKitMono),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                  )
                }
              }
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
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    listOf(14 to "细码", 24 to "中码", 40 to "粗码").forEach { (px, label) ->
                      val selected = mosaicBlockSize == px
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color =
                          if (selected) MaterialTheme.colorScheme.secondaryContainer
                          else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier =
                          Modifier.weight(1f).height(38.dp).tactileClickable {
                            mosaicBlockSize = px
                          },
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                          )
                        }
                      }
                    }
                    FilledTonalButton(
                      onClick = { showWatermarkDialog = true },
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                      modifier = Modifier.weight(1.15f).height(38.dp),
                    ) {
                      Icon(Icons.Rounded.BrandingWatermark, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(3.dp))
                      Text("水印", maxLines = 1, softWrap = false, fontSize = 12.sp)
                    }
                    FilledTonalButton(
                      onClick = { showExifDialog = true },
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                      modifier = Modifier.weight(1.35f).height(38.dp),
                    ) {
                      Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(3.dp))
                      Text("EXIF(${exifSummary.size})", maxLines = 1, softWrap = false, fontSize = 12.sp)
                    }
                  }
                }

                ImageToolTab.CROP_COMPRESS -> {
                  // Aspect ratio capsules
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    CropAspectRatio.entries.forEach { aspect ->
                      val selected = cropAspect == aspect
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color =
                          if (selected) MaterialTheme.colorScheme.secondaryContainer
                          else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border =
                          if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                          else null,
                        modifier =
                          Modifier.weight(1f).height(32.dp).tactileClickable {
                            cropAspect = aspect
                            val imgAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                            cropRect = fitCropRectToAspect(cropRect, aspect.ratio, imgAspect)
                            cropDirty = true
                          },
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Text(
                            text = aspect.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                          )
                        }
                      }
                    }
                  }

                  // Evenly distributed 4-action bar (never overflows on right edge)
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    CompactActionTile(
                      icon = Icons.AutoMirrored.Rounded.RotateRight,
                      label = "旋转90°",
                      selected = false,
                      onClick = {
                        haptics.tick()
                        val rotated = ImageEngine.rotate90(bmp)
                        pushUndoAndUpdate(rotated)
                        resetCropForBitmap(rotated)
                      },
                      modifier = Modifier.weight(1f),
                    )
                    CompactActionTile(
                      icon = Icons.Rounded.Crop,
                      label = "确认裁剪",
                      selected = cropDirty,
                      onClick = {
                        haptics.confirm()
                        val cropped =
                          ImageEngine.cropNormalized(
                            bmp,
                            cropRect.left,
                            cropRect.top,
                            cropRect.right,
                            cropRect.bottom,
                          )
                        pushUndoAndUpdate(cropped)
                        resetCropForBitmap(cropped)
                      },
                      modifier = Modifier.weight(1f),
                    )
                    CompactActionTile(
                      icon = Icons.Rounded.Tune,
                      label = "压缩上限",
                      selected = useTargetKb,
                      onClick = {
                        haptics.tick()
                        useTargetKb = !useTargetKb
                      },
                      modifier = Modifier.weight(1f),
                    )
                    CompactActionTile(
                      icon = Icons.Rounded.Security,
                      label = "EXIF(${exifSummary.size})",
                      selected = false,
                      onClick = {
                        haptics.tick()
                        showExifDialog = true
                      },
                      modifier = Modifier.weight(1f),
                    )
                  }

                  if (useTargetKb) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "压缩上限 ${targetKb.roundToInt()}KB",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TactKitMono),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                      )
                      Slider(
                        value = targetKb,
                        onValueChange = { targetKb = it },
                        valueRange = 50f..1500f,
                        modifier = Modifier.weight(1f),
                      )
                    }
                  }
                }

                ImageToolTab.STITCH_PDF -> {
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    FilledTonalButton(
                      onClick = {
                        pickMultipleImages.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      },
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                      modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                      Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("多选(${multiImageUris.size.coerceAtLeast(1)})", maxLines = 1, softWrap = false, fontSize = 12.sp)
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
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                      modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                      Icon(Icons.Rounded.ViewColumn, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("拼长图", maxLines = 1, softWrap = false, fontSize = 12.sp)
                    }
                    FilledTonalButton(
                      onClick = { saveImagesToPdfLauncher.launch("TactKit_Images.pdf") },
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                      modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                      Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("转 PDF", maxLines = 1, softWrap = false, fontSize = 12.sp)
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
                        text = "单指点击图片取色，双指捏合缩放/平移",
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
                      resetCropForBitmap(prev)
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

@Composable
private fun CompactActionTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) MaterialTheme.colorScheme.secondaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHighest,
    border =
      if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
      else null,
    modifier = modifier.height(52.dp).tactileClickable(onClick = onClick),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint =
          if (selected) MaterialTheme.colorScheme.onSecondaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(17.dp),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color =
          if (selected) MaterialTheme.colorScheme.onSecondaryContainer
          else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun clampZoomOffset(offset: Offset, scale: Float, boxSize: IntSize): Offset {
  val maxX = ((boxSize.width * scale - boxSize.width) / 2f).coerceAtLeast(0f)
  val maxY = ((boxSize.height * scale - boxSize.height) / 2f).coerceAtLeast(0f)
  return Offset(
    offset.x.coerceIn(-maxX, maxX),
    offset.y.coerceIn(-maxY, maxY),
  )
}

private fun fitCropRectToAspect(current: Rect, targetRatio: Float?, imageAspect: Float): Rect {
  if (targetRatio == null) return current
  val normRatio = (targetRatio / imageAspect.coerceAtLeast(0.01f)).coerceIn(0.05f, 20f)
  val cx = (current.left + current.right) / 2f
  val cy = (current.top + current.bottom) / 2f
  var nw = current.width.coerceAtLeast(0.15f)
  var nh = nw / normRatio
  if (nh > 0.88f) {
    nh = 0.88f
    nw = nh * normRatio
  }
  if (nw > 0.88f) {
    nw = 0.88f
    nh = nw / normRatio
  }
  nw = nw.coerceIn(0.08f, 0.96f)
  nh = nh.coerceIn(0.08f, 0.96f)
  var left = cx - nw / 2f
  var top = cy - nh / 2f
  if (left < 0f) left = 0f
  if (top < 0f) top = 0f
  if (left + nw > 1f) left = (1f - nw).coerceAtLeast(0f)
  if (top + nh > 1f) top = (1f - nh).coerceAtLeast(0f)
  return Rect(left, top, (left + nw).coerceAtMost(1f), (top + nh).coerceAtMost(1f))
}

private fun hitTestCropHandle(
  localPos: Offset,
  canvasSize: IntSize,
  rect: Rect,
  hitRadiusPx: Float,
): CropHandle? {
  val l = rect.left * canvasSize.width
  val t = rect.top * canvasSize.height
  val r = rect.right * canvasSize.width
  val b = rect.bottom * canvasSize.height

  val corners =
    listOf(
      CropHandle.TOP_LEFT to Offset(l, t),
      CropHandle.TOP_RIGHT to Offset(r, t),
      CropHandle.BOTTOM_LEFT to Offset(l, b),
      CropHandle.BOTTOM_RIGHT to Offset(r, b),
    )
  val nearestCorner =
    corners
      .map { (handle, pt) -> handle to (localPos - pt).getDistance() }
      .filter { it.second <= hitRadiusPx }
      .minByOrNull { it.second }
  if (nearestCorner != null) return nearestCorner.first

  val edgeRadius = hitRadiusPx * 0.85f
  if (abs(localPos.y - t) <= edgeRadius && localPos.x in (l - edgeRadius)..(r + edgeRadius)) {
    return CropHandle.TOP
  }
  if (abs(localPos.y - b) <= edgeRadius && localPos.x in (l - edgeRadius)..(r + edgeRadius)) {
    return CropHandle.BOTTOM
  }
  if (abs(localPos.x - l) <= edgeRadius && localPos.y in (t - edgeRadius)..(b + edgeRadius)) {
    return CropHandle.LEFT
  }
  if (abs(localPos.x - r) <= edgeRadius && localPos.y in (t - edgeRadius)..(b + edgeRadius)) {
    return CropHandle.RIGHT
  }
  if (localPos.x in l..r && localPos.y in t..b) {
    return CropHandle.MOVE
  }
  return null
}

private fun updateCropRect(
  rect: Rect,
  handle: CropHandle,
  dxNorm: Float,
  dyNorm: Float,
  targetRatio: Float?,
  imageAspect: Float,
): Rect {
  val minSize = 0.08f
  if (handle == CropHandle.MOVE) {
    val w = rect.width
    val h = rect.height
    val newL = (rect.left + dxNorm).coerceIn(0f, (1f - w).coerceAtLeast(0f))
    val newT = (rect.top + dyNorm).coerceIn(0f, (1f - h).coerceAtLeast(0f))
    return Rect(newL, newT, newL + w, newT + h)
  }

  if (targetRatio == null) {
    var l = rect.left
    var t = rect.top
    var r = rect.right
    var b = rect.bottom
    when (handle) {
      CropHandle.TOP_LEFT -> {
        l = (l + dxNorm).coerceIn(0f, r - minSize)
        t = (t + dyNorm).coerceIn(0f, b - minSize)
      }
      CropHandle.TOP_RIGHT -> {
        r = (r + dxNorm).coerceIn(l + minSize, 1f)
        t = (t + dyNorm).coerceIn(0f, b - minSize)
      }
      CropHandle.BOTTOM_LEFT -> {
        l = (l + dxNorm).coerceIn(0f, r - minSize)
        b = (b + dyNorm).coerceIn(t + minSize, 1f)
      }
      CropHandle.BOTTOM_RIGHT -> {
        r = (r + dxNorm).coerceIn(l + minSize, 1f)
        b = (b + dyNorm).coerceIn(t + minSize, 1f)
      }
      CropHandle.LEFT -> l = (l + dxNorm).coerceIn(0f, r - minSize)
      CropHandle.RIGHT -> r = (r + dxNorm).coerceIn(l + minSize, 1f)
      CropHandle.TOP -> t = (t + dyNorm).coerceIn(0f, b - minSize)
      CropHandle.BOTTOM -> b = (b + dyNorm).coerceIn(t + minSize, 1f)
      CropHandle.MOVE -> Unit
    }
    return Rect(l, t, r, b)
  }

  val normRatio = (targetRatio / imageAspect.coerceAtLeast(0.01f)).coerceIn(0.05f, 20f)
  val minW = if (normRatio >= 1f) minSize * normRatio else minSize
  val minH = minW / normRatio

  return when (handle) {
    CropHandle.TOP_LEFT,
    CropHandle.TOP_RIGHT,
    CropHandle.BOTTOM_LEFT,
    CropHandle.BOTTOM_RIGHT -> {
      val anchorRight = handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
      val anchorBottom = handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
      val ax = if (anchorRight) rect.right else rect.left
      val ay = if (anchorBottom) rect.bottom else rect.top
      val maxW = if (anchorRight) ax else 1f - ax
      val maxH = if (anchorBottom) ay else 1f - ay

      val desiredW = if (anchorRight) ax - (rect.left + dxNorm) else (rect.right + dxNorm) - ax
      val desiredH = if (anchorBottom) ay - (rect.top + dyNorm) else (rect.bottom + dyNorm) - ay

      var nw: Float
      var nh: Float
      if (abs(dxNorm) >= abs(dyNorm)) {
        nw = desiredW.coerceAtLeast(minW)
        nh = nw / normRatio
      } else {
        nh = desiredH.coerceAtLeast(minH)
        nw = nh * normRatio
      }
      if (nw < minW || nh < minH) {
        nw = minW
        nh = minH
      }
      if (nw > maxW) {
        nw = maxW
        nh = nw / normRatio
      }
      if (nh > maxH) {
        nh = maxH
        nw = nh * normRatio
      }
      val l = if (anchorRight) ax - nw else ax
      val r = if (anchorRight) ax else ax + nw
      val t = if (anchorBottom) ay - nh else ay
      val b = if (anchorBottom) ay else ay + nh
      Rect(l, t, r, b)
    }
    CropHandle.LEFT,
    CropHandle.RIGHT -> {
      val anchorRight = handle == CropHandle.LEFT
      val ax = if (anchorRight) rect.right else rect.left
      val cy = (rect.top + rect.bottom) / 2f
      val maxW = if (anchorRight) ax else 1f - ax
      val maxH = min(cy, 1f - cy) * 2f
      val desiredW = if (anchorRight) ax - (rect.left + dxNorm) else (rect.right + dxNorm) - ax
      var nw = desiredW.coerceAtLeast(minW)
      var nh = nw / normRatio
      if (nw > maxW) {
        nw = maxW
        nh = nw / normRatio
      }
      if (nh > maxH) {
        nh = maxH
        nw = nh * normRatio
      }
      val l = if (anchorRight) ax - nw else ax
      val r = if (anchorRight) ax else ax + nw
      Rect(l, cy - nh / 2f, r, cy + nh / 2f)
    }
    CropHandle.TOP,
    CropHandle.BOTTOM -> {
      val anchorBottom = handle == CropHandle.TOP
      val ay = if (anchorBottom) rect.bottom else rect.top
      val cx = (rect.left + rect.right) / 2f
      val maxH = if (anchorBottom) ay else 1f - ay
      val maxW = min(cx, 1f - cx) * 2f
      val desiredH = if (anchorBottom) ay - (rect.top + dyNorm) else (rect.bottom + dyNorm) - ay
      var nh = desiredH.coerceAtLeast(minH)
      var nw = nh * normRatio
      if (nh > maxH) {
        nh = maxH
        nw = nh * normRatio
      }
      if (nw > maxW) {
        nw = maxW
        nh = nw / normRatio
      }
      val t = if (anchorBottom) ay - nh else ay
      val b = if (anchorBottom) ay else ay + nh
      Rect(cx - nw / 2f, t, cx + nw / 2f, b)
    }
    CropHandle.MOVE -> rect
  }
}

