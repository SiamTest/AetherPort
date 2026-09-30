package com.forgeport.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forgeport.android.ui.ForgePortApp
import com.forgeport.android.ui.theme.ForgePortTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgePortTheme {
                ForgePortApp()
            }
        }
    }
}
