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

    @Test fun savedRepositoryVariablesStaySeparateFromTokens() {
        assertEquals("GITHUB_REPOSITORY_MY_PROJECT", RepoParsing.repositoryVariableName("My project", false))
        assertEquals("HF_REPOSITORY_MY_PROJECT", RepoParsing.repositoryVariableName("My project", true))
        assertEquals("github", RepoParsing.repositoryKind("GITHUB_REPOSITORY_MY_PROJECT"))
        assertEquals("github", RepoParsing.repositoryKind("GITHUB_REPOSITORY"))
        assertEquals("huggingface", RepoParsing.repositoryKind("HF_REPOSITORY_SPACE"))
        assertEquals("huggingface", RepoParsing.repositoryKind("HUGGINGFACE_REPOSITORY_SPACE"))
        assertEquals(null, RepoParsing.repositoryKind("GITHUB_TOKEN_SIAMTEST"))
        assertEquals(null, RepoParsing.repositoryKind("HF_TOKEN_SIAM"))
        assertThrows(IllegalArgumentException::class.java) { RepoParsing.repositoryVariableName("!@#", false) }
        assertEquals(true, RepoParsing.repositoryVariableName("x".repeat(500), false).length <= 128)
    }
}
