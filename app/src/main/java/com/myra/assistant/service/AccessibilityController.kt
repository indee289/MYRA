package com.myra.assistant.service

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). Thin facade over [SmartAccessibilityEngine].
 */
object AccessibilityController {

    fun goBack() {
        SmartAccessibilityEngine.goBack()
    }

    fun goHome() {
        SmartAccessibilityEngine.goHome()
    }

    fun scrollDown(): Boolean {
        return SmartAccessibilityEngine.scrollDown()
    }

    fun scrollUp(): Boolean {
        return SmartAccessibilityEngine.scrollUp()
    }

    fun smartClick(
        text: String? = null,
        id: String? = null,
        contentDesc: String? = null
    ): Boolean {
        return SmartAccessibilityEngine.click(text, id, contentDesc)
    }

    fun typeInField(text: String): Boolean {
        return SmartAccessibilityEngine.typeText(text)
    }
}
