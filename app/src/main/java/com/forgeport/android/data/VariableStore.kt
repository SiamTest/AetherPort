package com.forgeport.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.forgeport.android.model.SecretVariable
import com.forgeport.android.model.SavedRepository
import com.forgeport.android.repo.RepoParsing
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VariableStore(context: Context) {
    private val prefs = context.getSharedPreferences("forgeport_variables_v1", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun list(): List<SecretVariable> = prefs.all.keys
        .filter { it.startsWith(PREFIX) }
        .map { key -> key.removePrefix(PREFIX) }
        .sorted()
        .map { name -> SecretVariable(name, mask(get(name).orEmpty())) }

    fun put(nameRaw: String, value: String) {
        val name = normalizeName(nameRaw)
        require(value.isNotEmpty()) { "Variable value cannot be empty." }
        val storedValue = when (RepoParsing.repositoryKind(name)) {
            "github" -> RepoParsing.normalizeGitHubRepo(value)
            "huggingface" -> RepoParsing.normalizeHuggingFaceSpace(value)
            else -> value
        }
        prefs.edit().putString(PREFIX + name, encryptStored(storedValue)).apply()
    }

    fun get(nameRaw: String): String? {
        val name = normalizeName(nameRaw)
        val packed = prefs.getString(PREFIX + name, null) ?: return null
        val parts = packed.split('.', limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun delete(nameRaw: String) {
        prefs.edit().remove(PREFIX + normalizeName(nameRaw)).apply()
    }

    /** Export all encrypted variables, including saved repository names/paths. */
    fun exportPortableBackup(password: CharArray): ByteArray {
        val names = prefs.all.keys.filter { it.startsWith(PREFIX) }.map { it.removePrefix(PREFIX) }.sorted()
        val records = names.map { name ->
            CredentialBackupCodec.Entry(name, get(name) ?: error("Could not decrypt $name for backup."))
        }
        return CredentialBackupCodec.encrypt(records, password)
    }

    data class RestoreReport(val restored: Int, val skipped: Int)

    /** Parse and validate every entry BEFORE changing preferences; commit all changes atomically. */
    fun importPortableBackup(backup: ByteArray, password: CharArray, replaceExisting: Boolean): RestoreReport {
        val records = CredentialBackupCodec.decrypt(backup, password)
        val validated = records.map { record ->
            val name = normalizeName(record.name)
            val value = when (RepoParsing.repositoryKind(name)) {
                "github" -> RepoParsing.normalizeGitHubRepo(record.value)
                "huggingface" -> RepoParsing.normalizeHuggingFaceSpace(record.value)
                else -> record.value
            }
            name to value
        }
        val available = validated.filter { (name, _) -> replaceExisting || !prefs.contains(PREFIX + name) }
        // Prepare the entire encrypted batch before starting the preference transaction.
        val encrypted = available.map { (name, value) -> name to encryptStored(value) }
        if (encrypted.isNotEmpty()) {
            val editor = prefs.edit()
            encrypted.forEach { (name, value) -> editor.putString(PREFIX + name, value) }
            check(editor.commit()) { "Could not save imported credentials." }
        }
        return RestoreReport(encrypted.size, validated.size - encrypted.size)
    }

    private fun encryptStored(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun resolveGitHubToken(repoUrl: String): Pair<String, String>? {
        val exact = RepoParsing.githubTokenVariableName(repoUrl)
        get(exact)?.let { return exact to it }
        val candidates = list().map { it.name }.filter { it.startsWith("GITHUB_TOKEN") }
        if (candidates.size == 1) {
            val name = candidates.single()
            return name to (get(name) ?: return null)
        }
        return null
    }

    fun huggingFaceTokenNames(): List<String> = list().map { it.name }.filter {
        it.startsWith("HF_TOKEN") || it.startsWith("HUGGINGFACE_TOKEN")
    }

    fun savedRepositories(): List<SavedRepository> = prefs.all.keys.filter { it.startsWith(PREFIX) }.mapNotNull { key ->
        val name = key.removePrefix(PREFIX)
        val kind = RepoParsing.repositoryKind(name) ?: return@mapNotNull null
        runCatching {
            val value = get(name).orEmpty()
            val repo = if (kind == "github") RepoParsing.normalizeGitHubRepo(value) else RepoParsing.normalizeHuggingFaceSpace(value)
            SavedRepository(name, repo, kind == "huggingface")
        }.getOrNull()
    }.sortedBy { it.variableName }

    private fun secretKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun normalizeName(value: String): String {
        val name = value.trim().uppercase()
        require(name.matches(Regex("^[A-Z_][A-Z0-9_]{0,127}$"))) {
            "Variable names may contain A-Z, 0-9, and underscores and cannot start with a number."
        }
        return name
    }

    private fun mask(value: String): String = when {
        value.isEmpty() -> ""
        value.length <= 4 -> "••••"
        value.length <= 8 -> value.take(1) + "••••" + value.takeLast(1)
        else -> value.take(3) + "••••••" + value.takeLast(3)
    }

    companion object {
        private const val PREFIX = "v:"
        private const val KEY_ALIAS = "forgeport.variables.aes.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
