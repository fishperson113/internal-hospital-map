package com.example.hospital.presentation.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.data.api.ChatAction
import com.example.hospital.data.api.ChatMessage
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapAmber
import com.example.ui.theme.MapBlueContainer
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapChipSurface
import com.example.ui.theme.MapDivider
import com.example.ui.theme.MapGreenOnContainer
import com.example.ui.theme.MapGreenSuccess
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted
import com.example.ui.theme.MapInkSubtle
import com.example.ui.theme.MapRedContainer
import com.example.ui.theme.MapRedOnContainer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val WORD_MS = 35L // replies appear word by word, like typing (web: WORD_MS in assistant.js)

/**
 * Assistant chat in the bottom sheet (web: assistant.js). The map stays visible above it, so a route answer
 * is drawn while the user reads. Voice input uses the phone's speech recognizer screen (RecognizerIntent),
 * so no microphone permission is needed here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatSheet(
    messages: List<ChatMessage>,
    busy: Boolean,
    language: AppLanguage,
    floors: List<Floor>,
    startRoom: Room?,
    destRoom: Room?,
    route: HospitalRoute?,
    isRouteCalculating: Boolean,
    helpStatus: String?,
    ticket: String,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
    onNavigate: () -> Unit,
    onRerun: (Int) -> Unit,
    onSay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    fun t(vi: String, en: String) = l.t(language, vi, en)
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    // newest bubble in view: items = welcome (only when empty) + messages + typing dots
    val items = (if (messages.isEmpty()) 1 else 0) + messages.size + (if (busy) 1 else 0)
    LaunchedEffect(items) { list.animateScrollToItem(items - 1) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onSend)
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == AppLanguage.VIETNAMESE) "vi-VN" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, t("Bạn cần đi đâu?", "Where do you need to go?"))
        try { speech.launch(intent) } catch (_: ActivityNotFoundException) { /* no recognizer on this phone: typing still works */ }
    }

    MapBottomSheetContainer(modifier = modifier.testTag("chat_sheet")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Quay lại", "Back"), tint = MapInk)
            }
            Avatar(36.dp)
            Column {
                Text(t("Trợ lý Lối Khám", "Lối Khám assistant"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MapInk)
                Text(t("Hỏi đường bằng lời hoặc gõ", "Ask the way by voice or text"), fontSize = 12.sp, color = MapInkMuted)
            }
        }

        LazyColumn(
            state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 380.dp)
        ) {
            if (messages.isEmpty()) item {
                BotRow {
                    Text(
                        t("Chào bạn, mình là trợ lý Lối Khám. Bạn cần đi đâu? Nói hoặc gõ đều được.",
                            "Hi, I am the Lối Khám assistant. Where do you need to go? Speak or type."),
                        fontSize = 15.sp, color = MapInk, lineHeight = 21.sp
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestChip(t("Chụp X-quang", "X-ray"), Icons.Default.Sensors) { onSend(t("Chỗ chụp X-quang ở đâu?", "Where is the X-ray room?")) }
                        SuggestChip(t("Nhà vệ sinh gần nhất", "Nearest toilet"), Icons.Default.Wc) { onSend(t("Nhà vệ sinh gần nhất ở đâu?", "Where is the nearest toilet?")) }
                        SuggestChip(t("Quầy thu ngân", "Cashier"), Icons.Default.Payments) { onSend(t("Quầy thu ngân ở đâu?", "Where is the cashier?")) }
                        SuggestChip(t("Tôi bị lạc", "I am lost"), Icons.Default.Emergency, danger = true) { onSend(t("Tôi bị lạc", "I am lost")) }
                    }
                }
            }
            itemsIndexed(messages) { i, m ->
                if (m.role == "user") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Surface(color = MapBluePrimary, shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), modifier = Modifier.widthIn(max = 280.dp)) {
                            Text(m.text, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                } else BotRow {
                    TypedText(m)
                    when (val a = m.action) {
                        is ChatAction.Route -> RouteCard(a, i, floors, startRoom, destRoom, route, isRouteCalculating, language, onNavigate, onRerun, { onSay(m.text) })
                        ChatAction.Help -> HelpCard(helpStatus, ticket, startRoom, language)
                        null -> Unit
                    }
                }
            }
            if (busy) item { BotRow { TypingDots() } }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MapInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(input); input = "" }),
                modifier = Modifier.weight(1f).height(48.dp).testTag("chat_input"),
                decorationBox = { field ->
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFFF8F9FA), RoundedCornerShape(24.dp))
                            .border(1.dp, MapDivider, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) Text(t("Hỏi đường, ví dụ: phòng X-quang", "Ask the way, e.g. X-ray room"), fontSize = 15.sp, color = MapInkSubtle, maxLines = 1)
                        field()
                    }
                }
            )
            RoundButton(Icons.Default.Mic, t("Nói", "Speak"), MapBluePrimary, Color.White, "btn_chat_mic") { listen() }
            RoundButton(Icons.Default.ArrowUpward, t("Gửi", "Send"), MapBlueContainer, MapBluePrimary, "btn_chat_send") { onSend(input); input = "" }
        }
    }
}

