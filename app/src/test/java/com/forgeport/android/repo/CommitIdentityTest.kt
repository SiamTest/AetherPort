package com.forgeport.android.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommitIdentityTest {
    @Test fun creditsTheTokenOwnerAndValidatesAccountData() {
        assertEquals(CommitIdentity("Siam", "42+SiamTest@users.noreply.github.com"), CommitIdentity.github("SiamTest", 42, "Siam"))
        assertEquals(CommitIdentity("SiamTest", "42+SiamTest@users.noreply.github.com"), CommitIdentity.github("SiamTest", 42, ""))
        assertEquals("Siam", CommitIdentity.github("SiamTest", 42, "<Siam>\n").name)
        assertEquals(CommitIdentity("HF user", "user@example.com"), CommitIdentity.huggingFace("other-owner", "HF user", "user@example.com", true))
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.github("bad\nlogin", 42, "") }
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.github("user", 0, "") }
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.huggingFace("user", "", "user@example.com", false) }
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.huggingFace("user", "", "user@example.com\n", true) }
    }

    @Test fun omittedVerificationKeepsAccountEmailWithoutClaimingVerification() {
        assertEquals(CommitIdentity("HF user", "user@example.com"),
            CommitIdentity.huggingFace("user", "HF user", "user@example.com", null))
        assertEquals("user@users.noreply.huggingface.co", CommitIdentity.huggingFace("user", "", "", null).email)
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.huggingFace("user", "", "bad\n@example.com", null) }
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.huggingFace("user", "", "user@example.com", false) }
    }

    @Test fun hiddenEmailUsesTheAuthenticatedUsersHubNoreplyIdentity() {
        assertEquals(CommitIdentity("Token Owner", "ActualOwner@users.noreply.huggingface.co"),
            CommitIdentity.huggingFace("ActualOwner", "Token Owner", "", null))
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.huggingFace("bad\nuser", "", "", null) }
        assertThrows(IllegalArgumentException::class.java) { CommitIdentity.huggingFace("user", "", "", false) }
    }
}
