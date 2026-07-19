package com.guruswarupa.launch.widgets

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppUsageStatsManager




class WidgetSetupManager(
    private val activity: MainActivity,
    private val usageStatsManager: AppUsageStatsManager,
    private val weatherManager: com.guruswarupa.launch.utils.WeatherManager,
    private val permissionManager: com.guruswarupa.launch.core.PermissionManager,
    private val secureStorageManager: com.guruswarupa.launch.core.SecureStorageManager
) {
    companion object {
        private const val TAG = "WidgetSetupManager"
    }

    // Generic helper for widgets with SharedPreferences
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> setupWidgetWithPrefs(
        @androidx.annotation.IdRes containerId: Int,
        sharedPreferences: android.content.SharedPreferences,
        crossinline createWidget: (MainActivity, LinearLayout, android.content.SharedPreferences) -> T
    ): T {
        val container = activity.findViewById<LinearLayout>(containerId)
        if (container == null) {
            Log.e(TAG, "${T::class.simpleName} widget container not found!")
            throw IllegalStateException("${T::class.simpleName} container not found")
        }
        val widget = createWidget(activity, container, sharedPreferences)
        (widget as? com.guruswarupa.launch.widgets.InitializableWidget)?.initialize()
        com.guruswarupa.launch.managers.TypographyManager.applyToView(container)
        return widget
    }

    // Generic helper for widgets without SharedPreferences
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> setupWidget(
        @androidx.annotation.IdRes containerId: Int,
        crossinline createWidget: (MainActivity, LinearLayout) -> T
    ): T {
        val container = activity.findViewById<LinearLayout>(containerId)
        if (container == null) {
            Log.e(TAG, "${T::class.simpleName} widget container not found!")
            throw IllegalStateException("${T::class.simpleName} container not found")
        }
        val widget = createWidget(activity, container)
        (widget as? com.guruswarupa.launch.widgets.InitializableWidget)?.initialize()
        com.guruswarupa.launch.managers.TypographyManager.applyToView(container)
        return widget
    }

    // Generic helper for inflated widgets
    private inline fun <reified T> setupInflatedWidget(
        @androidx.annotation.IdRes containerId: Int,
        @androidx.annotation.LayoutRes layoutId: Int,
        crossinline createWidget: (View) -> T
    ): T {
        val container = activity.findViewById<ViewGroup?>(containerId)
        if (container == null) {
            Log.e(TAG, "${T::class.simpleName} widget container not found!")
            throw IllegalStateException("${T::class.simpleName} container not found")
        }
        val widgetView = LayoutInflater.from(activity).inflate(layoutId, container, false)
        container.addView(widgetView)
        Log.d(TAG, "${T::class.simpleName} widget setup: container found, added=${widgetView.parent != null}")
        com.guruswarupa.launch.managers.TypographyManager.applyToView(widgetView)
        return createWidget(widgetView)
    }

    fun setupTopWidgetData() {
        // Usage update is handled by UsageStatsRefreshManager via LifecycleManager
    }

    fun setupWeather(weatherIcon: ImageView, weatherText: TextView) {

        weatherManager.updateWeather(weatherIcon, weatherText)


        val weatherClickListener = View.OnClickListener {
            weatherManager.updateWeather(weatherIcon, weatherText)
        }
        weatherIcon.setOnClickListener(weatherClickListener)
        weatherText.setOnClickListener(weatherClickListener)


        val weatherLongClickListener = View.OnLongClickListener {
            weatherManager.showWeatherSettings(weatherIcon, weatherText)
            true
        }
        weatherIcon.setOnLongClickListener(weatherLongClickListener)
        weatherText.setOnLongClickListener(weatherLongClickListener)
    }

    fun setupCalculatorWidget(): CalculatorWidget = setupInflatedWidget(
        containerId = R.id.calculator_widget_container,
        layoutId = R.layout.calculator_widget
    ) { CalculatorWidget(it) }

    fun setupMediaControllerWidget(): MediaControllerWidget = setupInflatedWidget(
        containerId = R.id.media_controller_widget_container,
        layoutId = R.layout.media_controller_widget
    ) { MediaControllerWidget(activity, it) }

    fun setupWorkoutWidget(): WorkoutWidget = setupInflatedWidget(
        containerId = R.id.workout_widget_container,
        layoutId = R.layout.workout_widget
    ) { WorkoutWidget(it) }

    fun setupPhysicalActivityWidget(sharedPreferences: android.content.SharedPreferences): PhysicalActivityWidget =
        setupWidgetWithPrefs(R.id.physical_activity_widget_container, sharedPreferences) { act, container, prefs ->
            PhysicalActivityWidget(act, container, prefs)
        }

    fun setupCompassWidget(sharedPreferences: android.content.SharedPreferences): CompassWidget =
        setupWidgetWithPrefs(R.id.compass_widget_container, sharedPreferences) { act, container, prefs ->
            CompassWidget(act, container, prefs)
        }

    fun setupPressureWidget(sharedPreferences: android.content.SharedPreferences): PressureWidget =
        setupWidgetWithPrefs(R.id.pressure_widget_container, sharedPreferences) { act, container, prefs ->
            PressureWidget(act, container = container, sharedPreferences = prefs)
        }

    fun setupTemperatureWidget(sharedPreferences: android.content.SharedPreferences): TemperatureWidget =
        setupWidgetWithPrefs(R.id.temperature_widget_container, sharedPreferences) { act, container, prefs ->
            TemperatureWidget(act, container, prefs)
        }

    fun setupWeatherForecastWidget(): WeatherForecastWidget =
        setupWidget(R.id.weather_forecast_widget_container) { act, container ->
            WeatherForecastWidget(act, container)
        }

    fun setupNoiseDecibelWidget(sharedPreferences: android.content.SharedPreferences): NoiseDecibelWidget =
        setupWidgetWithPrefs(R.id.noise_decibel_widget_container, sharedPreferences) { act, container, prefs ->
            NoiseDecibelWidget(act, container, prefs)
        }

    fun setupCalendarEventsWidget(sharedPreferences: android.content.SharedPreferences): CalendarEventsWidget =
        setupWidgetWithPrefs(R.id.calendar_events_widget_container, sharedPreferences) { act, container, prefs ->
            CalendarEventsWidget(act, container, prefs)
        }

    fun setupCountdownWidget(sharedPreferences: android.content.SharedPreferences): CountdownWidget =
        setupWidgetWithPrefs(R.id.countdown_widget_container, sharedPreferences) { act, container, prefs ->
            CountdownWidget(act, container, prefs)
        }

    fun setupDnsWidget(sharedPreferences: android.content.SharedPreferences): DnsWidget =
        setupWidgetWithPrefs(R.id.dns_widget_container, sharedPreferences) { act, container, prefs ->
            DnsWidget(act, container, prefs)
        }

    fun setupNoteWidget(sharedPreferences: android.content.SharedPreferences): NoteWidget =
        setupWidgetWithPrefs(R.id.note_widget_container, sharedPreferences) { act, container, prefs ->
            NoteWidget(act, container, prefs)
        }

    fun setupNetworkStatsWidget(): NetworkStatsWidget =
        setupWidget(R.id.network_stats_widget_container) { act, container ->
            NetworkStatsWidget(act, container)
        }

    fun setupDeviceInfoWidget(): DeviceInfoWidget =
        setupWidget(R.id.device_info_widget_container) { act, container ->
            DeviceInfoWidget(act, container)
        }

    fun setupYearProgressWidget(sharedPreferences: android.content.SharedPreferences): YearProgressWidget =
        setupWidgetWithPrefs(R.id.year_progress_widget_container, sharedPreferences) { act, container, prefs ->
            YearProgressWidget(act, container, prefs)
        }

    // TODO: Implement weekly usage widget setup
    fun setupWeeklyUsageWidget() {
        // Not yet implemented
    }

    fun setupGithubContributionWidget(sharedPreferences: android.content.SharedPreferences): GithubContributionWidget =
        setupWidgetWithPrefs(R.id.github_contributions_widget_container, sharedPreferences) { act, container, _ ->
            val securePrefs = secureStorageManager.getSecurePrefs(com.guruswarupa.launch.core.SecureStorageManager.GITHUB_PREFS)
            GithubContributionWidget(act, container, securePrefs)
        }

    fun setupBatteryHealthWidget(): BatteryHealthWidget =
        setupWidget(R.id.battery_health_widget_container) { act, container ->
            BatteryHealthWidget(act, container)
        }

    fun requestNotificationPermission() {
        permissionManager.requestNotificationPermission()
    }

    fun getActivity(): MainActivity {
        return activity
    }
}
