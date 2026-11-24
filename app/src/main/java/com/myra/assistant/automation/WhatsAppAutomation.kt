package com.myra.assistant.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.myra.assistant.model.AIState
import com.myra.assistant.model.AITask
import com.myra.assistant.model.TaskStep
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.ScreenAnalyzer
import com.myra.assistant.service.SmartAccessibilityEngine
import com.myra.assistant.utils.ContactResolver
import com.myra.assistant.utils.HindiTransliterator

object WhatsAppAutomation {

    private const val TAG = "MYRA_WA"
    private const val MAX_RETRY = 6
    private const val STEP_DELAY = 2000L

    private val handler = Handler(Looper.getMainLooper())
    private var retry = 0
    private var lastStepTime = 0L

    fun formatNumberForWhatsApp(number: String): String {
        val digits = Regex("[^0-9]").replace(number, "")
        return if (digits.length == 10) "91$digits" else digits
    }

    fun performWhatsAppFlow(root: AccessibilityNodeInfo, task: AITask) {
        val now = System.currentTimeMillis()
        if (now - lastStepTime < 3000L) {
            return
        }
        val packageName = root.packageName?.toString() ?: return
        if (!packageName.contains("whatsapp")) {
            return
        }
        val contact = task.params["contact"] ?: task.params["name"] ?: ""
        val message = task.params["message"] ?: task.params["msg"] ?: ""
        val isCall = task.type == "WHATSAPP_CALL"
        val isVideo = task.params["video"]?.toBoolean() ?: false

        val screenState = ScreenAnalyzer.INSTANCE.analyze(root)
        val step = AIState.step
        Log.d(TAG, "WA Screen: ${screenState.screenType} | Step: $step")

        if (AIState.step == null) {
            AIState.step = TaskStep.OPEN_CHAT
            AIState.contact = contact
            AIState.message = message
            retry = 0
            lastStepTime = now
            Log.d(TAG, "Starting WA flow: contact=$contact, call=$isCall, msg=$message")
        }

        when (AIState.step) {
            TaskStep.OPEN_CHAT -> {
                lastStepTime = now
                val phoneNumber = findPhoneNumber(contact)
                if (phoneNumber != null) {
                    openDirectChat(phoneNumber)
                    AIState.step = if (isCall) {
                        TaskStep.CALL
                    } else if (message.isNotEmpty()) {
                        TaskStep.TYPE_MESSAGE
                    } else {
                        TaskStep.DONE
                    }
                    speak("$contact ka WhatsApp ${if (isCall) "call" else "chat"} khol rahi hoon")
                } else if (openChatViaSearch(contact)) {
                    AIState.step = if (isCall) TaskStep.CALL else TaskStep.TYPE_MESSAGE
                } else {
                    retryAndContinue("Contact nahi mila: $contact")
                }
            }
            TaskStep.TYPE_MESSAGE -> {
                lastStepTime = now
                if (screenState.screenType != ScreenAnalyzer.ScreenType.WHATSAPP_CHAT) {
                    Log.d(TAG, "Waiting for WA chat screen... current: ${screenState.screenType}")
                    return
                }
                if (clickMessageField(root)) {
                    handler.postDelayed({
                        if (AccessibilityHelperService.Companion.getRoot() == null) {
                            return@postDelayed
                        }
                        val typed = SmartAccessibilityEngine.typeText(message)
                        Log.d(TAG, "Typed msg: $typed")
                        if (typed) {
                            AIState.step = TaskStep.SEND
                            lastStepTime = System.currentTimeMillis()
                        } else {
                            // As decompiled: plain retry counter bump, not retryAndContinue().
                            retry++
                            lastStepTime = System.currentTimeMillis()
                        }
                    }, 800)
                } else {
                    retryAndContinue("Message field nahi mila")
                }
            }
            TaskStep.SEND -> {
                lastStepTime = now
                if (screenState.screenType != ScreenAnalyzer.ScreenType.WHATSAPP_CHAT) {
                    return
                }
                if (clickSendButton(root, packageName)) {
                    handler.postDelayed({
                        speak("Message bhej diya $contact ko")
                        reset()
                    }, 1000)
                } else {
                    retryAndContinue("Send button nahi mila")
                }
            }
            TaskStep.CALL -> {
                lastStepTime = now
                if (screenState.screenType != ScreenAnalyzer.ScreenType.WHATSAPP_CHAT &&
                    screenState.screenType != ScreenAnalyzer.ScreenType.WHATSAPP_HOME
                ) {
                    Log.d(TAG, "Waiting for WA to load for call...")
                    return
                }
                val pkg = root.packageName?.toString() ?: "com.whatsapp"
                // SmartAccessibilityEngine.click(text, id, contentDesc) — positional
                // so this does not depend on the other file's parameter names.
                val clicked = if (isVideo) {
                    clickNode(root, "$pkg:id/video_call_button", null, "Video call") ||
                        SmartAccessibilityEngine.click(null, null, "Video call")
                } else {
                    clickNode(root, "$pkg:id/voice_call_button", null, "Voice call") ||
                        clickNode(root, "$pkg:id/audio_call_button", null, "Audio call") ||
                        SmartAccessibilityEngine.click(null, null, "Voice call") ||
                        SmartAccessibilityEngine.click(null, null, "Audio call") ||
                        SmartAccessibilityEngine.click("Call", null, null)
                }
                if (clicked) {
                    speak("$contact ko WhatsApp ${if (isVideo) "video" else "voice"} call kar rahi hoon")
                    handler.postDelayed({
                        reset()
                    }, 3000)
                } else {
                    retryAndContinue("Call button nahi mila")
                }
            }
            else -> reset()
        }
    }

