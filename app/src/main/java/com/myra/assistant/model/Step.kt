package com.myra.assistant.model

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../model/Step.java).
 *
 * One step of a [MyraTask]. Data class: CFR shows component1/component2,
 * copy, equals, hashCode, toString. The synthetic constructor (mask bit 2)
 * shows `completed` defaults to false.
 */
data class Step(
    val description: String,
    val completed: Boolean = false
)
