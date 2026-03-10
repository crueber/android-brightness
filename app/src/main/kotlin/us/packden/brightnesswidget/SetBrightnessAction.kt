package us.packden.brightnesswidget

import android.content.Context
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
        val step  = parameters[brightnessStepKey] ?: return
        val steps = BrightnessConfig.BRIGHTNESS_STEPS

        // Convert step to integer brightness and write to system
        val brightness = stepToBrightness(step, steps)
        writeSystemBrightness(context, brightness)

        // Store the exact tapped step in Glance state for immediate re-render
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[brightnessActiveStepKey] = step
            }
        }

        BrightnessWidget().updateAll(context)
    }
}
