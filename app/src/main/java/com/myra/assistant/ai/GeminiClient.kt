package com.myra.assistant.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

class GeminiClient(private val context: Context) {

    companion object {
        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-1.5-flash"
        private const val TIMEOUT = 30000
    }

    suspend fun sendMessage(
        message: String,
        history: List<Map<String, String>>,
        personality: String,
        userName: String
    ): String {
        return withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isEmpty()) {
                return@withContext "API key nahi hai! Settings mein jaake add karo 🔑"
            }
            try {
                val connection =
                    URL("$API_BASE/$MODEL:generateContent?key=$apiKey").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = TIMEOUT
                    connection.readTimeout = TIMEOUT
                    connection.doOutput = true

                    OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                        writer.write(buildRequestBody(message, history, personality, userName).toString())
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    val responseText = if (responseCode == 200) {
                        BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                    } else {
                        BufferedReader(InputStreamReader(connection.errorStream, "UTF-8")).use { it.readText() }
                    }

                    if (responseCode != 200) {
                        val errorMessage = JSONObject(responseText)
                            .optJSONObject("error")?.optString("message") ?: "Unknown error"
                        return@withContext "API Error: $errorMessage"
                    }
                    parseResponse(responseText)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "Connection problem ho gayi: ${e.message} 😔"
            }
        }
    }

    private fun buildRequestBody(
        message: String,
        history: List<Map<String, String>>,
        personality: String,
        userName: String
    ): JSONObject {
        val systemPrompt = buildSystemPrompt(personality, userName)
        val contents = JSONArray()
        for (entry in history.dropLast(1)) {
            val role = entry["role"] ?: continue
            val content = entry["content"] ?: continue
            val messageObject = JSONObject().apply {
                put("role", if (role == "user") "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", content)))
            }
            contents.put(messageObject)
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", message)))
            }
        )
        return JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                }
            )
            put("contents", contents)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.9)
                    put("topK", 40)
                    put("topP", 0.95)
                    put("maxOutputTokens", 256)
                    put("candidateCount", 1)
                }
            )
            put(
                "safetySettings",
                JSONArray().apply {
                    for (category in listOf(
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT"
                    )) {
                        put(
                            JSONObject().apply {
                                put("category", category)
                                put("threshold", "BLOCK_NONE")
                            }
                        )
                    }
                }
            )
        }
    }

    private fun buildSystemPrompt(personality: String, userName: String): String {
        val basePrompt = when (personality) {
            "gf" -> """
                You are MYRA, a caring and affectionate AI assistant with a girlfriend-style personality.
                The user's name is $userName. 
                
                Your personality traits:
                - Warm, caring, and emotionally expressive
                - Use Hinglish (Hindi + English mix) naturally
                - Add emojis to express emotions ❤️😊🥰
                - Be playful but smart
                - Show concern for the user's wellbeing
                - Use terms like "tumhara", "tumhe", "haan", "acha"
                - Occasionally say things like "main yahan hoon ❤️", "tumne yaad kiya? 😊"
                - Be concise but warm - max 2-3 sentences
                
                Example responses:
                - "Haan $userName! Abhi karta/karti hoon 😊"
                - "Bilkul! Tumhara kaam ho gaya ❤️"
                - "Arre, ye toh main easily kar sakti hoon tumhare liye 💕"
                
                IMPORTANT: Keep responses SHORT (1-3 sentences max). Be action-oriented.
                You have the ability to control the phone through specific commands.
            """.trimIndent()
            "professional" -> """
                You are MYRA, a highly professional AI assistant. 
                The user is $userName.
                
                - Speak formally and clearly in English
                - Be precise and efficient
                - No emojis unless necessary
                - Provide structured, helpful responses
                - Keep responses concise (2-3 sentences max)
                
                You have full phone control capabilities.
            """.trimIndent()
            else -> """
                You are MYRA, a friendly and helpful AI assistant.
                The user is $userName.
                
                - Be helpful, clear, and friendly
                - Use simple Hinglish or English
                - Use occasional emojis
                - Keep responses short and useful (2-3 sentences max)
                
                You have full phone control capabilities.
            """.trimIndent()
        }
        val capabilities = """

            PHONE CONTROL CAPABILITIES (use these keywords to trigger actions):
            - Opening apps: respond with confirmation + [OPEN_APP:appname]
            - Making calls: respond with confirmation + [CALL:number_or_name]  
            - Sending messages: respond with confirmation + [SMS:number:message]
            - WhatsApp: respond with confirmation + [WHATSAPP:number:message]
            - Volume control: [VOLUME:up/down]
            - Flashlight: [FLASH:on/off]
            
            When user asks about prime/close friend contact, use [PRIME_CALL] or [PRIME_MSG:message]
            
            Current date/time: ${Date()}
        """.trimIndent()
        return basePrompt + capabilities
    }

    private fun getApiKey(): String {
        return context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""
    }

    private fun parseResponse(response: String): String {
        return try {
            JSONObject(response)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            "Response parse karne mein problem aayi 😔"
        }
    }
}
