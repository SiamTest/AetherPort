package com.forgeport.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialBackupCodecTest {
    private val password = "VeryLong-BackupPassword-2026".toCharArray()
    private val entries = listOf(
        CredentialBackupCodec.Entry("GITHUB_TOKEN_SIAMTEST", "ghp_example-secret-value"),
        CredentialBackupCodec.Entry("GITHUB_REPOSITORY_AETHERPORT", "SiamTest/AetherPort"),
        CredentialBackupCodec.Entry("HF_TOKEN_OTHER", "秘密 🔒"),
    )

    @Test fun `backup round trips credentials and repository variables with Unicode`() {
        assertEquals(entries, CredentialBackupCodec.decrypt(CredentialBackupCodec.encrypt(entries, password), password))
    }

    @Test fun `names and values are encrypted, and each export uses fresh randomness`() {
        val one = CredentialBackupCodec.encrypt(entries, password)
        val two = CredentialBackupCodec.encrypt(entries, password)
        assertFalse(one.contentEquals(two))
        val raw = one.toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("GITHUB_TOKEN_SIAMTEST"))
        assertFalse(raw.contains("ghp_example-secret-value"))
        assertFalse(raw.contains("SiamTest/AetherPort"))
    }

    @Test fun `wrong password, tampered file and incomplete file all fail`() {
        val backup = CredentialBackupCodec.encrypt(entries, password)
        assertThrows(IllegalArgumentException::class.java) {
            CredentialBackupCodec.decrypt(backup, "a totally wrong password".toCharArray())
        }
        val tampered = backup.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { CredentialBackupCodec.decrypt(tampered, password) }
        assertThrows(IllegalArgumentException::class.java) { CredentialBackupCodec.decrypt(backup.copyOf(20), password) }
    }

    @Test fun `refuse weak export passwords, duplicate names and invalid variable names`() {
        assertThrows(IllegalArgumentException::class.java) { CredentialBackupCodec.encrypt(entries, "short".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { CredentialBackupCodec.encrypt(entries + entries.first(), password) }
        assertThrows(IllegalArgumentException::class.java) {
            CredentialBackupCodec.encrypt(listOf(CredentialBackupCodec.Entry("BAD NAME", "abc")), password)
        }
    }
}
