package com.myra.assistant.ai

import android.content.Context
import com.myra.assistant.utils.HindiTransliterator
import java.util.Locale

class VoiceIntentResolver(private val context: Context) {

    fun resolveIntent(text: String): String {
        val normalized = HindiTransliterator.INSTANCE.normalizeForCommands(text)
            .lowercase(Locale.ROOT).trim()
        return when {
            normalized.contains("open") || normalized.contains("kholo") ||
                normalized.contains("khol") || normalized.contains("chalao") ||
                normalized.contains("chala") || normalized.contains("start") ||
                normalized.contains("launch") || normalized.contains("dikha") ||
                normalized.contains("show") -> "OPEN_APP"
            normalized.contains("whatsapp") || normalized.contains("whats") ||
                normalized.contains("whatsup") || normalized.contains("wa pe") ||
                normalized.contains("wa par") || normalized.contains("vhaats") ||
                normalized.contains("vhaat") -> "WHATSAPP"
            normalized.contains("call") || normalized.contains("phone") ||
                normalized.contains("dial") || normalized.contains("mila") ||
                normalized.contains("laga") || normalized.contains("baat") -> "CALL"
            normalized.contains("sms") || normalized.contains("message") ||
                normalized.contains("msg") || normalized.contains("text") ||
                normalized.contains("bhej") || normalized.contains("sandesh") ||
                normalized.contains("sendsms") -> "SMS"
            normalized.contains("flashlight") || normalized.contains("torch") ||
                normalized.contains("flash") || normalized.contains("tarch") ||
                normalized.contains("bati") -> "FLASHLIGHT"
            normalized.contains("volume") || normalized.contains("sound") ||
                normalized.contains("louder") || normalized.contains("softer") ||
                normalized.contains("chadhao") || normalized.contains("ghatao") ||
                normalized.contains("avaz") || normalized.contains("awaz") -> "VOLUME"
            normalized.contains("youtube") || normalized.contains("play video") ||
                normalized.contains("video play") || normalized.contains("gana bajao") ||
                normalized.contains("song play") || normalized.contains("music") ||
                normalized.contains("ytube") -> "YOUTUBE_PLAY"
            normalized.contains("spotify") || normalized.contains("song") ||
                normalized.contains("gaana") || normalized.contains("sangeet") -> "SPOTIFY_PLAY"
            normalized.contains("camera") || normalized.contains("photo") ||
                normalized.contains("pic") || normalized.contains("tasvir") ||
                normalized.contains("selfie") -> "CAMERA"
            normalized.contains("time") || normalized.contains("clock") ||
                normalized.contains("samay") || normalized.contains("ghadi") ||
                normalized.contains("date") || normalized.contains("tarikh") ||
                normalized.contains("din") -> "TIME"
            else -> "UNKNOWN"
        }
    }
}
