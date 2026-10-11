package com.example.hospital.presentation.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.hospital.presentation.state.RouteStepUi
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.hospital.sensor.IndoorPdrState
import com.example.hospital.sensor.wrap
import com.example.ui.theme.MapBluePrimary
import kotlin.math.roundToInt

/**
 * Camera arrow directions (web: src/web/ar.js, spec N6, decision D44). The rear camera fills the screen and
 * one arrow lies on the floor pointing the way: route line direction under the dot minus where the phone
 * points (the map cone). No ARCore and no new data, so it is only as good as the compass indoors (15 to 30
 * degrees): it shows the way, not a door. At a shaft (no line) only the card shows.
 */
@Composable
fun ArOverlay(
    step: RouteStepUi?,
    pdr: IndoorPdrState?,
    routeLine: Float?,
    facing: Float?,
    offRoute: Boolean,
    hasCamera: Boolean, // false: permission denied or no camera, the arrow still works on a dark background
    language: AppLanguage,
    onClose: () -> Unit
) {
    val l = HospitalLocalization
    var title = step?.title.orEmpty()
    var sub = step?.subtitle.orEmpty()
    var icon = step?.iconType ?: StepIconType.WALK
    var rel: Float? = null
    when {
        pdr?.hasArrived == true -> {
            title = l.t(language, "Bạn đã đến nơi", "You have arrived"); sub = ""; icon = StepIconType.DESTINATION
        }
        offRoute -> {
            title = l.t(language, "Bạn đang đi khác tuyến", "You left the route")
            sub = l.t(language, "Đang tìm đường mới…", "Finding a new route…"); icon = StepIconType.WALK
        }
        routeLine != null && facing != null -> {
            rel = wrap(routeLine - facing)
            val toTurn = pdr?.metersToTurn
            val toEnd = pdr?.metersToEnd
            if (toTurn != null) {
                val m = toTurn.roundToInt()
                val right = (pdr?.nextTurnDeg ?: 0f) > 0f
                sub = if (m <= 2) l.t(language, if (right) "Rẽ phải" else "Rẽ trái", if (right) "Turn right" else "Turn left")
                else l.t(language, "$m m nữa rẽ ${if (right) "phải" else "trái"}", "Turn ${if (right) "right" else "left"} in $m m")
            } else if (toEnd != null) sub = l.t(language, "Còn ${toEnd.roundToInt()} m", "${toEnd.roundToInt()} m to go")
        }
    }

    // pointerInput: touches stop here, the map under the camera does not move
    Box(Modifier.fillMaxSize().background(Color(0xFF202124)).pointerInput(Unit) {}.testTag("ar_overlay")) {
        if (hasCamera) CameraPreview(Modifier.fillMaxSize())

        NavigationBanner(
            step = RouteStepUi(icon, title, sub, "", 0, 0f, false),
            modifier = Modifier.statusBarsPadding().padding(16.dp)
        )

        // keep the angle continuous so the animation turns the short way round, never through 360
        val target = remember { FloatArray(1) }
        if (rel != null) {
            target[0] += wrap(rel - target[0])
            val angle by animateFloatAsState(target[0], tween(300), label = "ar_arrow")
            Box(
                Modifier
                    .align(BiasAlignment(0f, 0.16f)) // web: top 58%
                    .graphicsLayer { rotationX = 55f; cameraDistance = 12f * density } // lies on the floor
                    .graphicsLayer { rotationZ = angle }
            ) {
                Icon(
                    Icons.Default.Navigation, contentDescription = null, tint = MapBluePrimary,
                    modifier = Modifier.size(180.dp)
                )
            }
        }

        Text(
            if (hasCamera) l.t(language, "Giơ máy thẳng trước mặt. Chú ý xung quanh khi đi.", "Hold the phone upright. Watch where you walk.")
            else l.t(language, "Không mở được camera. Mũi tên vẫn chỉ đường.", "Camera unavailable. The arrow still shows the way."),
            style = TextStyle(color = Color.White, fontSize = 14.sp, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f)),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, end = 88.dp, bottom = 30.dp)
        )
        FloatingActionButton(
            onClick = onClose,
            shape = CircleShape,
            containerColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).testTag("btn_ar_close")
        ) {
            Icon(Icons.Default.Close, contentDescription = l.t(language, "Đóng camera", "Close camera"))
        }
    }
}

/** Rear camera preview bound to the screen lifecycle; released when the overlay leaves. */
@Composable
private fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            runCatching { future.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { future.addListener({ runCatching { future.get().unbindAll() } }, ContextCompat.getMainExecutor(context)) }
    }
    AndroidView({ view }, modifier)
}
