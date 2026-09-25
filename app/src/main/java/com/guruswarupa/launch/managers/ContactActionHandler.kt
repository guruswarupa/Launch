package com.guruswarupa.launch.managers

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.widget.AutoCompleteTextView
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.utils.VoiceCommandHandler

class ContactActionHandler(
    private val activity: FragmentActivity,
    private val packageManager: PackageManager,
    private val contentResolver: ContentResolver,
    private val searchBox: AutoCompleteTextView,
    private val appList: List<ResolveInfo>,
    private val onHandlerCreated: (VoiceCommandHandler) -> Unit
) {
    private var voiceCommandHandler: VoiceCommandHandler? = null

    private fun getHandler(): VoiceCommandHandler {
        return voiceCommandHandler ?: VoiceCommandHandler(
            activity,
            packageManager,
            contentResolver,
            searchBox,
            appList
        ).also {
            voiceCommandHandler = it
            onHandlerCreated(it)
        }
    }

    fun openWhatsAppChat(contactName: String) {
        getHandler().openWhatsAppChat(contactName)
    }

    fun openSMSChat(contactName: String) {
        getHandler().openSMSChat(contactName)
    }
}
