package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView

class FocusModeDialogs(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val pomodoroManager: PomodoroManager,
    private val onShowPomodoroSettings: () -> Unit,
    private val onEnableFocusMode: (durationMinutes: Int, enableDnd: Boolean, modeType: String) -> Unit
) {

    fun showDurationPicker() {
        val durations = arrayOf(pomodoroManager.getModeLabel(), "15 minutes", "30 minutes", "1 hour", "2 hours", "4 hours", "Custom")
        val durationValues = arrayOf(-2, 15, 30, 60, 120, 240, -1)

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_select_focus_mode_duration))
            .setItems(durations) { _, which ->
                when (durationValues[which]) {
                    -2 -> pomodoroManager.startPomodoro()
                    -1 -> showCustomDurationDialog()
                    else -> showTypeDialog(durationValues[which])
                }
            }
            .setNeutralButton(context.getString(R.string.dlg_pomodoro_settings)) { _, _ ->
                onShowPomodoroSettings()
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showTypeDialog(durationMinutes: Int) {
        val modes = arrayOf("Strict Mode", "Casual Mode")

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_select_focus_mode_type))
            .setSingleChoiceItems(modes, 0) { dialog, which ->
                val modeType = if (which == 0) Constants.Prefs.FOCUS_MODE_TYPE_STRICT else Constants.Prefs.FOCUS_MODE_TYPE_CASUAL
                sharedPreferences.edit { putString(Constants.Prefs.FOCUS_MODE_TYPE, modeType) }
                dialog.dismiss()
                promptForDnd(durationMinutes, modeType)
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showCustomDurationDialog() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter minutes (1-480)"
            DialogStyler.styleInput(context, this)
        }

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_custom_duration))
            .setMessage(context.getString(R.string.dlg_enter_duration_in_minutes))
            .setDialogInputView(context, input)
            .setPositiveButton(context.getString(R.string.next)) { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes in 1..480) {
                    showTypeDialog(minutes)
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_please_enter_a_duration_between_1_480_min), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun promptForDnd(durationMinutes: Int, modeType: String) {
        val modeLabel = if (modeType == Constants.Prefs.FOCUS_MODE_TYPE_STRICT) "Strict" else "Casual"
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_enable_do_not_disturb))
            .setMessage(context.getString(R.string.dlg_mode_would_you_like_to_enable_do_not_disturb_mod, modeLabel))
            .setPositiveButton(context.getString(R.string.dlg_yes)) { _, _ ->
                onEnableFocusMode(durationMinutes, true, modeType)
            }
            .setNegativeButton(context.getString(R.string.dlg_no)) { _, _ ->
                onEnableFocusMode(durationMinutes, false, modeType)
            }
            .setNeutralButton(context.getString(R.string.cancel_button), null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }
}
