package com.forgeport.android.gallery

import com.forgeport.android.ui.theme.ExpressiveButton as Button
import com.forgeport.android.ui.theme.ExpressiveTextButton as TextButton
import com.forgeport.android.ui.theme.ExpressiveIconButton as IconButton
import com.forgeport.android.ui.theme.ExpressiveDialog as AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import com.forgeport.android.ui.theme.ExpressiveMotion
import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun GalleryReaderScreen(url: String, startPage: Int, vm: GalleryViewModel, back: () -> Unit) {
    val galleries by vm.repository.galleries.collectAsStateWithLifecycle()
    val gallery = galleries.firstOrNull { it.url == url }
    LaunchedEffect(url) { if (gallery == null) vm.open(url) }
    if (gallery == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            if (vm.error == null) CircularProgressIndicator() else Text(vm.error.orEmpty())
            TextButton(onClick = back) { Text("Back to gallery") }
        }
        return
    }
    var current by rememberSaveable(gallery.key, startPage) { mutableIntStateOf(startPage.coerceIn(gallery.pages.indices)) }
    LaunchedEffect(gallery.key, current) {
        vm.repository.prefetch(gallery, current)
    }
    var mode by rememberSaveable { mutableStateOf(vm.repository.readingMode()) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var autoScroll by rememberSaveable(gallery.key) { mutableStateOf(false) }
    var intervalSeconds by rememberSaveable { mutableIntStateOf(vm.repository.autoScrollSeconds()) }
    var autoScrollDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSeconds by rememberSaveable { mutableIntStateOf(intervalSeconds) }
    val readyPages = remember(gallery.key) { mutableStateMapOf<Int, Boolean>() }
    val pager = rememberPagerState(initialPage = current, pageCount = { gallery.pages.size })
    val vertical = rememberLazyListState(initialFirstVisibleItemIndex = current)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val activity = context as? Activity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(mode) {
        zoomed = false
        vm.repository.setReadingMode(mode)
        if (mode == "VERTICAL") vertical.scrollToItem(current) else pager.scrollToPage(current)
    }
    LaunchedEffect(mode, pager, vertical) {
        snapshotFlow { if (mode == "VERTICAL") vertical.firstVisibleItemIndex else pager.settledPage }
            .distinctUntilChanged().collect { page ->
                current = page.coerceIn(gallery.pages.indices)
                zoomed = false
                vm.repository.markRead(gallery, current)
            }
    }
    fun jump(page: Int) {
        scope.launch {
            val target = page.coerceIn(gallery.pages.indices)
            if (mode == "VERTICAL") vertical.animateScrollToItem(target) else pager.animateScrollToPage(target, animationSpec = ExpressiveMotion.spatial())
        }
    }
    LaunchedEffect(autoScroll, current, mode, intervalSeconds, resumed, zoomed, settings, autoScrollDialog, readyPages[current]) {
        if (!autoScroll || !resumed || zoomed || settings || autoScrollDialog || readyPages[current] != true) return@LaunchedEffect
        while (isActive) {
            delay(intervalSeconds * 1000L)
            if (vertical.isScrollInProgress || pager.isScrollInProgress) continue
            if (mode == "VERTICAL") {
                val viewport = vertical.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
                val moved = vertical.animateScrollBy(viewport * 0.85f, animationSpec = ExpressiveMotion.spatial())
                if (abs(moved) < 1f && current == gallery.pages.lastIndex) {
                    autoScroll = false; controls = true; break
                }
            } else if (current < gallery.pages.lastIndex) {
                pager.animateScrollToPage(current + 1, animationSpec = ExpressiveMotion.spatial())
            } else {
                autoScroll = false; controls = true; break
            }
        }
    }
    if (autoScrollDialog) AlertDialog(
        onDismissRequest = { autoScrollDialog = false }, title = { Text("Auto-scroll") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scroll every $selectedSeconds ${if (selectedSeconds == 1) "second" else "seconds"}")
                Slider(value = selectedSeconds.toFloat(), onValueChange = { selectedSeconds = it.roundToInt() }, valueRange = 1f..60f, steps = 58)
                Text("Turns to the next image in paged mode, or scrolls down in vertical mode. Waits for images to load and pauses while zoomed or outside the reader.")
            }
        },
        confirmButton = { TextButton(onClick = {
            intervalSeconds = selectedSeconds
            vm.repository.setAutoScrollSeconds(intervalSeconds)
            autoScroll = true; autoScrollDialog = false
        }) { Text(if (autoScroll) "Apply" else "Start") } },
        dismissButton = { TextButton(onClick = { autoScrollDialog = false }) { Text("Cancel") } },
    )
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        AnimatedVisibility(controls, enter = expandVertically(ExpressiveMotion.spatial()), exit = shrinkVertically(ExpressiveMotion.spatial())) {
            Surface {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to gallery") }
                    Column(Modifier.weight(1f)) {
                        Text(gallery.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${current + 1} / ${gallery.pages.size}", style = MaterialTheme.typography.labelMedium)
                        if (autoScroll) Text("Auto-scroll • ${intervalSeconds}s", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        if (autoScroll) autoScroll = false
                        else { selectedSeconds = intervalSeconds; autoScrollDialog = true }
                    }) { Icon(if (autoScroll) Icons.Filled.Pause else Icons.Filled.Timer, contentDescription = if (autoScroll) "Pause auto-scroll" else "Set auto-scroll interval") }
                    Box {
                        IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Reading direction") }
                        DropdownMenu(expanded = settings, onDismissRequest = { settings = false }) {
                            listOf("RTL" to "Manga • right to left", "LTR" to "Left to right", "VERTICAL" to "Vertical scrolling").forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { mode = value; settings = false })
                            }
                            DropdownMenuItem(text = { Text("Auto-scroll settings") }, onClick = { settings = false; selectedSeconds = intervalSeconds; autoScrollDialog = true })
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mode == "VERTICAL") {
                LazyColumn(state = vertical, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(gallery.pages, key = { index, _ -> index }) { index, _ ->
                        ReaderPage(vm.repository, gallery, index, vertical = true, toggle = { controls = !controls }, zoomChanged = { if (index == current) zoomed = it }, readyChanged = { readyPages[index] = it })
                    }
                }
            } else {
                HorizontalPager(state = pager, reverseLayout = mode == "RTL", userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { index ->
                    ReaderPage(vm.repository, gallery, index, vertical = false, toggle = { controls = !controls }, zoomChanged = { if (index == current) zoomed = it }, readyChanged = { readyPages[index] = it })
                }
            }
        }
        AnimatedVisibility(controls, enter = expandVertically(ExpressiveMotion.spatial()), exit = shrinkVertically(ExpressiveMotion.spatial())) {
            Surface {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { jump(current - 1) }, enabled = current > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    if (gallery.pages.size > 1) {
                        var target by remember(current) { mutableFloatStateOf(current.toFloat()) }
                        Slider(
                            value = target, onValueChange = { target = it }, onValueChangeFinished = { jump(target.toInt()) },
                            valueRange = 0f..gallery.pages.lastIndex.toFloat(), modifier = Modifier.weight(1f),
                        )
                    } else Text("1 / 1", modifier = Modifier.weight(1f))
                    IconButton(onClick = { jump(current + 1) }, enabled = current < gallery.pages.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(
    repository: GalleryRepository, gallery: Gallery, index: Int, vertical: Boolean,
    toggle: () -> Unit, zoomChanged: (Boolean) -> Unit, readyChanged: (Boolean) -> Unit,
) {
    var retry by remember(gallery.key, index) { mutableIntStateOf(0) }
    val image = rememberGalleryImage(repository, gallery, index, retry, cover = false)
    DisposableEffect(image) {
        readyChanged(image is GalleryImageState.Ready)
        onDispose { readyChanged(false) }
    }
    val frame = if (vertical) Modifier.fillMaxWidth().heightIn(min = 240.dp) else Modifier.fillMaxSize()
    when (image) {
        GalleryImageState.Loading -> Box(frame, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is GalleryImageState.Failed -> Column(frame.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Page ${index + 1}", color = Color.White)
            Text(image.message, color = Color.White)
            TextButton(onClick = { retry++ }) { Text("Retry page") }
            TextButton(onClick = toggle) { Text("Show / hide controls") }
        }
        is GalleryImageState.Ready -> {
            var scale by remember(index, vertical) { mutableFloatStateOf(1f) }
            var offset by remember(index, vertical) { mutableStateOf(Offset.Zero) }
            var size by remember { mutableStateOf(IntSize.Zero) }
            val transform = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 4f)
                val maxX = size.width * (scale - 1) / 2
                val maxY = size.height * (scale - 1) / 2
                offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                zoomChanged(scale > 1.01f)
            }
            val dimensions = if (vertical) Modifier.fillMaxWidth().aspectRatio(image.bitmap.width.toFloat() / image.bitmap.height) else Modifier.fillMaxSize()
            Box(dimensions.clipToBounds().onSizeChanged { size = it }, contentAlignment = Alignment.Center) {
                Image(
                    image.bitmap.asImageBitmap(), "Page ${index + 1}",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    }.transformable(state = transform, canPan = { scale > 1f }).pointerInput(index, vertical) {
                        detectTapGestures(onTap = { toggle() }, onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2f
                            offset = Offset.Zero
                            zoomChanged(scale > 1f)
                        })
                    },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
