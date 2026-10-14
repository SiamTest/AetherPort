package com.forgeport.android.gallery

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

internal suspend fun retryGalleryThumbnail(download: suspend () -> Unit) {
    try {
        download()
    } catch (failure: IOException) {
        delay(500)
        download()
    }
}

internal suspend fun prefetchGalleryCovers(urls: List<String>, download: suspend (String) -> Unit) {
    try {
        for (batch in urls.filter(GalleryParser::isImageUrl).distinct().take(12).chunked(3)) {
            coroutineScope {
                batch.map { url -> async {
                    try {
                        download(url)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (limited: GalleryRateLimitException) {
                        throw limited
                    } catch (_: Exception) {
                        // Visible covers retain their retry button; one broken cover must not block others.
                    }
                } }.awaitAll()
            }
        }
    } catch (_: GalleryRateLimitException) {
        // Stop speculative requests when the service asks us to pause.
    }
}
