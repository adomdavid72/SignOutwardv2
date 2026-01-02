package com.example.signoutwardv2.visitor

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LineCrossingDetector - Detects when a person crosses a virtual entry line
 * 
 * This detector:
 * - Monitors person positions relative to a virtual line
 * - Detects crossings in a specific direction (entry)
 * - Prevents duplicate counts with debouncing
 * - Tracks crossings in-memory only (no storage)
 */
class LineCrossingDetector {
    companion object {
        private const val TAG = "LineCrossingDetector"
        private const val CROSSING_DEBOUNCE_MS = 1000L // Prevent duplicate counts
    }

    // Virtual entry line position (normalized 0-1, typically middle of frame)
    // Line is horizontal: y = lineY, person crosses from top (y < lineY) to bottom (y > lineY)
    private var entryLineY: Float = 0.5f // Default to middle of frame

    // Track crossings to prevent duplicates
    private val crossingHistory = mutableSetOf<Int>()
    private var lastCrossingTime = 0L
    private val visitorCount = MutableStateFlow(0)

    /**
     * Set the entry line position (normalized Y coordinate 0-1)
     * @param lineY Normalized Y position (0 = top, 1 = bottom)
     */
    fun setEntryLine(lineY: Float) {
        require(lineY in 0f..1f) { "Line Y must be between 0 and 1" }
        entryLineY = lineY
        Log.d(TAG, "Entry line set to Y=$lineY")
    }

    /**
     * Get current entry line position
     */
    fun getEntryLine(): Float = entryLineY

    /**
     * Process detected persons and detect line crossings
     * @param persons List of currently detected persons
     */
    fun processDetections(persons: List<PersonDetectionProcessor.DetectedPerson>) {
        val currentTime = System.currentTimeMillis()

        // Reset crossing history if enough time has passed
        if (currentTime - lastCrossingTime > CROSSING_DEBOUNCE_MS) {
            crossingHistory.clear()
        }

        persons.forEach { person ->
            // Check if person has crossed the entry line (from top to bottom)
            // Person is considered to have crossed if:
            // 1. Their center Y is below the line (centerY > entryLineY)
            // 2. They haven't been counted recently (debounce)
            // 3. They are in the middle portion of the frame (valid entry area)
            
            val isValidEntryArea = person.centerX in 0.2f..0.8f // Middle 60% of frame width
            val hasCrossedLine = person.centerY > entryLineY
            val notRecentlyCounted = person.id !in crossingHistory

            if (hasCrossedLine && isValidEntryArea && notRecentlyCounted) {
                // Person crossed the line - increment count
                crossingHistory.add(person.id)
                lastCrossingTime = currentTime
                val newCount = visitorCount.value + 1
                visitorCount.value = newCount
                Log.d(TAG, "Visitor detected crossing line. Count: $newCount")
            }
        }
    }

    /**
     * Get current visitor count (StateFlow for reactive UI updates)
     */
    fun getVisitorCount(): StateFlow<Int> = visitorCount.asStateFlow()

    /**
     * Reset visitor count
     */
    fun resetCount() {
        visitorCount.value = 0
        crossingHistory.clear()
        lastCrossingTime = 0L
        Log.d(TAG, "Visitor count reset")
    }

    /**
     * Get current count value (non-reactive)
     */
    fun getCurrentCount(): Int = visitorCount.value
}

