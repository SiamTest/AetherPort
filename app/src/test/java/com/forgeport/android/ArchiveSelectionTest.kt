package com.forgeport.android

import com.forgeport.android.model.PublishProject
import com.forgeport.android.model.selectedArchiveId
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveSelectionTest {
    private val old = PublishProject("zip:old", "old.zip", 10, 100)
    private val newest = PublishProject("zip:new", "new.zip", 20, 100)

    @Test fun automaticSelectionFollowsNewFilesWithoutOpeningTheMenu() {
        assertEquals(old.id, selectedArchiveId(listOf(old), ""))
        assertEquals(newest.id, selectedArchiveId(listOf(newest, old), ""))
    }

    @Test fun explicitOlderChoiceIsRespectedAndMissingSelectionFallsBack() {
        assertEquals(old.id, selectedArchiveId(listOf(newest, old), old.id))
        assertEquals(newest.id, selectedArchiveId(listOf(newest), old.id))
        assertEquals("", selectedArchiveId(emptyList(), old.id))
        assertEquals(newest.id, selectedArchiveId(listOf(newest), "staged:obsolete"))
    }
}
