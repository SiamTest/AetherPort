package com.forgeport.android.gallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.forgeport.android.MainActivity
import com.forgeport.android.R
import com.forgeport.android.ui.EhentaiNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = linkedMapOf<String, String>()
    private lateinit var repository: GalleryRepository
    private var activeKey: String? = null
    private var activeJob: Job? = null
    private var foregroundReady = false

    override fun onCreate() {
        super.onCreate()
        repository = GalleryRepository.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Gallery downloads", NotificationManager.IMPORTANCE_LOW),
        )
        foregroundReady = runCatching { startForeground(NOTIFICATION, notification(0, 0)); true }.getOrDefault(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(URL)?.let(EhentaiNavigation::galleryUrl)
        val key = url?.let { java.net.URI(it).path.trim('/').removePrefix("g/").replace('/', '_') }
        if (!foregroundReady) {
            key?.let { repository.setDownload(it, GalleryDownload(message = "Android could not start the download. Keep AetherPort open and retry later.")) }
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            PAUSE_ALL -> {
                pending.keys.forEach { repository.setDownload(it, GalleryDownload(message = "Paused. Tap Resume to continue.")) }
                pending.clear()
                activeJob?.cancel()
                if (activeJob == null) stopSelf()
            }
            PAUSE -> {
                if (key != null) {
                    pending.remove(key)
                    repository.setDownload(key, GalleryDownload(message = "Paused. Tap Resume to continue."))
                    if (activeKey == key) activeJob?.cancel()
                }
                if (activeJob == null && pending.isEmpty()) stopSelf()
            }
            else -> {
                if (url != null && key != null && key != activeKey && !pending.containsKey(key)) {
                    pending[key] = url
                    repository.setDownload(key, repository.downloadState(key).copy(queued = true, message = "Queued"))
                }
                if (activeJob == null) startNext()
            }
        }
        // Interrupted downloads keep completed pages and resume when the user asks.
        return START_NOT_STICKY
    }

    private fun startNext() {
        val entry = pending.entries.firstOrNull()
        if (entry == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return }
        val key = entry.key
        val url = entry.value
        pending.remove(key)
        activeKey = key
        activeJob = scope.launch {
            try {
                repository.setDownload(key, GalleryDownload(running = true, message = "Downloading"))
                repository.prepareSession()
                val gallery = repository.loadGallery(url)
                val percent = repository.downloadPercent(gallery)
                repository.download(gallery, percent) { done, total ->
                    repository.setDownload(key, GalleryDownload(running = true, message = "Downloading • $done / $total selected pages", completedPages = done, targetPages = total))
                    withContext(Dispatchers.Main) { startForeground(NOTIFICATION, notification(done, total)) }
                }
                val target = galleryDownloadPageCount(gallery.pages.size, percent)
                repository.setDownload(key, GalleryDownload(message = if (target == gallery.pages.size) "Ready offline" else "First $target pages ready offline ($percent%)", completedPages = target, targetPages = target))
            } catch (_: CancellationException) {
                repository.setDownload(key, GalleryDownload(message = "Paused. Tap Resume to continue."))
            } catch (error: Exception) {
                repository.setDownload(key, GalleryDownload(message = error.message ?: "Download failed. Tap Resume to retry."))
                if (error is GalleryRateLimitException) {
                    pending.keys.forEach { repository.setDownload(it, GalleryDownload(message = error.message.orEmpty())) }
                    pending.clear()
                }
            } finally {
                activeKey = null
                activeJob = null
                if (scope.isActive) startNext()
            }
        }
    }

    private fun notification(done: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).putExtra(OPEN_LIBRARY, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pause = PendingIntent.getService(
            this, 2, Intent(this, GalleryDownloadService::class.java).setAction(PAUSE_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_aetherport_monochrome)
            .setContentTitle("AetherPort gallery download")
            .setContentText(if (total > 0) "$done / $total pages • ${pending.size} queued" else "Preparing download")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(total, done, total == 0)
            .addAction(Notification.Action.Builder(null, "Pause all", pause).build()).build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        activeKey?.let { repository.setDownload(it, GalleryDownload(message = "Paused. Tap Resume to continue.")) }
        pending.keys.forEach { repository.setDownload(it, GalleryDownload(message = "Paused. Tap Resume to continue.")) }
        pending.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "gallery-downloads"
        private const val NOTIFICATION = 402
        private const val URL = "gallery_url"
        private const val PAUSE = "pause_gallery"
        private const val PAUSE_ALL = "pause_all_galleries"
        const val OPEN_LIBRARY = "open_gallery_library"

        fun start(context: Context, url: String) {
            context.startForegroundService(Intent(context, GalleryDownloadService::class.java).putExtra(URL, url))
        }

        fun pause(context: Context, url: String) {
            context.startService(Intent(context, GalleryDownloadService::class.java).setAction(PAUSE).putExtra(URL, url))
        }
    }
}
