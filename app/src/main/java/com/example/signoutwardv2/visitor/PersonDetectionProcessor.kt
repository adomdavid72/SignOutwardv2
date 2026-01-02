package com.example.signoutwardv2.visitor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * PersonDetectionProcessor - Processes camera frames to detect persons using ML Kit
 * 
 * This processor:
 * - Uses ML Kit Object Detection to detect persons (simpler than MediaPipe, better Android support)
 * - Tracks person positions for line crossing detection
 * - Does NOT store images or video (in-memory only)
 * - Optimized for tablet hardware (low CPU/GPU usage)
 * 
 * Note: Using ML Kit instead of MediaPipe for better Android integration and performance
 */
class PersonDetectionProcessor(private val context: Context) {
    companion object {
        private const val TAG = "PersonDetectionProcessor"
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
    }

    private var objectDetector: com.google.mlkit.vision.objects.ObjectDetector? = null
    private val detectedPersons = MutableStateFlow<List<DetectedPerson>>(emptyList())
    private val trackedPersons = mutableMapOf<Int, TrackedPerson>()

    /**
     * Detected person with bounding box and position
     */
    data class DetectedPerson(
        val id: Int,
        val centerX: Float, // Normalized 0-1
        val centerY: Float, // Normalized 0-1
        val width: Float,
        val height: Float,
        val confidence: Float
    )

    /**
     * Tracked person for line crossing detection
     */
    private data class TrackedPerson(
        val id: Int,
        var lastCenterX: Float,
        var lastCenterY: Float,
        var lastUpdateTime: Long
    )

    init {
        initializeDetector()
    }

    /**
     * Initialize ML Kit Object Detector for person detection
     */
    private fun initializeDetector() {
        try {
            // Use ML Kit Object Detection (optimized for Android, includes person detection)
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) // For real-time processing
                .enableMultipleObjects() // Detect multiple persons
                .enableClassification() // Enable classification
                .build()

            objectDetector = ObjectDetection.getClient(options)
            Log.d(TAG, "ML Kit Object Detector initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit detector", e)
        }
    }

    /**
     * Process detection result from ML Kit
     */
    private fun processDetectionResult(
        objects: List<DetectedObject>,
        imageWidth: Float,
        imageHeight: Float
    ) {
        val currentTime = System.currentTimeMillis()
        val persons = mutableListOf<DetectedPerson>()

        // Filter for person detections (ML Kit detects objects including persons)
        objects.forEachIndexed { index, detectedObject ->
            // Check if object has high confidence and reasonable size (likely a person)
            val boundingBox = detectedObject.boundingBox
            val objectWidth = boundingBox.width()
            val objectHeight = boundingBox.height()
            val objectArea = objectWidth * objectHeight
            val imageArea = imageWidth * imageHeight
            val areaRatio = objectArea / imageArea

            // Filter: reasonable size (not too small, not too large) - likely a person
            if (areaRatio in 0.01f..0.8f && objectHeight > objectWidth * 0.8f) {
                val centerX = boundingBox.left + objectWidth / 2f
                val centerY = boundingBox.top + objectHeight / 2f
                
                // Normalize coordinates (0-1 range)
                val normalizedCenterX = centerX / imageWidth
                val normalizedCenterY = centerY / imageHeight
                val normalizedWidth = objectWidth / imageWidth
                val normalizedHeight = objectHeight / imageHeight

                // Get confidence from classification if available
                val confidence = detectedObject.labels.firstOrNull()?.confidence ?: 0.7f

                persons.add(
                    DetectedPerson(
                        id = index,
                        centerX = normalizedCenterX,
                        centerY = normalizedCenterY,
                        width = normalizedWidth,
                        height = normalizedHeight,
                        confidence = confidence
                    )
                )
            }
        }

        // Update tracked persons for line crossing detection
        updateTrackedPersons(persons, currentTime)
        
        // Emit detected persons
        detectedPersons.value = persons
    }

    /**
     * Update tracked persons for line crossing detection
     */
    private fun updateTrackedPersons(persons: List<DetectedPerson>, currentTime: Long) {
        // Remove old tracked persons (not seen for 1 second)
        trackedPersons.entries.removeAll { (currentTime - it.value.lastUpdateTime) > 1000 }

        // Update or add tracked persons
        persons.forEach { person ->
            val tracked = trackedPersons[person.id]
            if (tracked != null) {
                tracked.lastCenterX = person.centerX
                tracked.lastCenterY = person.centerY
                tracked.lastUpdateTime = currentTime
            } else {
                trackedPersons[person.id] = TrackedPerson(
                    id = person.id,
                    lastCenterX = person.centerX,
                    lastCenterY = person.centerY,
                    lastUpdateTime = currentTime
                )
            }
        }
    }

    /**
     * Helper function to await Task result (ML Kit uses Google Play Services Tasks)
     */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T {
        return suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                cont.resume(result)
            }
            addOnFailureListener { exception ->
                cont.resumeWithException(exception)
            }
        }
    }

    /**
     * Detect persons in an ImageProxy from CameraX
     * This is called from CameraX for each frame
     */
    suspend fun detectPersonsFromImageProxy(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                val task = objectDetector?.process(image) ?: return
                val result = task.awaitResult()
                
                processDetectionResult(result, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting persons from ImageProxy", e)
        }
    }

    /**
     * Detect persons in a bitmap image (alternative method)
     */
    suspend fun detectPersons(bitmap: Bitmap) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val task = objectDetector?.process(image) ?: return
            val result = task.awaitResult()
            
            processDetectionResult(result, bitmap.width.toFloat(), bitmap.height.toFloat())
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting persons", e)
        }
    }

    /**
     * Get current detected persons (StateFlow for reactive UI updates)
     */
    fun getDetectedPersons(): StateFlow<List<DetectedPerson>> = detectedPersons.asStateFlow()

    /**
     * Get tracked persons for line crossing detection (removed - not needed externally)
     */

    /**
     * Release resources
     */
    fun release() {
        objectDetector?.close()
        objectDetector = null
        trackedPersons.clear()
        detectedPersons.value = emptyList()
        Log.d(TAG, "PersonDetectionProcessor released")
    }
}

