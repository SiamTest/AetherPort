package com.forgeport.android.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgeport.android.ui.EhentaiNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryCatalogScreen(
    vm: GalleryViewModel, back: () -> Unit, open: (String) -> Unit,
    website: (String) -> Unit, library: () -> Unit, downloads: () -> Unit,
) {
    val state = vm.catalog
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    val gridState = key(state.mode, state.query, state.filters) { rememberLazyGridState() }
    val listState = key(state.mode, state.query, state.filters) { rememberLazyListState() }
    LaunchedEffect(grid, gridState, listState) {
        snapshotFlow {
            val lastVisible = if (grid) gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                else listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            vm.catalog.canLoadMore(lastVisible)
        }.distinctUntilChanged().collect { load -> if (load) vm.browse(more = true) }
    }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { if (!vm.catalog.initialized) vm.browse() }
    fun submit() { focus.clearFocus(); vm.browse(query = search.trim()) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("E-Hentai") },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Filled.Search, "Search galleries") }
                        IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Filled.GridView else Icons.Filled.ViewList, if (grid) "Switch to list" else "Switch to grid") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Gallery library") }, onClick = { menu = false; library() })
                                DropdownMenuItem(text = { Text("Account & access") }, onClick = { menu = false; website(EhentaiNavigation.HOME) })
                                DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; vm.browse(refresh = true) })
                            }
                        }
                    },
                )
                if (searchOpen) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        placeholder = { Text("Search titles and tags") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        trailingIcon = { IconButton(onClick = { submit() }) { Icon(Icons.Filled.Search, "Run search") } },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogChip("Popular", Icons.Filled.Favorite, state.mode == GalleryCatalogMode.POPULAR, Modifier.weight(1.12f)) { vm.browse(mode = GalleryCatalogMode.POPULAR, refresh = true) }
                    CatalogChip("Latest", Icons.Filled.NewReleases, state.mode == GalleryCatalogMode.LATEST, Modifier.weight(1f)) { vm.browse(mode = GalleryCatalogMode.LATEST, refresh = true) }
                    CatalogChip(if (state.filters.active) "Filter •" else "Filter", Icons.Filled.FilterList, state.filters.active, Modifier.weight(1f)) { filterOpen = true }
                }
                if (state.query.isNotBlank() || state.filters.active) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.query.ifBlank { "Filters applied" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { search = ""; vm.browse(query = "", filters = GalleryFilters()) }) { Text("Clear") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = { GalleryNavigationBar("browse", browse = {}, library = library, downloads = downloads) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.browse(refresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    CatalogFeedback(state, retry = { vm.browse(refresh = true) }, website = { website(EhentaiNavigation.HOME) })
                }
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), state = gridState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.items, key = { it.url }) { item ->
                        Column(Modifier.fillMaxWidth().clickable { open(item.url) }) {
                            GalleryCover(vm.repository, item.cover, item.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)))
                            Text(item.title, Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CatalogFooter(state, load = { vm.browse(more = true) }, retry = { vm.browse(more = state.next != null, refresh = true) })
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(state.items, key = { it.url }) { item ->
                        Row(Modifier.fillMaxWidth().clickable { open(item.url) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GalleryCover(vm.repository, item.cover, item.title, Modifier.width(82.dp).height(123.dp).clip(RoundedCornerShape(4.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.title, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                Text(item.category, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { CatalogFooter(state, load = { vm.browse(more = true) }, retry = { vm.browse(more = state.next != null, refresh = true) }) }
                }
            }
        }
    }
    if (filterOpen) GalleryFilterDialog(state.filters, dismiss = { filterOpen = false }) { filters ->
        filterOpen = false; vm.browse(filters = filters)
    }
}

@Composable
private fun CatalogChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 42.dp), shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun CatalogFeedback(state: GalleryCatalogState, retry: () -> Unit, website: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.loading) {
            CircularProgressIndicator(); Text("Loading galleries…")
        } else if (state.error != null) {
            Text(state.error); Button(onClick = retry) { Text("Try again") }; TextButton(onClick = website) { Text("Account & access") }
        } else {
            Text("No galleries found")
            Text(if (state.mode == GalleryCatalogMode.POPULAR) "Try fewer filters, or search Latest for more galleries." else "Try another search or fewer filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CatalogFooter(state: GalleryCatalogState, load: () -> Unit, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.loading && !state.refreshing -> CircularProgressIndicator(Modifier.size(28.dp))
            state.error != null -> { Text(state.error); TextButton(onClick = retry) { Text("Retry loading") } }
            state.next != null -> OutlinedButton(onClick = load) { Text("Load more") }
        }
    }
}

@Composable
private fun GalleryFilterDialog(current: GalleryFilters, dismiss: () -> Unit, apply: (GalleryFilters) -> Unit) {
    var categories by remember { mutableStateOf(current.categories) }
    var language by remember { mutableStateOf(current.language) }
    var uploader by remember { mutableStateOf(current.uploader) }
    var rating by remember { mutableIntStateOf(current.minRating) }
    var minimum by remember { mutableStateOf(current.minPages?.toString().orEmpty()) }
    var maximum by remember { mutableStateOf(current.maxPages?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var languages by remember { mutableStateOf(false) }
    var ratings by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text("Filter galleries") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Categories", style = MaterialTheme.typography.titleSmall)
                GalleryCatalog.categories.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { category ->
                            Row(Modifier.weight(1f).clickable {
                                categories = if (category.flag in categories) categories - category.flag else categories + category.flag
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(category.flag in categories, onCheckedChange = { checked -> categories = if (checked) categories + category.flag else categories - category.flag })
                                Text(category.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { languages = true }, modifier = Modifier.fillMaxWidth()) { Text("Language: ${language.ifBlank { "Any" }}") }
                    DropdownMenu(languages, onDismissRequest = { languages = false }) {
                        listOf("", "English", "Japanese", "Chinese", "Korean", "Spanish", "French", "German", "Italian", "Portuguese", "Russian", "Thai", "Vietnamese").forEach { value ->
                            DropdownMenuItem(text = { Text(value.ifBlank { "Any" }) }, onClick = { language = value; languages = false })
                        }
                    }
                }
                OutlinedTextField(uploader, { uploader = it }, label = { Text("Uploader") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { ratings = true }, modifier = Modifier.fillMaxWidth()) { Text(if (rating == 0) "Minimum rating: Any" else "Minimum rating: $rating stars") }
                    DropdownMenu(ratings, onDismissRequest = { ratings = false }) {
                        listOf(0, 2, 3, 4, 5).forEach { value ->
                            DropdownMenuItem(text = { Text(if (value == 0) "Any rating" else "$value stars or more") }, onClick = { rating = value; ratings = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minimum, { minimum = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Min pages") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(maximum, { maximum = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Max pages") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { categories = GalleryFilters().categories; language = ""; uploader = ""; rating = 0; minimum = ""; maximum = ""; error = null }) { Text("Reset filters") }
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                require(minimum.isBlank() || minimum.toIntOrNull() != null) { "Enter a valid minimum page count." }
                require(maximum.isBlank() || maximum.toIntOrNull() != null) { "Enter a valid maximum page count." }
                val filters = GalleryFilters(categories, language, uploader.trim(), rating, minimum.toIntOrNull(), maximum.toIntOrNull())
                filters.validate(); apply(filters)
            } catch (failure: IllegalArgumentException) { error = failure.message }
        }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun GalleryCover(repository: GalleryRepository, url: String, title: String, modifier: Modifier) {
    var retry by remember(url) { mutableIntStateOf(0) }
    val state by produceState<GalleryImageState>(GalleryImageState.Loading, url, retry) {
        value = GalleryImageState.Loading
        try {
            value = GalleryImageState.Ready(withContext(Dispatchers.IO) { decodeGalleryImage(repository.thumbnailFile(url), cover = true) })
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { value = GalleryImageState.Failed(failure.message.orEmpty()) }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        when (val image = state) {
            GalleryImageState.Loading -> Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            is GalleryImageState.Ready -> Image(image.bitmap.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is GalleryImageState.Failed -> Box(contentAlignment = Alignment.Center) {
                if (url.isBlank()) Icon(Icons.Filled.Photo, "Cover unavailable", Modifier.size(32.dp))
                else TextButton(onClick = { retry++ }) { Text("Retry cover") }
            }
        }
    }
}
