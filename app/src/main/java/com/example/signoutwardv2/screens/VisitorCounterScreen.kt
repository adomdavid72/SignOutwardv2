package com.example.signoutwardv2.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signoutwardv2.ui.theme.*
import com.example.signoutwardv2.visitor.CameraPreview
import com.example.signoutwardv2.visitor.LineCrossingDetector
import com.example.signoutwardv2.visitor.PersonDetectionProcessor
import com.example.signoutwardv2.visitor.VisitorCountAggregator
import com.example.signoutwardv2.visitor.VisitorCountWorkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

/**
 * VisitorCounterScreen - Main screen for visitor counting feature
 * 
 * Features:
 * - Camera preview with person detection
 * - Real-time visitor count display
 * - Line crossing detection
 * - Reset count functionality
 * - No image/video storage (in-memory only)
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VisitorCounterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Camera permission
    val cameraPermissionState = rememberMultiplePermissionsState(
        listOf(android.Manifest.permission.CAMERA)
    )

    // Initialize person detection processor
    val personDetector = remember {
        PersonDetectionProcessor(context)
    }

    // Initialize line crossing detector
    val lineCrossingDetector = remember {
        LineCrossingDetector().apply {
            setEntryLine(0.5f) // Set entry line to middle of frame
        }
    }

    // Initialize visitor count aggregator (for hourly tracking and Supabase uploads)
    val countAggregator = remember {
        VisitorCountAggregator(context)
    }

    // Initialize WorkManager for hourly uploads
    val workManager = remember {
        VisitorCountWorkManager(context)
    }

    // Schedule hourly uploads on first launch
    LaunchedEffect(Unit) {
        workManager.scheduleHourlyUploads()
    }

    // Visitor count from line crossing detector (for display)
    val lineCrossingCount by lineCrossingDetector.getVisitorCount().collectAsState()
    
    // Current hour's count from aggregator (for Supabase)
    val aggregatedCount by countAggregator.getCurrentCount().collectAsState()
    
    // Display count (use line crossing count for UI, aggregator tracks for upload)
    val visitorCount = lineCrossingCount

    // Detected persons from person detector
    val detectedPersons by personDetector.getDetectedPersons().collectAsState()

    // Track previous count to detect increments
    var previousLineCrossingCount by remember { mutableStateOf(0) }

    // Process detections for line crossing
    LaunchedEffect(detectedPersons) {
        val persons = detectedPersons.map {
            PersonDetectionProcessor.DetectedPerson(
                id = it.id,
                centerX = it.centerX,
                centerY = it.centerY,
                width = it.width,
                height = it.height,
                confidence = it.confidence
            )
        }
        lineCrossingDetector.processDetections(persons)
    }

    // Sync line crossing count to aggregator (for hourly tracking)
    // When line crossing count increments, increment aggregator too
    LaunchedEffect(lineCrossingCount) {
        if (lineCrossingCount > previousLineCrossingCount) {
            // Count increased - increment aggregator
            val increment = lineCrossingCount - previousLineCrossingCount
            repeat(increment) {
                countAggregator.incrementCount()
            }
            previousLineCrossingCount = lineCrossingCount
        } else if (lineCrossingCount < previousLineCrossingCount) {
            // Count decreased (reset) - update previous count
            previousLineCrossingCount = lineCrossingCount
        }
    }

    // Handle camera permission
    if (!cameraPermissionState.allPermissionsGranted) {
        // Request camera permission
        LaunchedEffect(Unit) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
        
        // Show permission request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Camera Permission Required",
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Please grant camera permission to enable visitor counting",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Button(
                    onClick = {
                        scope.launch {
                            cameraPermissionState.launchMultiplePermissionRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary
                    )
                ) {
                    Text("Grant Permission", color = White)
                }
            }
        }
        return
    }

    // Main visitor counter UI
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview (background)
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            personDetector = personDetector
        )

        // Overlay UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Title and info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Visitor Counter",
                    color = White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Middle: Visitor count display (prominent)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Large count display
                    Card(
                        modifier = Modifier
                            .width(280.dp)
                            .height(280.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = PurplePrimary
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 12.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = visitorCount.toString(),
                                color = White,
                                fontSize = 120.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Label
                    Text(
                        text = "Visitors Today",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Bottom: Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        lineCrossingDetector.resetCount()
                        countAggregator.resetCount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Error
                    ),
                    modifier = Modifier
                        .width(200.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Reset Count",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

