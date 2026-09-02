package com.creationreadingassistant.ui.screen.reader.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAiRequestPolicyTest {

    @Test
    fun `selection and chapter context stay distinct`() {
        assertEquals(ReaderAiContextScope.SELECTION, readerAiContextScope(true))
        assertEquals(ReaderAiContextScope.CURRENT_CHAPTER, readerAiContextScope(false))
    }

    @Test
    fun `request context is capped at the disclosed limit`() {
        assertEquals(12, readerAiSentCharacterCount("123456789012"))
        assertEquals(AI_READER_CONTEXT_LIMIT, readerAiSentCharacterCount("x".repeat(4_001)))
    }

    @Test
    fun `question answer requires both context and a question`() {
        assertFalse(canSubmitReaderAiRequest("qa", "正文", "   "))
        assertFalse(canSubmitReaderAiRequest("summary", "", ""))
        assertTrue(canSubmitReaderAiRequest("qa", "正文", "这个人物的动机是什么？"))
        assertTrue(canSubmitReaderAiRequest("summary", "正文", ""))
    }

    @Test
    fun `only completed successful output can be saved as inspiration`() {
        assertTrue(canSaveReaderAiResult("完整解读", loading = false, error = null))
        assertFalse(canSaveReaderAiResult("流式中", loading = true, error = null))
        assertFalse(canSaveReaderAiResult("半成品", loading = false, error = "请求失败"))
        assertFalse(canSaveReaderAiResult("", loading = false, error = null))
    }

    @Test
    fun `failed or cancelled requests discard streamed partial output`() {
        assertEquals("完整结果", readerAiResultForRequestOutcome("完整结果", successful = true))
        assertEquals("", readerAiResultForRequestOutcome("流式半成品", successful = false))
    }
}
