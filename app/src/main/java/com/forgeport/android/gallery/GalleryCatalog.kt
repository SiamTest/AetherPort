package com.forgeport.android.gallery

import com.forgeport.android.ui.EhentaiNavigation
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal enum class GalleryCatalogMode { POPULAR, LATEST }
internal data class GalleryCategory(val label: String, val flag: Int)
internal data class GalleryFilters(
    val categories: Set<Int> = GalleryCatalog.categories.map { it.flag }.toSet(),
    val language: String = "",
    val uploader: String = "",
    val minRating: Int = 0,
    val minPages: Int? = null,
    val maxPages: Int? = null,
) {
    val active: Boolean get() = categories.size != GalleryCatalog.categories.size || language.isNotBlank() ||
        uploader.isNotBlank() || minRating > 0 || minPages != null || maxPages != null

    fun validate() {
        require(categories.isNotEmpty() && categories.all { flag -> GalleryCatalog.categories.any { it.flag == flag } }) { "Choose at least one category." }
        require(minRating == 0 || minRating in 2..5) { "Choose a rating between 2 and 5 stars." }
        require(minPages == null || minPages in 1..GalleryParser.MAX_PAGES) { "Enter a valid minimum page count." }
        require(maxPages == null || maxPages in 1..GalleryParser.MAX_PAGES) { "Enter a valid maximum page count." }
        require(minPages == null || maxPages == null || minPages <= maxPages) { "The minimum page count must not exceed the maximum." }
    }
}

internal data class GallerySummary(
    val url: String, val title: String, val cover: String, val category: String,
    val pages: Int? = null, val rating: Double? = null, val uploader: String = "", val tags: List<String> = emptyList(),
)
internal data class GalleryCatalogPage(val items: List<GallerySummary>, val next: String?)

internal object GalleryCatalog {
    val categories = listOf(
        GalleryCategory("Doujinshi", 2), GalleryCategory("Manga", 4), GalleryCategory("Artist CG", 8),
        GalleryCategory("Game CG", 16), GalleryCategory("Western", 512), GalleryCategory("Image set", 32),
        GalleryCategory("Non-H", 256), GalleryCategory("Cosplay", 64), GalleryCategory("Asian Porn", 128), GalleryCategory("Misc", 1),
    )

