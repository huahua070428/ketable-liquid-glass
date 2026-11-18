package com.workbuddy.ketable

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 纯逻辑校验：文本导入解析 + 本地问答，不涉及渲染 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogicTest {

    @Before
    fun seed() {
        DataStore.init(ApplicationProvider.getApplicationContext())
        DataStore.resetAll()
        DataStore.saveCourses(
            listOf(
                Course(id = "a", name = "高等数学", teacher = "张三", location = "教一101",
                    day = 1, startSection = 1, endSection = 2, weekStart = 1, weekEnd = 16),
                Course(id = "b", name = "大学英语", teacher = "李四", location = "外语楼302",
                    day = 3, startSection = 3, endSection = 4, weekStart = 1, weekEnd = 16)
            )
        )
        DataStore.saveSettings(Settings(semesterStart = "2026-09-07"))
    }

    @Test
    fun parseTextImport() {
        val list = TimetableText.parse(
            """
            高等数学 周一 1-2节 教一101
            大学英语 周三 3-4节 外语楼302
            体育 周五 第7节 操场
            这行没有节次，应该被忽略
            """.trimIndent()
        )
        assertEquals(3, list.size)
        assertEquals("高等数学", list[0].name)
        assertEquals(1, list[0].day)
        assertEquals(1, list[0].startSection)
        assertEquals(2, list[0].endSection)
        assertTrue(list[0].location.contains("101"))

        assertEquals(3, list[1].day)
        assertEquals("外语楼302", list[1].location)

        assertEquals(5, list[2].day)
        assertEquals(7, list[2].startSection)
        assertEquals(8, list[2].endSection)
    }

    @Test
    fun localQaAnswers() {
        assertNotNull(LocalQA.answer("明天几点上课"))
        assertNotNull(LocalQA.answer("周三在哪上课"))
        assertNotNull(LocalQA.answer("这周有多少节课"))
        assertNotNull(LocalQA.answer("下一节是什么课"))
        assertNotNull(LocalQA.answer("高等数学老师是谁"))
        assertNull(LocalQA.answer("帮我写一首诗"))
    }

    @Test
    fun weekMath() {
        val s = DataStore.settings
        // 2026-11-16 是周一，距 9/7 整 10 周
        assertEquals(11, Sch.weekNo(java.time.LocalDate.of(2026, 11, 16), s))
        val c = DataStore.courses[0]
        assertTrue(Sch.activeInWeek(c, 11))
        assertTrue(!Sch.activeInWeek(c, 17))
    }
}
