package com.myra.assistant.model

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../model/AITask.java).
 *
 * A task handed to the accessibility automations, e.g. "SPOTIFY_PLAY",
 * "WHATSAPP_MSG", "WHATSAPP_CALL", "YOUTUBE_PLAY" (see MainViewModel).
 * Data class: CFR shows component1/component2, copy, equals, hashCode,
 * toString. The synthetic constructor (mask bit 2) shows `params` defaults
 * to emptyMap().
 */
data class AITask(
    val type: String,
    val params: Map<String, String> = emptyMap()
)
