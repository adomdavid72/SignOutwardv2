package com.example.signoutwardv2.visitor

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * CameraPreview - CameraX preview with image analysis for person detection
 * 
 * This composable:
 * - Displays camera preview using CameraX
 * - Processes frames through PersonDetectionProcessor
 * - Does NOT store images/video (in-memory processing only)
 * - Optimized for performance (processes every few frames)
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    personDetector: PersonDetectionProcessor
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var preview by remember { mutableStateOf<Preview?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    
    // Frame processing control (process every 3rd frame for performance)
    var frameCounter by remember { mutableStateOf(0) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    // Initialize camera
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Preview use case
            val previewUseCase = Preview.Builder().build().also {
                preview = it
            }

            // Image analysis use case (for person detection)
            val analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Process latest frame only
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor) { imageProxy ->
                        // Process every 3rd frame for performance
                        frameCounter++
                        if (frameCounter % 3 == 0) {
                            scope.launch {
                                processImage(imageProxy, personDetector)
                            }
                        }
                        imageProxy.close()
                    }
                    imageAnalysis = it
                }

            // Select camera (back camera for entry detection)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind use cases to lifecycle
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase,
                analysisUseCase
            )

            Log.d("CameraPreview", "Camera initialized successfully")
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera initialization failed", e)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            preview = null
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            analysisExecutor.shutdown()
            personDetector.release()
        }
    }

    // Display camera preview
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                preview?.setSurfaceProvider(surfaceProvider)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Process image frame for person detection using ML Kit
 */
private suspend fun processImage(
    imageProxy: ImageProxy,
    personDetector: PersonDetectionProcessor
) {
    try {
        // ML Kit can process ImageProxy directly via InputImage.fromMediaImage()
        // But we need to convert rotation. For now, process the image
        personDetector.detectPersonsFromImageProxy(imageProxy)
    } catch (e: Exception) {
        Log.e("CameraPreview", "Error processing image frame", e)
    }
}

