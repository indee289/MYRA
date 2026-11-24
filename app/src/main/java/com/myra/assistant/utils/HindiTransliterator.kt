package com.myra.assistant.utils

import java.util.Locale

/**
 * Transliterates Devanagari (Hindi) text to Latin (Roman) script and normalises
 * Hindi voice commands to English keywords.
 *
 * Restored from CFR-decompiled bytecode of the user's own MYRA app
 * (com.myra.assistant). Both lookup tables are reproduced entry-for-entry,
 * in order, including duplicate keys — in a linkedMapOf the later value wins
 * (e.g. "व" maps to "w", "चलाओ" maps to "play").
 */
object HindiTransliterator {

    private val HINDI_TO_ENGLISH_MAP: LinkedHashMap<String, String> = linkedMapOf(
        "\u0915\u094d\u0937" to "ksh",
        "\u0924\u094d\u0930" to "tr",
        "\u091c\u094d\u091e" to "gy",
        "\u0936\u094d\u0930" to "shr",
        "\u0938\u094d\u0924" to "st",
        "\u0915\u094d\u0924" to "kt",
        "\u0926\u094d\u092f" to "dy",
        "\u0926\u094d\u0935" to "dw",
        "\u0926\u094d\u0930" to "dr",
        "\u092a\u094d\u0930" to "pr",
        "\u092d\u094d\u0930" to "bhr",
        "\u092e\u094d\u0930" to "mr",
        "\u0924\u094d\u0935" to "tw",
        "\u0920\u094d\u092f" to "thy",
        "\u0921\u094d\u092f" to "dy",
        "\u0922\u094d\u092f" to "dhy",
        "\u0915" to "k",
        "\u0916" to "kh",
        "\u0917" to "g",
        "\u0918" to "gh",
        "\u0919" to "ng",
        "\u091a" to "ch",
        "\u091b" to "chh",
        "\u091c" to "j",
        "\u091d" to "jh",
        "\u091e" to "ny",
        "\u091f" to "t",
        "\u0920" to "th",
        "\u0921" to "d",
        "\u0922" to "dh",
        "\u0923" to "n",
        "\u0924" to "t",
        "\u0925" to "th",
        "\u0926" to "d",
        "\u0927" to "dh",
        "\u0928" to "n",
        "\u092a" to "p",
        "\u092b" to "f",
        "\u092c" to "b",
        "\u092d" to "bh",
        "\u092e" to "m",
        "\u092f" to "y",
        "\u0930" to "r",
        "\u0932" to "l",
        "\u0935" to "v",
        "\u0935" to "w",
        "\u0936" to "sh",
        "\u0937" to "sh",
        "\u0938" to "s",
        "\u0939" to "h",
        "\u0921\u093c" to "r",
        "\u0922\u093c" to "rh",
        "\u092b\u093c" to "f",
        "\u091c\u093c" to "z",
        "\u0916\u093c" to "kh",
        "\u0905" to "a",
        "\u0906" to "a",
        "\u0907" to "i",
        "\u0908" to "i",
        "\u0909" to "u",
        "\u090a" to "u",
        "\u090b" to "ri",
        "\u090f" to "e",
        "\u0910" to "ai",
        "\u0913" to "o",
        "\u0914" to "au",
        "\u0905\u0902" to "an",
        "\u0905\u0903" to "ah",
        "\u0901" to "n",
        "\u093e" to "a",
        "\u093f" to "i",
        "\u0940" to "i",
        "\u0941" to "u",
        "\u0942" to "u",
        "\u0943" to "ri",
        "\u0947" to "e",
        "\u0948" to "ai",
        "\u094b" to "o",
        "\u094c" to "au",
        "\u0902" to "n",
        "\u0903" to "h",
        "\u094d" to "",
        "\u0966" to "0",
        "\u0967" to "1",
        "\u0968" to "2",
        "\u0969" to "3",
        "\u096a" to "4",
        "\u096b" to "5",
        "\u096c" to "6",
        "\u096d" to "7",
        "\u096e" to "8",
        "\u096f" to "9",
    )

