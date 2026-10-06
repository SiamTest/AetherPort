package com.forgeport.android.gallery

import android.content.Context
import android.graphics.BitmapFactory
import android.os.StatFs
import android.os.SystemClock
import android.util.AtomicFile
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.forgeport.android.ui.EhentaiNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal class GalleryRepository private constructor(private val context: Context) {
    private val root = File(context.filesDir, "ehentai-galleries")
    private val cache = File(context.cacheDir, "ehentai-pages")
    private val preferences = context.getSharedPreferences("gallery-reader", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val storageLock = Mutex()
    private val indexLock = Mutex()
    // ponytail: one gallery lock limits simultaneous network image requests; per-page locks if online reading needs more throughput.
    private val coverSlots = Semaphore(3)
    private val catalogLock = Mutex()
    private var lastApiRequest = 0L
    private val imageLocks = ConcurrentHashMap<String, Mutex>()
    private var initialized = false
    @Volatile private var cookies = ""
    @Volatile private var userAgent = ""
    private val mutableGalleries = MutableStateFlow<List<Gallery>>(emptyList())
    val galleries = mutableGalleries.asStateFlow()
    private val mutableDownloads = MutableStateFlow<Map<String, GalleryDownload>>(emptyMap())
    val downloads = mutableDownloads.asStateFlow()

    suspend fun prepareSession() = withContext(Dispatchers.Main) {
        cookies = CookieManager.getInstance().getCookie(EhentaiNavigation.HOME).orEmpty()
        userAgent = WebSettings.getDefaultUserAgent(context)
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!initialized) {
                check(root.isDirectory || root.mkdirs()) { "Gallery storage is unavailable." }
                check(cache.isDirectory || cache.mkdirs()) { "Image cache is unavailable." }
                mutableGalleries.value = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
                    runCatching {
                        val json = JSONObject(AtomicFile(File(directory, "metadata.json")).openRead().bufferedReader().use { it.readText() })
                        val url = requireNotNull(EhentaiNavigation.galleryUrl(json.getString("url")))
                        val pages = json.getJSONArray("pages").strings()
                        val gallery = Gallery(
                            url, json.getString("title"), json.optString("category"), json.optString("language"),
                            json.optString("uploader"), json.optJSONArray("tags")?.strings().orEmpty(), pages,
                            json.optBoolean("saved"),
                            cover = json.optString("cover"),
                            info = json.optJSONObject("info")?.stringMap().orEmpty(),
                            tagGroups = json.optJSONObject("tagGroups")?.let { groups ->
                                groups.keys().asSequence().associateWith { groups.getJSONArray(it).strings() }
                            }.orEmpty(), author = json.optString("author"),
                        )
                        check(gallery.key == directory.name && pages.size in 1..GalleryParser.MAX_PAGES)
                        check(pages.withIndex().all { (index, link) -> GalleryParser.pageNumber(link, gallery.id) == index + 1 })
                        gallery.copy(lastRead = lastRead(gallery), downloaded = countDownloaded(gallery))
                    }.getOrNull()
                }.sortedBy { it.title.lowercase() }
                initialized = true
            }
        }
    }

    suspend fun loadGallery(url: String, refresh: Boolean = false): Gallery = withContext(Dispatchers.IO) {
        initialize()
        val canonical = requireNotNull(EhentaiNavigation.galleryUrl(url)) { "Invalid gallery link." }
        indexLock.withLock {
            if (!refresh) galleries.value.firstOrNull { it.url == canonical }?.let { return@withLock it }
            val parsed = GalleryParser.index(canonical, text(canonical))
            val links = parsed.links.toMutableMap()
            var index = 1
            while (links.size < parsed.total) {
                currentCoroutineContext().ensureActive()
                check(index <= parsed.total) { "The gallery index could not be completed." }
                val more = GalleryParser.pageLinks(canonical, text("$canonical?p=$index"))
                val before = links.size
                more.filterKeys { it in 1..parsed.total }.forEach { (number, page) -> links.putIfAbsent(number, page) }
                check(links.size > before) { "Some gallery pages are unavailable. Check Account & access from the menu and retry." }
                index++
                delay(350)
            }
            check((1..parsed.total).all(links::containsKey)) { "The gallery index contains missing pages." }
            store(parsed.gallery.copy(pages = (1..parsed.total).map { links.getValue(it) }))
        }
    }

    suspend fun browse(mode: GalleryCatalogMode, query: String, filters: GalleryFilters, cursor: String? = null): GalleryCatalogPage = withContext(Dispatchers.IO) {
        initialize()
        val url = GalleryCatalog.request(mode, query, filters, cursor)
        val page = GalleryCatalog.parse(url, text(url))
        if (mode != GalleryCatalogMode.POPULAR) return@withContext page
        if (!filters.active && query.isBlank()) return@withContext page.copy(next = null)
        // Popular is ranked by the website; its search parameters are not supported.
        // Fetch official metadata and filter that ranked list without changing its order.
        val enriched = catalogLock.withLock {
            val values = mutableListOf<GallerySummary>()
            for (batch in page.items.chunked(25)) {
                delay((lastApiRequest + 1250 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                val ids = JSONArray().apply {
                    batch.forEach { item ->
                        val parts = URI(item.url).path.split('/')
                        put(JSONArray().put(parts[2].toLong()).put(parts[3]))
                    }
                }
                val payload = JSONObject().put("method", "gdata").put("gidlist", ids).put("namespace", 1)
                val request = Request.Builder().url("https://api.e-hentai.org/api.php")
                    .header("User-Agent", userAgent.ifBlank { "Mozilla/5.0" })
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                lastApiRequest = SystemClock.elapsedRealtime()
                val metadata = client.newCall(request).await().use { response ->
                    if (response.code == 429 || response.code == 509) throw GalleryRateLimitException()
                    check(response.isSuccessful) { "Popular filters could not load metadata (${response.code}). Retry later." }
                    JSONObject(readText(response)).getJSONArray("gmetadata")
                }
                val byId = (0 until metadata.length()).map { metadata.getJSONObject(it) }.associateBy { it.optString("gid") }
                values += batch.map { item ->
                    val parts = URI(item.url).path.split('/')
                    val data = requireNotNull(byId[parts[2]]) { "Popular gallery metadata is unavailable. Retry later." }
                    check(!data.has("error") && data.optString("token") == parts[3]) { "Popular gallery metadata is unavailable. Retry later." }
                    item.copy(category = data.optString("category", item.category),
                        pages = data.optString("filecount").toIntOrNull(), rating = data.optString("rating").toDoubleOrNull(),
                        uploader = data.optString("uploader"), tags = data.optJSONArray("tags")?.strings().orEmpty())
                }
            }
            values
        }
        GalleryCatalogPage(enriched.filter { GalleryCatalog.matches(it, query, filters) }, null)
    }

    suspend fun coverFile(gallery: Gallery): File = withContext(Dispatchers.IO) {
        val offline = pagePath(root, gallery, 0)
        if (validImage(offline)) return@withContext offline
        if (GalleryParser.isImageUrl(gallery.cover)) thumbnailFile(gallery.cover) else pageFile(gallery, 0)
    }

    suspend fun thumbnailFile(url: String): File = withContext(Dispatchers.IO) {
        require(GalleryParser.isImageUrl(url)) { "The cover is unavailable." }
        initialize()
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(cache, "covers/$hash.img")
        if (validImage(target)) { target.setLastModified(System.currentTimeMillis()); return@withContext target }
        coverSlots.withPermit {
            imageLocks.getOrPut("cover_$hash") { Mutex() }.withLock {
                if (!validImage(target)) { writeImage(url, target, EhentaiNavigation.HOME); trimCache(target) }
                target
            }
        }
    }

    private suspend fun store(gallery: Gallery): Gallery = storageLock.withLock {
        val previous = galleries.value.firstOrNull { it.key == gallery.key }
        val value = gallery.copy(
            saved = previous?.saved ?: gallery.saved,
            lastRead = lastRead(gallery), downloaded = countDownloaded(gallery),
        )
        writeMetadata(value)
        mutableGalleries.update { list -> (list.filterNot { it.key == value.key } + value).sortedBy { it.title.lowercase() } }
        value
    }

    private fun writeMetadata(gallery: Gallery) {
        val directory = File(root, gallery.key)
        check(directory.isDirectory || directory.mkdirs()) { "Gallery storage is unavailable." }
        val json = JSONObject().put("url", gallery.url).put("title", gallery.title)
            .put("category", gallery.category).put("language", gallery.language).put("uploader", gallery.uploader)
            .put("tags", JSONArray(gallery.tags)).put("pages", JSONArray(gallery.pages)).put("saved", gallery.saved)
            .put("cover", gallery.cover).put("author", gallery.author).put("info", JSONObject(gallery.info))
            .put("tagGroups", JSONObject().apply { gallery.tagGroups.forEach { (group, tags) -> put(group, JSONArray(tags)) } })
        val atomic = AtomicFile(File(directory, "metadata.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    suspend fun setSaved(gallery: Gallery, saved: Boolean) = withContext(Dispatchers.IO) {
        storageLock.withLock {
            val current = galleries.value.firstOrNull { it.key == gallery.key } ?: gallery
            val value = current.copy(saved = saved)
            writeMetadata(value)
            mutableGalleries.update { list -> list.map { if (it.key == value.key) value else it } }
        }
    }

    fun markRead(gallery: Gallery, page: Int) {
        val index = page.coerceIn(gallery.pages.indices)
        preferences.edit().putInt("page_${gallery.key}", index).apply()
        mutableGalleries.update { list -> list.map { if (it.key == gallery.key) it.copy(lastRead = index) else it } }
    }

    private fun lastRead(gallery: Gallery) = preferences.getInt("page_${gallery.key}", 0).coerceIn(gallery.pages.indices)
    fun readingMode(): String = preferences.getString("mode", "RTL") ?: "RTL"
    fun setReadingMode(mode: String) { preferences.edit().putString("mode", mode).apply() }

    fun setDownload(key: String, state: GalleryDownload) { mutableDownloads.update { it + (key to state) } }
    fun downloadState(key: String) = downloads.value[key] ?: GalleryDownload()

    suspend fun download(gallery: Gallery, progress: suspend (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        setSaved(gallery, true)
        progress(galleries.value.first { it.key == gallery.key }.downloaded, gallery.pages.size)
        for (index in gallery.pages.indices) {
            currentCoroutineContext().ensureActive()
            if (validImage(pagePath(root, gallery, index))) continue
            pageFile(gallery, index, permanent = true)
            progress(galleries.value.first { it.key == gallery.key }.downloaded, gallery.pages.size)
            if (index < gallery.pages.lastIndex) delay(600)
        }
    }

    suspend fun removeDownloads(gallery: Gallery) = withContext(Dispatchers.IO) {
        imageLocks.getOrPut(gallery.key) { Mutex() }.withLock {
            check(!downloadState(gallery.key).running && !downloadState(gallery.key).queued) { "Pause the download before removing it." }
            File(root, gallery.key).listFiles().orEmpty().filter { it.name.endsWith(".img") || it.name.endsWith(".part") }
                .forEach { check(it.delete()) { "Some downloaded pages could not be removed." } }
            mutableGalleries.update { list -> list.map { if (it.key == gallery.key) it.copy(downloaded = 0) else it } }
            setDownload(gallery.key, GalleryDownload(message = "Downloads removed."))
        }
    }

    suspend fun pageFile(gallery: Gallery, index: Int, permanent: Boolean = false): File = withContext(Dispatchers.IO) {
        require(index in gallery.pages.indices)
        if (!permanent) {
            val offline = pagePath(root, gallery, index)
            val cached = pagePath(cache, gallery, index)
            if (validImage(offline)) return@withContext offline
            if (validImage(cached)) return@withContext cached
        }
        imageLocks.getOrPut(gallery.key) { Mutex() }.withLock {
            val offline = pagePath(root, gallery, index)
            val cached = pagePath(cache, gallery, index)
            if (validImage(offline)) return@withLock offline
            val added = !offline.isFile
            val target = if (permanent) offline else cached
            if (validImage(cached)) {
                if (permanent) {
                    checkSpace(target, cached.length())
                    val temporary = File(target.path + ".part")
                    try {
                        cached.copyTo(temporary, overwrite = true)
                        currentCoroutineContext().ensureActive()
                        check(temporary.renameTo(target)) { "The downloaded page could not be saved." }
                    } finally { temporary.delete() }
                    updateDownloaded(gallery, added)
                } else cached.setLastModified(System.currentTimeMillis())
                return@withLock target
            }
            val page = gallery.pages[index]
            val imageUrl = GalleryParser.imageUrl(page, text(page))
            writeImage(imageUrl, target, page)
            if (permanent) updateDownloaded(gallery, added) else trimCache(target)
            target
        }
    }

    private suspend fun writeImage(imageUrl: String, target: File, referer: String) {
        response(imageUrl, image = true, referer = referer).use { response ->
            val body = requireNotNull(response.body) { "The service returned an empty image." }
            check(body.contentType()?.type == "image") { "The service did not return an image. Check Account & access from the menu." }
            val expected = body.contentLength()
            check(expected <= MAX_IMAGE_BYTES) { "The image exceeds the 32 MB page limit." }
            checkSpace(target, if (expected > 0) expected else MAX_IMAGE_BYTES)
            val temporary = File(target.path + ".part")
            try {
                var count = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = input.read(buffer)
                            if (size == -1) break
                            count += size
                            check(count <= MAX_IMAGE_BYTES) { "The image exceeds the 32 MB page limit." }
                            output.write(buffer, 0, size)
                        }
                    }
                }
                check(expected < 0 || count == expected) { "The image download was interrupted. Retry to resume." }
                check(validImage(temporary)) { "The downloaded page is not a supported image." }
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(target)) { "The downloaded page could not be saved." }
            } finally { temporary.delete() }
        }
    }

    private fun updateDownloaded(gallery: Gallery, added: Boolean) {
        mutableGalleries.update { list -> list.map {
            if (it.key == gallery.key) it.copy(downloaded = (it.downloaded + if (added) 1 else 0).coerceAtMost(it.pages.size)) else it
        } }
    }

    private fun pagePath(parent: File, gallery: Gallery, index: Int) = File(parent, "${gallery.key}/${(index + 1).toString().padStart(5, '0')}.img")
    private fun countDownloaded(gallery: Gallery) = gallery.pages.indices.count { pagePath(root, gallery, it).isFile }

    private fun validImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000L
    }

    private fun checkSpace(target: File, needed: Long) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Page storage is unavailable." }
        check(StatFs(target.parentFile!!.path).availableBytes > needed + 8 * 1024 * 1024) { "Not enough free space to save this page." }
    }

    private fun trimCache(keep: File) {
        val files = cache.walkTopDown().filter { it.isFile && it.name.endsWith(".img") }.toList()
        var size = files.sumOf { it.length() }
        if (size > 256L * 1024 * 1024) {
            for (file in files.filterNot { it == keep }.sortedBy { it.lastModified() }) {
                val bytes = file.length()
                if (file.delete()) size -= bytes
                if (size <= 192L * 1024 * 1024) break
            }
        }
    }

    private suspend fun text(url: String): String = response(url, image = false).use { readText(it) }

    private suspend fun readText(response: Response): String {
        val body = requireNotNull(response.body) { "The service returned an empty page." }
        check(body.contentLength() <= 8L * 1024 * 1024) { "The gallery index is too large." }
        val bytes = body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                check(output.size() + count <= 8 * 1024 * 1024) { "The gallery index is too large." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        check(bytes.size <= 8 * 1024 * 1024) { "The gallery index is too large." }
        return String(bytes, body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
    }

    private suspend fun response(url: String, image: Boolean, referer: String = EhentaiNavigation.HOME): Response {
        var current = url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            check(if (image) GalleryParser.isImageUrl(current) else EhentaiNavigation.isInternalUrl(current)) { "The service returned an unsupported link." }
            val builder = Request.Builder().url(current).header("Referer", referer)
                .header("User-Agent", userAgent.ifBlank { "Mozilla/5.0" })
            if (URI(current).host.equals("e-hentai.org", true) && cookies.isNotBlank()) builder.header("Cookie", cookies)
            val response = client.newCall(builder.build()).await()
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val next = response.header("Location")?.let { URI(current).resolve(it).toString() }
                response.close()
                current = requireNotNull(next) { "The service returned an invalid redirect." }
            } else {
                if (response.code == 429 || response.code == 509) { response.close(); throw GalleryRateLimitException() }
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    error("Connection error ($code). Retry, or check Account & access from the menu.")
                }
                return response
            }
        }
        error("The service redirected too many times.")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        @Volatile private var instance: GalleryRepository? = null
        fun get(context: Context): GalleryRepository = instance ?: synchronized(this) {
            instance ?: GalleryRepository(context.applicationContext).also { instance = it }
        }
    }
}

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith { optString(it) }
