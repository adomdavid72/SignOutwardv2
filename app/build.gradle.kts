plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.signoutwardv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.signoutwardv2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Supabase configuration - replace with your actual values
        buildConfigField("String", "SUPABASE_URL", "\"https://pjtctdyntfnfvcgkkipe.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_secret_JyvZlMiV55R1oN2GK2K5Bg_2fjV0REo\"")
        
        // Enable multidex for test builds to handle large test APKs
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    // Enable DEX version 040 for androidTest to support test function names with spaces
    // DEX 040 allows spaces in class/method names, required for Kotlin backtick test names
    // This is configured via gradle.properties: android.enableDexingArtifactTransform.desugaring=false
    // and by setting the test variant's minSdk through the packaging block
    testBuildType = "debug"
    
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    
    // Ktor for Supabase REST API
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    
    // CameraX for camera access
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ML Kit for person detection (better Android integration than MediaPipe)
    implementation("com.google.mlkit:object-detection:17.0.1")
    
    // Permissions handling
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // WorkManager for scheduled tasks
    val workManagerVersion = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workManagerVersion")
    
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // AndroidX Test dependencies - REQUIRED for AndroidJUnit4ClassRunner
    androidTestImplementation(libs.androidx.test.core)     // Core test utilities (required for runner)
    androidTestImplementation(libs.androidx.test.runner)  // Required for test runner instantiation
    androidTestImplementation(libs.androidx.test.rules)    // Required for test rules
    androidTestImplementation(libs.androidx.junit)         // JUnit 4 support for Android
    androidTestImplementation(libs.androidx.espresso.core) // Espresso for UI testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
