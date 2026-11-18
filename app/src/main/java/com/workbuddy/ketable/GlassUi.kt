package com.workbuddy.ketable

import android.content.Context
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 屏幕自诊断:着色器/传感器状态直接显示在页面上,便于远程定位 */
object DemoDiag {
    val bg = mutableStateOf("—")
    val glass = mutableStateOf("—")
    val sensor = mutableStateOf(false)
}

/** 陀螺仪倾斜量,-1..1 */
data class Tilt(val x: Float, val y: Float)

/** 陀螺仪:倾斜手机,高光和光斑实时移动(WebView 做不到的跟手动态) */
@Composable
fun rememberTilt(): MutableState<Tilt> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Tilt(0f, 0f)) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rMat = FloatArray(9)
        val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrix(rMat, null, e.values, null)
                SensorManager.getOrientation(rMat, ori)
                tilt.value = Tilt(
                    (ori[2] / 0.6f).coerceIn(-1f, 1f),
                    (ori[1] / 0.6f).coerceIn(-1f, 1f)
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            DemoDiag.sensor.value = true
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}

/**
 * v5.1 自研 AGSL 玻璃（保留，不再作为页面默认卡片：AGSL 只做屏幕空间假折射）。
 * v5.2 起页面统一走 Glass.kt 的新引擎；旧实现留着做回退对照，禁止直接删除。
 */
@Deprecated("v5.2 起改用 Glass.kt 的 GlassSurface / FlatCard")
@Composable
fun LegacyGlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    tiltX: Float,
    tiltY: Float,
    screen: IntSize,
    light: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "press"
    )
    var origin by remember { mutableStateOf(Offset.Zero) }
    val glassOn = LocalGlassOn.current

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer {
                val s = 1f - 0.02f * press
                scaleX = s
                scaleY = s
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false }
                    }
                )
            }
    ) {
        // 玻璃层:matchParentSize 跟随内容尺寸,只负责自己画玻璃
        Box(Modifier.matchParentSize().glassPanel(screen, origin, corner, tiltX, tiltY, press, light, glassOn))
        content()
    }
}

@Composable
private fun Modifier.glassPanel(
    screen: IntSize,
    origin: Offset,
    corner: Dp,
    tiltX: Float,
    tiltY: Float,
    press: Float,
    light: Float,
    glassOn: Boolean
): Modifier {
    val density = LocalDensity.current
    val radiusPx = with(density) { corner.toPx() }
    val paint = remember { Paint() }
    val shader = remember {
        try {
            RuntimeShader(GlassShaders.GLASS).also { DemoDiag.glass.value = "OK" }
        } catch (t: Throwable) {
            DemoDiag.glass.value = "异常:" + (t.message?.take(48) ?: "?")
            null
        }
    }
    return drawWithCache {
        // onDrawBehind:只画玻璃本身,完全不干预内容绘制
        onDrawBehind {
            if (!glassOn) {
                // 关掉液态玻璃：不跑 AGSL，退回纯色半透明面（比 shader 省电，文字更实）
                drawRoundRect(
                    brush = SolidColor(if (light > 0.5f) Color(0xF2FFFFFF) else Color(0x16FFFFFF)),
                    cornerRadius = CornerRadius(radiusPx)
                )
                drawRoundRect(
                    brush = SolidColor(if (light > 0.5f) Color(0x12181C32) else Color(0x1FFFFFFF)),
                    cornerRadius = CornerRadius(radiusPx),
                    style = Stroke(width = 1.5f)
                )
                return@onDrawBehind
            }
            if (shader != null) {
                shader.setFloatUniform("uRes", screen.width.toFloat(), screen.height.toFloat())
                shader.setFloatUniform("uCardPos", origin.x, origin.y)
                shader.setFloatUniform("uCardSize", size.width, size.height)
                shader.setFloatUniform("uRadius", radiusPx)
                shader.setFloatUniform("uTiltX", tiltX)
                shader.setFloatUniform("uTiltY", tiltY)
                shader.setFloatUniform("uPress", press)
                shader.setFloatUniform("uLight", light)
                paint.shader = shader
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
            } else {
                // 降级玻璃:普通半透明圆角 + 描边
                drawRoundRect(
                    brush = SolidColor(
                        if (light > 0.5f) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f)
                    ),
                    cornerRadius = CornerRadius(radiusPx)
                )
                drawRoundRect(
                    brush = SolidColor(
                        if (light > 0.5f) Color(0x1A181C32) else Color.White.copy(alpha = 0.22f)
                    ),
                    cornerRadius = CornerRadius(radiusPx),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color(0x99FFFFFF))
    }
}
