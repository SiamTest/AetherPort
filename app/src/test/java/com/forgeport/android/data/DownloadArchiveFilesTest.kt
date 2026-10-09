package com.forgeport.android.data

import com.forgeport.android.model.ProjectArchive
import com.forgeport.android.model.newestArchives
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadArchiveFilesTest {
    @Test fun downloadSelectionIsTopLevelZipOnlyAndNeverChangesOriginals() {
        val directory = Files.createTempDirectory("download-zips").toFile()
        try {
            val download = File(directory, "Download").apply { mkdir() }
            val old = File(download, "old.zip").apply { writeText("original old"); setLastModified(1_000) }
            val latest = File(download, "latest.ZIP").apply { writeText("original latest"); setLastModified(2_000) }
            File(download, "app.apk").writeText("apk")
            File(download, "folder.zip").mkdir()
            File(download, "nested").apply { mkdir(); File(this, "nested.zip").writeText("nested") }
            val outside = File(directory, "outside.zip").apply { writeText("outside") }
            Files.createSymbolicLink(File(download, "escape.zip").toPath(), outside.toPath())
            val archives = directDownloadZips(download).map { file -> ProjectArchive(file.path, file.name, file.lastModified(), file.length()) }
            assertEquals(listOf("latest.ZIP", "old.zip"), newestArchives(archives).map { it.name })
            assertEquals("original old", old.readText())
            assertEquals("original latest", latest.readText())
            assertThrows(IllegalArgumentException::class.java) { checkedDownloadZip(download, outside) }
            latest.delete()
            assertThrows(IllegalArgumentException::class.java) { checkedDownloadZip(download, latest) }
            assertEquals(listOf(old), directDownloadZips(download))
            assertThrows(IllegalStateException::class.java) { directDownloadZips(File(directory, "missing")) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun deleteDownloadZipActuallyRemovesTheFileAndTheListEntry() {
        val root = Files.createTempDirectory("aetherport-delete").toFile()
        try {
            val download = File(root, "Download").apply { mkdir() }
            val other = File(download, "keep.zip").apply { writeText("keep") }
            val doomed = File(download, "delete.zip").apply { writeText("delete") }
            deleteDownloadZip(download, doomed)
            assertFalse(doomed.exists())
            assertEquals(listOf(other), directDownloadZips(download))
            assertThrows(IllegalArgumentException::class.java) { deleteDownloadZip(download, doomed) }
        } finally { root.deleteRecursively() }
    }

    @Test fun deleteRejectsNonZipsDirectoriesAndSymlinksOutsideDownload() {
        val root = Files.createTempDirectory("aetherport-delete-security").toFile()
        try {
            val download = File(root, "Download").apply { mkdir() }
            val outside = File(root, "outside.zip").apply { writeText("preserve") }
            val nonZip = File(download, "keep.txt").apply { writeText("preserve") }
            val directory = File(download, "folder.zip").apply { mkdir() }
            val alias = File(download, "outside-link.zip")
            Files.createSymbolicLink(alias.toPath(), outside.toPath())
            for (invalid in listOf(outside, nonZip, directory, alias)) {
                assertThrows(IllegalArgumentException::class.java) { deleteDownloadZip(download, invalid) }
            }
            assertEquals("preserve", outside.readText())
            assertEquals("preserve", nonZip.readText())
            assertTrue(directory.isDirectory)
        } finally { root.deleteRecursively() }
    }

}
