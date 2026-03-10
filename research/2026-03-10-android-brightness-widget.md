---
date: 2026-03-10T16:22:45+0000
repository: android-brightness
topic: "Android Home Screen Brightness Control Widget — Existing Options & Build Plan"
tags: [research, android, appwidget, brightness, kotlin, jetpack-glance, obtainium]
---

# Research: Android Home Screen Brightness Control Widget

**Date**: 2026-03-10T16:22:45+0000  
**Repository**: android-brightness

## Research Question

Is there an existing free, open-source Android home screen widget that is a simple horizontal bar (1x2 grid and up) you can touch to raise or lower screen brightness? If not, what is needed to build one, preferably without writing Java?

---

## Summary

**No suitable existing app exists.** Every open-source Android brightness widget found is abandoned (2010–2018 era), targets ancient Android versions (2.x–5.x), has been removed from F-Droid, has no downloadable APK, and is not Obtainium-compatible. None implement a continuous drag slider — they all use preset tap-buttons.

**The path forward is to build it.** The recommended stack is **Kotlin + Jetpack Glance** (no Java required). There is one important platform constraint: Android's widget system (`RemoteViews`) does not support a live drag-to-adjust `SeekBar`. The best alternative is a row of tappable brightness-step segments that visually look like a bar — functionally equivalent, just tap-based rather than drag-based.

---

## Detailed Findings

### Existing Open-Source Brightness Widgets

All candidates found are dead projects. Summary:

