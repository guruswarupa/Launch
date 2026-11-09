# Development Guide

This guide provides detailed instructions for setting up and developing Launch.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Initial Setup](#initial-setup)
- [Project Structure](#project-structure)
- [Build Configuration](#build-configuration)
- [Running the App](#running-the-app)
- [Testing](#testing)
- [Debugging](#debugging)
- [Building Releases](#building-releases)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software

1. **Android Studio**
   - Version: Hedgehog (2023.1.1) or later
   - Download: [developer.android.com/studio](https://developer.android.com/studio)

2. **JDK (Java Development Kit)**
   - Version: JDK 11 or higher
   - Android Studio includes JDK, or install separately

3. **Android SDK**
   - Minimum SDK: 24 (Android 7.0)
   - Target SDK: 35 (Android 15)
   - Install via Android Studio SDK Manager

4. **Git**
   - For version control
   - Download: [git-scm.com](https://git-scm.com/)

### System Requirements

- **OS**: Windows, macOS, or Linux
- **RAM**: 8GB minimum, 16GB recommended
- **Disk Space**: At least 10GB free space

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/guruswarupa/launch.git
cd launch
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Select **File → Open**
3. Navigate to the `launch` directory
4. Click **OK**

### 3. Sync Gradle

Android Studio should automatically sync Gradle. If not:

1. Click **File → Sync Project with Gradle Files**
2. Wait for dependencies to download (first sync may take several minutes)

### 4. Configure SDK

1. Go to **File → Project Structure**
2. Under **SDK Location**, verify:
   - Android SDK location is set
   - JDK location is set (use embedded JDK or your installed JDK)

### 5. Install Required SDK Components

1. Go to **Tools → SDK Manager**
2. Install:
   - Android SDK Platform 35
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android Emulator (if using emulator)

## Project Structure

```
launch/
├── app/                                    # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/guruswarupa/launch/
│   │   │   │   ├── MainActivity.kt        # Main launcher activity
│   │   │   │   ├── SettingsActivity.kt     # Settings screen
│   │   │   │   ├── AppDockManager.kt      # Dock management
│   │   │   │   ├── AppLockManager.kt      # App locking
│   │   │   │   └── ...                    # Other components
│   │   │   ├── res/                       # Resources
│   │   │   │   ├── layout/                # XML layouts
│   │   │   │   ├── drawable/              # Icons and graphics
│   │   │   │   ├── values/                # Strings, colors, styles
│   │   │   │   └── ...
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/                   # Instrumented tests
│   │   └── test/                          # Unit tests
│   ├── build.gradle.kts                   # App build configuration
│   └── proguard-rules.pro                 # ProGuard rules
├── gradle/
│   ├── libs.versions.toml                 # Dependency versions
│   └── wrapper/                           # Gradle wrapper
├── build.gradle.kts                       # Project build config
├── settings.gradle.kts                    # Project settings
└── gradle.properties                      # Gradle properties
```

### Modules

Launch ships as one APK/AAB built from several Gradle modules:

| Module | What it holds |
|---|---|
| `:app` | The launcher itself, plus Files, Gallery, Video Player and the document/photo viewers |
| `:shared` | Shared UI helpers (`FilesPalette`, `FilesDialogs`, pickers, month grid), theme attributes, `Constants`, unit and currency converters, contact lookup |
| `:feature-calculator` | Calculator and converter |
| `:feature-calendar` | Calendar |
| `:feature-clock` | Alarms, timer, stopwatch, world clock |
| `:feature-phone` | Phone (keypad, recents, in-call screen), Contacts |
| `:feature-messages` | Messages (SMS and MMS) |
| `:feature-camera` | Camera |

Each feature module declares its own activities, services and receivers in its own `AndroidManifest.xml` and owns the strings and drawables only it uses (all translations included). Anything used by two or more modules lives in `:shared`. Feature modules depend on `:shared`, never on `:app` or on each other. Themes stay in `:app`; a feature activity gets its theme through a `tools:node="merge"` entry in the app manifest.

## Build Configuration

### Key Configuration Files

#### `gradle/libs.versions.toml`

Contains all dependency versions. Update versions here rather than in individual `build.gradle.kts` files.

#### `app/build.gradle.kts`

App-level build configuration:
- Application ID: `com.guruswarupa.launchh`
- Min SDK: 24
- Target SDK: 35
- Compile SDK: 35

#### `gradle.properties`

Project-wide Gradle settings:
- JVM arguments
- AndroidX usage
- Kotlin code style

## Running the App

### On an Emulator

1. **Create an AVD (Android Virtual Device)**:
   - Tools → Device Manager
   - Create Device → Select device → Next
   - Select system image (API 24+) → Next → Finish

2. **Run the app**:
   - Click the green **Run** button (▶)
   - Or press `Shift + F10` (Windows/Linux) or `Ctrl + R` (macOS)
   - Select your emulator/device

### On a Physical Device

1. **Enable Developer Options**:
   - Settings → About Phone → Tap "Build Number" 7 times

2. **Enable USB Debugging**:
   - Settings → Developer Options → Enable "USB Debugging"

3. **Connect Device**:
   - Connect via USB
   - Accept debugging prompt on device

4. **Run the app**:
   - Click Run in Android Studio
   - Select your device

### Using Gradle

```bash
# Install debug build
./gradlew installDebug

# Run tests
./gradlew test

# Build debug APK
./gradlew assembleDebug
```

## Testing

### Unit Tests

Located in `app/src/test/`:

```bash
./gradlew test
```

### Instrumented Tests

Located in `app/src/androidTest/`:

```bash
./gradlew connectedAndroidTest
```

### Running Tests in Android Studio

1. Right-click on test file or method
2. Select **Run 'TestName'**
3. View results in Run window

## Debugging

### Using Android Studio Debugger

1. Set breakpoints by clicking left margin
2. Click **Debug** button (🐛) or press `Shift + F9`
3. Use debugger controls:
   - Step Over (F8)
   - Step Into (F7)
   - Step Out (Shift + F8)
   - Resume (F9)

### Logcat

View logs in Android Studio:

1. Open **Logcat** tab at bottom
2. Filter by package: `com.guruswarupa.launch`
3. Use log levels: Verbose, Debug, Info, Warn, Error

### Common Debugging Tips

- Use `Log.d()`, `Log.e()`, etc. for logging
- Check logcat for errors
- Use breakpoints to inspect variables
- Use Android Studio's Layout Inspector for UI debugging

## Building Releases

### Debug Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

1. **Configure signing** (if not already done):
   - Create keystore file
   - Add signing config to `app/build.gradle.kts`

2. **Build release**:
   ```bash
   ./gradlew assembleRelease
   ```

3. **Output**: `app/build/outputs/apk/release/app-release.apk`

### Build Variants

Switch between debug and release in Android Studio:
- Build → Select Build Variant → Choose variant

## Troubleshooting

### Gradle Sync Fails

**Problem**: Gradle sync errors

**Solutions**:
- Check internet connection
- Invalidate caches: File → Invalidate Caches / Restart
- Delete `.gradle` folder and re-sync
- Check `gradle.properties` for correct settings

### Build Errors

**Problem**: Build fails with errors

**Solutions**:
- Clean project: Build → Clean Project
- Rebuild: Build → Rebuild Project
- Check SDK versions match requirements
- Update Gradle wrapper if needed

### App Crashes on Launch

**Problem**: App crashes immediately

**Solutions**:
- Check logcat for error messages
- Verify all permissions are requested
- Check if device meets minimum SDK (24)
- Try on different device/emulator

### Dependency Issues

**Problem**: Cannot resolve dependencies

**Solutions**:
- Check internet connection
- Verify repositories in `settings.gradle.kts`
- Try: File → Invalidate Caches / Restart
- Delete `.gradle` and `build` folders, re-sync

### Emulator Issues

**Problem**: Emulator won't start or is slow

**Solutions**:
- Enable hardware acceleration (HAXM/Intel HAXM)
- Increase emulator RAM in AVD settings
- Use x86/x86_64 system images (faster than ARM)
- Consider using physical device for testing

## Additional Resources

- [Android Developer Documentation](https://developer.android.com/docs)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Gradle User Guide](https://docs.gradle.org/current/userguide/userguide.html)

## Getting Help

If you encounter issues not covered here:

1. Check existing [GitHub Issues](https://github.com/guruswarupa/launch/issues)
2. Search closed issues for similar problems
3. Create a new issue with:
   - Detailed error message
   - Steps to reproduce
   - Environment details (OS, Android Studio version, etc.)

Happy coding! 🚀
