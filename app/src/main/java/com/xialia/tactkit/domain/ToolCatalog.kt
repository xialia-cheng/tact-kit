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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Sensors
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
  val isAvailable: Boolean,
  val roadmapNote: String = "",
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
        isAvailable = true,
      ),
      ToolItem(
        id = "quick_checklist",
        name = "待办清单",
        subtitle = "可勾选任务卡片",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Checklist,
        isAvailable = true,
      ),
      ToolItem(
        id = "voice_memo",
        name = "语音备忘录",
        subtitle = "原生 AAC 录音与本地回放",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Mic,
        isAvailable = true,
      ),
      ToolItem(
        id = "quick_look",
        name = "Quick Look",
        subtitle = "文件体检、SHA-256 与多页预览",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Preview,
        isAvailable = true,
      ),
      ToolItem(
        id = "ai_hub",
        name = "多模态 AI 中枢",
        subtitle = "BYOK 识图转 Markdown、PDF 摘要与清单整理",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.AutoAwesome,
        isAvailable = true,
      ),

      // PDF 与图像
      ToolItem(
        id = "pdf_toolkit",
        name = "PDF 核心工具箱",
        subtitle = "缩放预览、合并拆分、压缩与密码保护",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.PictureAsPdf,
        isAvailable = true,
      ),
      ToolItem(
        id = "pdf_annotate",
        name = "PDF 批注与签名",
        subtitle = "荧光笔高亮、自由涂鸦与手写签名导出",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.Draw,
        isAvailable = true,
      ),
      ToolItem(
        id = "image_privacy",
        name = "图片隐私与工坊",
        subtitle = "剥离 EXIF、马赛克、水印、长图拼接与取色",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.HideImage,
        isAvailable = true,
      ),

      // 媒体与压缩
      ToolItem(
        id = "media_extractor",
        name = "音视频无损轻处理",
        subtitle = "零重编码提取 .m4a 音轨与关键帧裁剪",
        category = ToolCategory.MEDIA_ARCHIVE,
        icon = Icons.Rounded.AudioFile,
        isAvailable = true,
      ),
      ToolItem(
        id = "archive_box",
        name = "压缩包工坊",
        subtitle = "ZIP / 7Z / TAR 本地打包与解压",
        category = ToolCategory.MEDIA_ARCHIVE,
        icon = Icons.Rounded.Archive,
        isAvailable = false,
        roadmapNote = "Phase 3 即将上线 · Commons Compress 离线归档",
      ),

      // 日常与传感器
      ToolItem(
        id = "level_compass",
        name = "水平仪与指南针",
        subtitle = "高精度三轴倾角与地磁校准",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Explore,
        isAvailable = false,
        roadmapNote = "Phase 3 即将上线 · Android SensorManager 实时融合",
      ),
      ToolItem(
        id = "unit_calculator",
        name = "工程单位换算",
        subtitle = "长度、面积、温度与数据存储换算",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Calculate,
        isAvailable = false,
        roadmapNote = "Phase 3 即将上线 · 离线公式求值器",
      ),

      // 开发与电子
      ToolItem(
        id = "codec_hash",
        name = "编码与哈希工坊",
        subtitle = "Base64 / URL / Hex / SHA-256 转换",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Code,
        isAvailable = false,
        roadmapNote = "Phase 4 即将上线 · 开发者高频编解码工作台",
      ),
      ToolItem(
        id = "electronics_calc",
        name = "电子与串口计算",
        subtitle = "电阻色环、分压与波特率误差",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Memory,
        isAvailable = false,
        roadmapNote = "Phase 4 即将上线 · 嵌入式硬件辅助工具",
      ),
    )

  fun findById(id: String): ToolItem? = allTools.firstOrNull { it.id == id }
}
