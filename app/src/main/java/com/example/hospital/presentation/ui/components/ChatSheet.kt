package com.example.hospital.presentation.ui.components

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.hospital.data.api.ChatSource
import com.example.hospital.data.api.PlanStop
import com.example.hospital.data.api.SlipPhoto
import com.example.hospital.presentation.util.loadSlipPhoto
import com.example.ui.theme.MapAmberContainer
import com.example.ui.theme.MapGreenContainer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private const val MAX_PHOTOS = 4 // slip photos per message (backend chat.js MAX_IMAGES)
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
    photos: List<SlipPhoto> = emptyList(),
    onAddPhotos: (List<SlipPhoto>) -> Unit = {},
    onRemovePhoto: (Int) -> Unit = {},
    onNextStop: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    fun t(vi: String, en: String) = l.t(language, vi, en)
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    // newest bubble in view: items = welcome (only when empty) + messages; a new step of the pending answer scrolls too
    val items = (if (messages.isEmpty()) 1 else 0) + messages.size
    LaunchedEffect(items, messages.lastOrNull()?.steps?.size) { list.animateScrollToItem(items - 1) }

    // Slip photos (web: the camera button, up to 4): taken with the camera app or picked from the gallery
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = MAX_PHOTOS - photos.size
    fun add(uris: List<Uri>) = scope.launch { onAddPhotos(uris.take(room).mapNotNull { loadSlipPhoto(context, it) }) }
    var shotUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> shotUri?.takeIf { ok }?.let { add(listOf(it)) } }
    fun shoot() {
        val file = File(context.cacheDir, "slips").apply { mkdirs() }.let { File(it, "slip-${System.currentTimeMillis()}.jpg") }
        shotUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file).also {
            try { camera.launch(it) } catch (_: ActivityNotFoundException) { /* no camera app: the gallery still works */ }
        }
    }
    // the app declares CAMERA (camera arrow), so the camera app may only be opened once it is granted
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) shoot() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { add(it) }
    var photoMenu by remember { mutableStateOf(false) }
    fun takePhoto() {
        photoMenu = false
        if (room <= 0) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) shoot()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    fun pickPhotos() {
        photoMenu = false
        if (room > 0) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun send() { onSend(input); input = "" }

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
                        t("Chào bạn, mình là trợ lý Lối Khám. Hỏi đường, hỏi giờ khám, nội quy, hoặc chụp phiếu chỉ định để mình xếp lộ trình. Nói hoặc gõ đều được.",
                            "Hi, I am the Lối Khám assistant. Ask the way, opening hours, rules, or take a photo of your service slip and I will plan your visit. Speak or type."),
                        fontSize = 15.sp, color = MapInk, lineHeight = 21.sp
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestChip(t("Chụp phiếu chỉ định", "Photo of a service slip"), Icons.Default.PhotoCamera) { takePhoto() }
                        SuggestChip(t("Chụp X-quang", "X-ray"), Icons.Default.Sensors) { onSend(t("Chỗ chụp X-quang ở đâu?", "Where is the X-ray room?")) }
                        SuggestChip(t("Nhà vệ sinh gần nhất", "Nearest toilet"), Icons.Default.Wc) { onSend(t("Nhà vệ sinh gần nhất ở đâu?", "Where is the nearest toilet?")) }
                        SuggestChip(t("Giờ thăm bệnh", "Visiting hours"), Icons.Default.Schedule) { onSend(t("Giờ thăm bệnh là mấy giờ?", "What are the visiting hours?")) }
                        SuggestChip(t("Khám bảo hiểm y tế", "Health insurance"), Icons.Default.HealthAndSafety) { onSend(t("Bệnh viện có khám bảo hiểm y tế không?", "Does the hospital accept health insurance?")) }
                        SuggestChip(t("Tôi bị lạc", "I am lost"), Icons.Default.Emergency, danger = true) { onSend(t("Tôi bị lạc", "I am lost")) }
                    }
                }
            }
            itemsIndexed(messages) { i, m ->
                if (m.role == "user") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.photos.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            m.photos.forEach { PhotoThumb(it, 64.dp) }
                        }
                        Surface(color = MapBluePrimary, shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), modifier = Modifier.widthIn(max = 280.dp)) {
                            Text(m.text, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                } else BotRow {
                    if (m.steps.isNotEmpty()) ThinkSteps(m, language)
                    if (m.pending) { if (m.steps.isEmpty()) TypingDots() }
                    else {
                        TypedText(m)
                        if (m.sources.isNotEmpty()) Sources(m.sources, language)
                        when (val a = m.action) {
                            is ChatAction.Route -> RouteCard(a, i, floors, startRoom, destRoom, route, isRouteCalculating, language, onNavigate, onRerun, { onSay(m.text) })
                            is ChatAction.Plan -> PlanCard(a, i, floors, destRoom, route, isRouteCalculating, language, onNavigate, onRerun, onNextStop, { onSay(m.text) })
                            ChatAction.Help -> HelpCard(helpStatus, ticket, startRoom, language)
                            null -> Unit
                        }
                    }
                }
            }
        }

        if (photos.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEachIndexed { i, p ->
                    Box {
                        PhotoThumb(p, 56.dp)
                        Surface(onClick = { onRemovePhoto(i) }, shape = CircleShape, color = MapInk.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(22.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = t("Bỏ ảnh", "Remove photo"), tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
            Text(
                t("Ảnh chỉ dùng để đọc dịch vụ trên phiếu, không lưu lại. Nên che họ tên trước khi chụp.",
                    "Photos are only read for the services on the slip and are not kept. Cover your name if you like."),
                fontSize = 12.sp, color = MapInkMuted, lineHeight = 16.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                IconButton(onClick = { photoMenu = true }, modifier = Modifier.size(40.dp).testTag("btn_chat_photo")) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = t("Chụp phiếu chỉ định", "Photo of a service slip"), tint = MapInkMuted)
                }
                DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                    DropdownMenuItem(text = { Text(t("Chụp ảnh phiếu", "Take a photo")) }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) }, onClick = { takePhoto() })
                    DropdownMenuItem(text = { Text(t("Chọn từ thư viện", "Pick from gallery")) }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) }, onClick = { pickPhotos() })
                }
            }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MapInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f).height(48.dp).testTag("chat_input"),
                decorationBox = { field ->
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFFF8F9FA), RoundedCornerShape(24.dp))
                            .border(1.dp, MapDivider, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) Text(t("Hỏi đường, giờ khám…", "Ask the way, hours…"), fontSize = 15.sp, color = MapInkSubtle, maxLines = 1)
                        field()
                    }
                }
            )
            RoundButton(Icons.Default.Mic, t("Nói", "Speak"), MapBluePrimary, Color.White, "btn_chat_mic") { listen() }
            RoundButton(Icons.Default.ArrowUpward, t("Gửi", "Send"), MapBlueContainer, MapBluePrimary, "btn_chat_send") { send() }
        }
    }
}

