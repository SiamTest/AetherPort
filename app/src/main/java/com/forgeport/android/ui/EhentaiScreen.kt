package com.forgeport.android.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private class EhentaiBrowserState(var savedState: Bundle? = null) {
    var webView: WebView? = null
    var openingGallery = false
    var url by mutableStateOf(EhentaiNavigation.HOME)
    var title by mutableStateOf("E-Hentai")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    fun save(): Bundle = Bundle().also {
        if (webView?.saveState(it) == null) savedState?.let(it::putAll)
    }

    fun update(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
        view.url?.takeIf(EhentaiNavigation::isInternalUrl)?.let { url = it }
    }

    companion object {
        val StateSaver = Saver<EhentaiBrowserState, Bundle>(
            save = { it.save() },
            restore = { EhentaiBrowserState(it) },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled") // The website requires JavaScript; no native bridge is exposed.
@Composable
internal fun EhentaiScreen(
    handleBack: Boolean = true,
    initialUrl: String = EhentaiNavigation.HOME,
    onOpenGallery: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val state = rememberSaveable(saver = EhentaiBrowserState.StateSaver) { EhentaiBrowserState() }
    var query by rememberSaveable { mutableStateOf("") }

    fun openInBrowser(url: String) {
        if (!EhentaiNavigation.isWebUrl(url)) {
            Toast.makeText(context, "This link cannot be opened here.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser is available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    fun search() {
        focus.clearFocus()
        state.webView?.loadUrl(EhentaiNavigation.searchUrl(query))
    }

    BackHandler(enabled = handleBack && state.canGoBack) { state.webView?.goBack() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    state.savedState = state.save()
                    state.webView?.onPause()
                    CookieManager.getInstance().flush()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search E-Hentai") },
            placeholder = { Text("Title, artist or tags") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            trailingIcon = {
                IconButton(onClick = { search() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search website")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (state.loading) {
            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webContext ->
                    WebView(webContext).apply {
                        state.webView = this
                        state.openingGallery = false
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                state.progress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView, title: String?) {
                                state.title = title?.takeIf { it.isNotBlank() } ?: "E-Hentai"
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                val gallery = EhentaiNavigation.galleryUrl(url)
                                if (request.isForMainFrame && gallery != null && onOpenGallery != null) {
                                    if (!state.openingGallery) {
                                        state.openingGallery = true
                                        onOpenGallery(gallery)
                                    }
                                    return true
                                }
                                if (EhentaiNavigation.isInternalUrl(url)) return false
                                if (request.isForMainFrame && request.hasGesture()) openInBrowser(url)
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                state.loading = true
                                state.progress = 0
                                state.error = null
                                state.update(view)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                state.loading = false
                                state.update(view)
                                CookieManager.getInstance().flush()
                                val gallery = url?.let(EhentaiNavigation::galleryUrl)
                                if (gallery != null && onOpenGallery != null && !state.openingGallery && state.error == null) {
                                    state.openingGallery = true
                                    onOpenGallery(gallery)
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                state.update(view)
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    state.error = "The page could not load. Check your connection and try again."
                                    state.loading = false
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) {
                                    state.error = "The website returned an error (${response.statusCode}). Try again or open it in your browser."
                                    state.loading = false
                                }
                            }
                        }
                        setDownloadListener { _, _, _, _, _ ->
                            Toast.makeText(webContext, "Use Download gallery on the gallery screen to save pages offline.", Toast.LENGTH_LONG).show()
                        }
                        val history = state.savedState?.let { restoreState(it) }
                        if (history == null) loadUrl(initialUrl) else state.update(this)
                    }
                },
                onRelease = { view ->
                    state.savedState = state.save()
                    state.webView = null
                    view.stopLoading()
                    view.webChromeClient = null
                    view.destroy()
                },
            )
            state.error?.let { error ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Unable to open page", style = MaterialTheme.typography.titleMedium)
                        Text(error, modifier = Modifier.padding(vertical = 12.dp))
                        Button(onClick = { state.webView?.loadUrl(state.url) }) { Text("Try again") }
                        TextButton(onClick = { openInBrowser(state.url) }) { Text("Open in browser") }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                Text(
                    state.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { state.webView?.goBack() }, enabled = state.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    IconButton(onClick = { state.webView?.goForward() }, enabled = state.canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                    IconButton(onClick = {
                        query = ""
                        focus.clearFocus()
                        state.webView?.loadUrl(EhentaiNavigation.HOME)
                    }) { Icon(Icons.Filled.Home, contentDescription = "E-Hentai home") }
                    IconButton(onClick = {
                        if (state.loading) {
                            state.webView?.stopLoading()
                            state.loading = false
                        } else state.webView?.reload()
                    }) {
                        Icon(
                            if (state.loading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = if (state.loading) "Stop loading" else "Reload page",
                        )
                    }
                    IconButton(onClick = { openInBrowser(state.url) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open current page in browser")
                    }
                }
            }
        }
    }
}
