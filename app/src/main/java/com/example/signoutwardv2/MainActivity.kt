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
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // CRITICAL: Prevent crashes from initialization failures
            // Apply emulator workarounds for Binder IPC and audio issues
            // Wrap in try-catch to prevent crashes from reflection failures
            EmulatorWorkarounds.applyWorkarounds(this)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Emulator workarounds failed: ${e.message}", e)
            // Continue - workarounds are optional
        }
        
        try {
            // Enable edge-to-edge display
            enableEdgeToEdge()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to enable edge-to-edge: ${e.message}", e)
            // Continue - edge-to-edge is optional
        }
        
        try {
            // Keep screen on for ad display
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to set keep screen on flag: ${e.message}", e)
            // Continue - flag is optional
        }
        
        try {
            // Hide system bars for immersive experience
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to configure window insets: ${e.message}", e)
            // Continue - insets are optional
        }
        
        try {
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
        } catch (e: Exception) {
            android.util.Log.e(TAG, "CRITICAL: Failed to set content: ${e.message}", e)
            // Re-throw - this is critical
            throw e
        }
    }
    
    override fun onResume() {
        super.onResume()
        // CRITICAL: Maintain window flags on resume to prevent surface issues
        // This ensures surface rendering works correctly after app resumes
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    override fun onPause() {
        super.onPause()
        // Optional: Can clear flags here if needed for battery optimization
        // For digital signage, we keep screen on even when paused
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup if needed
    }
}
