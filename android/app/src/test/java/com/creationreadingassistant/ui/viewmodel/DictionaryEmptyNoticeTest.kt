package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.feature.dictionary.DictionaryLookupEntry
import com.creationreadingassistant.feature.dictionary.InstalledDictionary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 词典面板「无释义可展示」时的文案推导（R3-X1 缺陷收口）。
 *
 * 这组用例守的是一条产品约束：**不得静默伪造释义，也不得静默伪造「查不到」**。
 * 空选区、未装词库、词库打不开、查询链路失败是四种不同的原因，
 * 用户看到的解释必须与真实原因一致，否则他会去排查错误的方向。
 */
class DictionaryEmptyNoticeTest {

    private fun dict(baseName: String) = InstalledDictionary(
        baseName = baseName,
        bookName = baseName,
        wordCount = 1,
        description = "",
        dir = File(baseName),
    )

    private fun hit(word: String) = DictionaryLookupEntry(
        sourceName = "测试词库",
        word = word,
        content = "释义",
        typeCode = 'm',
    )

    @Test
    fun `a blank selection is reported as nothing to look up`() {
        val notice = dictionaryEmptyNotice(
            DictionaryUiState(word = "", loading = false, searched = true),
        )

        assertTrue(notice!!.contains("没有可查的词"))
    }

    @Test
    fun `nothing is claimed while the lookup is still running or never started`() {
        assertNull(dictionaryEmptyNotice(DictionaryUiState(word = "词", loading = true)))
        assertNull(dictionaryEmptyNotice(DictionaryUiState(word = "词", searched = false)))
    }

    @Test
    fun `hits suppress every empty notice`() {
        assertNull(
            dictionaryEmptyNotice(
                DictionaryUiState(word = "词", searched = true, results = listOf(hit("词"))),
            ),
        )
    }

    @Test
    fun `an empty library points at the import entry`() {
        val notice = dictionaryEmptyNotice(DictionaryUiState(word = "词", searched = true))

        assertTrue(notice!!.contains("还没装离线词库"))
    }

    @Test
    fun `a real miss is worded as a miss`() {
        val notice = dictionaryEmptyNotice(
            DictionaryUiState(word = "词", searched = true, installed = listOf(dict("甲"))),
        )

        assertTrue(notice!!.contains("没有「词」"))
        assertFalse("没有词库故障时不要提「打不开」", notice.contains("没能打开"))
    }

    @Test
    fun `a partly unreadable library still discloses which dictionary failed`() {
        val notice = dictionaryEmptyNotice(
            DictionaryUiState(
                word = "词",
                searched = true,
                installed = listOf(dict("甲"), dict("乙")),
                unreadableDictionaries = listOf("乙"),
            ),
        )

        assertTrue("必须说明有词库没读上", notice!!.contains("没能打开"))
        assertTrue("必须点名是哪一部", notice.contains("乙"))
        assertTrue("必须说明还有几部能读", notice.contains("在能读的 1 部里"))
    }

    @Test
    fun `a fully unreadable library refuses to confirm anything about the word`() {
        val notice = dictionaryEmptyNotice(
            DictionaryUiState(
                word = "词",
                searched = true,
                installed = listOf(dict("甲")),
                unreadableDictionaries = listOf("甲"),
            ),
        )

        assertTrue(notice!!.contains("无法确认"))
        assertFalse("不能把「读不上」说成「没收录」", notice.contains("没有「词」"))
    }

    @Test
    fun `a repository failure outranks every other explanation`() {
        val notice = dictionaryEmptyNotice(
            DictionaryUiState(
                word = "词",
                searched = true,
                installed = listOf(dict("甲")),
                unreadableDictionaries = listOf("甲"),
                error = "读取离线词库失败（IOException），请检查词库目录或重新导入。",
            ),
        )

        assertTrue(notice!!.startsWith("读取离线词库失败"))
    }

    @Test
    fun `the status line reflects loading and the installed count`() {
        assertEquals(
            "查询中…",
            dictionaryStatusLine(DictionaryUiState(loading = true, installed = listOf(dict("甲")))),
        )
        assertEquals("尚未安装离线词库", dictionaryStatusLine(DictionaryUiState()))
        assertEquals(
            "已装 2 部词库",
            dictionaryStatusLine(DictionaryUiState(installed = listOf(dict("甲"), dict("乙")))),
        )
    }
}
