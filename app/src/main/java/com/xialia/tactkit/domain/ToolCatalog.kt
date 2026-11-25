package com.xialia.tactkit.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.TableChart
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
  val isAvailableInPhase1: Boolean,
  val roadmapNote: String = "",
)

object ToolCatalog {

  val allTools: List<ToolItem> =
    listOf(
      // 文档与办公
      ToolItem(
        id = "quick_note",
        name = "快速便签",
        subtitle = "Markdown / 代码便签卡片",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.EditNote,
        isAvailableInPhase1 = true,
      ),
      ToolItem(
        id = "quick_checklist",
        name = "待办清单",
        subtitle = "可勾选任务卡片",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Checklist,
        isAvailableInPhase1 = true,
      ),
      ToolItem(
        id = "quick_look",
        name = "Quick Look",
        subtitle = "本地文件体检与 SHA-256 预览",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Preview,
        isAvailableInPhase1 = true,
      ),
      ToolItem(
        id = "office_parser",
        name = "Office 文档解析",
        subtitle = "Word / Excel 离线提取与转 Markdown",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.TableChart,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 2 即将上线 · 离线文档引擎 (Apache POI)",
      ),

      // PDF 与图像
      ToolItem(
        id = "pdf_toolkit",
        name = "PDF 工具箱",
        subtitle = "合并、拆分、页面重排与签名",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.PictureAsPdf,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 2 即将上线 · 本地 PDF 引擎 (PdfBox-Android)",
      ),
      ToolItem(
        id = "image_privacy",
        name = "图片隐私抹除",
        subtitle = "清除 EXIF GPS 与添加防盗水印",
        category = ToolCategory.PDF_IMAGE,
        icon = Icons.Rounded.HideImage,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 2 即将上线 · 零网络本地图像脱敏",
      ),

      // 媒体与压缩
      ToolItem(
        id = "media_extractor",
        name = "音视频音轨提取",
        subtitle = "本地无损剥离音频流与转码",
        category = ToolCategory.MEDIA_ARCHIVE,
        icon = Icons.Rounded.AudioFile,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 3 即将上线 · 系统 MediaExtractor / MediaCodec",
      ),
      ToolItem(
        id = "archive_box",
        name = "压缩包工坊",
        subtitle = "ZIP / 7Z / TAR 本地打包与解压",
        category = ToolCategory.MEDIA_ARCHIVE,
        icon = Icons.Rounded.Archive,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 3 即将上线 · Commons Compress 离线归档",
      ),

      // 日常与传感器
      ToolItem(
        id = "level_compass",
        name = "水平仪与指南针",
        subtitle = "高精度三轴倾角与地磁校准",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Explore,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 3 即将上线 · Android SensorManager 实时融合",
      ),
      ToolItem(
        id = "unit_calculator",
        name = "工程单位换算",
        subtitle = "长度、面积、温度与数据存储换算",
        category = ToolCategory.DAILY_SENSOR,
        icon = Icons.Rounded.Calculate,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 3 即将上线 · 离线公式求值器",
      ),

      // 开发与电子
      ToolItem(
        id = "codec_hash",
        name = "编码与哈希工坊",
        subtitle = "Base64 / URL / Hex / SHA-256 转换",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Code,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 4 即将上线 · 开发者高频编解码工作台",
      ),
      ToolItem(
        id = "electronics_calc",
        name = "电子与串口计算",
        subtitle = "电阻色环、分压与波特率误差",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Memory,
        isAvailableInPhase1 = false,
        roadmapNote = "Phase 4 即将上线 · 嵌入式硬件辅助工具",
      ),
    )

  fun findById(id: String): ToolItem? = allTools.firstOrNull { it.id == id }
}
