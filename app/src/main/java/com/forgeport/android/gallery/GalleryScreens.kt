package com.forgeport.android.gallery

import com.forgeport.android.ui.theme.ExpressiveButton as Button
import com.forgeport.android.ui.theme.ExpressiveTonalButton as FilledTonalButton
import com.forgeport.android.ui.theme.ExpressiveTextButton as TextButton
import com.forgeport.android.ui.theme.ExpressiveIconButton as IconButton
import com.forgeport.android.ui.theme.ExpressiveFilterChip as FilterChip
import com.forgeport.android.ui.theme.ExpressiveCard as ElevatedCard
import com.forgeport.android.ui.theme.ExpressiveDialog as AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import com.forgeport.android.ui.theme.AdaptiveContent
import com.forgeport.android.ui.theme.ExpressiveMotion
import com.forgeport.android.ui.theme.sideBySideGalleryHeader
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryDetailsScreen(
    url: String, vm: GalleryViewModel, read: (String, Int) -> Unit, website: (String) -> Unit, back: () -> Unit,
) {
    val galleries by vm.repository.galleries.collectAsStateWithLifecycle()
    val downloads by vm.repository.downloads.collectAsStateWithLifecycle()
    val gallery = galleries.firstOrNull { it.url == url }
    LaunchedEffect(url) { vm.open(url) }
    LaunchedEffect(gallery?.key) {
        gallery?.let { vm.repository.prefetch(it, it.lastRead) }
    }
    var menu by remember { mutableStateOf(false) }
    var expanded by remember(url) { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val status = gallery?.let { downloads[it.key] } ?: GalleryDownload()
    val downloadAction = gallery?.let { rememberGalleryDownloadAction(vm, it, status) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Gallery details", style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }, actions = {
                if (gallery != null && downloadAction != null) IconButton(onClick = downloadAction, enabled = status.running || status.queued || gallery.downloaded < gallery.pages.size) {
                    Icon(if (status.running || status.queued) Icons.Filled.Pause else Icons.Filled.CloudDownload, if (status.running || status.queued) "Pause download" else "Download gallery")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Refresh gallery") }, onClick = { menu = false; vm.open(url, refresh = true) })
                        DropdownMenuItem(text = { Text("Account & access") }, onClick = { menu = false; website(url) })
                        if (gallery != null) {
                            if (gallery.saved) DropdownMenuItem(text = { Text("Remove from library") }, onClick = { menu = false; vm.removeFromLibrary(gallery) })
                            DropdownMenuItem(text = { Text("Delete history") }, onClick = { menu = false; vm.clearHistory(gallery) })
                        }
                        if (gallery != null && visibleInDownloads(gallery.downloaded, downloads[gallery.key])) DropdownMenuItem(text = { Text("Remove downloads") }, enabled = !status.running && !status.queued,
                            onClick = { menu = false; confirmDelete = true })
                    }
                }
            }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        },
        floatingActionButton = {
            if (gallery != null) ExtendedFloatingActionButton(
                onClick = { read(gallery.url, gallery.lastRead) },
                icon = { Icon(Icons.Filled.PlayArrow, null) }, text = { Text(if (gallery.lastRead > 0) "Continue" else "Start") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (gallery == null) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.loadingUrl == url || vm.error == null) {
                    CircularProgressIndicator(); Text("Opening gallery…", modifier = Modifier.padding(16.dp))
                } else {
                    Text(vm.error.orEmpty()); Button(onClick = { vm.open(url) }) { Text("Try again") }
                    TextButton(onClick = { website(url) }) { Text("Account & access") }
                }
            }
        } else {
            AdaptiveContent(Modifier.padding(padding)) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item {
                            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                                val cover: @Composable () -> Unit = {
                                    Surface(modifier = Modifier.width(110.dp).height(165.dp), shape = MaterialTheme.shapes.small) {
                                        GalleryPageImage(vm.repository, gallery, 0, Modifier.fillMaxSize(), cover = true)
                                    }
                                }
                                val summary: @Composable () -> Unit = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(gallery.title, style = MaterialTheme.typography.headlineSmall)
                                        Text(gallery.author.ifBlank { "Unknown author" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${gallery.pages.size} pages • E-Hentai", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (sideBySideGalleryHeader(maxWidth.value, LocalDensity.current.fontScale)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        cover()
                                        Box(Modifier.weight(1f)) { summary() }
                                    }
                                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    cover()
                                    summary()
                                }
                            }
                        }

                        item {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DetailAction(if (gallery.saved) "In library" else "Add to library", if (gallery.saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, gallery.saved) { vm.save(gallery) }
                                DetailAction("Refresh", Icons.Filled.Refresh) { vm.open(url, refresh = true) }
                                if (downloadAction != null) DetailAction(
                                    when { status.running || status.queued -> "Pause"; gallery.downloaded == gallery.pages.size -> "Offline ready"; else -> "Download" },
                                    if (status.running || status.queued) Icons.Filled.Pause else Icons.Filled.CloudDownload,
                                    enabled = status.running || status.queued || gallery.downloaded < gallery.pages.size,
                                    action = downloadAction,
                                )
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (vm.loadingUrl == url) LinearProgressIndicator(Modifier.fillMaxWidth())
                                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Text("Title: ${gallery.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AnimatedVisibility(expanded, enter = expandVertically(ExpressiveMotion.spatial()), exit = shrinkVertically(ExpressiveMotion.spatial())) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Spacer(Modifier.height(12.dp))
                                        if (gallery.uploader.isNotBlank()) Text("Uploader: ${gallery.uploader}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val metadata = linkedMapOf("posted" to "Posted", "visible" to "Visible", "language" to "Language", "file size" to "File Size", "length" to "Length", "favorited" to "Favorited", "rating" to "Rating")
                                        metadata.forEach { (key, label) ->
                                            val value = gallery.info[key].orEmpty().ifBlank {
                                                when (key) { "language" -> gallery.language; "length" -> "${gallery.pages.size} pages"; else -> "" }
                                            }
                                            if (value.isNotBlank()) Text("$label: $value", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (gallery.tags.isNotEmpty()) {
                                            Spacer(Modifier.height(16.dp)); Text("Tags:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (gallery.tagGroups.isNotEmpty()) gallery.tagGroups.forEach { (group, tags) ->
                                                Text("• $group: ${tags.joinToString(" • ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else Text(gallery.tags.joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Collapse description" else "Expand description")
                                }
                                if (gallery.category.isNotBlank()) Surface(shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                    Text(gallery.category, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("1 chapter", style = MaterialTheme.typography.titleLarge)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable { read(gallery.url, gallery.lastRead) }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("●  Chapter", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                        Text(if (gallery.lastRead > 0) "Continue at page ${gallery.lastRead + 1}" else "${gallery.pages.size} pages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (downloadAction != null) IconButton(onClick = downloadAction, enabled = status.running || status.queued || gallery.downloaded < gallery.pages.size) {
                                        Icon(if (status.running || status.queued) Icons.Filled.Pause else Icons.Filled.CloudDownload, if (status.running || status.queued) "Pause download" else "Download chapter")
                                    }
                                }
                                if (status.running || status.queued) LinearProgressIndicator(progress = { if (status.targetPages > 0) status.completedPages.toFloat() / status.targetPages else 0f }, modifier = Modifier.fillMaxWidth())
                                Text("${gallery.downloaded} / ${gallery.pages.size} pages saved offline", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                if (status.message.isNotBlank()) Text(status.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
            }
        }
    }

    if (confirmDelete && gallery != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Remove downloaded pages?") },
        text = { Text("Your saved gallery and reading progress will remain. Pages must be downloaded again for offline reading.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.removeDownloads(gallery) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DetailAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
    FilledTonalButton(onClick = action, enabled = enabled) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun rememberGalleryDownloadAction(vm: GalleryViewModel, gallery: Gallery, status: GalleryDownload): () -> Unit {
    val context = LocalContext.current
    var showSelection by rememberSaveable(gallery.key) { mutableStateOf(false) }
    var percent by rememberSaveable(gallery.key) { mutableIntStateOf(vm.repository.downloadPercent(gallery)) }
    var confirmedPercent by rememberSaveable(gallery.key) { mutableIntStateOf(percent) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.download(gallery, confirmedPercent) }
    if (showSelection) AlertDialog(
        onDismissRequest = { showSelection = false },
        title = { Text("Download gallery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val target = galleryDownloadPageCount(gallery.pages.size, percent)
                Text("$percent% • $target of ${gallery.pages.size} pages")
                Slider(value = percent.toFloat(), onValueChange = { percent = it.roundToInt() }, valueRange = 1f..100f, steps = 98)
                Text("Download pages 1–$target in reading order. Already downloaded pages are kept and skipped.")
            }
        },
        confirmButton = { TextButton(onClick = {
            showSelection = false
            confirmedPercent = percent
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else vm.download(gallery, confirmedPercent)
        }) { Text("Download") } },
        dismissButton = { TextButton(onClick = { showSelection = false }) { Text("Cancel") } },
    )
    return {
        if (status.running || status.queued) vm.pause(gallery)
        else { percent = vm.repository.downloadPercent(gallery); showSelection = true }
    }
}

@Composable
private fun GalleryDownloadButton(vm: GalleryViewModel, gallery: Gallery, status: GalleryDownload) {
    val active = status.running || status.queued
    val complete = gallery.downloaded == gallery.pages.size
    val action = rememberGalleryDownloadAction(vm, gallery, status)
    FilledTonalButton(
        onClick = action,
        enabled = !complete || active,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(if (active) Icons.Filled.Pause else Icons.Filled.CloudDownload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(when {
            active -> "Pause download"
            complete -> "Ready offline"
            gallery.downloaded > 0 -> "Resume download"
            else -> "Download gallery"
        })
    }
}

@Composable
internal fun GalleryNavigationBar(selected: String, browse: () -> Unit, library: () -> Unit, downloads: () -> Unit) {
    val colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        NavigationBarItem(selected == "browse", onClick = browse, icon = { Icon(Icons.Filled.Explore, null) }, label = { Text("Browse") }, colors = colors)
        NavigationBarItem(selected == "library", onClick = library, icon = { Icon(Icons.Filled.LibraryBooks, null) }, label = { Text("Library") }, colors = colors)
        NavigationBarItem(selected == "downloads", onClick = downloads, icon = { Icon(Icons.Filled.Download, null) }, label = { Text("Downloads") }, colors = colors)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryLibraryScreen(
    vm: GalleryViewModel, open: (String) -> Unit, browse: () -> Unit,
    library: () -> Unit, downloadsPage: () -> Unit, back: () -> Unit, read: (String, Int) -> Unit, downloadsOnly: Boolean = false,
) {
    val galleries by vm.repository.galleries.collectAsStateWithLifecycle()
    val downloads by vm.repository.downloads.collectAsStateWithLifecycle()
    var history by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Gallery?>(null) }
    val saved = remember(galleries, downloads, downloadsOnly, history) { galleries.filter {
        if (downloadsOnly) visibleInDownloads(it.downloaded, downloads[it.key])
        else if (history) it.visitedAt > 0 else it.saved
    }.let { if (history) it.sortedByDescending { item -> item.visitedAt } else it } }
    if (confirmHistory) AlertDialog(
        onDismissRequest = { confirmHistory = false }, title = { Text("Delete all history?") },
        text = { Text("This clears visited galleries and reading progress. Saved galleries and downloads remain.") },
        confirmButton = { TextButton(onClick = { confirmHistory = false; vm.clearHistory() }) { Text("Delete history") } },
        dismissButton = { TextButton(onClick = { confirmHistory = false }) { Text("Cancel") } },
    )
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(if (downloadsOnly) "Downloads" else if (history) "History" else "Gallery library", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            if (!downloadsOnly) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Library options") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (history) "Show library" else "Show history") }, onClick = { menu = false; history = !history })
                    DropdownMenuItem(text = { Text("Delete all history") }, onClick = { menu = false; confirmHistory = true })
                }
            }
        }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
        bottomBar = { GalleryNavigationBar(if (downloadsOnly) "downloads" else "library", browse, library, downloadsPage) },
    ) { padding ->
        AdaptiveContent(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            if (!downloadsOnly) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !history, onClick = { history = false }, label = { Text("Library") })
                FilterChip(selected = history, onClick = { history = true }, label = { Text("History") })
            }
            if (saved.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (downloadsOnly) Icons.Filled.Download else Icons.Filled.LibraryBooks, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(if (downloadsOnly) "No downloads yet" else if (history) "No history yet" else "Your gallery library", style = MaterialTheme.typography.titleLarge)
                    Text(if (downloadsOnly) "Download a gallery to read it offline." else if (history) "Galleries you visit appear here." else "Save a gallery to keep reading here.", modifier = Modifier.padding(vertical = 12.dp))
                    Button(onClick = browse) { Text("Browse galleries") }
                    vm.error?.let { Text(it) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(saved, key = { it.key }) { gallery ->
                        val status = downloads[gallery.key] ?: GalleryDownload()
                        ElevatedCard(onClick = { open(gallery.url) }) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GalleryPageImage(vm.repository, gallery, 0, Modifier.width(70.dp).height(100.dp), cover = true)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(gallery.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Page ${gallery.lastRead + 1} / ${gallery.pages.size}", style = MaterialTheme.typography.bodySmall)
                                    Text("${gallery.downloaded} / ${gallery.pages.size} pages offline", style = MaterialTheme.typography.bodySmall)
                                    if (status.message.isNotBlank()) Text(status.message, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                    if (status.running || status.queued) LinearProgressIndicator(progress = { if (status.targetPages > 0) status.completedPages.toFloat() / status.targetPages else 0f }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Box {
                                    IconButton(onClick = { selected = gallery }) { Icon(Icons.Filled.MoreVert, "Gallery options") }
                                    DropdownMenu(selected?.key == gallery.key, onDismissRequest = { selected = null }) {
                                        if (gallery.saved) DropdownMenuItem(text = { Text("Remove from library") }, onClick = { selected = null; vm.removeFromLibrary(gallery) })
                                        if (gallery.visitedAt > 0) DropdownMenuItem(text = { Text("Delete history") }, onClick = { selected = null; vm.clearHistory(gallery) })
                                        if (visibleInDownloads(gallery.downloaded, downloads[gallery.key])) DropdownMenuItem(text = { Text("Remove downloads") }, enabled = !status.running && !status.queued,
                                            onClick = { selected = null; vm.removeDownloads(gallery) })
                                    }
                                }
                            }
                            if (!downloadsOnly) TextButton(onClick = { read(gallery.url, gallery.lastRead) }) {
                                Icon(Icons.Filled.PlayArrow, null); Text(if (gallery.lastRead > 0) "Continue reading" else "Read gallery")
                            }
                            GalleryDownloadButton(vm, gallery, status)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
        }
    }
}

internal sealed interface GalleryImageState {
    data object Loading : GalleryImageState
    data class Ready(val bitmap: Bitmap) : GalleryImageState
    data class Failed(val message: String) : GalleryImageState
}

@Composable
internal fun rememberGalleryImage(repository: GalleryRepository, gallery: Gallery, index: Int, retry: Int, cover: Boolean): GalleryImageState {
    val state by produceState<GalleryImageState>(GalleryImageState.Loading, gallery.key, index, retry, cover) {
        value = GalleryImageState.Loading
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val file = if (cover && index == 0) repository.coverFile(gallery) else repository.pageFile(gallery, index)
                decodeGalleryImage(file, cover)
            }
            value = GalleryImageState.Ready(bitmap)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            value = GalleryImageState.Failed(failure.message ?: "This page could not load. Check your connection or download it first.")
        }
    }
    return state
}

@Composable
internal fun GalleryPageImage(repository: GalleryRepository, gallery: Gallery, index: Int, modifier: Modifier, cover: Boolean = false) {
    var retry by remember(gallery.key, index) { mutableIntStateOf(0) }
    when (val state = rememberGalleryImage(repository, gallery, index, retry, cover)) {
        GalleryImageState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        is GalleryImageState.Ready -> Image(state.bitmap.asImageBitmap(), "Page ${index + 1}", modifier, contentScale = if (cover) ContentScale.Crop else ContentScale.Fit)
        is GalleryImageState.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
            TextButton(onClick = { retry++ }) { Text(if (cover) "Retry cover" else "Retry page") }
        }
    }
}

// Retain only decoded covers, not large reader pages. Eviction releases references without
// recycling bitmaps still used by visible Compose images.
private val coverBitmaps = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

internal fun decodeGalleryImage(file: File, cover: Boolean): Bitmap {
    val cacheKey = if (cover) "${file.path}:${file.lastModified()}:${file.length()}" else null
    cacheKey?.let { coverBitmaps.get(it)?.let { bitmap -> return bitmap } }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image could not be decoded." }
    var sample = 1
    val maxPixels = if (cover) 400_000L else 4_000_000L
    while (bounds.outWidth / sample > (if (cover) 500 else 2048) ||
        bounds.outHeight / sample > (if (cover) 1000 else 8192) ||
        bounds.outWidth.toLong() * bounds.outHeight / sample / sample > maxPixels) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
    val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "This image could not be decoded." }
    cacheKey?.let { coverBitmaps.put(it, bitmap) }
    return bitmap
}