@Composable
private fun PhotoThumb(p: SlipPhoto, size: androidx.compose.ui.unit.Dp) {
    val img = remember(p) { p.thumb.asImageBitmap() }
    Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).border(1.dp, MapDivider, RoundedCornerShape(10.dp)))
}

/**
 * The server's working steps (web: thinkHtml): open while it works, folded into one line when the answer
 * arrives; tapping the line opens it again. Like the "thinking" line of chat apps.
 */
@Composable
private fun ThinkSteps(m: ChatMessage, language: AppLanguage) {
    fun t(vi: String, en: String) = HospitalLocalization.t(language, vi, en)
    var toggled by remember(m.atMs) { mutableStateOf<Boolean?>(null) }
    val open = toggled ?: m.pending
    val searches = m.steps.count { it.id.startsWith("web-") }
    val head = when {
        m.pending -> (m.steps.lastOrNull { it.running } ?: m.steps.last()).title
        searches > 0 -> t("Đã tìm trên web $searches lượt", "Searched the web $searches time${if (searches > 1) "s" else ""}")
        m.steps.any { it.id == "slip" } -> t("Đã đọc phiếu và xếp lộ trình", "Read the slip and planned the visit")
        m.steps.any { it.id == "boards" } -> t("Đã đọc bảng thông tin của bệnh viện", "Read the hospital notice boards")
        else -> (m.steps.firstOrNull { it.id == "rooms" } ?: m.steps.last()).title
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { toggled = !open }.padding(vertical = 2.dp)
        ) {
            if (m.pending) CircularProgressIndicator(strokeWidth = 2.dp, color = MapBluePrimary, modifier = Modifier.size(14.dp))
            else Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MapInkMuted, modifier = Modifier.size(16.dp))
            Text(head, fontSize = 13.sp, color = MapInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MapInkSubtle, modifier = Modifier.size(18.dp))
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
            m.steps.forEach { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.running) CircularProgressIndicator(strokeWidth = 2.dp, color = MapBluePrimary, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    else Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MapGreenSuccess, modifier = Modifier.size(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.title, fontSize = 13.sp, color = MapInk)
                        s.detail?.let { Text(it, fontSize = 12.sp, color = MapInkMuted, lineHeight = 16.sp) }
                        if (s.sites.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            s.sites.forEach { SourcePill(it, Icons.Default.Public) }
                        }
                    }
                }
            }
        }
    }
}

/** Where the answer comes from (web: sourcesHtml): a notice board on the hospital wall, or web pages (tap opens). */
@Composable
private fun Sources(sources: List<ChatSource>, language: AppLanguage) {
    val uri = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(HospitalLocalization.t(language, "Nguồn", "Sources"), fontSize = 12.sp, color = MapInkMuted, modifier = Modifier.padding(end = 2.dp))
        sources.forEach { s ->
            val url = s.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (s.domain == null) SourcePill(s.title, Icons.Default.Assignment)
            else SourcePill(s.domain, Icons.Default.Public) { url?.let { runCatching { uri.openUri(it) } } }
        }
    }
}

