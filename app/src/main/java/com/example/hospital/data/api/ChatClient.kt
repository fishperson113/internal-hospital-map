package com.example.hospital.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What the assistant asks the app to do: draw a route to a room, or send the "I am lost" request. */
sealed interface ChatAction {
    data class Route(val floorId: String, val rawId: Int) : ChatAction
    data object Help : ChatAction
}

/** One chat bubble. [role] is "user" or "model" (the server's names). [failed]: not sent back as history. */
data class ChatMessage(
    val role: String,
    val text: String,
    val action: ChatAction? = null,
    val failed: Boolean = false,
    val atMs: Long = System.currentTimeMillis()
)

/**
 * Hospital assistant: POST /api/chat on the backend (src/backend/chat.js, Gemini), same as the web's assistant.js.
 * The server checks the room the model picks, so [ChatAction.Route] is always a real named room.
 */
class ChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    /** [from] = the room the user is at, as the server's (floor id, room index); null when unknown. */
    suspend fun ask(history: List<ChatMessage>, from: Pair<String, Int>?, lang: String): ChatMessage = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("messages", JSONArray(history.filter { !it.failed }.takeLast(10).map { JSONObject().put("role", it.role).put("text", it.text) }))
            .put("from", from?.let { JSONObject().put("floor", it.first).put("id", it.second) } ?: JSONObject.NULL)
            .put("lang", lang)
        val request = Request.Builder()
            .url("${HospitalApiConfig.API_BASE}/api/chat")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { res ->
                val json = JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
                if (!res.isSuccessful) failure(json.optString("error"), lang) else parseReply(json)
            }
        } catch (e: Exception) {
            failure("network", lang)
        }
    }

    companion object {
        /** {"reply": "...", "action": {"type":"route","to":{"floor","id"}} | {"type":"help"} | null} */
        fun parseReply(json: JSONObject): ChatMessage {
            val a = json.optJSONObject("action")
            val to = a?.optJSONObject("to")
            val action = when (a?.optString("type")) {
                "route" -> to?.let { ChatAction.Route(it.getString("floor"), it.getInt("id")) }
                "help" -> ChatAction.Help
                else -> null
            }
            return ChatMessage("model", json.optString("reply"), action)
        }

        private fun failure(error: String, lang: String): ChatMessage {
            val vi = lang == "vi"
            val text = if (error == "rate-limited")
                (if (vi) "Bạn hỏi hơi nhanh, đợi mình một chút rồi hỏi lại nhé." else "Too many questions at once, please wait a moment.")
            else (if (vi) "Mình chưa kết nối được, bạn thử lại nhé." else "I could not connect, please try again.")
            return ChatMessage("model", text, failed = true)
        }
    }
}
