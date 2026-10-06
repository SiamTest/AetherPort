package com.forgeport.android.ui

import java.net.URI
import java.net.URLEncoder

internal object EhentaiNavigation {
    const val HOME = "https://e-hentai.org/"

    fun galleryUrl(url: String): String? = runCatching {
        if (!isInternalUrl(url)) return null
        val uri = URI(url)
        if (!uri.host.equals("e-hentai.org", ignoreCase = true)) return null
        val path = Regex("/g/([0-9]+)/([a-zA-Z0-9]+)/?").matchEntire(uri.path) ?: return null
        HOME + "g/${path.groupValues[1]}/${path.groupValues[2].lowercase()}/"
    }.getOrNull()

    fun searchUrl(query: String): String = query.trim().let {
        if (it.isEmpty()) HOME else HOME + "?f_search=" + URLEncoder.encode(it, "UTF-8")
    }

    // Keep only the website and its account/forum pages inside the app.
    fun isInternalUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in setOf("e-hentai.org", "forums.e-hentai.org") &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun isWebUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true))
    }.getOrDefault(false)
}
