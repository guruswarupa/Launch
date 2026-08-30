package com.guruswarupa.launch.utils

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

object DialogStyler {
    fun styleInput(context: Context, editText: EditText) {
        val horizontal = context.dpToPx(16)
        val vertical = context.dpToPx(14)
        editText.setTextColor(ThemeManager.color(context, R.attr.appTextPrimary))
        editText.setHintTextColor(ThemeManager.color(context, R.attr.appTextSecondary))
        editText.background = ContextCompat.getDrawable(context, R.drawable.dialog_input_background)
        editText.setPadding(horizontal, vertical, horizontal, vertical)
        editText.minimumHeight = context.dpToPx(52)
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    fun styleDialog(dialog: AlertDialog) {
        val context = dialog.context
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val themeColor = TypographyManager.getConfiguredFontColor(context) ?: ThemeManager.color(context, R.attr.appTextPrimary)
        val secondaryColor = ThemeManager.color(context, R.attr.appTextSecondary)

        applyDialogTranslucency(dialog, prefs)

        dialog.setOnShowListener {

            val titleView = dialog.findViewById<TextView>(context.resources.getIdentifier("alertTitle", "id", "android"))
                ?: dialog.findViewById<TextView>(android.R.id.title)

            titleView?.setTextColor(themeColor)


            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(themeColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondaryColor)


            dialog.listView?.let { listView ->
                for (i in 0 until listView.childCount) {
                    val itemView = listView.getChildAt(i)
                    if (itemView is TextView) {
                        itemView.setTextColor(themeColor)
                    }
                }
            }
        }
    }

    private fun applyDialogTranslucency(dialog: AlertDialog, prefs: android.content.SharedPreferences) {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        // appScrim carries the RGB base for this overlay (opaque black on today's dark palettes,
        // opaque white on the Light palette) — resolving it here rather than hardcoding
        // Color.argb(alpha, 0, 0, 0) is what makes dialogs readable once a light theme exists.
        val scrimBase = ThemeManager.color(dialog.context, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
    }




    fun createThemedTextAdapter(context: Context, items: Array<String>): ArrayAdapter<String> {
        val themeColor = TypographyManager.getConfiguredFontColor(context) ?: ThemeManager.color(context, R.attr.appTextPrimary)
        return object : ArrayAdapter<String>(context, android.R.layout.select_dialog_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                if (view is TextView) {
                    view.setTextColor(themeColor)
                }
                return view
            }
        }
    }
}
