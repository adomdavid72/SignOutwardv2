package com.example.signoutwardv2.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * EmulatorWorkarounds - App-level workarounds for emulator system issues
 * 
 * These workarounds help reduce Binder IPC and audio errors in the emulator
 * by minimizing IPC calls and suppressing audio operations.
 * 
 * Note: These are workarounds, not fixes. The actual issues are in the emulator/system.
 */
object EmulatorWorkarounds {
    private const val TAG = "EmulatorWorkarounds"
    
    /**
     * Check if running on emulator
     */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
    }
    
    /**
     * Suppress audio output on emulator
     * This reduces PCM write failures by minimizing audio operations
     */
    fun suppressAudioOnEmulator(context: Context) {
        if (!isEmulator()) return
        
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                // Set audio mode to silent
                am.setStreamMute(AudioManager.STREAM_MUSIC, true)
                am.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                am.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
                am.setStreamMute(AudioManager.STREAM_ALARM, true)
                
                // Set ringer mode to silent
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                
                Log.d(TAG, "Audio suppressed on emulator")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to suppress audio: ${e.message}")
        }
    }
    
    /**
     * Optimize Binder transactions
     * Reduces IPC calls that might cause -22 errors
     */
    fun optimizeBinderTransactions() {
        if (!isEmulator()) return
        
        try {
            // Set system properties via reflection (if possible)
            // Note: This may not work without root/system permissions
            val systemProperties = Class.forName("android.os.SystemProperties")
            val setMethod: Method = systemProperties.getMethod(
                "set",
                String::class.java,
                String::class.java
            )
            
            // Increase transaction buffer size
            try {
                setMethod.invoke(null, "debug.binder.transaction_buffer_size", "1024")
                Log.d(TAG, "Binder transaction buffer size increased")
            } catch (e: Exception) {
                // May fail without root - this is expected
                Log.d(TAG, "Could not set Binder buffer size (requires root): ${e.message}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Binder optimization not available: ${e.message}")
        }
    }
    
    /**
     * Apply all emulator workarounds
     */
    fun applyWorkarounds(context: Context) {
        if (!isEmulator()) {
            Log.d(TAG, "Not running on emulator - workarounds skipped")
            return
        }
        
        Log.d(TAG, "Applying emulator workarounds...")
        suppressAudioOnEmulator(context)
        optimizeBinderTransactions()
        Log.d(TAG, "Emulator workarounds applied")
    }
}

