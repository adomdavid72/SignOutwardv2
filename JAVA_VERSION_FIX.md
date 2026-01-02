# Java Version Compatibility Issue

## Problem

Gradle/Kotlin is failing with:
```
java.lang.IllegalArgumentException: 25.0.1
	at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
```

**Root Cause**: Kotlin 2.0.21 doesn't properly parse Java 25.0.1 version string.

## Solutions

### Option 1: Install Java 17 or 21 (Recommended)

Install a Java version that Kotlin fully supports:

```bash
# Install Java 17 using Homebrew
brew install openjdk@17

# Set JAVA_HOME for this session
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Or add to ~/.zshrc for permanent
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
```

Then run tests:
```bash
./gradlew test
```

### Option 2: Update Kotlin Version

Update Kotlin to a version that supports Java 25:

```kotlin
// In gradle/libs.versions.toml
kotlin = "2.1.0"  // Or latest version
```

Then update dependencies and rebuild.

### Option 3: Use Android Studio's Embedded JDK

Android Studio includes a compatible JDK. Use it:

1. Open Android Studio
2. Go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
3. Set **Gradle JDK** to Android Studio's embedded JDK
4. Run tests from Android Studio

### Option 4: Use Gradle Toolchain (If Available)

If Gradle 8.13 supports Java toolchains, configure it in `build.gradle.kts`:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

However, this may not work if Java 17 isn't installed.

## Quick Fix for Testing

If you need to run tests immediately and have Java 17/21 available:

```bash
# Find Java 17/21 installation
/usr/libexec/java_home -V

# Set JAVA_HOME temporarily
export JAVA_HOME=$(/usr/libexec/java_home -v 17)  # or -v 21

# Run tests
./gradlew test
```

## Verification

After applying a fix, verify:

```bash
java -version  # Should show Java 17 or 21
./gradlew test --info  # Should not show Java version parsing errors
```

## Current Status

- **Java Version**: 25.0.1 (not fully supported by Kotlin 2.0.21)
- **Kotlin Version**: 2.0.21
- **Project Target**: Java 11
- **Status**: ⚠️ Tests cannot run until Java version issue is resolved

