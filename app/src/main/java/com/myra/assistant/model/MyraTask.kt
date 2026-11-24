package com.myra.assistant.model

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../model/MyraTask.java).
 *
 * Data class: CFR shows component1/component2/component3, copy, equals,
 * hashCode, toString. The synthetic constructor (mask bit 4) shows `steps`
 * defaults to emptyList().
 *
 * NOTE: no usage of MyraTask (or Step) was found anywhere in the surviving
 * source or in the decompiled tree — it appears to be unused/legacy model
 * code. Restored exactly as decompiled anyway.
 */
data class MyraTask(
    val id: String,
    val title: String,
    val steps: List<Step> = emptyList()
)
