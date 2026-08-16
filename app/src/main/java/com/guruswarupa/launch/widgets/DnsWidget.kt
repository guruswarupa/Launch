package com.guruswarupa.launch.widgets

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.guruswarupa.launch.R

data class DnsProvider(
    val id: String,
    val name: String,
    val hostname: String,
    val description: String,
    val isAdBlocking: Boolean = false
) {
    fun toJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", id)
            put("name", name)
            put("hostname", hostname)
            put("description", description)
            put("isAdBlocking", isAdBlocking)
        }
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): DnsProvider {
            return DnsProvider(
                id = json.getString("id"),
                name = json.getString("name"),
                hostname = json.getString("hostname"),
                description = json.getString("description"),
                isAdBlocking = json.optBoolean("isAdBlocking", false)
            )
        }

        val DEFAULT_PROVIDERS = listOf(
            DnsProvider("current", "Automatic", "opportunistic", "System default (Automatic)", false),
            DnsProvider("off", "Off", "off", "Disable Private DNS", false),
            DnsProvider("google", "Google DNS", "dns.google", "Fast and reliable DNS by Google", false),
            DnsProvider("cloudflare", "Cloudflare DNS", "1dot1dot1dot1.cloudflare-dns.com", "Privacy-focused DNS", false),
            DnsProvider("cloudflare_security", "Cloudflare Security", "security.cloudflare-dns.com", "Blocks malware", true),
            DnsProvider("cloudflare_family", "Cloudflare Family", "family.cloudflare-dns.com", "Blocks malware and adult content", true),
            DnsProvider("adguard", "AdGuard DNS", "dns.adguard-dns.com", "Blocks ads, trackers, and malware", true),
            DnsProvider("adguard_family", "AdGuard Family", "family.adguard-dns.com", "Blocks ads, malware, and adult content", true),
            DnsProvider("quad9", "Quad9 DNS", "dns.quad9.net", "Blocks malicious domains", true),
            DnsProvider("cleanbrowsing", "CleanBrowsing", "family-filter-dns.cleanbrowsing.org", "Family filter DNS", true),
            DnsProvider("nextdns", "NextDNS", "dns.nextdns.io", "Cloud-based DNS", false)
        )
    }
}

class DnsWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: SharedPreferences
) : InitializableWidget {

    private lateinit var currentDnsText: TextView
    private lateinit var changeButton: View
    private lateinit var widgetContainer: LinearLayout
    private lateinit var widgetView: View

    private var currentProvider: DnsProvider? = null

    private var isInitialized = false

    companion object {
        private const val PREFS_DNS_KEY = "dns_widget_current"
        private const val PREFS_CUSTOM_DNS_KEY = "dns_widget_custom_providers"
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_dns, container, false)
        container.addView(widgetView)

        currentDnsText = widgetView.findViewById(R.id.current_dns_text)
        changeButton = widgetView.findViewById(R.id.change_dns_button)
        widgetContainer = widgetView.findViewById(R.id.dns_widget_container)

        changeButton.setOnClickListener {
            showDnsProviderDialog()
        }

        loadCurrentDns()
        updateDisplay()

        isInitialized = true
    }

    private fun loadCurrentDns() {
        try {
            val dnsJson = sharedPreferences.getString(PREFS_DNS_KEY, null)
            if (dnsJson != null) {
                val json = org.json.JSONObject(dnsJson)
                currentProvider = DnsProvider.fromJson(json)
            } else {
                currentProvider = DnsProvider.DEFAULT_PROVIDERS[0]
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentProvider = DnsProvider.DEFAULT_PROVIDERS[0]
        }
    }

    private fun saveCurrentDns(provider: DnsProvider) {
        try {
            sharedPreferences.edit {
                putString(PREFS_DNS_KEY, provider.toJson().toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateDisplay() {
        currentProvider?.let { provider ->
            currentDnsText.text = provider.name
            currentDnsText.setTextColor(
                if (provider.isAdBlocking) {
                    context.getColor(R.color.nord14)
                } else {
                    context.getColor(R.color.text)
                }
            )
        }
    }

    private fun showDnsProviderDialog() {
        val providers = DnsProvider.DEFAULT_PROVIDERS + getCustomProviders()
        val providerNames = providers.map {
            "${it.name}${if (it.isAdBlocking) " (Ad-Block)" else ""}"
        }.toTypedArray()

        val currentIndex = providers.indexOfFirst { it.id == currentProvider?.id }

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_select_dns_provider))
            .setSingleChoiceItems(providerNames, currentIndex) { dialog, which ->
                val selectedProvider = providers[which]
                applyDnsProvider(selectedProvider)
                dialog.dismiss()
            }
            .setPositiveButton(context.getString(R.string.cancel_button), null)
            .setNegativeButton(context.getString(R.string.dlg_add_custom)) { _, _ ->
                showAddCustomDnsDialog()
            }
            .show()

        fixDialogTextColors(dialog)
    }

    private fun applyDnsProvider(provider: DnsProvider) {
        try {
            val mode = when (provider.id) {
                "current" -> "opportunistic"
                "off" -> "off"
                else -> "hostname"
            }

            android.provider.Settings.Global.putString(
                context.contentResolver,
                "private_dns_mode",
                mode
            )

            if (mode == "hostname") {
                android.provider.Settings.Global.putString(
                    context.contentResolver,
                    "private_dns_specifier",
                    provider.hostname
                )
            }

            currentProvider = provider
            saveCurrentDns(provider)
            updateDisplay()

            Toast.makeText(context, context.getString(R.string.toast_dns_changed_to, provider.name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            showPermissionErrorDialog()
        }
    }

    private fun showPermissionErrorDialog() {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_permission_required))
            .setMessage(context.getString(R.string.dlg_modifying_private_dns_requires_the_write_secure) +
                    "Please run this command via ADB:\n\n" +
                    "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            .setPositiveButton(context.getString(R.string.dlg_copy_command)) { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.toast_command_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(context.getString(R.string.support_thank_you_close), null)
            .show()
    }

    private fun showAddCustomDnsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_dns, null)
        val nameEditText = dialogView.findViewById<android.widget.EditText>(R.id.custom_dns_name)
        val hostnameEditText = dialogView.findViewById<android.widget.EditText>(R.id.custom_dns_hostname)
        val descriptionEditText = dialogView.findViewById<android.widget.EditText>(R.id.custom_dns_description)
        val adBlockingCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.ad_blocking_checkbox)

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_add_custom_dns))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save_button)) { _, _ ->
                val name = nameEditText.text.toString().trim()
                val hostname = hostnameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()
                val isAdBlocking = adBlockingCheckbox.isChecked

                if (name.isEmpty() || hostname.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.toast_name_and_hostname_are_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val customProvider = DnsProvider(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name,
                    hostname = hostname,
                    description = description.ifEmpty { "Custom DNS provider" },
                    isAdBlocking = isAdBlocking
                )

                saveCustomProvider(customProvider)
                applyDnsProvider(customProvider)
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .show()

        fixDialogTextColors(dialog)
    }

    private fun getCustomProviders(): List<DnsProvider> {
        try {
            val customJson = sharedPreferences.getString(PREFS_CUSTOM_DNS_KEY, null) ?: return emptyList()
            val jsonArray = org.json.JSONArray(customJson)
            val providers = mutableListOf<DnsProvider>()

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                providers.add(DnsProvider.fromJson(json))
            }

            return providers
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun saveCustomProviders(providers: List<DnsProvider>) {
        try {
            val jsonArray = org.json.JSONArray()
            providers.forEach { provider ->
                jsonArray.put(provider.toJson())
            }
            sharedPreferences.edit {
                putString(PREFS_CUSTOM_DNS_KEY, jsonArray.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCustomProvider(provider: DnsProvider) {
        val customProviders = getCustomProviders().toMutableList()
        customProviders.add(provider)
        saveCustomProviders(customProviders)
    }

    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = context.getColor(R.color.text)
            val listView = dialog.listView
            listView?.post {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(textColor)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun onResume() {
        loadCurrentDns()
        updateDisplay()
    }

    fun onPause() {

    }

    fun cleanup() {

    }
}
