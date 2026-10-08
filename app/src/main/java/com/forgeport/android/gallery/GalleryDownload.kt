package com.forgeport.android.gallery

import java.io.File

internal data class GalleryDownload(val running: Boolean = false, val queued: Boolean = false, val message: String = "", val completedPages: Int = 0, val targetPages: Int = 0)

internal fun visibleInDownloads(offlinePages: Int, state: GalleryDownload?): Boolean =
    offlinePages > 0 || (state != null && (state.running || state.queued || state.message.isNotBlank()))

/** Metadata, saved/library flags and history are intentionally retained. */
internal fun removeDownloadedFiles(directory: File) {
    directory.listFiles().orEmpty().filter { it.name.endsWith(".img") || it.name.endsWith(".part") }
        .forEach { check(it.delete()) { "Some downloaded pages could not be removed." } }
}
