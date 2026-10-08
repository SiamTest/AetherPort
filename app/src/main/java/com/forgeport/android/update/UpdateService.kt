package com.forgeport.android.update

import android.content.Context
import com.forgeport.android.BuildConfig
import com.forgeport.android.data.VariableStore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateService(private val context: Context) {
    private val preferences = context.getSharedPreferences("forgeport_updates", Context.MODE_PRIVATE)
    private val variables = VariableStore(context)
    val repository: String get() = preferences.getString("repository", BuildConfig.UPDATE_GITHUB_REPOSITORY) ?: BuildConfig.UPDATE_GITHUB_REPOSITORY

    fun configureRepository(value: String) {
        val repo = UpdateSource.repository(value)
        preferences.edit().putString("repository", repo).apply()
    }

    private fun token(): String? = variables.resolveGitHubToken("https://github.com/$repository")?.second

    // Credentials are attached only to GitHub API requests, never browser/CDN URLs.
    private fun request(url: String, asset: Boolean = false): Request {
        val builder = Request.Builder().url(url)
            .header("Accept", if (asset) "application/octet-stream" else "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AetherPort-Android/${BuildConfig.VERSION_NAME}")
        if (UpdateSource.canAuthenticate(url)) token()?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun checkForUpdate(): AppUpdate? {
        val request = request("https://api.github.com/repos/$repository/releases?per_page=100")

        client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> error("Update repository $repository is unavailable. Check the repository in Updates. For a private repository, save a GITHUB_TOKEN variable with Contents read access.")
                401 -> error("The saved GitHub token is invalid or expired. Update it in Variables.")
                403, 429 -> error("GitHub denied the update check or its request limit was reached. Check token access or retry later.")
            }
            if (!response.isSuccessful) error("GitHub update check failed (${response.code}).")
            val releases = JSONArray(response.body?.string().orEmpty())
            check(releases.length() > 0) { "No releases are published in $repository yet. Wait for the Android release workflow to finish." }
            val currentIsPrerelease = BuildConfig.VERSION_NAME.contains(
                Regex("(?i)(alpha|beta|rc|dev|snapshot)"),
            )

            var best: AppUpdate? = null
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft", false)) continue
                val prerelease = release.optBoolean("prerelease", false)
                if (prerelease && !currentIsPrerelease) continue

                val tag = release.optString("tag_name")
                val version = tag.removePrefix("v").removePrefix("V")
                if (version.isBlank() || !VersionComparator.isNewer(version, BuildConfig.VERSION_NAME)) continue

                val update = parseRelease(release, version, prerelease) ?: continue
                if (best == null || VersionComparator.isNewer(update.versionName, best.versionName)) {
                    best = update
                }
            }
            return best
        }
    }

    fun downloadedFile(update: AppUpdate): File = File(updateDirectory(), safeFileName(update.apkName))

    fun hasCompleteDownload(update: AppUpdate): Boolean {
        val file = downloadedFile(update)
        if (!file.isFile) return false
        if (update.apkSizeBytes > 0L && file.length() != update.apkSizeBytes) {
            file.delete()
            return false
        }
        return runCatching {
            verifyChecksumIfAvailable(file, update)
            true
        }.getOrElse {
            file.delete()
            false
        }
    }

    fun download(update: AppUpdate, onProgress: (downloaded: Long, total: Long) -> Unit): File {
        val request = request(update.apkUrl, asset = true)
        val target = downloadedFile(update)
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Update download failed (${response.code}).")
            val body = response.body ?: error("GitHub returned an empty APK response.")
            val total = body.contentLength().takeIf { it > 0L } ?: update.apkSizeBytes
            var downloaded = 0L
            var lastReportedPercent = -1
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else -1
                        if (percent != lastReportedPercent || downloaded == total) {
                            lastReportedPercent = percent
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                }
            }
        }

        if (update.apkSizeBytes > 0L && partial.length() != update.apkSizeBytes) {
            partial.delete()
            error("Downloaded APK size does not match the GitHub release asset.")
        }
        verifyChecksumIfAvailable(partial, update)
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    private fun parseRelease(release: JSONObject, version: String, prerelease: Boolean): AppUpdate? {
        val assets = release.optJSONArray("assets") ?: return null
        var releaseApk: JSONObject? = null
        var fallbackApk: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (!name.contains("debug", ignoreCase = true) && releaseApk == null) {
                releaseApk = asset
            }
            if (fallbackApk == null) fallbackApk = asset
        }
        val apkAsset = releaseApk ?: fallbackApk ?: return null
        val apkName = apkAsset.optString("name")
        var checksum: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            if (!name.endsWith(".sha256", ignoreCase = true)) continue
            if (name.equals("$apkName.sha256", ignoreCase = true)) {
                checksum = asset
                break
            }
        }
        return AppUpdate(
            versionName = version,
            tagName = release.optString("tag_name"),
            releaseName = release.optString("name").ifBlank { "AetherPort Android $version" },
            notes = release.optString("body"),
            publishedAt = release.optString("published_at"),
            apkName = apkName,
            apkUrl = assetUrl(apkAsset),
            apkSizeBytes = apkAsset.optLong("size", 0L),
            checksumUrl = checksum?.let(::assetUrl)?.takeIf { it.isNotBlank() },
            prerelease = prerelease,
        )
    }

    private fun assetUrl(asset: JSONObject): String = asset.optString(
        if (token() != null) "url" else "browser_download_url",
    )

    private fun verifyChecksumIfAvailable(file: File, update: AppUpdate) {
        val url = update.checksumUrl ?: return
        val request = request(url, asset = true)
        val expected = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Could not verify the update checksum (${response.code}).")
            response.body?.string().orEmpty().trim().substringBefore(' ').lowercase()
        }
        if (!expected.matches(Regex("[0-9a-f]{64}"))) error("The release checksum is invalid.")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            error("Update checksum verification failed.")
        }
    }

    private fun updateDirectory(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun safeFileName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
