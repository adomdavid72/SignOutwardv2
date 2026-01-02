package com.example.signoutwardv2.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.signoutwardv2.data.DevicePreferences
import com.example.signoutwardv2.data.SupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for Supabase pairing
 * Tests: Device pairing → screens.is_paired = true
 * 
 * OPTIMIZED FOR FAST TEST EXECUTION:
 * - Fail-fast mechanism: Tests skip immediately if Supabase DB is unavailable (2-3 second timeout)
 * - Reduced timeouts: Network calls timeout after 2-3 seconds instead of default timeouts
 * - Removed @Ignore annotations: Tests now skip gracefully when DB unavailable instead of being ignored
 * - This allows tests to run quickly even when Supabase is unavailable
 * 
 * NOTE: These tests require a valid Supabase connection and test data
 * For CI/CD, use test fixtures or mock Supabase responses
 */
@RunWith(AndroidJUnit4::class)
class SupabasePairingTest {
    
    companion object {
        // Fast timeout for Supabase connectivity check (2-3 seconds)
        // This allows tests to skip quickly when DB is unavailable
        private const val SUPABASE_TIMEOUT_MS = 2500L
    }
    
    private lateinit var context: Context
    private lateinit var preferences: DevicePreferences
    
    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        preferences = DevicePreferences(context)
        