    private val COMMAND_KEYWORD_MAP: LinkedHashMap<String, String> = linkedMapOf(
        "\u0915\u0949\u0932" to "call",
        "\u0915\u093e\u0932" to "call",
        "\u092b\u094b\u0928" to "phone",
        "\u092e\u093f\u0932\u093e\u0913" to "call",
        "\u0932\u0917\u093e\u0913" to "call",
        "\u0932\u0917\u093e" to "call",
        "\u092e\u093f\u0932\u093e" to "call",
        "\u0915\u0930\u094b" to "",
        "\u0926\u094b" to "",
        "\u0916\u094b\u0932\u094b" to "open",
        "\u0916\u094b\u0932" to "open",
        "\u091a\u0932\u093e\u0913" to "open",
        "\u091a\u0932\u093e" to "open",
        "\u0926\u093f\u0916\u093e\u0913" to "show",
        "\u0926\u093f\u0916\u093e" to "show",
        "\u0913\u092a\u0928" to "open",
        "\u0938\u094d\u091f\u093e\u0930\u094d\u091f" to "start",
        "\u0932\u0949\u0928\u094d\u091a" to "launch",
        "\u0935\u094d\u0939\u093e\u091f\u094d\u0938\u090f\u092a" to "whatsapp",
        "\u0935\u094d\u0939\u093e\u091f\u094d\u0938" to "whats",
        "\u0935\u094d\u0939\u093e\u091f" to "what",
        "\u0935\u094d\u0939\u093e\u091f\u0905\u092a" to "whatsapp",
        "\u092e\u0948\u0938\u0947\u091c" to "message",
        "\u0938\u0928\u094d\u0926\u0947\u0936" to "message",
        "\u0938\u0902\u0926\u0947\u0936" to "message",
        "\u092d\u0947\u091c\u094b" to "send",
        "\u092d\u0947\u091c" to "send",
        "\u0932\u093f\u0916\u094b" to "type",
        "\u0932\u093f\u0916" to "write",
        "\u0938\u0947\u0902\u0921" to "send",
        "\u091f\u0947\u0915\u094d\u0938\u094d\u091f" to "text",
        "\u090f\u0938\u090f\u092e\u090f\u0938" to "sms",
        "\u092f\u0942\u091f\u094d\u092f\u0942\u092c" to "youtube",
        "\u092f\u0942\u091f\u094d\u092f\u0942\u092c" to "youtube",
        "\u0935\u0940\u0921\u093f\u092f\u094b" to "video",
        "\u0926\u0947\u0916\u094b" to "watch",
        "\u091a\u0932\u093e\u0913" to "play",
        "\u092c\u091c\u093e\u0913" to "play",
        "\u0917\u093e\u0928\u093e" to "song",
        "\u0917\u093e\u0928\u0947" to "songs",
        "\u092e\u094d\u092f\u0942\u091c\u093f\u0915" to "music",
        "\u0938\u094d\u092a\u0949\u091f\u093f\u092b\u093e\u0908" to "spotify",
        "\u0938\u094d\u092a\u0949\u091f\u093f\u092b\u093c\u093e\u0908" to "spotify",
        "\u0938\u094d\u092a\u0949\u091f\u0940\u092b\u093e\u0908" to "spotify",
        "\u0938\u0902\u0917\u0940\u0924" to "music",
        "\u0917\u093e\u0928\u093e" to "song",
        "\u091f\u0949\u0930\u094d\u091a" to "torch",
        "\u092b\u094d\u0932\u0948\u0936" to "flash",
        "\u092b\u094d\u0932\u0948\u0936\u0932\u093e\u0907\u091f" to "flashlight",
        "\u0915\u0948\u092e\u0930\u093e" to "camera",
        "\u092b\u094b\u091f\u094b" to "photo",
        "\u0924\u0938\u094d\u0935\u0940\u0930" to "photo",
        "\u0935\u0949\u0932\u094d\u092f\u0942\u092e" to "volume",
        "\u0906\u0935\u093e\u091c" to "sound",
        "\u092c\u0922\u093c\u093e\u0913" to "up",
        "\u0918\u091f\u093e\u0913" to "down",
        "\u0915\u092e" to "down",
        "\u091c\u093c\u094d\u092f\u093e\u0926\u093e" to "up",
        "\u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938" to "settings",
        "\u0938\u0947\u091f\u093f\u0902\u0917" to "setting",
        "\u092c\u094d\u0932\u0942\u091f\u0942\u0925" to "bluetooth",
        "\u0935\u093e\u0908\u092b\u093e\u0908" to "wifi",
        "\u0921\u0947\u091f\u093e" to "data",
        "\u092e\u094b\u092c\u093e\u0907\u0932" to "mobile",
        "\u092e\u0947\u0930\u093e" to "my",
        "\u092e\u0947\u0930\u0947" to "my",
        "\u092e\u0941\u091d\u0947" to "me",
        "\u092e\u0948\u0902" to "i",
        "\u0915\u094b" to "",
        "\u0938\u0947" to "",
        "\u092a\u0947" to "",
        "\u092a\u0930" to "",
        "\u0915\u093e" to "",
        "\u0915\u0940" to "",
        "\u0915\u0947" to "",
        "\u0939\u0948" to "",
        "\u0939\u0948\u0902" to "",
        "\u0914\u0930" to "and",
        "\u092f\u093e" to "or",
        "\u0928\u0939\u0940\u0902" to "not",
        "\u0938\u092e\u092f" to "time",
        "\u0918\u0921\u093c\u0940" to "clock",
        "\u0924\u093e\u0930\u0940\u0916" to "date",
        "\u0926\u093f\u0928" to "day",
        "\u0906\u091c" to "today",
        "\u0915\u0932" to "tomorrow",
        "\u0928\u092e\u0938\u094d\u0924\u0947" to "hello",
        "\u0939\u093e\u092f" to "hi",
        "\u0939\u0947\u0932\u094b" to "hello",
        "\u0927\u0928\u094d\u092f\u0935\u093e\u0926" to "thanks",
        "\u0936\u0941\u0915\u094d\u0930\u093f\u092f\u093e" to "thank you",
        "\u0918\u0930" to "home",
        "\u0935\u093e\u092a\u0938" to "back",
        "\u092a\u0940\u091b\u0947" to "back",
        "\u090a\u092a\u0930" to "up",
        "\u0928\u0940\u091a\u0947" to "down",
        "\u092c\u0902\u0926" to "close",
        "\u092c\u093e\u0939\u0930" to "exit",
    )

