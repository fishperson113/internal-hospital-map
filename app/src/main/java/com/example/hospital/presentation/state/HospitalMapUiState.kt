package com.example.hospital.presentation.state

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.presentation.util.AppLanguage

/** Which shafts the router may use to change floors. [roomKind] matches Room.kind in the building JSON. */
enum class TransferPreference(val roomKind: String?) {
    FASTEST(null),
    ELEVATOR_ONLY("elevator"),
    STAIRS_ONLY("stair")
}

/**
 * One row of the directions list / one screen of turn-by-turn navigation.
 * [focusFloorLevel] = floor the map should show for this step, always a 2D floor
 * (a floor change shows the floor the user stands on, with the elevator/stairs). Never the 3D stack.
 */
data class RouteStepUi(
    val iconType: StepIconType,
    val title: String,
    val subtitle: String,
    val chipLabel: String,
    val focusFloorLevel: Int,
    val distanceMeters: Float,
    val isTransfer: Boolean
)

data class HospitalMapUiState(
    val isLoading: Boolean = true,
    val hospital: Hospital? = null,
    val floors: List<Floor> = emptyList(),
    // null = "Tất cả" (isometric 3D stack of all floors); otherwise the level shown as a 2D plan
    val selectedFloorLevel: Int? = null,

    // Route
    val startRoom: Room? = null,
    val destRoom: Room? = null,
    val currentRoute: HospitalRoute? = null,
    val routeSteps: List<RouteStepUi> = emptyList(),
    val isRouteCalculating: Boolean = false,
    val routeNotFound: Boolean = false,
    val transferPreference: TransferPreference = TransferPreference.FASTEST,
    val isRouteDetailsExpanded: Boolean = false,

    // Room tapped on the map (room detail bottom sheet)
    val selectedRoom: Room? = null,

    // Search (full-screen overlay)
    val searchQuery: String = "",
    val searchResults: List<Room> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSelectingStartRoom: Boolean = false,

    // Turn-by-turn navigation
    val isNavigating: Boolean = false,
    val navStepIndex: Int = 0,

    // Real-time sensor positioning & orientation
    val userPosition: com.example.hospital.domain.model.Point2D? = null,
    val userFloorLevel: Int? = null,
    val userPlanHeading: Float? = null,       // facing direction on the plan (HeadingFusion or RoNIN, north = positioning.plan_north_deg); null = no arrow
    val stepCount: Int = 0,
    // Directions camera (web D29, FOLLOW in app.js): follows the dot, heading-up by the route, tilted.
    // A drag stops following, the recenter button resumes it.
    val isFollowing: Boolean = false,
    val routeCourse: Float? = null, // plan direction of the route piece under the dot; the map turns to it
    val isSensorTrackingActive: Boolean = false,
    val sensorTelemetryText: String = "",
    val sensorDiagnostics: String = "",       // raw sensor counters, shown when the telemetry badge is tapped
    val floorChangeSuggested: Boolean = false, // barometer says the user changed floor: point at "Bước tiếp theo"
    val hasArrivedAtDestination: Boolean = false,
    val isOffRoute: Boolean = false, // walked away from the line: re-routing from the dot, tap the map to correct
    val isWalkingBack: Boolean = false, // walking back the way the user came: the dot slides back along the route
    val isRoninMode: Boolean = false,   // positioning mode: false = Baseline (steps + gyro/compass), true = RoNIN (server)

    // CRM WebSocket live assistance ("Tôi bị lạc")
    val helpStatus: String? = null,

    val language: AppLanguage = AppLanguage.VIETNAMESE
)
