package com.guruswarupa.launch.managers

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.handlers.ActivityResultHandler
import com.guruswarupa.launch.models.PendingSystemWidgetBindRequest
import com.guruswarupa.launch.models.SystemWidgetInfo
import com.guruswarupa.launch.ui.activities.WidgetConfigurationActivity
import com.guruswarupa.launch.widgets.WidgetContainerFactory
import org.json.JSONArray
import org.json.JSONObject

class WidgetManager(
    private val context: Context,
    private val widgetContainer: LinearLayout,
    private val shouldLoadWidgets: Boolean = true
) {

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, APPWIDGET_HOST_ID)
    private val prefs: SharedPreferences = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
    private val widgets = mutableListOf<SystemWidgetInfo>()
    private val widgetOptionsCache = mutableMapOf<Int, String>()
    private var pendingConfigureWidgetId: Int? = null
    private var pendingBindRequest: PendingSystemWidgetBindRequest? = null
    private var startRetryAttempts = 0

    /** Invoked after system widget views have been (re)created, including after retries. */
    var onWidgetsRefreshed: (() -> Unit)? = null

    private val containerFactory = WidgetContainerFactory(
        context = context,
        dpToPx = { dp -> dpToPx(dp) },
        pxToDp = { px -> pxToDp(px) },
        updateWidgetCustomHeight = { id, height -> updateWidgetCustomHeight(id, height) },
        applyWidgetSizeOptions = { view, id, container, minH, forcedH ->
            applyWidgetSizeOptions(view, id, container, minH, forcedH)
        }
    )

    companion object {
        private const val APPWIDGET_HOST_ID = 1024
        private const val TAG = "WidgetManager"
        private const val PREFS_WIDGETS_KEY = "saved_widgets"
        private const val PREFS_WIDGETS_CHANGED_KEY = "saved_widgets_changed"
        private const val MAX_START_RETRY_ATTEMPTS = 5
        private const val START_RETRY_DELAY_MS = 400L
    }

    init {
        appWidgetHost.startListening()
        if (shouldLoadWidgets) {
            loadWidgets()
        }
    }

    fun requestPickWidget(activity: Activity, requestCode: Int) {
        try {
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetHost.allocateAppWidgetId())
            pickIntent.putExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, true)
            activity.startActivityForResult(pickIntent, requestCode)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_error_opening_widget_picker, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPickWidgetWithLauncher(launcher: ActivityResultLauncher<Intent>) {
        try {
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetHost.allocateAppWidgetId())
            pickIntent.putExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, true)
            launcher.launch(pickIntent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_error_opening_widget_picker, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun bindProvider(activity: Activity, providerPackage: String, providerClass: String, requestCode: Int) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val providers = appWidgetManager.installedProviders
        val providerInfo = providers.find {
            it.provider.packageName == providerPackage && it.provider.className == providerClass
        } ?: return

        val success = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
        } catch (_: Exception) {
            false
        }

        if (success) {
            launchConfigureOrBind(activity, appWidgetId, providerInfo)
        } else {
            pendingBindRequest = PendingSystemWidgetBindRequest(appWidgetId, providerPackage, providerClass)
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            activity.startActivityForResult(intent, requestCode)
        }
    }

    fun handleWidgetPicked(activity: Activity, data: Intent?) {
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

        if (appWidgetId == -1) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_widget_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo == null) {
            Toast.makeText(context, context.getString(R.string.toast_widget_info_not_found), Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return
        }

        if (appWidgetInfo.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            configIntent.component = appWidgetInfo.configure
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            try {
                pendingConfigureWidgetId = appWidgetId
                activity.startActivityForResult(configIntent, ActivityResultHandler.REQUEST_CONFIGURE_WIDGET)
            } catch (_: Exception) {
                pendingConfigureWidgetId = null
                bindWidget(appWidgetId, appWidgetInfo)
            }
        } else {
            bindWidget(appWidgetId, appWidgetInfo)
        }
    }

    fun handleWidgetConfigured(appWidgetId: Int? = null) {
        val resolvedWidgetId = appWidgetId ?: pendingConfigureWidgetId
        pendingConfigureWidgetId = null
        if (resolvedWidgetId == null || resolvedWidgetId == -1) {
            return
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(resolvedWidgetId)
        if (appWidgetInfo != null) {
            bindWidget(resolvedWidgetId, appWidgetInfo)
        } else {
            appWidgetHost.deleteAppWidgetId(resolvedWidgetId)
        }
    }

    fun handleWidgetConfigurationCanceled(appWidgetId: Int? = null) {
        val resolvedWidgetId = appWidgetId ?: pendingConfigureWidgetId
        pendingConfigureWidgetId = null
        if (resolvedWidgetId != null && resolvedWidgetId != -1) {
            widgets.removeAll { it.appWidgetId == resolvedWidgetId }
            widgetOptionsCache.remove(resolvedWidgetId)
            appWidgetHost.deleteAppWidgetId(resolvedWidgetId)
            saveWidgets()
        }
    }

    fun handleBindRequestResult(activity: Activity, approved: Boolean) {
        val pendingRequest = pendingBindRequest ?: return
        pendingBindRequest = null

        if (!approved) {
            appWidgetHost.deleteAppWidgetId(pendingRequest.appWidgetId)
            return
        }

        val providerInfo = appWidgetManager.installedProviders.find {
            it.provider.packageName == pendingRequest.providerPackage &&
                it.provider.className == pendingRequest.providerClass
        }

        if (providerInfo == null) {
            appWidgetHost.deleteAppWidgetId(pendingRequest.appWidgetId)
            Toast.makeText(context, context.getString(R.string.toast_widget_provider_is_no_longer_available), Toast.LENGTH_SHORT).show()
            return
        }

        launchConfigureOrBind(activity, pendingRequest.appWidgetId, providerInfo)
    }

    private fun launchConfigureOrBind(
        activity: Activity,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo
    ) {
        if (providerInfo.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                pendingConfigureWidgetId = appWidgetId
                activity.startActivityForResult(configIntent, ActivityResultHandler.REQUEST_CONFIGURE_WIDGET)
            } catch (_: Exception) {
                pendingConfigureWidgetId = null
                bindWidget(appWidgetId, providerInfo)
                (activity as? WidgetConfigurationActivity)?.loadWidgets()
            }
        } else {
            pendingConfigureWidgetId = null
            bindWidget(appWidgetId, providerInfo)
            (activity as? WidgetConfigurationActivity)?.loadWidgets()
        }
    }

    private fun bindWidget(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            val widgetView = try {
                appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
            } catch (_: Exception) {
                val bound = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        appWidgetManager.bindAppWidgetIdIfAllowed(
                            appWidgetId,
                            appWidgetInfo.profile,
                            appWidgetInfo.provider,
                            null
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetInfo.provider)
                    }
                } catch (_: Exception) {
                    false
                }

                if (!bound) {
                    Toast.makeText(context, context.getString(R.string.toast_cannot_add_this_widget_some_widgets_require_spec), Toast.LENGTH_LONG).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }

                try {
                    appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
                } catch (e2: Exception) {
                    Toast.makeText(context, context.getString(R.string.toast_failed_to_create_widget, e2.message), Toast.LENGTH_SHORT).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }
            }

            if (widgetView == null) {
                Toast.makeText(context, context.getString(R.string.toast_failed_to_create_widget_view), Toast.LENGTH_SHORT).show()
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return
            }

            widgetView.setAppWidget(appWidgetId, appWidgetInfo)

            val existingCustomHeightDp = widgets.find { it.appWidgetId == appWidgetId }?.customHeightDp
            val widgetInfo = SystemWidgetInfo(
                appWidgetId = appWidgetId,
                providerPackage = appWidgetInfo.provider.packageName,
                providerClass = appWidgetInfo.provider.className,
                minWidth = appWidgetInfo.minWidth,
                minHeight = appWidgetInfo.minHeight,
                customHeightDp = existingCustomHeightDp
            )

            widgets.removeAll { it.appWidgetId == appWidgetId }
            widgets.add(widgetInfo)

            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo, appWidgetInfo)
            widgetContainer.addView(widgetContainerView)

            saveWidgets()
            (context as? WidgetConfigurationActivity)?.loadWidgets()
            (context as? MainActivity)?.let { main ->
                if (main.deferredWidgetsInitialized) {
                    main.initializeDeferredWidgets()
                }
            }

            Toast.makeText(context, context.getString(R.string.toast_widget_added_successfully), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_error_adding_widget, e.message), Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }

    private fun createWidgetContainer(
        widgetView: AppWidgetHostView,
        widgetInfo: SystemWidgetInfo,
        appWidgetInfo: AppWidgetProviderInfo
    ): View {
        return containerFactory.createWidgetContainer(widgetView, widgetInfo, appWidgetInfo)
    }

    private fun applyWidgetSizeOptions(
        widgetView: AppWidgetHostView,
        appWidgetId: Int,
        containerView: View,
        providerMinHeightDp: Int,
        forcedHeightDp: Int? = null
    ) {
        val widthPx = containerView.width
        if (widthPx <= 0) return

        val widthDp = pxToDp(widthPx).coerceAtLeast(1)
        val measuredHeightDp = pxToDp(containerView.height)
        val heightDp = forcedHeightDp?.coerceAtLeast(1)
            ?: if (measuredHeightDp > 0) measuredHeightDp else providerMinHeightDp.coerceAtLeast(1)
        val optionsKey = "$widthDp:$heightDp"
        if (widgetOptionsCache[appWidgetId] == optionsKey) return
        widgetOptionsCache[appWidgetId] = optionsKey

        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        
        try {
            @Suppress("DEPRECATION")
            widgetView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
        } catch (_: Exception) {
            runCatching { appWidgetManager.updateAppWidgetOptions(appWidgetId, options) }
        }
    }

    private fun pxToDp(px: Int): Int {
        return (px / context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    fun updateWidgetCustomHeight(appWidgetId: Int, customHeightDp: Int) {
        val index = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        if (index < 0) return
        widgets[index] = widgets[index].copy(customHeightDp = customHeightDp)
        saveWidgets()
    }

    fun removeWidget(appWidgetId: Int) {
        try {
            for (i in 0 until widgetContainer.childCount) {
                val view = widgetContainer.getChildAt(i)
                if (view.tag == appWidgetId) {
                    widgetContainer.removeViewAt(i)
                    break
                }
            }

            widgets.removeAll { it.appWidgetId == appWidgetId }
            widgetOptionsCache.remove(appWidgetId)
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            saveWidgets()
            
            prefs.edit { 
                putBoolean(PREFS_WIDGETS_CHANGED_KEY, true)
            }

            Toast.makeText(context, context.getString(R.string.toast_widget_removed), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_error_removing_widget, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun syncWidgetOrder(appWidgetIds: List<Int>) {
        val newOrder = mutableListOf<SystemWidgetInfo>()
        appWidgetIds.forEach { id ->
            widgets.find { it.appWidgetId == id }?.let { newOrder.add(it) }
        }
        widgets.forEach { w -> if (newOrder.none { it.appWidgetId == w.appWidgetId }) newOrder.add(w) }
        
        widgets.clear()
        widgets.addAll(newOrder)
        saveWidgets()
    }

    private fun saveWidgets() {
        try {
            val jsonArray = JSONArray()
            widgets.forEach { widget ->
                val json = JSONObject().apply {
                    put("appWidgetId", widget.appWidgetId)
                    put("providerPackage", widget.providerPackage)
                    put("providerClass", widget.providerClass)
                    put("minWidth", widget.minWidth)
                    put("minHeight", widget.minHeight)
                    widget.customHeightDp?.let { put("customHeightDp", it) }
                }
                jsonArray.put(json)
            }
            prefs.edit {
                putString(PREFS_WIDGETS_KEY, jsonArray.toString())
                putBoolean(PREFS_WIDGETS_CHANGED_KEY, true)
            }
            (context as? WidgetConfigurationActivity)?.notifyWidgetConfigurationChanged()
        } catch (_: Exception) {
        }
    }

    private fun loadWidgets() {
        try {
            val widgetsJson = prefs.getString(PREFS_WIDGETS_KEY, null) ?: return
            val jsonArray = JSONArray(widgetsJson)

            var prunedAny = false
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val appWidgetId = json.getInt("appWidgetId")

                val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (appWidgetInfo != null) {
                    val widgetInfo = SystemWidgetInfo(
                        appWidgetId = appWidgetId,
                        providerPackage = json.getString("providerPackage"),
                        providerClass = json.getString("providerClass"),
                        minWidth = json.getInt("minWidth"),
                        minHeight = json.getInt("minHeight"),
                        customHeightDp = if (json.has("customHeightDp")) json.optInt("customHeightDp") else null
                    )
                    widgets.add(widgetInfo)
                } else {
                    // The widget id itself is gone (provider uninstalled or id revoked by the
                    // system) - this is a genuine removal, not a transient failure, so it's safe
                    // to prune here.
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    prunedAny = true
                }
            }

            if (prunedAny) {
                saveWidgets()
            }

            refreshSystemWidgetViews()
        } catch (_: Exception) {
        }
    }

    /**
     * (Re)creates the AppWidgetHostView for every entry in [widgets] and adds it to
     * [widgetContainer]. If a view fails to be created - e.g. right after boot or resume, when
     * the AppWidgetService/host binder may not be fully ready yet - the widget is left in place
     * (never deleted) and a bounded, backed-off retry is scheduled instead of silently dropping it.
     */
    private fun refreshSystemWidgetViews() {
        if (widgets.isEmpty()) {
            startRetryAttempts = 0
            return
        }

        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child.tag is Int) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { widgetContainer.removeView(it) }

        var anyMissing = false
        widgets.forEach { widgetInfo ->
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetInfo.appWidgetId)
            if (appWidgetInfo != null) {
                if (!recreateWidgetView(widgetInfo, appWidgetInfo)) {
                    anyMissing = true
                }
            } else {
                // Transient lookup failure - retry instead of assuming the widget is gone.
                anyMissing = true
            }
        }

        if (anyMissing && startRetryAttempts < MAX_START_RETRY_ATTEMPTS) {
            startRetryAttempts++
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { refreshSystemWidgetViews() },
                START_RETRY_DELAY_MS * startRetryAttempts
            )
        } else {
            startRetryAttempts = 0
        }

        onWidgetsRefreshed?.invoke()
    }

    private fun recreateWidgetView(widgetInfo: SystemWidgetInfo, appWidgetInfo: AppWidgetProviderInfo): Boolean {
        return try {
            val widgetView = appWidgetHost.createView(context, widgetInfo.appWidgetId, appWidgetInfo)
            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo, appWidgetInfo)
            widgetContainer.addView(widgetContainerView)
            true
        } catch (e: Exception) {
            // Don't delete the widget on a transient view-creation failure - just leave it for
            // the next retry/lifecycle event instead of silently losing it forever.
            Log.w(TAG, "Failed to recreate view for widget ${widgetInfo.appWidgetId}, will retry", e)
            false
        }
    }

    fun onStop() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping widget host listening", e)
        }
    }

    fun onStart() {
        try {
            appWidgetHost.startListening()
            refreshSystemWidgetViews()
        } catch (e: Exception) {
            Log.w(TAG, "Error starting widget host listening", e)
        }
    }

    fun reloadWidgets() {
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child.tag is Int) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { widgetContainer.removeView(it) }
        widgets.clear()
        widgetOptionsCache.clear()
        startRetryAttempts = 0
        loadWidgets()
    }

    fun reloadWidgetsIfPending() {
        if (!prefs.getBoolean(PREFS_WIDGETS_CHANGED_KEY, false)) {
            return
        }

        reloadWidgets()
        prefs.edit { putBoolean(PREFS_WIDGETS_CHANGED_KEY, false) }
    }

    fun onDestroy() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping widget host listening in destroy", e)
        }
    }
}
