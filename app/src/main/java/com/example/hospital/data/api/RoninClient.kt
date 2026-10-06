package com.example.hospital.data.api

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * RoNIN positioning mode: WebSocket wss://.../api/ronin (backend ronin.js, ANDROID_INTEGRATION.md 7b).
 * The app streams raw motion rows, the server runs the RoNIN network and answers one walking velocity
 * per 0.5 s: {"type":"v","t":12.5,"v":[x, y]} (m/s, in the game rotation vector frame, no true north).
 * Reconnects 2 s after a drop while the mode is on. [onVelocity] runs on the main thread.
 */
class RoninClient(private val onVelocity: (x: Float, y: Float) -> Unit) {
    private val client = OkHttpClient()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ws: WebSocket? = null
    @Volatile private var wanted = false

    @Volatile var isConnected = false
        private set

    fun connect() {
        wanted = true
        if (ws != null) return
        val request = Request.Builder().url("${HospitalApiConfig.WS_BASE}/api/ronin").build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket === ws) isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val v = runCatching { JSONObject(text).optJSONArray("v") }.getOrNull() ?: return
                if (v.length() < 2) return
                val x = v.optDouble(0).toFloat()
                val y = v.optDouble(1).toFloat()
                main.post { onVelocity(x, y) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("RoninClient", "WebSocket failure: ${t.message}")
                dropped(webSocket)
            }
        })
    }

    private fun dropped(webSocket: WebSocket) {
        if (webSocket !== ws) return // an old socket closed after a reconnect
        ws = null
        isConnected = false
        if (wanted) main.postDelayed({ if (wanted) connect() }, 2000L)
    }

    /** Sends rows [t, ax, ay, az, ra, rb, rg, alpha, beta, gamma] as "imu" messages of at most 400 rows. */
    fun send(rows: List<DoubleArray>) {
        val socket = ws ?: return
        if (!isConnected) return
        for (chunk in rows.chunked(MAX_ROWS)) {
            val sb = StringBuilder(chunk.size * 90).append("""{"type":"imu","s":[""")
            chunk.forEachIndexed { i, row ->
                if (i > 0) sb.append(',')
                sb.append('[')
                row.forEachIndexed { j, v ->
                    if (j > 0) sb.append(',')
                    sb.append((v * 1e4).roundToLong() / 1e4) // 4 decimals keep messages small
                }
                sb.append(']')
            }
            socket.send(sb.append("]}").toString())
        }
    }

    fun close() {
        wanted = false
        ws?.close(1000, null)
        ws = null
        isConnected = false
    }

    private companion object {
        const val MAX_ROWS = 400 // server limit per message (64 KB)
    }
}
