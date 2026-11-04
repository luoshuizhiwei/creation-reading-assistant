package com.creationreadingassistant.feature.reader.locator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegadoProgressMarkerTest {

    @Test
    fun `marker round trips clamped chapter position`() {
        val encoded = LegadoProgressMarker.encode(-1, 42)

        assertEquals(LegadoChapterPosition(0, 42), LegadoProgressMarker.decode(encoded))
    }

    @Test
    fun `marker rejects missing half and unrelated progress json`() {
        assertNull(LegadoProgressMarker.decode("""{"legado_position_v1":{"ci":2}}"""))
        assertNull(LegadoProgressMarker.decode("""{"offset":42,"chapter":2}"""))
    }
}
