package com.forgeport.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArchiveLayoutTest {
    private fun withProject(block: (File) -> Unit) {
        val temp = Files.createTempDirectory("archive-layout-test").toFile()
        try { block(temp) } finally { temp.deleteRecursively() }
    }

    @Test fun unwrapsSingleOuterProjectFolder() = withProject { root ->
        File(root, "Example-main/src").mkdirs()
        File(root, "Example-main/src/main.kt").writeText("class Main")
        assertEquals(File(root, "Example-main"), ArchiveLayout.detectPublishRoot(root))
    }

    @Test fun ignoresZipMetadataWhenDetectingWrapper() = withProject { root ->
        File(root, "__MACOSX/some").mkdirs()
        File(root, "__MACOSX/some/._README").writeText("junk")
        File(root, ".DS_Store").writeText("junk")
        File(root, ".forgeport-project.json").writeText("metadata")
        File(root, "repository/README.md").apply { parentFile.mkdirs(); writeText("real") }
        assertEquals(File(root, "repository"), ArchiveLayout.detectPublishRoot(root))
    }

    @Test fun flatZipKeepsFilesAtRoot() = withProject { root ->
        File(root, "README.md").writeText("readme")
        File(root, "src").mkdir()
        File(root, "src/main.py").writeText("print('hello')")
        assertEquals(root, ArchiveLayout.detectPublishRoot(root))
    }

    @Test fun twoTopLevelFoldersAreNotUnwrapped() = withProject { root ->
        File(root, "alpha/file.txt").apply { parentFile.mkdirs(); writeText("x") }
        File(root, "beta/file.txt").apply { parentFile.mkdirs(); writeText("y") }
        assertEquals(root, ArchiveLayout.detectPublishRoot(root))
    }

    @Test fun onlyOneWrapperLevelIsRemoved() = withProject { root ->
        File(root, "release/package/main.py").apply { parentFile.mkdirs(); writeText("print(1)") }
        assertEquals(File(root, "release"), ArchiveLayout.detectPublishRoot(root))
    }

    @Test fun conventionalProjectRootFolderIsPreserved() = withProject { root ->
        for (directory in listOf(".github", "src", "app", "assets", "lib")) {
            File(root, "$directory/example.txt").apply { parentFile.mkdirs(); writeText("real") }
            assertEquals(root, ArchiveLayout.detectPublishRoot(root))
            File(root, directory).deleteRecursively()
        }
    }

    @Test fun rejectsEmptyZipOrOnlyMetadata() = withProject { root ->
        assertThrows(IllegalArgumentException::class.java) { ArchiveLayout.detectPublishRoot(root) }
        File(root, "__MACOSX").mkdir()
        File(root, ".DS_Store").writeText("junk")
        assertThrows(IllegalArgumentException::class.java) { ArchiveLayout.detectPublishRoot(root) }
        File(root, "wrapper/inner").mkdirs()
        assertThrows(IllegalArgumentException::class.java) { ArchiveLayout.detectPublishRoot(root) }
    }
}
