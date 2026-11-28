package com.xialia.tactkit.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val title: String, val icon: ImageVector) {
  DOCUMENT_OFFICE("文档与办公", Icons.Rounded.Description),
  MEDIA_VISUAL("媒体与视觉", Icons.Rounded.Image),
  FILE_NETWORK("文件与网络", Icons.Rounded.Folder),
  DEV_ELECTRONICS("开发与电子", Icons.Rounded.Code),
  MEASURE_DAILY("测量与日常", Icons.Rounded.Straighten),
}

@Immutable
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
      // 1. 文档与办公
      ToolItem(
        id = "quick_note",
        name = "快速便签",
        subtitle = "Markdown 便签",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.EditNote,
      ),
      ToolItem(
        id = "quick_checklist",
        name = "待办清单",
        subtitle = "可勾选待办任务卡片",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Checklist,
      ),
      ToolItem(
        id = "voice_memo",
        name = "语音备忘录",
        subtitle = "实时波形录音与回放",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Mic,
      ),
      ToolItem(
        id = "doc_scanner",
        name = "文档扫描",
        subtitle = "拍照矫平生成多页PDF",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.DocumentScanner,
      ),
      ToolItem(
        id = "docx_editor",
        name = "Docx 文档工坊",
        subtitle = "离线阅读编辑与导出",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Description,
      ),
      ToolItem(
        id = "pdf_toolkit",
        name = "PDF 核心工具箱",
        subtitle = "合并拆分压缩与解锁",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.PictureAsPdf,
      ),
      ToolItem(
        id = "pdf_annotate",
        name = "PDF 批注与签名",
        subtitle = "高亮涂鸦与签名导出",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Draw,
      ),
      ToolItem(
        id = "quick_look",
        name = "Quick Look",
        subtitle = "文件体检哈希与预览",
        category = ToolCategory.DOCUMENT_OFFICE,
        icon = Icons.Rounded.Preview,
      ),

      // 2. 媒体与视觉
      ToolItem(
        id = "image_privacy",
        name = "图片隐私与工坊",
        subtitle = "去EXIF马赛克与水印",
        category = ToolCategory.MEDIA_VISUAL,
        icon = Icons.Rounded.HideImage,
      ),
      ToolItem(
        id = "image_long",
        name = "长图与转 PDF",
        subtitle = "多图长图拼接转PDF",
        category = ToolCategory.MEDIA_VISUAL,
        icon = Icons.Rounded.Collections,
      ),
      ToolItem(
        id = "media_extractor",
        name = "音视频轻处理",
        subtitle = "音轨提取抽帧与裁剪",
        category = ToolCategory.MEDIA_VISUAL,
        icon = Icons.Rounded.AudioFile,
      ),
      ToolItem(
        id = "led_clock",
        name = "LED 弹幕与时钟",
        subtitle = "手持弹幕与极简时钟",
        category = ToolCategory.MEDIA_VISUAL,
        icon = Icons.Rounded.Schedule,
      ),
      ToolItem(
        id = "ai_hub",
        name = "✨ AI 中枢",
        subtitle = "识图摘要与智能整理",
        category = ToolCategory.MEDIA_VISUAL,
        icon = Icons.Rounded.AutoAwesome,
      ),

      // 3. 文件与网络
      ToolItem(
        id = "file_manager",
        name = "文件管理",
        subtitle = "全盘分类检索整理",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.FolderOpen,
      ),
      ToolItem(
        id = "archive_box",
        name = "压缩包工坊",
        subtitle = "多格式压缩包打包解压",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.Archive,
      ),
      ToolItem(
        id = "data_workshop",
        name = "结构化数据工坊",
        subtitle = "表格互转校验与编辑",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.TableChart,
      ),
      ToolItem(
        id = "lan_share",
        name = "局域网 HTTP 快传",
        subtitle = "同网扫码双向传文件",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.WifiTethering,
      ),
      ToolItem(
        id = "net_diag",
        name = "局域网体检与 Ping",
        subtitle = "本机链路与端口探测",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.Lan,
      ),
      ToolItem(
        id = "dns_ssl",
        name = "DNS 与 SSL 检查",
        subtitle = "域名解析与证书检查",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.Dns,
      ),
      ToolItem(
        id = "wifi_channel",
        name = "Wi-Fi 信道分析",
        subtitle = "2.4G/5G拥挤度",
        category = ToolCategory.FILE_NETWORK,
        icon = Icons.Rounded.WifiFind,
      ),

      // 4. 开发与电子
      ToolItem(
        id = "hash_workshop",
        name = "校验和与哈希",
        subtitle = "流式计算哈希与比对",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Fingerprint,
      ),
      ToolItem(
        id = "crypto_vault",
        name = "AES-256 加密锁",
        subtitle = "文件便签口令加密锁",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.EnhancedEncryption,
      ),
      ToolItem(
        id = "codec_hash",
        name = "开发实用小工具",
        subtitle = "编码正则进制与子网",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Code,
      ),
      ToolItem(
        id = "electronics_calc",
        name = "电子工程计算器",
        subtitle = "色环电阻与分压计算",
        category = ToolCategory.DEV_ELECTRONICS,
        icon = Icons.Rounded.Memory,
      ),

      // 5. 测量与日常
      ToolItem(
        id = "level_compass",
        name = "指南针与水平仪",
        subtitle = "罗盘与双轴气泡水平仪",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.Explore,
      ),
      ToolItem(
        id = "metal_detector",
        name = "磁力计金属探测",
        subtitle = "磁场波形与触感告警",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.Sensors,
      ),
      ToolItem(
        id = "decibel_meter",
        name = "分贝噪音计",
        subtitle = "实时声压分贝波形计",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.GraphicEq,
      ),
      ToolItem(
        id = "light_lux",
        name = "光线照度计",
        subtitle = "环境照度传感器测量",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.LightMode,
      ),
      ToolItem(
        id = "physical_ruler",
        name = "屏幕物理直尺",
        subtitle = "物理厘米英寸双游标",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.Straighten,
      ),
      ToolItem(
        id = "morse_torch",
        name = "摩斯电码手电",
        subtitle = "SOS与文本闪烁信号",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.FlashlightOn,
      ),
      ToolItem(
        id = "battery_hardware",
        name = "电池与硬件参数",
        subtitle = "充放电功率与设备参数",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.BatteryChargingFull,
      ),
      ToolItem(
        id = "unit_calculator",
        name = "日常高频工具箱",
        subtitle = "计数器换算AA与番茄",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.Calculate,
      ),
      ToolItem(
        id = "qr_studio",
        name = "二维码工坊",
        subtitle = "离线制码与相册扫码",
        category = ToolCategory.MEASURE_DAILY,
        icon = Icons.Rounded.QrCode2,
      ),
    )

  fun findById(id: String): ToolItem? = allTools.firstOrNull { it.id == id }
}
