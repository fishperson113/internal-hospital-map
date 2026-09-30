package com.example.hospital.presentation.util

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.presentation.state.RouteStepUi
import kotlin.math.roundToInt

/** Turns the domain route into short, mobile-friendly steps (title + "Tầng 2 · 45 m"). */
object RouteStepFormatter {

    fun build(route: HospitalRoute, roomsById: Map<String, Room>, lang: AppLanguage): List<RouteStepUi> {
        val l = HospitalLocalization
        fun name(id: String?): String = id?.let { roomsById[it] }?.let { l.roomLabel(it, lang) } ?: ""
        fun meters(v: Float) = "${v.roundToInt().coerceAtLeast(1)} m"

        // Same-floor route (or graph fallback): a single "walk to destination" step.
        if (route.transfer == null) {
            val level = route.segments.firstOrNull()?.floorLevel ?: route.startNode.floorLevel
            val dest = name(route.steps.lastOrNull()?.destRoomId).ifBlank { route.destNode.label }
            val sub = "${l.getFloorLabel(level, lang)} · ${meters(route.totalDistanceMeters)}"
            return listOf(
                RouteStepUi(
                    iconType = StepIconType.DESTINATION,
                    title = l.t(lang, "Đi tới $dest", "Walk to $dest"),
                    subtitle = sub,
                    chipLabel = sub,
                    focusFloorLevel = level,
                    distanceMeters = route.totalDistanceMeters,
                    isTransfer = false
                )
            )
        }

        return route.steps.map { s ->
            when (s.iconType) {
                StepIconType.ELEVATOR, StepIconType.STAIRS -> {
                    val up = s.targetFloorLevel > s.floorLevel
                    val shaft = name(s.startRoomId)
                    val target = l.getFloorLabel(s.targetFloorLevel, lang)
                    RouteStepUi(
                        iconType = s.iconType,
                        title = l.t(lang, "${if (up) "Lên" else "Xuống"} $target", "${if (up) "Go up" else "Go down"} to $target"),
                        subtitle = l.t(lang, "Bằng $shaft", "Via $shaft"),
                        chipLabel = shaft,
                        focusFloorLevel = null,
                        distanceMeters = 0f,
                        isTransfer = true
                    )
                }
                else -> {
                    val sub = "${l.getFloorLabel(s.floorLevel, lang)} · ${meters(s.distanceMeters)}"
                    val target = name(s.destRoomId)
                    RouteStepUi(
                        iconType = s.iconType,
                        title = l.t(lang, "Đi tới $target", "Walk to $target"),
                        subtitle = sub,
                        chipLabel = sub,
                        focusFloorLevel = s.floorLevel,
                        distanceMeters = s.distanceMeters,
                        isTransfer = false
                    )
                }
            }
        }
    }

    /** Remaining walking time from [fromIndex], ~1.1 m/s plus 40 s per floor change. */
    fun remainingMinutes(steps: List<RouteStepUi>, fromIndex: Int): Int {
        val rest = steps.drop(fromIndex)
        val seconds = rest.sumOf { it.distanceMeters.toDouble() } / 1.1 + rest.count { it.isTransfer } * 40
        return kotlin.math.ceil(seconds / 60.0).toInt().coerceAtLeast(1)
    }
}
