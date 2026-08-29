package com.guruswarupa.launch.widgets

import android.content.SharedPreferences
import android.util.Log
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

    // A failure setting up one widget (e.g. its container view couldn't be found) must not take
    // down the rest of the widgets, or worse, the final onComplete() callback that marks
    // deferred-widget init as done and syncs widget visibility - skipping that would leave every
    // widget unsynced for the rest of the session.
    private inline fun setup(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up $name widget, skipping it", e)
        }
    }

    fun initialize() {
        widgetSetupManager.setupTopWidgetData()

        with(widgetLifecycleCoordinator) {
            setup("MediaController") { mediaControllerWidget = widgetSetupManager.setupMediaControllerWidget() }
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
