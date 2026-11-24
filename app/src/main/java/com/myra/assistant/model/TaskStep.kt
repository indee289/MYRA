package com.myra.assistant.model

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../model/TaskStep.java).
 *
 * The current step of the in-progress [AITask], tracked in [AIState.step]
 * and advanced by the Spotify/WhatsApp/YouTube automation classes.
 * Declaration order is exactly as in the CFR enum body / @Metadata d2
 * (OPEN_APP, CLICK_SEARCH, TYPE_NAME, OPEN_CHAT, TYPE_MESSAGE, SEND,
 * CALL, PLAY, DONE) — ordinals matter because the automation classes
 * switch on them via generated $EnumSwitchMapping arrays.
 */
enum class TaskStep {
    OPEN_APP,
    CLICK_SEARCH,
    TYPE_NAME,
    OPEN_CHAT,
    TYPE_MESSAGE,
    SEND,
    CALL,
    PLAY,
    DONE
}
