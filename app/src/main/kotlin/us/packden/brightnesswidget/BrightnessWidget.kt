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

// Key used to store the current brightness value (0–255) in Glance state
val brightnessValueKey = intPreferencesKey("brightness_value")

class BrightnessWidget : GlanceAppWidget() {

    // Use Glance's built-in Preferences state — survives process death,
    // triggers recomposition when updated via updateAppWidgetState()
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // SizeMode.Exact: recompose whenever the user resizes the widget
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Seed the state with the current system brightness on first load
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                if (this[brightnessValueKey] == null) {
                    this[brightnessValueKey] = readSystemBrightness(context)
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

/** Read the current system brightness (0–255), defaulting to 128 if unavailable. */
fun readSystemBrightness(context: Context): Int = try {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
} catch (e: Settings.SettingNotFoundException) {
    128
}

@Composable
fun BrightnessBar() {
    val steps = BrightnessConfig.BRIGHTNESS_STEPS
    val gapDp = BrightnessConfig.SEGMENT_GAP_DP
    val heightDp = BrightnessConfig.SEGMENT_HEIGHT_DP

    // Read brightness from Glance state — updated by SetBrightnessAction
    // and by the ContentObserver in BrightnessWidgetReceiver
    val prefs = currentState<Preferences>()
    val rawBrightness = prefs[brightnessValueKey] ?: 128

    // Which step is currently active (1..steps)?
    // Use roundToInt so that e.g. step 7/10 → value 178 → reads back as step 7
    val activeStep = ((rawBrightness / 255f) * steps).roundToInt().coerceIn(0, steps)

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
