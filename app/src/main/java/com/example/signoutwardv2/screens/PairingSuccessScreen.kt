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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signoutwardv2.ui.theme.*
import kotlinx.coroutines.delay

// ============================================================================
// PAIRING SUCCESS SCREEN
// ============================================================================
// Displayed when TEST01 is entered successfully
// - Animated checkmark icon with scale-in
// - Success message
// - Continue button to proceed to ad streaming
// ============================================================================

@Composable
fun PairingSuccessScreen(
    onContinue: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var showCheckmark by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
        delay(300)
        showCheckmark = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(500))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(48.dp)
            ) {
                // Success Checkmark
                SuccessCheckmark(visible = showCheckmark)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Title
                Text(
                    text = "Device Paired Successfully",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Subtitle
                Text(
                    text = "Your display is now connected and ready to show content",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 400.dp)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Continue Button
                Button(
                    onClick = onContinue,
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
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessCheckmark(visible: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmarkScale"
    )
    
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale * pulseScale)
            .size(120.dp)
            .clip(CircleShape)
            .background(PurplePrimary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PurplePrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                fontSize = 40.sp,
                color = White
            )
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

