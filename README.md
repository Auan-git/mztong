# 民职通

民职通是面向**民政职业大学（简称「民职大」）**师生的校园服务 App，由学生自发开发与维护，集课表、成绩、考场、校园卡、失物招领等常用功能于一体，帮助同学们把校园生活集中到一个应用里完成。

> 本工程由开源项目 **AHUTong（OpenAHU）** 二次开发而来，详见下文「开源致谢与原项目关系」。

## 功能一览

- **课表**：官方课表风格展示（周次大标题 + 日期区间、上/下一周、快速选周），课程卡片显示教师/校区/教学楼/教室/周次，节次起止时间、今天高亮与当前时间线，桌面小组件查看当天课程（「小工具」页可一键添加到桌面）。
- **成绩与考试**：成绩单、绩点、考场查询。
- **校园卡**：余额查询与流水查看页面（充值/缴费入口已从首页与「小工具」页移除）。
- **校园信息**：教务通知、空教室、校历、失物招领、图书馆检索、浴室开放等。
- **学习资料**：按学院浏览资料，数据源为本仓库的 `学习资料/<学院名>/` 目录（国内 Gitee，`master` 分支）。
- **校园地图**：「小工具 → 校园地图」，大兴校区 / 燕郊校区切换查看，支持本机定位并在地图上标出「我的位置」。
- **个性化**：本地运行的学习记录与建议排序，可在设置中管理保留期限并随时关闭。
- **其他**：主题切换（民职蓝 `#1F4E79` / 民职金 `#C9A227`）、全局搜索、课程提醒。

## 仓库说明

