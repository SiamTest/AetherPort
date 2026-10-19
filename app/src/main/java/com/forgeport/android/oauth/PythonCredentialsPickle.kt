package com.forgeport.android.oauth

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset

/**
 * Writes a small Python pickle which reconstructs google.oauth2.credentials.Credentials.
 *
 * Includes the current access token and UTC expiry when available, matching
 * pickle.dump(google.oauth2.credentials.Credentials). Refresh-only callers are supported.
 */
object PythonCredentialsPickle {
    private const val TOKEN_URI = "https://oauth2.googleapis.com/token"

    fun create(
        refreshToken: String,
        clientId: String,
        clientSecret: String,
        scopes: List<String>,
        accessToken: String? = null,
        expiry: Instant? = null,
    ): ByteArray {
        require(refreshToken.isNotBlank()) { "Google did not issue a refresh token." }
        require(clientId.isNotBlank()) { "Google OAuth client ID is required." }
        require(clientSecret.isNotBlank()) { "Google OAuth client secret is required." }
        require((accessToken == null) == (expiry == null)) { "Access token and expiry must be provided together." }
        require(accessToken == null || accessToken.isNotBlank()) { "Google access token is empty." }

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x80.toByte(), 0x03)) // PROTO 3 (Python 3)
        out.write("cgoogle.oauth2.credentials\nCredentials\n".toByteArray(Charsets.UTF_8)) // GLOBAL
        out.write(')'.code) // EMPTY_TUPLE
        out.write(0x81) // NEWOBJ
        out.write('}'.code) // EMPTY_DICT
        out.write('('.code) // MARK

        putKey(out, "token"); if (accessToken == null) putNone(out) else putString(out, accessToken)
        putKey(out, "expiry"); if (expiry == null) putNone(out) else putDateTime(out, expiry)
        putKey(out, "_refresh_token"); putString(out, refreshToken)
        putKey(out, "_id_token"); putNone(out)
        putKey(out, "_scopes"); putStringList(out, scopes)
        putKey(out, "_default_scopes"); putNone(out)
        putKey(out, "_granted_scopes"); putStringList(out, scopes)
        putKey(out, "_token_uri"); putString(out, TOKEN_URI)
        putKey(out, "_client_id"); putString(out, clientId)
        putKey(out, "_client_secret"); putString(out, clientSecret)
        putKey(out, "_quota_project_id"); putNone(out)
        putKey(out, "_rapt_token"); putNone(out)
        putKey(out, "_enable_reauth_refresh"); putBoolean(out, false)
        putKey(out, "_trust_boundary"); putNone(out)
        putKey(out, "_universe_domain"); putString(out, "googleapis.com")
        putKey(out, "_cred_file_path"); putNone(out)
        putKey(out, "_use_non_blocking_refresh"); putBoolean(out, false)
        putKey(out, "_account"); putString(out, "")

        out.write('u'.code) // SETITEMS
        out.write('b'.code) // BUILD -> Credentials.__setstate__
        out.write('.'.code) // STOP
        return out.toByteArray()
    }

    private fun putKey(out: ByteArrayOutputStream, value: String) = putString(out, value)

    private fun putDateTime(out: ByteArrayOutputStream, value: Instant) {
        val utc = value.atOffset(ZoneOffset.UTC)
        val micros = utc.nano / 1000
        val data = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
            .putShort(utc.year.toShort()).put(utc.monthValue.toByte()).put(utc.dayOfMonth.toByte())
            .put(utc.hour.toByte()).put(utc.minute.toByte()).put(utc.second.toByte())
            .put((micros shr 16).toByte()).put((micros shr 8).toByte()).put(micros.toByte()).array()
        out.write("cdatetime\ndatetime\n".toByteArray(Charsets.US_ASCII))
        out.write('C'.code); out.write(data.size); out.write(data)
        out.write(0x85); out.write('R'.code) // TUPLE1, REDUCE
    }

    private fun putString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write('X'.code) // BINUNICODE
        val size = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size).array()
        out.write(size)
        out.write(bytes)
    }

    private fun putStringList(out: ByteArrayOutputStream, values: List<String>) {
        out.write(']'.code) // EMPTY_LIST
        if (values.isNotEmpty()) {
            out.write('('.code) // MARK
            values.forEach { putString(out, it) }
            out.write('e'.code) // APPENDS
        }
    }

    private fun putNone(out: ByteArrayOutputStream) {
        out.write('N'.code)
    }

    private fun putBoolean(out: ByteArrayOutputStream, value: Boolean) {
        out.write(if (value) 0x88 else 0x89) // NEWTRUE / NEWFALSE
    }
}
