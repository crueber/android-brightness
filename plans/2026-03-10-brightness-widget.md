# Brightness Widget — Implementation Plan

**Date**: 2026-03-10  
**Research**: `research/2026-03-10-android-brightness-widget.md`

---

## Overview

Build a minimal Android home screen widget that displays a horizontal tap-zone brightness bar. Tapping any segment of the bar sets the screen brightness to that level and disables auto-brightness. A small companion launcher activity handles the one-time `WRITE_SETTINGS` permission grant flow. The app is distributed as a signed APK via GitHub Releases, compatible with Obtainium.

---

## Current State Analysis

This is a greenfield project. No existing code. The research document confirms:
- No suitable existing open-source app exists to fork or build on
- The platform constraint (no `SeekBar` in `RemoteViews`) means tap-zones are the correct interaction model
- Kotlin + Jetpack Glance is the only viable non-Java path with full tooling support

---

## Desired End State

A user can:
1. Install the APK (via Obtainium pointing at GitHub Releases)
2. Open the app launcher icon, see a simple permission screen, tap a button to go grant `WRITE_SETTINGS`, return to the app and see confirmation that permission is granted
3. Add the widget to their home screen from the widget picker
4. See a horizontal brightness bar showing current brightness level as filled/unfilled segments
5. Tap any segment to instantly set brightness to that level (auto-brightness is disabled as a side effect)
6. Resize the widget horizontally to any width; the bar fills the available space
7. Receive updated APKs automatically via Obtainium when new GitHub Releases are published

### Verification:
- Widget appears in the Android widget picker
- Tapping segments changes screen brightness visibly and immediately
- Auto-brightness is disabled after first tap
- Widget reflects current brightness level on load
- Widget resizes horizontally without breaking layout
- Permission screen correctly detects granted/not-granted state
- GitHub Actions produces a signed APK attached to a release tag

---

## What We're NOT Doing

- No drag-to-adjust slider (platform limitation — `RemoteViews` does not support `SeekBar`)
- No notification panel tile (Quick Settings tile)
- No brightness scheduling or automation
- No per-app brightness profiles
- No dark mode / theming options
- No settings screen beyond the permission grant flow
- No widget configuration screen (no `AppWidgetConfigure` activity)
- No support below Android 8.0 (API 26)
- No iOS / cross-platform support

---

## Implementation Approach

Four phases, each independently testable:

1. **Project scaffold** — Android Studio project, dependencies, manifest, signing config
2. **Permission activity** — launcher icon, permission detection, settings deep-link button
3. **Widget core** — Glance widget with tap-zone bar, brightness read/write, resize support
4. **CI/CD** — GitHub Actions workflow, keystore secrets, Obtainium-compatible releases

---

## Phase 1: Project Scaffold

### Overview
Create the Android Studio project with the correct structure, dependencies, manifest entries, and build configuration. No functional code yet — just a compiling, installable skeleton.

### Changes Required

#### 1. Android Studio Project Creation
**Action**: New Project > **No Activity** template  
**Settings**:
- Name: `Brightness Widget`
- Package name: `us.packden.brightnesswidget` (adjust to your domain)
- Save location: this repository root
- Language: **Kotlin**
- Minimum SDK: **API 26 (Android 8.0)**
- Build configuration language: **Kotlin DSL** (`.kts` files)

#### 2. `app/build.gradle.kts`
**File**: `app/build.gradle.kts`  
**Changes**: Set SDK versions, add Glance dependencies

```kotlin
android {
    namespace = "us.packden.brightnesswidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "us.packden.brightnesswidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
    // Needed for the permission activity
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
```

#### 3. `app/src/main/AndroidManifest.xml`
**File**: `app/src/main/AndroidManifest.xml`  
**Changes**: Declare permission, launcher activity, and widget receiver

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Required to read and write screen brightness -->
    <uses-permission android:name="android.permission.WRITE_SETTINGS" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.BrightnessWidget">

        <!-- Launcher activity: permission grant flow -->
        <activity
            android:name=".PermissionActivity"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Widget receiver -->
        <receiver
            android:name=".BrightnessWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/brightness_widget_info" />
        </receiver>

    </application>
