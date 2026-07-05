package com.oxarena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.oxarena.ui.navigation.OxNavHost
import com.oxarena.ui.theme.Background
import com.oxarena.ui.theme.OxArenaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All screens are Composables navigated within [OxNavHost].
 * Edge-to-edge for a modern, immersive full-bleed layout.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OxArenaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    OxNavHost()
                }
            }
        }
    }
}