@Composable
private fun Avatar(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(MapBluePrimary, RoundedCornerShape(size * 0.35f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Assistant, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
private fun BotRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Avatar(30.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) { content() }
    }
}

/** Words appear one by one; an old message (scrolled back into view) shows at once. */
@Composable
private fun TypedText(m: ChatMessage) {
    val words = remember(m) { m.text.split(' ') }
    var n by remember(m) {
        mutableIntStateOf(if (System.currentTimeMillis() - m.atMs > words.size * WORD_MS + 400) words.size else 0)
    }
    LaunchedEffect(m) { while (n < words.size) { delay(WORD_MS); n++ } }
    Text(words.take(n).joinToString(" "), fontSize = 15.sp, color = MapInk, lineHeight = 21.sp)
}

@Composable
private fun TypingDots() {
    val tr = rememberInfiniteTransition(label = "typing")
    Surface(color = MapChipSurface, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val y by tr.animateFloat(0f, -4f, infiniteRepeatable(tween(400, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
                Box(Modifier.offset(y = y.dp).size(7.dp).background(MapInkSubtle, CircleShape))
            }
        }
    }
}

@Composable
private fun SuggestChip(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (danger) MapRedContainer else Color.White,
        border = if (danger) null else BorderStroke(1.dp, MapDivider),
        modifier = Modifier.height(34.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = if (danger) MapRedOnContainer else MapBluePrimary, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (danger) MapRedOnContainer else MapInk)
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: Color, fg: Color, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = bg, modifier = Modifier.size(48.dp).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(24.dp)) }
    }
}

