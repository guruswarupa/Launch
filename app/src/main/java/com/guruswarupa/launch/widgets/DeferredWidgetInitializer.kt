package com.guruswarupa.launch.widgets

import android.content.SharedPreferences
import com.guruswarupa.launch.core.LifecycleManager

class DeferredWidgetInitializer(
    private val widgetSetupManager: WidgetSetupManager,
    private val sharedPreferences: SharedPreferences,
    private val lifecycleManager: LifecycleManager,
    private val widgetLifecycleCoordinator: WidgetLifecycleCoordinator,
    private val onComplete: () -> Unit
) {
    companion object {
        private const val TAG = "DeferredWidgetInit"
    }

    private inline fun setup(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
        }
    }

    fun initialize() {
        widgetSetupManager.setupTopWidgetData()

        with(widgetLifecycleCoordinator) {
            setup("Calculator") { calculatorWidget = widgetSetupManager.setupCalculatorWidget() }
            setup("Workout") { workoutWidget = widgetSetupManager.setupWorkoutWidget() }
            setup("PhysicalActivity") { physicalActivityWidget = widgetSetupManager.setupPhysicalActivityWidget(sharedPreferences) }
            setup("Compass") { compassWidget = widgetSetupManager.setupCompassWidget(sharedPreferences) }
            setup("Pressure") { pressureWidget = widgetSetupManager.setupPressureWidget(sharedPreferences) }
            setup("Temperature") { temperatureWidget = widgetSetupManager.setupTemperatureWidget(sharedPreferences) }
            setup("WeatherForecast") { weatherForecastWidget = widgetSetupManager.setupWeatherForecastWidget() }
            setup("NoiseDecibel") { noiseDecibelWidget = widgetSetupManager.setupNoiseDecibelWidget(sharedPreferences) }
            setup("CalendarEvents") { calendarEventsWidget = widgetSetupManager.setupCalendarEventsWidget(sharedPreferences) }
            setup("Countdown") { countdownWidget = widgetSetupManager.setupCountdownWidget(sharedPreferences) }
            setup("Dns") { dnsWidget = widgetSetupManager.setupDnsWidget(sharedPreferences) }
            setup("Note") { noteWidget = widgetSetupManager.setupNoteWidget(sharedPreferences) }
            setup("BatteryHealth") { batteryHealthWidget = widgetSetupManager.setupBatteryHealthWidget() }
            setup("NetworkStats") { networkStatsWidget = widgetSetupManager.setupNetworkStatsWidget() }
            setup("DeviceInfo") { deviceInfoWidget = widgetSetupManager.setupDeviceInfoWidget() }
            setup("YearProgress") { yearProgressWidget = widgetSetupManager.setupYearProgressWidget(sharedPreferences) }
            setup("GithubContribution") { githubContributionWidget = widgetSetupManager.setupGithubContributionWidget(sharedPreferences) }

            lifecycleManager.updateDependencies {
                copy(
                    networkStatsWidget = if (isNetworkStatsWidgetInitialized()) networkStatsWidget else null,
                    deviceInfoWidget = if (isDeviceInfoWidgetInitialized()) deviceInfoWidget else null
                )
            }

            setupDefaultLifecycle()
        }

        widgetSetupManager.requestNotificationPermission()

        onComplete()
    }
}
