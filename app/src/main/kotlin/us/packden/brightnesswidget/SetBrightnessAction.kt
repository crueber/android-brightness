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

val brightnessStepKey = ActionParameters.Key<Int>("brightness_step")

class SetBrightnessAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val step = parameters[brightnessStepKey] ?: return
        val steps = BrightnessConfig.BRIGHTNESS_STEPS
        val range = getBrightnessRange(context)

        // Map the tapped step to the device's actual brightness range
        val brightnessValue = stepToRawBrightness(step, steps, range)

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

        // Store both the raw value and the exact step that was tapped.
        // Storing the step directly avoids any rounding error on the display side.
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[intPreferencesKey("brightness_value")] = brightnessValue
                this[intPreferencesKey("brightness_active_step")] = step
            }
        }

        BrightnessWidget().updateAll(context)
    }
}
