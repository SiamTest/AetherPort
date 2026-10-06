package com.forgeport.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.forgeport.android.gallery.GalleryDownloadService
import com.forgeport.android.ui.ForgePortApp
import com.forgeport.android.ui.theme.ForgePortTheme

class MainActivity : ComponentActivity() {
    private var galleryLibraryRequest by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra(GalleryDownloadService.OPEN_LIBRARY, false)) galleryLibraryRequest++
        setContent {
            ForgePortTheme {
                ForgePortApp(galleryLibraryRequest = galleryLibraryRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(GalleryDownloadService.OPEN_LIBRARY, false)) galleryLibraryRequest++
    }
}
