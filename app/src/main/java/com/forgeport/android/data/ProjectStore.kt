package com.forgeport.android.data

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.forgeport.android.model.ProjectArchive
import com.forgeport.android.model.newestArchives
import com.forgeport.android.model.StagedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ProjectStore(private val context: Context) {
    private val projectsRoot = File(context.filesDir, "staged_projects")
    private val settings = context.getSharedPreferences("forgeport_project_settings", Context.MODE_PRIVATE)
    val downloadFolderEnabled: Boolean get() = settings.getBoolean("download_folder", false)
    val folderUri: String? get() = if (downloadFolderEnabled) null else settings.getString("zip_folder", null)
    val folderName: String get() = if (downloadFolderEnabled) "Download" else settings.getString("zip_folder_name", "").orEmpty()
    private val downloadDirectory: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun hasDownloadAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requireDownloadAccess() {
        check(hasDownloadAccess()) { "Download access is off. Open Settings and allow storage access, or choose another ZIP folder." }
    }

    suspend fun configureDownloadFolder() = withContext(Dispatchers.IO) {
        requireDownloadAccess()
        directDownloadZips(downloadDirectory) // Validate storage before replacing the current selection.
        val old = folderUri
        settings.edit().putBoolean("download_folder", true).remove("zip_folder").remove("zip_folder_name").apply()
        old?.let(::releaseFolder)
    }

    suspend fun configureFolder(uri: Uri) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a folder using Android's folder picker." }
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "This folder is unavailable." }
        check(folder.isDirectory && folder.canRead()) { "Choose a readable ZIP folder." }
        val old = folderUri
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.edit().remove("download_folder").putString("zip_folder", uri.toString()).putString("zip_folder_name", folder.name.orEmpty()).apply()
        if (old != null && old != uri.toString()) releaseFolder(old)
    }

    suspend fun clearFolder() = withContext(Dispatchers.IO) {
        val old = folderUri
        settings.edit().remove("download_folder").remove("zip_folder").remove("zip_folder_name").apply()
        old?.let(::releaseFolder)
    }

    private fun releaseFolder(uri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    suspend fun listArchives(): List<ProjectArchive> = withContext(Dispatchers.IO) {
        if (downloadFolderEnabled) {
            requireDownloadAccess()
            val archives = directDownloadZips(downloadDirectory).map { file ->
                currentCoroutineContext().ensureActive()
                ProjectArchive(Uri.fromFile(file).toString(), file.name, file.lastModified().coerceAtLeast(0), file.length())
            }
            return@withContext newestArchives(archives)
        }
        val tree = folderUri?.let(Uri::parse) ?: return@withContext emptyList()
        check(context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }) {
            "Folder access was lost. Choose the ZIP folder again in Settings."
        }
        check(DocumentFile.fromTreeUri(context, tree)?.isDirectory == true) { "The ZIP folder was moved or deleted. Choose it again in Settings." }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE)
        val archives = mutableListOf<ProjectArchive>()
        val cursor = context.contentResolver.query(children, columns, null, null, null)
            ?: error("The ZIP folder is unavailable. Choose it again in Settings.")
        cursor.use {
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val name = it.getString(1).orEmpty()
                if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR || !name.endsWith(".zip", ignoreCase = true)) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))
                archives += ProjectArchive(uri.toString(), name, if (it.isNull(3)) 0L else it.getLong(3).coerceAtLeast(0), if (it.isNull(4)) 0L else it.getLong(4).coerceAtLeast(0))
            }
        }
        newestArchives(archives)
    }

    suspend fun stageArchive(uri: String): StagedProject {
        // Rescan either source before accepting an external URI. Original ZIPs stay untouched.
        check(listArchives().any { it.uri == uri }) { "This ZIP is no longer in the configured folder. Refresh the project list." }
        return stageZip(Uri.parse(uri))
    }

    private suspend fun stageZip(uri: Uri): StagedProject = withContext(Dispatchers.IO) {
        val sourceFile = if (uri.scheme == "file") {
            check(downloadFolderEnabled) { "Select Download in Settings first." }
            requireDownloadAccess()
            checkedDownloadZip(downloadDirectory, File(requireNotNull(uri.path)))
        } else null
        projectsRoot.mkdirs()
        val resolver = context.contentResolver
        val displayName = sourceFile?.name ?: queryDisplayName(resolver, uri) ?: "project.zip"
        require(displayName.lowercase().endsWith(".zip")) { "Only ZIP archives are supported." }

        val compressedSize = sourceFile?.length() ?: querySize(resolver, uri)
        if (compressedSize != null) {
            require(compressedSize <= MAX_UPLOAD_BYTES) { "ZIP is larger than 500 MB." }
        }

        val desired = sanitizeName(displayName.substringBeforeLast('.').ifBlank { "project" })
        val dir = uniqueDir(desired)
        dir.mkdirs()
        var fileCount = 0
        var extractedBytes = 0L
        var streamedCompressed = 0L

        try {
            (sourceFile?.inputStream() ?: resolver.openInputStream(uri))?.use { raw ->
                val counting = object : java.io.FilterInputStream(BufferedInputStream(raw)) {
                    override fun read(): Int {
                        val result = super.read()
                        if (result >= 0) streamedCompressed++
                        if (streamedCompressed > MAX_UPLOAD_BYTES) throw IllegalArgumentException("ZIP is larger than 500 MB.")
                        return result
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val count = super.read(b, off, len)
                        if (count > 0) {
                            streamedCompressed += count
                            if (streamedCompressed > MAX_UPLOAD_BYTES) throw IllegalArgumentException("ZIP is larger than 500 MB.")
                        }
                        return count
                    }
                }
                ZipInputStream(counting).use { zip ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = zip.nextEntry ?: break
                        fileCount++
                        require(fileCount <= MAX_ZIP_ENTRIES) { "ZIP contains more than 25,000 entries." }
                        val target = safeTarget(dir, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(target)).use { out ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = zip.read(buffer)
                                    if (read <= 0) break
                                    extractedBytes += read
                                    require(extractedBytes <= MAX_EXTRACTED_BYTES) { "Extracted content is larger than 1.2 GB." }
                                    out.write(buffer, 0, read)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Could not open the selected ZIP.")
            require(fileCount > 0) { "The ZIP archive is empty." }
            writeMetadata(dir, displayName)
            projectFromDir(dir)
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        }
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        resolve(name).deleteRecursively()
    }

    fun resolve(name: String): File {
        val safe = sanitizeName(name)
        val file = File(projectsRoot, safe).canonicalFile
        val root = projectsRoot.canonicalFile
        require(file.parentFile == root && file.isDirectory) { "Staged project was not found." }
        return file
    }

    fun pushSource(project: File): File = ArchiveLayout.detectPublishRoot(project)

    private fun projectFromDir(dir: File): StagedProject {
        val metaFile = File(dir, METADATA_FILE)
        val meta = if (metaFile.isFile) JSONObject(metaFile.readText()) else JSONObject()
        var count = 0
        var bytes = 0L
        dir.walkTopDown().filter { it.isFile && it.name != METADATA_FILE }.forEach {
            count++
            bytes += it.length()
        }
        return StagedProject(
            name = dir.name,
            originalArchive = meta.optString("originalArchive", dir.name + ".zip"),
            createdAtEpochMs = meta.optLong("createdAtEpochMs", dir.lastModified()),
            fileCount = count,
            totalBytes = bytes,
        )
    }

    private fun writeMetadata(dir: File, originalArchive: String) {
        File(dir, METADATA_FILE).writeText(
            JSONObject()
                .put("originalArchive", originalArchive)
                .put("createdAtEpochMs", System.currentTimeMillis())
                .toString(),
        )
    }

    private fun uniqueDir(base: String): File {
        val first = File(projectsRoot, base)
        if (!first.exists()) return first
        for (index in 2..999) {
            val candidate = File(projectsRoot, "$base-$index")
            if (!candidate.exists()) return candidate
        }
        return File(projectsRoot, "$base-${UUID.randomUUID().toString().take(8)}")
    }

    private fun safeTarget(root: File, entryName: String): File {
        val cleanName = entryName.replace('\\', '/').trimStart('/')
        require(cleanName.isNotBlank()) { "ZIP contains an invalid entry." }
        val rootCanonical = root.canonicalFile
        val target = File(rootCanonical, cleanName).canonicalFile
        require(target.path.startsWith(rootCanonical.path + File.separator)) { "Blocked an unsafe path inside the ZIP archive." }
        return target
    }

    private fun sanitizeName(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('.', '-', '_').take(80)
        return cleaned.ifBlank { "project" }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = resolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }

    companion object {
        private const val METADATA_FILE = ".forgeport-project.json"
        private val IGNORED = setOf(".git", "__MACOSX", ".DS_Store", ".hf_uploader_meta.json")
        private const val MAX_UPLOAD_BYTES = 500L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 1200L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 25_000
    }
}
