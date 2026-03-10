package us.packden.brightnesswidget

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
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
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import kotlin.math.roundToInt

/**
 * Brightness fraction stored in Glance state: 0.0 = minimum, 1.0 = maximum.
 * Using a float fraction is device-agnostic — no need to know the integer range.
 * Key name is "brightness_fraction" (distinct from the old "brightness_value" Int
 * key) to avoid a type mismatch crash on devices that have the old state stored.
 */
val brightnessFractionKey = floatPreferencesKey("brightness_fraction")

/**
 * Active step (1..BRIGHTNESS_STEPS) stored in Glance state.
 * Written by SetBrightnessAction (exact tapped step) and syncBrightnessState
 * (derived from the current fraction). BrightnessBar reads this directly.
 */
val brightnessActiveStepKey = intPreferencesKey("brightness_active_step")

// ─── System brightness read/write ────────────────────────────────────────────

/**
 * Read the current brightness as a fraction 0.0–1.0.
 *
 * On API 26+ devices (including Pixel 9 emulator) the float setting
 * SCREEN_BRIGHTNESS_FLOAT is the authoritative value. The legacy integer
 * SCREEN_BRIGHTNESS is a compatibility stub on modern devices and does not
 * reliably reflect what the display is actually doing.
 *
 * Falls back to the integer setting on older devices.
 */
fun readBrightnessFraction(context: Context): Float {
    // Try float setting first (API 26+, authoritative on modern devices)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val f = Settings.System.getFloat(
                context.contentResolver,
                "screen_brightness_float"
            )
            if (f in 0f..1f) return f
        } catch (_: Settings.SettingNotFoundException) { }
    }
    // Fall back to integer setting, normalised against 255
    return try {
        val raw = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        )
        (raw / 255f).coerceIn(0f, 1f)
    } catch (_: Settings.SettingNotFoundException) {
        0.5f
    }
}

/**
 * Write a brightness fraction 0.0–1.0 to the system.
 *
 * Writes SCREEN_BRIGHTNESS_FLOAT on API 26+ (what modern devices actually use)
 * and also writes the legacy integer for compatibility.
 */
fun writeBrightnessFraction(context: Context, fraction: Float) {
    val cr = context.contentResolver
    val clamped = fraction.coerceIn(0f, 1f)

    // Disable auto-brightness so the manual value sticks
    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

    // Write float setting on API 26+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Settings.System.putFloat(cr, "screen_brightness_float", clamped)
    }

    // Always write the legacy integer too (needed on older devices, harmless on new ones)
    val intValue = (clamped * 255).roundToInt().coerceIn(1, 255)
    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, intValue)
}

// ─── Step ↔ fraction conversion ──────────────────────────────────────────────

/** Map step 1..N to fraction 0.0–1.0. Step 1 = 0.0, step N = 1.0. */
fun stepToFraction(step: Int, steps: Int): Float =
    ((step - 1).toFloat() / (steps - 1)).coerceIn(0f, 1f)

/** Map fraction 0.0–1.0 to the nearest step 1..N. */
fun fractionToStep(fraction: Float, steps: Int): Int =
    (fraction * (steps - 1)).roundToInt() + 1

// ─── Widget ───────────────────────────────────────────────────────────────────

class BrightnessWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val steps = BrightnessConfig.BRIGHTNESS_STEPS
        val fraction = readBrightnessFraction(context)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                // Always refresh on load so the widget reflects current brightness
                this[brightnessFractionKey] = fraction
                this[brightnessActiveStepKey] = fractionToStep(fraction, steps)
            }
        }

        provideContent {
            GlanceTheme {
                BrightnessBar()
            }
        }
    }
}

// ─── UI ───────────────────────────────────────────────────────────────────────

private val colorFilled   = Color(0xFFFFFFFF)
private val colorUnfilled = Color(0xFF333333)

@Composable
fun BrightnessBar() {
    val steps     = BrightnessConfig.BRIGHTNESS_STEPS
    val heightDp  = BrightnessConfig.SEGMENT_HEIGHT_DP
    val cornerDp  = BrightnessConfig.CORNER_RADIUS_DP
    val dividerDp = BrightnessConfig.DIVIDER_WIDTH_DP

    val prefs      = currentState<Preferences>()
    val activeStep = prefs[brightnessActiveStepKey]?.coerceIn(1, steps) ?: 1

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .cornerRadius(cornerDp.dp)
            .background(ColorProvider(colorUnfilled))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            for (step in 1..steps) {
                if (step > 1) {
                    Box(
                        modifier = GlanceModifier
                            .width(dividerDp.dp)
                            .fillMaxHeight()
                            .background(ColorProvider(colorUnfilled))
                    ) {}
                }
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(ColorProvider(
                            if (step <= activeStep) colorFilled else colorUnfilled
                        ))
                        .clickable(
                            actionRunCallback<SetBrightnessAction>(
                                actionParametersOf(brightnessStepKey to step)
                            )
                        )
                ) {}
            }
        }
    }
}
