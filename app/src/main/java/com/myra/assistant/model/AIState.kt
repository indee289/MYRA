package com.myra.assistant.model

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../model/AIState.java).
 *
 * Shared mutable state for the in-progress AI-driven task (set by MainViewModel,
 * read by AccessibilityEventRouter and the *Automation classes). Singleton:
 * the bytecode shows `public static final AIState INSTANCE` + a private
 * constructor, i.e. a Kotlin `object`.
 *
 * NOTE on bytecode shape: javap shows the four backing fields as `static`
 * (e.g. `private static String contact`) with instance get/set methods and
 * no static accessors. A plain `object` reproduces the exact Kotlin-level
 * API (AIState.task / AIState.reset() etc.); the static fields are most
 * likely a dex2jar/R8 artefact of the compiled dex, so no @JvmStatic was
 * added (that would have produced static accessors that javap does NOT show).
 */
object AIState {

    var task: AITask? = null
    var step: TaskStep? = null
    var contact: String = ""
    var message: String = ""

    fun reset() {
        task = null
        step = null
        contact = ""
        message = ""
    }
}
