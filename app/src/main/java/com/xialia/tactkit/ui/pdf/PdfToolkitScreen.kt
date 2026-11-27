package com.xialia.tactkit.ui.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.engine.PdfPrepareResult
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.TactKitMorphLoadingIndicator
import com.xialia.tactkit.ui.components.tactKitToolHeroTarget
import com.xialia.tactkit.util.rememberAppHaptics
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class PendingPdfExport {
  MERGE,
  SPLIT,
  COMPRESS,
  PROTECT,
  UNLOCK,
  EXPORT_PAGE_PNG,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolkitScreen(
  initialUriString: String?,
  onOpenAnnotate: (String) -> Unit,
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
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  var pendingMergeUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var pendingExport by remember { mutableStateOf<PendingPdfExport?>(null) }
  var showSplitDialog by remember { mutableStateOf(false) }
  var splitStart by remember { mutableStateOf("1") }
  var splitEnd by remember { mutableStateOf("1") }
  var showCompressDialog by remember { mutableStateOf(false) }
  var compressQuality by remember { mutableFloatStateOf(0.65f) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var passwordIsProtect by remember { mutableStateOf(true) }
  var passwordInput by remember { mutableStateOf("") }

  DisposableEffect(Unit) {
    onDispose {
      tempUnlockedFile?.delete()
      tempUnlockedFile = null
    }
  }

  val pickSinglePdf =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        tempUnlockedFile?.delete()
        tempUnlockedFile = null
        readableUri = null
        currentUri = uri
        currentPage = 0
        scale = 1f
        offset = Offset.Zero
      }
    }

  val createOutputPdf =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { outUri ->
      val op = pendingExport ?: return@rememberLauncherForActivityResult
      pendingExport = null
      if (outUri == null) return@rememberLauncherForActivityResult
      scope.launch {
        isBusy = true
        val res = runCatching {
          when (op) {
            PendingPdfExport.MERGE -> PdfEngine.mergePdfs(context, pendingMergeUris, outUri)
            PendingPdfExport.SPLIT -> {
              val src = readableUri ?: currentUri ?: error("未选择 PDF")
              PdfEngine.splitPdfByRange(
                context,
                src,
                splitStart.toIntOrNull() ?: 1,
                splitEnd.toIntOrNull() ?: pageCount.coerceAtLeast(1),
                outUri,
              )
            }
            PendingPdfExport.COMPRESS -> {
              val src = readableUri ?: currentUri ?: error("未选择 PDF")
              PdfEngine.compressPdf(context, src, compressQuality, outUri)
            }
            PendingPdfExport.PROTECT -> {
              val src = readableUri ?: currentUri ?: error("未选择 PDF")
              PdfEngine.protectPdf(context, src, passwordInput, passwordInput, outUri)
            }
            PendingPdfExport.UNLOCK -> {
              val src = currentUri ?: error("未选择 PDF")
              PdfEngine.unlockPdf(context, src, passwordInput, outUri)
            }
            PendingPdfExport.EXPORT_PAGE_PNG -> Unit
          }
        }
        isBusy = false
        res.onSuccess {
          haptics.confirm()
          Toast.makeText(context, "PDF 处理并保存成功", Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
          Toast.makeText(context, "处理失败：${err.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  val createOutputPng =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { outUri ->
      val bmp = pageBitmap
      if (outUri != null && bmp != null) {
        scope.launch {
          isBusy = true
          runCatching { PdfEngine.saveBitmapAsPng(context, bmp, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已导出当前页为 PNG", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
          isBusy = false
        }
      }
    }

  val pickMergePdfs =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.size >= 2) {
        pendingMergeUris = uris
        pendingExport = PendingPdfExport.MERGE
        createOutputPdf.launch("TactKit_Merged.pdf")
      } else if (uris.isNotEmpty()) {
        Toast.makeText(context, "请至少选择 2 个 PDF 文件进行合并", Toast.LENGTH_SHORT).show()
      }
    }

  LaunchedEffect(currentUri) {
    val uri = currentUri ?: return@LaunchedEffect
    isBusy = true
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
        splitEnd = res.pageCount.coerceAtLeast(1).toString()
        if (res.pageCount > 0) {
          pageBitmap = PdfEngine.renderPageBitmap(context, res.readableUri, 0)
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
    pageBitmap =
      runCatching { PdfEngine.renderPageBitmap(context, uri, currentPage) }.getOrNull()
    isBusy = false
  }

  if (currentUri == null) {
    ExpressiveEmptyFileScreen(
      title = "PDF 核心工具箱",
      icon = Icons.Rounded.PictureAsPdf,
      onPickFile = {
        haptics.tick()
        pickSinglePdf.launch(arrayOf("application/pdf"))
      },
      onNavigateBack = onNavigateBack,
      fabText = "选择 PDF",
      heroTargetRoute = "pdf_toolkit",
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
          title = {
            Text(
              text = "PDF 核心工具箱",
              fontWeight = FontWeight.Bold,
              modifier = Modifier.tactKitToolHeroTarget("pdf_toolkit"),
            )
          },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = { pickSinglePdf.launch(arrayOf("application/pdf")) },
              modifier = Modifier.testTag("pdf_reselect_button"),
            ) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = "切换 PDF")
            }
          },
        )
      },
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (isBusy) {
          TactKitBusyStatusBar(message = "正在处理与渲染 PDF 页面...")
        }

        // Multi-page Pinch-to-Zoom Preview Surface
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.fillMaxWidth()
                  .height(340.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                  .pointerInput(currentPage) {
                    detectTransformGestures { _, pan, zoom, _ ->
                      val newScale = (scale * zoom).coerceIn(1f, 4f)
                      scale = newScale
                      offset =
                        if (newScale > 1.02f) {
                          offset + pan
                        } else {
                          Offset.Zero
                        }
                    }
                  },
            ) {
              val bmp = pageBitmap
              if (bmp != null) {
                Image(
                  bitmap = bmp.asImageBitmap(),
                  contentDescription = "PDF 第 ${currentPage + 1} 页",
                  contentScale = ContentScale.Fit,
                  modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                      scaleX = scale
                      scaleY = scale
                      translationX = offset.x
                      translationY = offset.y
                    },
                )
              } else if (isBusy) {
                TactKitMorphLoadingIndicator(size = 46.dp)
              } else {
                Text("双指捏合可放大预览页面", style = MaterialTheme.typography.bodySmall)
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              IconButton(
                enabled = currentPage > 0,
                onClick = {
                  haptics.tick()
                  currentPage -= 1
                  scale = 1f
                  offset = Offset.Zero
                },
              ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一页")
              }
              Text(
                text =
                  if (pageCount > 0) "第 ${currentPage + 1} / $pageCount 页（支持双指缩放）"
                  else "准备中",
                style = MaterialTheme.typography.labelMedium,
              )
              IconButton(
                enabled = currentPage + 1 < pageCount,
                onClick = {
                  haptics.tick()
                  currentPage += 1
                  scale = 1f
                  offset = Offset.Zero
                },
              ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一页")
              }
            }
          }
        }

        // Annotate CTA
        Button(
          onClick = {
            haptics.tick()
            (readableUri ?: currentUri)?.let { onOpenAnnotate(it.toString()) }
          },
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pdf_open_annotate_btn"),
        ) {
          Icon(Icons.Rounded.Draw, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("进入 Canvas 荧光笔批注与手写签名")
        }

        // Operations Strip
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
          FilledTonalButton(
            onClick = { pickMergePdfs.launch(arrayOf("application/pdf")) },
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("合并 PDF")
          }
          FilledTonalButton(
            onClick = { showSplitDialog = true },
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("按页拆分")
          }
          FilledTonalButton(
            onClick = { showCompressDialog = true },
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("压缩体积")
          }
          FilledTonalButton(
            onClick = {
              passwordIsProtect = true
              passwordInput = ""
              showPasswordDialog = true
            },
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("设置密码")
          }
          FilledTonalButton(
            onClick = {
              passwordIsProtect = false
              passwordInput = ""
              showPasswordDialog = true
            },
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("解除密码")
          }
          FilledTonalButton(
            enabled = pageBitmap != null,
            onClick = { createOutputPng.launch("TactKit_Page_${currentPage + 1}.png") },
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("本页转 PNG")
          }
        }
      }
    }
  }

  if (showSplitDialog) {
    AlertDialog(
      onDismissRequest = { showSplitDialog = false },
      title = { Text("按页码范围拆分 PDF") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("共 $pageCount 页，请输入 1 起算的起止页码：", style = MaterialTheme.typography.bodySmall)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = splitStart,
              onValueChange = { splitStart = it.filter { ch -> ch.isDigit() } },
              label = { Text("起始页") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = splitEnd,
              onValueChange = { splitEnd = it.filter { ch -> ch.isDigit() } },
              label = { Text("结束页") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showSplitDialog = false
            pendingExport = PendingPdfExport.SPLIT
            createOutputPdf.launch("TactKit_Split.pdf")
          }
        ) {
          Text("导出拆分文件")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSplitDialog = false }) { Text("取消") }
      },
    )
  }

  if (showCompressDialog) {
    AlertDialog(
      onDismissRequest = { showCompressDialog = false },
      title = { Text("压缩 PDF 体积") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("图像质量：${(compressQuality * 100).roundToInt()}%")
          Slider(
            value = compressQuality,
            onValueChange = { compressQuality = it },
            valueRange = 0.30f..0.90f,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showCompressDialog = false
            pendingExport = PendingPdfExport.COMPRESS
            createOutputPdf.launch("TactKit_Compressed.pdf")
          }
        ) {
          Text("导出压缩文件")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCompressDialog = false }) { Text("取消") }
      },
    )
  }

  if (showPasswordDialog) {
    AlertDialog(
      onDismissRequest = { showPasswordDialog = false },
      title = { Text(if (passwordIsProtect) "设置 PDF 打开密码" else "解除 PDF 密码保护") },
      text = {
        OutlinedTextField(
          value = passwordInput,
          onValueChange = { passwordInput = it },
          label = { Text(if (passwordIsProtect) "输入新密码" else "输入当前打开密码") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          enabled = passwordInput.isNotBlank(),
          onClick = {
            showPasswordDialog = false
            pendingExport =
              if (passwordIsProtect) PendingPdfExport.PROTECT else PendingPdfExport.UNLOCK
            createOutputPdf.launch(
              if (passwordIsProtect) "TactKit_Protected.pdf" else "TactKit_Unlocked.pdf"
            )
          },
        ) {
          Text("导出")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPasswordDialog = false }) { Text("取消") }
      },
    )
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
              splitEnd = res.pageCount.coerceAtLeast(1).toString()
              showUnlockDialog = false
              haptics.confirm()
              if (res.pageCount > 0) {
                pageBitmap = PdfEngine.renderPageBitmap(context, res.readableUri, 0)
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
