package com.example.signoutwardv2

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.signoutwardv2.navigation.AppNavigation
import com.example.signoutwardv2.ui.theme.SignOutwardV2Theme
import com.example.signoutwardv2.ui.theme.White
import com.example.signoutwardv2.utils.EmulatorWorkarounds

// ============================================================================
// MAIN ACTIVITY
// ============================================================================
// Entry point for the SignOutward tablet app
// - Optimized for landscape tablet displays
// - Edge-to-edge immersive UI
// - Material Design 3 theming
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply emulator workarounds for Binder IPC and audio issues
        EmulatorWorkarounds.applyWorkarounds(this)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Keep screen on for ad display
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide system bars for immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        setContent {
            SignOutwardV2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = White
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
