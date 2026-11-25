package com.xialia.tactkit.ui.media

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.BatchExtractMode
import com.xialia.tactkit.engine.MediaEngine
import com.xialia.tactkit.engine.MediaInfo
import com.xialia.tactkit.engine.VoiceMemoManager
import com.xialia.tactkit.ui.components.ExpressiveEmptyFileScreen
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.image.ImageToolTab
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.rememberAppHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class MediaWorkshopTab(val label: String) {
  FRAME_GRAB("逐帧扒图"),
  AUDIO_TRIM("音视频无损"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWorkshopScreen(
  initialUriString: String?,
  onOpenImageToolkit: (uri: String, tab: ImageToolTab) -> Unit,
  onOpenAiVision: (uri: String) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var currentUri by remember(initialUriString) {
    mutableStateOf(initialUriString?.let { Uri.parse(it) })
  }
  var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
  var activeTab by remember { mutableStateOf(MediaWorkshopTab.FRAME_GRAB) }
  var trimRange by remember { mutableStateOf(0f..1f) }
  var isBusy by remember { mutableStateOf(false) }

  // Frame-by-frame extractor state
  var currentTimeMs by remember { mutableLongStateOf(0L) }
  var currentFrameIndex by remember { mutableIntStateOf(0) }
  var previewFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

  // Batch extraction state
  var batchRange by remember { mutableStateOf(0f..0.25f) }
  var batchMode by remember { mutableStateOf(BatchExtractMode.FIXED_FPS) }
  var batchFps by remember { mutableFloatStateOf(2f) }
  var batchExportAsPng by remember { mutableStateOf(true) }
  var batchProgressText by remember { mutableStateOf<String?>(null) }

  suspend fun loadPreviewFrame(uri: Uri, info: MediaInfo, targetFrameIdx: Int?, targetTimeMs: Long) {
    val bmp =
      MediaEngine.extractSingleFrame(
        context = context,
        uri = uri,
        frameIndex = targetFrameIdx,
        totalFrames = info.frameCount,
        timeUs = targetTimeMs * 1000L,
      )
    previewFrameBitmap = bmp
  }

  val pickVideoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) currentUri = uri
    }

  val exportAudioLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { outUri ->
      val src = currentUri
      if (outUri != null && src != null) {
        scope.launch {
          isBusy = true
          runCatching { MediaEngine.extractAudioLossless(context, src, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已无损提取 .m4a 音轨", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "提取失败：${it.message}", Toast.LENGTH_LONG).show()
            }
          isBusy = false
        }
      }
    }

  val exportTrimLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { outUri ->
      val src = currentUri
      val info = mediaInfo
      if (outUri != null && src != null && info != null) {
        val startMs = (trimRange.start * info.durationMs).toLong()
        val endMs = (trimRange.endInclusive * info.durationMs).toLong()
        scope.launch {
          isBusy = true
          runCatching { MediaEngine.trimVideoLossless(context, src, startMs, endMs, outUri) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已完成关键帧无损裁剪导出", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "裁剪失败：${it.message}", Toast.LENGTH_LONG).show()
            }
          isBusy = false
        }
      }
    }

  val exportFramePngLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { outUri ->
      val bmp = previewFrameBitmap
      if (outUri != null && bmp != null) {
        scope.launch {
          isBusy = true
          runCatching { MediaEngine.saveFrameToUri(context, bmp, outUri, asPng = true) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已保存原画质 PNG 帧", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
          isBusy = false
        }
      }
    }

  val exportFrameJpgLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { outUri ->
      val bmp = previewFrameBitmap
      if (outUri != null && bmp != null) {
        scope.launch {
          isBusy = true
          runCatching { MediaEngine.saveFrameToUri(context, bmp, outUri, asPng = false) }
            .onSuccess {
              haptics.confirm()
              Toast.makeText(context, "已保存原画质 JPG 帧", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
              Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
          isBusy = false
        }
      }
    }

  val batchTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      val src = currentUri
      val info = mediaInfo
      if (treeUri != null && src != null && info != null) {
        val startMs = (batchRange.start * info.durationMs).toLong()
        val endMs = (batchRange.endInclusive * info.durationMs).toLong()
        scope.launch {
          isBusy = true
          batchProgressText = "准备导出..."
          runCatching {
            MediaEngine.batchExtractFramesToTree(
              context = context,
              inputUri = src,
              mediaInfo = info,
              treeUri = treeUri,
              startMs = startMs,
              endMs = endMs,
              mode = batchMode,
              fps = batchFps.roundToInt(),
              asPng = batchExportAsPng,
              onProgress = { done, total ->
                batchProgressText = "正在抽帧写入目录：$done / $total"
              },
            )
          }.onSuccess { count ->
            haptics.confirm()
            batchProgressText = "已完成，共导出 $count 帧到所选目录"
            Toast.makeText(context, "成功导出 $count 帧", Toast.LENGTH_SHORT).show()
          }.onFailure {
            batchProgressText = "抽帧失败：${it.message}"
          }
          isBusy = false
        }
      }
    }

  LaunchedEffect(currentUri) {
    val uri = currentUri ?: return@LaunchedEffect
    isBusy = true
    currentTimeMs = 0L
    currentFrameIndex = 0
    batchProgressText = null
    runCatching {
      val info = MediaEngine.inspectMedia(context, uri)
      mediaInfo = info
      trimRange = 0f..1f
      batchRange = 0f..(if (info.durationMs > 5000L) 0.25f else 1f)
      if (info.hasVideo) {
        loadPreviewFrame(uri, info, targetFrameIdx = if (info.frameCount != null) 0 else null, targetTimeMs = 0L)
      } else {
        activeTab = MediaWorkshopTab.AUDIO_TRIM
      }
    }.onFailure {
      Toast.makeText(context, "无法解析该媒体轨道", Toast.LENGTH_SHORT).show()
    }
    isBusy = false
  }

  if (currentUri == null) {
    ExpressiveEmptyFileScreen(
      title = "音视频与逐帧扒图",
      icon = Icons.Rounded.AudioFile,
      onPickFile = {
        haptics.tick()
        pickVideoLauncher.launch(
          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
      },
      onNavigateBack = onNavigateBack,
      fabText = "选择视频",
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
          title = { Text("音视频与逐帧扒图", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                pickVideoLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              }
            ) {
              Icon(Icons.Rounded.VideoFile, contentDescription = "切换视频")
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MediaWorkshopTab.entries.forEach { tab ->
            FilterChip(
              selected = activeTab == tab,
              onClick = {
                haptics.tick()
                activeTab = tab
              },
              label = { Text(tab.label) },
              modifier = Modifier.testTag("media_tab_${tab.name}"),
            )
          }
        }

        val info = mediaInfo
        if (info != null) {
          when (activeTab) {
            MediaWorkshopTab.FRAME_GRAB -> {
              // Single-frame stepping capture card
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(14.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "逐帧步进抓拍",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text =
                        buildString {
                          if (info.frameCount != null) {
                            append("第 ${currentFrameIndex + 1}/${info.frameCount} 帧 · ")
                          }
                          append(MediaEngine.formatFrameTimestampMs(currentTimeMs))
                        },
                      style = MaterialTheme.typography.labelMedium.copy(fontFamily = TactKitMono),
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }

                  Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                  ) {
                    val bmp = previewFrameBitmap
                    if (bmp != null) {
                      Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "视频当前帧画面",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Text(
                        text = if (info.hasVideo) "拖动时间轴定位任意画面" else "该文件不包含视频画面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }

                  if (info.durationMs > 0L && info.hasVideo) {
                    val srcUri = currentUri
                    Slider(
                      value = (currentTimeMs.toFloat() / info.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f),
                      onValueChange = { frac ->
                        val nextMs = (frac * info.durationMs).toLong()
                        currentTimeMs = nextMs
                        info.frameCount?.let { total ->
                          currentFrameIndex = ((frac * (total - 1)).roundToInt()).coerceIn(0, total - 1)
                        }
                      },
                      onValueChangeFinished = {
                        if (srcUri != null) {
                          scope.launch {
                            loadPreviewFrame(
                              srcUri,
                              info,
                              targetFrameIdx = if (info.frameCount != null) currentFrameIndex else null,
                              targetTimeMs = currentTimeMs,
                            )
                          }
                        }
                      },
                    )

                    Row(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      FilledTonalButton(
                        enabled = !isBusy && (if (info.frameCount != null) currentFrameIndex > 0 else currentTimeMs > 0L),
                        onClick = {
                          if (srcUri == null) return@FilledTonalButton
                          haptics.tick()
                          val nextIdx = info.frameCount?.let { (currentFrameIndex - 1).coerceAtLeast(0) }
                          if (nextIdx != null && info.frameCount > 0) {
                            currentFrameIndex = nextIdx
                            currentTimeMs = ((nextIdx.toDouble() / info.frameCount) * info.durationMs).toLong()
                          } else {
                            currentTimeMs = (currentTimeMs - 33L).coerceAtLeast(0L)
                          }
                          scope.launch { loadPreviewFrame(srcUri, info, nextIdx, currentTimeMs) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("frame_prev_btn"),
                      ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                        Text("上一帧")
                      }

                      FilledTonalButton(
                        enabled =
                          !isBusy &&
                            (if (info.frameCount != null) currentFrameIndex + 1 < info.frameCount else currentTimeMs < info.durationMs),
                        onClick = {
                          if (srcUri == null) return@FilledTonalButton
                          haptics.tick()
                          val nextIdx = info.frameCount?.let { (currentFrameIndex + 1).coerceAtMost(it - 1) }
                          if (nextIdx != null && info.frameCount > 0) {
                            currentFrameIndex = nextIdx
                            currentTimeMs = ((nextIdx.toDouble() / info.frameCount) * info.durationMs).toLong()
                          } else {
                            currentTimeMs = (currentTimeMs + 33L).coerceAtMost(info.durationMs)
                          }
                          scope.launch { loadPreviewFrame(srcUri, info, nextIdx, currentTimeMs) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("frame_next_btn"),
                      ) {
                        Text("下一帧")
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                      }
                    }

                    Row(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Button(
                        enabled = previewFrameBitmap != null && !isBusy,
                        onClick = {
                          exportFramePngLauncher.launch("Frame_${currentTimeMs}ms.png")
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                      ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存 PNG")
                      }
                      FilledTonalButton(
                        enabled = previewFrameBitmap != null && !isBusy,
                        onClick = {
                          exportFrameJpgLauncher.launch("Frame_${currentTimeMs}ms.jpg")
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                      ) {
                        Text("保存 JPG")
                      }
                    }

                    // One-tap send current frame to Image Privacy / Color Picker / AI Vision
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      FilledTonalButton(
                        enabled = previewFrameBitmap != null && !isBusy,
                        onClick = {
                          val bmp = previewFrameBitmap ?: return@FilledTonalButton
                          scope.launch {
                            val cached = MediaEngine.cacheFrameForTransfer(context, bmp)
                            onOpenImageToolkit(cached.toString(), ImageToolTab.MOSAIC_WATERMARK)
                          }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                      ) {
                        Icon(Icons.Rounded.HideImage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("打码")
                      }
                      FilledTonalButton(
                        enabled = previewFrameBitmap != null && !isBusy,
                        onClick = {
                          val bmp = previewFrameBitmap ?: return@FilledTonalButton
                          scope.launch {
                            val cached = MediaEngine.cacheFrameForTransfer(context, bmp)
                            onOpenImageToolkit(cached.toString(), ImageToolTab.COLOR_PICKER)
                          }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                      ) {
                        Icon(Icons.Rounded.Colorize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("取色")
                      }
                      FilledTonalButton(
                        enabled = previewFrameBitmap != null && !isBusy,
                        onClick = {
                          val bmp = previewFrameBitmap ?: return@FilledTonalButton
                          scope.launch {
                            val cached = MediaEngine.cacheFrameForTransfer(context, bmp)
                            onOpenAiVision(cached.toString())
                          }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                      ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI 识图")
                      }
                    }
                  }
                }
              }

              // Batch Range Frame Extractor Card
              if (info.hasVideo && info.durationMs > 200L) {
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
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(Icons.Rounded.BurstMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                      Text("区间批量抽帧（流式防 OOM）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    val bStart = (batchRange.start * info.durationMs).toLong()
                    val bEnd = (batchRange.endInclusive * info.durationMs).toLong()
                    Row(
                      horizontalArrangement = Arrangement.SpaceBetween,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "起点 ${MediaEngine.formatFrameTimestampMs(bStart)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TactKitMono),
                      )
                      Text(
                        text = "终点 ${MediaEngine.formatFrameTimestampMs(bEnd)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TactKitMono),
                      )
                    }
                    RangeSlider(
                      value = batchRange,
                      onValueChange = {
                        if (it.endInclusive - it.start >= 0.01f) batchRange = it
                      },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      FilterChip(
                        selected = batchMode == BatchExtractMode.FIXED_FPS,
                        onClick = { batchMode = BatchExtractMode.FIXED_FPS },
                        label = { Text("每秒 ${batchFps.roundToInt()} 帧") },
                      )
                      FilterChip(
                        selected = batchMode == BatchExtractMode.EVERY_FRAME,
                        onClick = { batchMode = BatchExtractMode.EVERY_FRAME },
                        label = { Text("逐帧全抽") },
                      )
                      FilterChip(
                        selected = batchExportAsPng,
                        onClick = { batchExportAsPng = !batchExportAsPng },
                        label = { Text(if (batchExportAsPng) "PNG" else "JPG") },
                      )
                    }

                    if (batchMode == BatchExtractMode.FIXED_FPS) {
                      Slider(
                        value = batchFps,
                        onValueChange = { batchFps = it },
                        valueRange = 1f..10f,
                        steps = 8,
                      )
                    }

                    batchProgressText?.let { status ->
                      Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }

                    Button(
                      enabled = !isBusy && bEnd > bStart,
                      onClick = {
                        haptics.tick()
                        batchTreeLauncher.launch(null)
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("media_batch_extract_btn"),
                    ) {
                      Icon(Icons.Rounded.FolderCopy, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("选择输出文件夹并批量抽帧")
                    }
                  }
                }
              }
            }

            MediaWorkshopTab.AUDIO_TRIM -> {
              Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  Text("媒体轨道信息（零重编码）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text(
                    text = "总时长：${VoiceMemoManager.formatDuration(info.durationMs)} · 分辨率：${info.width}×${info.height}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
                  )
                  Text(
                    text = "视频轨：${info.videoMime ?: "无"} · 音频轨：${info.audioMime ?: "无"}" +
                      (info.frameCount?.let { " · 共 $it 帧" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
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
                  Text("MP4 / MOV 无损提取 .m4a 音轨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text(
                    text = "直接透传 AAC 音频帧流到独立 .m4a 容器，零音质损失、瞬间完成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Button(
                    enabled = info.hasAudio && !isBusy,
                    onClick = {
                      haptics.tick()
                      exportAudioLauncher.launch("TactKit_Audio_${System.currentTimeMillis()}.m4a")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("media_extract_audio_btn"),
                  ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导出 .m4a 音频")
                  }
                }
              }

              if (info.durationMs > 1000L) {
                Surface(
                  shape = RoundedCornerShape(22.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                  ) {
                    Text("关键帧无损视频裁剪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val startMs = (trimRange.start * info.durationMs).toLong()
                    val endMs = (trimRange.endInclusive * info.durationMs).toLong()
                    Row(
                      horizontalArrangement = Arrangement.SpaceBetween,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "起点 ${VoiceMemoManager.formatDuration(startMs)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TactKitMono),
                      )
                      Text(
                        text = "终点 ${VoiceMemoManager.formatDuration(endMs)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TactKitMono),
                      )
                    }
                    RangeSlider(
                      value = trimRange,
                      onValueChange = {
                        if (it.endInclusive - it.start >= 0.02f) trimRange = it
                      },
                    )
                    FilledTonalButton(
                      enabled = !isBusy && endMs > startMs,
                      onClick = {
                        haptics.tick()
                        exportTrimLauncher.launch("TactKit_Trim_${System.currentTimeMillis()}.mp4")
                      },
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().testTag("media_trim_video_btn"),
                    ) {
                      Icon(Icons.Rounded.ContentCut, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("导出裁剪片段 (.mp4)")
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
