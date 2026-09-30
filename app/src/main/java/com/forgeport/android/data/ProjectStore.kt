package com.forgeport.android.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.forgeport.android.model.StagedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ProjectStore(private val context: Context) {
    private val projectsRoot = File(context.filesDir, "staged_projects")

    suspend fun stageZip(uri: Uri): StagedProject = withContext(Dispatchers.IO) {
        projectsRoot.mkdirs()
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "project.zip"
        require(displayName.lowercase().endsWith(".zip")) { "Only ZIP archives are supported." }

        val compressedSize = querySize(resolver, uri)
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
            resolver.openInputStream(uri)?.use { raw ->
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

    suspend fun listProjects(): List<StagedProject> = withContext(Dispatchers.IO) {
        projectsRoot.mkdirs()
        projectsRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { runCatching { projectFromDir(it) }.getOrNull() }
            .sortedByDescending { it.createdAtEpochMs }
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

    fun pushSource(project: File, unwrapSingleFolder: Boolean): File {
        val entries = project.listFiles().orEmpty().filter { it.name != METADATA_FILE && it.name !in IGNORED }
        require(entries.isNotEmpty()) { "The staged project is empty." }
        if (!unwrapSingleFolder) return project
        if (entries.size == 1 && entries[0].isDirectory) return entries[0]
        return project
    }

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
