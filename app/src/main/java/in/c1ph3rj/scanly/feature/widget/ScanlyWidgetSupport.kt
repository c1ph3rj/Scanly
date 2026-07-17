package `in`.c1ph3rj.scanly.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Shared App Widget helpers.
 *
 * - Builds RemoteViews against a context that matches the **device** UI mode
 *   (light/dark) so night resources apply even if the process was started under
 *   a different configuration.
 * - Rebinds every lifecycle entry point so clicks work as soon as the host
 *   inflates the widget (avoids a long "blank / dead" state after placement).
 * - Pins widgets from in-app settings when the launcher supports it (API 26+).
 */
object ScanlyWidgetSupport {
    enum class PinResult {
        /** Launcher showed the system pin confirmation. */
        Requested,
        /** Launcher does not support [AppWidgetManager.requestPinAppWidget]. */
        Unsupported,
        /** Pin request failed for another reason. */
        Failed,
    }

    enum class WidgetKind {
        Actions,
        Scan,
        Qr,
    }

    fun isPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context.applicationContext).isRequestPinAppWidgetSupported
    }

    fun requestPin(context: Context, kind: WidgetKind): PinResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return PinResult.Unsupported
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        if (!manager.isRequestPinAppWidgetSupported) return PinResult.Unsupported
        val provider = when (kind) {
            WidgetKind.Actions -> ScanlyActionsWidgetProvider::class.java
            WidgetKind.Scan -> ScanlyScanWidgetProvider::class.java
            WidgetKind.Qr -> ScanlyQrWidgetProvider::class.java
        }
        return runCatching {
            val ok = manager.requestPinAppWidget(ComponentName(app, provider), null, null)
            if (ok) PinResult.Requested else PinResult.Failed
        }.getOrDefault(PinResult.Failed)
    }

    fun uiContext(context: Context): Context {
        val app = context.applicationContext
        val deviceNight =
            (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (deviceNight) {
                    Configuration.UI_MODE_NIGHT_YES
                } else {
                    Configuration.UI_MODE_NIGHT_NO
                }
        }
        return app.createConfigurationContext(config)
    }

    fun updateAll(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        buildViews: (Context) -> RemoteViews,
    ) {
        if (appWidgetIds.isEmpty()) return
        val themed = uiContext(context)
        val views = buildViews(themed)
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, views)
        }
    }

    fun refreshProvider(context: Context, providerClass: Class<out AppWidgetProvider>) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, providerClass))
        if (ids.isEmpty()) return
        when (providerClass) {
            ScanlyActionsWidgetProvider::class.java ->
                updateAll(app, manager, ids, ScanlyActionsWidgetProvider::buildViews)
            ScanlyScanWidgetProvider::class.java ->
                updateAll(app, manager, ids, ScanlyScanWidgetProvider::buildViews)
            ScanlyQrWidgetProvider::class.java ->
                updateAll(app, manager, ids, ScanlyQrWidgetProvider::buildViews)
        }
    }

    /** Push a themed rebind for every Scanly widget on the launcher. */
    fun refreshAll(context: Context) {
        refreshProvider(context, ScanlyActionsWidgetProvider::class.java)
        refreshProvider(context, ScanlyScanWidgetProvider::class.java)
        refreshProvider(context, ScanlyQrWidgetProvider::class.java)
    }
}

/** Base provider that rebinds on every host callback. */
abstract class ScanlyBaseWidgetProvider : AppWidgetProvider() {
    protected abstract fun buildViews(context: Context): RemoteViews

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        ScanlyWidgetSupport.updateAll(context, appWidgetManager, appWidgetIds, ::buildViews)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        ScanlyWidgetSupport.updateAll(
            context,
            appWidgetManager,
            intArrayOf(appWidgetId),
            ::buildViews,
        )
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        ScanlyWidgetSupport.updateAll(context, manager, newWidgetIds, ::buildViews)
    }

    override fun onEnabled(context: Context) {
        // First instance of this provider: ensure RemoteViews + PendingIntents land immediately.
        ScanlyWidgetSupport.refreshProvider(context, javaClass)
    }
}
