# AGSL 着色器字符串必须原样保留（R8 不做字符串优化，但显式声明避免后续误伤）
-keep class com.workbuddy.ketable.GlassShaders { *; }
# Glance 小组件通过反射实例化
-keep class com.workbuddy.ketable.NextClassWidgetReceiver { *; }

# Liquid Glass（Kyant0/AndroidLiquidGlass）：AGSL 着色器字符串与 GraphicsLayer 采样，
# 不要裁剪，否则真机上玻璃会退化成纯色面
-keep class com.kyant.backdrop.** { *; }
-keepclassmembers class com.kyant.backdrop.** { *; }


# ---- v5.3 稳定性优先 ----
# 不混淆：崩溃堆栈要能直接读（CrashGuard 会把堆栈写进本地文件）
-dontobfuscate
# 自己的代码全量保留，避免任何「被裁剪/被内联」导致的运行期差异
-keep class com.workbuddy.ketable.** { *; }
-keepclassmembers class com.workbuddy.ketable.** { *; }
# 保留行号，堆栈里能看到具体行
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
