package com.forgeport.android

import com.forgeport.android.model.ProjectArchive
import com.forgeport.android.model.newestArchives
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveOrderTest {
    @Test fun latestZipIsFirstWithStableTiesAndUnknownDatesLast() {
        val old = ProjectArchive("content://provider/old", "old.zip", 10, 100)
        val newest = ProjectArchive("content://provider/new", "new.zip", 30, 100)
        val unknown = ProjectArchive("content://provider/unknown", "unknown.zip", 0, 100)
        val sameDate = ProjectArchive("content://provider/a", "a.zip", 30, 100)
        val sameName = ProjectArchive("content://provider/other", "a.zip", 30, 100)
        assertEquals(listOf(sameDate, sameName, newest, old, unknown), newestArchives(listOf(unknown, newest, old, sameName, sameDate, old)))
        assertEquals(emptyList<ProjectArchive>(), newestArchives(emptyList()))
    }
}
