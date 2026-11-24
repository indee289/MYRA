package com.myra.assistant.service

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.automation.SpotifyAutomation
import com.myra.assistant.automation.WhatsAppAutomation
import com.myra.assistant.automation.YouTubeAutomation
import com.myra.assistant.model.AIState
import com.myra.assistant.security.AppLockActivity
import com.myra.assistant.security.BiometricManager
import com.myra.assistant.security.SecurityManager
import com.myra.assistant.ui.main.MainActivity

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). Routes accessibility events to the app-lock
 * screen, the unlock prompt and the WhatsApp / YouTube / Spotify automations.
 */
object AccessibilityEventRouter {

    private const val TAG = "MYRA_ROUTER"
    private const val MAX_WAIT_TIME = 30000L

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage: String = ""
    private var waitStartTime: Long = 0L
    private var automationRun: Boolean = false

    fun handle(service: AccessibilityHelperService, event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == service.packageName) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val isLauncher = pkg == "com.android.launcher3" ||
            pkg == "com.sec.android.app.launcher" ||
            pkg == "com.google.android.apps.nexuslauncher" ||
            pkg.contains("launcher")

        if (isLauncher && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !AppLockActivity.isUnlockedThisSession &&
            SecurityManager.isAppLockEnabled(service)
        ) {
            Log.d(TAG, "🔒 Home Screen Locked. Opening Lock Screen.")
            handler.postDelayed({
                val intent = Intent(service, AppLockActivity::class.java)
                intent.addFlags(0x14000000)
                intent.putExtra("locked_package", pkg)
                service.startActivity(intent)
            }, 50L)
        }

        val keyguardManager = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            Log.d(TAG, "📱 Device is locked. Current package: $pkg")
            if (AIState.task != null && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                System.currentTimeMillis() - waitStartTime > 5000L
            ) {
                speak(service, "Kripya apne phone ko unlock karein taaki main ye kaam kar sakun.")
                waitStartTime = System.currentTimeMillis()
            }
        }

        if ((eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
            !AppLockActivity.isUnlockedThisSession &&
            SecurityManager.isPackageLocked(service, pkg) &&
            pkg != service.packageName &&
            pkg != "com.myra.assistant"
        ) {
            Log.d(TAG, "🔒 App Locked: $pkg. Opening Lock Screen.")
            handler.postDelayed({
                val intent = Intent(service, AppLockActivity::class.java)
                intent.addFlags(0x10000000)
                intent.addFlags(0x04000000)
                intent.addFlags(0x00010000)
                intent.putExtra("locked_package", pkg)
                service.startActivity(intent)
            }, 50L)
        }

        val root = service.rootInActiveWindow ?: return
        val screenState = ScreenAnalyzer.analyze(root)
        Log.d(TAG, "Screen: ${screenState.screenType} | Confidence: ${screenState.confidence}")

        val task = AIState.task
        if (task != null) {
            if (BiometricManager.isBiometricEnabled(service) &&
                !BiometricManager.isSessionValid(service)
            ) {
                Log.w(TAG, "Biometric session expired, skipping automation")
                return
            }
            if (waitStartTime == 0L) {
                waitStartTime = System.currentTimeMillis()
            }
            if (System.currentTimeMillis() - waitStartTime > MAX_WAIT_TIME) {
                Log.d(TAG, "Timeout waiting for task completion, resetting")
                reset()
                return
            }
            if (isWhatsAppOpen(pkg)) {
                if (screenState.screenType == ScreenAnalyzer.ScreenType.WHATSAPP_HOME ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.WHATSAPP_CHAT ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.WHATSAPP_CALLING
                ) {
                    WhatsAppAutomation.performWhatsAppFlow(root, task)
                }
            } else if (isYouTubeOpen(pkg)) {
                if (screenState.screenType == ScreenAnalyzer.ScreenType.YOUTUBE_HOME ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.YOUTUBE_SEARCH ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.YOUTUBE_RESULTS ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.YOUTUBE_PLAYER
                ) {
                    YouTubeAutomation.performYouTubeFlow(root, task)
                }
            } else if (isSpotifyOpen(pkg)) {
                if (screenState.screenType == ScreenAnalyzer.ScreenType.SPOTIFY_HOME ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.SPOTIFY_SEARCH ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.SPOTIFY_RESULTS ||
                    screenState.screenType == ScreenAnalyzer.ScreenType.SPOTIFY_PLAYER
                ) {
                    SpotifyAutomation.performSpotifyFlow(root, task)
                }
            }
        }
    }

    fun reset() {
        Log.d(TAG, "♻️ Resetting router state")
        waitStartTime = 0L
        automationRun = false
        AIState.reset()
    }

    private fun resetState() {
        waitStartTime = 0L
        automationRun = false
    }

    private fun isWhatsAppOpen(pkg: String): Boolean {
        return pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"
    }

    private fun isYouTubeOpen(pkg: String): Boolean {
        return pkg == "com.google.android.youtube"
    }

    private fun isSpotifyOpen(pkg: String): Boolean {
        return pkg == "com.spotify.music"
    }

    private fun speak(context: Context, text: String) {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(0x10000000)
        intent.putExtra("SPEAK_TEXT", text)
        context.startActivity(intent)
    }
}
