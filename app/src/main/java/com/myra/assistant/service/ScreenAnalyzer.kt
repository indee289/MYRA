package com.myra.assistant.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Screen-state classifier restored from CFR-decompiled bytecode.
 * Dumps the active window tree into [NodeInfo]s, guesses the app screen
 * ([ScreenType]) from view-id / content-description patterns, and scores
 * candidate nodes for automation actions.
 */
object ScreenAnalyzer {

    private const val TAG = "MYRA_SCREEN"

    data class NodeInfo(
        val text: String,
        val contentDesc: String,
        val viewId: String,
        val className: String,
        val isClickable: Boolean,
        val bounds: Rect,
        val confidence: Float
    )

    data class ScreenState(
        val packageName: String,
        val screenType: ScreenType,
        val confidence: Float,
        val keyNodes: List<NodeInfo>
    )

    enum class ScreenType {
        UNKNOWN,
        WHATSAPP_HOME,
        WHATSAPP_CHAT,
        WHATSAPP_CALLING,
        WHATSAPP_CONTACT_PICKER,
        YOUTUBE_HOME,
        YOUTUBE_SEARCH,
        YOUTUBE_RESULTS,
        YOUTUBE_PLAYER,
        SPOTIFY_HOME,
        SPOTIFY_SEARCH,
        SPOTIFY_RESULTS,
        SPOTIFY_PLAYER,
        PHONE_DIALER,
        PHONE_CALLING,
        SYSTEM_HOME
    }

    fun analyze(root: AccessibilityNodeInfo?): ScreenState {
        if (root == null) {
            Log.w(TAG, "Root is null")
            return ScreenState("", ScreenType.UNKNOWN, 0f, emptyList())
        }
        val packageName = root.packageName?.toString() ?: ""
        val nodes = dumpNodes(root)
        val type = when {
            packageName.contains("whatsapp") -> detectWhatsAppScreen(nodes)
            packageName.contains("youtube") -> detectYouTubeScreen(nodes)
            packageName.contains("spotify") -> detectSpotifyScreen(nodes)
            packageName.contains("launcher") -> ScreenType.SYSTEM_HOME
            else -> ScreenType.UNKNOWN
        }
        val confidence = calculateConfidence(nodes, type)
        val keyNodes = findKeyNodes(nodes, type)
        Log.d(TAG, "Screen: $packageName | Type: $type | Confidence: $confidence%")
        return ScreenState(packageName, type, confidence, keyNodes)
    }

    fun findBestNode(
        root: AccessibilityNodeInfo?,
        text: String? = null,
        id: String? = null,
        contentDesc: String? = null,
        className: String? = null
    ): Pair<AccessibilityNodeInfo?, Float> {
        val fallback = 0f
        if (root == null) return null to fallback
        val candidates = ArrayList<Pair<AccessibilityNodeInfo, Float>>()
        if (id != null) {
            for (node in root.findAccessibilityNodeInfosByViewId(id)) {
                if (node.isClickable || hasClickableParent(node)) {
                    candidates.add(node to 95f)
                }
            }
        }
        if (text != null) {
            for (node in root.findAccessibilityNodeInfosByText(text)) {
                val nodeText = node.text?.toString() ?: ""
                if (nodeText.equals(text, ignoreCase = true)) {
                    val clickable = findClickableNode(node)
                    if (clickable != null) {
                        candidates.add(clickable to 90f)
                    }
                }
            }
        }
        if (contentDesc != null) {
            traverseForContentDesc(root, contentDesc, candidates)
        }
        if (className != null) {
            traverseForClassName(root, className, candidates)
        }
        if (text != null && candidates.isEmpty()) {
            for (node in root.findAccessibilityNodeInfosByText(text)) {
                val clickable = findClickableNode(node)
                if (clickable != null) {
                    candidates.add(clickable to 70f)
                }
            }
        }
        val best = candidates.maxByOrNull { it.second }
        return best ?: (null to fallback)
    }

    fun retryWithAlternatives(
        root: AccessibilityNodeInfo?,
        alternatives: List<Triple<String?, String?, String?>>
    ): Boolean {
        if (root == null) return false
        for ((text, id, contentDesc) in alternatives) {
            val (node, confidence) = findBestNode(root, text, id, contentDesc)
            val clickable = node?.let { findClickableNode(it) }
            if (node != null && confidence > 50f && clickable != null &&
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                Log.d(TAG, "✅ Retry success: text=$text, id=$id, conf=$confidence")
                return true
            }
        }
        return false
    }

