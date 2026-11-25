package com.xialia.tactkit.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.BatchRenameRule
import com.xialia.tactkit.engine.FileCategory
import com.xialia.tactkit.engine.FileManagerEngine
import com.xialia.tactkit.engine.ManagedFileEntry
import com.xialia.tactkit.ui.components.TactileCard
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.FileInspectionHelper
import java.io.File

enum class TactKitToolLaunch {
  PDF_TOOLKIT,
  PDF_ANNOTATE,
  DOCX_EDITOR,
  DATA_WORKSHOP,
  ARCHIVE_WORKSHOP,
  IMAGE_TOOLKIT,
  MEDIA_WORKSHOP,
  AI_VISION,
  AI_TEXT_ORGANIZE,
  QUICK_LOOK,
  HASH_WORKSHOP,
  CRYPTO_VAULT,
  LAN_SHARE,
}

data class RecommendedToolAction(
  val launch: TactKitToolLaunch,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
)

fun resolvePrimaryToolActions(entry: ManagedFileEntry): List<RecommendedToolAction> {
  if (entry.isDirectory) return emptyList()
  val ext = entry.extension.lowercase()
  return when {
    ext == "pdf" ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.PDF_TOOLKIT,
          "PDF 核心工具箱",
          "页面重组、合并拆分、压缩与密码解锁",
          Icons.Rounded.PictureAsPdf,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.PDF_ANNOTATE,
          "PDF 批注与签名",
          "高亮画笔标注与手写签名导出",
          Icons.Rounded.Draw,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 速览",
          "轻量分页预览与 SHA-256 校验",
          Icons.Rounded.Preview,
        ),
      )

    ext == "docx" ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.DOCX_EDITOR,
          "Docx 文档工坊",
          "离线阅读、段落编辑并导出 .docx",
          Icons.Rounded.Description,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 速览",
          "解析段落并校验文件指纹",
          Icons.Rounded.Preview,
        ),
      )

    ext in setOf("json", "csv", "yaml", "yml", "xml") ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.DATA_WORKSHOP,
          "结构化数据工坊",
          "格式校验、互转与表格编辑",
          Icons.Rounded.TableChart,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 速览",
          "源码与数据片段轻量预览",
          Icons.Rounded.Preview,
        ),
      )

    ext in setOf("zip", "7z", "tar", "gz", "tgz", "xz", "txz") ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.ARCHIVE_WORKSHOP,
          "压缩包工坊",
          "浏览目录、单文件或全量解压（支持加密 ZIP）",
          Icons.Rounded.Archive,
        ),
      )

    ext == "tactenc" ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.CRYPTO_VAULT,
          "AES-256 加密锁解锁",
          "输入口令还原 .tactenc 密文文件",
          Icons.Rounded.LockOpen,
        ),
      )

    ext in setOf("ics", "vcs", "vcf", "pem", "crt", "cer", "jks", "keystore", "p12", "pfx", "apk", "apks", "aab") ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 专项体检",
          "解析证书指纹、日历日程、APK 签名或密钥库",
          Icons.Rounded.Preview,
        ),
      )

    entry.category == FileCategory.IMAGES ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.IMAGE_TOOLKIT,
          "图片隐私与工坊",
          "抹除 EXIF、局部马赛克、水印与裁切",
          Icons.Rounded.HideImage,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.AI_VISION,
          "✨ AI 识图分析",
          "提取图中文字、表格或整理为便签",
          Icons.Rounded.AutoAwesome,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 速览",
          "双指缩放查看图片与元数据校验",
          Icons.Rounded.Preview,
        ),
      )

    entry.category == FileCategory.AUDIO_VIDEO ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.MEDIA_WORKSHOP,
          "音视频轻处理",
          "逐帧扒图、无损抽音轨与时间裁剪",
          Icons.Rounded.AudioFile,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 速览",
          "查看文件基础参数与指纹",
          Icons.Rounded.Preview,
        ),
      )

    ext in setOf("md", "txt") || entry.category == FileCategory.CODE_CONFIG ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 代码与文本预览",
          "轻量只读查看源码/Markdown 与文件指纹",
          Icons.Rounded.Preview,
        ),
        RecommendedToolAction(
          TactKitToolLaunch.AI_TEXT_ORGANIZE,
          "✨ AI 中枢智能整理",
          "摘要提炼或一键转为待办清单",
          Icons.Rounded.AutoAwesome,
        ),
      )

    else ->
      listOf(
        RecommendedToolAction(
          TactKitToolLaunch.QUICK_LOOK,
          "Quick Look 速览",
          "文件体检、SHA-256 与可识别内容预览",
          Icons.Rounded.Preview,
        ),
      )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
  entry: ManagedFileEntry,
  showLocateOption: Boolean,
  isPinnedDir: Boolean,
  onDismiss: () -> Unit,
  onLaunchTool: (ManagedFileEntry, TactKitToolLaunch) -> Unit,
  onLocateInTree: (ManagedFileEntry) -> Unit,
  onTogglePinDir: (ManagedFileEntry) -> Unit,
  onSystemShare: (ManagedFileEntry) -> Unit,
  onPackZip: (ManagedFileEntry) -> Unit,
  onRename: (ManagedFileEntry) -> Unit,
  onDelete: (ManagedFileEntry) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val primaryActions = remember(entry.id) { resolvePrimaryToolActions(entry) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp)
          .testTag("file_action_sheet"),
    ) {
      // Header info
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        FileEntryThumbnailOrBadge(entry = entry, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(3.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (!entry.isDirectory && entry.extension.isNotBlank()) {
              FileExtensionPill(entry.extension)
            }
            Text(
              text =
                if (entry.isDirectory) "${entry.childCount} 项"
                else FileInspectionHelper.formatBytes(entry.sizeBytes),
              fontFamily = TactKitMono,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "· ${entry.sourceLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      if (entry.relativePath.isNotBlank()) {
        Text(
          text = entry.relativePath,
          fontFamily = TactKitMono,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (!entry.isDirectory && primaryActions.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
          text = "用简工坊打开 / 原地加工",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        primaryActions.forEach { action ->
          SheetActionRow(
            icon = action.icon,
            title = action.title,
            subtitle = action.subtitle,
            isPrimary = true,
            onClick = {
              onDismiss()
              onLaunchTool(entry, action.launch)
            },
          )
        }
      }

      if (!entry.isDirectory) {
        Text(
          text = "通用安全与传输工具",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SheetActionRow(
          icon = Icons.Rounded.Fingerprint,
          title = "校验和与哈希计算",
          subtitle = "计算 MD5 / SHA-1 / SHA-256 / SHA-512",
          onClick = {
            onDismiss()
            onLaunchTool(entry, TactKitToolLaunch.HASH_WORKSHOP)
          },
        )
        if (entry.extension.lowercase() != "tactenc") {
          SheetActionRow(
            icon = Icons.Rounded.EnhancedEncryption,
            title = "AES-256 加密锁保护",
            subtitle = "使用口令加密并生成 .tactenc 密文文件",
            onClick = {
              onDismiss()
              onLaunchTool(entry, TactKitToolLaunch.CRYPTO_VAULT)
            },
          )
        }
        SheetActionRow(
          icon = Icons.Rounded.WifiTethering,
          title = "局域网 HTTP 快传",
          subtitle = "放入分享队列，同网设备扫码直取",
          onClick = {
            onDismiss()
            onLaunchTool(entry, TactKitToolLaunch.LAN_SHARE)
          },
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      if (showLocateOption && !entry.isSafBacked) {
        SheetActionRow(
          icon = Icons.AutoMirrored.Rounded.DriveFileMove,
          title = "定位到所在目录",
          subtitle = "跳转至目录树并高亮展示当前目录",
          onClick = {
            onDismiss()
            onLocateInTree(entry)
          },
        )
      }

      if (entry.isDirectory && !entry.isSafBacked) {
        SheetActionRow(
          icon = Icons.Rounded.PushPin,
          title = if (isPinnedDir) "取消快捷置顶" else "置顶该目录到快捷栏",
          subtitle = "将常用深层目录固定在文件管理顶部",
          onClick = {
            onDismiss()
            onTogglePinDir(entry)
          },
        )
      }

      if (!entry.isDirectory) {
        SheetActionRow(
          icon = Icons.Rounded.Share,
          title = "系统分享 / 用其他应用打开",
          subtitle = "通过 FileProvider 安全传递读取权限",
          onClick = {
            onDismiss()
            onSystemShare(entry)
          },
        )
      }

      if (!entry.isSafBacked) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          CompactSheetPillButton(
            icon = Icons.Rounded.FolderZip,
            label = "打包 ZIP",
            modifier = Modifier.weight(1f),
            onClick = {
              onDismiss()
              onPackZip(entry)
            },
          )
          CompactSheetPillButton(
            icon = Icons.Rounded.DriveFileRenameOutline,
            label = "重命名",
            modifier = Modifier.weight(1f),
            onClick = {
              onDismiss()
              onRename(entry)
            },
          )
          CompactSheetPillButton(
            icon = Icons.Rounded.DeleteOutline,
            label = "删除",
            isDanger = true,
            modifier = Modifier.weight(1f),
            onClick = {
              onDismiss()
              onDelete(entry)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun SheetActionRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  isPrimary: Boolean = false,
  onClick: () -> Unit,
) {
  TactileCard(
    onClick = onClick,
    containerColor =
      if (isPrimary) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
      else MaterialTheme.colorScheme.surfaceContainer,
    baseCorner = 16.dp,
    pressedCorner = 22.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(21.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun CompactSheetPillButton(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  isDanger: Boolean = false,
  onClick: () -> Unit,
) {
  TactileCard(
    onClick = onClick,
    containerColor =
      if (isDanger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
      else MaterialTheme.colorScheme.surfaceContainer,
    contentColor =
      if (isDanger) MaterialTheme.colorScheme.onErrorContainer
      else MaterialTheme.colorScheme.onSurface,
    baseCorner = 14.dp,
    pressedCorner = 20.dp,
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

@Composable
fun NewFolderDialog(
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("新建文件夹") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("文件夹名称") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name) },
        enabled = name.isNotBlank(),
      ) { Text("创建") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

@Composable
fun BatchRenameDialog(
  entries: List<ManagedFileEntry>,
  onDismiss: () -> Unit,
  onConfirm: (BatchRenameRule) -> Unit,
) {
  val isSingle = entries.size == 1
  var singleName by remember(entries) { mutableStateOf(entries.firstOrNull()?.name.orEmpty()) }
  var prefix by remember { mutableStateOf("") }
  var suffix by remember { mutableStateOf("") }
  var replaceFrom by remember { mutableStateOf("") }
  var replaceTo by remember { mutableStateOf("") }
  var addSequence by remember { mutableStateOf(!isSingle) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isSingle) "重命名" else "批量规则重命名 (${entries.size} 项)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isSingle) {
          OutlinedTextField(
            value = singleName,
            onValueChange = { singleName = it },
            label = { Text("新文件名（含扩展名）") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          OutlinedTextField(
            value = prefix,
            onValueChange = { prefix = it },
            label = { Text("添加前缀（可选）") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = suffix,
            onValueChange = { suffix = it },
            label = { Text("添加后缀（可选，置于扩展名前）") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = replaceFrom,
              onValueChange = { replaceFrom = it },
              label = { Text("查找词") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = replaceTo,
              onValueChange = { replaceTo = it },
              label = { Text("替换为") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = addSequence, onCheckedChange = { addSequence = it })
            Text("自动追加递增序号 (_01, _02...)", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            if (isSingle) {
              BatchRenameRule(singleNewName = singleName)
            } else {
              BatchRenameRule(
                prefix = prefix,
                suffix = suffix,
                replaceFrom = replaceFrom,
                replaceTo = replaceTo,
                addSequence = addSequence,
              )
            }
          )
        },
      ) { Text("应用") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

@Composable
fun PackZipDialog(
  itemCount: Int,
  defaultName: String,
  onDismiss: () -> Unit,
  onConfirm: (zipFileName: String, password: String?) -> Unit,
) {
  var fileName by remember { mutableStateOf(defaultName) }
  var enableAes by remember { mutableStateOf(false) }
  var password by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("打包为 ZIP ($itemCount 项)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = fileName,
          onValueChange = { fileName = it },
          label = { Text("压缩包名称（自动补全 .zip）") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = enableAes, onCheckedChange = { enableAes = it })
          Text("启用 AES-256 密码加密", style = MaterialTheme.typography.bodyMedium)
        }
        if (enableAes) {
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("设置解压密码") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val normalized =
            fileName.trim().let {
              if (it.lowercase().endsWith(".zip")) it else "$it.zip"
            }
          onConfirm(normalized, if (enableAes && password.isNotEmpty()) password else null)
        },
        enabled = fileName.isNotBlank() && (!enableAes || password.isNotEmpty()),
      ) { Text("开始打包") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

@Composable
fun DirectoryTargetPickerDialog(
  title: String,
  rootDir: File,
  initialDir: File,
  onDismiss: () -> Unit,
  onConfirmTarget: (File) -> Unit,
) {
  var browsingDir by remember(initialDir) { mutableStateOf(initialDir) }
  val subDirs =
    remember(browsingDir) {
      browsingDir.listFiles()
        ?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "当前：/${FileManagerEngine.toRelativePath(rootDir, browsingDir)}",
          fontFamily = TactKitMono,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (browsingDir.absolutePath != rootDir.absolutePath) {
          TextButton(
            onClick = {
              browsingDir.parentFile?.let { parent ->
                if (parent.absolutePath.startsWith(rootDir.absolutePath)) {
                  browsingDir = parent
                }
              }
            },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
          ) { Text("← 返回上一级") }
        }
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().height(220.dp),
        ) {
          items(subDirs, key = { it.absolutePath }) { sub ->
            Surface(
              onClick = { browsingDir = sub },
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Folder,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = sub.name,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onConfirmTarget(browsingDir) }) {
        Text("放到这里")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

@Composable
fun ConfirmDeleteDialog(
  count: Int,
  sampleName: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("确认彻底删除") },
    text = {
      Text(
        text =
          if (count == 1) "确定要从本地存储永久删除「$sampleName」吗？此操作不可撤销。"
          else "确定要从本地存储永久删除选中的 $count 个项目吗？此操作不可撤销。",
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) { Text("确认删除") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}
