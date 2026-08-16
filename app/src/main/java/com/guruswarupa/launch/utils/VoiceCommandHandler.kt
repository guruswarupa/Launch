package com.guruswarupa.launch.utils

import com.guruswarupa.launch.R
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.ContactsContract
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.net.toUri
import com.guruswarupa.launch.models.Constants




class VoiceCommandHandler(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val packageManager: android.content.pm.PackageManager,
    private val contentResolver: ContentResolver,
    private val searchBox: AutoCompleteTextView,
    private val appList: List<ResolveInfo>
) {





    fun handleCommand(command: String): Boolean {
        return when {
            command.startsWith("WhatsApp ", ignoreCase = true) -> {
                val contactName = command.substringAfter("WhatsApp ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    openWhatsAppChat(contactName)
                    searchBox.text.clear()
                    true
                } ?: false
            }
            command.startsWith("send ", ignoreCase = true) && command.contains(" to ", ignoreCase = true) -> {
                val parts = command.split(" to ", ignoreCase = true)
                if (parts.size == 2) {
                    val message = parts[0].substringAfter("send ").trim()
                    val contactName = parts[1].trim()
                    val phoneNumber = getPhoneNumberForContact(contactName)

                    phoneNumber?.let {
                        sendWhatsAppMessage(it, message)
                        searchBox.text.clear()
                        true
                    } ?: run {
                        Toast.makeText(activity, activity.getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
                        false
                    }
                } else false
            }
            command.startsWith("message ", ignoreCase = true) -> {
                val contactName = command.substringAfter("message ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    openSMSChat(contactName)
                    searchBox.text.clear()
                    true
                } ?: false
            }
            command.startsWith("call ", ignoreCase = true) -> {
                val contactName = command.substringAfter("call ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {

                    val callIntent = Intent(Intent.ACTION_DIAL)
                    callIntent.data = "tel:$it".toUri()
                    activity.startActivity(callIntent)
                    searchBox.text.clear()
                    true
                } ?: false
            }
            command.startsWith("search ", ignoreCase = true) -> {
                val query = command.substringAfter("search ", "").trim()
                val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                val engine = prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")
                val baseUrl = when (engine) {
                    "Bing" -> "https://www.bing.com/search?q="
                    "DuckDuckGo" -> "https://duckduckgo.com/?q="
                    "Ecosia" -> "https://www.ecosia.org/search?q="
                    "Brave" -> "https://search.brave.com/search?q="
                    "Startpage" -> "https://www.startpage.com/sp/search?query="
                    "Yahoo" -> "https://search.yahoo.com/search?p="
                    "Qwant" -> "https://www.qwant.com/?q="
                    else -> "https://www.google.com/search?q="
                }
                val searchIntent = Intent(Intent.ACTION_VIEW, "$baseUrl${Uri.encode(query)}".toUri())
                activity.startActivity(searchIntent)
                searchBox.text.clear()
                true
            }
            command.startsWith("open ", ignoreCase = true) -> {
                val appName = command.substringAfter("open ", "").trim()
                if (appName.isNotEmpty()) {
                    val normalizedAppName = appName.lowercase()
                    val matchingApp = appList.find { resolveInfo ->
                        resolveInfo.safeLabel(packageManager) == normalizedAppName
                    } ?: appList.find { resolveInfo ->
                        resolveInfo.safeLabel(packageManager)?.contains(normalizedAppName) == true
                    }

                    matchingApp?.let { app ->
                        val packageName = app.safePackageName() ?: return@let false
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.let { launchIntent ->
                            activity.startActivity(launchIntent)
                            searchBox.text.clear()
                            true
                        } ?: false
                    } ?: false
                } else false
            }
            command.startsWith("uninstall ", ignoreCase = true) -> {
                val appName = command.substringAfter("uninstall ", "").trim()
                if (appName.isNotEmpty()) {
                    val normalizedAppName = appName.lowercase()
                    val matchingApp = appList.find { resolveInfo ->
                        resolveInfo.safeLabel(packageManager)?.contains(normalizedAppName) == true
                    }

                    matchingApp?.let { app ->
                        val packageName = app.safePackageName() ?: return@let false
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = "package:$packageName".toUri()
                        }
                        activity.startActivity(intent)
                        searchBox.text.clear()
                        true
                    } ?: false
                } else false
            }
            command.contains(" to ", ignoreCase = true) -> {
                val locations = command.split(" to ", ignoreCase = true)
                if (locations.size == 2) {
                    val origin = locations[0].trim()
                    val destination = locations[1].trim()

                    if (origin.isNotEmpty() || destination.isNotEmpty()) {
                        val uriString = "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(destination)}&travelmode=driving"
                        val mapIntent = Intent(Intent.ACTION_VIEW, uriString.toUri())
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            activity.startActivity(mapIntent)
                            searchBox.text.clear()
                            true
                        } catch (_: Exception) {
                            Toast.makeText(activity, activity.getString(R.string.toast_google_maps_not_installed), Toast.LENGTH_SHORT).show()
                            false
                        }
                    } else false
                } else false
            }
            else -> false
        }
    }

    private fun getPhoneNumberForContact(contactName: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        fun normalize(name: String): List<String> {
            return name.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .split(" ")
                .filter { it.isNotBlank() }
        }

        val inputParts = normalize(contactName)
        val seenNames = mutableSetOf<String>()
        val matches = mutableListOf<Pair<String, String>>()

        cursor?.use { cursorObj ->
            while (cursorObj.moveToNext()) {
                val name = cursorObj.getString(0)?.trim() ?: continue
                val number = cursorObj.getString(1)?.trim() ?: continue

                if (number.isEmpty() || !number.any { char -> char.isDigit() }) continue

                val nameParts = normalize(name)

                if (inputParts.any { input -> nameParts.any { part -> part.contains(input) } }) {
                    if (!seenNames.contains(name.lowercase())) {
                        matches.add(name to number)
                        seenNames.add(name.lowercase())
                    }
                }
            }
        }

        return matches.minByOrNull { (name, _) ->
            val norm = normalize(name).joinToString(" ")
            when {
                norm == inputParts.joinToString(" ") -> 0
                norm.startsWith(inputParts.joinToString(" ")) -> 1
                else -> 2
            }
        }?.second
    }

    private fun sendWhatsAppMessage(phoneNumber: String, message: String) {
        try {
            val formattedPhoneNumber = phoneNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            val uriString = "https://wa.me/${Uri.encode(formattedPhoneNumber)}?text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uriString.toUri()
                setPackage("com.whatsapp")
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_whatsapp_not_installed_or_failed_to_open_message), Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsAppChat(contactName: String) {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val phoneNumber = it.getString(0)
                    .replace(" ", "")
                    .replace("-", "")
                    .replace("(", "")
                    .replace(")", "")

                try {
                    val uriString = "https://wa.me/${Uri.encode(phoneNumber)}"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = uriString.toUri()
                        setPackage("com.whatsapp")
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(activity, activity.getString(R.string.toast_whatsapp_not_installed), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openSMSChat(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "smsto:$phoneNumber".toUri()
            putExtra("sms_body", "")
        }

        try {
            if (intent.resolveActivity(packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_no_sms_app_installed), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_failed_to_open_messaging_app), Toast.LENGTH_SHORT).show()
        }
    }

    private fun ResolveInfo.safeLabel(packageManager: android.content.pm.PackageManager): String? {
        val component = safeComponentInfo() ?: return null
        return try {
            loadLabel(packageManager)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.lowercase()
                ?: component.packageName.lowercase()
        } catch (_: RuntimeException) {
            component.packageName.lowercase()
        }
    }

    private fun ResolveInfo.safePackageName(): String? = safeComponentInfo()?.packageName

    private fun ResolveInfo.safeComponentInfo(): ComponentInfo? {
        return activityInfo ?: serviceInfo ?: providerInfo
    }
}