| App | Type | Slider? | F-Droid? | GitHub Releases? | Obtainium? | Last Updated | Status |
|-----|------|---------|----------|------------------|------------|-------------|--------|
| [Fast Brightness Control Widget](https://github.com/marin-liovic/Fast-Brightness-Control-Widget) | Home screen widget | Preset buttons | Removed | No | **No** | 2014 | Dead |
| [BrightnessWidget (tillwoerner)](https://github.com/tillwoerner/BrightnessWidget) | Home screen widget | Preset buttons | Removed | No | **No** | 2013 | Dead |
| [BrightScreenWidget](https://github.com/junglesung/BrightScreenWidget) | Home screen widget | No (max only) | No | No | **No** | 2018 | Dead |
| [Android_BrightnessWidget](https://github.com/Narutuffy/Android_BrightnessWidget) | Home screen widget | Unknown | No | No | **No** | 2017 | Dead |
| [Android-Widget (ranjithv8)](https://github.com/ranjithv8/Android-Widget) | Home screen widget | Unknown | No | No | **No** | 2014 | Dead |
| [Brightness-Controller-AppWidget](https://github.com/stefanomunarini/Brightness-Controller-Android-AppWidget) | Home screen widget | Unknown | No | No | **No** | 2014 | Dead |
| [Brightness (svenhenrik)](https://github.com/svenhenrik/Brightness) | Home screen widget | No (toggle) | No | No | **No** | 2010 | Dead |
| [YAAB](https://f-droid.org/en/packages/biz.gyrus.yaab/) | Overlay/notification | Sort of | Yes (static) | No | Partial | 2014 | Dead/no source |
| [Adaptive Brightness Tile](https://github.com/rascarlo/AdaptiveBrightnessTile) | Quick Settings tile | No | Yes | Yes | **Yes** | 2019 | Dormant |
| [ScreenLighter](https://github.com/tobyhs/ScreenLighter) | App shortcut | No | No | No | **No** | Active | Active |
| [BrightnessSlider (gilbsgilbs)](https://github.com/gilbsgilbs/BrightnessSlider) | Status bar overlay | Yes | No | ? | ? | ? | ? |

**Key observations:**
- IzzyOnDroid has no brightness widget entries at all.
- The closest historical matches (Fast Brightness Control Widget, BrightnessWidget/tillwoerner) use preset tap-buttons, not a continuous slider, and are removed from F-Droid.
- No modern (post-2019), actively maintained, open-source home screen brightness widget exists anywhere.

### Why No Modern Slider Widget Exists

Android's `RemoteViews` system (used for all home screen widgets) supports only a restricted subset of UI components. `SeekBar` and `Slider` are **not** in that subset — in any Android version, including Android 12+. This is a fundamental platform constraint that explains why nobody has built a modern drag-slider brightness widget: it's not possible with standard AppWidget APIs.

---

## Build Plan

### Language Selection

**Kotlin** is the correct choice. It is:
- Google's official first-class Android language since 2019
- Fully supported for AppWidgets and Jetpack Glance
- Not Java (modern, concise, similar feel to Swift or TypeScript)
- The only non-Java language with full AppWidget tooling

**Flutter, React Native, and other cross-platform frameworks cannot build Android home screen widgets.** They render inside an Activity context and have no mechanism to produce `RemoteViews`-based AppWidgets. Flutter's `home_widget` package still requires the actual widget UI to be written in Kotlin/Java.

### Framework: Jetpack Glance

[Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) is the modern Kotlin/Compose-based API for building AppWidgets. Instead of constructing `RemoteViews` XML manually, you write Composable functions. Glance translates the composable tree into `RemoteViews` under the hood.

- **Current stable version**: 1.1.0 (late 2024)
- **Actively maintained** by Google
- **Official recommended approach** for new widgets
- **Sample repo**: https://github.com/android/platform-samples/tree/main/samples/user-interface/appwidgets

### The Slider Problem & Solution

Since `SeekBar` is impossible in `RemoteViews`, the best interaction model is **tap zones**:

A horizontal `Row` of N tappable segments, each filled or unfilled based on current brightness level. Tapping a segment sets brightness to that level. Visually looks like a slider bar; functionally is discrete tap-to-set.

```
[■][■][■][■][■][░][░][░][░][░]   ← 50% brightness, tap any segment to set level
```

Each segment is a clickable `Box` in Glance that fires an `ActionCallback` setting `SCREEN_BRIGHTNESS` to the corresponding value (0–255).

Alternative interaction models:
- **+/- buttons**: `[ - ] [■■■■■░░░░░] [ + ]` — simpler but less intuitive
- **Tap to open overlay**: Tap widget → transparent floating Activity with real `SeekBar` → auto-dismiss. Full drag experience but more complex.

### Required Android Permission

`WRITE_SETTINGS` is a **special permission** (not a normal runtime permission). The user must explicitly grant it in **Settings > Apps > Special App Access > Modify System Settings**.

The app must detect whether it has this permission and direct the user there if not:
```kotlin
if (!Settings.System.canWrite(context)) {
    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}"))
    context.startActivity(intent)
}
```

Setting brightness once granted:
```kotlin
// Disable auto-brightness first (required for manual to stick)
Settings.System.putInt(context.contentResolver,
    Settings.System.SCREEN_BRIGHTNESS_MODE,
    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

// Set brightness (0–255)
Settings.System.putInt(context.contentResolver,
    Settings.System.SCREEN_BRIGHTNESS,
    value)
```

### Widget Sizing & Resizability

Defined in `res/xml/brightness_widget_info.xml`:
```xml
<appwidget-provider
    android:minWidth="146dp"           <!-- 2 cells wide initial -->
    android:minHeight="50dp"           <!-- 1 cell tall -->
    android:minResizeWidth="73dp"      <!-- can shrink to 1 cell -->
    android:minResizeHeight="50dp"
    android:maxResizeWidth="2048dp"    <!-- can expand to full width -->
    android:resizeMode="horizontal"    <!-- user can drag to resize -->
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"     <!-- manual updates only -->
    android:targetCellWidth="2"        <!-- API 31+: 2 cells wide -->
    android:targetCellHeight="1"       <!-- API 31+: 1 cell tall -->
/>
```

In Glance, use `SizeMode.Exact` so the widget recomposes when the user resizes it:
```kotlin
override val sizeMode = SizeMode.Exact
```

### Minimum API Level

- `WRITE_SETTINGS` as special permission requiring user grant: **API 23** (Android 6.0)
- Recommended minimum: **API 26** (Android 8.0), covering ~97% of active devices
- Compile against: **API 35** (Android 15)

### Project Structure

```
app/
  src/main/
    AndroidManifest.xml
    kotlin/.../
      BrightnessWidget.kt        ← GlanceAppWidget subclass
      BrightnessWidgetReceiver.kt ← GlanceAppWidgetReceiver subclass
      SetBrightnessAction.kt     ← ActionCallback for brightness change
      MainActivity.kt            ← permission prompt on first launch
    res/
      xml/
        brightness_widget_info.xml  ← AppWidgetProviderInfo
      drawable/
        widget_preview.png          ← shown in widget picker
```

**`build.gradle.kts` dependencies:**
```kotlin
dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
}
```

**`AndroidManifest.xml` key entries:**
```xml
<uses-permission android:name="android.permission.WRITE_SETTINGS" />

<receiver android:name=".BrightnessWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/brightness_widget_info" />
</receiver>
```

### Glance Widget Skeleton

```kotlin
// BrightnessWidget.kt
class BrightnessWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                BrightnessBar(context)
            }
        }
    }
}

// BrightnessWidgetReceiver.kt
class BrightnessWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrightnessWidget()
}

// SetBrightnessAction.kt
val brightnessKey = ActionParameters.Key<Int>("brightness_value")

class SetBrightnessAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val value = parameters[brightnessKey] ?: return
        Settings.System.putInt(context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, value)
        BrightnessWidget().update(context, glanceId)
    }
}

// BrightnessBar composable (tap-zone approach)
@Composable
fun BrightnessBar(context: Context) {
    val steps = 10
    val currentBrightness = Settings.System.getInt(
        context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    val currentStep = (currentBrightness / 255f * steps).roundToInt()

    Row(modifier = GlanceModifier.fillMaxWidth().height(48.dp)) {
        for (i in 1..steps) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(if (i <= currentStep) Color.White else Color.DarkGray)
                    .clickable(
                        actionRunCallback<SetBrightnessAction>(
                            actionParametersOf(brightnessKey to (i * 255 / steps))
                        )
                    )
            )
        }
    }
}
```

### Build & Distribution (Obtainium-Compatible)

**Signing** (one-time setup):
```bash
keytool -genkey -v \
  -keystore brightness-widget.jks \
  -alias brightness-key \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

**GitHub Actions workflow** (triggers on version tags like `v1.0.0`):
```yaml
name: Build Release APK

on:
  push:
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
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
          files: ${{ steps.sign_app.outputs.signedReleaseFile }}
```

Obtainium then points at GitHub Releases to track and install updates automatically.

---

## Technical Constraints Summary

| Constraint | Detail |
|---|---|
| Language | Kotlin (no Java required) |
| Framework | Jetpack Glance 1.1.0 (stable) |
| Live drag slider | **Impossible** — RemoteViews has no SeekBar/Slider in any Android version |
| Best slider alternative | Tap zones (N clickable segments) or +/- buttons |
| Brightness permission | `WRITE_SETTINGS` — user must navigate to special settings page to grant |
| Auto-brightness | Must be disabled programmatically for manual brightness to stick |
| Min API | API 23 for WRITE_SETTINGS grant flow; recommend API 26 |
| Widget resize | `resizeMode="horizontal"` in XML, `SizeMode.Exact` in Glance |
| Flutter/RN | Cannot build AppWidgets — must use Kotlin regardless |
| CI/CD | GitHub Actions + `r0adkll/sign-android-release` is standard |
| Obtainium | Needs signed APK attached to GitHub Releases |

---

## Open Questions

1. **Interaction model preference**: Tap-zone bar (tap anywhere on the bar to set that level) vs. +/- increment buttons? The tap-zone approach looks more like a slider; +/- is simpler to implement.
2. **Number of steps**: 10 steps (increments of ~25/255) vs. more granular (e.g., 20 steps)? More steps = finer control but smaller tap targets.
3. **Visual style**: Filled segments with a gap between them (segmented look) vs. a solid filled bar with a single tap-anywhere-to-set behavior?
4. **Auto-brightness behavior**: Should the widget disable auto-brightness when you tap it, or should it leave auto-brightness mode alone and only work in manual mode?
5. **Permission flow**: A minimal `MainActivity` that only shows a permission prompt (and then closes) is the cleanest approach — do you want any other UI in the app itself, or purely just the widget?
6. **Minimum Android version**: API 26 (Android 8.0, ~2017) covers ~97% of devices. Is that acceptable, or do you need to go lower?
