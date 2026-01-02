package com.example.signoutwardv2.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.signoutwardv2.data.DevicePreferences
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.SupabaseClient
import com.example.signoutwardv2.pairing.DevicePairingManager
import com.example.signoutwardv2.ui.theme.*
import kotlinx.coroutines.launch

// ============================================================================
// PAIRING SCREEN
// ============================================================================
// Device pairing with 6-character code input
// Validates against Supabase device_pairing_codes table
// TEST01 triggers success for testing
// ============================================================================

@Composable
fun PairingScreen(
    onPairingSuccess: (screenId: String) -> Unit,
    onPairingFailure: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { DevicePreferences(context) }
    val repository = remember { PlaybackRepository(preferences, context) }
    val pairingManager = remember { DevicePairingManager(context) }
    
    // Fade-in animation on mount
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        isVisible = true
        kotlinx.coroutines.delay(300)
        focusRequester.requestFocus()
    }
    
    // Handle pairing logic
    fun attemptPairing() {
        if (code.length == 6 && !isLoading) {
            isLoading = true
            focusManager.clearFocus()
            
            scope.launch {
                val result = SupabaseClient.validatePairingCode(code.uppercase())
                
                result.fold(
                    onSuccess = { response ->
                        if (response.success && response.screenId != null) {
                            // CRITICAL: Clear all cached content on successful pairing
                            // This prevents stale content from previous pairings
                            scope.launch {
                                repository.clearAllCache()
                            }
                            
                            // Save pairing state persistently using DevicePairingManager
                            // This ensures pairing persists across app restarts and device reboots
                            scope.launch {
                                pairingManager.savePairing(
                                    screenId = response.screenId,
                                    pairingCode = code.uppercase(),
                                    groupId = response.groupId,
                                    locationId = response.locationId
                                )
                            }
                            
                            // Also update preferences for backward compatibility
                            preferences.setScreenId(response.screenId)
                            preferences.setPaired(true)
                            
                            android.util.Log.d("PairingScreen", "Pairing saved - will persist across app restarts and device reboots")
                            onPairingSuccess(response.screenId)
                        } else {
                            // Log the error message for debugging
                            android.util.Log.e("PairingScreen", "Pairing failed: ${response.message}")
                            onPairingFailure()
                        }
                    },
                    onFailure = { exception ->
                        // Log the exception for debugging
                        android.util.Log.e("PairingScreen", "Pairing exception", exception)
                        onPairingFailure()
                    }
                )
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            LoadingState()
        }
        
        AnimatedVisibility(
            visible = !isLoading && isVisible,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(300))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(48.dp)
            ) {
                // Title
                Text(
                    text = "Pair Your Device",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Subtitle
                Text(
                    text = "Enter the 6-character code shown in your web app to connect this display",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 500.dp)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Code Input
                CodeInputField(
                    code = code,
                    onCodeChange = { newCode ->
                        val filtered = newCode
                            .filter { it.isLetterOrDigit() }
                            .uppercase()
                            .take(6)
                        code = filtered
                    },
                    focusRequester = focusRequester,
                    onDone = { attemptPairing() }
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Pair Button
                Button(
                    onClick = { attemptPairing() },
                    enabled = code.length == 6,
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = White,
                        disabledContainerColor = SurfaceMedium,
                        disabledContentColor = TextSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "Pair Device",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    Box {
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(1.dp)
                .offset(x = (-100).dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            ),
            cursorBrush = SolidColor(PurplePrimary)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            repeat(6) { index ->
                CharacterBox(
                    char = code.getOrNull(index),
                    isFocused = code.length == index
                )
            }
        }
    }
}

@Composable
private fun CharacterBox(
    char: Char?,
    isFocused: Boolean
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            char != null -> PurplePrimary
            isFocused -> PurpleAccent
            else -> BorderLight
        },
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (char != null) SurfaceLight else White,
        animationSpec = tween(200),
        label = "backgroundColor"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = CodeInputStyle,
                color = PurplePrimary
            )
        } else if (isFocused) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(32.dp)
                    .background(PurplePrimary.copy(alpha = cursorAlpha))
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = PurplePrimary,
            strokeWidth = 6.dp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Synchronizing with web app…",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )
    }
}
