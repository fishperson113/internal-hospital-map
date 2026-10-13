package com.example

import com.example.hospital.data.api.ChatAction
import com.example.hospital.data.api.ChatClient
import com.example.hospital.data.api.ChatStep
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** POST /api/chat replies (src/backend/chat.js) become the right map action. Robolectric for org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatClientTest {

    @Test
    fun `route reply points at the room`() {
        val m = ChatClient.parseReply(JSONObject("""{"reply":"Tầng 1","action":{"type":"route","to":{"floor":"tang-1","id":22}}}"""))
        assertEquals("Tầng 1", m.text)
        assertEquals(ChatAction.Route("tang-1", 22), m.action)
    }

    @Test
    fun `help and no action`() {
        assertEquals(ChatAction.Help, ChatClient.parseReply(JSONObject("""{"reply":"x","action":{"type":"help"}}""")).action)
        assertNull(ChatClient.parseReply(JSONObject("""{"reply":"x","action":null}""")).action)
    }

    @Test
    fun `plan reply keeps the stops in order and sources are read`() {
        val m = ChatClient.parseReply(JSONObject("""{"reply":"x","sources":[{"type":"board","title":"Nội quy"},{"type":"web","title":"t","url":"https://hoanmy.com/a","domain":"hoanmy.com"}],
            "action":{"type":"plan","simulated":true,"back":null,"readyAt":1000,"stops":[
              {"to":{"floor":"tang-1","id":3},"services":["Siêu âm"],"waiting":2,"waitMin":10,"doneAt":500},
              {"to":{"floor":"tang-2","id":7},"services":[],"waiting":0,"waitMin":0,"doneAt":900}]}}"""))
        val plan = m.action as ChatAction.Plan
        assertEquals(listOf(3, 7), plan.stops.map { it.rawId })
        assertEquals(listOf("Siêu âm"), plan.stops[0].services)
        assertNull(plan.back)
        assertEquals(0, plan.cur)
        assertEquals(listOf(null, "hoanmy.com"), m.sources.map { it.domain })
    }

    @Test
    fun `stream events come one by one and the answer last`() {
        val body = "event: step
data: {\"id\":\"classify\",\"state\":\"run\",\"title\":\"Hiểu câu hỏi\"}

" +
            "event: step
data: {\"id\":\"web-1\",\"state\":\"done\",\"title\":\"Tìm\",\"sites\":[{\"url\":\"https://a.vn\",\"domain\":\"a.vn\"}]}

" +
            "event: answer
data: {\"reply\":\"ok\",\"action\":null}

"
        val got = mutableListOf<String>()
        var step: ChatStep? = null
        ChatClient.readEvents(Buffer().writeUtf8(body)) { e, d -> got += e; if (e == "step") step = ChatClient.parseStep(d) }
        assertEquals(listOf("step", "step", "answer"), got)
        assertEquals(listOf("a.vn"), step!!.sites)
    }
}
