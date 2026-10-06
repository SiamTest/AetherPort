package com.forgeport.android.gallery

import com.forgeport.android.ui.EhentaiNavigation
import org.jsoup.Jsoup
import java.net.URI

internal data class Gallery(
    val url: String,
    val title: String,
    val category: String,
    val language: String,
    val uploader: String,
    val tags: List<String>,
    val pages: List<String>,
    val saved: Boolean = false,
    val lastRead: Int = 0,
    val downloaded: Int = 0,
    val cover: String = "",
    val info: Map<String, String> = emptyMap(),
    val tagGroups: Map<String, List<String>> = emptyMap(),
    val author: String = "",
) {
    val id: String get() = URI(url).path.split('/')[2]
    val key: String get() = URI(url).path.trim('/').removePrefix("g/").replace('/', '_')
}

internal data class GalleryIndex(val gallery: Gallery, val total: Int, val links: Map<Int, String>)
internal data class GalleryDownload(val running: Boolean = false, val queued: Boolean = false, val message: String = "")
internal class GalleryRateLimitException : IllegalStateException("The image limit was reached. Pause and try again later.")

internal object GalleryParser {
    const val MAX_PAGES = 10000

    fun pageNumber(url: String, galleryId: String): Int? = runCatching {
        val uri = URI(url)
        if (!EhentaiNavigation.isInternalUrl(url) || !uri.host.equals("e-hentai.org", true)) return null
        val match = Regex("/s/[a-zA-Z0-9]+/([0-9]+)-([0-9]+)").matchEntire(uri.path) ?: return null
        if (match.groupValues[1] != galleryId) return null
        match.groupValues[2].toIntOrNull()?.takeIf { it in 1..MAX_PAGES }
    }.getOrNull()

    fun pageLinks(url: String, html: String): Map<Int, String> {
        val id = URI(url).path.split('/')[2]
        return buildMap {
            Jsoup.parse(html, url).select("#gdt a[href]").forEach { element ->
                val link = element.absUrl("href")
                pageNumber(link, id)?.let { if (!containsKey(it)) put(it, link) }
            }
        }
    }

    fun index(url: String, html: String): GalleryIndex {
        val canonical = requireNotNull(EhentaiNavigation.galleryUrl(url)) { "Invalid gallery link." }
        val doc = Jsoup.parse(html, canonical)
        val title = doc.selectFirst("#gn")?.text().orEmpty().ifBlank { doc.selectFirst("#gj")?.text().orEmpty() }
        check(title.isNotBlank()) { "Gallery unavailable. Check Account & access from the menu, then retry." }
        val info = doc.select("#gdd tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size >= 2) cells[0].text().trimEnd(':').lowercase() to cells[1].text() else null
        }.toMap()
        val total = Regex("[0-9,]+").find(info["length"].orEmpty())?.value?.replace(",", "")?.toIntOrNull()
        check(total != null && total in 1..MAX_PAGES) { "The gallery page count is unavailable or exceeds $MAX_PAGES pages." }
        val links = pageLinks(canonical, html)
        check(links.isNotEmpty()) { "Gallery pages are unavailable. Check Account & access from the menu." }
        check(links.keys.all { it <= total }) { "The service returned an inconsistent page index." }
        val groups = doc.select("#taglist tr").mapNotNull { row ->
            val name = row.selectFirst("td")?.text().orEmpty().trimEnd(':')
            val tags = row.select("a").map { it.text() }.distinct()
            if (name.isBlank() || tags.isEmpty()) null else name to tags
        }.toMap()
        val cover = GalleryCatalog.coverFromStyle(doc.selectFirst("#gd1 div[style]")?.attr("style").orEmpty(), canonical)
            .ifBlank { doc.selectFirst("#gd1 img")?.absUrl("src")?.takeIf(::isImageUrl).orEmpty() }
        val details = info + mapOf(
            "rating" to doc.selectFirst("#rating_label")?.text().orEmpty(),
            "favorited" to doc.selectFirst("#favcount")?.text().orEmpty(),
        ).filterValues { it.isNotBlank() }
        return GalleryIndex(
            Gallery(
                canonical, title, doc.selectFirst("#gdc")?.text().orEmpty(),
                info["language"].orEmpty(), doc.selectFirst("#gdn")?.text().orEmpty(),
                doc.select("#taglist a").map { it.text() }.distinct(), emptyList(),
                cover = cover, info = details, tagGroups = groups,
                author = (groups["artist"] ?: groups["cosplayer"]).orEmpty().joinToString(", "),
            ), total, links,
        )
    }

    fun imageUrl(pageUrl: String, html: String): String {
        val image = Jsoup.parse(html, pageUrl).selectFirst("img#img")?.absUrl("src").orEmpty()
        if (Regex("/509\\.(gif|jpg|png)", RegexOption.IGNORE_CASE).containsMatchIn(image)) throw GalleryRateLimitException()
        check(isImageUrl(image)) { "The image is unavailable. Check Account & access or try again later." }
        return image
    }

    fun isImageUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        val validPort = if (host.endsWith(".hath.network")) uri.port == -1 || uri.port in 1..65535 else uri.port == -1 || uri.port == 443
        uri.scheme.equals("https", true) && uri.rawUserInfo == null && validPort &&
            (host == "e-hentai.org" || host == "ehgt.org" || host.endsWith(".ehgt.org") || host.endsWith(".hath.network"))
    }.getOrDefault(false)
}