    fun request(mode: GalleryCatalogMode, query: String, filters: GalleryFilters, cursor: String? = null): String {
        filters.validate()
        val values = linkedMapOf("inline_set" to "dm_t")
        val path = if (mode == GalleryCatalogMode.POPULAR) "popular" else ""
        if (mode == GalleryCatalogMode.LATEST) {
            val search = buildList {
                if (query.isNotBlank()) add(query.trim())
                if (filters.language.isNotBlank()) add("language:${filters.language.lowercase()}")
                if (filters.uploader.isNotBlank()) add("uploader:\"${filters.uploader.trim().replace("\"", "")}\"")
            }.joinToString(" ")
            values["f_cats"] = (1023 - filters.categories.sum()).toString()
            values["f_search"] = search
            values["f_sname"] = "on"
            values["f_stags"] = "on"
            values["f_sdesc"] = "on"
            values["advsearch"] = "1"
            if (filters.minRating > 0) { values["f_sr"] = "on"; values["f_srdd"] = filters.minRating.toString() }
            if (filters.minPages != null || filters.maxPages != null) values["f_sp"] = "on"
            filters.minPages?.let { values["f_spf"] = it.toString() }
            filters.maxPages?.let { values["f_spt"] = it.toString() }
            cursor?.takeIf(::isListUrl)?.let {
                URI(it).rawQuery.orEmpty().split('&').forEach { parameter ->
                    val parts = parameter.split('=', limit = 2)
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    if (key in setOf("next", "prev", "page", "seek", "jump", "range") && parts.size == 2) {
                        values[key] = URLDecoder.decode(parts[1], "UTF-8")
                    }
                }
            }
        }
        return EhentaiNavigation.HOME + path + "?" + values.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
    }

    fun isListUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        EhentaiNavigation.isInternalUrl(url) && uri.host.equals("e-hentai.org", true) && uri.path in listOf("", "/", "/popular", "/popular/")
    }.getOrDefault(false)

    fun coverFromStyle(style: String, base: String): String {
        val value = Regex("url\\(\\s*['\"]?([^)'\"]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE).find(style)?.groupValues?.get(1)?.trim().orEmpty()
        return runCatching { URI(base).resolve(value).toString().takeIf(GalleryParser::isImageUrl).orEmpty() }.getOrDefault("")
    }

    fun parse(url: String, html: String): GalleryCatalogPage {
        require(isListUrl(url)) { "Invalid gallery listing link." }
        val doc = Jsoup.parse(html, url)
        val containers = doc.select(".itg .gl1t, .itg .gl1e, .itg tr, .gl1t, .gl1e")
        val seen = mutableSetOf<String>()
        val items = containers.mapNotNull { container ->
            val anchors = container.select("a[href]")
            val gallery = anchors.firstNotNullOfOrNull { EhentaiNavigation.galleryUrl(it.absUrl("href")) } ?: return@mapNotNull null
            if (!seen.add(gallery)) return@mapNotNull null
            val title = container.selectFirst(".glink")?.text().orEmpty().ifBlank {
                anchors.firstOrNull { EhentaiNavigation.galleryUrl(it.absUrl("href")) == gallery && it.text().isNotBlank() }?.text().orEmpty()
            }.ifBlank { "Gallery ${URI(gallery).path.split('/')[2]}" }
            val cover = container.select("img").firstNotNullOfOrNull { image ->
                listOf(image.absUrl("data-src"), image.absUrl("src")).firstOrNull(GalleryParser::isImageUrl)
            } ?: container.select("[style]").map { coverFromStyle(it.attr("style"), url) }.firstOrNull { it.isNotBlank() }.orEmpty()
            GallerySummary(gallery, title, cover, container.selectFirst(".cn, .cs")?.text().orEmpty())
        }
        val noResults = doc.text().contains("No hits found", true) || doc.text().contains("No galleries found", true)
        check(items.isNotEmpty() || noResults) { "The gallery list could not load. Retry, or check Account & access from the menu." }
        val next = doc.select("#unext[href], #dnext[href], a[rel=next], .ptt a[href]").firstNotNullOfOrNull { link ->
            val candidate = link.absUrl("href")
            val isNext = link.id() in setOf("unext", "dnext") || link.attr("rel") == "next" || link.text() in setOf(">", "›", "»", "Next")
            candidate.takeIf { isNext && isListUrl(it) && it != url }
        }
        return GalleryCatalogPage(items, next)
    }

    fun matches(item: GallerySummary, query: String, filters: GalleryFilters): Boolean {
        val category = categories.firstOrNull { it.label.replace(" ", "").equals(item.category.replace(" ", ""), true) }
        if (category == null || category.flag !in filters.categories) return false
        if (filters.language.isNotBlank() && item.tags.none { it.equals("language:${filters.language}", true) }) return false
        if (filters.uploader.isNotBlank() && !item.uploader.equals(filters.uploader.trim(), true)) return false
        if (filters.minRating > 0 && (item.rating ?: -1.0) < filters.minRating) return false
        if (filters.minPages != null && (item.pages ?: -1) < filters.minPages) return false
        if (filters.maxPages != null && (item.pages ?: Int.MAX_VALUE) > filters.maxPages) return false
        val searchable = (item.title + " " + item.tags.joinToString(" ")).lowercase()
        return Regex("\"[^\"]+\"|\\S+").findAll(query.trim()).all {
            val token = it.value.trim('"').lowercase()
            if (token.startsWith('-')) !searchable.contains(token.drop(1).trim('"')) else searchable.contains(token)
        }
    }
}
