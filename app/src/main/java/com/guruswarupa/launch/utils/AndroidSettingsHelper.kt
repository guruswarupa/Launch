package com.guruswarupa.launch.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import android.os.Build

object AndroidSettingsHelper {

    data class SettingInfo(
        val title: String,
        val description: String,
        val action: String,
        val searchKeywords: List<String> = emptyList()
    )

    @SuppressLint("InlinedApi")
    fun getAllSystemSettings(): List<SettingInfo> {
        val settingsList = mutableListOf<SettingInfo>()

        settingsList.add(
            SettingInfo(
                title = "Wi-Fi",
                description = "Connect to Wi-Fi networks",
                action = Settings.ACTION_WIFI_SETTINGS,
                searchKeywords = listOf("wifi", "wi-fi", "wireless", "network", "internet", "connection")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Bluetooth",
                description = "Pair and connect to Bluetooth devices",
                action = Settings.ACTION_BLUETOOTH_SETTINGS,
                searchKeywords = listOf("bluetooth", "pair", "connect", "device", "headphones", "speaker")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Data Usage",
                description = "Manage mobile data usage",
                action = Settings.ACTION_DATA_USAGE_SETTINGS,
                searchKeywords = listOf("data", "usage", "mobile data", "cellular", "traffic", "bandwidth")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Mobile Network",
                description = "Configure mobile network settings",
                action = Settings.ACTION_DATA_ROAMING_SETTINGS,
                searchKeywords = listOf("mobile", "network", "cellular", "roaming", "carrier", "signal")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Airplane Mode",
                description = "Turn on airplane mode",
                action = Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                searchKeywords = listOf("airplane", "flight", "mode", "offline", "disconnect")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Sound",
                description = "Adjust volume and sound settings",
                action = Settings.ACTION_SOUND_SETTINGS,
                searchKeywords = listOf("sound", "volume", "ringtone", "notification", "audio", "mute")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Display",
                description = "Adjust brightness and wallpaper",
                action = Settings.ACTION_DISPLAY_SETTINGS,
                searchKeywords = listOf("display", "brightness", "wallpaper", "screen", "resolution", "theme")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Notifications",
                description = "Manage notification settings",
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                searchKeywords = listOf("notifications", "alerts", "messages", "popups", "permissions")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Location",
                description = "Manage location services",
                action = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                searchKeywords = listOf("location", "gps", "map", "position", "coordinates", "services")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Security",
                description = "Security and screen lock settings",
                action = Settings.ACTION_SECURITY_SETTINGS,
                searchKeywords = listOf("security", "lock", "password", "pin", "pattern", "fingerprint", "biometric")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Privacy",
                description = "Privacy and permission settings",
                action = Settings.ACTION_PRIVACY_SETTINGS,
                searchKeywords = listOf("privacy", "permission", "data", "personal", "tracking", "consent")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Accessibility",
                description = "Accessibility features and services",
                action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                searchKeywords = listOf("accessibility", "vision", "hearing", "motor", "features", "help")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Storage",
                description = "Manage phone storage",
                action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                searchKeywords = listOf("storage", "memory", "space", "disk", "internal", "files")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Battery",
                description = "Battery usage and optimization",
                action = Settings.ACTION_BATTERY_SAVER_SETTINGS,
                searchKeywords = listOf("battery", "power", "saver", "optimization", "charge", "life")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Applications",
                description = "Manage installed applications",
                action = Settings.ACTION_APPLICATION_SETTINGS,
                searchKeywords = listOf("applications", "apps", "installed", "manage", "storage", "permissions")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Developer Options",
                description = "Advanced developer settings",
                action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                searchKeywords = listOf("developer", "debug", "advanced", "adb", "usb", "options")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Date & Time",
                description = "Set date, time, and time zone",
                action = Settings.ACTION_DATE_SETTINGS,
                searchKeywords = listOf("date", "time", "timezone", "clock", "format", "sync")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Language & Input",
                description = "Language and keyboard settings",
                action = Settings.ACTION_INPUT_METHOD_SETTINGS,
                searchKeywords = listOf("language", "input", "keyboard", "text", "typing", "ime")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Accounts",
                description = "Manage accounts and sync",
                action = Settings.ACTION_SYNC_SETTINGS,
                searchKeywords = listOf("accounts", "sync", "google", "email", "cloud", "backup")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "About Phone",
                description = "Phone information and software",
                action = Settings.ACTION_DEVICE_INFO_SETTINGS,
                searchKeywords = listOf("about", "phone", "info", "software", "version", "model", "imei")
            )
        )

        settingsList.add(
            SettingInfo(
                title = "Data Saver",
                description = "Restrict background data usage",
                action = "android.settings.DATA_SAVER_SETTINGS",
                searchKeywords = listOf("data saver", "data saving", "restrict", "background", "bandwidth")
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsList.add(
                SettingInfo(
                    title = "App Info",
                    description = "Detailed app information and controls",
                    action = Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
                    searchKeywords = listOf("app info", "application info", "details", "manage apps", "all apps")
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settingsList.add(
                SettingInfo(
                    title = "Digital Wellbeing",
                    description = "Track and manage app usage",
                    action = "android.settings.DIGITAL_WELLBEING_SETTINGS",
                    searchKeywords = listOf("wellbeing", "digital wellbeing", "usage", "screen time", "health")
                )
            )
        }

        return settingsList
    }

    fun searchSettings(query: String): List<SettingInfo> {
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.isEmpty()) return emptyList()

        return getAllSystemSettings().filter { setting ->
            setting.title.lowercase().contains(lowerQuery) ||
            setting.description.lowercase().contains(lowerQuery) ||
            setting.searchKeywords.any { it.contains(lowerQuery) }
        }
    }

    fun createSettingsIntent(settingAction: String): Intent {
        return try {
            Intent(settingAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {

            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
