package com.example.signoutwardv2.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.signoutwardv2.pairing.DevicePairingManager
import com.example.signoutwardv2.screens.*
import kotlinx.coroutines.flow.first

// ============================================================================
// NAVIGATION - App Routes
// ============================================================================

sealed class Screen(val route: String) {
    object Pairing : Screen("pairing")
    object PairingFailure : Screen("pairing_failure")
    object PairingSuccess : Screen("pairing_success/{screenId}") {
        fun createRoute(screenId: String) = "pairing_success/$screenId"
    }
    object AdStreaming : Screen("ad_streaming?screenId={screenId}") {
        fun createRoute(screenId: String?) = 
            if (screenId != null) "ad_streaming?screenId=$screenId" 
            else "ad_streaming"
    }
    object VisitorCounter : Screen("visitor_counter")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val pairingManager = remember { DevicePairingManager(context) }
    
    // Check pairing state on startup
    var pairingChecked by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Check if device is already paired
        val isPaired = pairingManager.isPaired()
        
        if (isPaired) {
            // Device is already paired - skip pairing and go directly to playback
            val screenId = pairingManager.getScreenId()
            Log.d("AppNavigation", "Device already paired - screenId: $screenId")
            Log.d("AppNavigation", "Skipping pairing workflow - navigating directly to playback")
            
            // Navigate to playback screen
            navController.navigate(
                Screen.AdStreaming.createRoute(screenId)
            ) {
                // Clear back stack so user can't go back to pairing
                popUpTo(0) { inclusive = true }
            }
        } else {
            // Device not paired - show pairing screen
            Log.d("AppNavigation", "Device not paired - showing pairing screen")
        }
        
        pairingChecked = true
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Pairing.route,
        enterTransition = {
            fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(400))
        }
    ) {
        composable(Screen.Pairing.route) {
            PairingScreen(
                onPairingSuccess = { screenId ->
                    navController.navigate(Screen.PairingSuccess.createRoute(screenId)) {
                        popUpTo(Screen.Pairing.route) { inclusive = true }
                    }
                },
                onPairingFailure = {
                    navController.navigate(Screen.PairingFailure.route)
                }
            )
        }
        
        composable(Screen.PairingFailure.route) {
            PairingFailureScreen(
                onRetry = {
                    navController.navigate(Screen.Pairing.route) {
                        popUpTo(Screen.PairingFailure.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.PairingSuccess.route,
            arguments = listOf(navArgument("screenId") { type = NavType.StringType })
        ) { backStackEntry ->
            val screenId = backStackEntry.arguments?.getString("screenId") ?: ""
            PairingSuccessScreen(
                onContinue = {
                    navController.navigate(Screen.AdStreaming.createRoute(screenId)) {
                        popUpTo(Screen.PairingSuccess.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.AdStreaming.route,
            arguments = listOf(
                navArgument("screenId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val screenId = backStackEntry.arguments?.getString("screenId")
            AdStreamingScreen(screenId = screenId)
        }
        
        composable(Screen.VisitorCounter.route) {
            VisitorCounterScreen()
        }
    }
}
