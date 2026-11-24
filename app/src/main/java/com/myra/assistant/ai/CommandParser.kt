package com.myra.assistant.ai

import android.content.Context
import com.myra.assistant.model.AppCommand
import com.myra.assistant.utils.HindiTransliterator
import java.util.Locale

class CommandParser(private val context: Context) {

    private val WHATSAPP_WORDS = listOf(
        "whatsapp", "whats app", "whatsup", "watsp", "wasap", "watsap",
        "wa pe", "wa par", "wa ko", "what's", "whats",
        "व्हाट्सएप", "व्हाट्स", "व्हाट्सप"
    )
    private val CALL_WORDS = listOf(
        "call", "phone", "dial", "ring", "milaao", "milao", "lagao", "laga",
        "karo", "kar", "baat", "कॉल", "मिलाओ"
    )
    private val MESSAGE_WORDS = listOf(
        "message", "msg", "likho", "likh", "bhejo", "bhej", "send", "text",
        "bol", "bolo", "sms", "मैसेज", "संदेश"
    )
    private val FILLER_WORDS = listOf(
        "ko", "se", "pe", "par", "ka", "ki", "ke", "hai", "hain", "do",
        "please", "kripya", "mujhe", "mera", "mere",
        "को", "से", "पर", "का", "की", "के"
    )
    private val YOUTUBE_SKIP_WORDS = listOf(
        "youtube", "ytube", "you tube", "यूट्यूब", "play", "bajao", "chalao",
        "chala", "search", "dhoondo", "video", "gaana", "song", "gana",
        "dekho", "dekhna"
    )
    private val SPOTIFY_SKIP_WORDS = listOf(
        "spotify", "sportify", "spotifi", "स्पॉटिफाई", "play", "bajao",
        "sunao", "suno", "chalao", "chala", "music", "gaana", "song", "gana",
        "sangeet"
    )

    fun parse(input: String): AppCommand? {
        if (input.isBlank()) {
            return null
        }
        val normalized = HindiTransliterator.INSTANCE.normalizeForCommands(input)
        val lower = normalized.lowercase(Locale.ROOT).trim()
        return parseStructuredFormat(lower, normalized) ?: parseVoiceInput(lower, normalized)
    }

    private fun parseStructuredFormat(lower: String, original: String): AppCommand? {
        if (lower.contains("open_app")) {
            val appName = extractAfterKeyword(original, listOf("OPEN_APP", "open_app"))
                ?.let { cleanFiller(it) }
                ?: return null
            return AppCommand("OPEN_APP", mapOf("app_name" to appName))
        }
        if (lower.contains("youtube_play")) {
            val query = extractAfterKeyword(original, listOf("YOUTUBE_PLAY", "youtube_play"))
                ?: return null
            return AppCommand("YOUTUBE_PLAY", mapOf("query" to query))
        }
        if (lower.contains("spotify_play")) {
            val query = extractAfterKeyword(original, listOf("SPOTIFY_PLAY", "spotify_play"))
                ?: return null
            return AppCommand("SPOTIFY_PLAY", mapOf("query" to query))
        }
        if (lower.contains("whatsapp_msg")) {
            val rest = extractAfterKeyword(original, listOf("WHATSAPP_MSG", "whatsapp_msg"))
                ?: return null
            val parts = rest.trim().split(" ", limit = 2)
            val name = if (parts.isNotEmpty()) parts[0] else ""
            val message = if (parts.size > 1) parts[1] else "Hi"
            return AppCommand(
                "WHATSAPP_MSG",
                mapOf("name" to name, "number" to "", "message" to message)
            )
        }
        if (lower.contains("whatsapp_call")) {
            val name = extractAfterKeyword(original, listOf("WHATSAPP_CALL", "whatsapp_call"))
                ?.let { cleanFiller(it) }
                ?: return null
            return AppCommand("WHATSAPP_CALL", mapOf("name" to name, "number" to ""))
        }
        return null
    }