    fun verifyAction(
        service: AccessibilityService,
        expectedScreen: ScreenType,
        timeoutMs: Long = 5000L
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (analyze(service.rootInActiveWindow).screenType == expectedScreen) {
                Log.d(TAG, "✅ Verified: $expectedScreen")
                return true
            }
            Thread.sleep(500L)
        }
        Log.w(TAG, "❌ Verification failed for: $expectedScreen")
        return false
    }

    private fun calculateConfidence(nodes: List<NodeInfo>, screenType: ScreenType): Float {
        return when (screenType) {
            ScreenType.WHATSAPP_CHAT -> {
                if (nodes.isEmpty()) 70f
                else if (nodes.any { it.viewId.contains("entry") }) 95f
                else 70f
            }
            ScreenType.YOUTUBE_PLAYER -> {
                if (nodes.isEmpty()) 75f
                else if (nodes.any { it.viewId.contains("player") }) 95f
                else 75f
            }
            ScreenType.SPOTIFY_PLAYER -> {
                if (nodes.isEmpty()) 70f
                else if (nodes.any { it.viewId.contains("play") }) 90f
                else 70f
            }
            else -> 50f
        }
    }

    private fun detectWhatsAppScreen(nodes: List<NodeInfo>): ScreenType {
        val hasList = nodes.any {
            it.viewId.contains("conversation_list") || it.viewId.contains("list")
        }
        val hasEntry = nodes.any {
            it.viewId.contains("entry") || it.viewId.contains("input")
        }
        val hasSend = nodes.any {
            it.viewId.contains("send") || it.contentDesc.contains("send")
        }
        val hasCall = nodes.any {
            it.viewId.contains("call") || it.contentDesc.contains("call")
        }
        val hasPicker = nodes.any {
            it.viewId.contains("contact_picker") || it.viewId.contains("picker")
        }
        return when {
            hasEntry && hasSend -> ScreenType.WHATSAPP_CHAT
            hasCall && !hasEntry -> ScreenType.WHATSAPP_CALLING
            hasPicker -> ScreenType.WHATSAPP_CONTACT_PICKER
            hasList -> ScreenType.WHATSAPP_HOME
            else -> ScreenType.UNKNOWN
        }
    }

    private fun detectYouTubeScreen(nodes: List<NodeInfo>): ScreenType {
        val hasSearchBar = nodes.any {
            it.viewId.contains("search") && it.viewId.contains("bar")
        }
        val hasResults = nodes.any {
            it.viewId.contains("result") || it.viewId.contains("video")
        }
        val hasPlayer = nodes.any {
            it.viewId.contains("player") || it.viewId.contains("watch")
        }
        val hasHome = nodes.any {
            it.viewId.contains("feed") || it.viewId.contains("home")
        }
        return when {
            hasPlayer -> ScreenType.YOUTUBE_PLAYER
            hasResults && hasSearchBar -> ScreenType.YOUTUBE_RESULTS
            hasSearchBar -> ScreenType.YOUTUBE_SEARCH
            hasHome -> ScreenType.YOUTUBE_HOME
            else -> ScreenType.UNKNOWN
        }
    }

    private fun detectSpotifyScreen(nodes: List<NodeInfo>): ScreenType {
        val hasSearch = nodes.any { it.viewId.contains("search") }
        val hasPlayer = nodes.any {
            it.viewId.contains("player") || it.viewId.contains("now_playing")
        }
        val hasTrack = nodes.any {
            it.viewId.contains("track") || it.viewId.contains("list")
        }
        return when {
            hasPlayer -> ScreenType.SPOTIFY_PLAYER
            hasTrack && hasSearch -> ScreenType.SPOTIFY_RESULTS
            hasSearch -> ScreenType.SPOTIFY_SEARCH
            else -> ScreenType.SPOTIFY_HOME
        }
    }

    private fun findKeyNodes(nodes: List<NodeInfo>, screenType: ScreenType): List<NodeInfo> {
        return when (screenType) {
            ScreenType.WHATSAPP_CHAT -> nodes.filter {
                it.viewId.contains("send") ||
                    it.viewId.contains("entry") ||
                    it.viewId.contains("attach")
            }
            ScreenType.YOUTUBE_SEARCH -> nodes.filter {
                it.viewId.contains("search") || it.viewId.contains("submit")
            }
            ScreenType.SPOTIFY_SEARCH -> nodes.filter {
                it.viewId.contains("search") || it.viewId.contains("query")
            }
            else -> emptyList()
        }
    }

    private fun dumpNodes(root: AccessibilityNodeInfo): List<NodeInfo> {
        val list = ArrayList<NodeInfo>()
        traverse(root, list, 0)
        return list
    }

    private fun traverse(node: AccessibilityNodeInfo?, list: MutableList<NodeInfo>, depth: Int) {
        if (node != null && depth <= 15) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""
            val clickable = node.isClickable
            val confidence = if (clickable) 80f else 50f
            list.add(NodeInfo(text, contentDesc, viewId, className, clickable, rect, confidence))
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), list, depth + 1)
            }
        }
    }

    private fun traverseForContentDesc(
        node: AccessibilityNodeInfo?,
        desc: String,
        list: MutableList<Pair<AccessibilityNodeInfo, Float>>
    ) {
        if (node == null) return
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (contentDesc.contains(desc, ignoreCase = true)) {
            val clickable = findClickableNode(node)
            if (clickable != null) {
                list.add(clickable to 85f)
            }
        }
        for (i in 0 until node.childCount) {
            traverseForContentDesc(node.getChild(i), desc, list)
        }
    }

    private fun traverseForClassName(
        node: AccessibilityNodeInfo?,
        className: String,
        list: MutableList<Pair<AccessibilityNodeInfo, Float>>
    ) {
        if (node == null) return
        val nodeClass = node.className?.toString() ?: ""
        if (nodeClass.contains(className, ignoreCase = true)) {
            val clickable = findClickableNode(node)
            if (clickable != null) {
                list.add(clickable to 75f)
            }
        }
        for (i in 0 until node.childCount) {
            traverseForClassName(node.getChild(i), className, list)
        }
    }

    private fun findClickableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isClickable) return node
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) return parent
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo?): Boolean {
        return findClickableNode(node) != null
    }
}
