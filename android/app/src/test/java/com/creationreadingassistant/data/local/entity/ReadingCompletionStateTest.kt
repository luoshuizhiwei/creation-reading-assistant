package com.creationreadingassistant.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingCompletionStateTest {
    @Test fun supportedStatesRoundTripThroughRoomValue() {
        ReadingCompletionState.entries.forEach { state ->
            assertEquals(state, ReadingCompletionState.fromStorage(state.storageValue))
        }
    }

    @Test fun oldCompletedAliasMigratesInMemoryToFinished() {
        assertEquals(
            ReadingCompletionState.FINISHED,
            ReadingCompletionState.fromStorage("completed"),
        )
    }

    @Test fun missingOrUnknownStateIsReading() {
        assertEquals(ReadingCompletionState.READING, ReadingCompletionState.fromStorage(null))
        assertEquals(ReadingCompletionState.READING, ReadingCompletionState.fromStorage("unknown"))
    }
}