    private fun parseVoiceInput(lower: String, original: String): AppCommand? {
        if (isWhatsAppMention(lower)) {
            val hasOpenWord = lower.contains("open") || lower.contains("khol") || lower.contains("chala")
            if (hasOpenWord && !hasMessageIntent(lower) && !hasCallIntent(lower)) {
                // Plain "open WhatsApp ..." with a contact named: fall through below
                // unless no contact could be extracted at all.
                if (extractContactFromWhatsAppCmd(lower).isNullOrEmpty()) {
                    return AppCommand("OPEN_APP", mapOf("app_name" to "whatsapp"))
                }
            }
            val contact = extractContactFromWhatsAppCmd(lower) ?: ""
            val hasMessage = hasMessageIntent(lower)
            val hasCall = hasCallIntent(lower)
            if (contact.isEmpty() && !hasMessage && !hasCall) {
                return AppCommand("OPEN_APP", mapOf("app_name" to "whatsapp"))
            }
            if (hasCall) {
                return AppCommand("WHATSAPP_CALL", mapOf("name" to contact, "number" to ""))
            }
            if (hasMessage) {
                val message = extractMessageText(lower) ?: "Hi"
                return AppCommand(
                    "WHATSAPP_MSG",
                    mapOf("name" to contact, "number" to "", "message" to message)
                )
            }
            return if (contact.isNotEmpty()) {
                AppCommand(
                    "WHATSAPP_MSG",
                    mapOf("name" to contact, "number" to "", "message" to "Hi")
                )
            } else {
                AppCommand("OPEN_APP", mapOf("app_name" to "whatsapp"))
            }
        }

        if (isYouTubeMention(lower)) {
            val query = extractMediaQuery(lower, YOUTUBE_SKIP_WORDS)
            return if (!query.isNullOrEmpty()) {
                AppCommand("YOUTUBE_PLAY", mapOf("query" to query))
            } else {
                AppCommand("OPEN_APP", mapOf("app_name" to "youtube"))
            }
        }

        if (isSpotifyMention(lower)) {
            val query = extractMediaQuery(lower, SPOTIFY_SKIP_WORDS)
            return if (!query.isNullOrEmpty()) {
                AppCommand("SPOTIFY_PLAY", mapOf("query" to query))
            } else {
                AppCommand("OPEN_APP", mapOf("app_name" to "spotify"))
            }
        }

        if (hasCallIntent(lower) && !isWhatsAppMention(lower)) {
            val contact = extractContactForCall(lower)
            if (!contact.isNullOrEmpty()) {
                return AppCommand("CALL", mapOf("name" to contact, "number" to ""))
            }
        }

        for (keyword in listOf("open", "kholo", "khol do", "khol", "start", "launch", "chala", "chalao")) {
            if (lower.contains(keyword)) {
                val appName = extractAppNameAfterKeyword(lower, keyword)
                if (!appName.isNullOrEmpty() && appName.length > 1) {
                    return AppCommand("OPEN_APP", mapOf("app_name" to appName))
                }
            }
        }

        if (lower.contains("sms") || lower.contains("message bhejo")) {
            val contact = extractContactForCall(lower) ?: ""
            val message = extractMessageText(lower) ?: "Hi"
            return AppCommand(
                "SMS",
                mapOf("name" to contact, "number" to "", "message" to message)
            )
        }

        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash")) {
            val on = !lower.contains("off") && !lower.contains("band")
            return AppCommand("FLASHLIGHT", mapOf("on" to on.toString()))
        }

        if (lower.contains("volume") || lower.contains("sound") || lower.contains("awaaz")) {
            return AppCommand("VOLUME", mapOf("action" to lower))
        }