</manifest>
```

#### 4. Widget metadata XML
**File**: `app/src/main/res/xml/brightness_widget_info.xml`  
**Changes**: New file — defines widget size, resize behavior, and preview

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="146dp"
    android:minHeight="50dp"
    android:minResizeWidth="73dp"
    android:minResizeHeight="50dp"
    android:maxResizeWidth="2048dp"
    android:resizeMode="horizontal"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:description="@string/widget_description"
    android:previewImage="@drawable/widget_preview" />
```

#### 5. String resources
**File**: `app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">Brightness Widget</string>
    <string name="widget_description">Tap to set screen brightness</string>
    <string name="permission_title">One Permission Needed</string>
    <string name="permission_explanation">Brightness Widget needs permission to modify system settings in order to control your screen brightness.\n\nTap the button below, find \"Brightness Widget\" in the list, and enable the toggle.</string>
    <string name="permission_granted">Permission granted. You can now add the widget to your home screen.</string>
    <string name="open_settings_button">Open Settings to Grant Permission</string>
    <string name="permission_granted_label">Permission Granted</string>
</resources>
```

#### 6. Placeholder files (stubs to make the project compile)
Create empty stub files for the classes declared in the manifest:
- `app/src/main/kotlin/us/packden/brightnesswidget/PermissionActivity.kt` — empty `AppCompatActivity` subclass
- `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessWidgetReceiver.kt` — empty `GlanceAppWidgetReceiver` subclass
- `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessWidget.kt` — empty `GlanceAppWidget` subclass

### Success Criteria

#### Automated Verification:
- [ ] Project builds without errors: `./gradlew assembleDebug`
- [ ] No lint errors on manifest or resources: `./gradlew lint`

#### Manual Verification:
- [ ] APK installs on a device or emulator (API 26+)
- [ ] App icon appears in the launcher
- [ ] Widget appears in the widget picker (even if it shows nothing yet)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the APK installs and the widget appears in the picker before proceeding to Phase 2.

---

## Phase 2: Permission Activity

### Overview
Implement the launcher activity that detects whether `WRITE_SETTINGS` has been granted, shows a clear explanation if not, and provides a button that deep-links directly to the correct system settings page. When the user returns from settings, the screen updates to show a confirmation.

### Changes Required

#### 1. `PermissionActivity.kt`
**File**: `app/src/main/kotlin/us/packden/brightnesswidget/PermissionActivity.kt`

```kotlin
package us.packden.brightnesswidget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class PermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PermissionScreen()
            }
        }
    }
}

@Composable
fun PermissionScreen() {
    val context = LocalContext.current

    // Re-check permission every time the activity resumes (user may have just granted it)
    var hasPermission by remember { mutableStateOf(Settings.System.canWrite(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.System.canWrite(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasPermission) {
                // Granted state
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.permission_granted),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                // Not-granted state
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.permission_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.open_settings_button))
                }
            }
        }
    }
}
```

#### 2. Additional Material Icons dependency
**File**: `app/build.gradle.kts` — add to dependencies:
```kotlin
implementation("androidx.compose.material:material-icons-extended:1.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
```

### Success Criteria

#### Automated Verification:
- [ ] Project builds without errors: `./gradlew assembleDebug`

#### Manual Verification:
- [ ] Opening the app shows the "One Permission Needed" screen when permission is not yet granted
- [ ] Tapping the button navigates directly to the correct system settings page for this app
- [ ] After granting permission in settings and returning to the app, the screen updates to show the "Permission Granted" confirmation without needing to restart the app
- [ ] If permission is already granted when the app is opened, the granted confirmation is shown immediately (no button)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the permission flow works end-to-end before proceeding to Phase 3.

---

## Phase 3: Widget Core

### Overview
Implement the Glance widget: a horizontal tap-zone brightness bar that reads current brightness on load, renders filled/unfilled segments, and sets brightness (disabling auto-brightness) when a segment is tapped. The number of steps is a single named constant so it can be changed in one place.

### Changes Required

