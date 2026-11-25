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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

private enum class ImageToolTab(val label: String) {
  CROP_ROTATE("裁剪旋转"),
  COMPRESS_EXIF("压缩与 EXIF"),
  MOSAIC_WATERMARK("马赛克水印"),
  STITCH_PDF("长图与 PDF"),
  COLOR_PICKER("像素取色"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolkitScreen(
  initialUriString: String?,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var currentUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var workingBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var exifSummary by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var activeTab by remember { mutableStateOf(ImageToolTab.CROP_ROTATE) }
  var isBusy by remember { mutableStateOf(false) }

  var targetKb by remember { mutableFloatStateOf(300f) }
  var useTargetKb by remember { mutableStateOf(false) }
  var watermarkText by remember { mutableStateOf("仅用于本人业务核验 · TactKit") }
  var mosaicStart by remember { mutableStateOf<Offset?>(null) }
  var mosaicEnd by remember { mutableStateOf<Offset?>(null) }
  var sampledColor by remember { mutableStateOf<SampledPixelColor?>(null) }
  var multiImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
            Toast.makeText(context, "已剥离隐私并保存图片", Toast.LENGTH_SHORT).show()
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
        LargeTopAppBar(
          colors =
            TopAppBarDefaults.largeTopAppBarColors(
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
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
      ) {
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        // Tab Strip
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
          ImageToolTab.entries.forEach { tab ->
            FilterChip(
              selected = activeTab == tab,
              onClick = {
                haptics.tick()
                activeTab = tab
              },
              label = { Text(tab.label) },
              modifier = Modifier.testTag("image_tab_${tab.name}"),
            )
          }
        }

        // Interactive Image Canvas Preview
        val bmp = workingBitmap
        if (bmp != null) {
          val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.3f, 3f)
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(activeTab, bmp) {
                      if (activeTab == ImageToolTab.MOSAIC_WATERMARK) {
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
                              workingBitmap =
                                ImageEngine.applyMosaic(
                                  bmp,
                                  min(s.x, e.x),
                                  min(s.y, e.y),
                                  max(s.x, e.x),
                                  max(s.y, e.y),
                                )
                              haptics.confirm()
                            }
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
                  contentDescription = "图片预览",
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

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text =
                  when (activeTab) {
                    ImageToolTab.MOSAIC_WATERMARK ->
                      "当前尺寸 ${bmp.width} × ${bmp.height} · 在图上拖拽框选即可打码"
                    ImageToolTab.COLOR_PICKER ->
                      "当前尺寸 ${bmp.width} × ${bmp.height} · 点击图像任意像素取色"
                    else -> "当前尺寸 ${bmp.width} × ${bmp.height}"
                  },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          // Controls for active tab
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              when (activeTab) {
                ImageToolTab.CROP_ROTATE -> {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                      onClick = {
                        haptics.tick()
                        workingBitmap = ImageEngine.rotate90(bmp)
                      },
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("旋转 90°")
                    }
                    FilledTonalButton(
                      onClick = {
                        haptics.tick()
                        workingBitmap = ImageEngine.cropNormalized(bmp, 0.1f, 0.1f, 0.9f, 0.9f)
                      },
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Rounded.Crop, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("居中裁切 80%")
                    }
                  }
                }

                ImageToolTab.COMPRESS_EXIF -> {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      Icons.Rounded.Security,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                      text =
                        if (exifSummary.isEmpty()) "未检测到敏感 EXIF 拍摄/定位标签"
                        else "检测到 ${exifSummary.size} 项 EXIF 元数据（导出时自动全部剥离）",
                      style = MaterialTheme.typography.titleSmall,
                    )
                  }
                  exifSummary.forEach { (k, v) ->
                    Text(
                      text = "$k: $v",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  FilterChip(
                    selected = useTargetKb,
                    onClick = { useTargetKb = !useTargetKb },
                    label = { Text("限制输出体积上限：${targetKb.roundToInt()} KB") },
                  )
                  if (useTargetKb) {
                    Slider(
                      value = targetKb,
                      onValueChange = { targetKb = it },
                      valueRange = 50f..1500f,
                    )
                  }
                }

                ImageToolTab.MOSAIC_WATERMARK -> {
                  OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("自定义水印文字") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                  )
                  FilledTonalButton(
                    onClick = {
                      haptics.confirm()
                      workingBitmap = ImageEngine.applyTextWatermark(bmp, watermarkText)
                    },
                    shape = RoundedCornerShape(14.dp),
                  ) {
                    Text("应用斜向防盗水印")
                  }
                }

                ImageToolTab.STITCH_PDF -> {
                  Text(
                    text = "已选择 ${multiImageUris.size.coerceAtLeast(1)} 张图片",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                      onClick = {
                        pickMultipleImages.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      },
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Rounded.Collections, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("多选图片")
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
                            workingBitmap = stitched
                            haptics.confirm()
                            Toast.makeText(context, "长图拼接完成，可点击下方保存", Toast.LENGTH_SHORT)
                              .show()
                          }
                          isBusy = false
                        }
                      },
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Rounded.ViewColumn, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("纵向拼接长图")
                    }
                  }
                  Button(
                    onClick = { saveImagesToPdfLauncher.launch("TactKit_Images.pdf") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("将所选图片转为 PDF")
                  }
                }

                ImageToolTab.COLOR_PICKER -> {
                  val color = sampledColor
                  if (color == null) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(Icons.Rounded.Colorize, contentDescription = null)
                      Text("点击上方预览图任意位置即可拾取像素颜色")
                    }
                  } else {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Box(
                        modifier =
                          Modifier.size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(color.argb))
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = color.hex,
                          style = MaterialTheme.typography.titleMedium.copy(fontFamily = TactKitMono),
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = "RGB(${color.r}, ${color.g}, ${color.b})",
                          style = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      IconButton(
                        onClick = {
                          val cm =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                          cm.setPrimaryClip(ClipData.newPlainText("HEX", color.hex))
                          haptics.confirm()
                          Toast.makeText(context, "已复制 ${color.hex}", Toast.LENGTH_SHORT).show()
                        }
                      ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制 HEX")
                      }
                    }
                  }
                }
              }
            }
          }

          Button(
            onClick = {
              haptics.tick()
              saveImageLauncher.launch("TactKit_Clean_${System.currentTimeMillis()}.jpg")
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("image_save_clean_btn"),
          ) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("一键剥离 EXIF 并导出新图片")
          }
        }
      }
    }
  }
}
