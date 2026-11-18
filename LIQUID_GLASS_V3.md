# Liquid Glass 视觉改造 v3 — 尽量贴近 iOS 26

目标：不是“像毛玻璃”，而是像 iOS 26 的液态玻璃。改动只做参数级调整 +
把已有的真玻璃接到之前是实色的地方，没有重写玻璃核心，
`GlassShaders.kt`、`CrashGuard`、降级逻辑、诊断行全部保留。

## 先说实话：为什么之前总差一口气

看源码后找到 5 个真原因，不是你手感问题：

1. **背景是纯黑，玻璃下面什么都没有。**
   今日页背景是 `#000000` + 几乎看不见的暗角。折射折的是黑，
   模糊黑还是黑，色散也看不出来。iOS 的玻璃之所以惊艳，
   是因为它下面永远有彩色内容在动。
2. **V2 把模糊收得太狠了。**
   Regular 从 16dp 收到 11dp，是为了让 Dock 后面的字更清楚，
   但代价是玻璃直接变回“灰色板子”。iOS 26 底栏模糊在 20dp 上下，
   字是靠蒙层 + 对比度保证可读，不是靠调小模糊。
3. **Lens 折射太浅。**
   之前 `refractionHeight 16dp / Amount 22dp`，边缘几乎看不出弯曲。
   iOS 的玻璃泡边缘是把背景明显拽弯一圈的。
4. **蒙层太厚，把折射盖住了。**
   Lens 蒙层是 28% 白，玻璃泡看起来就是一块实色圆片。
5. **灵动岛根本不是玻璃。**
   `IslandBar` 用的是 `Color(0xB8161618)`，72% 不透明的实色黑条。
   截图里最显眼的顶部胶囊，之前完全没走玻璃。

## 本轮改了什么

### `Glass.kt`（核心参数，全部集中在这里）
- 新增 `LiquidGlassV3` 参数总表：以后做别的 App，照抄这组数就是同一套玻璃。
- Lens（玻璃泡 / FAB）：模糊 8dp → **16dp**；
  折射 `refractionHeight 16 → 34dp`，`refractionAmount 22 → 46dp`；
  加饱和度 1.18、亮度 +0.03；蒙层 0.28 → **0.16**；镜内放大 1.07 → **1.12**。
- Regular（Dock 长条 / 顶栏 / 灵动岛）：模糊 11dp → **20dp**；
  饱和度 1.08 → 1.22，亮度 +0.02。
- 高光从“糊开的雾”改成“细而亮的边”：
  宽 1.2dp → 1.6dp，模糊 7dp → **3dp**，强度 0.9 → 1.0，falloff 1.35 → 0.85。
- 前表面渐变顶部提亮：Regular 0x2A → 0x40 白，Lens 0x28 → 0x38 白。
- 描边加亮：Lens 深色 0x66 → 0x8C 白，Regular 0x58 → 0x66 白。

### `TodayV3.kt`
- `NeutralBackground`：顶部加 8% 紫光、底部加 6% 青光。
  肉眼看还是黑底，但玻璃边缘滑过时能拽出一点点颜色。
  如果觉得花，把 `0x14 / 0x0F` 这两个透明度调小，别直接删掉，
  删掉就回到“折射折了个寂寞”。
- `IslandBar`：从实色黑条改成 `GlassSurface(tier = Regular)` 真玻璃。
  它在 `BackdropHost` 之外，`LocalBackdrop` 是有的，可以直接采样。
- `WeekChip` 没动真玻璃：它在 `BackdropHost` 录制层内，
  `LocalBackdrop` 是 null，硬套玻璃会自采样闪退，只能用蒙层等效。

### `MainActivity.kt`
- Dock 选中泡从 `CircleShape`（64×52 的椭圆）改成 `RoundedCornerShape(26.dp)` 胶囊。
  iOS 的选中泡是标准胶囊，椭圆会让折射 SDF 算歪，一侧弯得多、一侧弯得少。
- FAB 放大 1.10 → **1.14**。

### `Theme.kt`
- 深色 Dock 蒙层从 0x2A（16.5% 白）降到 0x1F（12% 白）。

## 没改的东西（故意没动）
- `GlassShaders.kt` 的 AGSL 着色器、坐标规则、CrashGuard、诊断行、降级逻辑。
- 业务页面、数据、路由、AI、设置、Widget。
- 依赖：仍是 `com.github.Kyant0:AndroidLiquidGlass:1.0.0-rc01`，没有升级。

## 真机验收（必须真机，模拟器看不出 AGSL）
1. 先在原来那台小米 / HyperOS 真机上装包，进今日页。
2. 慢慢上下滑课程列表，看 Dock：列表文字从 Dock 下面滑过时，
   应该先被模糊、再在 Dock 边缘被轻轻弯曲，而不是直接露出来。
3. 点 Dock 切换 Tab，看选中泡：泡里的图标和背景应该比外面大一圈，
   边缘有一条细亮线，泡滑过去时有轻微拉伸回弹。
4. 看灵动岛：不再是一条实色黑条，背后内容滚动时应该能透出来。
5. 看 FAB：+ 号那颗圆钮边缘应该有折射 + 色散，不是纯灰圆。
6. 如果出现整卡消失 / 闪退，先看页面底部诊断行，
   `GlassRuntime.lastError` 会告诉你是 AGSL 编译失败还是驱动问题，
   玻璃会自动降级成平面卡，不会带崩 App。

## 如果还觉得不够像，先调这 3 个数，一次只动一个
- 折射不够弯：`Glass.kt` 里 `refractionAmount 46 → 56dp`
- 边缘不够亮：`Highlight` 的 `falloff 0.85 → 0.7`
- 整体太透 / 太糊：`Regular blur 20 → 18dp`，或 `dockTint 0x1F → 0x24`

## 诚实说明：为什么还不是 100%
苹果的液态玻璃是系统级 Metal 着色器 + 合成器，安卓上 Kyant 的
AGSL Lens 是最接近的开源近似。做到这轮之后，在深色 + 有内容的
背景下已经非常接近 iOS 26 的观感；但在纯黑、静止、无内容的区域，
任何实现都看不出折射 —— 这是物理限制，不是参数问题。
本环境没有 Android SDK / Java，所以没有在这里编译 APK，
请在 Workbuddy / 有 SDK 的环境执行 `./gradlew assembleRelease`，
并在真机上按上面 6 步验收。
