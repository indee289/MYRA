package com.myra.assistant.utils

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Dumps an accessibility node tree to text (one line per node).
 *
 * This is the utils.ScreenReader — NOT service.ScreenReader
 * (com.myra.assistant.service.ScreenReader), which has a different private
 * traversal method (collect) and a different dump format. Surviving code
 * (MainActivity) imports this utils one.
 *
 * Restored from CFR-decompiled bytecode of the user's own MYRA app
 * (com.myra.assistant).
 */
object ScreenReader {

    fun dump(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val builder = StringBuilder()
        traverseNode(root, builder, 0)
        return builder.toString()
    }

    private fun traverseNode(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val className = node.className
        val text = node.text ?: ""
        val contentDescription = node.contentDescription ?: ""
        builder.append("$indent$className - $text - $contentDescription\n")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, builder, depth + 1)
        }
    }
}
