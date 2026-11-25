package com.xialia.tactkit.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactileCard
import com.xialia.tactkit.ui.components.tactileExpandVertically
import com.xialia.tactkit.ui.components.tactileShrinkVertically
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import com.xialia.tactkit.util.AppHaptics

private data class HelpTipItem(
  val title: String,
  val steps: String,
  val keywords: String,
  val targetToolId: String? = null,
)

private data class HelpCategory(
  val id: String,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val items: List<HelpTipItem>,
)

private val HELP_CATEGORIES =
  listOf(
    HelpCategory(
      id = "getting_started",
      title = "入门与桌面",
      subtitle = "混合卡片桌面、悬浮球置顶、Glance 小组件与聚光灯引导",
      icon = Icons.Rounded.Home,
      items =
        listOf(
          HelpTipItem(
            title = "混合卡片桌面（清单 / 便签 / 语音备忘）",
            steps = "统一在一个工作台管理待办、Markdown 便签与录音：\n1. 在主页点击「新建清单」「新建便签」或「语音备忘」。\n2. 全部数据 100% 离线保存在本地 Room 数据库。\n3. 支持卡片置顶、分类筛选与顶部全局搜索。",
            keywords = "桌面 卡片 清单 便签 语音 markdown",
            targetToolId = "quick_note",
          ),
          HelpTipItem(
            title = "液态玻璃悬浮球与自由置顶",
            steps = "随时呼出全部 5 大分类工具并自定义常用栏：\n1. 点击右下角液态玻璃悬浮球展开工具抽屉。\n2. 点击任意工具右侧的图钉图标即可无限置顶到主页快捷条。\n3. 如需省电，可在「我的与设置 → 渲染与省电」切换 M3 半透明卡片。",
            keywords = "悬浮球 置顶 液态玻璃 工具抽屉",
            targetToolId = "home_dashboard",
          ),
          HelpTipItem(
            title = "Glance 桌面小组件",
            steps = "无需打开 App，在手机桌面直接勾选待办与速记：\n1. 长按手机主屏幕空白处 → 选择「小组件」。\n2. 找到「简工坊」清单或快捷入口小组件拖到桌面。\n3. 点击小组件上的待办条目即可实时同步状态。",
            keywords = "小组件 widget glance 桌面 清单",
            targetToolId = "home_dashboard",
          ),
          HelpTipItem(
            title = "首次聚光灯引导重播",
            steps = "想重新熟悉主页 5 大核心区域与快捷键位置：\n1. 点击下方「去试试」或本页底部的「重播首次引导教程」。\n2. 自动返回主页并按 5 步聚光灯高亮逐一引导。\n3. 引导结束后不会在冷启动时重复弹出。",
            keywords = "引导 聚光灯 教程 重播 新手",
            targetToolId = "__replay_onboarding__",
          ),
        ),
    ),
    HelpCategory(
      id = "files_cross_app",
      title = "文件与跨 App 选文件",
      subtitle = "独立文件管理器、第三方 App 选文件与 Quick Look 悬浮预览",
      icon = Icons.Rounded.FolderOpen,
      items =
        listOf(
          HelpTipItem(
            title = "独立文件管理器（时间流 / 精简目录 / QQ微信 / 清理）",
            steps = "直接遍历物理目录，支持全后缀索引与空间释放：\n1. 在「最近文件」按修改时间倒序浏览，点「图片」自动切换 3 列大图。\n2. 在「目录树」开启「精简目录」折叠空目录，或点击「关联 QQ/微信目录」SAF 授权。\n3. 在「空间清理」扫描 >50MB/>100MB 大文件与空文件夹并一键清理。",
            keywords = "文件管理 最近 目录树 精简 qq 微信 空间清理 大文件",
            targetToolId = "file_manager",
          ),
          HelpTipItem(
            title = "在第三方 App 中通过「简工坊 · 全盘文件」选文件",
            steps = "突破系统默认选择器限制，直接挑选任意后缀文件：\n1. 在微信、浏览器或其他应用点击「选择文件 / 上传附件」。\n2. 打开系统文件选择器左上角菜单，选择「简工坊 · 全盘文件」。\n3. 直接挑选 .jks、.pem、.apk、代码或 QQ/微信下载文件。",
            keywords = "跨app 选文件 documentsprovider 微信 浏览器 全盘文件",
            targetToolId = "file_manager",
          ),
          HelpTipItem(
            title = "Quick Look 外部悬浮预览",
            steps = "不离开当前应用即可速览文档并生成校验指纹：\n1. 在其他 App 点击「分享」，选择「简工坊 · Quick Look」。\n2. 或点击下方「去试试」直接挑选本地文件弹窗速览。\n3. 支持加密 PDF、Markdown、代码与图片预览及一键导入工作台。",
            keywords = "quick look 速览 悬浮 分享 预览",
            targetToolId = "quick_look",
          ),
        ),
    ),
    HelpCategory(
      id = "pdf_scanner_office",
      title = "文档与 PDF",
      subtitle = "PDF 解锁与批注、合并拆分压缩加密、CameraX 扫描与 .docx 编辑",
      icon = Icons.Rounded.PictureAsPdf,
      items =
        listOf(
          HelpTipItem(
            title = "PDF 预览与加密 PDF 原地弹窗解锁",
            steps = "纯本地内存解密并高清渲染受密码保护的 PDF：\n1. 打开任意加密 PDF 文件，系统会自动弹出密码输入框。\n2. 输入口令后直接在本地内存解锁并逐页渲染查看。\n3. 解锁后的页面可继续执行导出、拆分或移除密码操作。",
            keywords = "pdf 加密 解锁 密码 预览",
            targetToolId = "pdf_toolkit",
          ),
          HelpTipItem(
            title = "PDF 合并 / 拆分 / 压缩 / 加密",
            steps = "一站式离线处理多份 PDF 文档：\n1. 在 PDF 核心工具箱选择要处理的 PDF 文件。\n2. 切换对应功能：多文件合并、按页码拆分、体积压缩或设密码加密。\n3. 导出新文件至本地目录并可一键分享或快传。",
            keywords = "pdf 合并 拆分 压缩 加密 导出",
            targetToolId = "pdf_toolkit",
          ),
          HelpTipItem(
            title = "Canvas 手写批注与签名",
            steps = "在 PDF 页面上自由涂鸦、荧光高亮与手写签名：\n1. 打开目标 PDF 进入批注画布。\n2. 选择画笔颜色、粗细与透明度，支持多步撤回。\n3. 点击导出即可将批注永久栅格化合成为新 PDF。",
            keywords = "pdf 批注 手写 签名 高亮 画笔",
            targetToolId = "pdf_annotate",
          ),
          HelpTipItem(
            title = "CameraX 文档扫描仪（四角透视与多页导出）",
            steps = "将纸质文件拍成平整清晰的多页电子扫描件：\n1. 连续拍摄纸质文档或从相册导入多张照片。\n2. 拖动四个角点进行透视拉直，并选择黑白/增强文档滤镜。\n3. 一键导出多页 PDF 后可直达「批注」「加密」或「局域网快传」。",
            keywords = "文档扫描 camerax 透视 拉直 多页 pdf",
            targetToolId = "doc_scanner",
          ),
          HelpTipItem(
            title = "Word (.docx) 编辑与导出",
            steps = "无需安装臃肿办公套件即可离线读写 .docx：\n1. 打开本地 .docx 文件或新建空白文档。\n2. 直接编辑正文段落与标题样式并实时预览。\n3. 一键保存覆盖或另存为新的 .docx 文档。",
            keywords = "docx word 文档 编辑 导出 办公",
            targetToolId = "docx_editor",
          ),
        ),
    ),
    HelpCategory(
      id = "media_format_archive",
      title = "媒体、格式与压缩",
      subtitle = "图片打码去 EXIF、长图转 PDF、取色、音视频抽帧、压缩包与数据转换",
      icon = Icons.Rounded.Archive,
      items =
        listOf(
          HelpTipItem(
            title = "图片隐私马赛克与剥离 EXIF",
            steps = "发图前彻底涂抹敏感信息并抹除拍摄定位数据：\n1. 选择图片进入「马赛克水印」页，固定画布不漂移。\n2. 使用矩形或画笔涂抹人脸/订单号，可调节像素块大小。\n3. 保存时自动剥离相机型号、时间与 GPS 等 EXIF 隐私字段。",
            keywords = "马赛克 exif 隐私 打码 水印 抹除",
            targetToolId = "image_privacy",
          ),
          HelpTipItem(
            title = "长图拼接与图片转 PDF",
            steps = "将聊天记录或网页截图无缝拼为长图或 PDF：\n1. 进入「长图与 PDF」Tab，按顺序添加多张图片。\n2. 拖拽调整排列顺序，选择纵向拼长图或按页生成 PDF。\n3. 一键导出高清成品并自动记录到工作台。",
            keywords = "长图 拼接 图片转pdf 多图",
            targetToolId = "image_long",
          ),
          HelpTipItem(
            title = "像素取色器",
            steps = "精准拾取图片任意像素的 HEX / RGB / HSL 色值：\n1. 进入图片工坊的「像素取色」Tab 并加载图片。\n2. 在画布上滑动准星，放大镜实时锁定目标像素点。\n3. 一键复制 HEX 或 RGB 色值用于设计或开发。",
            keywords = "取色 像素 hex rgb 颜色 吸管",
            targetToolId = "image_color_picker",
          ),
          HelpTipItem(
            title = "视频无损提取音频与逐帧扒图",
            steps = "纯本地解复用提取音轨并精准抓取高清视频帧：\n1. 导入本地视频文件。\n2. 切换功能抽离无损音轨（M4A/AAC）、按时间段无损裁剪，或拖动逐帧微调。\n3. 将抓取的原画质帧保存为 PNG，或一键送去打码/取色/AI 识图。",
            keywords = "视频 音频 提取 逐帧 扒图 裁剪 抽帧",
            targetToolId = "media_extractor",
          ),
          HelpTipItem(
            title = "全能压缩包（ZIP AES-256 / 7Z / TAR / GZ）",
            steps = "支持多格式归档预览与高强度加密打包：\n1. 打开压缩包工坊浏览 ZIP、7Z、TAR、GZ/TXZ 内部目录。\n2. 支持带密码 ZIP (AES-256) 与 7Z 解压至本地。\n3. 多选文件或目录一键加密打包生成标准 ZIP。",
            keywords = "zip 7z tar gz 压缩 解压 aes 密码",
            targetToolId = "archive_box",
          ),
          HelpTipItem(
            title = "CSV / JSON / YAML / XML 互转与表格编辑",
            steps = "离线校验、格式化与转换各类结构化配置文件：\n1. 粘贴文本或导入本地 CSV / JSON / YAML / XML 文件。\n2. 切换「表格预览」直观编辑单元格，或一键互转目标格式。\n3. 支持语法校验与一键复制/导出。",
            keywords = "csv json yaml xml 互转 表格 编辑 格式化",
            targetToolId = "data_workshop",
          ),
        ),
    ),
    HelpCategory(
      id = "ai_network_transfer",
      title = "AI 与网络传输",
      subtitle = "多模态 AI 中枢、局域网 HTTP 快传、端口诊断、DNS/SSL 与 Wi-Fi 信道",
      icon = Icons.Rounded.AutoAwesome,
      items =
        listOf(
          HelpTipItem(
            title = "多模态 ✨ AI 中枢（预设 / 识图 / 摘要 / 清单）",
            steps = "自带密钥、硬件加密保管的全能离线前端：\n1. 在「我的与设置 → AI 中枢设置」一键选用 DeepSeek / OpenAI / 硅基流动 / Ollama 本地预设并保存 Key（AndroidKeyStore AES-GCM 加密）。\n2. 在 AI 中枢切换「识图提取」「PDF 问答」或「整理清单」。\n3. 生成的摘要或清单可一键保存为桌面卡片。",
            keywords = "ai deepseek openai 硅基流动 ollama 识图 摘要 清单",
            targetToolId = "ai_hub",
          ),
          HelpTipItem(
            title = "高速双向局域网 HTTP 快传",
            steps = "同局域网下手机与电脑免装客户端双向高速互传：\n1. 连接同一 Wi-Fi 或手机热点，开启本地 HTTP 服务。\n2. 电脑或另一台手机浏览器扫码或访问局域网地址。\n3. 即可双向拖拽上传下载文件或发送剪贴板文本。",
            keywords = "局域网 快传 http 扫码 互传 wifi",
            targetToolId = "lan_share",
          ),
          HelpTipItem(
            title = "局域网 & 端口诊断",
            steps = "快速排查本机网络状态与目标主机端口连通性：\n1. 查看本机内网 IP、网关、子网掩码与活跃网卡信息。\n2. 输入目标 IP 或域名进行 Ping 延迟探测与常用端口扫描。\n3. 实时输出延迟统计与端口开放状态。",
            keywords = "局域网 ping 端口 扫描 诊断 ip",
            targetToolId = "net_diag",
          ),
          HelpTipItem(
            title = "域名 DNS 与 SSL 证书查验",
            steps = "一键核验域名解析记录与 HTTPS 证书有效性：\n1. 输入目标域名（如 example.com）。\n2. 查看 A/AAAA/CNAME 解析结果与服务器 IP。\n3. 展示完整 SSL 证书链、颁发机构与剩余过期天数。",
            keywords = "dns ssl 证书 https 域名 解析",
            targetToolId = "dns_ssl",
          ),
          HelpTipItem(
            title = "Wi-Fi 信道拥挤度分析",
            steps = "找出干扰最小的 2.4G / 5G 路由器最佳信道：\n1. 授予位置权限并开启系统位置开关后点击扫描。\n2. 查看周边热点分布图与各信道拥挤度评分。\n3. 根据推荐信道调整路由器以降低无线丢包。",
            keywords = "wifi 信道 2.4g 5g 拥挤 干扰",
            targetToolId = "wifi_channel",
          ),
        ),
    ),
    HelpCategory(
      id = "dev_electronics_specialty",
      title = "开发、电子与冷门工具",
      subtitle = "4 Tab 哈希、AES-256 保险箱、开发编码正则、色环电阻与冷门文件验看",
      icon = Icons.Rounded.Code,
      items =
        listOf(
          HelpTipItem(
            title = "4 Tab 校验和与哈希工坊",
            steps = "流式计算大文件或文本摘要，精准校验文件完整性：\n1. 选择文件或输入文本，查看 MD5、SHA-1、SHA-256、SHA-512。\n2. 粘贴官方发布的校验值即可实时高亮比对是否一致。\n3. 支持大文件流式计算，不占满内存。",
            keywords = "哈希 hash md5 sha1 sha256 sha512 校验和",
            targetToolId = "hash_workshop",
          ),
          HelpTipItem(
            title = "AES-256 文件与便签锁",
            steps = "使用高强度口令本地加密敏感文件或私密文本：\n1. 选择「文件加密」或「文本加密」Tab 并设置口令。\n2. 生成 .tactenc 加密文件或密文串（AES-256-GCM）。\n3. 仅凭正确口令可在本工具内原地解密还原。",
            keywords = "aes 256 加密 保险箱 tactenc 便签锁",
            targetToolId = "crypto_vault",
          ),
          HelpTipItem(
            title = "编码 / URL 清理 / 正则 / JWT / CIDR",
            steps = "开发者高频离线随手工具集合：\n1. 切换 Tab 进行 Base64/URL/Unicode 编解码与追踪参数清理。\n2. 实时测试正则表达式高亮匹配，或解码校验 JWT Token 载荷。\n3. 输入 IP/掩码快速计算 CIDR 网段、广播地址与可用主机数。",
            keywords = "编码 base64 url 正则 regex jwt cidr 子网",
            targetToolId = "codec_hash",
          ),
          HelpTipItem(
            title = "色环电阻与分压计算",
            steps = "硬件调试与电路设计必备速算器：\n1. 点选 4 环或 5 环电阻色环颜色，实时换算阻值与误差。\n2. 切换到分压/LED 限流或欧姆定律 Tab 输入参数。\n3. 即时输出标准标称阻值与功率建议。",
            keywords = "电阻 色环 分压 电子 硬件 欧姆",
            targetToolId = "electronics_calc",
          ),
          HelpTipItem(
            title = ".ics 日程与 .pem / .jks / .apk 只读验看",
            steps = "无需外部专业软件即可安全验看特殊后缀文件：\n1. 在文件管理器中直接点击 .ics、.pem、.jks 或 .apk 文件。\n2. 自动识别类型并只读解析日程详情、证书指纹/有效期或 APK 签名与权限。\n3. 支持一键复制 SHA-256 指纹与包名信息。",
            keywords = "ics pem jks apk 证书 签名 日程 验看",
            targetToolId = "file_manager",
          ),
        ),
    ),
    HelpCategory(
      id = "measure_daily",
      title = "测量与日常",
      subtitle = "分贝仪、尺子、摩斯手电、罗盘、金属探测、照度、电池、LED、番茄钟与扫码",
      icon = Icons.Rounded.Straighten,
      items =
        listOf(
          HelpTipItem(
            title = "环境分贝仪",
            steps = "利用麦克风实时采样并绘制声压分贝波形：\n1. 授权录音权限后自动开始实时采集环境音量。\n2. 查看当前 dB 数值、峰值/平均值与噪声等级提示。\n3. 支持暂停/重置统计以便对比不同环境。",
            keywords = "分贝 db 噪音 麦克风 声压",
            targetToolId = "decibel_meter",
          ),
          HelpTipItem(
            title = "屏幕物理尺子",
            steps = "按真实屏幕物理 DPI 校准的随身双游标直尺：\n1. 在屏幕边缘对齐被测物体。\n2. 拖动双游标即可精准读取厘米 (cm) 与英寸 (inch) 间距。\n3. 若与标准尺有微差，可点击校准微调屏幕 DPI。",
            keywords = "尺子 物理 厘米 英寸 测量 dpi",
            targetToolId = "physical_ruler",
          ),
          HelpTipItem(
            title = "手电筒摩斯电码 / SOS",
            steps = "通过手机闪光灯发射标准国际摩斯电码光信号：\n1. 输入英文或数字文本，预览自动转换的点划节奏。\n2. 点击「发射」控制闪光灯按节奏闪烁。\n3. 紧急情况下可直接点击「SOS」一键循环求救信号。",
            keywords = "摩斯 电码 手电筒 sos 闪光灯",
            targetToolId = "morse_torch",
          ),
          HelpTipItem(
            title = "指南针 / 水平仪",
            steps = "高精度方位角罗盘与双轴气泡水平仪：\n1. 切换「指南针」查看真北/磁北方位角与经纬参考。\n2. 切换「水平仪」将手机贴合桌面或相框，观察双轴倾斜度数。\n3. 水平归零时触发清脆触感提示。",
            keywords = "指南针 罗盘 水平仪 角度 倾斜",
            targetToolId = "level_compass",
          ),
          HelpTipItem(
            title = "磁力计金属探测",
            steps = "调用三轴磁力传感器探测墙体暗线或金属物体：\n1. 打开工具观察当前环境基准磁场强度 (µT)。\n2. 将手机背部缓慢贴近待测墙面或物体移动。\n3. 磁场突变超过阈值时将触发波形峰值与震动告警。",
            keywords = "金属探测 磁力计 磁场 传感器",
            targetToolId = "metal_detector",
          ),
          HelpTipItem(
            title = "光线照度计",
            steps = "实时读取环境光传感器测得的勒克斯 (Lux) 照度：\n1. 将手机屏幕朝向待测光源或桌面阅读区域。\n2. 实时查看当前照度数值与护眼/摄影亮度参考区间。\n3. 支持记录最低/最高照度波动。",
            keywords = "光照度 lux 亮度 传感器 护眼",
            targetToolId = "light_lux",
          ),
          HelpTipItem(
            title = "电池充电功率与硬件面板",
            steps = "实时监测充放电功率 W 与设备底层硬件参数：\n1. 插上充电器后查看实时电流 (mA)、电压 (V) 与实时瓦数 (W)。\n2. 监测电池温度、健康度与充放电状态。\n3. 下方附带 CPU 架构、内存与屏幕刷新率等硬件概览。",
            keywords = "电池 充电 功率 瓦数 电流 硬件",
            targetToolId = "battery_hardware",
          ),
          HelpTipItem(
            title = "手持 LED 弹幕与大时钟",
            steps = "全屏高对比滚动弹幕灯牌与桌面翻页大时钟：\n1. 输入任意祝福语或接机提示词，自定义文字大小、颜色与滚动速度。\n2. 点击「全屏灯牌」进入无遮挡横屏/竖屏滚动展示。\n3. 也可切换为极简桌面大时钟配合夜间使用。",
            keywords = "led 弹幕 灯牌 时钟 全屏",
            targetToolId = "led_clock",
          ),
          HelpTipItem(
            title = "番茄钟实况通知（含计数器与单位换算）",
            steps = "专注计时与日常高频小工具三合一：\n1. 进入「番茄钟与倒计时」Tab，启动 25 分钟专注并支持通知栏实况进度。\n2. 切换到「持久化计数器」记录任意次数（自动保存不断电）。\n3. 切换到「换算与 AA 分摊」快速换算单位或聚餐AA均摊。",
            keywords = "番茄钟 计时器 实况通知 计数器 单位换算 aa",
            targetToolId = "pomodoro_timer",
          ),
          HelpTipItem(
            title = "二维码生成与扫码",
            steps = "100% 离线制作高清二维码与快速识别相册/镜头码：\n1. 在「制码」Tab 输入文本、网址或 Wi-Fi 信息生成高清二维码并保存。\n2. 切换到「扫码」Tab 直接对准二维码或从相册选图识别。\n3. 识别结果支持一键复制或打开链接。",
            keywords = "二维码 qr 扫码 制码 相册",
            targetToolId = "qr_studio",
          ),
        ),
    ),
    HelpCategory(
      id = "troubleshooting_feedback",
      title = "故障排除与反馈",
      subtitle = "权限排查、GitHub Issue、反馈邮件与重播首次引导",
      icon = Icons.Rounded.BugReport,
      items =
        listOf(
          HelpTipItem(
            title = "所有文件访问与 SAF 双模式排查",
            steps = "文件管理器看不到全盘文件或 QQ/微信目录时：\n1. 前往「我的与设置 → 所有文件访问权限」确认已开启开关。\n2. 若不愿开启全盘权限，也可在文件管理顶部点「关联 QQ/微信目录」通过 SAF 单独授权。\n3. 所有文件均纯本地处理，不经过任何云端服务器。",
            keywords = "所有文件 saf photo picker 权限 本地 文件看不到",
            targetToolId = "settings",
          ),
          HelpTipItem(
            title = "麦克风、相机与定位权限排查（按需动态申请）",
            steps = "仅在用到对应硬件能力时请求权限，拒绝后其余功能不受影响：\n1. 分贝仪/语音备忘无声：请在系统应用权限中开启麦克风 (RECORD_AUDIO)。\n2. 文档扫描/扫码黑屏：请开启相机 (CAMERA) 权限。\n3. Wi-Fi 信道列表为空：Android 要求同时开启定位权限 (ACCESS_FINE_LOCATION) 与系统位置开关。",
            keywords = "权限 排查 相机 麦克风 定位 wifi 分贝",
            targetToolId = "settings",
          ),
        ),
    ),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTipsScreen(
  onLaunchTool: (String) -> Unit,
  onReplayOnboarding: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current

  val quickLookLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        AppHaptics.click(view)
        val intent =
          Intent(context, QuickLookActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        context.startActivity(intent)
      }
    }

  var isSearchOpen by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  val expandedCategories = remember {
    mutableStateMapOf<String, Boolean>().apply {
      put("getting_started", true)
    }
  }

  val normalizedQuery = searchQuery.trim().lowercase()
  val filteredCategories =
    remember(normalizedQuery) {
      if (normalizedQuery.isEmpty()) {
        HELP_CATEGORIES
      } else {
        HELP_CATEGORIES.mapNotNull { cat ->
          val catMatches =
            cat.title.lowercase().contains(normalizedQuery) ||
              cat.subtitle.lowercase().contains(normalizedQuery)
          val matchingItems =
            cat.items.filter { item ->
              item.title.lowercase().contains(normalizedQuery) ||
                item.steps.lowercase().contains(normalizedQuery) ||
                item.keywords.lowercase().contains(normalizedQuery)
            }
          when {
            matchingItems.isNotEmpty() -> cat.copy(items = matchingItems)
            catMatches -> cat
            else -> null
          }
        }
      }
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
          navigationIcon = {
            IconButton(
              onClick = {
                AppHaptics.click(view)
                onNavigateBack()
              },
              modifier = Modifier.testTag("help_back_btn"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          title = {
            Text(
              text = "帮助和提示",
              fontWeight = FontWeight.Bold,
            )
          },
          actions = {
            Text(
              text = "✨",
              fontSize = 18.sp,
              color = Color(0xFFD99B26),
              modifier = Modifier.padding(end = 16.dp),
            )
          },
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = {
            AppHaptics.click(view)
            isSearchOpen = !isSearchOpen
            if (!isSearchOpen) searchQuery = ""
          },
          shape = RoundedCornerShape(16.dp),
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.testTag("help_search_fab"),
        ) {
          Icon(
            imageVector = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
            contentDescription = "搜索帮助",
          )
        }
      },
    ) { innerPadding ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        AnimatedVisibility(
          visible = isSearchOpen,
          enter = tactileExpandVertically(),
          exit = tactileShrinkVertically(),
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索教程关键词（如 jks、QQ、摩斯、加密）...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Rounded.Close, contentDescription = "清除")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("help_search_input"),
          )
        }

        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        ) {
          items(filteredCategories, key = { it.id }) { category ->
            val expanded =
              if (normalizedQuery.isNotEmpty()) true
              else (expandedCategories[category.id] ?: false)

            Surface(
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("help_cat_${category.id}"),
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                TactileCard(
                  onClick = { expandedCategories[category.id] = !expanded },
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                  baseCorner = 22.dp,
                  pressedCorner = 26.dp,
                  pressedScale = 0.97f,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                  ) {
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier =
                        Modifier.size(44.dp)
                          .clip(RoundedCornerShape(14.dp))
                          .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                      Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                      )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                      )
                    }

                    Icon(
                      imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                      contentDescription = if (expanded) "收起" else "展开",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                AnimatedVisibility(
                  visible = expanded,
                  enter = tactileExpandVertically(),
                  exit = tactileShrinkVertically(),
                ) {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                  ) {
                    HorizontalDivider(
                      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    category.items.forEach { item ->
                      Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Column(
                          verticalArrangement = Arrangement.spacedBy(6.dp),
                          modifier = Modifier.padding(12.dp),
                        ) {
                          Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                          )
                          Text(
                            text = item.steps,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                          if (item.targetToolId != null) {
                            Row(
                              horizontalArrangement = Arrangement.End,
                              modifier = Modifier.fillMaxWidth(),
                            ) {
                              TextButton(
                                onClick = {
                                  AppHaptics.click(view)
                                  when (item.targetToolId) {
                                    "quick_look" -> quickLookLauncher.launch(arrayOf("*/*"))
                                    "__replay_onboarding__" -> onReplayOnboarding()
                                    else -> onLaunchTool(item.targetToolId)
                                  }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                              ) {
                                Text("去试试", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                  imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                  contentDescription = null,
                                  modifier = Modifier.size(15.dp),
                                )
                              }
                            }
                          }
                        }
                      }
                    }

                    if (category.id == "troubleshooting_feedback") {
                      Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        OutlinedButton(
                          onClick = {
                            AppHaptics.click(view)
                            runCatching {
                              context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdateRepository.ISSUES_URL))
                              )
                            }.onFailure {
                              Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
                            }
                          },
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier.weight(1f),
                        ) {
                          Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("GitHub Issue", maxLines = 1)
                        }
                        OutlinedButton(
                          onClick = {
                            AppHaptics.click(view)
                            val intent =
                              Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:zly080701@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "简工坊 (TactKit) 问题反馈")
                              }
                            runCatching {
                              context.startActivity(Intent.createChooser(intent, "发送反馈邮件"))
                            }.onFailure {
                              Toast.makeText(context, "未找到邮件客户端：zly080701@gmail.com", Toast.LENGTH_SHORT).show()
                            }
                          },
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier.weight(1f),
                        ) {
                          Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("发送邮件", maxLines = 1)
                        }
                      }

                      FilledTonalButton(
                        onClick = {
                          AppHaptics.click(view)
                          onReplayOnboarding()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("help_replay_onboarding_btn"),
                      ) {
                        Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重播首次引导教程", fontWeight = FontWeight.SemiBold)
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
}
