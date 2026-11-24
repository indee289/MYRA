package com.myra.assistant.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). NOTE: this is the service-package ScreenReader.
 * A separate utils.ScreenReader (com.myra.assistant.utils.ScreenReader)
 * also exists in this app and is the one MainActivity imports — do not
 * confuse the two. Dumps visible text / content descriptions from an
 * accessibility node tree, to a max depth of 10.
 */
object ScreenReader {

    fun dump(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val sb = StringBuilder()
        collect(root, sb, 0)
        return sb.toString()
    }

    private fun collect(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 10) return
        node.text?.let { sb.append(it).append(" | ") }
        node.contentDescription?.let { sb.append(it).append(" | ") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, sb, depth + 1)
        }
    }
}
