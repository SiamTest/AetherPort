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

data class SavedRepository(val variableName: String, val repository: String, val huggingFace: Boolean)

data class ProjectArchive(val uri: String, val name: String, val modifiedAt: Long, val size: Long)

fun newestArchives(archives: List<ProjectArchive>): List<ProjectArchive> = archives.distinctBy { it.uri }
    .sortedWith(compareByDescending<ProjectArchive> { it.modifiedAt }.thenBy { it.name.lowercase() }.thenBy { it.uri })

data class PublishProject(val id: String, val name: String, val timestamp: Long, val size: Long)

data class OperationResult(
    val ok: Boolean,
    val message: String,
    val log: String = "",
)

/** Only unsuccessful operations should produce a user-visible status banner. */
fun OperationResult.failureOrNull(): OperationResult? = takeUnless { it.ok }

data class GoogleTokenBundle(
    val tokenJson: ByteArray,
    val tokenPickle: ByteArray,
    val accountHint: String = "",
)
