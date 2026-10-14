package com.forgeport.android.gallery

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GalleryCoverPrefetchTest {
    private fun cover(index: Int) = "https://ehgt.org/sample-$index.jpg"

    @Test fun preloadsOnlyTwelveUniqueTrustedCoversWithAtMostThreeDownloads() = runBlocking {
        val downloaded = mutableListOf<String>()
        var active = 0
        var maximum = 0
        val urls = listOf("", "https://example.org/untrusted.jpg", cover(0)) +
            (0..29).map(::cover)
        prefetchGalleryCovers(urls) { url ->
            downloaded += url
            active++
            maximum = maxOf(maximum, active)
            delay(10)
            active--
        }
        assertEquals((0..11).map(::cover), downloaded)
        assertEquals(3, maximum)
        assertEquals(0, active)
    }

    @Test fun oneUnavailableCoverDoesNotBlockTheRemainingCovers() = runBlocking {
        val downloaded = mutableListOf<String>()
        prefetchGalleryCovers((0..5).map(::cover)) { url ->
            if (url == cover(1)) throw IOException("Interrupted")
            downloaded += url
        }
        assertEquals(listOf(0, 2, 3, 4, 5).map(::cover), downloaded)
    }

    @Test fun serviceImageLimitStopsLaterBatches() = runBlocking {
        val attempted = mutableListOf<String>()
        prefetchGalleryCovers((0..11).map(::cover)) { url ->
            attempted += url
            throw GalleryRateLimitException()
        }
        assertTrue(attempted.isNotEmpty())
        assertTrue(attempted.all { it in (0..2).map(::cover) })
    }

    @Test fun leavingTheScreenCancelsPrefetchInsteadOfContinuing() {
        var attempts = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                prefetchGalleryCovers((0..11).map(::cover)) {
                    attempts++
                    throw CancellationException("Screen closed")
                }
            }
        }
        assertTrue(attempts in 1..3)
    }

    @Test fun emptyAndUntrustedListsDoNotStartDownloads() = runBlocking {
        prefetchGalleryCovers(emptyList()) { fail("Unexpected download") }
        prefetchGalleryCovers(listOf("", "http://ehgt.org/image.jpg", "https://example.org/image.jpg")) {
            fail("Unexpected download")
        }
    }

    @Test fun interruptedThumbnailRetriesOnce() = runBlocking {
        var attempts = 0
        retryGalleryThumbnail {
            attempts++
            if (attempts == 1) throw IOException("Interrupted")
        }
        assertEquals(2, attempts)
    }

    @Test fun repeatedNetworkFailureAndServiceLimitAreReturnedToTheCoverUi() {
        var attempts = 0
        assertThrows(IOException::class.java) {
            runBlocking { retryGalleryThumbnail { attempts++; throw IOException("Offline") } }
        }
        assertEquals(2, attempts)
        attempts = 0
        assertThrows(GalleryRateLimitException::class.java) {
            runBlocking { retryGalleryThumbnail { attempts++; throw GalleryRateLimitException() } }
        }
        assertEquals(1, attempts)
    }

    @Test fun cancelledRetryDoesNotStartAnotherDownload() = runBlocking {
        var attempts = 0
        val started = CompletableDeferred<Unit>()
        val job = launch {
            retryGalleryThumbnail {
                attempts++
                started.complete(Unit)
                throw IOException("Interrupted")
            }
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(1, attempts)
    }
}
