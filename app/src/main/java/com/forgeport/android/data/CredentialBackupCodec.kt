package com.forgeport.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, password-encrypted backup format. No Android Keystore material is exported.
 * Header: 8-byte magic, 16-byte PBKDF2 salt, 12-byte GCM nonce, ciphertext + 16-byte tag.
 * The authenticated payload contains the schema version and length-prefixed UTF-8 records.
 * Both variable names and values are encrypted (including saved repository variables).
 */
internal object CredentialBackupCodec {
    internal data class Entry(val name: String, val value: String)

    private val magic = "AETHCRD1".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val MAX_BACKUP = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 512
    private const val MAX_FIELD = 128 * 1024
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128

    fun encrypt(entries: List<Entry>, password: CharArray): ByteArray {
        require(password.size >= 12) { "Use a backup password of at least 12 characters." }
        validateEntries(entries)
        val payload = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { writer ->
                writer.writeInt(VERSION)
                writer.writeInt(entries.size)
                for (entry in entries) {
                    writeField(writer, entry.name)
                    writeField(writer, entry.value)
                }
            }
            output.toByteArray()
        }
        require(payload.size <= MAX_BACKUP - 64) { "Too many credentials to export." }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(magic)
            val encrypted = cipher.doFinal(payload)
            return ByteArrayOutputStream().use { output ->
                output.write(magic)
                output.write(salt)
                output.write(nonce)
                output.write(encrypted)
                output.toByteArray()
            }
        } finally {
            payload.fill(0)
        }
    }

    fun decrypt(input: ByteArray, password: CharArray): List<Entry> {
        require(input.size in (magic.size + SALT_SIZE + NONCE_SIZE + 17)..MAX_BACKUP) {
            "Invalid credential backup size."
        }
        val salt: ByteArray
        val nonce: ByteArray
        val encrypted: ByteArray
        DataInputStream(ByteArrayInputStream(input)).use { reader ->
            val header = ByteArray(magic.size).also(reader::readFully)
            require(header.contentEquals(magic)) { "Unsupported AetherPort credential backup." }
            salt = ByteArray(SALT_SIZE).also(reader::readFully)
            nonce = ByteArray(NONCE_SIZE).also(reader::readFully)
            encrypted = ByteArray(reader.available()).also(reader::readFully)
        }
        val key = deriveKey(password, salt)
        val payload = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(magic)
            cipher.doFinal(encrypted)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Wrong password or damaged credential backup.")
        }
        try {
            return DataInputStream(ByteArrayInputStream(payload)).use { reader ->
                require(reader.readInt() == VERSION) { "Unsupported credential backup version." }
                val count = reader.readInt()
                require(count in 1..MAX_ENTRIES) { "Invalid credential backup entries." }
                val entries = List(count) {
                    Entry(readField(reader), readField(reader))
                }
                require(reader.available() == 0) { "Unexpected data in credential backup." }
                validateEntries(entries)
                entries
            }
        } finally {
            payload.fill(0)
        }
    }

    private fun validateEntries(entries: List<Entry>) {
        require(entries.size in 1..MAX_ENTRIES) { "No credentials available or backup is too large." }
        val names = HashSet<String>()
        entries.forEach { entry ->
            require(entry.name.matches(Regex("[A-Z_][A-Z0-9_]{0,127}"))) { "Invalid credential name." }
            require(entry.value.isNotEmpty() && entry.value.toByteArray(Charsets.UTF_8).size <= MAX_FIELD) {
                "Invalid credential value length."
            }
            require(names.add(entry.name)) { "Backup contains duplicate credential names." }
        }
    }

    private fun writeField(writer: DataOutputStream, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD) { "Credential field is too large." }
        writer.writeInt(encoded.size)
        writer.write(encoded)
    }

    private fun readField(reader: DataInputStream): String {
        val length = reader.readInt()
        require(length in 1..MAX_FIELD && length <= reader.available()) { "Malformed credential backup." }
        val bytes = ByteArray(length).also(reader::readFully)
        return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