    fun transliterate(hindi: String): String {
        if (hindi.isEmpty()) return ""
        // If the text contains only ASCII letters, digits or whitespace there
        // is nothing Devanagari to transliterate; return it unchanged.
        if (hindi.all { it in 'a'..'z' || it in 'A'..'Z' || it.isWhitespace() || it.isDigit() }) {
            return hindi
        }
        var result = hindi
        // Longest keys first so conjuncts (e.g. "क्ष") are replaced before
        // their constituent letters.
        for ((key, value) in HINDI_TO_ENGLISH_MAP.toList().sortedByDescending { it.first.length }) {
            result = result.replace(key, value, ignoreCase = true)
        }
        return result.trim()
    }

    fun normalizeForCommands(input: String): String {
        var result = input.lowercase(Locale.ROOT).trim()
        for ((key, value) in COMMAND_KEYWORD_MAP) {
            result = result.replace(key, value, ignoreCase = true)
        }
        val words = result.split(" ").map { word ->
            if (isHindi(word)) transliterate(word) else word
        }
        val joined = words.joinToString(" ")
        return Regex("\\s+").replace(joined, " ").trim()
    }

    fun transliterateName(hindiName: String): String {
        var result = transliterate(hindiName)
        for ((key, value) in mapOf(
            "\u091c\u0940" to "ji",
            "\u092d\u093e\u0908" to "bhai",
            "\u092c\u0939\u0928" to "behan",
            "\u0905\u0902\u0915\u0932" to "uncle",
            "\u0906\u0902\u091f\u0940" to "aunty"
        )) {
            result = result.replace(key, value, ignoreCase = true)
        }
        return result
    }

    private fun isHindi(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }
}
