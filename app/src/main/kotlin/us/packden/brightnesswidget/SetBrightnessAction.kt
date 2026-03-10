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

        // Convert the tapped step to a 0.0–1.0 fraction and write to system
        val fraction = stepToFraction(step, steps)
        writeBrightnessFraction(context, fraction)

        // Store fraction and exact tapped step in Glance state.
        // Storing the step directly avoids any rounding error on re-render.
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[brightnessFractionKey]   = fraction
                this[brightnessActiveStepKey] = step
            }
        }

        BrightnessWidget().updateAll(context)
    }
}
