<div align="center">

<img src="app/src/main/res/drawable/img_tactkit_icon.png" alt="简工坊图标" width="88">

# 简工坊 · TactKit

本地优先的 Android 工具箱与文档工作台。处理文档、整理文件、编辑图片，也能随手记下待办。开源、无广告。

[![版本](https://img.shields.io/github/v/release/xialia-cheng/tact-kit?style=flat-square&label=Release)](https://github.com/xialia-cheng/tact-kit/releases/latest)
[![累计下载量](https://img.shields.io/github/downloads/xialia-cheng/tact-kit/total?style=flat-square&logo=github&label=Downloads)](https://github.com/xialia-cheng/tact-kit/releases)
[![MIT 许可证](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Android 8.0 及以上](https://img.shields.io/badge/Android-8.0%2B-green?style=flat-square&logo=android&logoColor=white)](https://github.com/xialia-cheng/tact-kit/releases/latest)

[下载 APK](https://github.com/xialia-cheng/tact-kit/releases/latest) · [功能](#功能) · [更新记录](https://github.com/xialia-cheng/tact-kit/releases) · [从源码构建](#从源码构建)

</div>

## 功能

- **个人工作台**。用卡片管理 Markdown 便签、待办清单、语音备忘录与导入的文件。支持搜索和置顶，Android 桌面也可添加快捷工具和置顶清单小组件。
- **文档与办公**。拍照扫描，调整四角并矫正透视，合成多页 PDF。支持 PDF 合并、按页拆分、压缩、加密、凭原密码解密，以及高亮、手写批注和签名导出。可离线阅读、编辑和导出 `.docx` 文档。
- **媒体与视觉**。图片支持裁切、旋转、去除 EXIF、马赛克、文字水印、取色、长图拼接与转 PDF。音视频工具支持单帧或批量抽帧、提取音轨和按关键帧裁剪。
- **文件与网络**。按类型或修改时间浏览文件，按名称或后缀搜索，批量移动或重命名。Quick Look 可预览文件、查看 SHA-256，也可查看 APK 与部分证书格式的信息。压缩包工坊支持 ZIP、7z、TAR、TAR.GZ 与 TAR.XZ。局域网工具支持扫码双向传文件，并提供连通性、DNS、TLS 证书及 Wi-Fi 信道检查。
- **开发与电子**。支持 CSV、JSON、YAML 和 XML 转换与校验、CSV 表格编辑、Base64、正则测试、进制转换和 IPv4 子网计算。可校验文件哈希、为文件或文本设置 AES-256 加密口令，还能计算色环电阻和电压分压。
- **测量与日常**。内置指南针与水平仪、分贝计、照度计、屏幕直尺、电池与硬件信息、二维码生成和扫描，以及计数器、单位换算、账单分摊和番茄钟。测量功能取决于设备传感器，读数供参考。
- **可选 AI 中枢**。自备 API Key，接入兼容 OpenAI 的模型接口，将图片转为 Markdown，对可提取文本的 PDF 做摘要或问答，或把一段文字整理成待办清单。图片识别需要模型支持视觉输入。

界面使用 Kotlin、Jetpack Compose 和 Material 3，支持浅色、深色及跟随系统主题，Android 12 及以上可启用动态取色。

## 下载

前往 [GitHub Releases](https://github.com/xialia-cheng/tact-kit/releases/latest)，在 **Assets** 中选择 `TactKit-v<版本号>.apk` 安装。

| 平台 | 安装包 | 系统要求 |
| --- | --- | --- |
| Android | GitHub Releases 签名 APK | Android 8.0 及以上（API 26+） |

历次发布和更新说明见 [Releases](https://github.com/xialia-cheng/tact-kit/releases)。

## 使用

- **打开常用工具**。点主页右下角的工具球打开分类列表，点图钉即可把工具固定到常用栏。
- **从其他应用速览文件**。在文件管理器或聊天应用里打开分享菜单，选择「简工坊 · Quick Look」，即可在弹窗中速览受支持的文件。
- **用浏览器传文件**。在「局域网 HTTP 快传」启动服务，让另一台处于同一局域网的设备扫描二维码，或在浏览器输入显示的地址，即可上传和下载文件。

### 本地处理与联网

便签、清单、录音和导入的工作台文件保存在设备上。文档、图片、压缩包和格式转换等常用工具可离线运行。

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

## 支持

如果你觉得简工坊好用，欢迎给仓库点一个 **Star**。发现问题或有功能建议，可以提交 [GitHub Issue](https://github.com/xialia-cheng/tact-kit/issues)。

## 许可证

简工坊以 [MIT 开源协议](LICENSE)发布。
