package com.pocketnode.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketnode.client.ui.MainClientScreen
import com.pocketnode.client.ui.theme.ObsidianBase
import com.pocketnode.client.ui.theme.PocketNodeClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketNodeClientTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBase
                ) {
                    MainClientScreen()
                }
            }
        }
    }
}
