package com.forgeport.android.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.forgeport.android.model.GoogleTokenBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit

class GoogleOAuthService(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun authorizeWithDesktopCredentials(
        credentialsJsonUri: Uri,
        scopes: List<String>,
    ): GoogleTokenBundle = withContext(Dispatchers.IO) {
        val config = readInstalledConfig(credentialsJsonUri)
        require(scopes.isNotEmpty()) { "At least one Google OAuth scope is required." }

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 180_000
            val redirectUri = "http://127.0.0.1:${server.localPort}/"
            val verifier = randomUrlSafe(64)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            )
            val state = randomUrlSafe(32)
            val authorizationUri = Uri.parse(config.authUri).buildUpon()
                .appendQueryParameter("client_id", config.clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", scopes.joinToString(" "))
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "select_account consent")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build()

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW, authorizationUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            val callback = server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                }
                val path = requestLine.split(' ').getOrNull(1) ?: "/"
                val query = parseQuery(path.substringAfter('?', ""))
                val responseHtml = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="font-family:sans-serif;background:#111;color:#fff;padding:32px">
                    <h2>ForgePort authorization received</h2><p>You can return to the ForgePort app.</p></body></html>
                """.trimIndent()
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${responseHtml.toByteArray().size}\r\nConnection: close\r\n\r\n$responseHtml")
                writer.flush()
                query
            }

            callback["error"]?.let { error("Google authorization failed: $it") }
            require(callback["state"] == state) { "Google OAuth state validation failed." }
            val code = callback["code"].orEmpty()
            require(code.isNotBlank()) { "Google did not return an authorization code." }

            val token = exchangeCode(config, redirectUri, verifier, code)
            val refreshToken = token.optString("refresh_token")
            require(refreshToken.isNotBlank()) {
                "Google did not issue a refresh token. Revoke the old grant if necessary and authorize again with consent."
            }
            val accessToken = token.optString("access_token")
            val expiresIn = token.optLong("expires_in", 3600L).coerceAtLeast(60L)
            val grantedScopes = token.optString("scope")
                .split(' ')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { scopes }
            val expiry = Instant.now().plus(expiresIn, ChronoUnit.SECONDS).toString()

            val tokenJson = JSONObject()
                .put("token", accessToken)
                .put("refresh_token", refreshToken)
                .put("token_uri", config.tokenUri)
                .put("client_id", config.clientId)
                .put("client_secret", config.clientSecret)
                .put("scopes", JSONArray(grantedScopes))
                .put("universe_domain", "googleapis.com")
                .put("account", "")
                .put("expiry", expiry)
                .toString(2)
                .toByteArray(Charsets.UTF_8)

            val tokenPickle = PythonCredentialsPickle.create(
                refreshToken = refreshToken,
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                scopes = grantedScopes,
            )
            GoogleTokenBundle(tokenJson = tokenJson, tokenPickle = tokenPickle)
        }
    }

    private fun readInstalledConfig(uri: Uri): InstalledConfig {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Could not open credentials.json.")
        val root = JSONObject(text)
        val installed = root.optJSONObject("installed")
            ?: error("Use credentials.json from a Google OAuth Desktop application client.")
        return InstalledConfig(
            clientId = installed.getString("client_id"),
            clientSecret = installed.getString("client_secret"),
            authUri = installed.optString("auth_uri", "https://accounts.google.com/o/oauth2/auth"),
            tokenUri = installed.optString("token_uri", "https://oauth2.googleapis.com/token"),
        )
    }

    private fun exchangeCode(
        config: InstalledConfig,
        redirectUri: String,
        verifier: String,
        code: String,
    ): JSONObject {
        val body = FormBody.Builder()
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri)
            .build()
        val request = Request.Builder().url(config.tokenUri).post(body).build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val description = runCatching {
                    val json = JSONObject(payload)
                    json.optString("error_description").ifBlank { json.optString("error") }
                }.getOrDefault("")
                error(if (description.isBlank()) "Google token exchange failed." else description)
            }
            return JSONObject(payload)
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query
        .split('&')
        .filter { it.isNotBlank() }
        .associate { item ->
            val key = item.substringBefore('=')
            val value = item.substringAfter('=', "")
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    private fun randomUrlSafe(bytes: Int): String {
        val data = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private data class InstalledConfig(
        val clientId: String,
        val clientSecret: String,
        val authUri: String,
        val tokenUri: String,
    )
}
