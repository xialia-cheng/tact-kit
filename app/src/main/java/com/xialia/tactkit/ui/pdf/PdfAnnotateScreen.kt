package com.xialia.tactkit.ui.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.AnnotationTool
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.engine.PdfPrepareResult
import com.xialia.tactkit.engine.PdfStroke
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.util.rememberAppHaptics
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotateScreen(
  initialUriString: String?,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var currentUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var readableUri by remember { mutableStateOf<Uri?>(null) }
  var tempUnlockedFile by remember { mutableStateOf<File?>(null) }
  var showUnlockDialog by remember { mutableStateOf(false) }
  var isUnlocking by remember { mutableStateOf(false) }
  var unlockError by remember { mutableStateOf<String?>(null) }

  var pageCount by remember { mutableIntStateOf(0) }
  var currentPage by remember { mutableIntStateOf(0) }
  var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isBusy by remember { mutableStateOf(false) }

  var activeTool by remember { mutableStateOf(AnnotationTool.HIGHLIGHTER) }
  var activeColor by remember { mutableStateOf(Color(0xFFFACC15)) }
  var isZoomMode by remember { mutableStateOf(false) }
  var zoomScale by remember { mutableFloatStateOf(1f) }
  var zoomOffset by remember { mutableStateOf(Offset.Zero) }

  val strokesByPage = remember { mutableStateMapOf<Int, List<PdfStroke>>() }
  var activeStrokePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

  DisposableEffect(Unit) {
    onDispose {
      tempUnlockedFile?.delete()
      tempUnlockedFile = null
    }
  }

  val palette =
    listOf(
      Color(0xFFFACC15),
      Color(0xFF38BDF8),
      Color(0xFFEF4444),
      Color(0xFF10B981),
      Color(0xFF1E293B),
    )

  val pickPdfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        tempUnlockedFile?.delete()
        tempUnlockedFile = null
        readableUri = null
        currentUri = uri
        currentPage = 0
        zoomScale = 1f
        zoomOffset = Offset.Zero
        strokesByPage.clear()
      }
    }

  val exportPdfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { outUri ->
      val srcUri = readableUri ?: currentUri
      if (outUri != null && srcUri != null) {
        scope.launch {
          isBusy = true
          runCatching { PdfEngine.exportAnnotatedPdf(context, srcUri, strokesByPage.toMap(), outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已导出含批注与签名的新 PDF", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
            }
          isBusy = false
        }
      }
    }

  LaunchedEffect(currentUri) {
    val uri = currentUri ?: return@LaunchedEffect
    isBusy = true
    zoomScale = 1f
    zoomOffset = Offset.Zero
    pageBitmap = null
    pageCount = 0
    showUnlockDialog = false
    unlockError = null
    when (val res = PdfEngine.prepareReadablePdf(context, uri)) {
      is PdfPrepareResult.Ready -> {
        tempUnlockedFile?.delete()
        tempUnlockedFile = res.tempFile
        readableUri = res.readableUri
        pageCount = res.pageCount
        if (res.pageCount > 0) {
          pageBitmap = PdfEngine.renderPageBitmap(context, res.readableUri, 0, targetWidthPx = 1280)
        }
      }
      PdfPrepareResult.PasswordRequired,
      PdfPrepareResult.WrongPassword -> {
        readableUri = null
        showUnlockDialog = true
      }
      is PdfPrepareResult.Error -> {
        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
      }
    }
    isBusy = false
  }

  LaunchedEffect(readableUri, currentPage) {
    val uri = readableUri ?: return@LaunchedEffect
    if (pageCount <= 0) return@LaunchedEffect
    isBusy = true
    zoomScale = 1f
    zoomOffset = Offset.Zero
    pageBitmap =
      runCatching { PdfEngine.renderPageBitmap(context, uri, currentPage, targetWidthPx = 1280) }
        .getOrNull()
    isBusy = false
  }

  if (currentUri == null) {
    ExpressiveEmptyFileScreen(
      title = "PDF 批注与手写签名",
      icon = Icons.Rounded.Draw,
      onPickFile = {
        haptics.tick()
        pickPdfLauncher.launch(arrayOf("application/pdf"))
      },
      onNavigateBack = onNavigateBack,
      fabText = "选择 PDF",
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
          title = { Text("PDF 批注与签名", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            FilterChip(
              selected = isZoomMode,
              onClick = {
                haptics.tick()
                isZoomMode = !isZoomMode
                if (!isZoomMode && zoomScale <= 1.01f) {
                  zoomOffset = Offset.Zero
                }
              },
              leadingIcon = {
                Icon(Icons.Rounded.Pinch, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              label = { Text(if (isZoomMode) "缩放中" else "缩放预览", maxLines = 1, softWrap = false) },
              modifier = Modifier.padding(end = 8.dp).testTag("pdf_annotate_zoom_toggle"),
            )
          },
        )
      },
    ) { innerPadding ->
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        if (isBusy) TactKitBusyStatusBar(message = "正在渲染与导出 PDF 批注...")

        // Interactive PDF Canvas Surface (fills remaining middle space)
        val bmp = pageBitmap
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .padding(8.dp),
        ) {
          if (bmp != null) {
            val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
            val strokeNormWidth =
              when (activeTool) {
                AnnotationTool.HIGHLIGHTER -> 0.032f
                AnnotationTool.PEN -> 0.007f
                AnnotationTool.SIGNATURE -> 0.0055f
              }
            val currentPageStrokes = strokesByPage[currentPage].orEmpty()

            Box(
              modifier =
                Modifier.aspectRatio(ratio)
                  .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomOffset.x
                    translationY = zoomOffset.y
                  }
                  .clip(RoundedCornerShape(12.dp))
                  .pointerInput(currentPage, activeTool, activeColor, isZoomMode) {
                    if (isZoomMode) {
                      detectTransformGestures { _, pan, zoom, _ ->
                        val next = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = next
                        zoomOffset = if (next > 1.02f) zoomOffset + pan else Offset.Zero
                      }
                    } else {
                      detectDragGestures(
                        onDragStart = { start ->
                          val nx = (start.x / size.width).coerceIn(0f, 1f)
                          val ny = (start.y / size.height).coerceIn(0f, 1f)
                          activeStrokePoints = listOf(nx to ny)
                        },
                        onDrag = { change, _ ->
                          change.consume()
                          val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                          val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                          activeStrokePoints = activeStrokePoints + (nx to ny)
                        },
                        onDragEnd = {
                          if (activeStrokePoints.size >= 2) {
                            val stroke =
                              PdfStroke(
                                tool = activeTool,
                                colorArgb = activeColor.toArgb(),
                                normalizedWidth = strokeNormWidth,
                                normalizedPoints = activeStrokePoints,
                              )
                            strokesByPage[currentPage] =
                              strokesByPage[currentPage].orEmpty() + stroke
                          }
                          activeStrokePoints = emptyList()
                        },
                        onDragCancel = { activeStrokePoints = emptyList() },
                      )
                    }
                  },
            ) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "批注画布页面",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
              )

              val strokePath = remember { Path() }
              Canvas(modifier = Modifier.fillMaxSize().testTag("pdf_annotate_canvas")) {
                fun drawStroke(stroke: PdfStroke) {
                  if (stroke.normalizedPoints.size < 2) return
                  strokePath.rewind()
                  val first = stroke.normalizedPoints[0]
                  strokePath.moveTo(first.first * size.width, first.second * size.height)
                  for (i in 1 until stroke.normalizedPoints.size) {
                    val pt = stroke.normalizedPoints[i]
                    strokePath.lineTo(pt.first * size.width, pt.second * size.height)
                  }
                  val baseColor = Color(stroke.colorArgb)
                  val alpha = if (stroke.tool == AnnotationTool.HIGHLIGHTER) 0.36f else 1f
                  drawPath(
                    path = strokePath,
                    color = baseColor.copy(alpha = alpha),
                    style =
                      Stroke(
                        width = (stroke.normalizedWidth * size.width).coerceIn(2f, 48f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                      ),
                  )
                }

                currentPageStrokes.forEach { drawStroke(it) }
                if (activeStrokePoints.size >= 2) {
                  drawStroke(
                    PdfStroke(
                      tool = activeTool,
                      colorArgb = activeColor.toArgb(),
                      normalizedWidth = strokeNormWidth,
                      normalizedPoints = activeStrokePoints,
                    )
                  )
                }
              }
            }
          }
        }

        // Fixed Bottom Persistent Tool / Color / Undo / Export Bar
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
          ) {
            // Tool mode + colors strip
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
              FilterChip(
                selected = !isZoomMode && activeTool == AnnotationTool.HIGHLIGHTER,
                onClick = {
                  haptics.tick()
                  isZoomMode = false
                  activeTool = AnnotationTool.HIGHLIGHTER
                  if (activeColor == Color(0xFF1E293B)) activeColor = Color(0xFFFACC15)
                },
                leadingIcon = {
                  Icon(Icons.Rounded.Highlight, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("荧光笔") },
              )
              FilterChip(
                selected = !isZoomMode && activeTool == AnnotationTool.PEN,
                onClick = {
                  haptics.tick()
                  isZoomMode = false
                  activeTool = AnnotationTool.PEN
                },
                leadingIcon = {
                  Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("画笔") },
              )
              FilterChip(
                selected = !isZoomMode && activeTool == AnnotationTool.SIGNATURE,
                onClick = {
                  haptics.tick()
                  isZoomMode = false
                  activeTool = AnnotationTool.SIGNATURE
                  activeColor = Color(0xFF1E293B)
                },
                leadingIcon = {
                  Icon(Icons.Rounded.Gesture, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("签名") },
              )

              palette.forEach { swatch ->
                val selected = activeColor == swatch
                Box(
                  modifier =
                    Modifier.size(if (selected) 28.dp else 22.dp)
                      .clip(CircleShape)
                      .background(swatch)
                      .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color =
                          if (selected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                      )
                      .clickable {
                        haptics.tick()
                        activeColor = swatch
                      }
                )
              }
            }

            // Bottom page nav + undo + clear + export
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  enabled = currentPage > 0,
                  onClick = { currentPage -= 1 },
                  modifier = Modifier.size(38.dp),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一页")
                }
                Text(
                  text = "${currentPage + 1}/${pageCount.coerceAtLeast(1)}",
                  style = MaterialTheme.typography.labelMedium,
                )
                IconButton(
                  enabled = currentPage + 1 < pageCount,
                  onClick = { currentPage += 1 },
                  modifier = Modifier.size(38.dp),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一页")
                }
                IconButton(
                  enabled = !strokesByPage[currentPage].isNullOrEmpty(),
                  onClick = {
                    haptics.tick()
                    val cur = strokesByPage[currentPage].orEmpty()
                    if (cur.isNotEmpty()) strokesByPage[currentPage] = cur.dropLast(1)
                  },
                  modifier = Modifier.size(38.dp),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销一笔")
                }
                IconButton(
                  enabled = !strokesByPage[currentPage].isNullOrEmpty(),
                  onClick = {
                    haptics.tick()
                    strokesByPage.remove(currentPage)
                  },
                  modifier = Modifier.size(38.dp),
                ) {
                  Icon(Icons.Rounded.DeleteSweep, contentDescription = "清空本页")
                }
              }

              Button(
                onClick = {
                  haptics.tick()
                  exportPdfLauncher.launch("TactKit_Annotated.pdf")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("pdf_export_annotated_btn"),
              ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("导出 PDF")
              }
            }
          }
        }
      }
    }
  }

  if (showUnlockDialog) {
    PdfPasswordUnlockDialog(
      isVerifying = isUnlocking,
      errorText = unlockError,
      onSubmitPassword = { pwd ->
        val src = currentUri ?: return@PdfPasswordUnlockDialog
        scope.launch {
          isUnlocking = true
          unlockError = null
          when (val res = PdfEngine.prepareReadablePdf(context, src, password = pwd)) {
            is PdfPrepareResult.Ready -> {
              tempUnlockedFile?.delete()
              tempUnlockedFile = res.tempFile
              readableUri = res.readableUri
              pageCount = res.pageCount
              currentPage = 0
              showUnlockDialog = false
              haptics.confirm()
              if (res.pageCount > 0) {
                pageBitmap = PdfEngine.renderPageBitmap(context, res.readableUri, 0, targetWidthPx = 1280)
              }
            }
            PdfPrepareResult.WrongPassword,
            PdfPrepareResult.PasswordRequired -> {
              unlockError = "密码错误，请重试"
            }
            is PdfPrepareResult.Error -> {
              unlockError = res.message
            }
          }
          isUnlocking = false
        }
      },
      onDismiss = { showUnlockDialog = false },
    )
  }
}
