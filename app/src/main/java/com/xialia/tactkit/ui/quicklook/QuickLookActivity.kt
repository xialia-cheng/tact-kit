package com.xialia.tactkit.ui.quicklook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.xialia.tactkit.engine.PdfPrepareResult
import com.xialia.tactkit.ui.components.MarkdownRenderer
import com.xialia.tactkit.ui.pdf.PdfPasswordUnlockDialog
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.TactKitTheme
import com.xialia.tactkit.util.ApkInspectionInfo
import com.xialia.tactkit.util.CalendarEventInfo
import com.xialia.tactkit.util.CertificateEntryInfo
import com.xialia.tactkit.util.ConfigKvEntry
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.LogSummaryInfo
import com.xialia.tactkit.util.QuickLookFileInspection
import com.xialia.tactkit.util.SpecialtyFileKind
import com.xialia.tactkit.util.SpecialtyFormatParser
import com.xialia.tactkit.util.VCardContactInfo
import com.xialia.tactkit.util.rememberAppHaptics
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating translucent Quick Look overlay Activity triggered via ACTION_VIEW / ACTION_SEND.
 * Inspects metadata, computes streaming SHA-256, previews Markdown/code/images/PDFs/DOCX, and
 * provides specialized local read-only cards for ICS/PEM/JKS/P12/APK/VCF/ENV/TOML/LOG files.
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
  var pdfReadableUri by remember { mutableStateOf<Uri?>(null) }
  var pdfTempUnlockedFile by remember { mutableStateOf<File?>(null) }
  var showPdfUnlockDialog by remember { mutableStateOf(false) }
  var isPdfUnlocking by remember { mutableStateOf(false) }
  var pdfUnlockError by remember { mutableStateOf<String?>(null) }

  // Ephemeral in-memory keystore state
  var unlockedCerts by remember { mutableStateOf<List<CertificateEntryInfo>>(emptyList()) }
  var keystorePassword by remember { mutableStateOf("") }
  var keystoreError by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      keystorePassword = ""
      unlockedCerts = emptyList()
      pdfTempUnlockedFile?.delete()
      pdfTempUnlockedFile = null
    }
  }

  LaunchedEffect(intent) {
    isLoading = true
    errorText = null
    pdfPageCount = 0
    pdfPageIndex = 0
    pdfBitmap = null
    pdfReadableUri = null
    pdfTempUnlockedFile?.delete()
    pdfTempUnlockedFile = null
    showPdfUnlockDialog = false
    pdfUnlockError = null
    unlockedCerts = emptyList()
    keystorePassword = ""
    keystoreError = null
    try {
      val uri = extractIncomingUri(intent)
      if (uri != null) {
        val result = FileInspectionHelper.inspectUri(context.contentResolver, uri, context)
        inspection = result
        if (result.mimeType == "application/pdf") {
          when (val prep = PdfEngine.prepareReadablePdf(context, uri)) {
            is PdfPrepareResult.Ready -> {
              pdfTempUnlockedFile = prep.tempFile
              pdfReadableUri = prep.readableUri
              pdfPageCount = prep.pageCount
              if (prep.pageCount > 0) {
                pdfBitmap = PdfEngine.renderPageBitmap(context, prep.readableUri, 0, targetWidthPx = 1080)
              }
            }
            PdfPrepareResult.PasswordRequired,
            PdfPrepareResult.WrongPassword -> {
              showPdfUnlockDialog = true
            }
            is PdfPrepareResult.Error -> Unit
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

  LaunchedEffect(pdfReadableUri, pdfPageIndex) {
    val readable = pdfReadableUri ?: return@LaunchedEffect
    if (pdfPageCount > 0) {
      pdfZoom = 1f
      pdfPan = Offset.Zero
      pdfBitmap =
        runCatching {
          PdfEngine.renderPageBitmap(context, readable, pdfPageIndex, targetWidthPx = 1080)
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
                  fontFamily = TactKitMono,
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

          data.specialtyError?.let { specErr ->
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = ContinuousRoundedRectangle(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = specErr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          // Specialty File Cards
          when (data.specialtyKind) {
            SpecialtyFileKind.CALENDAR_ICS -> {
              CalendarEventsSection(
                events = data.calendarEvents,
                onAddToCalendar = { ev ->
                  val calIntent =
                    Intent(Intent.ACTION_INSERT).apply {
                      setData(CalendarContract.Events.CONTENT_URI)
                      putExtra(CalendarContract.Events.TITLE, ev.title)
                      if (ev.location.isNotBlank()) {
                        putExtra(CalendarContract.Events.EVENT_LOCATION, ev.location)
                      }
                      if (ev.description.isNotBlank()) {
                        putExtra(CalendarContract.Events.DESCRIPTION, ev.description)
                      }
                      ev.startEpochMs?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                      ev.endEpochMs?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                      if (ev.isAllDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    }
                  runCatching { context.startActivity(calIntent) }.onFailure {
                    Toast.makeText(context, "未找到可用的系统日历应用", Toast.LENGTH_SHORT).show()
                  }
                },
              )
            }
            SpecialtyFileKind.CERTIFICATE -> {
              CertificatesSection(certs = data.certificates, onCopyText = onCopyText)
            }
            SpecialtyFileKind.KEYSTORE_SECRET -> {
              KeystoreSecretSection(
                password = keystorePassword,
                onPasswordChange = { keystorePassword = it },
                unlockedCerts = unlockedCerts,
                errorMsg = keystoreError,
                onUnlock = {
                  val uriStr = data.uriString ?: return@KeystoreSecretSection
                  val pwdChars = keystorePassword.toCharArray()
                  keystorePassword = ""
                  scope.launch {
                    val res =
                      withContext(Dispatchers.IO) {
                        runCatching {
                          val bytes =
                            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                              it.readBytes()
                            } ?: error("无法读取密钥库文件")
                          SpecialtyFormatParser.inspectKeyStoreInMemory(bytes, data.fileName, pwdChars)
                        }
                      }
                    res.onSuccess { certs ->
                      haptics.confirm()
                      unlockedCerts = certs
                      keystoreError = null
                    }.onFailure { err ->
                      unlockedCerts = emptyList()
                      keystoreError = err.message ?: "无法解锁密钥库"
                    }
                  }
                },
                onLockClear = {
                  haptics.tick()
                  keystorePassword = ""
                  unlockedCerts = emptyList()
                  keystoreError = null
                },
                onCopyText = onCopyText,
              )
            }
            SpecialtyFileKind.APK_PACKAGE -> {
              data.apkInfo?.let { apk ->
                ApkInfoSection(apk = apk)
              }
            }
            SpecialtyFileKind.VCARD_CONTACT -> {
              VCardSection(vcards = data.vcards)
            }
            SpecialtyFileKind.CONFIG_KV -> {
              ConfigKvSection(entries = data.configEntries)
            }
            SpecialtyFileKind.LOG_FILE -> {
              data.logSummary?.let { summary ->
                LogSummarySection(summary = summary)
              }
            }
            SpecialtyFileKind.DOCX,
            SpecialtyFileKind.STANDARD -> Unit
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

          // Optional Rich Markdown / Code / DOCX Preview
          val showTextPreview =
            data.textPreview != null &&
              data.specialtyKind != SpecialtyFileKind.LOG_FILE &&
              data.specialtyKind != SpecialtyFileKind.CONFIG_KV
          if (showTextPreview) {
            val preview = data.textPreview.orEmpty()
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
                    text =
                      when {
                        data.specialtyKind == SpecialtyFileKind.DOCX -> "Word (.docx) 文本预览"
                        data.isTextTruncated -> "文本预览（前 32 KB）"
                        else -> "Markdown / 文本预览"
                      },
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

          // Bottom Actions (No desktop bookmark for high-sensitivity KEYSTORE_SECRET)
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = {
                haptics.tick()
                keystorePassword = ""
                unlockedCerts = emptyList()
                onClose()
              },
              shape = ContinuousCapsule(),
              modifier = Modifier.weight(1f).testTag("quick_look_close_button"),
            ) {
              Text("关闭")
            }
            if (data.specialtyKind != SpecialtyFileKind.KEYSTORE_SECRET) {
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

  if (showPdfUnlockDialog) {
    PdfPasswordUnlockDialog(
      isVerifying = isPdfUnlocking,
      errorText = pdfUnlockError,
      onSubmitPassword = { pwd ->
        val uriStr = inspection?.uriString ?: return@PdfPasswordUnlockDialog
        scope.launch {
          isPdfUnlocking = true
          pdfUnlockError = null
          when (val prep = PdfEngine.prepareReadablePdf(context, Uri.parse(uriStr), password = pwd)) {
            is PdfPrepareResult.Ready -> {
              pdfTempUnlockedFile?.delete()
              pdfTempUnlockedFile = prep.tempFile
              pdfReadableUri = prep.readableUri
              pdfPageCount = prep.pageCount
              pdfPageIndex = 0
              showPdfUnlockDialog = false
              haptics.confirm()
              if (prep.pageCount > 0) {
                pdfBitmap = PdfEngine.renderPageBitmap(context, prep.readableUri, 0, targetWidthPx = 1080)
              }
            }
            PdfPrepareResult.WrongPassword,
            PdfPrepareResult.PasswordRequired -> {
              pdfUnlockError = "密码错误，请重试"
            }
            is PdfPrepareResult.Error -> {
              pdfUnlockError = prep.message
            }
          }
          isPdfUnlocking = false
        }
      },
      onDismiss = { showPdfUnlockDialog = false },
    )
  }
}

@Composable
private fun CalendarEventsSection(
  events: List<CalendarEventInfo>,
  onAddToCalendar: (CalendarEventInfo) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    events.forEach { ev ->
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ContinuousRoundedRectangle(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(14.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = ev.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          if (ev.startRaw.isNotBlank()) {
            Text(
              text = "时间：${ev.startRaw}" + if (ev.endRaw.isNotBlank()) " ~ ${ev.endRaw}" else "",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = TactKitMono,
            )
          }
          if (ev.location.isNotBlank()) {
            Text(text = "地点：${ev.location}", style = MaterialTheme.typography.bodySmall)
          }
          if (ev.organizer.isNotBlank()) {
            Text(text = "组织者：${ev.organizer}", style = MaterialTheme.typography.bodySmall)
          }
          if (ev.description.isNotBlank()) {
            Text(
              text = ev.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FilledTonalButton(
            onClick = { onAddToCalendar(ev) },
            shape = ContinuousCapsule(),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("添加到系统日历")
          }
        }
      }
    }
  }
}

@Composable
private fun CertificatesSection(
  certs: List<CertificateEntryInfo>,
  onCopyText: (String, String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    certs.forEach { cert ->
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ContinuousRoundedRectangle(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(14.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(cert.alias, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Surface(
              color =
                if (cert.isCurrentlyValid) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
              shape = ContinuousCapsule(),
            ) {
              Text(
                text = if (cert.isCurrentlyValid) "在有效期内" else "已过期/未生效",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (cert.isCurrentlyValid) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Text("主题：${cert.subject}", style = MaterialTheme.typography.bodySmall)
          Text("签发者：${cert.issuer}", style = MaterialTheme.typography.bodySmall)
          Text(
            "有效期：${cert.validFrom} 至 ${cert.validUntil}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = TactKitMono,
          )
          Text(
            "算法：${cert.signatureAlgorithm} · 序列号：${cert.serialHex}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = TactKitMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "SHA-256: ${cert.sha256Fingerprint}",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = TactKitMono,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onCopyText("证书指纹", cert.sha256Fingerprint) }) {
              Icon(Icons.Rounded.ContentCopy, contentDescription = "复制证书指纹", modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun KeystoreSecretSection(
  password: String,
  onPasswordChange: (String) -> Unit,
  unlockedCerts: List<CertificateEntryInfo>,
  errorMsg: String?,
  onUnlock: () -> Unit,
  onLockClear: () -> Unit,
  onCopyText: (String, String) -> Unit,
) {
  var showPwd by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = ContinuousRoundedRectangle(16.dp),
    modifier = Modifier.fillMaxWidth().testTag("quick_look_keystore_card"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text("高敏感密钥库（仅内存只读校验）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      }
      Text(
        text = "密码与解包信息仅暂存于当前内存，关闭窗口即自动清零；不提供收藏至桌面或上传功能。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("输入密钥库密码以临时解锁") },
        singleLine = true,
        visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { showPwd = !showPwd }) {
            Icon(
              if (showPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = "切换密码显示",
            )
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("quick_look_keystore_pwd"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = onUnlock,
          shape = ContinuousCapsule(),
          modifier = Modifier.weight(1f).testTag("quick_look_keystore_unlock_btn"),
        ) {
          Text("内存解锁查看")
        }
        if (unlockedCerts.isNotEmpty()) {
          OutlinedButton(
            onClick = onLockClear,
            shape = ContinuousCapsule(),
            modifier = Modifier.weight(1f),
          ) {
            Text("清除内存状态")
          }
        }
      }

      errorMsg?.let {
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }

      if (unlockedCerts.isNotEmpty()) {
        CertificatesSection(certs = unlockedCerts, onCopyText = onCopyText)
      }
    }
  }
}

@Composable
private fun ApkInfoSection(apk: ApkInspectionInfo) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = ContinuousRoundedRectangle(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Rounded.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
          text = "${apk.appLabel} (${apk.versionName})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "包名：${apk.packageName}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = TactKitMono,
      )
      Text(
        text = "versionCode: ${apk.versionCode} · minSdk: ${apk.minSdk ?: "?"} · targetSdk: ${apk.targetSdk ?: "?"}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = TactKitMono,
      )
      Text(
        text = "DEX 数量：${apk.dexCount} · 包内条目：${apk.entryCount} · 声明权限：${apk.permissions.size}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (apk.permissions.isNotEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = apk.permissions.take(15).joinToString("\n") { it.removePrefix("android.permission.") },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = TactKitMono,
            modifier = Modifier.padding(10.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun VCardSection(vcards: List<VCardContactInfo>) {
  val context = LocalContext.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    vcards.forEach { card ->
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ContinuousRoundedRectangle(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(14.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.ContactPage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
              Text(card.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              val orgTitle = listOf(card.organization, card.title).filter { it.isNotBlank() }.joinToString(" · ")
              if (orgTitle.isNotBlank()) {
                Text(orgTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
          card.phones.forEach { phone ->
            FilledTonalButton(
              onClick = {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                runCatching { context.startActivity(dialIntent) }
              },
              shape = ContinuousCapsule(),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(phone, fontFamily = TactKitMono)
            }
          }
          card.emails.forEach { email ->
            OutlinedButton(
              onClick = {
                val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                runCatching { context.startActivity(mailIntent) }
              },
              shape = ContinuousCapsule(),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(email, fontFamily = TactKitMono)
            }
          }
          if (card.address.isNotBlank()) {
            Text("地址：${card.address}", style = MaterialTheme.typography.bodySmall)
          }
          if (card.note.isNotBlank()) {
            Text("备注：${card.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun ConfigKvSection(entries: List<ConfigKvEntry>) {
  var revealSensitive by remember { mutableStateOf(false) }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = ContinuousRoundedRectangle(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("配置键值 (${entries.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = { revealSensitive = !revealSensitive }) {
          Icon(
            if (revealSensitive) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = "显示或隐藏敏感键值",
          )
        }
      }
      entries.take(80).forEach { entry ->
        val displayVal =
          if (entry.isSensitive && !revealSensitive && entry.value.isNotEmpty()) "••••••••"
          else entry.value
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = entry.key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = TactKitMono,
            fontWeight = FontWeight.Bold,
            color = if (entry.isSensitive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.45f),
          )
          Text(
            text = displayVal,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = TactKitMono,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f),
          )
        }
      }
    }
  }
}

@Composable
private fun LogSummarySection(summary: LogSummaryInfo) {
  var levelFilter by remember { mutableStateOf("ALL") }
  val filtered =
    remember(summary, levelFilter) {
      when (levelFilter) {
        "ERROR" -> summary.previewLines.filter {
          it.contains("ERROR", ignoreCase = true) || it.contains("FATAL", ignoreCase = true)
        }
        "WARN" -> summary.previewLines.filter { it.contains("WARN", ignoreCase = true) }
        else -> summary.previewLines
      }
    }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = ContinuousRoundedRectangle(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
          text = "日志分析 · 共 ${summary.totalLines} 行",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
      }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(
            selected = levelFilter == "ALL",
            onClick = { levelFilter = "ALL" },
            label = { Text("全部", maxLines = 1, softWrap = false) },
          )
        }
        item {
          FilterChip(
            selected = levelFilter == "ERROR",
            onClick = { levelFilter = "ERROR" },
            label = { Text("ERROR (${summary.errorCount})", maxLines = 1, softWrap = false) },
          )
        }
        item {
          FilterChip(
            selected = levelFilter == "WARN",
            onClick = { levelFilter = "WARN" },
            label = { Text("WARN (${summary.warnCount})", maxLines = 1, softWrap = false) },
          )
        }
      }
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = filtered.take(80).joinToString("\n").ifBlank { "（当前过滤级别下无匹配日志行）" },
          style = MaterialTheme.typography.labelSmall,
          fontFamily = TactKitMono,
          modifier = Modifier.padding(10.dp),
        )
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
