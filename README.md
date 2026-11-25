<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="144" height="144" alt="简工坊应用图标">
</p>

<h1 align="center">简工坊 · TactKit</h1>

<p align="center">
  本地优先的开源 Android 文件与文档工具箱。<br>
  扫描和批注 PDF、整理手机文件、编辑图片，也能在同一网络里扫码传文件。全程无广告。
</p>

<p align="center">
  <a href="https://github.com/xialia-cheng/tact-kit/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-4d669a" alt="MIT 开源许可证"></a>
  <a href="https://github.com/xialia-cheng/tact-kit/releases/latest"><img src="https://img.shields.io/github/v/release/xialia-cheng/tact-kit?sort=date&amp;label=Release&amp;color=4d669a" alt="最新发布版本"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-38765e?logo=android&amp;logoColor=white" alt="支持 Android 8.0 及以上版本">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7652a4?logo=kotlin&amp;logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4d669a?logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose">
  <a href="https://github.com/xialia-cheng/tact-kit/releases"><img src="https://img.shields.io/github/downloads/xialia-cheng/tact-kit/total?label=%E4%B8%8B%E8%BD%BD%E9%87%8F&amp;color=4d669a" alt="累计下载量"></a>
</p>

<p align="center">
  <a href="https://github.com/xialia-cheng/tact-kit/releases/latest"><strong>下载 APK</strong></a> ·
  <a href="#功能">功能</a> ·
  <a href="#隐私与联网">隐私与联网</a> ·
  <a href="#从源码构建">源码构建</a>
</p>

---

## 下载

打开 **[GitHub 最新发布](https://github.com/xialia-cheng/tact-kit/releases/latest)**，在 **Assets** 中下载 `TactKit-v<版本号>.apk`。应用支持 **Android 8.0（API 26）及以上版本**。

[查看历史版本与更新记录](https://github.com/xialia-cheng/tact-kit/releases)

## 功能

- **文档扫描与编辑**　拍照后调整四角，矫正透视并生成多页 PDF。可合并、拆分、压缩 PDF，设置密码或凭原密码解除保护，也能高亮、手写批注、签名并导出。支持离线阅读、编辑和导出 Word（`.docx`）文档。
- **文件管理与速览**　可按类型或修改时间查看文件，按名称或后缀搜索，批量移动或重命名。Quick Look 支持预览、查看 SHA-256，以及查看 APK 和部分证书的详细信息。支持处理 ZIP、7z、TAR、TAR.GZ 和 TAR.XZ 压缩包。
- **图片与音视频**　支持裁切、旋转、马赛克、文字水印、取色及去除 EXIF，也可把多张图片拼成长图或导出为 PDF。视频可单帧或批量抽帧、提取音轨，并按关键帧裁剪。
- **局域网快传与诊断**　同一网络下，用浏览器扫码即可上传和下载文件。提供网络连通性、DNS、TLS 证书及 Wi-Fi 信道检查。
- **个人工作台**　用卡片整理 Markdown 便签、待办清单、语音备忘录和导入文件，支持搜索和置顶。Android 桌面可添加快捷工具和置顶清单小组件。
- **开发与电子**　支持 CSV、JSON、YAML 和 XML 转换与校验、CSV 表格编辑、Base64、正则测试、进制转换和 IPv4 子网计算。还可核对文件哈希、用口令加密文件或文字（AES-256），以及计算色环电阻和电压分压。
- **测量与日常**　提供指南针、水平仪、分贝计、照度计、屏幕直尺、电池与硬件信息、二维码生成和扫描，以及计数器、单位换算、账单分摊和番茄钟。传感器工具的可用性取决于设备。
- **可选 AI 中枢**　配置兼容 OpenAI 的模型接口与 API Key 后，可把图片转成 Markdown、对含文本的 PDF 摘要或问答，或把文字整理成待办清单。图片识别需要支持视觉输入的模型。

界面基于 Jetpack Compose 和 Material 3，支持浅色、深色和跟随系统主题。Android 12 及以上可启用动态取色。

## 使用

- **打开常用工具**。点主页右下角的工具球打开分类列表，点图钉即可把工具固定到常用栏。
- **从其他应用速览文件**。在文件管理器或聊天应用里打开分享菜单，选择「简工坊 · Quick Look」，即可在弹窗中速览受支持的文件。
- **用浏览器传文件**。在「局域网 HTTP 快传」启动服务，让另一台处于同一局域网的设备扫描二维码，或在浏览器输入显示的地址，即可上传和下载文件。

## 隐私与联网

便签、清单、录音和导入的工作台文件保存在设备上。文档、图片、压缩包和格式转换等常用工具可离线使用。

| 功能 | 数据去向 |
| --- | --- |
| AI 中枢 | 主动提交时，将所选图片、提取的 PDF 文本或输入文字发送到你配置的模型服务。API Key 使用 Android Keystore 加密保存。 |
| 检查更新 | 查询 GitHub Releases，并并行访问更新镜像。自动检查可在设置中关闭。 |
| 局域网快传与网络诊断 | 快传使用同一局域网内的 HTTP 服务。网络诊断会访问所选目标或调用系统的网络接口。 |

## 从源码构建

准备 **JDK 17**、**Gradle 9.3.1** 和 **Android SDK Platform 36**。在 Android Studio 中导入仓库即可配置 SDK，也可以在终端设置 `ANDROID_HOME`。

```bash
git clone https://github.com/xialia-cheng/tact-kit.git
cd tact-kit
```

首次构建时，如根目录还没有 `debug.keystore`，用 JDK 自带的 `keytool` 创建调试签名。

```bash
keytool -genkeypair -keystore debug.keystore -storetype JKS -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
gradle :app:assembleDebug
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。正式签名构建由 [GitHub Actions](https://github.com/xialia-cheng/tact-kit/actions) 执行。

## 许可证与反馈

简工坊以 **[MIT 开源协议](LICENSE)** 发布。遇到问题或有功能建议，欢迎在 [GitHub Issues](https://github.com/xialia-cheng/tact-kit/issues) 反馈。如果你觉得简工坊实用，欢迎给仓库点一颗 **Star**。
