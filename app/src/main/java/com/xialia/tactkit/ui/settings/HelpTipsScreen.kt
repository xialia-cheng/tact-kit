package com.xialia.tactkit.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
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
      subtitle = "桌面卡片、悬浮球无限置顶、液态玻璃省电模式",
      icon = Icons.Rounded.Home,
      items =
        listOf(
          HelpTipItem(
            title = "桌面卡片与快速开始",
            steps = "在主页顶部点击「新建清单」「新建便签」「录制语音备忘」或「导入本地文件」，所有内容均本地存储于 Room 数据库，支持长按/点击置顶与全文检索。",
            keywords = "桌面 卡片 清单 便签 语音 markdown",
            targetToolId = "quick_note",
          ),
          HelpTipItem(
            title = "液态玻璃悬浮球与无限置顶",
            steps = "点击右下角液态玻璃悬浮球展开 5 大分类工具抽屉；点击任意工具右侧的图钉图标即可无限置顶到顶部常用栏，无数量上限。",
            keywords = "悬浮球 置顶 液态玻璃 工具抽屉",
          ),
          HelpTipItem(
            title = "液态玻璃省电模式",
            steps = "在「我的与设置 → 外观与触控」中可开启「液态玻璃省电模式」，关闭实时模糊着色器以降低 GPU 功耗。",
            keywords = "省电 液态玻璃 模糊 gpu",
          ),
        ),
    ),
    HelpCategory(
      id = "files_cross_app",
      title = "文件管理与跨 App 选文件",
      subtitle = "最近全后缀流、精简目录、QQ/微信目录关联、跨 App 选文件",
      icon = Icons.Rounded.FolderOpen,
      items =
        listOf(
          HelpTipItem(
            title = "全盘最近文件流与 3 列大图网格",
            steps = "进入「文件管理」默认按修改时间倒序展示全盘文件；点击「图片」分类胶囊会自动切换为 3 列正方形大图直览。",
            keywords = "文件管理 最近 图片 网格 缩略图",
            targetToolId = "file_manager",
          ),
          HelpTipItem(
            title = "精简目录与 QQ/微信目录关联",
            steps = "在「目录树」中开启「精简目录」，空文件夹与 Alarms/Podcasts 等系统冷门目录会自动折叠沉底；点击顶部「关联 QQ/微信目录」可通过 SAF 持久授权访问聊天接收目录。",
            keywords = "精简目录 qq 微信 saf 目录树",
            targetToolId = "file_manager",
          ),
          HelpTipItem(
            title = "在其他 App 选文件时直接调用简工坊",
            steps = "在微信、浏览器或其他应用点击「选择文件」时，打开系统文件选择器左上角菜单，选择「简工坊 · 全盘文件」即可直接挑选 .jks、.pem、.apk 或 QQ/微信文件。",
            keywords = "跨app 选文件 documentsprovider 微信 浏览器",
            targetToolId = "file_manager",
          ),
        ),
    ),
    HelpCategory(
      id = "pdf_scanner_quicklook",
      title = "PDF、文档扫描与 Quick Look",
      subtitle = "加密 PDF 原地弹窗解锁、Canvas 批注、文档扫描、.docx 与速览",
      icon = Icons.Rounded.PictureAsPdf,
      items =
        listOf(
          HelpTipItem(
            title = "加密 PDF 原地弹窗解锁",
            steps = "打开受密码保护的 PDF 时，会自动弹出密码输入框，在本地内存直接解锁渲染页面，支持拆分、合并与重新加密导出。",
            keywords = "pdf 加密 解锁 密码 拆分 合并",
            targetToolId = "pdf_toolkit",
          ),
          HelpTipItem(
            title = "PDF Canvas 手写批注",
            steps = "选择任意 PDF 进入批注画布，支持多种画笔颜色、粗细调节与撤回，一键导出带批注的新 PDF。",
            keywords = "pdf 批注 画笔 签名 标注",
            targetToolId = "pdf_annotate",
          ),
          HelpTipItem(
            title = "CameraX 文档扫描与一键流转",
            steps = "连续拍摄纸质文档或从相册选图，支持四角透视拉直与黑白/增强滤镜；导出 PDF 后可直接一键跳转「PDF 批注」「加密/拆分」或「局域网快传」。",
            keywords = "文档扫描 透视 拍照 pdf 批注 快传",
            targetToolId = "doc_scanner",
          ),
          HelpTipItem(
            title = "Word (.docx) 编辑与分享即预览 Quick Look",
            steps = "支持本地读取与编辑 .docx 文档；在其他 App 点击「分享 → 简工坊 · Quick Look」即可弹窗速览文本、代码、图片或一键导入工作台。",
            keywords = "docx word quick look 速览 分享",
            targetToolId = "docx_editor",
          ),
        ),
    ),
    HelpCategory(
      id = "media_ai_lanshare",
      title = "媒体、AI 中枢与局域网快传",
      subtitle = "马赛克与剥离 EXIF、视频逐帧扒图、双向快传、BYOK AI",
      icon = Icons.Rounded.AutoAwesome,
      items =
        listOf(
          HelpTipItem(
            title = "图片隐私马赛克与 EXIF 剥离",
            steps = "固定画布不漂移，支持矩形/画笔打码、像素化程度调节与一键抹除拍摄设备/GPS 等 EXIF 隐私信息，也支持多图拼长图转 PDF。",
            keywords = "马赛克 exif 隐私 拼图 长图",
            targetToolId = "image_privacy",
          ),
          HelpTipItem(
            title = "视频逐帧扒图与无损裁剪",
            steps = "拖动时间轴或点击上一帧/下一帧精准定位画面，保存原画质 PNG/JPG，或一键送入「马赛克」「取色」或「AI 识图」。",
            keywords = "视频 逐帧 扒图 抓拍 无损 裁剪 音频",
            targetToolId = "media_extractor",
          ),
          HelpTipItem(
            title = "局域网双向快传",
            steps = "同一 Wi-Fi / 热点下开启本地 HTTP 服务，手机或电脑浏览器扫码即可高速互传文件，零流量且不经过任何公网服务器。",
            keywords = "局域网 快传 传输 wifi 扫码",
            targetToolId = "lan_share",
          ),
          HelpTipItem(
            title = "BYOK AI 中枢与硬件 Keystore",
            steps = "在设置中一键选择 DeepSeek / OpenAI / 硅基流动 / Ollama 本地预设并填入 API Key，密钥经 AndroidKeyStore AES-GCM 硬件加密存储。",
            keywords = "ai deepseek openai ollama keystore 识图 问答",
            targetToolId = "ai_hub",
          ),
        ),
    ),
    HelpCategory(
      id = "archive_data_hash",
      title = "压缩、格式转换与校验和",
      subtitle = "ZIP AES-256 / 7Z 解压打包、CSV/JSON/YAML/XML 互转、4 Tab 哈希",
      icon = Icons.Rounded.Archive,
      items =
        listOf(
          HelpTipItem(
            title = "全能压缩包工坊 (ZIP / 7Z / TAR)",
            steps = "支持带密码 ZIP (AES-256) 与 7Z/TAR.GZ/TXZ 解压浏览，也支持多选文件或文件夹一键加密打包为 ZIP。",
            keywords = "zip 7z 压缩 解压 aes 密码 tar",
            targetToolId = "archive_box",
          ),
          HelpTipItem(
            title = "数据工坊 (CSV / JSON / YAML / XML)",
            steps = "本地解析与格式化 CSV、JSON、YAML、XML，支持一键互转、表格预览与语法高亮复制。",
            keywords = "csv json yaml xml 转换 格式化",
            targetToolId = "data_workshop",
          ),
          HelpTipItem(
            title = "4 Tab 校验和与哈希工坊",
            steps = "支持文件与文本的 MD5、SHA-1、SHA-256、SHA-512 计算与双值比对，快速验证安装包或证书完整性。",
            keywords = "哈希 hash md5 sha256 sha512 校验和",
            targetToolId = "hash_workshop",
          ),
        ),
    ),
    HelpCategory(
      id = "specialty_sensors_electronics",
      title = "冷门辅助、传感器与电子工具",
      subtitle = ".ics / .pem / .jks / .apk 验看、分贝仪、尺子、摩斯、Wi-Fi 与电阻",
      icon = Icons.Rounded.Sensors,
      items =
        listOf(
          HelpTipItem(
            title = ".ics 日程 / .pem & .jks 证书 / .apk 验看",
            steps = "在文件管理中点击 .ics 日历、.pem 公私钥证书、.jks 签名库或 .apk 安装包，即可只读验看签名指纹、有效期、包名权限与日程详情。",
            keywords = "ics pem jks apk 证书 签名 验看",
            targetToolId = "file_manager",
          ),
          HelpTipItem(
            title = "AES 文件保险箱",
            steps = "使用口令对任意本地文件进行 AES-GCM 加密生成 .tactenc 保险文件，防止敏感资料明文泄露。",
            keywords = "aes 加密 文件锁 tactenc 保险箱",
            targetToolId = "crypto_vault",
          ),
          HelpTipItem(
            title = "分贝仪、物理尺子与摩斯手电筒",
            steps = "调用麦克风实时监测环境 dB 分贝；利用屏幕物理 DPI 校准厘米/英寸直尺；输入文本一键以闪光灯发送摩斯电码或 SOS。",
            keywords = "分贝 db 尺子 摩斯 手电筒 sos 传感器",
            targetToolId = "decibel_meter",
          ),
          HelpTipItem(
            title = "Wi-Fi 信道、DNS/SSL、充电功率、LED 弹幕与色环电阻",
            steps = "查看 2.4G/5G Wi-Fi 信道拥挤度、DNS 解析与 SSL 证书链；实时监测电池充电瓦数 W；全屏手持 LED 滚动弹幕；4/5 色环电阻阻值速算。",
            keywords = "wifi dns ssl 充电 功率 led 弹幕 电阻 色环",
            targetToolId = "electronics_calc",
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
            title = "所有文件访问与 SAF 双模式",
            steps = "简工坊支持通过系统 Photo Picker 与 SAF 选择单文件，也支持在设置中授权「所有文件访问权限」以便高效管理本地文档与导出文件；所有文件均仅在本地处理。",
            keywords = "所有文件 saf photo picker 权限 本地",
          ),
          HelpTipItem(
            title = "常见权限排查与按需动态申请",
            steps = "1. 相机、麦克风与 Wi-Fi 权限均按需动态申请：仅在使用文档扫描/扫码 (CAMERA)、语音备忘/分贝仪 (RECORD_AUDIO) 或 Wi-Fi 信道分析 (ACCESS_FINE_LOCATION + 位置开关) 时请求，拒绝后其余功能不受影响。\n2. 文件管理看不到文件：请在系统设置开启「所有文件访问权限」，或点击「关联 QQ/微信目录」通过 SAF 授权。",
            keywords = "权限 排查 相机 麦克风 定位 所有文件",
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
                                  onLaunchTool(item.targetToolId)
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
