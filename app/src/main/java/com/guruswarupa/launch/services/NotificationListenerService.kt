package com.guruswarupa.launch.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import java.lang.ref.WeakReference
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

class LaunchNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "LaunchNotificationListener"
        private var _instance: WeakReference<LaunchNotificationListenerService>? = null
        var instance: LaunchNotificationListenerService?
            get() = _instance?.get()
            private set(value) {
                _instance = value?.let { WeakReference(it) }
            }

        private val badgeCounts = ConcurrentHashMap<String, Int>()

        var onBadgeCountsChanged: (() -> Unit)? = null

        fun getBadgeCount(packageName: String): Int = badgeCounts[packageName] ?: 0
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
        recomputeBadgeCounts()
    }

    override fun onListenerDisconnected() {
        isListenerConnected = false
        badgeCounts.clear()
        onBadgeCountsChanged?.invoke()
        try {
            super.onListenerDisconnected()
        } catch (e: Exception) {

        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationPosted(sbn)
                recomputeBadgeCounts()
            }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationRemoved(sbn)
                recomputeBadgeCounts()
            }
        } catch (_: Exception) {
        }
    }

    private fun recomputeBadgeCounts() {
        try {
            val counts = mutableMapOf<String, Int>()
            for (sbn in getActiveNotifications()) {
                val notification = sbn.notification ?: continue
                if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            }
            badgeCounts.clear()
            badgeCounts.putAll(counts)
            onBadgeCountsChanged?.invoke()
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
        } catch (_: Exception) {
        }
    }

    fun dismissNotificationByKey(key: String) {
        try {
            if (isListenerConnected) {
                cancelNotification(key)
            }
        } catch (e: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
