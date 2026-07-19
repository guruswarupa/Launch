package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView
import java.text.DateFormat
import java.util.Locale

class PomodoroSettingsDialog(
    private val context: Context,
    private val pomodoroManager: PomodoroManager
) {

    fun show() {
        val config = pomodoroManager.getConfig()
        val stats = pomodoroManager.getSessionStats()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val statsView = TextView(context).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            text = buildStatsText(stats)
        }

        val descriptionView = TextView(context).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            text = "Set the length of your work sessions, regular breaks, long breaks, and how often a long break should happen."
        }

        val workField = createInputField("Work session length", "Minutes spent focusing before a break starts.", config.workMinutes)
        val shortBreakField = createInputField("Short break length", "Minutes for the regular break after most work sessions.", config.shortBreakMinutes)
        val longBreakField = createInputField("Long break length", "Minutes for the longer recovery break.", config.longBreakMinutes)
        val longBreakIntervalField = createInputField("Long break frequency", "Start a long break after this many completed work sessions.", config.longBreakInterval)

        container.addView(statsView)
        container.addView(descriptionView)
        container.addView(workField)
        container.addView(shortBreakField)
        container.addView(longBreakField)
        container.addView(longBreakIntervalField)

        val scrollView = ScrollView(context).apply {
            addView(container)
        }

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Pomodoro Settings")
            .setView(scrollView)
            .setPositiveButton("Save", null)
            .setNeutralButton("Start", null)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saveConfig(workField, shortBreakField, longBreakField, longBreakIntervalField)) {
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (saveConfig(workField, shortBreakField, longBreakField, longBreakIntervalField)) {
                    pomodoroManager.startPomodoro()
                    dialog.dismiss()
                }
            }
        }

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun saveConfig(
        workField: LinearLayout,
        shortBreakField: LinearLayout,
        longBreakField: LinearLayout,
        longBreakIntervalField: LinearLayout
    ): Boolean {
        val workInput = workField.tag as EditText
        val shortBreakInput = shortBreakField.tag as EditText
        val longBreakInput = longBreakField.tag as EditText
        val longBreakIntervalInput = longBreakIntervalField.tag as EditText
        val workMinutes = workInput.text.toString().toIntOrNull() ?: -1
        val shortBreakMinutes = shortBreakInput.text.toString().toIntOrNull() ?: -1
        val longBreakMinutes = longBreakInput.text.toString().toIntOrNull() ?: -1
        val longBreakInterval = longBreakIntervalInput.text.toString().toIntOrNull() ?: -1

        if (workMinutes !in 1..180 || shortBreakMinutes !in 1..60 || longBreakMinutes !in 1..120 || longBreakInterval !in 2..12) {
            Toast.makeText(context, "Use work 1-180, short break 1-60, long break 1-120, interval 2-12", Toast.LENGTH_LONG).show()
            return false
        }

        pomodoroManager.updateConfig(workMinutes, shortBreakMinutes, longBreakMinutes, longBreakInterval)
        Toast.makeText(context, "Pomodoro settings updated", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun createInputField(title: String, subtitle: String, value: Int): LinearLayout {
        val fieldContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val topPadding = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topPadding, 0, 0)
        }
        val titleView = TextView(context).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            text = title
        }
        val subtitleView = TextView(context).apply {
            setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
            textSize = 13f
            text = subtitle
        }
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            hint = title
            DialogStyler.styleInput(context, this)
        }
        fieldContainer.addView(titleView)
        fieldContainer.addView(subtitleView)
        fieldContainer.addView(input)
        fieldContainer.tag = input
        return fieldContainer
    }

    private fun buildStatsText(stats: PomodoroManager.PomodoroStats): String {
        val recent = if (stats.recentSessions.isEmpty()) {
            "Recent sessions: none yet"
        } else {
            stats.recentSessions.joinToString(separator = "\n", prefix = "Recent sessions:\n") { session ->
                val timestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(session.completedAtMillis)
                "$timestamp  ${session.durationMinutes}m  cycle ${session.cycleNumber}"
            }
        }
        return "Completed sessions: ${stats.completedSessions}\nToday's sessions: ${stats.todaySessions}\nTotal focus time: ${stats.totalFocusMinutes}m\nCurrent cycle: ${stats.currentCycle}\n\n$recent"
    }
}
