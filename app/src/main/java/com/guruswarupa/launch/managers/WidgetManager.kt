package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
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
            Toast.makeText(context, "Error opening widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPickWidgetWithLauncher(launcher: ActivityResultLauncher<Intent>) {
        try {
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetHost.allocateAppWidgetId())
            pickIntent.putExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, true)
            launcher.launch(pickIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Invalid widget selected", Toast.LENGTH_SHORT).show()
            return
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo == null) {
            Toast.makeText(context, "Widget info not found", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Widget provider is no longer available", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        context,
                        "Cannot add this widget. Some widgets require special launcher permissions.",
                        Toast.LENGTH_LONG
                    ).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }

                try {
                    appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
                } catch (e2: Exception) {
                    Toast.makeText(context, "Failed to create widget: ${e2.message}", Toast.LENGTH_SHORT).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }
            }

            if (widgetView == null) {
                Toast.makeText(context, "Failed to create widget view", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(context, "Widget added successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error adding widget: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun showWidgetOptionsMenu(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        val currentIndex = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        val widgetName = appWidgetInfo.loadLabel(context.packageManager)

        val options = mutableListOf<String>()

        if (currentIndex > 0) {
            options.add("Move Up")
        }
        if (currentIndex < widgets.size - 1) {
            options.add("Move Down")
        }
        options.add("Delete")

        if (options.isEmpty()) return

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(widgetName)
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "Move Up" -> moveWidgetUp(appWidgetId)
                    "Move Down" -> moveWidgetDown(appWidgetId)
                    "Delete" -> showRemoveWidgetDialog(appWidgetId, widgetName.toString())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        fixDialogTextColors(dialog)
    }

    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(context, R.color.text)
            val listView = dialog.listView
            listView?.post {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(textColor)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun moveWidgetUp(appWidgetId: Int) {
        val currentIndex = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        if (currentIndex > 0 && currentIndex < widgetContainer.childCount) {
            val widget = widgets.removeAt(currentIndex)
            widgets.add(currentIndex - 1, widget)

            val viewToMove = widgetContainer.getChildAt(currentIndex)
            val viewToSwapWith = widgetContainer.getChildAt(currentIndex - 1)

            widgetContainer.removeView(viewToMove)
            widgetContainer.removeView(viewToSwapWith)

            widgetContainer.addView(viewToSwapWith, currentIndex)
            widgetContainer.addView(viewToMove, currentIndex - 1)

            saveWidgets()
        }
    }

    fun moveWidgetDown(appWidgetId: Int) {
        val currentIndex = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        if (currentIndex < widgets.size - 1 && currentIndex < widgetContainer.childCount - 1) {
            val widget = widgets.removeAt(currentIndex)
            widgets.add(currentIndex + 1, widget)

            val viewToMove = widgetContainer.getChildAt(currentIndex)
            val viewToSwapWith = widgetContainer.getChildAt(currentIndex + 1)

            widgetContainer.removeView(viewToMove)
            widgetContainer.removeView(viewToSwapWith)

            widgetContainer.addView(viewToSwapWith, currentIndex)
            widgetContainer.addView(viewToMove, currentIndex + 1)

            saveWidgets()
        }
    }

    private fun showRemoveWidgetDialog(appWidgetId: Int, widgetName: String) {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Remove Widget")
            .setMessage("Remove \"$widgetName\" widget?")
            .setPositiveButton("Remove") { _, _ ->
                removeWidget(appWidgetId)
            }
            .setNegativeButton("Cancel", null)
            .show()
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

            Toast.makeText(context, "Widget removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error removing widget: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    recreateWidgetView(widgetInfo, appWidgetInfo)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                }
            }

            if (widgets.size != jsonArray.length()) {
                saveWidgets()
            }
        } catch (_: Exception) {
        }
    }

    private fun recreateWidgetView(widgetInfo: SystemWidgetInfo, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            val widgetView = appWidgetHost.createView(context, widgetInfo.appWidgetId, appWidgetInfo)
            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo, appWidgetInfo)
            widgetContainer.addView(widgetContainerView)
        } catch (_: Exception) {
            widgets.remove(widgetInfo)
            appWidgetHost.deleteAppWidgetId(widgetInfo.appWidgetId)
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
            
            if (widgets.isNotEmpty()) {
                val viewsToRemove = mutableListOf<View>()
                for (i in 0 until widgetContainer.childCount) {
                    val child = widgetContainer.getChildAt(i)
                    if (child.tag is Int) {
                        viewsToRemove.add(child)
                    }
                }
                viewsToRemove.forEach { widgetContainer.removeView(it) }
                
                widgets.forEach { widgetInfo ->
                    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetInfo.appWidgetId)
                    if (appWidgetInfo != null) {
                        recreateWidgetView(widgetInfo, appWidgetInfo)
                    }
                }
            }
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
