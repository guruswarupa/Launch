package com.guruswarupa.launch.widgets

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.guruswarupa.launch.core.LifecycleManager

class DeferredWidgetInitializer(
    private val widgetSetupManager: WidgetSetupManager,
    private val sharedPreferences: SharedPreferences,
    private val lifecycleManager: LifecycleManager,
    private val widgetLifecycleCoordinator: WidgetLifecycleCoordinator,
    private val onComplete: () -> Unit,
    private val onFinished: () -> Unit = {}
) {
    companion object {
        private const val TAG = "DeferredWidgetInit"
        private const val STEP_DELAY_MS = 40L
    }

    private inline fun setup(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val steps = ArrayDeque<() -> Unit>()
    private var finished = false
    private var stepRunnable: Runnable? = null

    init {
        steps.add { widgetSetupManager.setupTopWidgetData() }
        steps.add { with(widgetLifecycleCoordinator) { setup("Calculator") { calculatorWidget = widgetSetupManager.setupCalculatorWidget() } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Workout") { workoutWidget = widgetSetupManager.setupWorkoutWidget() } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("PhysicalActivity") { physicalActivityWidget = widgetSetupManager.setupPhysicalActivityWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Compass") { compassWidget = widgetSetupManager.setupCompassWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Pressure") { pressureWidget = widgetSetupManager.setupPressureWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Temperature") { temperatureWidget = widgetSetupManager.setupTemperatureWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("WeatherForecast") { weatherForecastWidget = widgetSetupManager.setupWeatherForecastWidget() } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("NoiseDecibel") { noiseDecibelWidget = widgetSetupManager.setupNoiseDecibelWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("CalendarEvents") { calendarEventsWidget = widgetSetupManager.setupCalendarEventsWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Countdown") { countdownWidget = widgetSetupManager.setupCountdownWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Dns") { dnsWidget = widgetSetupManager.setupDnsWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("Note") { noteWidget = widgetSetupManager.setupNoteWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("BatteryHealth") { batteryHealthWidget = widgetSetupManager.setupBatteryHealthWidget() } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("NetworkStats") { networkStatsWidget = widgetSetupManager.setupNetworkStatsWidget() } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("DeviceInfo") { deviceInfoWidget = widgetSetupManager.setupDeviceInfoWidget() } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("YearProgress") { yearProgressWidget = widgetSetupManager.setupYearProgressWidget(sharedPreferences) } } }
        steps.add { with(widgetLifecycleCoordinator) { setup("GithubContribution") { githubContributionWidget = widgetSetupManager.setupGithubContributionWidget(sharedPreferences) } } }
        steps.add {
            with(widgetLifecycleCoordinator) {
                lifecycleManager.updateDependencies {
                    copy(
                        networkStatsWidget = if (isNetworkStatsWidgetInitialized()) networkStatsWidget else null,
                        deviceInfoWidget = if (isDeviceInfoWidgetInitialized()) deviceInfoWidget else null
                    )
                }

                setupDefaultLifecycle()
            }
        }
        steps.add {
            widgetSetupManager.requestNotificationPermission()
            onComplete()
        }
    }

    fun initialize() {
        finishNow()
    }

    fun finishNow() {
        stepRunnable?.let { handler.removeCallbacks(it) }
        stepRunnable = null
        while (steps.isNotEmpty()) {
            steps.removeFirst().invoke()
        }
        markFinished()
    }

    fun startStaggered(isAlive: () -> Boolean) {
        scheduleNext(isAlive)
    }

    private fun scheduleNext(isAlive: () -> Boolean) {
        val runnable = Runnable {
            stepRunnable = null
            if (!isAlive()) {
                steps.clear()
                return@Runnable
            }
            if (steps.isNotEmpty()) {
                steps.removeFirst().invoke()
            }
            if (steps.isEmpty()) {
                markFinished()
            } else {
                scheduleNext(isAlive)
            }
        }
        stepRunnable = runnable
        handler.postDelayed(runnable, STEP_DELAY_MS)
    }

    private fun markFinished() {
        if (finished) return
        finished = true
        onFinished()
    }
}
