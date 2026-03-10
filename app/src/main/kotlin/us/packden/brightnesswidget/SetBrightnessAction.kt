package us.packden.brightnesswidget

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlin.math.roundToInt

val brightnessStepKey = ActionParameters.Key<Int>("brightness_step")

class SetBrightnessAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val step = parameters[brightnessStepKey] ?: return
        val steps = BrightnessConfig.BRIGHTNESS_STEPS

        // Map step (1..steps) evenly across the full brightness range (1..255).
        // Formula: (step-1) / (steps-1) spans 0.0–1.0 across the full range,
        // so step 1 = minimum and step N = maximum.
        // Floor at 1 (not 0) to avoid a completely black screen.
        val brightnessValue = ((step - 1).toFloat() / (steps - 1) * 255).roundToInt().coerceIn(1, 255)

        // Disable auto-brightness so the manual value sticks
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )

        // Set the system brightness
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue
        )

        // Write the new value into Glance state so the widget re-renders
        // immediately with the correct filled/unfilled segments
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[intPreferencesKey("brightness_value")] = brightnessValue
            }
        }

        // Trigger a re-render of all widget instances
        BrightnessWidget().updateAll(context)
    }
}
