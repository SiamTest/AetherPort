package com.forgeport.android.gallery

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class GalleryDownloadStateTest {
    @Test fun removedDownloadsDisappearWithoutRemovingLibraryMetadata() {
        val directory = Files.createTempDirectory("gallery-delete").toFile()
        try {
            directory.resolve("metadata.json").writeText("saved gallery metadata")
            directory.resolve("00001.img").writeText("offline image")
            directory.resolve("00002.img.part").writeText("partial image")
            removeDownloadedFiles(directory)
            assertEquals(listOf("metadata.json"), directory.listFiles()!!.map { it.name })
            assertFalse(visibleInDownloads(0, null))
            assertFalse(visibleInDownloads(0, GalleryDownload()))
            removeDownloadedFiles(directory) // Repeat removal is safe.
        } finally { directory.deleteRecursively() }
    }

    @Test fun activeAndRetryableDownloadsRemainVisibleEvenBeforeFirstPage() {
        assertTrue(visibleInDownloads(1, null))
        assertTrue(visibleInDownloads(0, GalleryDownload(queued = true)))
        assertTrue(visibleInDownloads(0, GalleryDownload(running = true)))
        assertTrue(visibleInDownloads(0, GalleryDownload(message = "Paused. Tap Resume to continue.")))
        assertTrue(visibleInDownloads(0, GalleryDownload(message = "Connection failed")))
    }

    @Test fun deletionFailureIsReportedRatherThanSilentlyCleared() {
        val directory = Files.createTempDirectory("gallery-delete-failure").toFile()
        try {
            directory.resolve("cannot-delete.img").mkdir()
            directory.resolve("cannot-delete.img/child").writeText("preserved")
            assertThrows(IllegalStateException::class.java) { removeDownloadedFiles(directory) }
            assertTrue(directory.resolve("cannot-delete.img/child").exists())
        } finally { directory.deleteRecursively() }
    }
}
