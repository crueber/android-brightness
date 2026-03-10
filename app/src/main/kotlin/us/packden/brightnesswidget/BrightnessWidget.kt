package us.packden.brightnesswidget

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlin.math.pow
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

// ─── Step ↔ brightness conversion (gamma-corrected) ─────────────────────────
//
// The Android Quick Settings brightness slider uses a gamma/perceptual curve
// (AOSP BrightnessUtils) so that evenly-spaced slider positions correspond to
// perceptually even brightness changes. We apply the same curve so that our
// widget steps match the slider positions the user sees.
//
// The mapping is:
//   sliderPosition = (step - 1) / (steps - 1)          [0..1, linear in step]
//   linearBrightness = pow(sliderPosition, GAMMA)       [0..1, gamma-corrected]
//   integerBrightness = MIN + linearBrightness * (MAX - MIN)
//
// This allocates more steps to the dim end (where the eye is more sensitive)
// and fewer to the bright end, matching the QS slider behavior.

/**
 * Map step (1..N) to a brightness integer (BRIGHTNESS_MIN..BRIGHTNESS_MAX)
 * using a gamma curve that matches the Quick Settings slider.
 *   step 1 → BRIGHTNESS_MIN (dimmest)
 *   step N → BRIGHTNESS_MAX (brightest)
 */
fun stepToBrightness(step: Int, steps: Int): Int {
    val sliderPos = (step - 1).toFloat() / (steps - 1)
    val linear = sliderPos.pow(BrightnessConfig.GAMMA)
    return (BRIGHTNESS_MIN + linear * (BRIGHTNESS_MAX - BRIGHTNESS_MIN))
        .roundToInt()
        .coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
}

/**
 * Map a brightness integer (0–255) to the nearest step (1..N)
 * using the inverse gamma curve. Inverse of stepToBrightness.
 */
fun brightnessToStep(brightness: Int, steps: Int): Int {
    val clamped = brightness.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
    val linear = (clamped - BRIGHTNESS_MIN).toFloat() / (BRIGHTNESS_MAX - BRIGHTNESS_MIN)
    val sliderPos = linear.pow(1.0f / BrightnessConfig.GAMMA)
    return (sliderPos * (steps - 1)).roundToInt() + 1
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

// Semi-transparent label colors that contrast with the segment background
private val labelOnFilled   = Color(0x55000000)  // dark on white
private val labelOnUnfilled = Color(0x55FFFFFF)  // light on dark

/** The step index (1-based) at the midpoint of the bar. */
private fun midStep(steps: Int): Int = (steps / 2) + if (steps % 2 == 0) 0 else 1

@Composable
fun BrightnessBar() {
    val steps     = BrightnessConfig.BRIGHTNESS_STEPS
    val heightDp  = BrightnessConfig.SEGMENT_HEIGHT_DP
    val cornerDp  = BrightnessConfig.CORNER_RADIUS_DP
    val dividerDp = BrightnessConfig.DIVIDER_WIDTH_DP
    val iconSizeDp = BrightnessConfig.ICON_SIZE_DP

    val prefs      = currentState<Preferences>()
    val activeStep = prefs[brightnessActiveStepKey]?.coerceIn(1, steps) ?: 1

    val mid = midStep(steps)

    // Outer Box stacks children: the segment Row on the bottom, icon overlay on top
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .cornerRadius(cornerDp.dp)
            .background(ColorProvider(colorUnfilled))
    ) {
        // ── Segment Row ──────────────────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            for (step in 1..steps) {
                val isFilled = step <= activeStep
                val leftPad = if (step > 1) dividerDp.dp else 0.dp

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(start = leftPad)
                        .background(ColorProvider(
                            if (isFilled) colorFilled else colorUnfilled
                        ))
                        .clickable(
                            actionRunCallback<SetBrightnessAction>(
                                actionParametersOf(brightnessStepKey to step)
                            )
                        ),
                    contentAlignment = when (step) {
                        steps -> Alignment.CenterEnd
                        else  -> Alignment.Center
                    }
                ) {
                    // Brightness icon in the first step, centered
                    if (step == 1) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_brightness),
                            contentDescription = "Brightness",
                            modifier = GlanceModifier.size(iconSizeDp.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.glance.ColorFilter.tint(
                                ColorProvider(
                                    if (isFilled) labelOnFilled else labelOnUnfilled
                                )
                            )
                        )
                    }
                    // "50%" label in the middle step
                    if (step == mid) {
                        Text(
                            text = "50%",
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(
                                    if (isFilled) labelOnFilled else labelOnUnfilled
                                ),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    // "100%" label in the last step
                    if (step == steps) {
                        Text(
                            text = "100%",
                            maxLines = 1,
                            modifier = GlanceModifier.padding(end = 3.dp),
                            style = TextStyle(
                                color = ColorProvider(
                                    if (isFilled) labelOnFilled else labelOnUnfilled
                                ),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End
                            )
                        )
                    }
                }
            }
        }
    }
}
