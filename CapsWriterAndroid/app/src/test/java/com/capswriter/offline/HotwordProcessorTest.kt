package com.capswriter.offline

import com.capswriter.offline.util.HotwordProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotwordProcessorTest {

    @Test
    fun bareTermsAndReplacementTargetsBecomeContext() {
        val config = HotwordProcessor.parse(
            """
            CapsWriter
            语音输入
            错词=>正确词
            """.trimIndent()
        )
        assertEquals("CapsWriter、语音输入、正确词", config.context)
        assertEquals(1, config.rules.size)
    }

    @Test
    fun literalReplacementWorks() {
        val text = HotwordProcessor.apply(
            "这是错词，另一个错词。",
            "错词=>正确词",
        )
        assertEquals("这是正确词，另一个正确词。", text)
    }

    @Test
    fun longerLiteralSourceWins() {
        val text = HotwordProcessor.apply(
            "语音输入法很好用",
            "输入法=>输入方式\n语音输入法=>CapsWriter",
        )
        assertEquals("CapsWriter很好用", text)
    }

    @Test
    fun regularExpressionReplacementWorks() {
        val text = HotwordProcessor.apply(
            "卡普斯和卡普丝",
            "re:卡普[斯丝]=>Caps",
        )
        assertEquals("Caps和Caps", text)
    }

    @Test
    fun invalidRegexIsIgnored() {
        val config = HotwordProcessor.parse(
            """
            re:[未闭合=>坏规则
            正常词
            """.trimIndent()
        )
        assertEquals(0, config.rules.size)
        assertTrue(config.context.contains("正常词"))
        assertEquals("原文", HotwordProcessor.apply("原文", config))
    }

    @Test
    fun commentsAndBlankLinesAreIgnored() {
        val config = HotwordProcessor.parse(
            """
            # 注释
            
            热词
            """.trimIndent()
        )
        assertEquals("热词", config.context)
        assertEquals(0, config.rules.size)
    }
}
