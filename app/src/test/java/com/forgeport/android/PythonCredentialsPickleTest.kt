package com.forgeport.android

import com.forgeport.android.oauth.PythonCredentialsPickle
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonCredentialsPickleTest {
    @Test
    fun pickleContainsCredentialsClassAndRefreshMaterial() {
        val data = PythonCredentialsPickle.create(
            refreshToken = "refresh-123",
            clientId = "client.apps.googleusercontent.com",
            clientSecret = "secret-456",
            scopes = listOf("https://www.googleapis.com/auth/drive"),
        )
        val text = data.toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("google.oauth2.credentials"))
        assertTrue(text.contains("Credentials"))
        assertTrue(text.contains("refresh-123"))
        assertTrue(text.contains("oauth2.googleapis.com/token"))
    }
}
