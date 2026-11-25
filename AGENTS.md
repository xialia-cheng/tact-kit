# TactKit 项目规范 (AGENTS.md)

## 1. 技术栈与红线
- **架构与配置**：单模块纯 Kotlin + Jetpack Compose + Material 3 Expressive，`applicationId = "com.xialia.tactkit"`，`compileSdk = 36`，`minSdk = 26`。
- **核心依赖版本锁定**：保持 `io.github.kyant0:backdrop:1.0.6` 与 `io.github.kyant0:capsule:2.1.2`（不要升级至 `2.0.1` 等破坏性版本）。
- **版本与构建保护**：保留 `app/build.gradle.kts` 中的 CI 动态版本号逻辑。
- **严禁修改/引入**：
  - 严禁修改 `.github/workflows/main.yml` 与根目录 `LICENSE`。
  - 严禁引入 Hilt、Firebase 或 NDK。

## 2. 视觉与交互
- **字体**：全局使用 `FontFamily.Default` 系统字体。
- **触控与反馈**：所有可点击元素统一使用 `TactilePressModifier`（Q 弹缩放 + 震动 + `SoundEffectConstants.CLICK` 系统点击音）。

## 3. 本地验证与自动推送习惯
- **轻量验证**：修改代码后仅运行轻量 `gradle compileDebugKotlin`（或静态检查）确认无编译错误即可，**不要在本地运行耗时的 `testDebugUnitTest`、`lintDebug` 或 `assembleRelease`**（全量测试、Lint 与签名打包统一由 GitHub Actions 自动完成）。
- **自动提交推送**：确认无编译错误后，直接自动执行 `git add`、`git commit` 并 `git push origin main`。