#### 1. `BrightnessConfig.kt` — single source of truth for tunables
**File**: `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessConfig.kt`

```kotlin
package us.packden.brightnesswidget

/**
 * Central configuration for the brightness widget.
 * Change BRIGHTNESS_STEPS to adjust granularity:
 *   10 = 10% increments (larger tap targets, coarser control)
 *   20 = 5% increments (smaller tap targets, finer control)
 */
object BrightnessConfig {
    const val BRIGHTNESS_STEPS = 10
    const val SEGMENT_GAP_DP = 2   // gap between segments in dp
    const val SEGMENT_HEIGHT_DP = 48 // height of the bar in dp
}
```

#### 2. `SetBrightnessAction.kt` — ActionCallback that writes brightness
**File**: `app/src/main/kotlin/us/packden/brightnesswidget/SetBrightnessAction.kt`

```kotlin
package us.packden.brightnesswidget

import android.content.Context
import android.provider.Settings
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

val brightnessStepKey = ActionParameters.Key<Int>("brightness_step")

class SetBrightnessAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val step = parameters[brightnessStepKey] ?: return
        val steps = BrightnessConfig.BRIGHTNESS_STEPS

        // Convert step (1..steps) to Android brightness value (1..255)
        // Use 1 as minimum (not 0) to avoid a completely black screen
        val brightnessValue = ((step.toFloat() / steps) * 255).toInt().coerceIn(1, 255)

        // Disable auto-brightness so the manual value sticks
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )

        // Set the brightness
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue
        )

        // Refresh all instances of this widget
        BrightnessWidget().updateAll(context)
    }
}
```

#### 3. `BrightnessWidget.kt` — Glance widget with tap-zone bar
**File**: `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessWidget.kt`

```kotlin
package us.packden.brightnesswidget

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.unit.ColorProvider

class BrightnessWidget : GlanceAppWidget() {

    // SizeMode.Exact: recompose whenever the user resizes the widget
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            BrightnessBar(context)
        }
    }
}

@Composable
fun BrightnessBar(context: Context) {
    val steps = BrightnessConfig.BRIGHTNESS_STEPS
    val gapDp = BrightnessConfig.SEGMENT_GAP_DP
    val heightDp = BrightnessConfig.SEGMENT_HEIGHT_DP

    // Read current brightness (0–255); default to midpoint if unreadable
    val rawBrightness = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Settings.SettingNotFoundException) {
        128
    }

    // Which step is currently active (1..steps)?
    val activeStep = ((rawBrightness / 255f) * steps).toInt().coerceIn(0, steps)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .padding(4.dp),
        horizontalAlignment = Alignment.Horizontal.Start
    ) {
        for (step in 1..steps) {
            val isFilled = step <= activeStep
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(horizontal = (gapDp / 2).dp)
                    .background(
                        ColorProvider(
                            if (isFilled) Color(0xFFFFFFFF) else Color(0xFF444444)
                        )
                    )
                    .cornerRadius(4.dp)
                    .clickable(
                        actionRunCallback<SetBrightnessAction>(
                            actionParametersOf(brightnessStepKey to step)
                        )
                    )
            )
        }
    }
}
```

#### 4. `BrightnessWidgetReceiver.kt`
**File**: `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessWidgetReceiver.kt`

```kotlin
package us.packden.brightnesswidget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class BrightnessWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrightnessWidget()
}
```

#### 5. Widget preview image
**File**: `app/src/main/res/drawable/widget_preview.png`  
**Action**: Create a simple PNG (e.g., 320×80px) showing a horizontal bar with some segments filled and some empty. This is shown in the widget picker. Can be a screenshot of the running widget once Phase 3 is complete, or a hand-drawn placeholder for now.

### Success Criteria

#### Automated Verification:
- [ ] Project builds without errors: `./gradlew assembleDebug`
- [ ] No lint errors: `./gradlew lint`

