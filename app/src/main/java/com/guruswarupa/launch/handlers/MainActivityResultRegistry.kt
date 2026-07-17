package com.guruswarupa.launch.handlers

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.widgets.WidgetVisibilityManager
import com.guruswarupa.launch.ui.activities.WidgetConfigurationActivity
import com.guruswarupa.launch.utils.VoiceCommandHandler
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator

class MainActivityResultRegistry(private val activity: FragmentActivity) {


    val widgetPickerLauncher: ActivityResultLauncher<Intent>
    val widgetConfigurationLauncher: ActivityResultLauncher<Intent>
    val voiceSearchLauncher: ActivityResultLauncher<Intent>




    data class DependencyContainer(
        var widgetManager: WidgetManager? = null,
        var widgetVisibilityManager: WidgetVisibilityManager? = null,
        var widgetConfigurationManager: WidgetConfigurationManager? = null,
        var voiceCommandHandler: VoiceCommandHandler? = null,
        var activityResultHandler: ActivityResultHandler? = null,
        var packageManager: android.content.pm.PackageManager? = null,
        var contentResolver: android.content.ContentResolver? = null,
        var searchBox: android.widget.AutoCompleteTextView? = null,
        var appList: MutableList<android.content.pm.ResolveInfo>? = null,
        var widgetLifecycleCoordinator: WidgetLifecycleCoordinator? = null
    )

    private var deps = DependencyContainer()

    init {

        widgetPickerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {

                deps.widgetManager?.handleWidgetPicked(activity, result.data)
            }
        }


        widgetConfigurationLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val yearProgress = deps.widgetLifecycleCoordinator?.let { if (it.isYearProgressWidgetInitialized()) it.yearProgressWidget else null }
                val githubContribution = deps.widgetLifecycleCoordinator?.let { if (it.isGithubContributionWidgetInitialized()) it.githubContributionWidget else null }

                deps.widgetVisibilityManager?.update(yearProgress, githubContribution)

                refreshSystemWidgets()

                deps.widgetVisibilityManager?.update(yearProgress, githubContribution)


                deps.widgetLifecycleCoordinator?.let { coordinator ->
                    if (coordinator.isCalendarEventsWidgetInitialized()) {
                        val isEnabled = deps.widgetConfigurationManager?.isWidgetEnabled("calendar_events_widget_container") ?: false
                        if (isEnabled) {
                            coordinator.calendarEventsWidget.refresh()
                        }
                    }
                }


                deps.widgetLifecycleCoordinator?.let { coordinator ->
                    if (coordinator.isCountdownWidgetInitialized()) {
                        val isEnabled = deps.widgetConfigurationManager?.isWidgetEnabled("countdown_widget_container") ?: false
                        if (isEnabled) {
                            coordinator.countdownWidget.refresh()
                        }
                    }
                }
            }
        }


        voiceSearchLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {

                val pm = deps.packageManager
                val cr = deps.contentResolver
                val sb = deps.searchBox
                val al = deps.appList
                if (deps.voiceCommandHandler == null && pm != null && cr != null &&
                    sb != null && al != null) {
                    deps.voiceCommandHandler = VoiceCommandHandler(
                        activity,
                        pm,
                        cr,
                        sb,
                        al
                    )
                }
                deps.activityResultHandler?.setVoiceCommandHandler(deps.voiceCommandHandler)
            }

            deps.activityResultHandler?.handleActivityResult(PermissionManager.VOICE_SEARCH_REQUEST, result.resultCode, result.data)
        }
    }




    fun setDependencies(dependencies: DependencyContainer) {
        this.deps = dependencies
    }




    private fun refreshSystemWidgets() {

        deps.widgetManager?.reloadWidgets()
    }




    fun showWidgetConfigurationDialog() {
        val intent = Intent(activity, WidgetConfigurationActivity::class.java)
        widgetConfigurationLauncher.launch(intent)
    }
}
