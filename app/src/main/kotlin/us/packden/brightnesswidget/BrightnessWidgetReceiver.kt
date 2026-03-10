package us.packden.brightnesswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.datastore.preferences.core.intPreferencesKey
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

    // ContentObserver that fires whenever SCREEN_BRIGHTNESS changes externally
    // (pull-down slider, Settings app, etc.)
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
        // Re-register observer on each update broadcast (covers device reboot)
        registerBrightnessObserver(context)
        // Sync current system brightness into state on every update
        syncBrightnessState(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Also sync on any broadcast (covers edge cases like widget restore)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            syncBrightnessState(context)
        }
    }

    private fun registerBrightnessObserver(context: Context) {
        if (brightnessObserver != null) return  // already registered

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                syncBrightnessState(context)
            }
        }

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            /* notifyForDescendants = */ false,
            observer
        )

        brightnessObserver = observer
    }

    private fun unregisterBrightnessObserver(context: Context) {
        brightnessObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            brightnessObserver = null
        }
    }

    /**
     * Read the current system brightness and push it into Glance state for
     * every widget instance, then trigger a re-render.
     */
    private fun syncBrightnessState(context: Context) {
        val newBrightness = readSystemBrightness(context)
        coroutineScope.launch {
            val glanceIds = GlanceAppWidgetManager(context)
                .getGlanceIds(BrightnessWidget::class.java)
            for (id in glanceIds) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[intPreferencesKey("brightness_value")] = newBrightness
                    }
                }
            }
            BrightnessWidget().updateAll(context)
        }
    }
}