- 软件源码与「学习资料」放在同一个 Gitee 仓库：[edge-of-the-abyss/ChinaCivil-Affairs-University-using](https://gitee.com/edge-of-the-abyss/ChinaCivil-Affairs-University-using)。
- 学习资料按学院放在 `学习资料/<学院名>/`，把 PDF / Word / PPT / Excel / TXT / Markdown 放进去后提交，App 即可读取。
- 新增资料后建议运行 `powershell -ExecutionPolicy Bypass -File tools/generate_materials_snapshot.ps1`，重新生成 App 内置的目录快照（Gitee 接口被限流时兜底用）。
- 构建产物（`dist/`、`build/`、APK 等）已在 `.gitignore` 中排除，不随源码提交。
- 原工程的可选子模块（`sdk/`、`GuiXu-Rust/`）未随源码提交，缺少它们不影响 `:app:assembleDebug` 构建；如需原生相关能力请按下文说明自行补齐。

## 技术栈

- **语言**：Kotlin 为主，含少量 Java。
- **架构**：MVVM + Hilt 依赖注入 + Kotlin Coroutines / Flow。
- **UI**：Jetpack Compose、Material 3，搭配 Monet / Capsule / Backdrop 等 Compose 组件库。
- **桌面小组件**：Jetpack Glance + `AppWidgetProvider` 双实现。
- **数据**：Retrofit + OkHttp + Gson 处理网络请求，MMKV / DataStore 本地存储，Room 存放个性化数据。
- **原生能力**：Rust SDK（`sdk/` 子模块，通过 JNI 调用）负责爬虫与协议相关逻辑；`GuiXu-Rust` 子模块提供嵌入式数据库实现。
- **工程配置**：compileSdk / targetSdk 36，minSdk 26，仅打包 `arm64-v8a`。

## 环境要求

| 依赖 | 版本 / 说明 |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 36、Build-Tools 37.0.0 |
| Android NDK | 28.2.13676358 |
| Rust | stable 工具链，target `aarch64-linux-android`，需安装 `cargo-ndk` |
| Gradle | 使用仓库自带的 `./gradlew`（Wrapper） |

## 构建与运行

1. 初始化子模块（`sdk`、`GuiXu-Rust`）：

   ```bash
   git submodule update --init --recursive
   ```

   > 注意：`.gitmodules` 中 `sdk` 的地址目前为占位符（`REPLACE_ME`），需替换为本项目实际使用的 Rust SDK 仓库后才能完成构建。

2. 构建 Debug APK：

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Windows 下使用 `gradlew.bat :app:assembleDebug`。产物位于 `app/build/outputs/apk/debug/`。

3. 安装到设备（需已通过 `adb devices` 确认设备在线）：

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. 运行单元测试与构建 Release 包：

   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:assembleRelease
   ```

5. 使用 Android Studio 打开工程根目录即可正常同步与运行，构建配置与命令行一致。

### 在电脑上预览（不需要手机）

电脑上可以用 Android 模拟器直接运行，不必先装到手机：

1. 确认模拟器已启动（本机已配置 ApkStart，模拟器窗口标题会显示当前应用名如「民职通」；没有窗口时先启动 ApkStart 或执行 `D:\apk\emulator\emulator.exe -avd apkstart`）。
2. 在项目根目录执行：

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\preview_on_pc.ps1
   ```

   脚本会自动找到正在运行的模拟器，安装 `dist/` 里最新的 APK 并启动。
3. 在模拟器窗口里用**学号 + 教务系统密码**登录，即可查看首页、课表等界面。

也可以直接把 APK 拖进模拟器窗口安装。注意：模拟器里装的是 debug 版（包名 `com.ahu.ahutong.debug`），与手机日常使用互不影响。

## 与原项目 AHUTong（OpenAHU）的关系与开源致谢

本项目是 [AHUTong](https://github.com/OpenAHU)（OpenAHU 团队维护的校园服务客户端）的二次开发版本：

- 代码基线、功能结构、Rust SDK 与桌面小组件等设计来自原项目，本分支在此之上完成界面品牌、文案与主题的调整。
- 原项目的版权归其作者所有，本项目**完整保留原项目的版权声明与许可证**，未移除任何原作者署名。
- 感谢 OpenAHU 团队及原项目所有贡献者将代码开源，使本项目得以在真实可用的基础上继续演进。
- 同时感谢以下上游开源项目：Jetpack Compose、Hilt、Retrofit / OkHttp、Coil、Monet（Kyant0）、Capsule、Glance、GuiXu（Rust 重写版）等。第三方组件授权说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [NOTICE](NOTICE)。

本项目同样以 **GNU General Public License v3.0** 发布，详见 [LICENSE](LICENSE)。任何基于本项目的二次开发或分发，都应遵守 GPLv3 并保留原项目与本项目的版权与许可声明。

## 已知限制

- **教务系统已接入超星**：登录与课表抓取指向民政职业大学真实教务系统 `https://bcsa.jw.chaoxing.com`（协议见 [docs/民政职业大学适配说明.md](docs/民政职业大学适配说明.md) 第 4.1 节）。
- **其余业务接口为占位配置**：成绩、考试、空教室、评教、一卡通、缴费、失物招领等仍指向旧占位地址，暂不可用。
- **Rust 原生库沿用原项目**：`libahutong_rs.so` 等构建产物与 `sdk` 子模块来自原项目，其内部协议与接口尚未适配新学校环境（课表链路已不依赖它）。
- **仅支持 arm64-v8a**：当前只打包 64 位 ARM 架构，模拟器与其他 ABI 设备无法直接安装。
- 自动化更新服务、生产发版流程尚未接入，更新相关提示仅作占位。
- 学校业务数据（电话本号码、浴室缴费项目 ID、校区 ID、楼宇名称）为占位值，逐项核对清单见 [docs/民政职业大学适配说明.md](docs/民政职业大学适配说明.md)。

## 免责声明

1. 本项目为**非官方**项目，由学生自发开发与维护，不代表民政职业大学官方立场，也不提供任何官方承诺或担保。
2. 使用本应用时，请通过学校官方渠道确认账号、密码等敏感信息的用途；请勿在非可信网络环境下登录。
3. 应用会访问学校业务系统以完成您主动发起的查询与操作，请遵守学校相关管理规定，合理使用接口。
4. 因使用本应用（包括非官方渠道分发的二次开发版本）产生的任何直接或间接损失，项目开发者不承担责任。
5. 本项目完全开源，欢迎通过项目仓库的 Issue 反馈问题、提出建议或参与贡献。
