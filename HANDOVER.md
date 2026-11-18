# 交接文档 · 课程表 App Android 原生版(v0.4.0 M1)

> 本工程由 WebView 版课表 App 迁移而来,Android 端改为原生 Compose + AGSL 液态玻璃。
> 玻璃渲染核心已在小米真机(HyperOS,Android 15)验证通过,**接手后请勿重写玻璃核心,只做参数级调整**。
> 完整路线图见根目录 `PRD.md`,本文档补充工程地图、踩坑铁律和当前待优化项。

## 1. 环境与构建

- JDK 17,Android SDK(Platform 35),minSdk **33**(AGSL 门槛,Android 13+)
- 构建:`./gradlew assembleRelease` → 产物在 `app/build/outputs/apk/release/app-release.apk`(release 用 debug 签名,可直接安装)
- 验证必须用真机(AGSL 着色器行为与模拟器不一致,所有历史 bug 都只在真机暴露)
- 帧率验收:开发者选项 → GPU 渲染条,滚动/连点无红色超线

## 2. 文件地图

| 文件 | 职责 | 能否改 |
|---|---|---|
| `GlassShaders.kt` | AGSL 着色器:背景(渐变+光斑)与玻璃(折射+RGB色差+高光+颗粒)。**背景函数 backdrop() 被两个着色器共用,保证玻璃内外逐像素连续** | 禁止重写;允许调参数(位移量、光斑位置色值、高光强度) |
| `GlassUi.kt` | `GlassCard` 玻璃卡片组件(着色器图层 + 内容图层 + 按压 spring + 陀螺仪 tilt) | 结构勿动;可调手感参数 |
| `MainActivity.kt` | M1 页面:今日页(玻璃大卡+倒计时+课程列表)、课表骨架、设置页、AI/备忘占位、玻璃 Dock | 主要迭代区 |
| `SampleData.kt` | 内置示例课程 JSON | M2 换成真实导入 |
| `NextClassWidget.kt` | Glance 小组件(下一节课) | M3 完善 |
| `AndroidManifest.xml` / `build.gradle.kts` | minSdk 33、Compose BOM、Glance 依赖 | 慎改 |

## 3. 踩坑铁律(每条都真机踩过,违反必回归)

1. **坐标规则**:着色器里玻璃形状用**节点本地坐标**算,虚拟背景用 `本地坐标 + uCardPos` 换算的**屏幕坐标**算——两者错位会导致整卡消失或背景错位
2. **AGSL 编译错误只在真机报**:桌面编译期查不出来。所以页面底部的诊断行(`背景 OK · 玻璃 OK · 传感器 OK`)是排障生命线,**任何改动后都必须保留它**;玻璃创建包 try-catch,失败降级为半透明卡
3. **变量改名要全局一次改净**:曾因 `sp→spScreen` 漏改一行,真机报 `error: 87: unknown identifier` 整卡消失
4. **z 层顺序**:背景最底 → 玻璃着色器层 → 内容文字层;玻璃层永远不许盖住文字
5. 不要用 backing bitmap / RenderScript 方案(历史验证帧率与兼容性都差)
6. 文案、注释、诊断信息全中文;色彩 token:#0B0D12 背景、#7C5CFF 强调色、白四级透明度(1/.72/.5/.35),图标一律单色线性(Material Symbols Outlined),禁止 emoji 图标

## 4. M1 已知待优化(用户反馈"还行但需优化",优先处理)

- 课表页只是按周分组的骨架,需做成完整周视图网格(参考 WebView 版布局)
- Dock 玻璃与列表内容的滚动穿透观感可再调
- 今日页信息密度、间距、字号层级需向 WebView 版成熟布局对齐
- 倒计时"明天/后天"等跨天文案、无课状态分支需补全
- AI/备忘占位页:M4 计划在原生壳内嵌 WebView 承载旧前端,先做路由占位
- 设置页只有三项占位,M2 接真实 JSON 导入导出

## 5. 数据 Schema(M2 实现,与 WebView 版兼容)

```kotlin
@Serializable data class CourseData(
  val courses: List<Course>,
  val semesterStart: String,   // "2026-09-07"
  val settings: Map<String, String> = emptyMap()
)
@Serializable data class Course(
  val name: String, val teacher: String = "", val room: String = "",
  val dayOfWeek: Int, val startNode: Int, val endNode: Int,
  val weeks: List<Int> = emptyList(), val color: String = "#7C5CFF"
)
```
导入:SAF 文件选择器读 JSON → 宽容解析(字段缺省不抛错)→ 存 DataStore;导出:反向生成。WebView 版导出的旧 JSON 必须能直接读入。

## 6. 迭代纪律

- 小步提交,一轮只改一个关注点,每轮真机截图验收
- 改着色器/玻璃参数时:一次只动一个数值,装包看效果再动下一个
- 玻璃手感参考:位移量 `s` 内部 72~100、折射带 `band` = 短边 18%、按压位移降到 72%

## 7. 分发

当前用"直链下载页"分发 APK(构建后把 `app-release.apk` base64 更新到部署服务)。接手方如有自己的构建环境,可改为任意分发方式,工程本身无服务端依赖。
