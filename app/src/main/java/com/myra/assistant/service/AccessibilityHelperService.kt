package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). NOTE: the kapt stub at
 * app/build/tmp/kapt3/stubs/debug/.../service/AccessibilityHelperService.java
 * is an older, stripped version ("Stub Accessibility Service - Automation
 * removed") — it lacks onUnbind, forceRefreshRoot and the Companion's
 * lastRoot / currentPackage / refreshRoot members. The dex (CFR) version
 * below is authoritative and is what was restored here.
 */
class AccessibilityHelperService : AccessibilityService() {

    fun forceRefreshRoot(): AccessibilityNodeInfo? {
        var root = rootInActiveWindow
        if (root != null) {
            try {
                lastRoot = root
            } catch (e: Exception) {
                root = lastRoot ?: return null
            }
        }
        return root
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val pkg = event.packageName?.toString() ?: currentPackage
            currentPackage = pkg
            val root = rootInActiveWindow
            if (root != null) {
                lastRoot = root
            }
            AccessibilityEventRouter.handle(this, event)
        } catch (e: Exception) {
            Log.e(TAG, "Event Error: ${e.message}")
        }
    }

    override fun onDestroy() {
        SmartAccessibilityEngine.unregisterReceiver()
        instance = null
        lastRoot = null
        SmartAccessibilityEngine.setService(null)
        Log.w(TAG, "Accessibility Destroyed")
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        SmartAccessibilityEngine.setService(this)
        val info = AccessibilityServiceInfo()
        info.eventTypes = 2105
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = 82
        info.notificationTimeout = 80L
        setServiceInfo(info)
        Log.d(TAG, "✅ Accessibility Connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        SmartAccessibilityEngine.unregisterReceiver()
        instance = null
        lastRoot = null
        SmartAccessibilityEngine.setService(null)
        Log.w(TAG, "Accessibility Unbound")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "MYRA_ACCESS"

        @Volatile
        var instance: AccessibilityHelperService? = null

        @Volatile
        var lastRoot: AccessibilityNodeInfo? = null

        @Volatile
        var currentPackage: String = ""

        fun getRoot(): AccessibilityNodeInfo? {
            val service = instance
            if (service != null) {
                val root = service.rootInActiveWindow
                if (root != null) return root
            }
            return try {
                lastRoot
            } catch (e: Exception) {
                lastRoot
            }
        }

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return try {
                val componentName = ComponentName(context, AccessibilityHelperService::class.java)
                enabledServices.contains(componentName.flattenToString())
            } catch (e: Exception) {
                false
            }
        }

        fun refreshRoot(): AccessibilityNodeInfo? {
            val service = instance ?: return null
            var root = service.rootInActiveWindow ?: return null
            try {
                lastRoot = root
            } catch (e: Exception) {
                root = lastRoot ?: return null
            }
            return root
        }
    }
}
