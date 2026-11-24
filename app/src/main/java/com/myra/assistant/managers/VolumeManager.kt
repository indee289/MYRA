package com.myra.assistant.managers

import android.content.Context
import android.media.AudioManager

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../managers/VolumeManager.java).
 */
class VolumeManager(private val context: Context) {

    /**
     * Raises ([up] = true) or lowers ([up] = false) the MUSIC stream
     * volume by one step, showing the system volume UI. The CFR literals
     * are stream 3 ([AudioManager.STREAM_MUSIC]), direction +1 / -1 and
     * flags 1 ([AudioManager.FLAG_SHOW_UI]).
     */
    fun adjustVolume(up: Boolean) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }
}
