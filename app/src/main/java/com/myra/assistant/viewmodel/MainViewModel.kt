package com.myra.assistant.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.ai.GeminiClient
import com.myra.assistant.automation.CallAutomation
import com.myra.assistant.automation.WhatsAppAutomation
import com.myra.assistant.managers.AppLaunchManager
import com.myra.assistant.managers.ContactManager
import com.myra.assistant.managers.FlashlightManager
import com.myra.assistant.managers.VolumeManager
import com.myra.assistant.model.AIState
import com.myra.assistant.model.AITask
import com.myra.assistant.service.AccessibilityEventRouter
import com.myra.assistant.utils.AccessibilityVisualFeedback
import com.myra.assistant.utils.HindiTransliterator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restored from CFR-decompiled bytecode
 * (decompiled-java/com/myra/assistant/viewmodel/MainViewModel.java).
 *
 * Core logic hub: routes a voice/text command (direct Gemini command
 * strings such as "open_app ...", "whatsapp_msg ..." as well as natural
 * Hinglish voice input) to WhatsApp / call / SMS / YouTube / Spotify /
 * app-launch / flashlight / volume actions, and posts the spoken reply
 * on [aiResponse] for MainActivity to speak/display.
 *
 * Method/field names, word lists, Hindi/Hinglish reply strings and
 * branching follow the decompiled output exactly; the @Metadata d2
 * strings in the CFR header were used to confirm the Kotlin member
 * names and parameter names.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    private val _aiResponse = MutableLiveData<String?>(null)
    val aiResponse: LiveData<String?> = _aiResponse

    private val TAG = "MYRA_VM"

    private val appLaunchManager = AppLaunchManager(context)
    private val contactManager = ContactManager(context)
    private val flashlightManager = FlashlightManager(context)
    private val volumeManager = VolumeManager(context)
    private val geminiClient = GeminiClient(context)

    // Short rolling history so free-chat (REST) replies stay in context.
    private val chatHistory = mutableListOf<Map<String, String>>()

    private val WHATSAPP_WORDS = listOf(
        "whatsapp", "whats app", "whatsup", "watsp", "wasap", "watsap",
        "wa pe", "wa par", "wa ko", "व्हाट्सएप", "व्हाट्स"
    )
    private val WHATSAPP_SKIP = listOf(
        "whatsapp", "whats app", "whatsup", "watsp", "wasap",
        "wa pe", "wa par", "wa ko", "wa"
    )
    private val CALL_WORDS = listOf(
        "call", "phone", "dial", "ring", "milaao", "milao", "lagao",
        "laga", "baat", "कॉल", "मिलाओ"
    )
    private val MSG_WORDS = listOf(
        "message", "msg", "likho", "likh", "bhejo", "bhej", "send",
        "text", "bol", "bolo", "sms", "मैसेज"
    )
    private val FILLERS = listOf(
        "ko", "se", "pe", "par", "ka", "ki", "ke", "hai", "karo", "kar",
        "do", "please", "mujhe", "mera", "mere",
        "को", "से", "पर", "का", "की", "के"
    )
    private val YOUTUBE_SKIP = listOf(
        "youtube", "ytube", "you tube", "play", "bajao", "chalao",
        "chala", "search", "video", "gaana", "song", "gana", "dekho"
    )
    private val SPOTIFY_SKIP = listOf(
        "spotify", "sportify", "spotifi", "play", "bajao", "sunao",
        "suno", "chalao", "chala", "music", "gaana", "song", "gana"
    )

    fun isDirectCommand(input: String): Boolean {
        val normalized = HindiTransliterator.normalizeForCommands(input)
            .lowercase(Locale.ROOT).trim()
        val responsePhrases = listOf(
            "kar rahi hoon", "kar raha hoon", "khul gaya", "khula hai",
            "bhej diya", "play ho raha", "calling", "searching",
            "maaf kijiye", "nahi mila", "theek hai", "done", "ok jaan"
        )
        if (responsePhrases.any { normalized.contains(it) }) return false
        val commandPrefixes = listOf(
            "open_app", "youtube_play", "spotify_play", "whatsapp_msg",
            "whatsapp_call", "call ", "sms ", "flashlight", "volume_"
        )
        if (commandPrefixes.any { normalized.startsWith(it) }) return true
        return isWhatsAppCmd(normalized) || isCallCmd(normalized) ||
            isYouTubeCmd(normalized) || isSpotifyCmd(normalized) ||
            isOpenAppCmd(normalized) || isMediaCmd(normalized) ||
            isSystemCmd(normalized)
    }

    fun processCommand(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = HindiTransliterator.normalizeForCommands(input).trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            Log.d(TAG, "processCommand: '$trimmed'")
            AccessibilityVisualFeedback.showActionFeedback(context, "cmd")
            val result = when {
                lower.startsWith("open_app ") ->
                    doOpenApp(
                        trimmed.removePrefix("open_app ")
                            .removePrefix("OPEN_APP ").trim()
                    )
                lower.startsWith("youtube_play ") ->
                    doYouTube(
                        trimmed.removePrefix("youtube_play ")
                            .removePrefix("YOUTUBE_PLAY ").trim()
                    )
                lower.startsWith("spotify_play ") ->
                    doSpotify(
                        trimmed.removePrefix("spotify_play ")
                            .removePrefix("SPOTIFY_PLAY ").trim()
                    )
                lower.startsWith("whatsapp_msg ") -> parseWhatsAppMsg(trimmed)
                lower.startsWith("whatsapp_call ") -> parseWhatsAppCall(trimmed)
                lower.startsWith("call ") ->
                    doCall(
                        trimmed.removePrefix("call ")
                            .removePrefix("CALL ").trim()
                    )
                lower.startsWith("sms ") -> parseSms(trimmed)
                lower == "flashlight_on" -> doFlashlight(true)
                lower == "flashlight_off" -> doFlashlight(false)
                lower == "volume_up" -> doVolume(true)
                lower == "volume_down" -> doVolume(false)
                isWhatsAppCmd(lower) -> handleWhatsAppVoice(lower, trimmed)
                isCallCmd(lower) -> handleCallVoice(lower)
                isYouTubeCmd(lower) -> handleYouTubeVoice(lower)
                isSpotifyCmd(lower) -> handleSpotifyVoice(lower)
                isOpenAppCmd(lower) -> doOpenApp(extractAppName(lower))
                isSystemCmd(lower) -> handleSystemVoice(lower)
                else -> chatWithGemini(trimmed)
            }
            try {
                _aiResponse.postValue(result)
            } catch (e: Exception) {
                Log.e(TAG, "processCommand error: ${e.message}")
                _aiResponse.postValue("Kuch gadbad ho gayi")
            }
        }
    }

    /**
     * Free-chat fallback: when the input is not a device command, ask Gemini
     * over REST (gemini-1.5-flash) instead of the hardcoded "Samajh nahi aaya"
     * reply. Works even when the Live websocket is down; GeminiClient itself
     * reports a missing key / API error in plain words.
     */
    private suspend fun chatWithGemini(input: String): String {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val userName = prefs.getString("user_name", "Sir") ?: "Sir"
        val reply = geminiClient.sendMessage(input, chatHistory.toList(), personality, userName)
        val isError = reply.startsWith("API Error") || reply.startsWith("API key nahi hai") ||
            reply.startsWith("Connection problem") || reply.startsWith("Response parse")
        if (isError) return reply
        // Strip any [COMMAND:...] tokens Gemini may include; this path only chats.
        val clean = reply.replace(Regex("\\[[A-Z_]+(?::[^\\]]*)?\\]"), "").trim()
        val finalReply = clean.ifEmpty { "Samajh nahi aaya, dobara bolo" }
        chatHistory.add(mapOf("role" to "user", "content" to input))
        chatHistory.add(mapOf("role" to "model", "content" to finalReply))
        while (chatHistory.size > 10) chatHistory.removeAt(0)
        return finalReply
    }

    private fun parseWhatsAppMsg(input: String): String {
        val rest = Regex("(?i)whatsapp_msg\\s*").replace(input, "").trim()
        val parts = rest.split(" ", limit = 2)
        val contact = if (parts.isNotEmpty()) parts[0].trim() else ""
        val message = if (parts.size > 1) parts[1] else "Hi"
        return doWhatsAppMsg(contact, message.trim())
    }

    private fun parseWhatsAppCall(input: String): String {
        return doWhatsAppCall(
            Regex("(?i)whatsapp_call\\s*").replace(input, "").trim()
        )
    }

    private fun parseSms(input: String): String {
        val rest = Regex("(?i)sms\\s*").replace(input, "").trim()
        val parts = rest.split(" ", limit = 2)
        val name = if (parts.isNotEmpty()) parts[0] else ""
        val message = if (parts.size > 1) parts[1] else "Hi"
        return doSms(name, message)
    }

    private fun handleWhatsAppVoice(lower: String, original: String): String {
        // NOTE: `original` is a dead parameter in the original — the
        // decompiled body overwrites its local copy with `lower` on the
        // first line, so only `lower` is ever used below. The parameter
        // is kept so the signature matches the @Metadata exactly.
        @Suppress("UNUSED_PARAMETER")
        val unusedOriginal = original
        val openWords = listOf("open", "kholo", "khol", "launch", "start", "chala")
        val isOpen = openWords.any { lower.contains(it) }
        val contact = extractContact(
            lower,
            WHATSAPP_SKIP + CALL_WORDS + MSG_WORDS + FILLERS + openWords
        )
        val isCall = CALL_WORDS.any { lower.contains(it) }
        val isMessage = MSG_WORDS.any { lower.contains(it) }
        return when {
            isOpen && contact.isEmpty() -> doOpenApp("whatsapp")
            isMessage -> doWhatsAppMsg(contact, extractMessageText(lower) ?: "Hi")
            isCall -> doWhatsAppCall(contact)
            contact.isNotEmpty() -> doWhatsAppCall(contact)
            else -> doOpenApp("whatsapp")
        }
    }

    private fun handleCallVoice(lower: String): String {
        return if (WHATSAPP_WORDS.any { lower.contains(it) }) {
            doWhatsAppCall(extractContact(lower, WHATSAPP_SKIP + CALL_WORDS + FILLERS))
        } else {
            doCall(extractContact(lower, CALL_WORDS + FILLERS))
        }
    }

    private fun handleYouTubeVoice(lower: String): String {
        val openWords = listOf("open", "kholo", "khol", "launch", "start", "chala")
        val isOpen = openWords.any { lower.contains(it) }
        val query = extractMediaQuery(lower, YOUTUBE_SKIP + openWords)
        if (!isOpen) return doYouTube(query)
        if (query.isEmpty()) return doOpenApp("youtube")
        return doYouTube(query)
    }

    private fun handleSpotifyVoice(lower: String): String {
        val openWords = listOf("open", "kholo", "khol", "launch", "start", "chala")
        val isOpen = openWords.any { lower.contains(it) }
        val query = extractMediaQuery(lower, SPOTIFY_SKIP + openWords)
        if (!isOpen) return doSpotify(query)
        if (query.isEmpty()) return doOpenApp("spotify")
        return doSpotify(query)
    }

    private fun handleSystemVoice(lower: String): String {
        return when {
            lower.contains("flashlight") || lower.contains("torch") ->
                doFlashlight(!(lower.contains("off") || lower.contains("band")))
            lower.contains("volume") || lower.contains("awaaz") ->
                doVolume(
                    lower.contains("up") || lower.contains("badhao") ||
                        lower.contains("zyada")
                )
            lower.contains("settings") || lower.contains("setting") ->
                doOpenApp("settings")
            lower.contains("wifi") || lower.contains("bluetooth") ->
                doOpenApp("settings")
            else -> "Samajh nahi aaya"
        }
    }

    private fun doWhatsAppMsg(contact: String, message: String): String {
        val name = HindiTransliterator.transliterate(contact).trim()
        var msg = message.trim()
        if (msg.isEmpty()) msg = "Hi"
        Log.d(TAG, "WhatsApp MSG: contact='$name' msg='$msg'")
        val packageName =
            if (!isAppInstalled("com.whatsapp.w4b") || isAppInstalled("com.whatsapp")) {
                "com.whatsapp"
            } else {
                "com.whatsapp.w4b"
            }
        val number = if (name.any { it.isDigit() }) {
            name
        } else {
            contactManager.resolveNumber("", name)
        }
        if (number.isEmpty()) {
            launchApp(packageName)
            return "Contact nahi mila, WhatsApp khul gaya"
        }
        val formatted = WhatsAppAutomation.formatNumberForWhatsApp(number)
        AccessibilityEventRouter.reset()
        AIState.task = AITask(
            "WHATSAPP_MSG",
            mapOf("contact" to formatted, "name" to name, "message" to msg)
        )
        AIState.step = null
        return if (launchApp(packageName)) {
            "$name ko WhatsApp message bhej rahi hoon"
        } else {
            "WhatsApp install nahi hai"
        }
    }

    private fun doWhatsAppCall(contact: String): String {
        val name = HindiTransliterator.transliterate(contact).trim()
        Log.d(TAG, "WhatsApp CALL: '$name'")
        val packageName =
            if (!isAppInstalled("com.whatsapp.w4b") || isAppInstalled("com.whatsapp")) {
                "com.whatsapp"
            } else {
                "com.whatsapp.w4b"
            }
        val number = if (name.any { it.isDigit() }) {
            name
        } else {
            contactManager.resolveNumber("", name)
        }
        if (number.isEmpty()) {
            launchApp(packageName)
            return "Contact nahi mila, WhatsApp khul gaya"
        }
        val formatted = WhatsAppAutomation.formatNumberForWhatsApp(number)
        AccessibilityEventRouter.reset()
        AIState.task = AITask(
            "WHATSAPP_CALL",
            mapOf("contact" to formatted, "name" to name, "video" to "false")
        )
        AIState.step = null
        return if (launchApp(packageName)) {
            "$name ko WhatsApp call kar rahi hoon"
        } else {
            "WhatsApp install nahi hai"
        }
    }

    private fun doYouTube(query: String): String {
        val trimmed = query.trim()
        Log.d(TAG, "YouTube: '$trimmed'")
        if (trimmed.isNotEmpty()) {
            AccessibilityEventRouter.reset()
            AIState.task = AITask("YOUTUBE_PLAY", mapOf("query" to trimmed))
            AIState.step = null
        }
        return if (appLaunchManager.openApp("com.google.android.youtube")) {
            if (trimmed.isNotEmpty()) {
                "YouTube pe '$trimmed' search kar rahi hoon"
            } else {
                "YouTube khul gaya"
            }
        } else {
            "YouTube install nahi hai"
        }
    }

    private fun doSpotify(query: String): String {
        val trimmed = query.trim()
        Log.d(TAG, "Spotify: '$trimmed'")
        if (trimmed.isNotEmpty()) {
            AccessibilityEventRouter.reset()
            AIState.task = AITask("SPOTIFY_PLAY", mapOf("query" to trimmed))
            AIState.step = null
        }
        return if (appLaunchManager.openApp("com.spotify.music")) {
            if (trimmed.isNotEmpty()) {
                "Spotify pe '$trimmed' play kar rahi hoon"
            } else {
                "Spotify khul gaya"
            }
        } else {
            "Spotify install nahi hai"
        }
    }

    private fun doCall(nameOrNumber: String): String {
        val trimmed = nameOrNumber.trim()
        if (trimmed.isEmpty()) return "Kisko call karoon? Naam bolo"
        val number = if (trimmed.any { it.isDigit() }) {
            trimmed
        } else {
            contactManager.resolveNumber("", trimmed)
        }
        if (number.isEmpty()) return "'$trimmed' ka number nahi mila"
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "Call ki permission chahiye"
        }
        return try {
            CallAutomation.makeCall(context, number)
            "$trimmed ko call kar rahi hoon"
        } catch (e: Exception) {
            "Call nahi ho payi: ${e.message}"
        }
    }

    private fun doSms(name: String, message: String): String {
        val number = if (name.any { it.isDigit() }) {
            name
        } else {
            contactManager.resolveNumber("", name)
        }
        if (number.isEmpty()) return "'$name' ka number nahi mila"
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "$name ko SMS bhej raha hoon"
        } catch (e: Exception) {
            "SMS nahi bheja"
        }
    }

    private fun doOpenApp(appName: String): String {
        val lower = appName.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return "Kaun sa app kholoon?"
        val packageName: String? = when {
            lower.contains("whatsapp") || lower.contains("watsp") ||
                lower.contains("wasap") -> "com.whatsapp"
            lower.contains("youtube") || lower.contains("ytube") ->
                "com.google.android.youtube"
            lower.contains("spotify") || lower.contains("sportify") ||
                lower.contains("spotifi") -> "com.spotify.music"
            lower.contains("instagram") || lower.contains("insta") ->
                "com.instagram.android"
            lower.contains("facebook") || lower.contains("fb") ->
                "com.facebook.katana"
            lower.contains("twitter") || lower.contains("x app") ->
                "com.twitter.android"
            lower.contains("camera") -> null
            lower.contains("settings") || lower.contains("setting") ->
                "com.android.settings"
            lower.contains("gmail") -> "com.google.android.gm"
            lower.contains("maps") || lower.contains("google maps") ->
                "com.google.android.apps.maps"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("calculator") || lower.contains("calc") -> null
            else -> appLaunchManager.getPackageName(lower)
        }
        if (lower.contains("camera")) {
            return try {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Camera khul gaya"
            } catch (e: Exception) {
                "Camera nahi khula"
            }
        }
        if (packageName != null) {
            return if (launchApp(packageName)) {
                "$appName khul gaya"
            } else {
                "$appName install nahi hai"
            }
        }
        val resolved = appLaunchManager.getPackageName(lower)
        return if (resolved != null) {
            if (launchApp(resolved)) "$appName khul gaya" else "$appName nahi khula"
        } else {
            "$appName nahi mila"
        }
    }

    private fun doFlashlight(on: Boolean): String {
        return try {
            flashlightManager.setFlashlight(on)
            if (on) "Flashlight on" else "Flashlight off"
        } catch (e: Exception) {
            "Flashlight control nahi ho paya"
        }
    }

    private fun doVolume(up: Boolean): String {
        return try {
            volumeManager.adjustVolume(up)
            if (up) "Volume badha diya" else "Volume kam kar diya"
        } catch (e: Exception) {
            "Volume adjust nahi hua"
        }
    }

    private fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp '$packageName': ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isWhatsAppCmd(t: String): Boolean {
        return WHATSAPP_WORDS.any { t.contains(it) }
    }

    private fun isCallCmd(t: String): Boolean {
        return CALL_WORDS.any { t.contains(it) } &&
            !isYouTubeCmd(t) && !isSpotifyCmd(t)
    }

    private fun isYouTubeCmd(t: String): Boolean {
        return listOf("youtube", "ytube", "you tube").any { t.contains(it) }
    }

    private fun isSpotifyCmd(t: String): Boolean {
        return listOf("spotify", "sportify", "spotifi").any { t.contains(it) }
    }

    private fun isOpenAppCmd(t: String): Boolean {
        return listOf("open", "kholo", "khol", "launch", "start", "chala")
            .any { t.contains(it) }
    }

    private fun isMediaCmd(t: String): Boolean {
        return listOf("camera", "photo", "gallery").any { t.contains(it) }
    }

    private fun isSystemCmd(t: String): Boolean {
        return listOf(
            "flashlight", "torch", "volume", "awaaz",
            "settings", "wifi", "bluetooth"
        ).any { t.contains(it) }
    }

    private fun extractContact(lower: String, skipWords: List<String>): String {
        var result = lower
        for (separator in listOf(
            " bol ", " bolo ", " ki ", " that ", " likho ",
            " kaho ", " saying ", " message ", " msg "
        )) {
            val index = result.indexOf(separator)
            if (index > 0) {
                result = result.substring(0, index).trim()
                break
            }
        }
        var cleaned = result
        for (word in skipWords.sortedByDescending { it.length }) {
            cleaned = Regex("\\b${Regex.escape(word)}\\b").replace(cleaned, " ")
        }
        val extracted = Regex("\\s+").replace(cleaned, " ").trim()
        Log.d(TAG, "Extracted Contact: '$extracted' from input: '$lower'")
        return extracted
    }

    private fun extractMessageText(lower: String): String? {
        for (prefix in listOf(
            "bol ", "bolo ", "likho ", "bhejo ", "message ", "msg ",
            "send ", "text ", "ki ", "that ", "saying ", "bata "
        )) {
            val index = lower.indexOf(prefix)
            if (index < 0) continue
            val message = lower.substring(index + prefix.length).trim()
            if (message.length > 1) return message
        }
        return null
    }

    private fun extractMediaQuery(lower: String, skipWords: List<String>): String {
        var result = lower
        for (word in skipWords) {
            result = Regex("\\b${Regex.escape(word)}\\b").replace(result, " ")
        }
        for (word in FILLERS) {
            result = Regex("\\b${Regex.escape(word)}\\b").replace(result, " ")
        }
        return Regex("\\s+").replace(result, " ").trim()
    }

    private fun extractAppName(lower: String): String {
        var result = lower
        for (word in listOf(
            "open", "kholo", "khol do", "khol",
            "launch", "start", "chala", "chalao"
        )) {
            result = Regex("\\b${Regex.escape(word)}\\b").replace(result, " ")
        }
        for (word in FILLERS) {
            result = Regex("\\b${Regex.escape(word)}\\b").replace(result, " ")
        }
        return Regex("\\s+").replace(result, " ").trim()
    }
}
