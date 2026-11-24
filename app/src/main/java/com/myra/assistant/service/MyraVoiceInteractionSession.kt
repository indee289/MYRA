package com.myra.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.myra.assistant.ui.main.MainActivity

/**
 * Restored from CFR-decompiled bytecode (compiled dex is the only surviving
 * copy of the lost source). Launches MainActivity with the mic trigger and
 * finishes immediately once shown.
 */
class MyraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = 0x14000000
        intent.putExtra("TRIGGER_MIC", true)
        context.startActivity(intent)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        finish()
    }
}