#### Manual Verification:
- [ ] Widget appears in the widget picker with the correct name and preview image
- [ ] After adding the widget to the home screen, it displays a brightness bar reflecting the current brightness level
- [ ] Tapping the leftmost segment sets brightness to the lowest non-zero level (visibly dim)
- [ ] Tapping the rightmost segment sets brightness to maximum (visibly bright)
- [ ] Tapping a middle segment sets brightness to approximately that fraction of maximum
- [ ] After tapping any segment, auto-brightness is disabled (verify in Settings > Display > Adaptive/Auto Brightness)
- [ ] Resizing the widget horizontally causes the bar to fill the new width correctly
- [ ] The filled/unfilled state of segments correctly reflects the current brightness after each tap

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the widget works correctly end-to-end before proceeding to Phase 4.

---

## Phase 4: CI/CD and Distribution

### Overview
Set up GitHub Actions to build and sign a release APK on every version tag push, attach it to a GitHub Release, and make it trackable by Obtainium.

### Changes Required

#### 1. Keystore generation (one-time, local)
**Action**: Run locally, keep the `.jks` file safe — never commit it.

```bash
keytool -genkey -v \
  -keystore brightness-widget.jks \
  -alias brightness-key \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -dname "CN=Brightness Widget, O=packden, C=US"
```

Encode for GitHub secrets:
```bash
openssl base64 < brightness-widget.jks | tr -d '\n'
# Copy the output → GitHub secret: SIGNING_KEY
```

#### 2. GitHub repository secrets
**Action**: In the GitHub repo > Settings > Secrets and variables > Actions, add:

| Secret name | Value |
|---|---|
| `SIGNING_KEY` | base64-encoded contents of `brightness-widget.jks` |
| `KEY_ALIAS` | `brightness-key` |
| `KEY_STORE_PASSWORD` | the password chosen during `keytool` |
| `KEY_PASSWORD` | the key password chosen during `keytool` |

#### 3. GitHub Actions workflow
**File**: `.github/workflows/release.yml`

```yaml
name: Build and Release APK

on:
  push:
    tags:
      - 'v*'   # triggers on tags like v1.0.0, v1.1.0, etc.

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Gradle cache
        uses: gradle/actions/setup-gradle@v3

      - name: Build release APK
        run: ./gradlew assembleRelease

      - name: Sign APK
        uses: r0adkll/sign-android-release@v1
        id: sign_app
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.KEY_ALIAS }}
          keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}
        env:
          BUILD_TOOLS_VERSION: "34.0.0"

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          name: "Brightness Widget ${{ github.ref_name }}"
          files: ${{ steps.sign_app.outputs.signedReleaseFile }}
          generate_release_notes: true
```

#### 4. `.gitignore` additions
**File**: `.gitignore`  
**Changes**: Ensure the keystore and any local secrets are never committed

```
# Android keystore — never commit
*.jks
*.keystore

# Standard Android ignores
.gradle/
local.properties
app/build/
build/
*.iml
.idea/
```

#### 5. Obtainium setup (user action, documented in README)
Once the first release tag is pushed:
1. In Obtainium, tap **+**
2. Enter the GitHub repository URL (e.g., `https://github.com/crueber/android-brightness`)
3. Obtainium will detect GitHub Releases and track new APKs automatically

### Success Criteria

#### Automated Verification:
- [ ] Pushing a tag `v1.0.0` triggers the GitHub Actions workflow
- [ ] Workflow completes successfully (green checkmark)
- [ ] A signed APK is attached to the GitHub Release for that tag
- [ ] APK filename ends in `-signed.apk` (not `-unsigned.apk`)

#### Manual Verification:
- [ ] The signed APK installs cleanly on a device (no "app not installed" error)
- [ ] Obtainium can find and install the APK from the GitHub Release
- [ ] Pushing a second tag `v1.0.1` produces a new release that Obtainium detects as an update

**Implementation Note**: After completing this phase, the project is fully functional and distributable. Confirm with the human that Obtainium tracking works before considering the project complete.

---

## Local Development

### Prerequisites

#### 1. Java Development Kit (JDK 17)
Android's build toolchain requires JDK 17. On macOS with Homebrew:
```bash
brew install --cask temurin@17
```
Verify:
```bash
java -version
# Should print: openjdk version "17.x.x" ...
```

