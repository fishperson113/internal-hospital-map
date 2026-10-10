package com.example

import com.example.hospital.data.api.ChatAction
import com.example.hospital.data.api.ChatClient
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
}
