package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.widget.Toast
import androidx.core.content.edit
import com.guruswarupa.launch.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class PomodoroManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val focusModeManager: FocusModeManager
) {
    data class PomodoroConfig(
        val workMinutes: Int,
        val shortBreakMinutes: Int,
        val longBreakMinutes: Int,
        val longBreakInterval: Int
    )

    data class PomodoroSessionRecord(
        val completedAtMillis: Long,
        val durationMinutes: Int,
        val cycleNumber: Int
    )

    data class PomodoroStats(
        val completedSessions: Int,
        val totalFocusMinutes: Int,
        val todaySessions: Int,
        val currentCycle: Int,
        val recentSessions: List<PomodoroSessionRecord>
    )

    companion object {
        const val PREF_POMODORO_STATE = "pomodoro_state"
        const val PREF_POMODORO_END_TIME = "pomodoro_end_time"
        const val PREF_POMODORO_CYCLE_COUNT = "pomodoro_cycle_count"
        private const val PREF_POMODORO_WORK_MINUTES = "pomodoro_work_minutes"
        private const val PREF_POMODORO_BREAK_MINUTES = "pomodoro_break_minutes"
        private const val PREF_POMODORO_LONG_BREAK_MINUTES = "pomodoro_long_break_minutes"
        private const val PREF_POMODORO_LONG_BREAK_INTERVAL = "pomodoro_long_break_interval"
        private const val PREF_POMODORO_HISTORY = "pomodoro_history"
        private const val MAX_HISTORY_ENTRIES = 100

        const val STATE_INACTIVE = "inactive"
        const val STATE_WORK = "work"
        const val STATE_BREAK = "break"
        const val STATE_LONG_BREAK = "long_break"

        private const val DEFAULT_WORK_MINUTES = 25
        private const val DEFAULT_BREAK_MINUTES = 5
        private const val DEFAULT_LONG_BREAK_MINUTES = 20
        private const val DEFAULT_LONG_BREAK_INTERVAL = 4
    }

    private var currentTimer: CountDownTimer? = null
    var onTimerTick: ((remainingMillis: Long, state: String) -> Unit)? = null
    var onStateChanged: ((state: String, isFocusMode: Boolean) -> Unit)? = null
    var onSessionEnded: (() -> Unit)? = null

    fun isPomodoroActive(): Boolean {
        return sharedPreferences.getString(PREF_POMODORO_STATE, STATE_INACTIVE) != STATE_INACTIVE
    }

    fun getCurrentState(): String {
        return sharedPreferences.getString(PREF_POMODORO_STATE, STATE_INACTIVE) ?: STATE_INACTIVE
    }

    fun getConfig(): PomodoroConfig {
        return PomodoroConfig(
            workMinutes = sharedPreferences.getInt(PREF_POMODORO_WORK_MINUTES, DEFAULT_WORK_MINUTES),
            shortBreakMinutes = sharedPreferences.getInt(PREF_POMODORO_BREAK_MINUTES, DEFAULT_BREAK_MINUTES),
            longBreakMinutes = sharedPreferences.getInt(PREF_POMODORO_LONG_BREAK_MINUTES, DEFAULT_LONG_BREAK_MINUTES),
            longBreakInterval = sharedPreferences.getInt(PREF_POMODORO_LONG_BREAK_INTERVAL, DEFAULT_LONG_BREAK_INTERVAL)
        )
    }

    fun updateConfig(workMinutes: Int, shortBreakMinutes: Int, longBreakMinutes: Int, longBreakInterval: Int) {
        sharedPreferences.edit {
            putInt(PREF_POMODORO_WORK_MINUTES, workMinutes)
            putInt(PREF_POMODORO_BREAK_MINUTES, shortBreakMinutes)
            putInt(PREF_POMODORO_LONG_BREAK_MINUTES, longBreakMinutes)
            putInt(PREF_POMODORO_LONG_BREAK_INTERVAL, longBreakInterval)
        }
    }

    fun getModeLabel(): String {
        val config = getConfig()
        return "Pomodoro (${config.workMinutes}/${config.shortBreakMinutes}/${config.longBreakMinutes})"
    }

    fun getSessionStats(recentLimit: Int = 5): PomodoroStats {
        val history = loadHistory()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return PomodoroStats(
            completedSessions = history.size,
            totalFocusMinutes = history.sumOf { it.durationMinutes },
            todaySessions = history.count { it.completedAtMillis >= todayStart },
            currentCycle = sharedPreferences.getInt(PREF_POMODORO_CYCLE_COUNT, 0),
            recentSessions = history.take(recentLimit)
        )
    }

    fun startPomodoro() {
        currentTimer?.cancel()
        sharedPreferences.edit {
            putInt(PREF_POMODORO_CYCLE_COUNT, 0)
        }
        startWorkSession()
    }

    private fun startWorkSession() {
        val duration = getConfig().workMinutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + duration
        sharedPreferences.edit {
            putString(PREF_POMODORO_STATE, STATE_WORK)
            putLong(PREF_POMODORO_END_TIME, endTime)
        }

        focusModeManager.setFocusModeEnabled(true)
        onStateChanged?.invoke(STATE_WORK, true)
        startTimer(duration, STATE_WORK)
        Toast.makeText(context, context.getString(R.string.toast_session_started_m, context.getString(R.string.pomodoro_work), getConfig().workMinutes), Toast.LENGTH_SHORT).show()
    }

    private fun startBreakSession(isLongBreak: Boolean) {
        val config = getConfig()
        val breakMinutes = if (isLongBreak) config.longBreakMinutes else config.shortBreakMinutes
        val state = if (isLongBreak) STATE_LONG_BREAK else STATE_BREAK
        val endTime = System.currentTimeMillis() + (breakMinutes * 60 * 1000L)
        sharedPreferences.edit {
            putString(PREF_POMODORO_STATE, state)
            putLong(PREF_POMODORO_END_TIME, endTime)
        }

        focusModeManager.setFocusModeEnabled(false)
        onStateChanged?.invoke(state, false)
        startTimer(breakMinutes * 60 * 1000L, state)
        val label = if (isLongBreak) context.getString(R.string.pomodoro_long_break) else context.getString(R.string.pomodoro_break)
        Toast.makeText(context, context.getString(R.string.toast_session_started_m, label, breakMinutes), Toast.LENGTH_SHORT).show()
    }

    fun stopPomodoro() {
        currentTimer?.cancel()
        currentTimer = null

        sharedPreferences.edit {
            putString(PREF_POMODORO_STATE, STATE_INACTIVE)
            remove(PREF_POMODORO_END_TIME)
            remove(PREF_POMODORO_CYCLE_COUNT)
        }

        focusModeManager.setFocusModeEnabled(false)
        onSessionEnded?.invoke()
    }

    private fun startTimer(duration: Long, state: String) {
        currentTimer?.cancel()
        currentTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                onTimerTick?.invoke(millisUntilFinished, state)
            }

            override fun onFinish() {
                if (state == STATE_WORK) {
                    completeWorkSession()
                } else {
                    startWorkSession()
                }
            }
        }.start()
    }

    fun resumeIfNeeded() {
        val state = getCurrentState()
        if (state == STATE_INACTIVE) return

        val endTime = sharedPreferences.getLong(PREF_POMODORO_END_TIME, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime >= endTime) {
            if (state == STATE_WORK) {
                completeWorkSession()
            } else {
                startWorkSession()
            }
        } else {
            startTimer(endTime - currentTime, state)
        }
    }

    private fun completeWorkSession() {
        val config = getConfig()
        val cycles = sharedPreferences.getInt(PREF_POMODORO_CYCLE_COUNT, 0) + 1
        sharedPreferences.edit { putInt(PREF_POMODORO_CYCLE_COUNT, cycles) }
        appendHistory(
            PomodoroSessionRecord(
                completedAtMillis = System.currentTimeMillis(),
                durationMinutes = config.workMinutes,
                cycleNumber = cycles
            )
        )
        val useLongBreak = cycles % config.longBreakInterval == 0
        startBreakSession(useLongBreak)
    }

    private fun appendHistory(record: PomodoroSessionRecord) {
        val updatedHistory = ArrayList<PomodoroSessionRecord>(loadHistory())
        updatedHistory.add(0, record)
        while (updatedHistory.size > MAX_HISTORY_ENTRIES) {
            updatedHistory.removeAt(updatedHistory.lastIndex)
        }
        val jsonArray = JSONArray()
        updatedHistory.forEach { session ->
            jsonArray.put(
                JSONObject()
                    .put("completedAtMillis", session.completedAtMillis)
                    .put("durationMinutes", session.durationMinutes)
                    .put("cycleNumber", session.cycleNumber)
            )
        }
        sharedPreferences.edit {
            putString(PREF_POMODORO_HISTORY, jsonArray.toString())
        }
    }

    private fun loadHistory(): List<PomodoroSessionRecord> {
        val serialized = sharedPreferences.getString(PREF_POMODORO_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(serialized)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    add(
                        PomodoroSessionRecord(
                            completedAtMillis = item.optLong("completedAtMillis"),
                            durationMinutes = item.optInt("durationMinutes"),
                            cycleNumber = item.optInt("cycleNumber")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
