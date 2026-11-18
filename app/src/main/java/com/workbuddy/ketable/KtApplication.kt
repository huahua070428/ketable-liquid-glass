package com.workbuddy.ketable

import android.app.Application

/**
 * 进程入口。这里只做两件事，都必须在任何界面之前完成：
 *
 * 1. [DataStore.init] —— 数据层先就绪，崩溃处理器要读设置。
 * 2. [CrashGuard.install] —— 注册 UncaughtExceptionHandler + 读上次启动标记。
 *    放在 Application 里而不是 Activity 里，才能覆盖「还没进 Activity 就崩」的情况。
 */
class KtApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataStore.init(this)
        CrashGuard.install(this)
        CrashGuard.mark("app_create")
    }
}
