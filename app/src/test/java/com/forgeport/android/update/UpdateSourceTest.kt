package com.forgeport.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSourceTest {
    @Test fun acceptsRepositorySlugAndGitHubLink() {
        assertEquals("example/AetherPort", UpdateSource.repository(" example/AetherPort "))
        assertEquals("example/AetherPort", UpdateSource.repository("https://github.com/example/AetherPort.git/"))
    }

    @Test fun credentialsStayOnTheHttpsGitHubApi() {
        assertTrue(UpdateSource.canAuthenticate("https://api.github.com/repos/example/AetherPort/releases"))
        listOf("http://api.github.com/repos/a/b", "https://api.github.com:8443/x", "https://api.github.com.evil.example/x", "https://api.github.com@evil.example/x", "https://github.com/a/b", "https://objects.githubusercontent.com/x", "file:///x", "bad url")
            .forEach { assertFalse(it, UpdateSource.canAuthenticate(it)) }
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNonGitHubSources() {
        UpdateSource.repository("https://example.org/owner/repo")
    }
}
