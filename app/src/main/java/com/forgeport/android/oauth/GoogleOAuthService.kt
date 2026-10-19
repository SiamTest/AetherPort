package com.forgeport.android.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.forgeport.android.data.VariableStore
import com.forgeport.android.model.GoogleTokenBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

class GoogleOAuthService(private val context: Context) {
    private val cache = VariableStore(context, "aetherport_google_oauth_v1")
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    suspend fun authorizeWithDesktopCredentials(
        credentialsJsonUri: Uri, scopes: List<String>, forceNew: Boolean = false,
    ): GoogleTokenBundle = withContext(Dispatchers.IO) {
        val config = readInstalledConfig(credentialsJsonUri)
        require(scopes.isNotEmpty() && scopes.all {
            it in listOf("openid", "email", "profile") || it.startsWith("https://www.googleapis.com/auth/")
        }) { "Choose at least one valid Google OAuth permission." }
        val cacheKey = "GOOGLE_" + MessageDigest.getInstance("SHA-256")
            .digest((config.clientId + "\n" + scopes.sorted().joinToString("\n")).toByteArray())
            .joinToString("") { "%02X".format(it) }
        val saved = if (forceNew) null else cache.get(cacheKey)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (saved != null && saved.optString("client_id") == config.clientId &&
            saved.optString("client_secret") == config.clientSecret &&
            jsonScopes(saved).containsAll(scopes) && saved.optString("refresh_token").isNotBlank()) {
            val expiry = runCatching { Instant.parse(saved.optString("expiry")) }.getOrNull()
            if (saved.optString("token").isNotBlank() && expiry != null && expiry.isAfter(Instant.now().plusSeconds(225))) {
                return@withContext bundle(saved)
            }
            val refreshed = requestToken(FormBody.Builder()
                .add("client_id", config.clientId).add("client_secret", config.clientSecret)
                .add("refresh_token", saved.getString("refresh_token"))
                .add("grant_type", "refresh_token").build(), refreshing = true)
            if (refreshed != null) {
                val info = tokenInfo(config, refreshed, jsonScopes(saved), saved.getString("refresh_token"))
                require(jsonScopes(info).containsAll(scopes)) { "Saved token permissions changed. Select Sign in again." }
                val result = bundle(info)
                cache.put(cacheKey, info.toString())
                return@withContext result
            }
        }
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val redirectUri = "http://127.0.0.1:${server.localPort}/"
            val verifier = randomUrlSafe(64)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
            val state = randomUrlSafe(32)
            val authorizationUri = Uri.parse(AUTH_URI).buildUpon()
                .appendQueryParameter("client_id", config.clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", scopes.joinToString(" "))
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "select_account consent")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256").build()
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, authorizationUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            val callback = awaitGoogleOAuthCallback(server, state)
            callback["error"]?.let { error("Google authorization was not completed: $it") }
            val code = callback["code"].orEmpty()
            require(code.isNotBlank()) { "Google did not return an authorization code." }
            val token = requestToken(FormBody.Builder()
                .add("client_id", config.clientId).add("client_secret", config.clientSecret)
                .add("code", code).add("code_verifier", verifier)
                .add("grant_type", "authorization_code").add("redirect_uri", redirectUri).build())
                ?: error("Google authorization expired. Try signing in again.")
            val info = tokenInfo(config, token, scopes)
            require(jsonScopes(info).containsAll(scopes)) {
                "Some requested permissions were not approved. Sign in again and allow the selected permissions."
            }
            val result = bundle(info)
            cache.put(cacheKey, info.toString())
            result
        }
    }

    private fun readInstalledConfig(uri: Uri): InstalledConfig {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024 + 1)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
            ?: error("Could not open credentials.json.")
        require(bytes.size <= 64 * 1024) { "credentials.json is too large." }
        val installed = JSONObject(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")).optJSONObject("installed")
            ?: error("Choose credentials.json from a Google Desktop OAuth client.")
        val auth = installed.optString("auth_uri")
        require(auth in listOf(AUTH_URI, "https://accounts.google.com/o/oauth2/auth") &&
            installed.optString("token_uri") == TOKEN_URI) {
            "Use an unmodified Google Desktop credentials.json with Google OAuth endpoints."
        }
        val id = installed.optString("client_id")
        val secret = installed.optString("client_secret")
        require(id.isNotBlank() && secret.isNotBlank()) { "Google Desktop client ID and secret are required." }
        return InstalledConfig(id, secret)
    }

    private fun requestToken(body: FormBody, refreshing: Boolean = false): JSONObject? {
        http.newCall(Request.Builder().url(TOKEN_URI).post(body).build()).execute().use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (refreshing && response.code == 400 && json?.optString("error") == "invalid_grant") return null
            check(response.isSuccessful && json != null) {
                "Google token ${if (refreshing) "refresh" else "exchange"} failed. Check your connection and client setup, then try again."
            }
            return json
        }
    }

    private fun tokenInfo(config: InstalledConfig, token: JSONObject, scopes: List<String>, previousRefresh: String = ""): JSONObject {
        val refresh = token.optString("refresh_token").ifBlank { previousRefresh }
        val access = token.optString("access_token")
        require(refresh.isNotBlank() && access.isNotBlank()) {
            "Google did not issue usable refresh credentials. Select Sign in again and approve access."
        }
        val granted = token.optString("scope").split(' ').filter { it.isNotBlank() }.ifEmpty { scopes }
        return JSONObject().put("token", access).put("refresh_token", refresh).put("token_uri", TOKEN_URI)
            .put("client_id", config.clientId).put("client_secret", config.clientSecret)
            .put("scopes", JSONArray(granted)).put("universe_domain", "googleapis.com").put("account", "")
            .put("expiry", Instant.now().plusSeconds(token.optLong("expires_in", 3600L).coerceAtLeast(1L)).toString())
    }

    private fun jsonScopes(info: JSONObject): List<String> = info.optJSONArray("scopes")?.let { values ->
        (0 until values.length()).map { values.getString(it) }
    }.orEmpty()

    private fun bundle(info: JSONObject): GoogleTokenBundle = GoogleTokenBundle(
        tokenJson = info.toString(2).toByteArray(Charsets.UTF_8),
        tokenPickle = PythonCredentialsPickle.create(
            refreshToken = info.getString("refresh_token"), clientId = info.getString("client_id"),
            clientSecret = info.getString("client_secret"), scopes = jsonScopes(info),
            accessToken = info.getString("token"), expiry = Instant.parse(info.getString("expiry"))),
    )

    private fun randomUrlSafe(bytes: Int): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    private data class InstalledConfig(val clientId: String, val clientSecret: String)

    companion object {
        private const val AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        val WZML_SCOPES = listOf(
            "https://www.googleapis.com/auth/drive",
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/youtube.force-ssl",
        )
    }
}
