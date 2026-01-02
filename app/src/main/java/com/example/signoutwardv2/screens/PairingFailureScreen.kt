package com.example.signoutwardv2.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signoutwardv2.ui.theme.*
import kotlinx.coroutines.delay

// ============================================================================
// PAIRING FAILURE SCREEN
// ============================================================================
// Displayed when pairing fails (any code except TEST01)
// - Error icon with animation
// - Clear error message
// - Retry button to return to pairing
// ============================================================================

@Composable
fun PairingFailureScreen(
    onRetry: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(500)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(500)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(48.dp)
            ) {
                // Error Icon
                ErrorIcon()
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Title
                Text(
                    text = "Pairing Failed",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Message
                Text(
                    text = "Unable to synchronize with the web app.\nPlease try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 400.dp)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Retry Button
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Text(
                        text = "Retry Pairing",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorIcon() {
    // Shake animation
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                0f at 0
                -4f at 100
                4f at 200
                -4f at 300
                4f at 400
                0f at 500
                0f at 2000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "offsetX"
    )
    
    Box(
        modifier = Modifier
            .offset(x = offsetX.dp)
            .size(120.dp)
            .clip(CircleShape)
            .background(Error.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Error.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕",
                fontSize = 40.sp,
                color = Error
            )
        }
    }
}

