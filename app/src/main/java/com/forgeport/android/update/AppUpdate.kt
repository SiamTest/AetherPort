package com.forgeport.android.update

data class AppUpdate(
    val versionName: String,
    val tagName: String,
    val releaseName: String,
    val notes: String,
    val publishedAt: String,
    val apkName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val checksumUrl: String?,
    val prerelease: Boolean,
)