@Composable
private fun SourcePill(label: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MapChipSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MapInkMuted, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 12.sp, color = MapInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

/**
 * Visit plan from service slip photos (web: planCardHtml): the stops in the suggested order with their simulated
 * queues, and when all results are ready. The map routes to the current stop; "Next stop" moves on.
 */
@Composable
private fun PlanCard(
    a: ChatAction.Plan, index: Int, floors: List<Floor>, destRoom: Room?, route: HospitalRoute?, calculating: Boolean,
    language: AppLanguage, onNavigate: () -> Unit, onRerun: (Int) -> Unit, onNext: (Int) -> Unit, onSay: () -> Unit
) {
    val l = HospitalLocalization
    fun t(vi: String, en: String) = l.t(language, vi, en)
    val clock = remember(language) { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    fun roomOf(s: PlanStop) = floors.firstOrNull { it.floorId == s.floorId }?.rooms?.firstOrNull { it.rawId == s.rawId }
    val cur = a.cur
    val stop = a.stops[cur]
    val routed = destRoom != null && destRoom.id == roomOf(stop)?.id && route != null && !calculating
    val n = a.stops.size
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, MapDivider), shadowElevation = 2.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(t("LỘ TRÌNH GỢI Ý", "SUGGESTED VISIT"), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MapInkMuted)
                    Text(
                        t("$n nơi · đủ kết quả ${clock.format(Date(a.readyAtMs))}", "$n stop${if (n > 1) "s" else ""} · results by ${clock.format(Date(a.readyAtMs))}"),
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MapInk
                    )
                }
                if (a.simulated) Surface(color = MapAmberContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(t("Hàng chờ giả lập", "Simulated queues"), fontSize = 11.sp, color = MapAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            a.stops.forEachIndexed { k, s ->
                val state = when { k < cur -> "done"; k == cur -> "now"; else -> "" }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(24.dp).background(if (state == "now") MapBluePrimary else if (state == "done") MapGreenContainer else MapChipSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state == "done") Icon(Icons.Default.Check, contentDescription = null, tint = MapGreenOnContainer, modifier = Modifier.size(14.dp))
                        else Text("${k + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (state == "now") Color.White else MapInk)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(roomOf(s)?.let { l.roomLabel(it, language) } ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = if (state == "done") MapInkMuted else MapInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${t("xong", "done")} ${clock.format(Date(s.doneAtMs))}", fontSize = 12.sp, color = MapInkMuted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(roomOf(s)?.let { l.getFloorLabel(it.floorLevel, language) } ?: "", fontSize = 12.sp, color = MapInkMuted)
                            val free = s.waitMin < 1
                            Surface(color = if (free) MapGreenContainer else MapChipSurface, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    if (free) t("Không phải chờ", "No wait") else t("${s.waiting} người chờ · ~${s.waitMin} phút", "${s.waiting} waiting · ~${s.waitMin} min"),
                                    fontSize = 11.sp, color = if (free) MapGreenOnContainer else MapInk, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (s.services.isNotEmpty()) Text(
                            (if (s.services.size > 1) t("${s.services.size} dịch vụ: ", "${s.services.size} services: ") else "") + s.services.joinToString(" · "),
                            fontSize = 12.sp, color = MapInkMuted, lineHeight = 16.sp
                        )
                    }
                }
            }
            a.back?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = MapInkMuted, modifier = Modifier.size(16.dp))
                    Text(t("Có đủ kết quả thì quay lại $it", "With all results, go back to $it"), fontSize = 13.sp, color = MapInk)
                }
            }
            if (routed) Button(
                onClick = onNavigate,
                colors = ButtonDefaults.buttonColors(containerColor = MapBluePrimary),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_plan_navigate")
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + t("Dẫn tôi tới điểm ${cur + 1}", "Take me to stop ${cur + 1}"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            } else OutlinedButton(
                onClick = { onRerun(index) },
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, tint = MapBluePrimary, modifier = Modifier.size(18.dp))
                Text("  " + t("Chỉ đường tới điểm ${cur + 1}", "Route to stop ${cur + 1}"), fontSize = 14.sp, color = MapBluePrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cur < a.stops.lastIndex) OutlinedButton(
                    onClick = { onNext(index) },
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f).height(44.dp).testTag("btn_plan_next")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = MapBluePrimary, modifier = Modifier.size(18.dp))
                    Text("  " + t("Sang điểm tiếp theo", "Next stop"), fontSize = 14.sp, color = MapBluePrimary)
                } else Box(Modifier.weight(1f))
                Surface(onClick = onSay, shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, MapDivider), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.VolumeUp, contentDescription = t("Đọc lại", "Read aloud"), tint = MapBluePrimary) }
                }
            }
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
