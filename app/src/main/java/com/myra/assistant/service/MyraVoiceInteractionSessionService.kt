package com.myra.assistant.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source).
 */
class MyraVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MyraVoiceInteractionSession(this)
    }
}
