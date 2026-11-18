# Ketable Liquid Glass V2 — Workbuddy 构建交接

本包以用户上传的 `ketable-native-v5.4.1test-src` 原始 Kotlin/Compose 项目为基础，保留原有业务页面、数据模型、路由、AI、设置、课程编辑、导入导出、Widget 与玻璃运行时诊断。

## 本轮改动

仅修改以下 4 个文件：

1. `app/src/main/java/com/workbuddy/ketable/TodayV3.kt`
   - 沿用 V1 已完成的中性 Hero 内容卡，去掉大面积紫蓝极光和斜向扫光。Hero 是内容卡，不伪装成背景采样玻璃。
2. `app/src/main/java/com/workbuddy/ketable/Glass.kt`
   - 保留 Kyant AndroidLiquidGlass、BackdropHost、AGSL Lens、异常捕获与自动降级。
   - Regular 模糊从 16dp 进一步收敛到 11dp，并降低饱和度，提升 Dock 后方课程文字的可辨认度。
   - Regular 增加更明确但克制的上缘反射和下缘暗边。
   - 完整 Lens 增加轻微表面反射；不改变 AGSL 着色器接口和异常保护逻辑。
3. `app/src/main/java/com/workbuddy/ketable/Theme.kt`
   - 深色 Dock 蒙层改为低不透明度中性白，不再覆盖厚重黑灰色。
4. `app/src/main/java/com/workbuddy/ketable/MainActivity.kt`
   - FAB 稍微增大并提高背景放大感，符号改为中性白色。
   - Dock 选中泡高度微调，不改变导航宽度、路由或点击逻辑。

## 对 Workbuddy 的明确任务

**只做最后构建与验证，不要重新设计，不要覆盖本包的视觉修改。**

1. 解压本 ZIP，并以其中 `ketable-native` 作为项目根目录打开。
2. 检查 Gradle 同步；项目原有依赖不可随意升级或替换，尤其保留 `com.github.Kyant0:AndroidLiquidGlass:1.0.0-rc01`。
3. 执行 `./gradlew testDebugUnitTest`（如果项目环境支持），然后执行 `./gradlew assembleRelease`。
4. 若构建失败，只修复明确的编译/依赖/打包错误；不得重写 `Glass.kt`、`GlassShaders.kt`、`GlassUi.kt` 的核心架构，不得删除 CrashGuard、GlassRuntime、诊断日志或玻璃降级逻辑。
5. 产物应为 `app/build/outputs/apk/release/app-release.apk`。项目 release 使用 debug 签名，供个人测试安装。
6. 提供实际 APK 文件、构建日志摘要、测试结果和 APK SHA-256。不能只回复“构建成功”；必须确认 APK 文件真实存在且大小大于 0。
7. 真机 Android 16 验收：打开“液态玻璃效果”开关，观察今日页滚动时 Dock 能透出并柔和模糊背后的课程内容；切换五个 Tab；连续点 FAB；进出设置页；确认不闪退。若玻璃渲染失败，应保留自动降级并报告 `GlassRuntime.lastError` / CrashGuard 诊断。

## 限制与验收边界

该版本是源码改造候选版，当前交付环境未完成 Android SDK/Gradle 真机编译，因此不能宣称 APK 已通过构建或 Android 16 实机验收。AGSL 的最终折射效果、GPU 帧率和不同厂商驱动表现，必须由 Workbuddy 构建后在目标手机实测。

## 安全

不要把 debug keystore、签名密码、API token 或本地 `local.properties` 上传到公共位置。保留原始 ZIP 与原 APK 作为回滚点。
