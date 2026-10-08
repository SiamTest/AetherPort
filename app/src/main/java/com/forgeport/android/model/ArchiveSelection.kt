package com.forgeport.android.model

/** Input is already sorted newest first by the archive source. */
fun selectedArchiveId(archives: List<PublishProject>, manualId: String): String =
    archives.firstOrNull { it.id == manualId }?.id ?: archives.firstOrNull()?.id.orEmpty()
