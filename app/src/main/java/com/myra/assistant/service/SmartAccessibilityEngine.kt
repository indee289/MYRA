package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Singleton accessibility engine restored from CFR-decompiled bytecode.
 * Holds the [AccessibilityService] set by AccessibilityHelperService and
 * performs clicks / typing / scrolling / TTS on the active window tree.
 * All nullable params of [click] default to null (as in the original Kotlin
 * defaults recovered from the click$default synthetic).
 */
object SmartAccessibilityEngine {

    private const val TAG = "MYRA_ENGINE"
    private val handler = Handler(Looper.getMainLooper())

    // Backing field kept under a different name so the explicit
    // getService()/setService() below do not clash with a property accessor.
    private var boundService: AccessibilityService? = null

    fun getService(): AccessibilityService? = boundService

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakReceiver: BroadcastReceiver? = null

    fun setService(service: AccessibilityService?) {
        boundService = service
        if (service != null) {
            initTts(service)
            registerSpeakReceiver(service)
            Log.d(TAG, "Service set, TTS + SpeakReceiver ready")
        }
    }

    fun unregisterReceiver() {
        val receiver = speakReceiver
        if (receiver != null) {
            try {
                boundService?.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // empty catch block
            }
            speakReceiver = null
        }
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    fun speak(text: String) {
        if (ttsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ENGINE")
            Log.d(TAG, "TTS speak: $text")
        } else {
            Log.w(TAG, "TTS not ready, trying to init...")
            boundService?.let { initTts(it) }
            handler.postDelayed({
                if (ttsReady) {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ENGINE")
                }
            }, 500L)
        }
    }

    fun click(text: String? = null, id: String? = null, contentDesc: String? = null): Boolean {
        val root = root() ?: return false
        if (id != null) {
            for (node in root.findAccessibilityNodeInfosByViewId(id)) {
                if (tryClick(node)) {
                    Log.d(TAG, "✅ Clicked by ID: $id")
                    return true
                }
            }
        }
        if (text != null) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                val nodeText = node.text?.toString() ?: ""
                if (nodeText.equals(text, ignoreCase = true) && tryClick(node)) {
                    Log.d(TAG, "✅ Clicked by exact text: $text")
                    return true
                }
            }
            for (node in nodes) {
                if (tryClick(node)) {
                    Log.d(TAG, "✅ Clicked by partial text: $text")
                    return true
                }
            }
        }
        if (contentDesc != null && searchByDescAndClick(root, contentDesc)) {
            Log.d(TAG, "✅ Clicked by desc: $contentDesc")
            return true
        }
        Log.w(TAG, "❌ Click failed: text=$text, id=$id, desc=$contentDesc")
        return false
    }

    fun typeText(text: String): Boolean {
        val root = root()
        if (root == null) {
            Log.w(TAG, "typeText: root is null")
            return false
        }
        val editText = findEditText(root)
        if (editText == null) {
            Log.w(TAG, "typeText: no EditText found")
            return false
        }
        editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text)
        }
        val ok = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "typeText '$text': $ok")
        return ok
    }

    fun pressImeEnter(): Boolean {
        val root = root() ?: return false
        val editText = findEditText(root) ?: return false
        // 0x10000004 — literal action id as decompiled (IME Enter).
        return editText.performAction(0x10000004)
    }

    fun scrollDown(): Boolean {
        val root = root() ?: return false
        return scrollNode(root, true)
    }

    fun scrollUp(): Boolean {
        val root = root() ?: return false
        return scrollNode(root, false)
    }

    fun goBack() {
        boundService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun goHome() {
        boundService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun whatsappSmartSend(pkg: String): Boolean {
        return click(id = "$pkg:id/send") ||
            click(text = "Send") ||
            click(contentDesc = "Send")
    }

    fun whatsappSmartCall(video: Boolean): Boolean {
        return if (video) {
            click(contentDesc = "Video call") || click(text = "Video call")
        } else {
            click(contentDesc = "Voice call") ||
                click(contentDesc = "Audio call") ||
                click(text = "Call")
        }
    }

    fun youtubePlay(): Boolean {
        return click(contentDesc = "Play") ||
            click(text = "Play") ||
            click(text = "Search")
    }

    fun spotifyPlay(): Boolean {
        return click(text = "Play") ||
            click(text = "Shuffle play") ||
            click(contentDesc = "Play")
    }

    private fun root(): AccessibilityNodeInfo? {
        return boundService?.rootInActiveWindow
    }

    private fun initTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("hi", "IN")
                Log.d(TAG, "TTS ready")
            }
        }
    }

    private fun registerSpeakReceiver(service: AccessibilityService) {
        val old = speakReceiver
        if (old != null) {
            try {
                service.unregisterReceiver(old)
            } catch (e: Exception) {
                // empty catch block
            }
        }
        speakReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra("text")
                if (text != null) {
                    Log.d(TAG, "Speak broadcast: $text")
                    speak(text)
                }
            }
        }
        val filter = IntentFilter("com.myra.assistant.SPEAK_TEXT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(speakReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            service.registerReceiver(speakReceiver, filter)
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val className = node.className?.toString() ?: ""
        if (!className.contains("EditText") && !className.contains("edit", ignoreCase = true)) {
            if (node.isEditable) return node
            for (i in 0 until node.childCount) {
                val found = findEditText(node.getChild(i))
                if (found != null) return found
            }
            return null
        }
        return node
    }

    private fun tryClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 6) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun searchByDescAndClick(node: AccessibilityNodeInfo?, desc: String): Boolean {
        if (node == null) return false
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (contentDesc.contains(desc, ignoreCase = true) && tryClick(node)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (searchByDescAndClick(node.getChild(i), desc)) return true
        }
        return false
    }

    private fun scrollNode(node: AccessibilityNodeInfo, down: Boolean): Boolean {
        if (node.isScrollable) {
            val action = if (down) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && scrollNode(child, down)) return true
        }
        return false
    }
}
