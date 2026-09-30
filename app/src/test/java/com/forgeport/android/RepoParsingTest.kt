package com.forgeport.android

import com.forgeport.android.repo.RepoParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepoParsingTest {
    @Test
    fun githubNormalizationAndTokenNameMatchLegacyRules() {
        assertEquals("chowdhury-siam/demo", RepoParsing.normalizeGitHubRepo("https://github.com/chowdhury-siam/demo.git"))
        assertEquals("GITHUB_TOKEN_CHOWDHURY_SIAM", RepoParsing.githubTokenVariableName("chowdhury-siam/demo"))
    }

    @Test
    fun rejectsUnsafeTargetPath() {
        assertThrows(IllegalArgumentException::class.java) {
            RepoParsing.sanitizeTargetPath("../escape")
        }
    }

    @Test
    fun normalizesHuggingFaceSpace() {
        assertEquals("user/demo", RepoParsing.normalizeHuggingFaceSpace("https://huggingface.co/spaces/user/demo"))
    }
}