#### 2. Android Studio
Download from https://developer.android.com/studio and install normally. Android Studio bundles:
- The Android SDK
- Android build tools
- An AVD (emulator) manager
- The Gradle wrapper (no separate Gradle install needed)

After first launch, let Android Studio complete its SDK component downloads. Accept all license agreements when prompted.

#### 3. Android SDK command-line tools (optional but useful)
Android Studio installs these automatically. If you want `adb` and `sdkmanager` available in your terminal, add the SDK platform-tools to your PATH. In `~/.zshrc` (or `~/.bashrc`):
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export PATH="$PATH:$ANDROID_HOME/tools"
```
Then reload: `source ~/.zshrc`

Verify `adb` is available:
```bash
adb version
# Android Debug Bridge version 1.0.xx
```

---

### Opening the Project

1. Open Android Studio
2. **File > Open** → select the repository root directory
3. Android Studio will detect the `build.gradle.kts` files and sync the project
4. Wait for the Gradle sync to complete (progress bar at the bottom)
5. If prompted to upgrade Gradle or AGP (Android Gradle Plugin), decline for now — use the versions specified in the plan

---

### Running on a Physical Device

This is the recommended way to test brightness changes — the emulator does not have a real screen brightness setting.

#### Enable Developer Options on your Android device:
1. Settings > About Phone > tap **Build Number** 7 times
2. Settings > Developer Options > enable **USB Debugging**

#### Connect and verify:
```bash
adb devices
# Should list your device, e.g.:
# List of devices attached
# R5CW3xxxxx    device
```
If it shows `unauthorized`, check your phone for a "Allow USB debugging?" prompt and tap Allow.

#### Install the debug APK directly via adb:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
The `-r` flag reinstalls over an existing version without uninstalling first.

Or use Android Studio's **Run** button (green triangle) with your device selected in the device dropdown — this builds, installs, and launches in one step.

#### View logs in real time:
```bash
adb logcat -s "BrightnessWidget" "GlanceAppWidget" "AndroidRuntime"
```
This filters to only your app's log output. Useful for debugging widget tap actions and brightness writes.

---

### Running on an Emulator

The emulator is useful for testing the **permission flow** and **UI layout**, but **cannot test actual brightness changes** (the emulator has no real screen brightness).

#### Create an emulator (AVD):
1. In Android Studio: **Tools > Device Manager > Create Device**
2. Choose a phone profile (e.g., Pixel 6)
3. Select a system image: **API 26** minimum, **API 35** recommended
4. Finish and launch the AVD

#### Or launch from the command line:
```bash
# List available AVDs
$ANDROID_HOME/emulator/emulator -list-avds

# Start one
$ANDROID_HOME/emulator/emulator -avd Pixel_6_API_35
```

#### Install to the running emulator:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`adb` automatically targets the running emulator if no physical device is connected. If both are connected, specify the target:
```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Gradle Build Commands

All commands are run from the repository root. The `./gradlew` wrapper script is committed to the repo and handles downloading the correct Gradle version automatically — no separate Gradle installation needed.

| Task | Command | When to use |
|---|---|---|
| Build debug APK | `./gradlew assembleDebug` | Development / testing |
| Build release APK (unsigned) | `./gradlew assembleRelease` | Pre-release check |
| Install debug APK to connected device | `./gradlew installDebug` | Faster than manual adb install |
| Run lint checks | `./gradlew lint` | Before committing |
| Run unit tests | `./gradlew test` | After logic changes |
| Clean build outputs | `./gradlew clean` | When build behaves unexpectedly |
| Clean + rebuild debug | `./gradlew clean assembleDebug` | Full fresh build |
| List all available tasks | `./gradlew tasks` | Discovery |

**Output locations:**
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK (unsigned): `app/build/outputs/apk/release/app-release-unsigned.apk`
- Lint report: `app/build/reports/lint-results-debug.html`

---

### Building a Signed Release APK Locally

