package com.myra.assistant.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.myra.assistant.service.MyraOverlayService

/**
 * Restored from CFR-decompiled bytecode
 * (decompiled-java/com/myra/assistant/ui/main/AssistLauncherActivity.java).
 *
 * Transparent, no-history trampoline declared in AndroidManifest.xml
 * with the ASSIST / VOICE_COMMAND / VOICE_ASSIST intent filters: when
 * the user invokes the assistant (e.g. power-button / assist gesture)
 * this activity starts [MyraOverlayService] in the foreground with the
 * "SHOW_OVERLAY" action, suppresses the transition animation and
 * finishes immediately. No layout is ever set.
 */
class AssistLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MyraOverlayService::class.java)
        intent.action = "SHOW_OVERLAY"
        ContextCompat.startForegroundService(this, intent)
        overridePendingTransition(0, 0)
        finish()
    }
}