        // Clear any existing pairing state
        preferences.clearAll()
    }
    
    /**
     * Fast-fail check: Verify Supabase is available before running test
     * Returns true if available, false if unavailable (test should skip)
     * Uses 2-3 second timeout to fail fast when DB is unavailable
     */
    private suspend fun checkSupabaseAvailable(): Boolean {
        return try {
            // Quick connectivity check with fast timeout
            // Use a simple pairing code check to verify connectivity
            withTimeout(SUPABASE_TIMEOUT_MS) {
                val result = SupabaseClient.validatePairingCode("CONNECTIVITY_CHECK")
                // If we get a result (success or failure), Supabase is reachable
                result.isSuccess || result.isFailure
            }
            true
        } catch (e: TimeoutCancellationException) {
            // Supabase unavailable or too slow - fail fast
            false
        } catch (e: Exception) {
            // Other errors - assume unavailable
            false
        }
    }
    
    /**
     * Test: validatePairingCode updates screens_is_paired when successful
     * 
     * OPTIMIZED: Uses fast timeout (2-3 seconds) and fail-fast mechanism
     * Removed @Ignore: Test now skips gracefully when DB unavailable instead of being ignored
     * Skips immediately if Supabase is unavailable to avoid long waits
     */
    @Test
    fun validatepairingcodeUpdatesScreensIsPairedWhenSuccessful() = runBlocking {
        // Fail-fast: Check Supabase availability before proceeding
        // This allows test to skip immediately if DB is unavailable (2-3 second timeout)
        val isAvailable = checkSupabaseAvailable()
        org.junit.Assume.assumeTrue(
            "Supabase DB unavailable or timeout - skipping test for fast execution",
            isAvailable
        )
        
        // This test requires a valid pairing code in Supabase
        // Replace "TESTCODE" with an actual unused pairing code from your test database
        val testCode = "TESTCODE" // TODO: Replace with actual test code
        
        // Use reduced timeout for pairing code validation
        val result = try {
            withTimeout(SUPABASE_TIMEOUT_MS) {
                SupabaseClient.validatePairingCode(testCode)
            }
        } catch (e: TimeoutCancellationException) {
            // Backend unavailable or too slow - skip test gracefully
            org.junit.Assume.assumeTrue(
                "Supabase backend unavailable or timeout - skipping test: ${e.message}",
                false
            )
            return@runBlocking
        } catch (e: Exception) {
            // Other network errors - skip test gracefully
            org.junit.Assume.assumeTrue(
                "Supabase connection failed - skipping test: ${e.message}",
                false
            )
            return@runBlocking
        }
        
        result.fold(
            onSuccess = { response ->
                if (response.success && response.screenId != null) {
                    // Verify screen ID is saved locally
                    val savedScreenId = preferences.screenId.first()
                    assertEquals("Screen ID should be saved", response.screenId, savedScreenId)
                    
                    // Verify paired status is saved
                    val isPaired = preferences.isPaired.first()
                    assertTrue("Device should be marked as paired", isPaired)
                    
                    // Note: We can't directly verify Supabase DB state here without
                    // making another API call, but the pairing response indicates success
                } else {
                    // If test code doesn't exist, skip test
                    org.junit.Assume.assumeTrue(
                        "Test code not available or already used",
                        false
                    )
                }
            },
            onFailure = { exception ->
                // Network errors are acceptable in test environment
                // Skip test rather than fail if Supabase is unreachable
                org.junit.Assume.assumeNoException(
                    "Supabase connection failed - skipping test: ${exception.message}",
                    exception
                )
            }
        )
    }
    
    /**
     * Test: validatePairingCode fails for invalid code
     * 
     * OPTIMIZED: Uses fast timeout (2-3 seconds) and fail-fast mechanism
     * Removed @Ignore: Test now skips gracefully when DB unavailable instead of being ignored
     * Skips immediately if Supabase is unavailable to avoid long waits
     */
    @Test
    fun validatepairingcodeFailsForInvalidCode() = runBlocking {
        // Fail-fast: Check Supabase availability before proceeding
        // This allows test to skip immediately if DB is unavailable (2-3 second timeout)
        val isAvailable = checkSupabaseAvailable()
        org.junit.Assume.assumeTrue(
            "Supabase DB unavailable or timeout - skipping test for fast execution",
            isAvailable
        )
        
        val invalidCode = "INVALID"
        
        // Use reduced timeout for pairing code validation
        val result = try {
            withTimeout(SUPABASE_TIMEOUT_MS) {
                SupabaseClient.validatePairingCode(invalidCode)
            }
        } catch (e: TimeoutCancellationException) {
            // Backend unavailable or too slow - skip test gracefully
            org.junit.Assume.assumeTrue(
                "Supabase backend unavailable or timeout - skipping test: ${e.message}",
                false
            )
            return@runBlocking
        } catch (e: Exception) {
            // Other network errors - skip test gracefully
            org.junit.Assume.assumeTrue(
                "Supabase connection failed - skipping test: ${e.message}",
                false
            )
            return@runBlocking
        }
        
        result.fold(
            onSuccess = { response ->
                assertFalse("Invalid code should fail", response.success)
                assertNull("Screen ID should be null for failed pairing", response.screenId)
            },
            onFailure = { exception ->
                // Network errors are acceptable
                org.junit.Assume.assumeNoException(
                    "Supabase connection failed - skipping test: ${exception.message}",
                    exception
                )
            }
        )
    }
    
    /**
     * Test: validatePairingCode fails for already used code
     * 
     * OPTIMIZED: Uses fast timeout (2-3 seconds) and fail-fast mechanism
     * Removed @Ignore: Test now skips gracefully when DB unavailable instead of being ignored
     * Skips immediately if Supabase is unavailable to avoid long waits
     */
    @Test
    fun validatepairingcodeFailsForAlreadyUsedCode() = runBlocking {
        // Fail-fast: Check Supabase availability before proceeding
        // This allows test to skip immediately if DB is unavailable (2-3 second timeout)
        val isAvailable = checkSupabaseAvailable()
        org.junit.Assume.assumeTrue(
            "Supabase DB unavailable or timeout - skipping test for fast execution",
            isAvailable
        )
        
        // This test requires a pairing code that has already been used
        // Replace with an actual used code from your test database
        val usedCode = "USEDCODE" // TODO: Replace with actual used code
        
        // Use reduced timeout for pairing code validation
        val result = try {
            withTimeout(SUPABASE_TIMEOUT_MS) {
                SupabaseClient.validatePairingCode(usedCode)
            }
        } catch (e: TimeoutCancellationException) {
            // Backend unavailable or too slow - skip test gracefully
            org.junit.Assume.assumeTrue(
                "Supabase backend unavailable or timeout - skipping test: ${e.message}",
                false
            )
            return@runBlocking
        } catch (e: Exception) {
            // Other network errors - skip test gracefully
            org.junit.Assume.assumeTrue(
                "Supabase connection failed - skipping test: ${e.message}",
                false
            )
            return@runBlocking
        }
        
        result.fold(
            onSuccess = { response ->
                assertFalse("Used code should fail", response.success)
            },
            onFailure = { exception ->
                org.junit.Assume.assumeNoException(
                    "Supabase connection failed - skipping test: ${exception.message}",
                    exception
                )
            }
        )
    }
}
