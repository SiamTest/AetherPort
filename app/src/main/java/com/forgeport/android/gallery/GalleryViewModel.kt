package com.forgeport.android.gallery

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = GalleryRepository.get(application)
    var loadingUrl by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var loadingJob: Job? = null
    private var galleryRequest = 0
    var catalog by mutableStateOf(GalleryCatalogState())
        private set
    private var catalogJob: Job? = null
    private var catalogRequest = 0

    fun browse(mode: GalleryCatalogMode = catalog.mode, query: String = catalog.query,
               filters: GalleryFilters = catalog.filters, more: Boolean = false, refresh: Boolean = false) {
        if (more && (catalog.loading || catalog.next == null)) return
        filters.validate()
        val cursor = if (more) catalog.next else null
        val sameSelection = mode == catalog.mode && query == catalog.query && filters == catalog.filters
        val previous = if (more || (refresh && sameSelection)) catalog.items else emptyList()
        catalogJob?.cancel()
        val request = ++catalogRequest
        catalog = GalleryCatalogState(mode, query, filters, previous, cursor, loading = true, initialized = true, refreshing = refresh && !more && previous.isNotEmpty())
        catalogJob = viewModelScope.launch {
            try {
                repository.prepareSession()
                val page = repository.browse(mode, query, filters, cursor)
                check(!more || page.next == null || (page.next != cursor && page.items.any { item -> previous.none { it.url == item.url } })) {
                    "No new galleries loaded. Pull down to refresh or retry."
                }
                if (request == catalogRequest) catalog = catalog.copy(
                    items = (if (more) previous + page.items else page.items).distinctBy { it.url },
                    next = page.next, loading = false, refreshing = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == catalogRequest) catalog = catalog.copy(loading = false, refreshing = false,
                    error = failure.message ?: "The gallery list could not load.")
            }
        }
    }

    init {
        viewModelScope.launch {
            try {
                repository.prepareSession()
                repository.initialize()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) { error = failure.message }
        }
    }

    fun open(url: String, refresh: Boolean = false) {
        loadingJob?.cancel()
        val request = ++galleryRequest
        loadingJob = viewModelScope.launch {
            loadingUrl = url
            error = null
            try {
                repository.prepareSession()
                repository.loadGallery(url, refresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == galleryRequest) error = failure.message ?: "The gallery could not load."
            } finally { if (request == galleryRequest) loadingUrl = null }
        }
    }

    fun download(gallery: Gallery) {
        viewModelScope.launch {
            try {
                repository.prepareSession()
                repository.setSaved(gallery, true)
                repository.setDownload(gallery.key, GalleryDownload(queued = true, message = "Queued"))
                GalleryDownloadService.start(getApplication(), gallery.url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                repository.setDownload(gallery.key, GalleryDownload(message = failure.message ?: "The download could not start."))
            }
        }
    }

    fun pause(gallery: Gallery) {
        runCatching { GalleryDownloadService.pause(getApplication(), gallery.url) }
            .onFailure { repository.setDownload(gallery.key, GalleryDownload(message = "Could not pause. Try again.")) }
    }

    fun save(gallery: Gallery) = viewModelScope.launch {
        try { repository.setSaved(gallery, !gallery.saved) } catch (failure: Exception) { error = failure.message }
    }

    fun removeDownloads(gallery: Gallery) = viewModelScope.launch {
        try { repository.removeDownloads(gallery) } catch (failure: Exception) { error = failure.message }
    }
}

internal data class GalleryCatalogState(
    val mode: GalleryCatalogMode = GalleryCatalogMode.POPULAR,
    val query: String = "", val filters: GalleryFilters = GalleryFilters(),
    val items: List<GallerySummary> = emptyList(), val next: String? = null,
    val loading: Boolean = false, val error: String? = null, val initialized: Boolean = false,
    val refreshing: Boolean = false,
) {
    fun canLoadMore(lastVisible: Int): Boolean = !loading && error == null && next != null &&
        items.isNotEmpty() && lastVisible >= 0 && lastVisible >= items.size - 4
}
