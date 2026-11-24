package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.myra.assistant.R
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.ui.main.OrbAnimationView
import com.myra.assistant.utils.LiveAudioManager

/**
 * Foreground overlay service restored from CFR-decompiled bytecode.
 * Shows a floating MYRA orb (overlay_orb layout) summoned by double-pressing
 * the power button (SCREEN_ON / SCREEN_OFF within 600 ms), listens continuously
 * via SpeechRecognizer and talks back through Gemini Live.
 */
class MyraOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var orbView: OrbAnimationView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var liveClient: GeminiLiveClient? = null
    private val liveAudioManager = LiveAudioManager(this)
    private var shouldContinueListening = false
    private var isListening = false
    private var isSpeaking = false
    private var isAwaitingResponse = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val restartHandler = Handler(Looper.getMainLooper())
    private val restartListeningRunnable = Runnable { startListening() }

    private val powerButtonReceiver = object : BroadcastReceiver() {
        private var lastPressTime = 0L
        private var pressCount = 0

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF ||
                intent.action == Intent.ACTION_SCREEN_ON
            ) {
                val now = System.currentTimeMillis()
                if (now - lastPressTime < 600L) {
                    pressCount++
                    if (pressCount >= 2) {
                        pressCount = 0
                        showOverlay()
                    }
                } else {
                    pressCount = 1
                }
                lastPressTime = now
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(powerButtonReceiver, filter)
        initSpeechRecognizer()
        initGeminiLive()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1001, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            when (action) {
                "SHOW_OVERLAY" -> showOverlay()
                "HIDE_OVERLAY" -> hideOverlay()
                "UPDATE_STATE" -> {
                    val speaking = intent?.getBooleanExtra("speaking", false) ?: false
                    val thinking = intent?.getBooleanExtra("thinking", false) ?: false
                    orbView?.setSpeaking(speaking)
                    orbView?.setThinking(thinking)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        unregisterReceiver(powerButtonReceiver)
        restartHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        liveClient?.disconnect()
        liveAudioManager.stop()
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (isOverlayVisible) return
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_orb, null)
            orbView = overlayView?.findViewById(R.id.overlayOrbView)
            // LayoutParams(wrap_content, wrap_content, TYPE_APPLICATION_OVERLAY,
            // flags 262408, PixelFormat.TRANSLUCENT), gravity CENTER — as decompiled.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                262408,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = android.view.Gravity.CENTER
            windowManager?.addView(overlayView, params)
            orbView?.setActive(true)
            isOverlayVisible = true
            (overlayView?.findViewById<ImageView>(R.id.closeOverlayBtn))?.setOnClickListener {
                hideOverlay()
            }
            (overlayView?.findViewById<FrameLayout>(R.id.orbContainer))?.setOnClickListener {
                if (!shouldContinueListening) {
                    shouldContinueListening = true
                    startListening()
                }
            }
            shouldContinueListening = true
            restartHandler.removeCallbacks(restartListeningRunnable)
            restartHandler.postDelayed(restartListeningRunnable, 400L)
        } catch (e: Exception) {
            // empty catch block (as decompiled)
        }
    }

    private fun hideOverlay() {
        val view = overlayView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // empty catch block
            }
            overlayView = null
            orbView = null
        }
        shouldContinueListening = false
        restartHandler.removeCallbacks(restartListeningRunnable)
        isOverlayVisible = false
        liveAudioManager.stop()
    }

    private fun startListening() {
        if (!shouldContinueListening) return
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer?.cancel()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                mainHandler.post { orbView?.setThinking(false) }
            }

            override fun onBeginningOfSpeech() {
                mainHandler.post {
                    isListening = true
                    orbView?.setPulsating(true)
                }
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                mainHandler.post {
                    isListening = false
                    orbView?.setPulsating(false)
                    if (!isAwaitingResponse && shouldContinueListening) {
                        restartHandler.removeCallbacks(restartListeningRunnable)
                        restartHandler.postDelayed(restartListeningRunnable, 700L)
                    }
                }
            }

            override fun onError(error: Int) {
                mainHandler.post {
                    isListening = false
                    orbView?.setPulsating(false)
                    orbView?.setThinking(false)
                    if (!isSpeaking && !isAwaitingResponse) {
                        if (shouldContinueListening) {
                            restartHandler.removeCallbacks(restartListeningRunnable)
                            restartHandler.postDelayed(restartListeningRunnable, 800L)
                        }
                    }
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                if (!isAwaitingResponse && !isSpeaking) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text != null) {
                        mainHandler.post {
                            isListening = false
                            shouldContinueListening = false
                            isAwaitingResponse = true
                            orbView?.setPulsating(false)
                            orbView?.setThinking(true)
                            liveClient?.sendTextMessage(text)
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
    }

    private fun initGeminiLive() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val personalityMode = prefs.getString("personality_mode", "gf") ?: "gf"
        val userName = prefs.getString("user_name", "Sir") ?: "Sir"
        if (apiKey.isEmpty()) return
        liveClient = GeminiLiveClient(
            apiKey,
            "You are MYRA, a $personalityMode assistant for $userName. Speak Hinglish. Keep it short.",
            object : GeminiLiveClient.LiveListener {
                override fun onAudioReceived(data: ByteArray) {
                    mainHandler.post {
                        if (isListening) {
                            isListening = false
                            speechRecognizer?.cancel()
                        }
                        isSpeaking = true
                        isAwaitingResponse = true
                        shouldContinueListening = false
                        restartHandler.removeCallbacks(restartListeningRunnable)
                        liveAudioManager.playChunk(data)
                        orbView?.setSpeaking(true)
                    }
                }

                override fun onConnected() {}

                override fun onError(msg: String) {}

                override fun onTextReceived(text: String) {
                    mainHandler.post { orbView?.setThinking(false) }
                }

                override fun onTurnComplete() {
                    mainHandler.post {
                        orbView?.setSpeaking(false)
                        isSpeaking = false
                        isAwaitingResponse = false
                        if (isOverlayVisible) {
                            shouldContinueListening = true
                            restartHandler.removeCallbacks(restartListeningRunnable)
                            restartHandler.postDelayed(restartListeningRunnable, 800L)
                        }
                    }
                }
            }
        )
        liveClient?.start()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "myra_overlay_channel",
            "MYRA Assistant",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "myra_overlay_channel")
            .setContentTitle("MYRA System Active")
            .setContentText("Double press power button to summon MYRA")
            .setSmallIcon(R.mipmap.img)
            .setOngoing(true)
            .build()
    }

    companion object {
        var isRunning: Boolean = false
        var isOverlayVisible: Boolean = false

        fun updateState(context: Context, speaking: Boolean = false, thinking: Boolean = false) {
            val intent = Intent(context, MyraOverlayService::class.java).apply {
                action = "UPDATE_STATE"
                putExtra("speaking", speaking)
                putExtra("thinking", thinking)
            }
            if (isRunning) {
                context.startService(intent)
            }
        }
    }
}
