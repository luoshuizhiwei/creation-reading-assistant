package local.creationReadingAssistant.reader.legado.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPosTest {
    @Test
    fun compareOrdersPageLineAndColumn() {
        val origin = TextPos(0, 2, 4)
        assertEquals(-3, origin.compare(TextPos(1, 0, 0)))
        assertEquals(2, origin.compare(TextPos(0, 1, 99)))
        assertEquals(-1, origin.compare(TextPos(0, 2, 5)))
        assertEquals(0, origin.compare(TextPos(0, 2, 4)))
    }

    @Test
    fun resetClearsSelection() {
        val pos = TextPos(1, 2, 3)
        assertTrue(pos.isSelected())
        pos.reset()
        assertFalse(pos.isSelected())
    }
}