        return null
    }

    private fun isWhatsAppMention(input: String): Boolean {
        val words = listOf(
            "whatsapp", "whats app", "whatsup", "watsp", "wasap", "watsap",
            "wa pe", "wa par", "wa ko", "wa me",
            "व्हाट्सएप", "व्हाट्स", "व्हाट्सप"
        )
        if (words.any { input.contains(it) }) {
            return true
        }
        return (input.contains("what's") || input.contains("whats")) &&
            (hasCallIntent(input) || hasMessageIntent(input) ||
                input.contains("pe") || input.contains("par"))
    }

    private fun isYouTubeMention(input: String): Boolean {
        return input.contains("youtube") || input.contains("ytube") ||
            input.contains("you tube") || input.contains("यूट्यूब")
    }

    private fun isSpotifyMention(input: String): Boolean {
        return input.contains("spotify") || input.contains("sportify") ||
            input.contains("spotifi") || input.contains("स्पॉटिफाई")
    }

    private fun hasCallIntent(input: String): Boolean {
        return input.contains("call") || input.contains("phone") ||
            input.contains("dial") || input.contains("ring") ||
            input.contains("milaao") || input.contains("milao") ||
            input.contains("lagao") || input.contains("laga") ||
            input.contains("baat") || input.contains("karo") ||
            input.contains("कॉल") || input.contains("मिलाओ")
    }

    private fun hasMessageIntent(input: String): Boolean {
        return input.contains("message") || input.contains("msg") ||
            input.contains("likho") || input.contains("likh") ||
            input.contains("bhejo") || input.contains("bhej") ||
            input.contains("send") || input.contains("text") ||
            input.contains("bol") || input.contains("bolo") ||
            input.contains("बोलो") || input.contains("मैसेज") ||
            input.contains("संदेश")
    }

    private fun extractContactFromWhatsAppCmd(input: String): String? {
        var text = input
        for (word in WHATSAPP_WORDS) {
            text = text.replace(word, " ")
        }
        for (word in CALL_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        for (word in MESSAGE_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        for (word in FILLER_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        text = Regex("\\s+").replace(text, " ").trim()
        // Cut off anything after a message separator ("... bol/bolo/ki/that/saying ...").
        for (separator in listOf(" bol ", " bolo ", " ki ", " that ", " saying ")) {
            val index = text.indexOf(separator)
            if (index > 0) {
                text = text.substring(0, index).trim()
                break
            }
        }
        return if (text.isNotEmpty() && text.length >= 2) text else null
    }

    private fun extractContactForCall(input: String): String? {
        var text = input
        for (word in CALL_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        for (word in FILLER_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        text = Regex("\\s+").replace(text, " ").trim()
        return if (text.isNotEmpty() && text.length >= 2) text else null
    }

    private fun extractMessageText(input: String): String? {
        for (prefix in listOf(
            "bol ", "bolo ", "likho ", "bhejo ", "message ", "msg ",
            "send ", "text ", "saying ", "ki ", "that ", "bata "
        )) {
            val index = input.indexOf(prefix)
            if (index < 0) continue
            val message = input.substring(index + prefix.length).trim()
            if (message.length > 1) {
                return message
            }
        }
        return null
    }

    private fun extractMediaQuery(input: String, skipWords: List<String>): String? {
        var text = input
        for (word in skipWords) {
            text = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
                .replace(text, " ")
        }
        for (word in FILLER_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        text = Regex("\\s+").replace(text, " ").trim()
        return if (text.isNotEmpty() && text.length >= 2) text else null
    }

    private fun extractAfterKeyword(input: String, keywords: List<String>): String? {
        for (keyword in keywords) {
            val index = input.indexOf(keyword, ignoreCase = true)
            if (index < 0) continue
            val rest = input.substring(index + keyword.length).trim()
            if (rest.isNotEmpty()) {
                return rest
            }
        }
        return null
    }

    private fun extractAppNameAfterKeyword(input: String, keyword: String): String? {
        val index = input.indexOf(keyword)
        if (index < 0) {
            return null
        }
        var text = input.substring(index + keyword.length).trim()
        for (word in FILLER_WORDS) {
            text = Regex("\\b${Regex.escape(word)}\\b").replace(text, " ")
        }
        text = Regex("\\s+").replace(text, " ").trim()
        return if (text.isNotEmpty()) cleanFiller(text) else null
    }

    private fun cleanFiller(input: String): String {
        var text = input
        for (word in listOf("karo", "do", "please", "kholo", "chalao", "kar", "दो", "खोल")) {
            text = Regex("\\s+${Regex.escape(word)}$", RegexOption.IGNORE_CASE)
                .replace(text, "").trim()
        }
        return text.trim()
    }
}
