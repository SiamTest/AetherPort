package com.forgeport.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryPrefetchTest {
    private fun gallery(count: Int) = Gallery("https://e-hentai.org/g/42/abcd/", "Example", "Manga", "English", "", emptyList(), List(count) { "" })

    @Test fun preloadsCurrentPageAndTwentyAheadWithinGalleryBounds() {
        assertEquals((0..20).toList(), gallery(100).preloadPages(0).toList())
        assertEquals((39..59).toList(), gallery(100).preloadPages(39).toList())
        assertEquals((95..99).toList(), gallery(100).preloadPages(95).toList())
        assertEquals(listOf(0), gallery(1).preloadPages(0).toList())
        assertEquals(listOf(99), gallery(100).preloadPages(1000).toList())
        assertEquals((0..20).toList(), gallery(100).preloadPages(-1).toList())
    }

    @Test fun clearingHistoryPreservesLibraryAndOfflinePages() {
        val original = gallery(50).copy(saved = true, downloaded = 50, lastRead = 35, visitedAt = 1234L)
        val cleared = original.withoutHistory()
        assertEquals(original.copy(lastRead = 0, visitedAt = 0), cleared)
        assertEquals(original.pages, cleared.pages)
        assertEquals(true, cleared.saved)
        assertEquals(50, cleared.downloaded)
    }
}
