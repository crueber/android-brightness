package us.packden.brightnesswidget

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider

class BrightnessWidget : GlanceAppWidget() {

    // SizeMode.Exact: recompose whenever the user resizes the widget
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                BrightnessBar(context)
            }
        }
    }
}

@Composable
fun BrightnessBar(context: Context) {
    val steps = BrightnessConfig.BRIGHTNESS_STEPS
    val gapDp = BrightnessConfig.SEGMENT_GAP_DP
    val heightDp = BrightnessConfig.SEGMENT_HEIGHT_DP

    // Read current brightness (0–255); default to midpoint if unreadable
    val rawBrightness = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Settings.SettingNotFoundException) {
        128
    }

    // Which step is currently active (0..steps)?
    val activeStep = ((rawBrightness / 255f) * steps).toInt().coerceIn(0, steps)

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
