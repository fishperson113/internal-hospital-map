package com.example.hospital.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Patient WebSocket client for wss://45-128-222-24.sslip.io/live?role=patient&id=<id>
 * Matches Section 8 specification.
 */
class LiveClient(
    context: Context? = null,
    private val onHelpStatus: (String?) -> Unit
) {
    companion object {
        private const val TAG = "LiveClient"
        private const val PREFS_NAME = "hospital_live_prefs"
        private const val KEY_SESSION_ID = "session_id"
    }

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var inMemorySessionId: String? = null
    val sessionId: String = getOrCreateSessionId()

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var isConnected = false
    private var isClosedManually = false
    private var lastSentState = ""
    private var lastSendTimeMs = 0L
    private var pendingHelp = false

    private fun getOrCreateSessionId(): String {
        var id = prefs?.getString(KEY_SESSION_ID, null) ?: inMemorySessionId
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "").take(8)
            prefs?.edit()?.putString(KEY_SESSION_ID, id)?.apply()
            inMemorySessionId = id
        }
        return id
    }

    fun connect() {
        if (isConnected || ws != null) return
        isClosedManually = false

        val url = "${HospitalApiConfig.WS_BASE}/live?role=patient&id=$sessionId"
        val request = Request.Builder().url(url).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected ($sessionId)")
                isConnected = true
                lastSentState = ""

                if (pendingHelp) {
                    webSocket.send("""{"type":"help"}""")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val m = JSONObject(text)
                    if (m.optString("type") != "help") return
                    val status = if (m.isNull("status")) null else m.getString("status")
                    if (status == null && pendingHelp) return
                    if (status != null) pendingHelp = false

                    mainHandler.post {
                        onHelpStatus(status)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse ws message: $text", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason ($code)")
                isConnected = false
                ws = null
                if (!isClosedManually) retry()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                ws = null
                if (!isClosedManually) retry()
            }
        })
    }

    private fun retry() {
        if (isClosedManually) return
        mainHandler.postDelayed({
            if (!isClosedManually && !isConnected) {
                connect()
            }
        }, 2000L)
    }

    /**
     * Send state update to server.
     * Throttles normal movement updates to max 2 per second.
     */
    fun sendState(stateJson: String, isMajorChange: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!isMajorChange && (now - lastSendTimeMs < 500L)) {
            return
        }
        if (stateJson == lastSentState) return

        if (ws?.send(stateJson) == true) {
            lastSentState = stateJson
            lastSendTimeMs = now
        } else if (!isConnected && !isClosedManually) {
            connect()
        }
    }

    /**
     * Send "Tôi bị lạc" request.
     */
    fun askHelp() {
        pendingHelp = true
        if (isConnected && ws?.send("""{"type":"help"}""") == true) {
            Log.d(TAG, "Sent help request")
        } else {
            connect()
        }
    }

    fun disconnect() {
        isClosedManually = true
        try {
            ws?.close(1000, "App closed")
        } catch (_: Exception) {}
        ws = null
        isConnected = false
    }
}
