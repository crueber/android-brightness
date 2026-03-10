package us.packden.brightnesswidget

import android.content.Context
import android.provider.Settings
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

val brightnessStepKey = ActionParameters.Key<Int>("brightness_step")

class SetBrightnessAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val step = parameters[brightnessStepKey] ?: return
        val steps = BrightnessConfig.BRIGHTNESS_STEPS

        // Convert step (1..steps) to Android brightness value (1..255)
        // Use 1 as minimum (not 0) to avoid a completely black screen
        val brightnessValue = ((step.toFloat() / steps) * 255).toInt().coerceIn(1, 255)

        // Disable auto-brightness so the manual value sticks
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )

        // Set the brightness
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue
        )

        // Refresh all instances of this widget
        BrightnessWidget().updateAll(context)
    }
}
