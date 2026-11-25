package com.xialia.tactkit.ui.quicklook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.xialia.tactkit.R
import com.xialia.tactkit.data.TactKitDatabase
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.repository.WorkbenchRepository
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.ui.components.MarkdownRenderer
import com.xialia.tactkit.ui.theme.TactKitTheme
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.QuickLookFileInspection
import com.xialia.tactkit.util.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * Floating translucent Quick Look overlay Activity triggered via ACTION_VIEW / ACTION_SEND.
 * Inspects metadata, computes streaming SHA-256, previews Markdown/code/images/PDFs, and
 * optionally bookmarks the file into TactKit's local Room workbench without leaving the caller app.
 */
class QuickLookActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val settingsRepository = SettingsRepository(applicationContext)
    val workbenchRepository =
      WorkbenchRepository(
        TactKitDatabase.getInstance(applicationContext).workbenchCardDao(),
        applicationContext,
      )

    setContent {
      val prefs by
        settingsRepository.preferencesFlow.collectAsStateWithLifecycle(
          initialValue = UserPreferences()
        )
      TactKitTheme(themeMode = prefs.themeMode, dynamicColor = prefs.dynamicColor) {
        QuickLookOverlayScreen(
          intent = intent,
          onSaveToWorkbench = { inspection ->
            if (inspection.uriString != null) {
              workbenchRepository.saveInspectionAsCard(inspection)
            } else {
              workbenchRepository.createNote(
                title = inspection.fileName,
                markdownContent = inspection.textPreview.orEmpty(),
              )
            }
          },
          onCopyText = { label, text -> copyToClipboard(label, text) },
          onClose = { finish() },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    recreate()
  }

  private fun copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "已复制 $label", Toast.LENGTH_SHORT).show()
  }
}

