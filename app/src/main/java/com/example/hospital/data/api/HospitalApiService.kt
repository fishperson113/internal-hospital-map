package com.example.hospital.data.api

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.FloorLabel
import com.example.hospital.domain.model.Graph
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.HospitalMapData
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.model.RouteAssumptions
import com.example.hospital.domain.model.RouteFetchResult
import com.example.hospital.domain.model.RouteSegment
import com.example.hospital.domain.model.RouteStep
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.domain.model.TransferInfo
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class HospitalApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Calls GET /api/floors
     */
    suspend fun fetchFloors(): Result<HospitalMapData> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${HospitalApiConfig.API_BASE}/api/floors")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP error code: ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                val mapData = parseFloorsJson(bodyStr)
                Result.success(mapData)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calls GET /api/route?from=<from>&to=<to>&pref=<pref>
     */
    suspend fun fetchRoute(
        fromParam: String,
        toParam: String,
        pref: String = "any",
        floors: List<Floor>,
        destNameOverride: String? = null,
        language: AppLanguage = AppLanguage.VIETNAMESE
    ): RouteFetchResult = withContext(Dispatchers.IO) {
        try {
            val url = "${HospitalApiConfig.API_BASE}/api/route".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("from", fromParam)
                ?.addQueryParameter("to", toParam)
                ?.addQueryParameter("pref", pref)
                ?.build()
                ?: return@withContext RouteFetchResult.Error("bad-request", "Invalid URL")

            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    if (response.code == 400) {
                        val errMsg = try {
                            JSONObject(bodyStr).optString("message", "Bad request")
                        } catch (_: Exception) {
                            "Bad request"
                        }
                        return@withContext RouteFetchResult.Error("bad-request", errMsg)
                    }
                    return@withContext RouteFetchResult.Error("network", "HTTP ${response.code}")
                }

                val root = JSONObject(bodyStr)
                if (root.has("error")) {
                    val err = root.getString("error")
                    return@withContext RouteFetchResult.Error(err)
                }

                val route = parseRouteJson(root, floors, destNameOverride, language)
                RouteFetchResult.Success(route)
            }
        } catch (e: IOException) {
            RouteFetchResult.Error("network", e.message)
        } catch (e: Exception) {
            RouteFetchResult.Error("network", e.message)
        }
    }

    fun parseFloorsJson(jsonStr: String): HospitalMapData {
        val root = JSONObject(jsonStr)

        val hospObj = root.optJSONObject("hospital")
        val hospName = hospObj?.optString("name", "Bệnh viện Hoàn Mỹ Thủ Đức") ?: "Bệnh viện Hoàn Mỹ Thủ Đức"
        val hospNameEn = hospObj?.optString("name_en", "Hoan My Thu Duc Hospital") ?: "Hoan My Thu Duc Hospital"
        val hospital = Hospital(
            id = "hoan_my_thu_duc",
            name = hospName,
            address = hospNameEn
        )

        val floorsArray = root.getJSONArray("floors")
        val floors = mutableListOf<Floor>()

        for (i in 0 until floorsArray.length()) {
            val fObj = floorsArray.getJSONObject(i)
            val floorId = fObj.getString("id")
            val level = fObj.getInt("level")
            val name = fObj.getString("name")
            val nameEn = fObj.optString("name_en", "Floor $level")

            if (fObj.isNull("data")) {
                floors.add(
                    Floor(
                        level = level,
                        name = name,
                        description = nameEn,
                        rooms = emptyList(),
                        floorId = floorId,
                        width = 1795f,
                        height = 734f,
                        isTodo = true
                    )
                )
            } else {
                val dataObj = fObj.getJSONObject("data")
                val width = dataObj.getDouble("width").toFloat()
                val height = dataObj.getDouble("height").toFloat()

                val frameList = mutableListOf<Float>()
                dataObj.optJSONArray("frame")?.let { frArr ->
                    for (fIdx in 0 until frArr.length()) {
                        frameList.add(frArr.getDouble(fIdx).toFloat())
                    }
                }

                val roomsArray = dataObj.getJSONArray("rooms")
                val rooms = mutableListOf<Room>()
                for (rIdx in 0 until roomsArray.length()) {
                    val rObj = roomsArray.getJSONObject(rIdx)
                    val rawId = rIdx
                    val rName = rObj.optString("name", "")
                    val rNameEn = rObj.optString("name_en", "")
                    val color = rObj.optString("color", "#cfe0e8")
                    val textColor = rObj.optString("text_color", "#222222")
                    val kind = if (rObj.has("kind") && !rObj.isNull("kind")) rObj.getString("kind") else null
                    val link = if (rObj.has("link") && !rObj.isNull("link")) rObj.getString("link") else null

                    val polyArr = rObj.optJSONArray("polygon") ?: org.json.JSONArray()
                    val poly = mutableListOf<Point2D>()
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (pIdx in 0 until polyArr.length()) {
                        val pt = polyArr.getJSONArray(pIdx)
                        val px = pt.getDouble(0).toFloat()
                        val py = pt.getDouble(1).toFloat()
                        poly.add(Point2D(px, py))
                        if (px < minX) minX = px
                        if (py < minY) minY = py
                        if (px > maxX) maxX = px
                        if (py > maxY) maxY = py
                    }

                    var labelAt: Point2D? = null
                    if (rObj.has("label_at") && !rObj.isNull("label_at")) {
                        val la = rObj.getJSONArray("label_at")
                        labelAt = Point2D(la.getDouble(0).toFloat(), la.getDouble(1).toFloat())
                    }

                    val category = when {
                        kind == "stair" -> RoomCategory.STAIRS
                        kind == "elevator" -> RoomCategory.ELEVATOR
                        rName.contains("VỆ SINH", ignoreCase = true) -> RoomCategory.RESTROOM
                        rName.contains("QUẦY", ignoreCase = true) -> RoomCategory.RECEPTION
                        rName.contains("KHÁM", ignoreCase = true) -> RoomCategory.CLINIC
                        rName.contains("XÉT NGHIỆM", ignoreCase = true) || rName.contains("SIÊU ÂM", ignoreCase = true) -> RoomCategory.DIAGNOSTIC
                        rName.contains("THUỐC", ignoreCase = true) -> RoomCategory.PHARMACY
                        rName.contains("CẤP CỨU", ignoreCase = true) || rName.contains("MỔ", ignoreCase = true) || rName.contains("HỒI SỨC", ignoreCase = true) -> RoomCategory.EMERGENCY
                        else -> RoomCategory.FACILITY
                    }

                    val roomId = "$floorId:$rawId"
                    rooms.add(
                        Room(
                            id = roomId,
                            code = "P$rawId",
                            name = rName,
                            floorLevel = level,
                            category = category,
                            description = if (rNameEn.isNotEmpty()) rNameEn else "$name - P$rawId",
                            nodeId = "node_$roomId",
                            boundsLeft = if (minX == Float.MAX_VALUE) 0f else minX,
                            boundsTop = if (minY == Float.MAX_VALUE) 0f else minY,
                            boundsWidth = if (maxX == -Float.MAX_VALUE) 0f else (maxX - minX),
                            boundsHeight = if (maxY == -Float.MAX_VALUE) 0f else (maxY - minY),
                            nameEn = rNameEn,
                            colorHex = color,
                            textColorHex = textColor,
                            kind = kind,
                            link = link,
                            polygon = poly,
                            labelAt = labelAt,
                            rawId = rawId,
                            floorId = floorId
                        )
                    )
                }

                val labels = mutableListOf<FloorLabel>()
                dataObj.optJSONArray("labels")?.let { lArr ->
                    for (lIdx in 0 until lArr.length()) {
                        val lObj = lArr.getJSONObject(lIdx)
                        val lName = lObj.optString("name", "")
                        val lNameEn = lObj.optString("name_en", "")
                        val at = lObj.getJSONArray("at")
                        val w = lObj.optDouble("w", 100.0).toFloat()
                        val h = lObj.optDouble("h", 40.0).toFloat()
                        labels.add(
                            FloorLabel(
                                name = lName,
                                nameEn = lNameEn,
                                x = at.getDouble(0).toFloat(),
                                y = at.getDouble(1).toFloat(),
                                w = w,
                                h = h
                            )
                        )
                    }
                }

                floors.add(
                    Floor(
                        level = level,
                        name = name,
                        description = nameEn,
                        rooms = rooms,
                        floorId = floorId,
                        width = width,
                        height = height,
                        isTodo = false,
                        labels = labels,
                        frame = frameList
                    )
                )
            }
        }

        floors.sortBy { it.level }
        return HospitalMapData(hospital, floors, Graph(emptyMap(), emptyList()))
    }

    private fun parseRouteJson(
        root: JSONObject,
        floors: List<Floor>,
        destNameOverride: String?,
        language: AppLanguage
    ): HospitalRoute {
        val l = HospitalLocalization
        val meters = root.optDouble("meters", 0.0).toFloat()
        val minutes = root.optInt("minutes", 1)

        val assumptionsObj = root.optJSONObject("assumptions")
        val assumptions = RouteAssumptions(
            metersPerPx = assumptionsObj?.optDouble("metersPerPx", 0.08) ?: 0.08,
            walkSpeedMps = assumptionsObj?.optDouble("walkSpeedMps", 1.1) ?: 1.1,
            floorChangeSec = assumptionsObj?.optDouble("floorChangeSec", 40.0) ?: 40.0
        )

        val segmentsArr = root.getJSONArray("segments")
        val segments = mutableListOf<RouteSegment>()
        for (i in 0 until segmentsArr.length()) {
            val segObj = segmentsArr.getJSONObject(i)
            val floorStr = segObj.getString("floor")
            val segMeters = segObj.optDouble("meters", 0.0).toFloat()
            val ptsArr = segObj.getJSONArray("pts")
            val pts = mutableListOf<Point2D>()
            for (pIdx in 0 until ptsArr.length()) {
                val pt = ptsArr.getJSONArray(pIdx)
                pts.add(Point2D(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat()))
            }
            val floorLevel = floors.find { it.floorId == floorStr }?.level
                ?: floorStr.substringAfter("tang-", "2").toIntOrNull() ?: 2
            segments.add(RouteSegment(floorLevel, pts, segMeters, floorStr))
        }

        var transfer: TransferInfo? = null
        if (!root.isNull("transfer")) {
            val transObj = root.getJSONObject("transfer")
            val link = transObj.getString("link")
            val kind = transObj.getString("kind")
            val aObj = transObj.getJSONObject("a")
            val bObj = transObj.getJSONObject("b")

            val aFloorStr = aObj.getString("floor")
            val aId = aObj.getInt("id")
            val bFloorStr = bObj.getString("floor")
            val bId = bObj.getInt("id")

            val floorA = floors.find { it.floorId == aFloorStr }
            val floorB = floors.find { it.floorId == bFloorStr }

            val roomA = floorA?.rooms?.getOrNull(aId) ?: Room(
                id = "$aFloorStr:$aId", code = "P$aId", name = if (kind == "elevator") "Thang máy" else "Thang bộ",
                floorLevel = floorA?.level ?: 2, category = if (kind == "elevator") RoomCategory.ELEVATOR else RoomCategory.STAIRS,
                nodeId = "node_${aFloorStr}_$aId", kind = kind, link = link, rawId = aId, floorId = aFloorStr
            )
            val roomB = floorB?.rooms?.getOrNull(bId) ?: Room(
                id = "$bFloorStr:$bId", code = "P$bId", name = if (kind == "elevator") "Thang máy" else "Thang bộ",
                floorLevel = floorB?.level ?: 3, category = if (kind == "elevator") RoomCategory.ELEVATOR else RoomCategory.STAIRS,
                nodeId = "node_${bFloorStr}_$bId", kind = kind, link = link, rawId = bId, floorId = bFloorStr
            )

            transfer = TransferInfo(link, kind, roomA, roomB)
        }

        val startFloorLevel = segments.firstOrNull()?.floorLevel ?: 2
        val destFloorLevel = segments.lastOrNull()?.floorLevel ?: startFloorLevel
        val isCrossFloor = transfer != null

        // Build navigation steps as defined in Section 6
        val steps = mutableListOf<RouteStep>()
        val startFloor = floors.find { it.level == startFloorLevel } ?: Floor(startFloorLevel, "Tầng $startFloorLevel", "", emptyList())
        val destFloor = floors.find { it.level == destFloorLevel } ?: Floor(destFloorLevel, "Tầng $destFloorLevel", "", emptyList())

        if (transfer == null) {
            val dist = segments.firstOrNull()?.length ?: meters
            val dName = destNameOverride ?: l.t(language, "Điểm đến", "Destination")
            steps.add(
                RouteStep(
                    stepNumber = 1,
                    floorLevel = startFloorLevel,
                    targetFloorLevel = startFloorLevel,
                    instruction = l.t(language, "Đi tới $dName", "Walk to $dName"),
                    distanceMeters = dist,
                    estimatedDurationSeconds = (dist / assumptions.walkSpeedMps).roundToInt(),
                    isCrossFloorStep = false,
                    iconType = StepIconType.DESTINATION
                )
            )
        } else {
            val shaftName = l.roomLabel(transfer.roomA, language)
            val seg1Meters = segments.getOrNull(0)?.length ?: 0f
            val seg2Meters = segments.getOrNull(1)?.length ?: 0f
            val isUp = destFloor.level > startFloor.level

            // Step 1: Walk to transfer shaft
            steps.add(
                RouteStep(
                    stepNumber = 1,
                    floorLevel = startFloorLevel,
                    targetFloorLevel = startFloorLevel,
                    instruction = l.t(language, "Đi tới $shaftName", "Walk to $shaftName"),
                    distanceMeters = seg1Meters,
                    estimatedDurationSeconds = (seg1Meters / assumptions.walkSpeedMps).roundToInt(),
                    isCrossFloorStep = false,
                    iconType = if (transfer.kind == "elevator") StepIconType.ELEVATOR else StepIconType.STAIRS
                )
            )

            // Step 2: Change floor via shaft (focusFloorLevel = null -> 3D view)
            steps.add(
                RouteStep(
                    stepNumber = 2,
                    floorLevel = startFloorLevel,
                    targetFloorLevel = destFloorLevel,
                    instruction = if (isUp) l.t(language, "Lên ${destFloor.name}", "Go up to ${destFloor.description.ifBlank { "Floor ${destFloor.level}" }}")
                    else l.t(language, "Xuống ${destFloor.name}", "Go down to ${destFloor.description.ifBlank { "Floor ${destFloor.level}" }}"),
                    distanceMeters = 0f,
                    estimatedDurationSeconds = assumptions.floorChangeSec.roundToInt(),
                    isCrossFloorStep = true,
                    iconType = if (transfer.kind == "elevator") StepIconType.ELEVATOR else StepIconType.STAIRS
                )
            )

            // Step 3: Walk from shaft to destination
            val dName = destNameOverride ?: l.t(language, "Điểm đến", "Destination")
            steps.add(
                RouteStep(
                    stepNumber = 3,
                    floorLevel = destFloorLevel,
                    targetFloorLevel = destFloorLevel,
                    instruction = l.t(language, "Đi tới $dName", "Walk to $dName"),
                    distanceMeters = seg2Meters,
                    estimatedDurationSeconds = (seg2Meters / assumptions.walkSpeedMps).roundToInt(),
                    isCrossFloorStep = false,
                    iconType = StepIconType.DESTINATION
                )
            )
        }

        val startPt = segments.firstOrNull()?.points?.firstOrNull() ?: Point2D(0f, 0f)
        val destPt = segments.lastOrNull()?.points?.lastOrNull() ?: Point2D(0f, 0f)

        val startNode = MapNode("start", startFloorLevel, startPt.x, startPt.y, "Start")
        val destNode = MapNode("dest", destFloorLevel, destPt.x, destPt.y, "Destination")

        return HospitalRoute(
            startNode = startNode,
            destNode = destNode,
            isCrossFloor = isCrossFloor,
            totalDistanceMeters = meters,
            estimatedDurationMinutes = minutes,
            steps = steps,
            pathNodeIds = emptyList(),
            segments = segments,
            transfer = transfer,
            assumptions = assumptions
        )
    }
}
