package com.xialia.tactkit.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val title: String, val icon: ImageVector) {
  DOCUMENT_OFFICE("文档与办公", Icons.Rounded.Description),
  PDF_IMAGE("PDF 与图像", Icons.Rounded.PictureAsPdf),
  MEDIA_ARCHIVE("媒体与压缩", Icons.Rounded.Archive),
  DAILY_SENSOR("日常与传感器", Icons.Rounded.Sensors),
  DEV_ELECTRONICS("开发与电子", Icons.Rounded.Code),
}

data class ToolItem(
  val id: String,
  val name: String,
  val subtitle: String,
  val category: ToolCategory,
  val icon: ImageVector,
  val isAvailable: Boolean = true,
)

object ToolCatalog {

  val allTools: List<ToolItem> =
    listOf(
      // 文档与办公
      ToolItem(
        id = "quick_note",
        name = "快速便签",
        subtitle = "Markdown 富文本、表格与代码便签",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.EditNote,
      ),
      ToolItem(
        id = "quick_checklist",
        name = "待办清单",
        subtitle = "可勾选任务卡片",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Checklist,
      ),
      ToolItem(
        id = "voice_memo",
        name = "语音备忘录",
        subtitle = "实时波形 AAC 录音与本地回放",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Mic,
      ),
      ToolItem(
        id = "quick_look",
        name = "Quick Look",
        subtitle = "文件体检、SHA-256 与多页预览",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Preview,
      ),
      ToolItem(
        id = "ai_hub",
        name = "多模态 AI 中枢",
        subtitle = "识图转 Markdown、PDF 摘要与清单整理",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.AutoAwesome,
      ),
      ToolItem(
        id = "docx_editor",
        name = "Docx 文档工坊",
        subtitle = "离线阅读、段落编辑与导出 Word (.docx)",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Description,
      ),
      ToolItem(
        id = "data_workshop",
        name = "结构化数据工坊",
        subtitle = "CSV / JSON / YAML / XML 互转、校验与表格编辑",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.TableChart,
      ),

      // PDF 与图像
      ToolItem(
        id = "pdf_toolkit",
        name = "PDF 核心工具箱",
        subtitle = "缩放预览、合并拆分、压缩与密码保护",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.PictureAsPdf,
      ),
      ToolItem(
        id = "pdf_annotate",
        name = "PDF 批注与签名",
        subtitle = "荧光笔高亮、自由涂鸦与手写签名导出",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.Draw,
      ),
      ToolItem(
        id = "image_privacy",
        name = "图片隐私与工坊",
        subtitle = "剥离 EXIF、马赛克、水印、长图拼接与取色",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.HideImage,
      ),

      // 媒体与压缩
      ToolItem(
        id = "media_extractor",
        name = "音视频无损轻处理",
        subtitle = "零重编码提取 .m4a 音轨、抽帧与裁剪",
        category = ToolCategory.MEDIA_ARCHIVE,
        icon = Icons.Rounded.AudioFile,
      ),
      ToolItem(
        id = "archive_box",
        name = "压缩包工坊",
        subtitle = "ZIP (AES-256) / 7Z / TAR / GZ / XZ 打包与解压",
        category = ToolCategory.MEDIA_ARCHIVE,
        icon = Icons.Rounded.Archive,
      ),

      // 日常与传感器
      ToolItem(
        id = "level_compass",
        name = "指南针与水平仪",
        subtitle = "高精度罗盘与双轴气泡水平仪",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Explore,
      ),
      ToolItem(
        id = "metal_detector",
        name = "磁力计金属探测",
        subtitle = "μT 磁场实时波形与渐进触感告警",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Sensors,
      ),
      ToolItem(
        id = "unit_calculator",
        name = "日常高频工具箱",
        subtitle = "音量键计数器、单位换算、AA 分摊与番茄钟",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Calculate,
      ),
      ToolItem(
        id = "qr_studio",
        name = "二维码工坊",
        subtitle = "离线生成、相册识码与相机实时扫描",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.QrCode2,
      ),

      // 开发与电子
      ToolItem(
        id = "hash_workshop",
        name = "校验和与哈希",
        subtitle = "MD5 / SHA 全家族流式计算、文本哈希与批量比对",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Fingerprint,
      ),
      ToolItem(
        id = "crypto_vault",
        name = "AES-256 加密锁",
        subtitle = "文件与私密便签口令加密保护",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.EnhancedEncryption,
      ),
      ToolItem(
        id = "codec_hash",
        name = "开发实用小工具",
        subtitle = "Base64 / URL 去追踪 / JWT / 正则 / 进制 / CIDR",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Code,
      ),
      ToolItem(
        id = "electronics_calc",
        name = "电子工程计算器",
        subtitle = "4/5 环色环电阻交互与电阻分压计算",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Memory,
      ),
      ToolItem(
        id = "lan_share",
        name = "局域网 HTTP 快传",
        subtitle = "同 Wi-Fi 扫码浏览器双向传文件，一键停止",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.WifiTethering,
      ),
    )

  fun findById(id: String): ToolItem? = allTools.firstOrNull { it.id == id }
}
