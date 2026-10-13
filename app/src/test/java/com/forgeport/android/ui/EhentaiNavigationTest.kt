package com.forgeport.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EhentaiNavigationTest {
    @Test
    fun searchEncodesTextWithoutInjectingOtherParameters() {
        assertEquals(EhentaiNavigation.HOME, EhentaiNavigation.searchUrl("  "))
        assertEquals(
            "https://e-hentai.org/?f_search=artist%3Atest+%26+name%23%E6%97%A5",
            EhentaiNavigation.searchUrl("  artist:test & name#日  "),
        )
    }

    @Test
    fun onlyExactHttpsWebsiteHostsStayInWebView() {
        assertTrue(EhentaiNavigation.isInternalUrl("https://e-hentai.org/g/123/abc/"))
        assertTrue(EhentaiNavigation.isInternalUrl("https://forums.e-hentai.org/index.php"))
        assertTrue(EhentaiNavigation.isInternalUrl("https://E-HENTAI.ORG:443/"))
        listOf(
            "http://e-hentai.org/", "https://e-hentai.org.evil.example/",
            "https://e-hentai.org@evil.example/", "https://user@e-hentai.org/",
            "https://e-hentai.org:8443/", "file:///etc/passwd", "javascript:alert(1)", "not a url",
        ).forEach { assertFalse(it, EhentaiNavigation.isInternalUrl(it)) }
    }

    @Test
    fun externalNavigationAllowsOnlyWebLinks() {
        assertTrue(EhentaiNavigation.isWebUrl("https://example.com/page"))
        assertTrue(EhentaiNavigation.isWebUrl("http://example.com/page"))
        listOf("intent://example.com/", "content://example.com/", "file:///tmp/a", "javascript:alert(1)", "https://user@example.com/")
            .forEach { assertFalse(it, EhentaiNavigation.isWebUrl(it)) }
    }
}
