package com.workbuddy.ketable

/**
 * AGSL(SkSL)着色器。API 33+ 可用。
 *
 * 思路:背景是程序化生成的(渐变 + 高斯光斑),玻璃卡片在片元着色器里
 * 重新计算"虚拟背景",在边缘带内沿 SDF 法线做位移采样(折射),
 * 并对 R/G/B 用不同位移量制造色差;玻璃内部与真实背景完全连续,
 * 因此看起来是"透"的,只有折射、高光、暗角在提示玻璃的存在。
 */
object GlassShaders {

    /** 两个着色器共用的函数库(背景 = 玻璃的虚拟背景,保证完全一致) */
    private const val COMMON = """
uniform float2 uRes;
uniform float uTiltX;
uniform float uTiltY;
uniform float uLight;   // 0=深空深色 1=晨雾浅色（仅换基色与强度，不改结构）

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float3 blob(float3 col, float2 uv, float2 c, float3 tint, float amp) {
    float2 d = uv - c;
    d.x *= uRes.x / uRes.y;
    float g = exp(-dot(d, d) / 0.016);
    return mix(col, tint, g * amp);
}

float3 backdrop(float2 sp, float2 tilt) {
    float2 uv = sp / uRes;
    // 深空：近黑 → 深蓝紫；晨雾：冷白 → 浅蓝灰
    float3 darkCol = mix(float3(0.043, 0.051, 0.071), float3(0.082, 0.090, 0.220), uv.y);
    float3 liteCol = mix(float3(0.965, 0.969, 0.988), float3(0.914, 0.922, 0.957), uv.y);
    float3 col = mix(darkCol, liteCol, uLight);
    float2 t = tilt * 0.02;
    // 浅色下光斑同样存在,但幅度压到 22%,只留"隐约有色"的呼吸感
    float amp = mix(1.0, 0.22, uLight);
    col = blob(col, uv, float2(0.15, 0.10) + t, float3(0.486, 0.361, 1.000), 0.50 * amp);
    col = blob(col, uv, float2(0.88, 0.30) - t, float3(0.937, 0.290, 0.610), 0.38 * amp);
    col = blob(col, uv, float2(0.08, 0.60) + t * 0.6, float3(0.100, 0.720, 0.750), 0.30 * amp);
    col = blob(col, uv, float2(0.80, 0.88) - t * 0.6, float3(0.180, 0.760, 0.560), 0.26 * amp);
    return col;
}

float2 sdfGrad(float2 p, float2 b, float r) {
    float e = 1.5;
    float gx = sdRoundRect(p + float2(e, 0.0), b, r) - sdRoundRect(p - float2(e, 0.0), b, r);
    float gy = sdRoundRect(p + float2(0.0, e), b, r) - sdRoundRect(p - float2(0.0, e), b, r);
    return float2(gx, gy) / (2.0 * e);
}

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
"""

    /** 全屏背景 */
    val BG = COMMON + """
half4 main(float2 fragCoord) {
    float2 tilt = float2(uTiltX, uTiltY);
    float3 col = backdrop(fragCoord, tilt);
    float2 uv = fragCoord / uRes;
    col *= 1.0 - mix(0.30, 0.05, uLight) * length(uv - float2(0.5, 0.45));
    col += (hash(fragCoord) - 0.5) * 0.015;
    return half4(col.x, col.y, col.z, 1.0);
}
"""

    /** 玻璃卡片 */
    val GLASS = COMMON + """
uniform float2 uCardPos;
uniform float2 uCardSize;
uniform float uRadius;
uniform float uPress;

half4 main(float2 fragCoord) {
    // fragCoord 是卡片节点本地坐标(0..宽, 0..高)
    float2 p = fragCoord;
    // 屏幕坐标 = 本地 + 卡片在屏幕上的偏移,虚拟背景与真实背景逐像素对齐
    float2 spScreen = fragCoord + uCardPos;
    float2 center = uCardSize * 0.5;
    float2 b = center - 2.0;
    float sd = sdRoundRect(p - center, b, uRadius);
    float alpha = clamp(-sd / 1.5, 0.0, 1.0);
    if (alpha <= 0.0) { return half4(0.0, 0.0, 0.0, 0.0); }

    float2 tilt = float2(uTiltX, uTiltY);
    float2 n = normalize(sdfGrad(p - center, b, uRadius) + float2(0.000001, 0.000001));
    float band = min(uCardSize.x, uCardSize.y) * 0.18;
    float t = clamp(1.0 + sd / band, 0.0, 1.0);
    float s = mix(100.0, 72.0, uPress) * t * t;

    // 折射 + RGB 色差
    float3 colR = backdrop(spScreen + n * s * 1.12, tilt);
    float3 colG = backdrop(spScreen + n * s * 1.00, tilt);
    float3 colB = backdrop(spScreen + n * s * 0.88, tilt);
    float3 refract = float3(colR.x, colG.y, colB.z);

    // 内部:与背景连续,略微提亮以示"透光"
    float3 innerDark = backdrop(spScreen, tilt) * 1.18 + 0.02;
    float3 innerLite = mix(backdrop(spScreen, tilt), float3(1.0), 0.55);
    float3 interior = mix(innerDark, innerLite, uLight);
    float3 col = mix(interior, refract, smoothstep(0.05, 0.45, t));

    // 顶部 1.5px 内亮线,顶部强、四周衰减
    float rim = exp(-pow((sd + 1.6) / 1.3, 2.0));
    float upness = clamp(-n.y, 0.0, 1.0);
    col += float3(rim * (0.15 + 0.45 * upness)) * (1.0 - uLight);
    col -= float3(rim * 0.05 * uLight);

    // 底部环境光遮蔽
    col *= 1.0 - 0.10 * clamp(n.y, 0.0, 1.0) * smoothstep(0.0, 0.5, t);

    // 随手机倾斜移动的高光光斑,按压时增强
    float2 hi = center + tilt * min(uCardSize.x, uCardSize.y) * 0.28;
    float d2 = dot(p - hi, p - hi);
    float spot = exp(-d2 / (0.30 * dot(center, center)));
    col += float3(spot * (0.22 + 0.30 * uPress)) * (1.0 - 0.75 * uLight);

    // 颗粒
    col += (hash(spScreen) - 0.5) * 0.02;

    return half4(col.x, col.y, col.z, alpha);
}
"""
}