    fun reset() {
        AIState.task = null
        AIState.step = null
        AIState.contact = ""
        AIState.message = ""
        retry = 0
        lastStepTime = 0L
        Log.d(TAG, "WhatsApp state reset")
    }

    private fun clickFirstContactRow(root: AccessibilityNodeInfo): Boolean {
        return findAndClickFirstRow(
            root,
            listOf(
                "com.whatsapp:id/contactpicker_row_name",
                "com.whatsapp:id/name",
                "com.whatsapp:id/contact_row_name"
            )
        )
    }

    private fun clickMessageField(root: AccessibilityNodeInfo): Boolean {
        return clickNode(root, "com.whatsapp:id/entry", "Type a message", "Message") ||
            clickNode(root, "com.whatsapp.w4b:id/entry")
    }

    private fun clickNode(
        node: AccessibilityNodeInfo,
        id: String?,
        text: String? = null,
        desc: String? = null
    ): Boolean {
        if (id != null) {
            for (candidate in node.findAccessibilityNodeInfosByViewId(id)) {
                if (performClickOnNode(candidate)) {
                    return true
                }
            }
        }
        if (text != null) {
            for (candidate in node.findAccessibilityNodeInfosByText(text)) {
                if (performClickOnNode(candidate)) {
                    return true
                }
            }
        }
        return desc != null && findAndClickByDesc(node, desc)
    }

    private fun clickSendButton(root: AccessibilityNodeInfo, pkg: String): Boolean {
        return clickNode(root, "$pkg:id/send", null, "Send") ||
            SmartAccessibilityEngine.click(null, "com.whatsapp:id/send", null) ||
            SmartAccessibilityEngine.click(null, null, "Send") ||
            SmartAccessibilityEngine.click("Send", null, null)
    }

    private fun findAndClickByDesc(node: AccessibilityNodeInfo?, desc: String): Boolean {
        if (node == null) {
            return false
        }
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (contentDesc.contains(desc, ignoreCase = true) && performClickOnNode(node)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (findAndClickByDesc(node.getChild(i), desc)) {
                return true
            }
        }
        return false
    }

    private fun findAndClickFirstRow(root: AccessibilityNodeInfo, ids: List<String>): Boolean {
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty() && performClickOnNode(nodes[0])) {
                return true
            }
        }
        return false
    }

    private fun findPhoneNumber(name: String): String? {
        val service = AccessibilityHelperService.Companion.getInstance() ?: return null
        val transliterated = HindiTransliterator.INSTANCE.transliterate(name)
        val resolved = ContactResolver.INSTANCE.resolve(service, transliterated)
        if (resolved.isNotEmpty()) {
            return ContactResolver.INSTANCE.clean(resolved)
        }
        val fuzzy = ContactResolver.INSTANCE.fuzzyResolve(service, transliterated)
        if (fuzzy.isNotEmpty()) {
            return ContactResolver.INSTANCE.clean(fuzzy)
        }
        if (name != transliterated) {
            val originalResolved = ContactResolver.INSTANCE.resolve(service, name)
            if (originalResolved.isNotEmpty()) {
                return ContactResolver.INSTANCE.clean(originalResolved)
            }
        }
        Log.w(TAG, "No number found for: $name")
        return null
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openChatViaSearch(contactName: String): Boolean {
        if (AccessibilityHelperService.Companion.getInstance() == null) {
            return false
        }
        val clicked = SmartAccessibilityEngine.click(null, "com.whatsapp:id/fab", null) ||
            SmartAccessibilityEngine.click(null, null, "New chat") ||
            SmartAccessibilityEngine.click(null, null, "New conversation")
        if (!clicked) {
            return false
        }
        val transliterated = HindiTransliterator.INSTANCE.transliterate(contactName)
        handler.postDelayed({
            SmartAccessibilityEngine.typeText(transliterated)
        }, 1200)
        handler.postDelayed({
            val currentRoot = AccessibilityHelperService.Companion.getRoot() ?: return@postDelayed
            val contactClicked = clickNode(
                currentRoot,
                "com.whatsapp:id/contactpicker_row_name",
                transliterated
            ) || clickFirstContactRow(currentRoot)
            Log.d(TAG, "Search contact click: $contactClicked")
        }, 2500)
        return true
    }

    private fun openDirectChat(phoneNumber: String) {
        val service = AccessibilityHelperService.Companion.getInstance() ?: return
        val number = formatNumberForWhatsApp(phoneNumber)
        val pkg = if (isPackageInstalled(service, "com.whatsapp.w4b")) {
            "com.whatsapp.w4b"
        } else {
            "com.whatsapp"
        }
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://api.whatsapp.com/send?phone=$number")
            ).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)
            Log.d(TAG, "✅ Direct chat opened: $number in $pkg")
        } catch (e: Exception) {
            Log.e(TAG, "Direct chat error: ${e.message}")
        }
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            return false
        }
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun retryAndContinue(reason: String = "") {
        retry++
        Log.w(TAG, "Retry $retry/$MAX_RETRY: $reason")
        if (retry >= MAX_RETRY) {
            speak("Maaf kijiye, WhatsApp par kaam nahi ho paaya")
            reset()
        }
        lastStepTime = System.currentTimeMillis()
    }

    private fun speak(text: String) {
        SmartAccessibilityEngine.speak(text)
        Log.d(TAG, "Speaking: $text")
    }
}
