package com.forgeport.android.update

import android.content.Context
import com.forgeport.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun checkForUpdate(): AppUpdate? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_GITHUB_REPOSITORY}/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ForgePort-Android/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub update check failed (${response.code}).")
            val releases = JSONArray(response.body?.string().orEmpty())
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
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "ForgePort-Android/${BuildConfig.VERSION_NAME}")
            .build()
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
        var fallbackChecksum: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            if (!name.endsWith(".sha256", ignoreCase = true)) continue
            if (name.equals("$apkName.sha256", ignoreCase = true)) {
                checksum = asset
                break
            }
            if (fallbackChecksum == null) fallbackChecksum = asset
        }
        checksum = checksum ?: fallbackChecksum
        return AppUpdate(
            versionName = version,
            tagName = release.optString("tag_name"),
            releaseName = release.optString("name").ifBlank { "ForgePort Android $version" },
            notes = release.optString("body"),
            publishedAt = release.optString("published_at"),
            apkName = apkName,
            apkUrl = apkAsset.optString("browser_download_url"),
            apkSizeBytes = apkAsset.optLong("size", 0L),
            checksumUrl = checksum?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
            prerelease = prerelease,
        )
    }

    private fun verifyChecksumIfAvailable(file: File, update: AppUpdate) {
        val url = update.checksumUrl ?: return
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ForgePort-Android/${BuildConfig.VERSION_NAME}")
            .build()
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
