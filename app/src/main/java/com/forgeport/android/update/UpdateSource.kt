package com.forgeport.android.update

import java.net.URI

internal object UpdateSource {
    fun repository(value: String): String {
        val repo = value.trim().removePrefix("https://github.com/").trimEnd('/').removeSuffix(".git")
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Enter a GitHub repository as owner/repository." }
        return repo
    }

    fun canAuthenticate(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "api.github.com" && uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)
}