@Composable
private fun QuickLookOverlayScreen(
  intent: Intent?,
  onSaveToWorkbench: suspend (QuickLookFileInspection) -> Long,
  onCopyText: (String, String) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()
  val scope = rememberCoroutineScope()

  var isLoading by remember { mutableStateOf(true) }
  var inspection by remember { mutableStateOf<QuickLookFileInspection?>(null) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var savedToDesktop by remember { mutableStateOf(false) }

  // Multi-page PDF preview state inside Quick Look
  var pdfPageCount by remember { mutableIntStateOf(0) }
  var pdfPageIndex by remember { mutableIntStateOf(0) }
  var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var pdfZoom by remember { mutableFloatStateOf(1f) }
  var pdfPan by remember { mutableStateOf(Offset.Zero) }

  LaunchedEffect(intent) {
    isLoading = true
    errorText = null
    pdfPageCount = 0
    pdfPageIndex = 0
    pdfBitmap = null
    try {
      val uri = extractIncomingUri(intent)
      if (uri != null) {
        val result = FileInspectionHelper.inspectUri(context.contentResolver, uri)
        inspection = result
        if (result.mimeType == "application/pdf") {
          pdfPageCount = PdfEngine.getPageCount(context, uri)
          if (pdfPageCount > 0) {
            pdfBitmap = PdfEngine.renderPageBitmap(context, uri, 0, targetWidthPx = 1080)
          }
        }
      } else {
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrEmpty()) {
          val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
          val byteSize = sharedText.toByteArray(Charsets.UTF_8).size.toLong()
          inspection =
            QuickLookFileInspection(
              uriString = null,
              fileName = subject ?: "分享文本片段.txt",
              fileSizeBytes = byteSize,
              mimeType = intent.type ?: "text/plain",
              sha256 = FileInspectionHelper.computeTextSha256(sharedText),
              textPreview = sharedText,
              isTextTruncated = false,
            )
        } else {
          errorText = "未检测到可预览的文件 URI 或文本内容。"
        }
      }
    } catch (e: Exception) {
      errorText = "读取文件失败：${e.message ?: "权限不足或文件不可访问"}"
    } finally {
      isLoading = false
    }
  }

  LaunchedEffect(inspection?.uriString, pdfPageIndex) {
    val data = inspection ?: return@LaunchedEffect
    val uriStr = data.uriString ?: return@LaunchedEffect
    if (data.mimeType == "application/pdf" && pdfPageCount > 0) {
      pdfZoom = 1f
      pdfPan = Offset.Zero
      pdfBitmap =
        runCatching {
          PdfEngine.renderPageBitmap(context, Uri.parse(uriStr), pdfPageIndex, targetWidthPx = 1080)
        }.getOrNull()
    }
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.28f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
        ) {
          onClose()
        }
        .safeDrawingPadding()
        .padding(16.dp),
  ) {
    Surface(
      shape = ContinuousRoundedRectangle(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      tonalElevation = 8.dp,
      shadowElevation = 16.dp,
      border =
        BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
      modifier =
        Modifier.widthIn(max = 540.dp)
          .fillMaxWidth()
          .heightIn(max = 720.dp)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { /* Consume taps inside card */ }
          .testTag("quick_look_card"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Header
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = ContinuousCapsule(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Preview,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Quick Look 悬浮预览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
          Spacer(modifier = Modifier.weight(1f))
          IconButton(
            onClick = {
              haptics.tick()
              onClose()
            },
            modifier = Modifier.testTag("quick_look_close_icon"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.cd_close),
            )
          }
        }

        if (isLoading) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
          ) {
            CircularProgressIndicator()
            Text(
              text = "正在流式计算 SHA-256 与读取预览...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else if (errorText != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = ContinuousRoundedRectangle(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorText.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
          OutlinedButton(
            onClick = onClose,
            shape = ContinuousCapsule(),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("关闭")
          }
        } else {
          val data = inspection ?: return@Column

          Text(
            text = data.fileName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("quick_look_filename"),
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = ContinuousRoundedRectangle(10.dp),
            ) {
              Text(
                text = FileInspectionHelper.formatBytes(data.fileSizeBytes),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = ContinuousRoundedRectangle(10.dp),
            ) {
              Text(
                text = data.mimeType,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = ContinuousRoundedRectangle(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "SHA-256 摘要（流式校验）",
                  style = MaterialTheme.typography.labelMedium,
                )
                Text(
                  text = data.sha256,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.testTag("quick_look_sha256"),
                )
              }
              IconButton(
                onClick = {
                  haptics.confirm()
                  onCopyText("SHA-256", data.sha256)
                },
                modifier = Modifier.testTag("quick_look_copy_sha"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.ContentCopy,
                  contentDescription = stringResource(R.string.cd_copy),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }

          // Multi-page PDF preview (if PDF)
          if (data.mimeType == "application/pdf" && pdfPageCount > 0) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = ContinuousRoundedRectangle(18.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "PDF 双指缩放预览 · 第 ${pdfPageIndex + 1} / $pdfPageCount 页",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(
                    enabled = pdfPageIndex > 0,
                    onClick = {
                      haptics.tick()
                      pdfPageIndex--
                    },
                  ) {
                    Icon(
                      Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                      contentDescription = "上一页",
                    )
                  }
                  IconButton(
                    enabled = pdfPageIndex + 1 < pdfPageCount,
                    onClick = {
                      haptics.tick()
                      pdfPageIndex++
                    },
                  ) {
                    Icon(
                      Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                      contentDescription = "下一页",
                    )
                  }
                }

                pdfBitmap?.let { bmp ->
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .pointerInput(pdfPageIndex) {
                          detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (pdfZoom * zoom).coerceIn(1f, 3.5f)
                            pdfZoom = nextScale
                            pdfPan =
                              if (nextScale > 1.05f) pdfPan + pan else Offset.Zero
                          }
                        },
                  ) {
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = "PDF 第 ${pdfPageIndex + 1} 页",
                      contentScale = ContentScale.Fit,
                      modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                          scaleX = pdfZoom
                          scaleY = pdfZoom
                          translationX = pdfPan.x
                          translationY = pdfPan.y
                        },
                    )
                  }
                }
              }
            }
          }

          // Optional Image Thumbnail Preview
          data.imagePreview?.let { bitmap ->
            Image(
              bitmap = bitmap.asImageBitmap(),
              contentDescription = data.fileName,
              contentScale = ContentScale.Fit,
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(max = 220.dp)
                  .clip(ContinuousRoundedRectangle(16.dp)),
            )
          }

          // Optional Rich Markdown / Code Preview
          data.textPreview?.let { preview ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = ContinuousRoundedRectangle(16.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (data.isTextTruncated) "Markdown 预览（前 32 KB）" else "Markdown 预览",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                  )
                  FilledTonalButton(
                    onClick = {
                      haptics.confirm()
                      onCopyText("文本内容", preview)
                    },
                    shape = ContinuousCapsule(),
                    modifier = Modifier.testTag("quick_look_copy_text"),
                  ) {
                    Icon(
                      Icons.Rounded.ContentCopy,
                      contentDescription = null,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键复制")
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.testTag("quick_look_text_preview")) {
                  MarkdownRenderer(markdown = preview, compact = false)
                }
              }
            }
          }

          // Bottom Actions
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = {
                haptics.tick()
                onClose()
              },
              shape = ContinuousCapsule(),
              modifier = Modifier.weight(1f).testTag("quick_look_close_button"),
            ) {
              Text("关闭")
            }
            Button(
              enabled = !savedToDesktop,
              onClick = {
                scope.launch {
                  onSaveToWorkbench(data)
                  haptics.confirm()
                  savedToDesktop = true
                }
              },
              shape = ContinuousCapsule(),
              modifier = Modifier.weight(1.4f).testTag("quick_look_bookmark_button"),
            ) {
              Icon(
                Icons.Rounded.BookmarkAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (savedToDesktop) "已收藏到桌面" else "收藏到简工坊桌面")
            }
          }
        }
      }
    }
  }
}

private fun extractIncomingUri(intent: Intent?): Uri? {
  if (intent == null) return null
  if (intent.data != null) return intent.data
  if (intent.action == Intent.ACTION_SEND) {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
  }
  return null
}
