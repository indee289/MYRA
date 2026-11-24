package com.myra.assistant.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import com.myra.assistant.R

/**
 * Shows a full-screen border overlay as visual feedback when an action is
 * performed through the accessibility service.
 *
 * Restored from CFR-decompiled bytecode of the user's own MYRA app
 * (com.myra.assistant). The two synthetic-lambda files
 * (AccessibilityVisualFeedback$$ExternalSyntheticLambda0/1) are folded back
 * in here as the postDelayed lambdas; both just call [removeFeedback].
 */
object AccessibilityVisualFeedback {

    private const val TAG = "MYRA_FEEDBACK"

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: FrameLayout? = null
    private var windowManager: WindowManager? = null

    fun removeFeedback(context: Context) {
        try {
            handler.removeCallbacksAndMessages(null)
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
            }
            Log.d(TAG, "Feedback removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing feedback: ${e.message}")
        }
    }

    fun showActionFeedback(context: Context, actionName: String = "action") {
        try {
            removeFeedback(context)
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlay = FrameLayout(context)
            overlay.setBackgroundResource(R.drawable.accessibility_feedback_border)
            overlayView = overlay
            // LayoutParams(width, height, type, flags, format):
            // MATCH_PARENT x MATCH_PARENT, TYPE_APPLICATION_OVERLAY (2038),
            // FLAG_NOT_FOCUSABLE (8) | FLAG_NOT_TOUCHABLE (16) |
            // FLAG_LAYOUT_IN_SCREEN (256) = 280, PixelFormat.TRANSLUCENT (-3).
            val params = WindowManager.LayoutParams(-1, -1, 2038, 280, -3)
            params.gravity = 0x800033
            windowManager?.addView(overlay, params)
            Log.d(TAG, "Showing feedback for: $actionName")
            handler.postDelayed({ removeFeedback(context) }, 2000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing feedback: ${e.message}")
        }
    }

    fun showElementFeedback(context: Context, elementName: String, durationMs: Long = 1500L) {
        showActionFeedback(context, elementName)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ removeFeedback(context) }, durationMs)
    }
}
