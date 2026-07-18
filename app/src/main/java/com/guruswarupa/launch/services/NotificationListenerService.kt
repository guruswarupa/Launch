package com.guruswarupa.launch.services

import android.service.notification.NotificationListenerService
import java.lang.ref.WeakReference
import android.service.notification.StatusBarNotification
import android.util.Log

class LaunchNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "LaunchNotificationListener"
        private var _instance: WeakReference<LaunchNotificationListenerService>? = null
        var instance: LaunchNotificationListenerService?
            get() = _instance?.get()
            private set(value) {
                _instance = value?.let { WeakReference(it) }
            }
    }

    private var isListenerConnected = false

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        try {

            instance = null
            isListenerConnected = false
        } catch (_: Exception) {
        } finally {
            super.onDestroy()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        try {
            super.onListenerDisconnected()
        } catch (e: Exception) {

            Log.w(TAG, "Error during listener disconnection", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationPosted(sbn)
            }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationRemoved(sbn)
            }
        } catch (_: Exception) {
        }
    }

    override fun getActiveNotifications(): Array<StatusBarNotification> {
        return try {
            if (isListenerConnected) {
                super.getActiveNotifications()
            } else {
                emptyArray()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException getting active notifications", e)
            emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
    }


    @Suppress("unused", "DEPRECATION")
    fun dismissNotification(pkg: String, tag: String?, id: Int) {
        try {
            if (isListenerConnected) {

                cancelNotification(pkg, tag, id)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException dismissing notification", e)
        } catch (_: Exception) {
        }
    }


    fun dismissNotificationByKey(key: String) {
        try {
            if (isListenerConnected) {
                cancelNotification(key)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException dismissing notification by key", e)
        } catch (_: Exception) {
        }
    }
}
