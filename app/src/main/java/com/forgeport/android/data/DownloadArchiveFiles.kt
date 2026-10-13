package com.forgeport.android.data

import java.io.File

/** Only readable ZIP files directly inside Download; never follow links outside it. */
internal fun directDownloadZips(directory: File): List<File> {
    val root = directory.canonicalFile
    check(root.isDirectory) { "The Download folder is unavailable. Check your device storage." }
    val children = root.listFiles() ?: error("Could not read Download. Allow storage access in Settings.")
    return children.mapNotNull { file ->
        runCatching { checkedDownloadZip(root, file) }.getOrNull()
    }.distinctBy { it.path }
}

internal fun checkedDownloadZip(directory: File, file: File): File {
    val root = directory.canonicalFile
    val source = file.canonicalFile
    require(source.parentFile == root && source.isFile && source.canRead() && source.name.endsWith(".zip", ignoreCase = true)) {
        "This ZIP is no longer available directly in Download. Refresh the project list."
    }
    return source
}


/** Delete an actual top-level Download ZIP; never follow a symlink or remove a directory. */
internal fun deleteDownloadZip(directory: File, selected: File) {
    val archive = checkedDownloadZip(directory, selected)
    check(archive.delete() && !archive.exists()) {
        "Could not delete ${archive.name} from Download. Check storage permissions."
    }
}
