package com.creationreadingassistant.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchIndexWorkerPolicyTest {

    @Test
    fun `first two failed attempts retry`() {
        assertEquals(SearchIndexWorkerFailureAction.RETRY, searchIndexWorkerFailureAction(0))
        assertEquals(SearchIndexWorkerFailureAction.RETRY, searchIndexWorkerFailureAction(1))
    }

    @Test
    fun `third and later failed attempts stop automatic retry`() {
        assertEquals(SearchIndexWorkerFailureAction.FAIL, searchIndexWorkerFailureAction(2))
        assertEquals(SearchIndexWorkerFailureAction.FAIL, searchIndexWorkerFailureAction(5))
    }
}
