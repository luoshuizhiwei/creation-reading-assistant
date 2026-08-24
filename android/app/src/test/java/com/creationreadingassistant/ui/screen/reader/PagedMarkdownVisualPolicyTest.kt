package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.pager.PagedMarkdownVisualPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedMarkdownVisualPolicyTest {

    @Test
    fun `paged table rows use a panel and monospace text while header is emphasized`() {
        val header = PagedMarkdownVisualPolicy.styleFor(BlockRole.TABLE_HEADER)
        val row = PagedMarkdownVisualPolicy.styleFor(BlockRole.TABLE_ROW)

        assertTrue(header.drawPanel)
        assertTrue(row.drawPanel)
        assertTrue(header.monospace)
        assertTrue(row.monospace)
        assertTrue(header.emphasized)
        assertFalse(row.emphasized)
    }

    @Test
    fun `ordinary prose does not inherit table decoration`() {
        val body = PagedMarkdownVisualPolicy.styleFor(BlockRole.BODY)

        assertFalse(body.drawPanel)
        assertFalse(body.monospace)
        assertFalse(body.emphasized)
    }
}
