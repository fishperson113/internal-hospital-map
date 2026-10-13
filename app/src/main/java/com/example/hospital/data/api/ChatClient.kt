package com.example.hospital.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What the assistant asks the app to do: draw a route to a room, send the "I am lost" request, or a visit plan. */
sealed interface ChatAction {
    data class Route(val floorId: String, val rawId: Int) : ChatAction
    data object Help : ChatAction
    /** Visit plan from photos of service slips (backend slip.js). [cur] = the stop the map routes to now. */
    data class Plan(
        val stops: List<PlanStop>,
        val readyAtMs: Long,
        val back: String?,
        val simulated: Boolean,
        val cur: Int = 0
    ) : ChatAction
}

/** One stop of a visit plan, in the suggested order. Queue numbers are simulated on the server (queue_sim.json). */
data class PlanStop(
    val floorId: String,
    val rawId: Int,
    val services: List<String>,
    val waiting: Int,
    val waitMin: Int,
    val doneAtMs: Long
)

/** Where an answer comes from: a notice board on the hospital wall ([url] null) or a web page. */
data class ChatSource(val title: String, val url: String? = null, val domain: String? = null)

/** One working step the server reports while it prepares the answer (stream `step` event). */
data class ChatStep(
    val id: String,
    val running: Boolean,
    val title: String,
    val detail: String? = null,
    val sites: List<String> = emptyList() // domains searched
)

/**
 * One chat bubble. [role] is "user" or "model" (the server's names). [failed]: not sent back as history.
 * [pending]: the answer is being prepared, [steps] show meanwhile. [photos]: slip photos sent with a user message.
 */
data class ChatMessage(
    val role: String,
    val text: String,
    val action: ChatAction? = null,
    val failed: Boolean = false,
    val atMs: Long = System.currentTimeMillis(),
    val sources: List<ChatSource> = emptyList(),
    val steps: List<ChatStep> = emptyList(),
    val pending: Boolean = false,
    val photos: List<SlipPhoto> = emptyList()
)

/** A slip photo shrunk for sending: [dataUrl] goes to the server, [thumb] shows in the chat. */
data class SlipPhoto(val dataUrl: String, val thumb: android.graphics.Bitmap)

/**
 * Hospital assistant: POST /api/chat on the backend (src/backend/chat.js, OpenAI), same as the web's assistant.js.
 * Streamed (stream: true): the working steps arrive as server-sent events before the answer.
 * The server checks the rooms the model picks, so route and plan actions always point at real named rooms.
 */
class ChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // a web search or a slip photo takes up to ~10 s between events
        .build()
) {
    /** [from] = the room the user is at, as the server's (floor id, room index); null when unknown. */
    suspend fun ask(
        history: List<ChatMessage>,
        from: Pair<String, Int>?,
        lang: String,
        images: List<String> = emptyList(),
        onStep: (ChatStep) -> Unit = {}
    ): ChatMessage = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("messages", JSONArray(history.filter { !it.failed && !it.pending }.takeLast(10).map { JSONObject().put("role", it.role).put("text", it.text) }))
            .put("from", from?.let { JSONObject().put("floor", it.first).put("id", it.second) } ?: JSONObject.NULL)
            .put("lang", lang)
            .put("stream", true)
        if (images.isNotEmpty()) body.put("images", JSONArray(images))
        val request = Request.Builder()
            .url("${HospitalApiConfig.API_BASE}/api/chat")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { res ->
                val source = res.body?.source() ?: return@use failure("network", lang)
                // errors before the stream starts (400, 429, 503) come back as plain JSON
                if (res.header("Content-Type")?.contains("event-stream") != true) {
                    val json = JSONObject(source.readUtf8().ifBlank { "{}" })
                    return@use if (!res.isSuccessful) failure(json.optString("error"), lang) else parseReply(json)
                }
                var answer: ChatMessage? = null
                var error: String? = null
                readEvents(source) { event, data ->
                    when (event) {
                        "step" -> onStep(parseStep(data))
                        "answer" -> answer = parseReply(data)
                        "error" -> error = data.optString("error")
                    }
                }
                answer ?: failure(error ?: "no-answer", lang)
            }
        } catch (e: Exception) {
            failure("network", lang)
        }
    }

    /** POST /api/tts: the reply read aloud by the server's voice (mp3). Null when the server cannot answer. */
    suspend fun speech(text: String, lang: String): ByteArray? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("text", text.take(600)).put("lang", lang)
        val request = Request.Builder()
            .url("${HospitalApiConfig.API_BASE}/api/tts")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(request).execute().use { if (it.isSuccessful) it.body?.bytes() else null } }.getOrNull()
    }

    companion object {
        /** Server-sent events: calls [onEvent] with the `event` name and the JSON `data` of each block. */
        fun readEvents(source: BufferedSource, onEvent: (String, JSONObject) -> Unit) {
            var event: String? = null
            var data: String? = null
            while (true) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event: ") -> event = line.removePrefix("event: ")
                    line.startsWith("data: ") -> data = line.removePrefix("data: ")
                    line.isEmpty() -> {
                        val e = event; val d = data
                        if (e != null && d != null) onEvent(e, JSONObject(d))
                        event = null; data = null
                    }
                }
            }
        }

        fun parseStep(j: JSONObject): ChatStep {
            val sites = j.optJSONArray("sites")
            return ChatStep(
                id = j.optString("id"),
                running = j.optString("state") == "run",
                title = j.optString("title"),
                detail = j.optString("detail").takeIf { it.isNotBlank() },
                sites = (0 until (sites?.length() ?: 0)).mapNotNull { sites?.optJSONObject(it)?.optString("domain")?.takeIf(String::isNotBlank) }
            )
        }

        /** {"reply", "action": route | help | plan | null, "sources": [{"type":"board","title"} | {"type":"web","title","url","domain"}]} */
        fun parseReply(json: JSONObject): ChatMessage {
            val a = json.optJSONObject("action")
            val to = a?.optJSONObject("to")
            val action = when (a?.optString("type")) {
                "route" -> to?.let { ChatAction.Route(it.getString("floor"), it.getInt("id")) }
                "help" -> ChatAction.Help
                "plan" -> parsePlan(a)
                else -> null
            }
            val src = json.optJSONArray("sources")
            val sources = (0 until (src?.length() ?: 0)).mapNotNull { i ->
                val s = src!!.optJSONObject(i) ?: return@mapNotNull null
                if (s.optString("type") == "web") ChatSource(s.optString("title"), s.optString("url"), s.optString("domain"))
                else ChatSource(s.optString("title"))
            }
            return ChatMessage("model", json.optString("reply"), action, sources = sources)
        }

        private fun parsePlan(a: JSONObject): ChatAction.Plan? {
            val arr = a.optJSONArray("stops") ?: return null
            val stops = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                val to = s.getJSONObject("to")
                val svc = s.optJSONArray("services")
                PlanStop(
                    floorId = to.getString("floor"),
                    rawId = to.getInt("id"),
                    services = (0 until (svc?.length() ?: 0)).map { svc!!.getString(it) },
                    waiting = s.optInt("waiting"),
                    waitMin = s.optInt("waitMin"),
                    doneAtMs = s.optLong("doneAt")
                )
            }
            if (stops.isEmpty()) return null
            return ChatAction.Plan(stops, a.optLong("readyAt"), a.optString("back").takeIf { it.isNotBlank() && it != "null" }, a.optBoolean("simulated"))
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
