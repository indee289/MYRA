package com.myra.assistant.automation

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
import com.myra.assistant.utils.HindiTransliterator

object SpotifyAutomation {

    private const val TAG = "MYRA_SPOTIFY"
    private const val MAX_RETRY = 6
    private const val STEP_DELAY = 2000L

    private val handler = Handler(Looper.getMainLooper())
    private var retry = 0
    private var lastStepTime = 0L

    fun performSpotifyFlow(root: AccessibilityNodeInfo, task: AITask) {
        val now = System.currentTimeMillis()
        if (now - lastStepTime < 3000L) {
            return
        }
        val packageName = root.packageName?.toString() ?: return
        if (packageName != "com.spotify.music") {
            return
        }
        val query = task.params["query"]?.let { HindiTransliterator.transliterate(it) }
            ?: return
        val screenState = ScreenAnalyzer.analyze(root)
        val step = AIState.step
        Log.d(TAG, "Spotify Screen: ${screenState.screenType} | Step: $step | Query: $query")

        if (AIState.step == null) {
            AIState.step = TaskStep.CLICK_SEARCH
            AIState.message = query
            retry = 0
            lastStepTime = now
        }

        when (AIState.step) {
            TaskStep.CLICK_SEARCH -> {
                lastStepTime = now
                speak("Spotify par search kar rahi hoon")
                // SmartAccessibilityEngine.click(text, id, contentDesc) — positional
                // so this does not depend on the other file's parameter names.
                val clicked = SmartAccessibilityEngine.click(null, "com.spotify.music:id/search_tab", null) ||
                    SmartAccessibilityEngine.click(null, "com.spotify.music:id/bottom_nav_search", null) ||
                    SmartAccessibilityEngine.click(null, null, "Search") ||
                    SmartAccessibilityEngine.click("Search", null, null)
                if (clicked) {
                    AIState.step = TaskStep.TYPE_NAME
                } else {
                    retryAndContinue()
                }
            }
            TaskStep.TYPE_NAME -> {
                lastStepTime = now
                if (screenState.screenType != ScreenAnalyzer.ScreenType.SPOTIFY_SEARCH &&
                    screenState.screenType != ScreenAnalyzer.ScreenType.SPOTIFY_HOME
                ) {
                    Log.d(TAG, "Waiting for Spotify search screen...")
                    return
                }
                speak("$query dhoond rahi hoon")
                // CFR discards this result; the delayed type is posted unconditionally.
                SmartAccessibilityEngine.click(null, "com.spotify.music:id/query", null) ||
                    SmartAccessibilityEngine.click(null, "com.spotify.music:id/search_query", null) ||
                    SmartAccessibilityEngine.click(null, "com.spotify.music:id/edit_text", null) ||
                    SmartAccessibilityEngine.click(null, null, "Search for songs") ||
                    SmartAccessibilityEngine.click("Search for songs, albums...", null, null)
                handler.postDelayed({
                    val typed = SmartAccessibilityEngine.typeText(query)
                    Log.d(TAG, "Typed '$query': $typed")
                    if (typed) {
                        AIState.step = TaskStep.SEND
                        lastStepTime = System.currentTimeMillis()
                    } else {
                        retryAndContinue()
                    }
                }, 700)
            }
            TaskStep.SEND -> {
                lastStepTime = now
                speak("Search kar rahi hoon")
                val submitted = SmartAccessibilityEngine.click(null, "com.spotify.music:id/search_button", null) ||
                    SmartAccessibilityEngine.click(null, "com.spotify.music:id/submit", null) ||
                    SmartAccessibilityEngine.click(null, null, "Search") ||
                    SmartAccessibilityEngine.click("Search", null, null) ||
                    submitViaImeAction(root)
                if (submitted) {
                    AIState.step = TaskStep.PLAY
                } else {
                    // As decompiled: IME submit is retried once, then PLAY is set anyway.
                    submitViaImeAction(root)
                    AIState.step = TaskStep.PLAY
                }
            }
            TaskStep.PLAY -> {
                lastStepTime = now
                if (screenState.screenType != ScreenAnalyzer.ScreenType.SPOTIFY_RESULTS &&
                    screenState.screenType != ScreenAnalyzer.ScreenType.SPOTIFY_HOME
                ) {
                    Log.d(TAG, "Waiting for Spotify results...")
                    return
                }
                speak("Gaana play kar rahi hoon")
                handler.postDelayed({
                    val currentRoot = AccessibilityHelperService.Companion.getRoot()
                    if (currentRoot != null) {
                        playFirstResult(currentRoot)
                    }
                }, 1500)
                lastStepTime = now + 5000
            }
            else -> reset()
        }
    }

    fun reset() {
        AIState.task = null
        AIState.step = null
        retry = 0
        lastStepTime = 0L
        Log.d(TAG, "Spotify state reset")
    }

    private fun playFirstResult(root: AccessibilityNodeInfo) {
        val played = SmartAccessibilityEngine.click(null, "com.spotify.music:id/play_button", null) ||
            SmartAccessibilityEngine.click(null, null, "Play") ||
            SmartAccessibilityEngine.click("Shuffle play", null, null) ||
            SmartAccessibilityEngine.click(null, null, "Shuffle play")
        if (played) {
            handler.postDelayed({
                speak("Gaana play ho raha hai")
                reset()
            }, 2000)
            return
        }
        for (id in listOf(
            "com.spotify.music:id/track_name",
            "com.spotify.music:id/title",
            "com.spotify.music:id/song_name",
            "com.spotify.music:id/entity_name"
        )) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val target = findClickableAncestor(nodes[0])
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    handler.postDelayed({
                        speak("Gaana play ho raha hai")
                        reset()
                    }, 2000)
                    return
                }
            }
        }
        if (clickFirstPlayableTraversal(root)) {
            handler.postDelayed({
                speak("Gaana play ho raha hai")
                reset()
            }, 2000)
        } else {
            retryAndContinue()
        }
    }

    private fun clickFirstPlayableTraversal(node: AccessibilityNodeInfo?): Boolean {
        fun traverse(current: AccessibilityNodeInfo?): Boolean {
            if (current == null) {
                return false
            }
            val viewId = current.viewIdResourceName ?: ""
            if (viewId.contains("track") || viewId.contains("song") ||
                viewId.contains("title") || viewId.contains("result") ||
                viewId.contains("entity") || viewId.contains("album")
            ) {
                val clickable = findClickableAncestor(current)
                if (clickable != null &&
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    return true
                }
            }
            for (i in 0 until current.childCount) {
                if (traverse(current.getChild(i))) {
                    return true
                }
            }
            return false
        }
        return traverse(node)
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) {
            return null
        }
        if (node.isClickable) {
            return node
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                return parent
            }
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun submitViaImeAction(root: AccessibilityNodeInfo): Boolean {
        try {
            for (id in listOf("com.spotify.music:id/query", "com.spotify.music:id/search_query")) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    // 0x10000004 as decompiled (ACTION_IME_ENTER).
                    nodes[0].performAction(0x10000004)
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignored in the original source.
        }
        return false
    }

    private fun retryAndContinue(reason: String = "") {
        retry++
        Log.w(TAG, "Retry $retry/$MAX_RETRY: $reason")
        if (retry >= MAX_RETRY) {
            speak("Maaf kijiye, Spotify par gaana nahi mil paaya")
            reset()
        }
        lastStepTime = System.currentTimeMillis()
    }

    private fun speak(text: String) {
        SmartAccessibilityEngine.speak(text)
        Log.d(TAG, "Speaking: $text")
    }
}
