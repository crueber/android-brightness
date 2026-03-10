package us.packden.brightnesswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class BrightnessWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BrightnessWidget()

    private val coroutineScope = MainScope()
    private var brightnessObserver: ContentObserver? = null

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        registerBrightnessObserver(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        unregisterBrightnessObserver(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        registerBrightnessObserver(context)
        syncBrightnessState(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            syncBrightnessState(context)
        }
    }

    private fun registerBrightnessObserver(context: Context) {
        if (brightnessObserver != null) return

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                syncBrightnessState(context)
            }
        }

        val cr = context.contentResolver

        // Observe the float setting on API 26+ (authoritative on modern devices)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cr.registerContentObserver(
                Settings.System.getUriFor("screen_brightness_float"),
                false, observer
            )
        }

        // Always observe the legacy integer setting too (older devices + fallback)
        cr.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false, observer
        )

        brightnessObserver = observer
    }

    private fun unregisterBrightnessObserver(context: Context) {
        brightnessObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            brightnessObserver = null
        }
    }

    private fun syncBrightnessState(context: Context) {
        val fraction  = readBrightnessFraction(context)
        val steps     = BrightnessConfig.BRIGHTNESS_STEPS
        val activeStep = fractionToStep(fraction, steps)
        coroutineScope.launch {
            val glanceIds = GlanceAppWidgetManager(context)
                .getGlanceIds(BrightnessWidget::class.java)
            for (id in glanceIds) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[brightnessFractionKey]   = fraction
                        this[brightnessActiveStepKey] = activeStep
                    }
                }
            }
            BrightnessWidget().updateAll(context)
        }
    }
}
