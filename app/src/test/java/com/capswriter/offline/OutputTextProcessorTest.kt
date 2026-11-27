package com.capswriter.offline

import com.capswriter.offline.util.OutputTextProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputTextProcessorTest {

    @Test
    fun removesOnlyTheLastTrailingPunctuation() {
        assertEquals("你好，", OutputTextProcessor.trimTrailingPunctuation("你好，。"))
        assertEquals("hello world,", OutputTextProcessor.trimTrailingPunctuation("hello world,."))
    }

    @Test
    fun removesWhitespaceAfterPunctuation() {
        assertEquals("你好", OutputTextProcessor.trimTrailingPunctuation("你好。  "))
    }

    @Test
    fun keepsInternalPunctuation() {
        assertEquals("你好，世界", OutputTextProcessor.trimTrailingPunctuation("你好，世界。"))
    }

    @Test
    fun doesNotRemoveQuestionOrExclamation() {
        assertEquals("真的吗？", OutputTextProcessor.trimTrailingPunctuation("真的吗？"))
    }

    @Test
    fun shortSentenceBelowThresholdGetsTrimmed() {
        assertEquals(
            "一二三四五六七八",
            OutputTextProcessor.trimTrailingPunctuation("一二三四五六七八。", threshold = 8),
        )
    }

    @Test
    fun longerSentenceAboveThresholdKeepsPunctuation() {
        assertEquals(
            "一二三四五六七八九。",
            OutputTextProcessor.trimTrailingPunctuation("一二三四五六七八九。", threshold = 8),
        )
    }

    @Test
    fun zeroThresholdAlwaysTrims() {
        assertEquals(
            "这是一段很长的话",
            OutputTextProcessor.trimTrailingPunctuation("这是一段很长的话。", threshold = 0),
        )
    }

    @Test
    fun forceOverridesThresholdForWeChat() {
        assertEquals(
            "一二三四五六七八九",
            OutputTextProcessor.trimTrailingPunctuation(
                "一二三四五六七八九。",
                threshold = 8,
                force = true,
            ),
        )
    }

    @Test
    fun countsChineseCharactersEnglishWordsAndNumbers() {
        assertEquals(4, OutputTextProcessor.countSemanticUnits("你好CapsWriter123"))
    }
}
