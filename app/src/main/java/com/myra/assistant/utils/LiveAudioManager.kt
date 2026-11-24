package com.myra.assistant.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Plays raw PCM audio chunks (24 kHz, mono, 16-bit) received from the live
 * voice session, routing output to the loudspeaker.
 *
 * Restored from CFR-decompiled bytecode of the user's own MYRA app
 * (com.myra.assistant). The three synthetic-lambda files
 * (LiveAudioManager$$ExternalSyntheticLambda0/1/2) are folded back in here:
 * Lambda2 is the playChunk runnable, Lambda0 is the audio-focus listener
 * used by [requestAudioFocus]. Lambda1 is a byte-identical duplicate of the
 * focus listener that is never instantiated anywhere in the dex (verified
 * with javap across all project jars) — dead R8 output, not a second
 * listener — so it is intentionally not reproduced.
 */
class LiveAudioManager(private val context: Context) {

    private val TAG = "LiveAudioManager"
    private val playExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    init {
        setupAudioTrack()
    }

    fun playChunk(data: ByteArray) {
        playExecutor.execute {
            try {
                requestAudioFocus()
                routeToSpeaker()
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    setupAudioTrack()
                }
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }
                val written = audioTrack?.write(data, 0, data.size) ?: 0
                if (written < 0) {
                    Log.e(TAG, "Write Error: $written")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback Error: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            audioManager?.setSpeakerphoneOn(false)
            abandonAudioFocus()
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop Error: ${e.message}")
        }
    }

    private fun setupAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                24000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(24000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
            Log.d(TAG, "AudioTrack Ready")
        } catch (e: Exception) {
            Log.e(TAG, "Init Error: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> hasAudioFocus = true
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        hasAudioFocus = false
                        stop()
                    }
                }
            }
            .build()
        audioFocusRequest = request
        val result = audioManager?.requestAudioFocus(request)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Audio Focus: $hasAudioFocus")
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val request = audioFocusRequest
        if (request != null) {
            audioManager?.abandonAudioFocusRequest(request)
        }
        hasAudioFocus = false
    }

    private fun routeToSpeaker() {
        try {
            if (audioManager == null) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            }
            audioManager?.setMode(AudioManager.MODE_IN_COMMUNICATION)
            audioManager?.setSpeakerphoneOn(true)
            Log.d(TAG, "Audio routed to speaker")
        } catch (e: Exception) {
            Log.e(TAG, "Speaker route error: ${e.message}")
        }
    }
}
