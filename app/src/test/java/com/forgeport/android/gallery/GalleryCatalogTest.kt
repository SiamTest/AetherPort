package com.forgeport.android.gallery

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class GalleryCatalogTest {
    private val first = "https://e-hentai.org/g/42/abcd/"
    private fun parameters(url: String) = URI(url).rawQuery.split('&').associate {
        val parts = it.split('=', limit = 2)
        URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
    }

    @Test fun latestEncodesSearchAndEveryFilterAndRetainsOnlyPagingCursor() {
        val filters = GalleryFilters(setOf(4, 256), language = "English", uploader = "Sample user", minRating = 4, minPages = 10, maxPages = 90)
        val url = GalleryCatalog.request(GalleryCatalogMode.LATEST, "Science & art", filters,
            "https://e-hentai.org/?next=100&f_search=wrong&f_cats=0")
        val values = parameters(url)
        assertEquals("/", URI(url).path)
        assertEquals("Science & art language:english uploader:\"Sample user\"", values["f_search"])
        assertEquals("763", values["f_cats"])
        assertEquals("on", values["f_sr"]); assertEquals("4", values["f_srdd"])
        assertEquals("10", values["f_spf"]); assertEquals("90", values["f_spt"])
        assertEquals("100", values["next"]); assertEquals("dm_t", values["inline_set"])
        assertFalse(parameters(GalleryCatalog.request(GalleryCatalogMode.LATEST, "", filters, "https://example.org/?next=1")).containsKey("next"))
    }

    @Test fun popularUsesItsOwnFeedRatherThanUnsupportedSearchParameters() {
        val url = GalleryCatalog.request(GalleryCatalogMode.POPULAR, "sample", GalleryFilters(setOf(4)), "https://e-hentai.org/?next=1")
        assertEquals("/popular", URI(url).path)
        assertEquals(mapOf("inline_set" to "dm_t"), parameters(url))
    }

    @Test fun parsesThumbnailGridLazyCoversAndCursorWithoutDuplicatingGalleryLinks() {
        val html = """
            <div class='itg'><div class='gl1t'><div class='cn'>Manga</div>
              <a href='/g/42/abcd/'><div class='glink'>Sample &amp; Story</div></a>
              <a href='/g/42/abcd/'><img data-src='https://ehgt.org/sample.jpg' src='data:image/gif;base64,AA'></a>
            </div><div class='gl1t'><a href='/g/43/abce/'><div class='glink'>Second</div></a>
              <div style="background-image:url('https://ehgt.org/second.jpg')"></div>
            </div><div class='gl1t'><a href='https://example.org/g/44/abcd/'>Untrusted</a></div></div>
            <a id='unext' href='/?next=43&amp;f_search=sample'>Next</a>
        """
        val page = GalleryCatalog.parse("https://e-hentai.org/", html)
        assertEquals(2, page.items.size); assertEquals(first, page.items[0].url)
        assertEquals("Sample & Story", page.items[0].title)
        assertEquals("https://ehgt.org/sample.jpg", page.items[0].cover)
        assertEquals("https://ehgt.org/second.jpg", page.items[1].cover)
        assertEquals("https://e-hentai.org/?next=43&f_search=sample", page.next)
    }

    @Test fun compactRowsAndRelativeCoverStylesAreSupported() {
        val html = "<table class='itg'><tr><td class='cn'>Non-H</td><td><a href='$first'><span class='glink'>Neutral sample</span></a></td><td><img src='/cover.jpg'></td></tr></table>"
        val page = GalleryCatalog.parse("https://e-hentai.org/", html)
        assertEquals("Neutral sample", page.items.single().title)
        assertEquals("https://e-hentai.org/cover.jpg", page.items.single().cover)
        assertEquals("https://ehgt.org/cover.jpg", GalleryCatalog.coverFromStyle("background:url(//ehgt.org/cover.jpg)", first))
        assertEquals("", GalleryCatalog.coverFromStyle("url(https://example.org/cover.jpg)", first))
    }

    @Test fun popularFilteringUsesMetadataAndPreservesRankInsteadOfResorting() {
        val item = GallerySummary(first, "Sample science story", "", "Manga", 30, 4.5, "Sample user", listOf("language:english", "other:science fiction"))
        val filters = GalleryFilters(setOf(4), "English", "sample USER", 4, 20, 40)
        assertTrue(GalleryCatalog.matches(item, "\"science story\" -unwanted", filters))
        assertTrue(GalleryCatalog.matches(item, "science", filters))
        assertFalse(GalleryCatalog.matches(item, "-science", filters))
        assertFalse(GalleryCatalog.matches(item.copy(pages = null), "", filters))
        assertFalse(GalleryCatalog.matches(item.copy(rating = 3.5), "", filters))
        assertFalse(GalleryCatalog.matches(item.copy(tags = listOf("language:japanese")), "", filters))
        assertFalse(GalleryCatalog.matches(item.copy(category = "Non-H"), "", filters))
        val ranked = listOf(item.copy(url = "https://e-hentai.org/g/45/abcf/"), item.copy(pages = 2), item)
        assertEquals(listOf(ranked[0].url, first), ranked.filter { GalleryCatalog.matches(it, "", filters) }.map { it.url })
    }

    @Test fun emptyResultsAreValidButAccessPagesAreErrors() {
        assertTrue(GalleryCatalog.parse("https://e-hentai.org/", "<p>No hits found</p>").items.isEmpty())
        assertThrows(IllegalStateException::class.java) { GalleryCatalog.parse("https://e-hentai.org/", "<h1>Sign in</h1>") }
    }

    @Test fun validatesEmptyCategoriesAndPageRanges() {
        assertThrows(IllegalArgumentException::class.java) { GalleryFilters(categories = emptySet()).validate() }
        assertThrows(IllegalArgumentException::class.java) { GalleryFilters(minPages = 20, maxPages = 10).validate() }
        assertThrows(IllegalArgumentException::class.java) { GalleryFilters(maxPages = 10001).validate() }
        assertThrows(IllegalArgumentException::class.java) { GalleryFilters(minRating = 1).validate() }
        GalleryFilters().validate()
    }

    @Test fun automaticPagingWaitsForVisibleTailAndStopsOnLoadingErrorsOrEnd() {
        val items = (1..12).map { GallerySummary("https://e-hentai.org/g/$it/abcd/", "Sample $it", "", "Non-H") }
        val state = GalleryCatalogState(items = items, next = "https://e-hentai.org/?next=1")
        assertFalse(state.canLoadMore(-1))
        assertFalse(state.canLoadMore(5))
        assertTrue(state.canLoadMore(8))
        assertFalse(state.copy(loading = true).canLoadMore(11))
        assertFalse(state.copy(error = "Retry later").canLoadMore(11))
        assertFalse(state.copy(next = null).canLoadMore(11))
        assertFalse(state.copy(items = emptyList()).canLoadMore(0))
        assertFalse(state.copy(items = items.take(1)).canLoadMore(-1))
        assertTrue(state.copy(items = items.take(1)).canLoadMore(0))
    }
}
