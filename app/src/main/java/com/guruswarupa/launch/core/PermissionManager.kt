package com.guruswarupa.launch.core

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.receivers.ScreenOffAdminReceiver
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import com.guruswarupa.launch.services.LaunchNotificationListenerService




class PermissionManager(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        const val CONTACTS_PERMISSION_REQUEST = 100
        const val REQUEST_CODE_CALL_PHONE = 200
        const val SMS_PERMISSION_REQUEST = 300
        const val STORAGE_PERMISSION_REQUEST = 400
        const val VOICE_SEARCH_REQUEST = 500
        const val USAGE_STATS_REQUEST = 600
        const val ACTIVITY_RECOGNITION_REQUEST = 700
        const val NOTIFICATION_PERMISSION_REQUEST = 900
        const val DEVICE_ADMIN_REQUEST = 1000
        const val NOTIFICATION_POLICY_REQUEST = 1100
        const val DEFAULT_LAUNCHER_REQUEST = 1200
    }


    private var isRequestingPermissions = false
    private var pendingContactsGranted: (() -> Unit)? = null




    fun requestContactsPermission(onGranted: () -> Unit = {}) {

        if (isRequestingPermissions) return

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS) ||
                !sharedPreferences.getBoolean(Constants.Prefs.CONTACTS_PERMISSION_DENIED, false)) {
                isRequestingPermissions = true
                pendingContactsGranted = onGranted
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    CONTACTS_PERMISSION_REQUEST
                )
            }
        } else {
            onGranted()
        }
    }




    fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS) ||
                !sharedPreferences.getBoolean("sms_permission_denied", false)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.SEND_SMS),
                    SMS_PERMISSION_REQUEST
                )
            }
        }
    }




    @Suppress("unused")
    fun requestCallPhonePermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CODE_CALL_PHONE
            )
        }
    }




    fun requestUsageStatsPermission(usageStatsManager: AppUsageStatsManager, onComplete: () -> Unit = {}) {

        if (isRequestingPermissions) return

        if (!usageStatsManager.hasUsageStatsPermission()) {
            if (!sharedPreferences.getBoolean(Constants.Prefs.USAGE_STATS_PERMISSION_DENIED, false)) {
                isRequestingPermissions = true
                val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
                    .setTitle(R.string.usage_stats_permission_title)
                    .setMessage(R.string.usage_stats_permission_message)
                    .setPositiveButton(R.string.usage_stats_permission_grant) { _, _ ->

                        sharedPreferences.edit { putBoolean(Constants.Prefs.WAITING_FOR_USAGE_STATS_RETURN, true) }
                        isRequestingPermissions = false
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        activity.startActivity(intent)
                    }
                    .setNegativeButton(R.string.usage_stats_permission_skip) { _, _ ->
                        sharedPreferences.edit { putBoolean(Constants.Prefs.USAGE_STATS_PERMISSION_DENIED, true) }
                        isRequestingPermissions = false
                        onComplete()
                    }
                    .setOnCancelListener {
                        isRequestingPermissions = false
                        onComplete()
                    }
                    .show()

                fixDialogTextColors(dialog)
            } else {
                onComplete()
            }
        } else {
            onComplete()
        }
    }




    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == activity.packageName
    }




    fun requestDefaultLauncher(onComplete: () -> Unit = {}) {
        if (isDefaultLauncher()) {
            onComplete()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, DEFAULT_LAUNCHER_REQUEST)


                onComplete()
            } else {
                onComplete()
            }
        } else {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(Intent.createChooser(intent, "Set Default Launcher"))
            onComplete()
        }
    }




    fun isDeviceAdminActive(): Boolean {
        val devicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(activity, ScreenOffAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(componentName)
    }




    fun requestDeviceAdminPermission() {
        val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.request_device_admin_title))
            .setMessage(activity.getString(R.string.request_device_admin_message))
            .setPositiveButton(activity.getString(R.string.grant_permission)) { _, _ ->
                val componentName = ComponentName(activity, ScreenOffAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.device_admin_description))
                }

                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, DEVICE_ADMIN_REQUEST)
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()

        fixDialogTextColors(dialog)
    }




    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(activity, ScreenLockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }




    fun requestAccessibilityPermission() {
        val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.accessibility_permission_title))
            .setMessage(activity.getString(R.string.accessibility_permission_message))
            .setPositiveButton(activity.getString(R.string.grant_permission)) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()

        fixDialogTextColors(dialog)
    }




    fun isNotificationPolicyAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.isNotificationPolicyAccessGranted
        }
        return false
    }




    fun requestNotificationPolicyPermission() {
        val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.notification_policy_permission_title))
            .setMessage(activity.getString(R.string.notification_policy_permission_message))
            .setPositiveButton(activity.getString(R.string.grant_permission)) { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()

        fixDialogTextColors(dialog)
    }




    fun isNotificationListenerServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(activity.contentResolver, "enabled_notification_listeners")
            ?: return false

        val componentName = ComponentName(activity, LaunchNotificationListenerService::class.java)
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == componentName) {
                return true
            }
        }
        return false
    }




    fun requestNotificationListenerPermission() {
        val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.notification_listener_permission_title))
            .setMessage(activity.getString(R.string.notification_listener_permission_message))
            .setPositiveButton(activity.getString(R.string.grant_permission)) { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()

        fixDialogTextColors(dialog)
    }

    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(activity, R.color.text)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        } catch (_: Exception) {}
    }




    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }




    fun requestStoragePermission(onGranted: () -> Unit = {}) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(activity, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) ||
                !sharedPreferences.getBoolean("storage_permission_denied", false)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(permission),
                    STORAGE_PERMISSION_REQUEST
                )
            }
        } else {
            onGranted()
        }
    }




    fun requestActivityRecognitionPermission(onGranted: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION) ||
                    !sharedPreferences.getBoolean("activity_recognition_permission_denied", false)) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        ACTIVITY_RECOGNITION_REQUEST
                    )
                }
            }
        } else {

            onGranted()
            return
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACTIVITY_RECOGNITION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        }
    }




    @Suppress("unused")
    fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                VOICE_SEARCH_REQUEST
            )
        }
    }




    fun handlePermissionResult(
        requestCode: Int,
        @Suppress("unused") permissions: Array<String>,
        grantResults: IntArray,
        onContactsGranted: () -> Unit = {},
        onCallPhoneGranted: () -> Unit = {},
        onNotificationGranted: () -> Unit = {},
        onStorageGranted: () -> Unit = {},
        onActivityRecognitionGranted: () -> Unit = {}
    ) {

        isRequestingPermissions = false

        when (requestCode) {
            CONTACTS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit { putBoolean(Constants.Prefs.CONTACTS_PERMISSION_DENIED, false) }
                    onContactsGranted()
                    pendingContactsGranted?.invoke()
                    pendingContactsGranted = null
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
                        sharedPreferences.edit { putBoolean(Constants.Prefs.CONTACTS_PERMISSION_DENIED, true) }
                    }
                    pendingContactsGranted = null
                }
            }
            REQUEST_CODE_CALL_PHONE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onCallPhoneGranted()
                }
            }
            SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit { putBoolean("sms_permission_denied", false) }
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)) {
                        sharedPreferences.edit { putBoolean("sms_permission_denied", true) }
                    }
                }
            }
            STORAGE_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit { putBoolean("storage_permission_denied", false) }
                    onStorageGranted()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
                            else Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        sharedPreferences.edit { putBoolean("storage_permission_denied", true) }
                    }
                }
            }
            ACTIVITY_RECOGNITION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit { putBoolean("activity_recognition_permission_denied", false) }
                    onActivityRecognitionGranted()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)) {
                        sharedPreferences.edit { putBoolean("activity_recognition_permission_denied", true) }
                    }
                }
            }
            VOICE_SEARCH_REQUEST -> {

            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onNotificationGranted()
                }
            }
        }
    }
}
