package local.creationReadingAssistant.reader.legado

import org.junit.Assert.assertEquals
import org.junit.Test

class LegadoCoreProvenanceTest {
    @Test
    fun upstreamSnapshotIsPinned() {
        assertEquals(40, LegadoCoreProvenance.UPSTREAM_COMMIT.length)
        assertEquals("GPL-3.0-only", LegadoCoreProvenance.LICENSE)
    }
}
