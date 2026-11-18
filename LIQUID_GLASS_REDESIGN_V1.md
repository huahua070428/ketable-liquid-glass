# Liquid Glass 视觉改造 v1

本轮是低风险的第一轮视觉基线调整，保留原有课程、导航、数据、AI、设置与玻璃渲染架构。

## 修改文件

- `app/src/main/java/com/workbuddy/ketable/TodayV3.kt`
  - 移除 Hero 卡大面积紫蓝极光渐变和斜向扫光。
  - 改为中性深色内容卡，只保留微弱顶部反射与细边缘光。
  - Hero 仍然是内容卡，不冒充背景采样玻璃；真正玻璃保留给系统级悬浮控件。
- `app/src/main/java/com/workbuddy/ketable/Glass.kt`
  - Regular 玻璃模糊从 24dp 调整为 16dp，降低过度糊化。
  - Regular 玻璃增加柔和的上缘高光与轻微下缘暗部。
  - 提高玻璃边缘描边可见度。
  - 保留现有 Backdrop / AGSL Lens 实现、异常降级和诊断逻辑。
- `app/src/main/java/com/workbuddy/ketable/Theme.kt`
  - 深色 Dock 蒙层从偏黑改为低不透明度白色中性玻璃色，改善背景透射。

## 验收说明

本环境无法联网下载 Gradle 8.13，因此没有完成 APK 编译；请在 Workbuddy / Android 构建环境同步 Gradle 后编译，并在 Android 16 真机上验收。首次安装前请保留原 APK 和原始项目 ZIP。

建议验收顺序：今日页 Hero 色彩、底部 Dock 背景透射、选中玻璃泡、FAB；确认无闪退后再继续精修参数。
