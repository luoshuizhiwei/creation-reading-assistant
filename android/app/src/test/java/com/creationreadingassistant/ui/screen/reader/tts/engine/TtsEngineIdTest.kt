package com.creationreadingassistant.ui.screen.reader.tts.engine

import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId.Companion.fromKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** TtsEngineId 持久化键解析：未知/空值必须回退 SYSTEM（本地优先），不得抛异常。 */
class TtsEngineIdTest {

    @Test
    fun `null and unknown keys fall back to system`() {
        assertEquals(TtsEngineId.SYSTEM, fromKey(null))
        assertEquals(TtsEngineId.SYSTEM, fromKey(""))
        assertEquals(TtsEngineId.SYSTEM, fromKey("bogus"))
        assertEquals(TtsEngineId.SYSTEM, fromKey("Edge-TTS"))
    }

    @Test
    fun `known keys parse case-insensitively`() {
        assertEquals(TtsEngineId.SYSTEM, fromKey("system"))
        assertEquals(TtsEngineId.SYSTEM, fromKey("SYSTEM"))
        assertEquals(TtsEngineId.EDGE, fromKey("edge"))
        assertEquals(TtsEngineId.EDGE, fromKey("EDGE"))
    }

    @Test
    fun `key roundtrip is stable`() {
        TtsEngineId.entries.forEach { id ->
            assertEquals(id, fromKey(id.key))
        }
    }
}
