package com.guruswarupa.launch.widgets

class WidgetLifecycleCoordinator {
    lateinit var calculatorWidget: CalculatorWidget
    lateinit var mediaControllerWidget: MediaControllerWidget
    lateinit var workoutWidget: WorkoutWidget
    lateinit var physicalActivityWidget: PhysicalActivityWidget
    lateinit var compassWidget: CompassWidget
    lateinit var pressureWidget: PressureWidget
    lateinit var temperatureWidget: TemperatureWidget
    lateinit var weatherForecastWidget: WeatherForecastWidget
    lateinit var noiseDecibelWidget: NoiseDecibelWidget
    lateinit var calendarEventsWidget: CalendarEventsWidget
    lateinit var countdownWidget: CountdownWidget
    lateinit var dnsWidget: DnsWidget
    lateinit var noteWidget: NoteWidget
    lateinit var yearProgressWidget: YearProgressWidget
    lateinit var githubContributionWidget: GithubContributionWidget
    lateinit var batteryHealthWidget: BatteryHealthWidget
    lateinit var networkStatsWidget: NetworkStatsWidget
    lateinit var deviceInfoWidget: DeviceInfoWidget

    fun isMediaControllerWidgetInitialized() = ::mediaControllerWidget.isInitialized
    fun isPhysicalActivityWidgetInitialized() = ::physicalActivityWidget.isInitialized
    fun isCompassWidgetInitialized() = ::compassWidget.isInitialized
    fun isPressureWidgetInitialized() = ::pressureWidget.isInitialized
    fun isTemperatureWidgetInitialized() = ::temperatureWidget.isInitialized
    fun isWeatherForecastWidgetInitialized() = ::weatherForecastWidget.isInitialized
    fun isNoiseDecibelWidgetInitialized() = ::noiseDecibelWidget.isInitialized
    fun isCalendarEventsWidgetInitialized() = ::calendarEventsWidget.isInitialized
    fun isCountdownWidgetInitialized() = ::countdownWidget.isInitialized
    fun isDnsWidgetInitialized() = ::dnsWidget.isInitialized
    fun isNoteWidgetInitialized() = ::noteWidget.isInitialized
    fun isYearProgressWidgetInitialized() = ::yearProgressWidget.isInitialized
    fun isGithubContributionWidgetInitialized() = ::githubContributionWidget.isInitialized
    fun isBatteryHealthWidgetInitialized() = ::batteryHealthWidget.isInitialized
    fun isNetworkStatsWidgetInitialized() = ::networkStatsWidget.isInitialized
    fun isDeviceInfoWidgetInitialized() = ::deviceInfoWidget.isInitialized

    private data class WidgetWrapper(
        val isInitialized: () -> Boolean,
        val onResume: () -> Unit,
        val onPause: () -> Unit,
        val onDestroy: () -> Unit
    )

    private val widgets = mutableListOf<WidgetWrapper>()

    fun register(
        isInitializedCheck: () -> Boolean,
        onResumeAction: () -> Unit,
        onPauseAction: () -> Unit,
        onDestroyAction: () -> Unit = {}
    ) {
        val wrapper = WidgetWrapper(
            isInitialized = isInitializedCheck,
            onResume = {
                if (isInitializedCheck()) {
                    onResumeAction()
                }
            },
            onPause = {
                if (isInitializedCheck()) {
                    onPauseAction()
                }
            },
            onDestroy = {
                if (isInitializedCheck()) {
                    onDestroyAction()
                }
            }
        )
        widgets.add(wrapper)
    }

    fun setupDefaultLifecycle() {
        widgets.clear()
        register({ ::mediaControllerWidget.isInitialized }, { mediaControllerWidget.refreshController() }, { }, { mediaControllerWidget.cleanup() })
        register({ ::yearProgressWidget.isInitialized }, { yearProgressWidget.onResume() }, { yearProgressWidget.onPause() }, { yearProgressWidget.cleanup() })
        register({ ::physicalActivityWidget.isInitialized }, { physicalActivityWidget.onResume() }, { physicalActivityWidget.onPause() }, { physicalActivityWidget.cleanup() })
        register({ ::compassWidget.isInitialized }, { compassWidget.onResume() }, { compassWidget.onPause() }, { compassWidget.cleanup() })
        register({ ::pressureWidget.isInitialized }, { pressureWidget.onResume() }, { pressureWidget.onPause() }, { pressureWidget.cleanup() })
        register({ ::temperatureWidget.isInitialized }, { temperatureWidget.onResume() }, { temperatureWidget.onPause() }, { temperatureWidget.cleanup() })
        register({ ::weatherForecastWidget.isInitialized }, { weatherForecastWidget.onResume() }, { weatherForecastWidget.onPause() }, { weatherForecastWidget.cleanup() })
        register({ ::noiseDecibelWidget.isInitialized }, { noiseDecibelWidget.onResume() }, { noiseDecibelWidget.onPause() }, { noiseDecibelWidget.cleanup() })
        register({ ::calendarEventsWidget.isInitialized }, { calendarEventsWidget.onResume() }, { calendarEventsWidget.onPause() }, { calendarEventsWidget.cleanup() })
        register({ ::countdownWidget.isInitialized }, { countdownWidget.onResume() }, { countdownWidget.onPause() }, { countdownWidget.cleanup() })
        register({ ::dnsWidget.isInitialized }, { dnsWidget.onResume() }, { dnsWidget.onPause() }, { dnsWidget.cleanup() })
        register({ ::noteWidget.isInitialized }, { noteWidget.onResume() }, { noteWidget.onPause() }, { noteWidget.cleanup() })
        register({ ::batteryHealthWidget.isInitialized }, { batteryHealthWidget.onResume() }, { batteryHealthWidget.onPause() }, { batteryHealthWidget.cleanup() })
        register({ ::githubContributionWidget.isInitialized }, { githubContributionWidget.onResume() }, { githubContributionWidget.onPause() }, { githubContributionWidget.cleanup() })
    }

    fun onResume() {
        widgets.forEach { widget ->
            widget.onResume()
        }
    }

    fun onPause() {
        widgets.forEach { widget ->
            widget.onPause()
        }
    }

    fun onDestroy() {
        widgets.forEach { widget ->
            widget.onDestroy()
        }
    }
}
