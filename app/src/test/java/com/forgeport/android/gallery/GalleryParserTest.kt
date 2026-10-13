package com.forgeport.android.gallery

import com.forgeport.android.ui.EhentaiNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryParserTest {
    private val galleryUrl = "https://e-hentai.org/g/42/abcd/"
    private val html = """
        <h1 id="gn">Sample &amp; Story</h1><div id="gdc">Manga</div><div id="gdn">Example uploader</div>
        <table id="gdd">
          <tr><td class="gdt1">Length:</td><td class="gdt2">3 pages</td></tr>
          <tr><td>Language:</td><td>English</td></tr>
        </table>
        <div id="taglist"><a>sample tag</a><a>sample tag</a></div>
        <div id="gdt">
          <a href="/s/cccc/42-3">Third</a><a href="/s/aaaa/42-1">First</a>
          <a href="https://e-hentai.org/s/bbbb/42-2">Second</a>
          <a href="/s/aaaa/42-1">Duplicate</a><a href="/s/ffff/99-1">Another gallery</a>
        </div>
    """.trimIndent()

    @Test
    fun galleryLinkIsCanonicalAndCannotSmuggleOtherHosts() {
        assertEquals(galleryUrl, EhentaiNavigation.galleryUrl("${galleryUrl}?p=1#comments"))
        assertEquals(galleryUrl, EhentaiNavigation.galleryUrl("https://e-hentai.org/g/42/ABCD"))
        listOf("https://example.org/g/42/abcd/", "https://e-hentai.org.evil.example/g/42/abcd/", "https://e-hentai.org@evil.example/g/42/abcd/", "file:///g/42/abcd/", "https://e-hentai.org/g/../../path/")
            .forEach { assertEquals(null, EhentaiNavigation.galleryUrl(it)) }
    }

    @Test
    fun parsesMetadataAndPageNumbersRatherThanLinkOrder() {
        val result = GalleryParser.index(galleryUrl, html)
        assertEquals("Sample & Story", result.gallery.title)
        assertEquals("English", result.gallery.language)
        assertEquals("Manga", result.gallery.category)
        assertEquals("42_abcd", result.gallery.key)
        assertEquals(listOf("sample tag"), result.gallery.tags)
        assertEquals(3, result.total)
        assertEquals(listOf(1, 2, 3), result.links.keys.sorted())
        assertEquals("https://e-hentai.org/s/aaaa/42-1", result.links[1])
    }

    @Test
    fun firstIndexCanShowDetailsWithoutFetchingEveryThumbnailPage() {
        val partial = GalleryParser.index(galleryUrl, html.replace("3 pages", "100 pages"))
        assertEquals(100, partial.total)
        assertEquals("Sample & Story", partial.gallery.title)
        assertEquals(3, partial.links.size)
        assertFalse(partial.links.containsKey(40))
    }

    @Test
    fun indexesCanBeMergedWithoutDroppingLaterPages() {
        val first = GalleryParser.pageLinks(galleryUrl, "<div id='gdt'><a href='/s/aaaa/42-1'>One</a></div>")
        val later = GalleryParser.pageLinks(galleryUrl, "<div id='gdt'><a href='/s/bbbb/42-2'>Two</a><a href='/s/cccc/42-3'>Three</a></div>")
        val merged = first + later
        assertEquals(listOf(1, 2, 3), merged.keys.sorted())
        assertFalse(merged.keys.contains(4))
        assertEquals(null, GalleryParser.pageNumber("https://e-hentai.org/s/aaaa/99-1", "42"))
    }

    @Test
    fun imageParsingAcceptsAttributeOrderAndDecodesEntities() {
        assertEquals(
            "https://ehgt.org/images/sample.jpg?one=1&two=2",
            GalleryParser.imageUrl("https://e-hentai.org/s/aaaa/42-1", "<img src='https://ehgt.org/images/sample.jpg?one=1&amp;two=2' id='img'>"),
        )
        assertTrue(GalleryParser.isImageUrl("https://example.hath.network/page.jpg"))
        assertTrue(GalleryParser.isImageUrl("https://example.hath.network:4433/page.jpg"))
        listOf("file:///tmp/page.jpg", "http://ehgt.org/image.jpg", "https://ehgt.org.evil.example/image.jpg", "https://user@ehgt.org/image.jpg", "https://127.0.0.1/image.jpg")
            .forEach { assertFalse(it, GalleryParser.isImageUrl(it)) }
    }

    @Test(expected = GalleryRateLimitException::class)
    fun quotaImageIsAnErrorRatherThanADownloadedPage() {
        GalleryParser.imageUrl("https://e-hentai.org/s/aaaa/42-1", "<img id='img' src='https://ehgt.org/g/509.gif'>")
    }

    @Test(expected = IllegalStateException::class)
    fun signInPageCannotBecomeAnEmptySuccessfulGallery() {
        GalleryParser.index(galleryUrl, "<h1>Please sign in</h1>")
    }

    @Test
    fun detailMetadataIncludesTrustedCoverAndNamespacedTags() {
        val extra = """
            <div id='gd1'><div style="background-image:url(https://ehgt.org/cover.jpg)"></div></div>
            <div id='rating_label'>Average: 4.50</div><span id='favcount'>12 times</span>
        """
        val tags = "<table id='taglist'><tr><td>artist:</td><td><a>Sample artist</a></td></tr><tr><td>language:</td><td><a>english</a></td></tr></table>"
        val parsed = GalleryParser.index(galleryUrl, html.replace("<div id=\"taglist\"><a>sample tag</a><a>sample tag</a></div>", tags) + extra).gallery
        assertEquals("https://ehgt.org/cover.jpg", parsed.cover)
        assertEquals("Sample artist", parsed.author)
        assertEquals(listOf("english"), parsed.tagGroups["language"])
        assertEquals("Average: 4.50", parsed.info["rating"])
        assertEquals("12 times", parsed.info["favorited"])
        assertEquals("", GalleryParser.index(galleryUrl, html + extra.replace("https://ehgt.org/cover.jpg", "https://example.org/cover.jpg")).gallery.cover)
    }
}
