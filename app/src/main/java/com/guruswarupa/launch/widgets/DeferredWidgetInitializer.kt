package com.guruswarupa.launch.widgets

import android.content.SharedPreferences
import com.guruswarupa.launch.core.LifecycleManager
import com.guruswarupa.launch.managers.WidgetConfigurationManager

class DeferredWidgetInitializer(
    private val widgetSetupManager: WidgetSetupManager,
    private val sharedPreferences: SharedPreferences,
    private val lifecycleManager: LifecycleManager,
    private val widgetConfigurationManager: WidgetConfigurationManager,
    private val widgetLifecycleCoordinator: WidgetLifecycleCoordinator,
    private val onComplete: () -> Unit
) {

    fun initialize() {
        widgetSetupManager.setupTopWidgetData()

        with(widgetLifecycleCoordinator) {
            mediaControllerWidget = widgetSetupManager.setupMediaControllerWidget()
            calculatorWidget = widgetSetupManager.setupCalculatorWidget()
            workoutWidget = widgetSetupManager.setupWorkoutWidget()
            physicalActivityWidget = widgetSetupManager.setupPhysicalActivityWidget(sharedPreferences)
            compassWidget = widgetSetupManager.setupCompassWidget(sharedPreferences)
            pressureWidget = widgetSetupManager.setupPressureWidget(sharedPreferences)
            temperatureWidget = widgetSetupManager.setupTemperatureWidget(sharedPreferences)
            weatherForecastWidget = widgetSetupManager.setupWeatherForecastWidget()
            noiseDecibelWidget = widgetSetupManager.setupNoiseDecibelWidget(sharedPreferences)
            calendarEventsWidget = widgetSetupManager.setupCalendarEventsWidget(sharedPreferences)
            countdownWidget = widgetSetupManager.setupCountdownWidget(sharedPreferences)
            dnsWidget = widgetSetupManager.setupDnsWidget(sharedPreferences)
            noteWidget = widgetSetupManager.setupNoteWidget(sharedPreferences)
            batteryHealthWidget = widgetSetupManager.setupBatteryHealthWidget()

            networkStatsWidget = widgetSetupManager.setupNetworkStatsWidget()
            deviceInfoWidget = widgetSetupManager.setupDeviceInfoWidget()
            yearProgressWidget = widgetSetupManager.setupYearProgressWidget(sharedPreferences)
            githubContributionWidget = widgetSetupManager.setupGithubContributionWidget(sharedPreferences)

            lifecycleManager.updateDependencies {
                copy(
                    networkStatsWidget = networkStatsWidget,
                    deviceInfoWidget = deviceInfoWidget
                )
            }


            setupDefaultLifecycle()
        }

        widgetSetupManager.requestNotificationPermission()

        onComplete()
    }
}
