package com.myra.assistant.model

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../model/AppCommand.java).
 *
 * A parsed voice command produced by CommandParser (e.g. "OPEN_APP",
 * "YOUTUBE_PLAY", "SPOTIFY_PLAY", "WHATSAPP_MSG", "WHATSAPP_CALL", "CALL",
 * "VOLUME", "FLASHLIGHT", "SMS"). Data class: CFR shows component1/
 * component2, copy, equals, hashCode, toString.
 *
 * NOTE: unlike AITask, AppCommand has NO synthetic/default constructor in
 * the bytecode (javap shows only the 2-arg constructor), so `params` has
 * NO default value here — both arguments are required. Every call site in
 * CommandParser passes both arguments, which confirms this.
 */
data class AppCommand(
    val type: String,
    val params: Map<String, String>
)
