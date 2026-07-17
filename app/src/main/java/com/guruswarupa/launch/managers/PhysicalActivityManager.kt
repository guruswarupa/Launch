package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

data class ActivityData(
    val steps: Int,
    val distanceKm: Double,
    val date: String,
    val walkingMinutes: Int = 0
)

data class HourlyActivityData(
    val hour: Int,
    val steps: Int,
    val distanceKm: Double,
    val walkingMinutes: Int = 0
)

class PhysicalActivityManager(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences("physical_activity_prefs", Context.MODE_PRIVATE)

    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var isListening = false
    private var isDataLoaded = false


    private var lastStepCount = 0
    private var todayStepCount = 0
    private var lastResetDate = ""
    private var totalStepsBase = 0


    private var lastSavedStepCount = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var saveRunnable: Runnable? = null




    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val calendar = Calendar.getInstance()


    private var historicalDataCache: MutableMap<String, ActivityData>? = null
    private var hourlyDataCache: MutableMap<String, String>? = null


    private var todayDistanceKm: Double = 0.0
    private var todayWalkingMinutes: Int = 0


    private var currentHour = -1
    private var hourlySteps: MutableMap<Int, Int> = mutableMapOf()
    private var hourlyDistances: MutableMap<Int, Double> = mutableMapOf()
    private var hourlyWalkingMinutes: MutableMap<Int, Int> = mutableMapOf()
    private var activeMinuteBucketsToday = mutableSetOf<Int>()

    companion object {
        private const val TAG = "PhysicalActivityManager"
        private const val PREF_LAST_STEP_COUNT = "last_step_count"
        private const val PREF_TODAY_STEP_COUNT = "today_step_count"
        private const val PREF_LAST_RESET_DATE = "last_reset_date"
        private const val PREF_TOTAL_STEPS_BASE = "total_steps_base"
        private const val PREF_TODAY_WALKING_MINUTES = "today_walking_minutes"
        private const val PREF_ACTIVE_MINUTE_BUCKETS = "active_minute_buckets"
        private const val PREF_HISTORICAL_DATA = "historical_activity_data"
        private const val PREF_HOURLY_DATA = "hourly_activity_data"
        private const val PREF_STRIDE_LENGTH_METERS = "stride_length_meters"
        private const val PREF_USER_HEIGHT_CM = "user_height_cm"

        private const val SAVE_INTERVAL_MS = 30 * 1000L
        private const val MIN_STEPS_FOR_SAVE = 1
        private const val DEFAULT_STEP_LENGTH_METERS = 0.75
        private const val MIN_STEP_LENGTH_METERS = 0.45
        private const val MAX_STEP_LENGTH_METERS = 0.90
    }

    init {
        initializeSensors()

    }





    fun initializeAsync(autoStartTracking: Boolean = true, onComplete: (() -> Unit)? = null) {
        Thread {
            try {
                loadSavedData()
                handler.post {
                    if (autoStartTracking && hasActivityRecognitionPermission()) {
                        startTracking()
                    }
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during async initialization", e)
            }
        }.start()
    }

    private fun initializeSensors() {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepCounterSensor == null && stepDetectorSensor == null) {
            Log.w(TAG, "No step sensors available on this device")
        }
    }

    private fun loadSavedData() {
        lastStepCount = prefs.getInt(PREF_LAST_STEP_COUNT, 0)
        todayStepCount = prefs.getInt(PREF_TODAY_STEP_COUNT, 0)
        todayWalkingMinutes = prefs.getInt(PREF_TODAY_WALKING_MINUTES, 0)
        lastResetDate = prefs.getString(PREF_LAST_RESET_DATE, "") ?: ""
        totalStepsBase = prefs.getInt(PREF_TOTAL_STEPS_BASE, 0)
        activeMinuteBucketsToday = prefs.getString(PREF_ACTIVE_MINUTE_BUCKETS, null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()


        if (lastStepCount < 0) lastStepCount = 0
        if (todayStepCount < 0) todayStepCount = 0
        if (todayWalkingMinutes < 0) todayWalkingMinutes = 0

        val today = getCurrentDate()
        if (lastResetDate != today) {
            resetDailyCount()
        } else {

            loadHourlyData(today)

            todayDistanceKm = distanceKmFromSteps(todayStepCount)
            if (todayWalkingMinutes == 0 && hourlyWalkingMinutes.isNotEmpty()) {
                todayWalkingMinutes = hourlyWalkingMinutes.values.sum()
            }

            if (todayDistanceKm < 0.0) todayDistanceKm = 0.0
        }
        isDataLoaded = true
    }

    private fun ensureDataLoaded() {
        if (!isDataLoaded) {
            loadSavedData()
        }
    }

    fun getCurrentDate(): String {
        return dateFormatter.format(Date())
    }




    fun getStrideLengthMeters(): Double {
        val stored = prefs.getFloat(PREF_STRIDE_LENGTH_METERS, -1f)
        if (stored > 0) {
            return normalizeStrideLengthMeters(stored.toDouble())
        }

        val heightCm = prefs.getInt(PREF_USER_HEIGHT_CM, -1)
        if (heightCm > 0) {

            return normalizeStrideLengthMeters((heightCm * 0.43) / 100.0)
        }
        return DEFAULT_STEP_LENGTH_METERS
    }




    fun setStrideLengthMeters(meters: Double) {
        val normalized = normalizeStrideLengthMeters(meters)
        prefs.edit { putFloat(PREF_STRIDE_LENGTH_METERS, normalized.toFloat()) }

        todayDistanceKm = distanceKmFromSteps(todayStepCount)

        hourlyDistances.clear()
        hourlySteps.forEach { (hour, steps) ->
            hourlyDistances[hour] = distanceKmFromSteps(steps)
        }
    }




    fun setUserHeightCm(heightCm: Int) {
        prefs.edit { putInt(PREF_USER_HEIGHT_CM, heightCm.coerceIn(120, 230)) }

        val calculatedStride = (heightCm.coerceIn(120, 230) * 0.43) / 100.0
        setStrideLengthMeters(calculatedStride)
    }




    fun getUserHeightCm(): Int {
        return prefs.getInt(PREF_USER_HEIGHT_CM, -1)
    }




    fun resetStrideLengthToDefault() {
        prefs.edit {
            remove(PREF_STRIDE_LENGTH_METERS)
            remove(PREF_USER_HEIGHT_CM)
        }
        todayDistanceKm = distanceKmFromSteps(todayStepCount)
        hourlyDistances.clear()
        hourlySteps.forEach { (hour, steps) ->
            hourlyDistances[hour] = distanceKmFromSteps(steps)
        }
    }

    private fun resetDailyCount() {
        val today = getCurrentDate()

        if (lastResetDate.isNotEmpty()) {
            saveCurrentData(lastResetDate)
        }

        if (lastResetDate.isNotEmpty() && lastResetDate != today && todayStepCount > 0) {
            saveHistoricalData(lastResetDate, todayStepCount, todayDistanceKm, todayWalkingMinutes)
            saveHourlyData(lastResetDate, hourlySteps, hourlyDistances, hourlyWalkingMinutes)
        }

        lastResetDate = today
        todayStepCount = 0
        todayDistanceKm = 0.0
        todayWalkingMinutes = 0
        lastStepCount = 0
        hourlySteps.clear()
        hourlyDistances.clear()
        hourlyWalkingMinutes.clear()
        activeMinuteBucketsToday.clear()
        currentHour = -1


        prefs.edit {
            putString(PREF_LAST_RESET_DATE, today)
            putInt(PREF_TODAY_STEP_COUNT, 0)
            putInt(PREF_TODAY_WALKING_MINUTES, 0)
            putInt(PREF_LAST_STEP_COUNT, 0)
            remove(PREF_ACTIVE_MINUTE_BUCKETS)
        }


        historicalDataCache = null
        hourlyDataCache = null
    }

    private fun saveHistoricalData(date: String, steps: Int, distanceKm: Double, walkingMinutes: Int = 0) {

        val historicalData = historicalDataCache ?: getHistoricalData()


        historicalData[date] = ActivityData(
            steps,
            distanceKmFromSteps(steps),
            date,
            walkingMinutes.takeIf { it > 0 } ?: estimateWalkingMinutesFromSteps(steps)
        )
        historicalDataCache = historicalData


        val jsonString = prefs.getString(PREF_HISTORICAL_DATA, null)
        val json = if (jsonString != null) {
            try {
                org.json.JSONObject(jsonString)
            } catch (_: Exception) {
                org.json.JSONObject()
            }
        } else {
            org.json.JSONObject()
        }


        val resolvedWalkingMinutes = walkingMinutes.takeIf { it > 0 } ?: estimateWalkingMinutesFromSteps(steps)
        val resolvedDistanceKm = distanceKmFromSteps(steps)
        json.put(date, "$steps|$resolvedDistanceKm|$resolvedWalkingMinutes")


        prefs.edit { putString(PREF_HISTORICAL_DATA, json.toString()) }
    }

    private fun getHistoricalData(forceRefresh: Boolean = false): MutableMap<String, ActivityData> {
        val cache = historicalDataCache
        if (!forceRefresh && cache != null) {
            return cache
        }

        val data = mutableMapOf<String, ActivityData>()
        val jsonString = prefs.getString(PREF_HISTORICAL_DATA, null)
        if (jsonString != null) {
            try {
                val json = org.json.JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val date = keys.next()
                    val value = json.getString(date)
                    val parts = value.split("|")
                    if (parts.size >= 2) {
                        val steps = parts[0].toIntOrNull() ?: 0
                        val distance = distanceKmFromSteps(steps)
                        val walkingMinutes = parts.getOrNull(2)?.toIntOrNull()
                            ?: estimateWalkingMinutesFromSteps(steps)
                        data[date] = ActivityData(steps, distance, date, walkingMinutes)
                    }
                }
            } catch (_: Exception) {
            }
        }
        historicalDataCache = data
        return data
    }

    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun startTracking() {
        ensureDataLoaded()

        if (!hasActivityRecognitionPermission()) {
            Log.w(TAG, "Activity recognition permission not granted")
            return
        }

        if (isListening) {
            return
        }


        val today = getCurrentDate()
        if (lastResetDate != today) {
            resetDailyCount()
        } else {

            if (currentHour == -1) {
                calendar.timeInMillis = System.currentTimeMillis()
                currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            }
        }


        var registered = false
        stepCounterSensor?.let { sensor ->
            registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) || registered
        }
        stepDetectorSensor?.let { sensor ->
            registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) || registered
        }

        if (registered) {
            isListening = true
        } else {
            Log.w(TAG, "No step sensors available")
        }
    }

    fun stopTracking() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false

            saveRunnable?.let { handler.removeCallbacks(it) }

            saveCurrentData()
        }
    }

    private fun scheduleSave() {

        val stepsChanged = todayStepCount - lastSavedStepCount
        if (stepsChanged < MIN_STEPS_FOR_SAVE) {

            saveRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                saveCurrentData()
            }
            saveRunnable = runnable
            handler.postDelayed(runnable, SAVE_INTERVAL_MS)
        } else {

            saveRunnable?.let { handler.removeCallbacks(it) }
            saveCurrentData()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        ensureDataLoaded()

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {

                val currentTotalSteps = event.values[0].toInt()


                if (currentTotalSteps < 0) {
                    Log.w(TAG, "Invalid step count from sensor: $currentTotalSteps")
                    return
                }

                val today = getCurrentDate()
                if (lastResetDate != today) {
                    resetDailyCount()
                    totalStepsBase = currentTotalSteps
                    lastStepCount = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    scheduleSave()
                    return
                }

                if (totalStepsBase == 0 && todayStepCount == 0 && lastStepCount == 0) {
                    totalStepsBase = currentTotalSteps
                    lastStepCount = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    return
                }

                if (currentTotalSteps < lastStepCount) {
                    Log.w(TAG, "Step counter reset detected: current=$currentTotalSteps, last=$lastStepCount, preserving today=$todayStepCount")
                    totalStepsBase = currentTotalSteps - todayStepCount
                }

                val exactTodaySteps = (currentTotalSteps - totalStepsBase).coerceAtLeast(0)
                val deltaSteps = exactTodaySteps - todayStepCount

                if (deltaSteps > 0) {
                    val strideLength = getStrideLengthMeters()
                    val distanceKm = (deltaSteps * strideLength) / 1000.0
                    updateHourlySteps(deltaSteps, distanceKm)
                } else if (deltaSteps < 0) {
                    Log.w(TAG, "Correcting negative daily delta: $deltaSteps (current=$currentTotalSteps, base=$totalStepsBase, today=$todayStepCount)")
                }

                todayStepCount = exactTodaySteps
                todayDistanceKm = distanceKmFromSteps(todayStepCount)
                lastStepCount = currentTotalSteps
                scheduleSave()
            }
            Sensor.TYPE_STEP_DETECTOR -> {

                if (event.values[0] == 1.0f) {
                    recordWalkingMinute()

                    if (stepCounterSensor == null) {
                        todayStepCount++
                        val distanceKm = distanceKmFromSteps(1)
                        todayDistanceKm += distanceKm
                        updateHourlySteps(1, distanceKm)
                    }
                    scheduleSave()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun updateHourlySteps(steps: Int, distanceKm: Double) {
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        currentHour = hour
        hourlySteps[hour] = (hourlySteps[hour] ?: 0) + steps
        hourlyDistances[hour] = (hourlyDistances[hour] ?: 0.0) + distanceKm
    }

    private fun recordWalkingMinute() {
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minuteOfDay = hour * 60 + calendar.get(Calendar.MINUTE)
        if (activeMinuteBucketsToday.add(minuteOfDay)) {
            todayWalkingMinutes += 1
            hourlyWalkingMinutes[hour] = (hourlyWalkingMinutes[hour] ?: 0) + 1
        }
    }

    private fun saveCurrentData(date: String = getCurrentDate()) {
        if (todayStepCount < 0) {
            Log.w(TAG, "Invalid step count for saving: $todayStepCount")
            return
        }
        if (todayDistanceKm < 0.0) {
            Log.w(TAG, "Invalid distance for saving: $todayDistanceKm")
            todayDistanceKm = 0.0
        }


        if (todayStepCount > 100000) {
            Log.w(TAG, "Unrealistic step count detected: $todayStepCount, not saving")
            return
        }
        if (todayDistanceKm > 100.0) {
            Log.w(TAG, "Unrealistic distance detected: $todayDistanceKm km, not saving")
            return
        }
        prefs.edit {
            putInt(PREF_TODAY_STEP_COUNT, todayStepCount)
            putInt(PREF_TODAY_WALKING_MINUTES, todayWalkingMinutes)
            putInt(PREF_LAST_STEP_COUNT, lastStepCount)
            putInt(PREF_TOTAL_STEPS_BASE, totalStepsBase)
            putString(PREF_ACTIVE_MINUTE_BUCKETS, activeMinuteBucketsToday.joinToString(","))
        }
        lastSavedStepCount = todayStepCount
        if (todayStepCount > 0) {
            saveHistoricalData(date, todayStepCount, todayDistanceKm, todayWalkingMinutes)
            saveHourlyData(date, hourlySteps, hourlyDistances, hourlyWalkingMinutes)
        }
    }

    private fun saveHourlyData(
        date: String,
        hourlyData: Map<Int, Int>,
        hourlyDistances: Map<Int, Double>,
        hourlyWalkingMinutes: Map<Int, Int>
    ) {
        val dateKey = "hourly_$date"


        val allHourlyData = hourlyDataCache ?: getHourlyDataMap()


        val json = org.json.JSONObject()
        hourlyData.forEach { (hour, steps) ->
            val distanceKm = distanceKmFromSteps(steps)
            val walkingMinutes = hourlyWalkingMinutes[hour] ?: 0
            json.put(hour.toString(), "$steps|$distanceKm|$walkingMinutes")
        }


        allHourlyData[dateKey] = json.toString()
        hourlyDataCache = allHourlyData


        val mainJson = org.json.JSONObject()
        allHourlyData.forEach { (key, data) ->
            mainJson.put(key, data)
        }


        prefs.edit { putString(PREF_HOURLY_DATA, mainJson.toString()) }
    }

    private fun getHourlyDataMap(forceRefresh: Boolean = false): MutableMap<String, String> {
        val cache = hourlyDataCache
        if (!forceRefresh && cache != null) {
            return cache
        }

        val map = mutableMapOf<String, String>()
        val jsonString = prefs.getString(PREF_HOURLY_DATA, null)
        if (jsonString != null) {
            try {
                val json = org.json.JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.getString(key)
                }
            } catch (_: Exception) {
            }
        }
        hourlyDataCache = map
        return map
    }

    private fun loadHourlyData(date: String) {
        val dateKey = "hourly_$date"
        val allHourlyData = getHourlyDataMap(forceRefresh = true)

        val todayJsonStr = allHourlyData[dateKey]
        if (todayJsonStr != null) {
            try {
                val json = org.json.JSONObject(todayJsonStr)
                hourlySteps.clear()
                hourlyDistances.clear()
                hourlyWalkingMinutes.clear()
                todayDistanceKm = 0.0
                todayWalkingMinutes = 0

                val keys = json.keys()
                while (keys.hasNext()) {
                    val hourStr = keys.next()
                    val value = json.getString(hourStr)
                    val parts = value.split("|")
                    if (parts.size >= 2) {
                        val hour = hourStr.toIntOrNull() ?: continue
                        val steps = parts[0].toIntOrNull() ?: 0
                        val distance = distanceKmFromSteps(steps)
                        val walkingMinutes = parts.getOrNull(2)?.toIntOrNull()
                            ?: estimateWalkingMinutesFromSteps(steps)
                        hourlySteps[hour] = steps
                        hourlyDistances[hour] = distance
                        hourlyWalkingMinutes[hour] = walkingMinutes
                        todayDistanceKm += distance
                        todayWalkingMinutes += walkingMinutes
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getHourlyActivityForDate(date: String): List<HourlyActivityData> {
        val dateKey = "hourly_$date"
        val allHourlyData = getHourlyDataMap(forceRefresh = true)
        val jsonStr = allHourlyData[dateKey]

        val list = mutableListOf<HourlyActivityData>()
        if (jsonStr != null) {
            try {
                val json = org.json.JSONObject(jsonStr)

                for (hour in 0..23) {
                    val value = json.optString(hour.toString(), "0|0.0")
                    val parts = value.split("|")
                    if (parts.size >= 2) {
                        val steps = parts[0].toIntOrNull() ?: 0
                        val distance = distanceKmFromSteps(steps)
                        val walkingMinutes = parts.getOrNull(2)?.toIntOrNull()
                            ?: estimateWalkingMinutesFromSteps(steps)
                        list.add(HourlyActivityData(hour, steps, distance, walkingMinutes))
                    } else {
                        list.add(HourlyActivityData(hour, 0, 0.0, 0))
                    }
                }
            } catch (_: Exception) {
            }
        } else {

            for (hour in 0..23) {
                list.add(HourlyActivityData(hour, 0, 0.0, 0))
            }
        }
        return list
    }

    private fun getActivityFromHourlyData(date: String): ActivityData? {
        val hourlyData = getHourlyActivityForDate(date)
        val totalSteps = hourlyData.sumOf { it.steps }
        val totalDistance = hourlyData.sumOf { it.distanceKm }
        val totalWalkingMinutes = hourlyData.sumOf { it.walkingMinutes }
        return if (totalSteps > 0 || totalDistance > 0.0 || totalWalkingMinutes > 0) {
            ActivityData(totalSteps, totalDistance, date, totalWalkingMinutes)
        } else {
            null
        }
    }




    fun getActivityForDate(date: String): ActivityData {
        val historicalData = getHistoricalData(forceRefresh = true)
        val historical = historicalData[date]
        val hourly = getActivityFromHourlyData(date)
        return when {
            hourly != null -> hourly
            historical != null -> historical
            else -> ActivityData(0, 0.0, date, 0)
        }
    }




    fun getTodayActivity(): ActivityData {
        ensureDataLoaded()

        if (!isListening) {
            return getTodayActivityFromPrefs()
        }


        if (todayStepCount > 100000 || todayDistanceKm > 100.0) {
            Log.w(TAG, "Corrupted data detected: steps=$todayStepCount, distance=$todayDistanceKm km")

            todayStepCount = 0
            todayDistanceKm = 0.0
            hourlySteps.clear()
            hourlyDistances.clear()

        }


        val validSteps = todayStepCount.coerceAtLeast(0)
        val validDistance = todayDistanceKm.coerceAtLeast(0.0)

        if (validSteps != todayStepCount || validDistance != todayDistanceKm) {
            Log.w(TAG, "Data validation corrected: steps $todayStepCount->$validSteps, distance $todayDistanceKm->$validDistance")

            if (todayStepCount < 0) todayStepCount = 0
            if (todayDistanceKm < 0.0) todayDistanceKm = 0.0
        }

        val walkingMinutes = todayWalkingMinutes
            .takeIf { it > 0 }
            ?.coerceAtLeast(0)
            ?: estimateWalkingMinutesFromSteps(validSteps)
        return ActivityData(validSteps, validDistance, getCurrentDate(), walkingMinutes)
    }




    fun getMonthlyActivity(year: Int, month: Int): Map<String, ActivityData> {
        val historicalData = getHistoricalData(forceRefresh = true)
        val monthPrefix = String.format(Locale.getDefault(), "%d-%02d", year, month)
        val monthlyData = historicalData
            .filter { it.key.startsWith(monthPrefix) }
            .toMutableMap()

        val hourlyKeys = getHourlyDataMap(forceRefresh = true).keys
        hourlyKeys
            .filter { it.startsWith("hourly_$monthPrefix") }
            .forEach { key ->
                val date = key.removePrefix("hourly_")
                getActivityFromHourlyData(date)?.let { monthlyData[date] = it }
            }

        val todayActivity = getTodayActivityFromPrefs()
        if (todayActivity.date.startsWith(monthPrefix)) {
            monthlyData[todayActivity.date] = todayActivity
        }
        return monthlyData
    }




    fun getAllHistoricalDates(): Set<String> {
        return getHistoricalData().keys
    }

    fun getTodaySteps(): Int = getTodayActivity().steps

    fun getTodayDistanceKm(): Double = getTodayActivity().distanceKm




    fun resetAllData() {
        Log.w(TAG, "Resetting all activity data due to corruption")


        todayStepCount = 0
        todayDistanceKm = 0.0
        todayWalkingMinutes = 0
        lastStepCount = 0
        totalStepsBase = 0
        lastResetDate = ""
        hourlySteps.clear()
        hourlyDistances.clear()
        hourlyWalkingMinutes.clear()
        activeMinuteBucketsToday.clear()
        currentHour = -1


        prefs.edit {
            remove(PREF_LAST_STEP_COUNT)
            remove(PREF_TODAY_STEP_COUNT)
            remove(PREF_TODAY_WALKING_MINUTES)
            remove(PREF_LAST_RESET_DATE)
            remove(PREF_TOTAL_STEPS_BASE)
            remove(PREF_ACTIVE_MINUTE_BUCKETS)
            remove(PREF_HISTORICAL_DATA)
            remove(PREF_HOURLY_DATA)
            remove(PREF_STRIDE_LENGTH_METERS)
            remove(PREF_USER_HEIGHT_CM)
        }


        historicalDataCache = null
        hourlyDataCache = null
    }

    fun cleanup() {
        stopTracking()
    }

    private fun getTodayActivityFromPrefs(): ActivityData {
        val today = getCurrentDate()
        val steps = prefs.getInt(PREF_TODAY_STEP_COUNT, 0).coerceAtLeast(0)
        val walkingMinutes = prefs.getInt(PREF_TODAY_WALKING_MINUTES, 0)
            .takeIf { it > 0 }
            ?.coerceAtLeast(0)
            ?: estimateWalkingMinutesFromSteps(steps)
        val distanceKm = distanceKmFromSteps(steps)
        return ActivityData(steps, distanceKm, today, walkingMinutes)
    }

    private fun estimateWalkingMinutesFromSteps(steps: Int): Int {
        if (steps <= 0) return 0
        return ((steps + 99) / 100).coerceAtLeast(1)
    }

    private fun normalizeStrideLengthMeters(meters: Double): Double {
        return meters.coerceIn(MIN_STEP_LENGTH_METERS, MAX_STEP_LENGTH_METERS)
    }

    private fun distanceKmFromSteps(steps: Int): Double {
        return ((steps.coerceAtLeast(0) * getStrideLengthMeters()) / 1000.0).coerceAtLeast(0.0)
    }
}
