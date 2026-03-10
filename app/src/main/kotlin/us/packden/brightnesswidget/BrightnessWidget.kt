package us.packden.brightnesswidget

import android.content.Context
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

// ─── Glance state keys ───────────────────────────────────────────────────────

/** Active step (1..BRIGHTNESS_STEPS) stored in Glance state. */
val brightnessActiveStepKey = intPreferencesKey("brightness_active_step_v2")

// ─── System brightness read/write ────────────────────────────────────────────
//
// Uses ONLY the integer Settings.System.SCREEN_BRIGHTNESS (range 0–255).
//
// This is the one brightness API that works identically across every Android
// version from API 26 through API 36+, on every Pixel generation (5 through 10),
// and on every manufacturer's device. The pull-down Quick Settings slider reads
// and writes this same setting.
//
// We deliberately avoid screen_brightness_float because:
//   - It doesn't exist on some devices
//   - On API 36+ it moved to Settings.Secure (can't write without root)
//   - The integer setting is always kept in sync by the system framework
//   - 0–255 gives us more than enough granularity for 10–20 widget steps

/** The system's integer brightness range. Always 0–255 on AOSP/Pixel. */
const val BRIGHTNESS_MIN = 1    // floor at 1 to avoid completely black screen
const val BRIGHTNESS_MAX = 255

/** Read the current system brightness as an integer 0–255. */
fun readSystemBrightness(context: Context): Int = try {
    Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS
    ).coerceIn(0, BRIGHTNESS_MAX)
} catch (_: Settings.SettingNotFoundException) {
    BRIGHTNESS_MAX / 2
}

/** Write a brightness integer (0–255) to the system. Also disables auto-brightness. */
fun writeSystemBrightness(context: Context, value: Int) {
    val cr = context.contentResolver
    val clamped = value.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)

    // Disable auto-brightness so the manual value sticks
    Settings.System.putInt(
        cr,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    )

    // Write the brightness
    Settings.System.putInt(
        cr,
        Settings.System.SCREEN_BRIGHTNESS,
        clamped
    )
}

// ─── Step ↔ brightness conversion ────────────────────────────────────────────

/**
 * Map step (1..N) to a brightness integer (BRIGHTNESS_MIN..BRIGHTNESS_MAX).
 *   step 1 → BRIGHTNESS_MIN (dimmest)
 *   step N → BRIGHTNESS_MAX (brightest)
 */
fun stepToBrightness(step: Int, steps: Int): Int {
    val fraction = (step - 1).toFloat() / (steps - 1)
    return (BRIGHTNESS_MIN + fraction * (BRIGHTNESS_MAX - BRIGHTNESS_MIN))
        .roundToInt()
        .coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
}

/**
 * Map a brightness integer (0–255) to the nearest step (1..N).
 * Inverse of stepToBrightness.
 */
fun brightnessToStep(brightness: Int, steps: Int): Int {
    val clamped = brightness.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
    val fraction = (clamped - BRIGHTNESS_MIN).toFloat() / (BRIGHTNESS_MAX - BRIGHTNESS_MIN)
    return (fraction * (steps - 1)).roundToInt() + 1
}

// ─── Widget ───────────────────────────────────────────────────────────────────

class BrightnessWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Always sync state with the current system brightness on load
        val steps = BrightnessConfig.BRIGHTNESS_STEPS
        val current = readSystemBrightness(context)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                this[brightnessActiveStepKey] = brightnessToStep(current, steps)
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
                val leftPad = if (step > 1) dividerDp.dp else 0.dp
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(start = leftPad)
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
