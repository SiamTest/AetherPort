package com.forgeport.android.repo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class HuggingFaceMetadataTest {
    @Test fun normalizesOnlyTheStagedTopLevelEmojiAndPreservesBytesElsewhere() {
        val root = Files.createTempDirectory("hf-card-test").toFile()
        try {
            val readme = root.resolve("README.md")
            for (value in listOf("✦", "'✧'", "\"✦\"")) {
                val text = "\uFEFF---\r\ntitle: Keep ✦\r\nemoji: $value # icon\r\nsdk: docker\r\n---\r\nBody ✧\r\nemoji: ✦\r\n"
                readme.writeText(text)
                normalizeHuggingFaceReadme(root)
                val expected = text.replace("emoji: $value # icon", "emoji: ${value.replace('✦', '✨').replace('✧', '✨')} # icon")
                assertEquals(expected, readme.readText())
                normalizeHuggingFaceReadme(root)
                assertEquals(expected, readme.readText())
            }
            val unchanged = listOf("---\nemoji: 🚀\n---\n", "---\nemoji: '✦ demo'\n---\n",
                "# Plain README\nemoji: ✦\n", "---\nemoji: ✦\n", "---\nemoji: ✨\n---\n")
            for (text in unchanged) {
                readme.writeText(text)
                normalizeHuggingFaceReadme(root)
                assertEquals(text, readme.readText())
            }
            val invalidUtf8 = "---\nemoji: ✦\n---\n".toByteArray() + byteArrayOf(0xFF.toByte())
            readme.writeBytes(invalidUtf8)
            normalizeHuggingFaceReadme(root)
            assertArrayEquals(invalidUtf8, readme.readBytes())
        } finally { root.deleteRecursively() }
    }
}
