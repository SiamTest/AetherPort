package com.forgeport.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OperationResultTest {
    @Test fun successNeverDisplaysAStatus() {
        assertNull(OperationResult(true, "Published successfully to main.", "Commit 12345678").failureOrNull())
    }

    @Test fun noChangesIsAlsoSuccess() {
        assertNull(OperationResult(true, "No changes found. Nothing was pushed.").failureOrNull())
    }

    @Test fun failedPublishPreservesErrorAndDiagnosticLog() {
        val failure = OperationResult(false, "Permission denied", "HTTP 403")
        assertSame(failure, failure.failureOrNull())
        assertEquals("HTTP 403", failure.failureOrNull()?.log)
    }
}
