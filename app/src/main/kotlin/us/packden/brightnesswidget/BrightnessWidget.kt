package us.packden.brightnesswidget

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import kotlin.math.roundToInt

// Key used to store the current raw brightness value in Glance state
val brightnessValueKey = intPreferencesKey("brightness_value")

/**
 * The device's actual brightness range, read from PowerManager at runtime.
 * Manufacturers use different ranges — commonly 1–100 or 1–255.
 * Falls back to 1–255 if PowerManager returns nonsensical values.
 */
data class BrightnessRange(val min: Int, val max: Int)

fun getBrightnessRange(context: Context): BrightnessRange {
    // minimumScreenBrightnessSetting and maximumScreenBrightnessSetting are
    // hidden APIs not in the public SDK. Access via reflection with a safe fallback.
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val min = PowerManager::class.java
            .getMethod("getMinimumScreenBrightnessSetting")
            .invoke(pm) as Int
        val max = PowerManager::class.java
            .getMethod("getMaximumScreenBrightnessSetting")
            .invoke(pm) as Int
        if (max > min && min >= 0) BrightnessRange(min.coerceAtLeast(1), max)
        else BrightnessRange(1, 255)
    } catch (e: Exception) {
        BrightnessRange(1, 255)
    }
}

/**
 * Map a step number (1..BRIGHTNESS_STEPS) to a raw brightness value
 * within the device's actual range.
 *   step 1          → range.min  (minimum brightness)
 *   step STEPS      → range.max  (maximum brightness)
 */
fun stepToRawBrightness(step: Int, steps: Int, range: BrightnessRange): Int {
    val fraction = (step - 1).toFloat() / (steps - 1)
    return (range.min + fraction * (range.max - range.min)).roundToInt()
        .coerceIn(range.min, range.max)
}

/**
 * Map a raw brightness value back to the nearest step (1..BRIGHTNESS_STEPS).
 * Inverse of stepToRawBrightness.
 */
fun rawBrightnessToStep(raw: Int, steps: Int, range: BrightnessRange): Int {
    val fraction = (raw - range.min).toFloat() / (range.max - range.min)
    return (fraction * (steps - 1)).roundToInt() + 1
}

/** Read the current raw system brightness, clamped to the device's range. */
fun readSystemBrightness(context: Context): Int {
    val range = getBrightnessRange(context)
    return try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            .coerceIn(range.min, range.max)
    } catch (e: Settings.SettingNotFoundException) {
        (range.min + range.max) / 2
    }
}

class BrightnessWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Seed state with current system brightness on first load
        val range = getBrightnessRange(context)
        val currentBrightness = readSystemBrightness(context)
        val steps = BrightnessConfig.BRIGHTNESS_STEPS
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                if (this[brightnessValueKey] == null) {
                    this[brightnessValueKey] = currentBrightness
                    this[brightnessActiveStepKey] = rawBrightnessToStep(currentBrightness, steps, range)
                }
            }
        }

        provideContent {
            GlanceTheme {
                BrightnessBar()
            }
        }
    }
}

@Composable
fun BrightnessBar() {
    val steps = BrightnessConfig.BRIGHTNESS_STEPS
    val gapDp = BrightnessConfig.SEGMENT_GAP_DP
    val heightDp = BrightnessConfig.SEGMENT_HEIGHT_DP

    // Raw brightness value stored in Glance state (in the device's native range)
    val prefs = currentState<Preferences>()
    val rawBrightness = prefs[brightnessValueKey] ?: -1

    // brightnessActiveStepKey is written by both SetBrightnessAction (exact step)
    // and syncBrightnessState (converted from raw using the device range).
    // Fallback: if somehow only rawBrightness is present, show 1 segment.
    val activeStep = prefs[brightnessActiveStepKey]?.coerceIn(1, steps) ?: 1

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
            ) {}
        }
    }
}

// Stores the active step (1..BRIGHTNESS_STEPS) so BrightnessBar never has to
// call getBrightnessRange() — that's done once in the action/observer path.
val brightnessActiveStepKey = intPreferencesKey("brightness_active_step")
