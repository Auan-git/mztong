# 超星评教接入

入口：民职通 → 小工具 → 教评。

当前支持当前登录账号的任务列表、未评/已保存/已提交状态，以及问卷题目、选项、已有答案和意见字数限制。填写和提交仍在教务系统完成。本模块没有调用保存或提交接口。

## 实现

- `ChaoxingClient.fetchEvaluations()`：读取教师排名检查、列表页隐藏查询字段，分页查询任务并核对总数。任务按 `zbmbxxid/jxbid/tid` 组合标识区分；不同教师可能复用列表行 `id`。
- `ChaoxingClient.fetchEvaluationQuestionnaire()`：未提交任务读取 `edit` 页面，已提交任务读取 `showDetail` 页面。问卷隐藏的任务标识必须匹配所选课程和教师。
- 评教请求使用桌面浏览器 User-Agent。超星对手机 User-Agent 返回不同的移动页面，缺少本解析器依赖的列表查询字段。其余模块保留原有请求设置。
- 请求使用现有 Cookie 和账号登录流程；会话失效后重登重试一次。页面/分页结构异常会报告错误，不按零任务处理。
- `ChaoxingEvaluationParsing` 解析 HTML 与 JSON，无浏览器、截图或 OCR 依赖。兼容 `0.0`、`10000.0` 这样的整数字数限制；异常值不静默忽略。
- `ChaoxingEvaluationViewModel` 管理刷新、问卷加载与返回取消。刷新失败保留上次列表并提示；关闭问卷后迟到的请求结果不会重新打开详情。
- `Evaluation` 使用 Compose 原生页面展示任务和问卷，支持顶部返回和系统返回。

## 验证命令

先执行 `adb devices -l`，再运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.chaoxing.*' --tests 'com.ahu.ahutong.ui.state.ChaoxingEvaluationViewModelTest' :app:assembleDebug --console=plain --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1280m -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=128m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
```

构建内存参数只用于本次命令。产物为 Android 默认调试签名的 Debug APK，包名 `com.ahu.ahutong.debug`，不作为生产发布包。

## 验证结果（2026-11-26）

- 单元测试 25 项通过、0 失败、0 跳过：评教解析 7 项、评教 ViewModel 3 项、成绩解析 5 项、课表等解析 10 项。
- 覆盖真实格式的整数字数限制、异常范围、问卷与任务不匹配、重复题号、会话过期、同一列表行 id 的不同教师、刷新失败保留旧列表、取消后迟到的结果以及连续请求的加载状态。