/** Room, floor, minutes / metres / floors of the drawn route, "Take me there" (web: chatCardHtml route). */
@Composable
private fun RouteCard(
    a: ChatAction.Route, index: Int, floors: List<Floor>, startRoom: Room?, destRoom: Room?, route: HospitalRoute?,
    calculating: Boolean, language: AppLanguage, onNavigate: () -> Unit, onRerun: (Int) -> Unit, onSay: () -> Unit
) {
    val l = HospitalLocalization
    fun t(vi: String, en: String) = l.t(language, vi, en)
    val room = floors.firstOrNull { it.floorId == a.floorId }?.rooms?.firstOrNull { it.rawId == a.rawId } ?: return
    val current = destRoom?.id == room.id
    val r = route.takeIf { current && !calculating }
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, MapDivider), shadowElevation = 2.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(MapBlueContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = MapBluePrimary)
                }
                Column {
                    Text(l.roomLabel(room, language), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MapInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(l.getFloorLabel(room.floorLevel, language), fontSize = 13.sp, color = MapInkMuted)
                }
            }
            if (r != null) {
                val levels = room.floorLevel - (r.segments.firstOrNull()?.floorLevel ?: startRoom?.floorLevel ?: room.floorLevel)
                val via = r.transfer?.let { if (it.kind.startsWith("stair")) t("thang bộ", "stairs") else t("thang máy", "elevator") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("${r.estimatedDurationMinutes} ${t("phút", "min")}", t("đi bộ", "walk"), MapGreenOnContainer, Modifier.weight(1f))
                    Stat("${r.totalDistanceMeters.roundToInt()} m", t("quãng đường", "distance"), MapInk, Modifier.weight(1f))
                    Stat(
                        if (levels == 0) t("Cùng tầng", "Same floor") else "${if (levels > 0) "+" else ""}$levels ${t("tầng", "floor")}",
                        via ?: t("không đổi tầng", "no floor change"), MapInk, Modifier.weight(1f)
                    )
                }
            } else if (current && calculating) {
                Text(t("Đang vẽ đường…", "Drawing the route…"), fontSize = 14.sp, color = MapInkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r != null) Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(containerColor = MapBluePrimary),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f).height(44.dp).testTag("btn_chat_navigate")
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  " + t("Dẫn tôi đi", "Take me there"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                } else if (!current) OutlinedButton(
                    onClick = { onRerun(index) },
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, tint = MapBluePrimary, modifier = Modifier.size(18.dp))
                    Text("  " + t("Chỉ đường lại", "Show route again"), fontSize = 14.sp, color = MapBluePrimary)
                } else Box(Modifier.weight(1f))
                Surface(onClick = onSay, shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, MapDivider), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.VolumeUp, contentDescription = t("Đọc lại", "Read aloud"), tint = MapBluePrimary) }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, valueColor: Color, modifier: Modifier) {
    Column(modifier.background(MapChipSurface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1)
        Text(label, fontSize = 12.sp, color = MapInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "I am lost" status, live from the CRM (web: chatCardHtml help). */
@Composable
private fun HelpCard(helpStatus: String?, ticket: String, startRoom: Room?, language: AppLanguage) {
    val l = HospitalLocalization
    fun t(vi: String, en: String) = l.t(language, vi, en)
    val h = helpStatus ?: "done"
    Surface(shape = RoundedCornerShape(18.dp), color = MapRedContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("YÊU CẦU HỖ TRỢ", "HELP REQUEST"), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MapRedOnContainer)
                    Text(
                        startRoom?.let { "${l.roomLabel(it, language)} · ${l.getFloorLabel(it.floorLevel, language)}" } ?: t("Chưa rõ vị trí", "Location unknown"),
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MapInk
                    )
                }
                if (ticket.isNotEmpty()) Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                    Text("STT $ticket", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MapRedOnContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            HelpStep(if (h == "sending") "now" else "done", Icons.Default.Send,
                if (h == "sending") t("Đang gửi tới nhân viên", "Sending to staff") else t("Đã gửi tới nhân viên", "Sent to staff"))
            HelpStep(if (h == "open" || h == "coming") "now" else if (h == "done") "done" else "", Icons.Default.DirectionsWalk,
                if (h == "open") t("Đang chờ nhân viên nhận", "Waiting for staff") else t("Nhân viên đang tới", "Staff on the way"))
            HelpStep(if (h == "done") "done" else "", Icons.Default.Handshake, t("Đã gặp nhân viên", "Staff with you"))
        }
    }
}

@Composable
private fun HelpStep(state: String, icon: ImageVector, text: String) {
    val tr = rememberInfiniteTransition(label = "help")
    val blink by tr.animateFloat(1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "blink")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (state == "done") Icons.Default.CheckCircle else icon, contentDescription = null,
            tint = when (state) { "done" -> MapGreenSuccess; "now" -> MapAmber.copy(alpha = blink); else -> MapInkSubtle },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text, fontSize = 14.sp,
            fontWeight = if (state == "now") FontWeight.Medium else FontWeight.Normal,
            color = if (state == "") MapInkMuted else MapInk
        )
    }
}