For local testing of the release build (e.g., to verify ProGuard/R8 minification doesn't break anything) before pushing a tag:

#### One-time: create a local signing config
Add to `app/build.gradle.kts` (do **not** commit the actual passwords — use `local.properties` or environment variables):

```kotlin
// In app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file("../brightness-widget.jks")
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

#### Build and install the signed release APK:
```bash
export KEY_STORE_PASSWORD="your-password"
export KEY_ALIAS="brightness-key"
export KEY_PASSWORD="your-key-password"

./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

### Iterating on the Widget During Development

Android widgets have a quirk: **changes to widget layout do not hot-reload**. After rebuilding and reinstalling, you must:

1. Long-press the existing widget on the home screen → **Remove**
2. Long-press the home screen → **Widgets** → find "Brightness Widget" → add it again

This is a platform limitation of `RemoteViews` — the widget host (launcher) caches the layout. Removing and re-adding forces a fresh render.

**Tip**: Keep `adb logcat` running in a terminal while iterating so you can see errors immediately:
```bash
adb logcat -s "BrightnessWidget" "GlanceAppWidget" "GlanceAppWidgetManager" "AndroidRuntime" "*:E"
```

---

### Triggering a Release from the Command Line

Once Phase 4 (CI/CD) is complete, releasing a new version is:

```bash
# Bump versionCode and versionName in app/build.gradle.kts first, then:
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.1.0"
git tag v1.1.0
git push origin main --tags
```

GitHub Actions picks up the `v1.1.0` tag, builds, signs, and publishes the APK to GitHub Releases automatically. Obtainium will detect the new release on its next check.

---

## Testing Strategy

### Unit Tests

The core logic is simple enough that unit tests are optional for v1, but if added:
- Test `SetBrightnessAction` brightness value calculation: given step N of BRIGHTNESS_STEPS, verify the correct 0–255 value is computed
- Test edge cases: step 1 → value ≥ 1 (not 0), step BRIGHTNESS_STEPS → value = 255

Run unit tests (JVM only, no device needed):
```bash
./gradlew test
# Report: app/build/reports/tests/testDebugUnitTest/index.html
```

### Manual Testing Steps
1. Fresh install on a physical device with auto-brightness enabled:
   ```bash
   ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open app → verify "One Permission Needed" screen appears
3. Tap button → verify it opens **Settings > Apps > Special App Access > Modify System Settings** with Brightness Widget listed
4. Enable the toggle → press Back → verify app now shows "Permission Granted" (no restart needed)
5. Long-press home screen → Widgets → find "Brightness Widget" → drag to home screen
6. Tap leftmost segment → screen dims noticeably; verify in Settings > Display that auto-brightness is now off
7. Tap rightmost segment → screen at maximum brightness
8. Tap a middle segment → brightness at approximately that fraction of maximum
9. Resize widget by long-pressing → drag handle → stretch to full screen width → verify bar fills correctly
10. Reboot device → verify widget still shows correct brightness level on reload (not a stale/blank state)

### Changing Step Granularity (Developer Test)
To switch from 10 to 20 steps:
1. Open `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessConfig.kt`
2. Change `const val BRIGHTNESS_STEPS = 10` to `const val BRIGHTNESS_STEPS = 20`
3. Rebuild and reinstall:
   ```bash
   ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Remove and re-add the widget (required after layout changes — see note above)
5. Verify 20 segments appear and each represents ~5% brightness

---

## Performance Considerations

- The widget has no background service, no polling, and no `updatePeriodMillis` — it only updates when tapped. Battery impact is negligible.
- `BrightnessWidget().updateAll(context)` is called after each tap to refresh the visual state. This is a lightweight `RemoteViews` push and completes in milliseconds.
- `SizeMode.Exact` causes a recompose on resize, but this is a user-initiated action and not a performance concern.

---

## Migration Notes

N/A — greenfield project, no existing data or users to migrate.

---

## References

- Research document: `research/2026-03-10-android-brightness-widget.md`
- Jetpack Glance docs: https://developer.android.com/develop/ui/compose/glance
- Glance AppWidget samples: https://github.com/android/platform-samples/tree/main/samples/user-interface/appwidgets
- `r0adkll/sign-android-release` action: https://github.com/r0adkll/sign-android-release
- Android `WRITE_SETTINGS` permission docs: https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_WRITE_SETTINGS
