package com.forgeport.android.model

data class StagedProject(
    val name: String,
    val originalArchive: String,
    val createdAtEpochMs: Long,
    val fileCount: Int,
    val totalBytes: Long,
)

data class SecretVariable(
    val name: String,
    val maskedValue: String,
)

data class OperationResult(
    val ok: Boolean,
    val message: String,
    val log: String = "",
)

data class GoogleTokenBundle(
    val tokenJson: ByteArray,
    val tokenPickle: ByteArray,
    val accountHint: String = "",
)
