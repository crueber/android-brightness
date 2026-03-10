# Brightness Widget

A minimal Android home screen widget that lets you control screen brightness with a single tap. No frills, no extra features — just a clean horizontal bar you add to your home screen and tap to set brightness.

## What it does

- Displays as a horizontal bar on your home screen (starts at 2×1 grid cells, resizable to any width)
- The bar is divided into 10 segments; tap any segment to set brightness to that level
- Disables auto-brightness automatically when you tap, so your setting sticks
- Requires one special permission (`Modify System Settings`) granted once through a simple setup screen

## Requirements

- Android 8.0 (API 26) or newer
- The `Modify System Settings` permission (the app walks you through granting it on first launch)

## Installation

### Via Obtainium (recommended)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium)
2. Tap **+** and enter: `https://github.com/crueber/android-brightness`
3. Obtainium will find the latest release APK and install it, and notify you of future updates

### Direct APK download

Download the latest `app-release-signed.apk` from the [Releases](https://github.com/crueber/android-brightness/releases) page and install it manually.

## First-time setup

1. Open the **Brightness Widget** app from your launcher
2. Tap **Open Settings to Grant Permission** and enable the toggle for Brightness Widget
3. Return to the app — it will confirm the permission is granted
4. Long-press your home screen → **Widgets** → drag **Brightness Widget** onto your home screen

## Building locally

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (installs the Android SDK automatically)
- JDK 17 — install via Homebrew: `brew install --cask temurin@17`
- Add `adb` to your PATH (optional but useful):
  ```bash
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export PATH="$PATH:$ANDROID_HOME/platform-tools"
  ```

### Build a debug APK

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install to a connected device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use the **Run** button in Android Studio with your device selected.

### Useful Gradle tasks

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew assembleDebug` |
| Build release APK | `./gradlew assembleRelease` |
| Install debug to device | `./gradlew installDebug` |
| Run lint | `./gradlew lint` |
| Clean build outputs | `./gradlew clean` |

### Adjusting brightness granularity

Open `app/src/main/kotlin/us/packden/brightnesswidget/BrightnessConfig.kt` and change:

```kotlin
const val BRIGHTNESS_STEPS = 10   // 10% increments, larger tap targets
// or
const val BRIGHTNESS_STEPS = 20   // 5% increments, finer control
```

Rebuild and reinstall after changing. You'll need to remove and re-add the widget from your home screen for the new segment count to appear.

## Releasing

See [RELEASING.md](RELEASING.md) for instructions on signing, tagging, and publishing a release via GitHub Actions.

## License

MIT — see [LICENSE](LICENSE)
