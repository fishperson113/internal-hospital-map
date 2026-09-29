package com.example.hospital.data.datasource

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.FloorLabel
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import org.json.JSONObject

object BuildingJsonParser {

    fun parseFloor(jsonStr: String): Floor {
        val root = JSONObject(jsonStr)
        val floorId = root.getString("floor_id")
        val floorName = root.getString("floor_name")
        val level = root.getInt("level")
        val width = root.getDouble("width").toFloat()
        val height = root.getDouble("height").toFloat()

        val registration = root.optJSONObject("registration")
        val links = mutableListOf<String>()
        registration?.optJSONArray("links")?.let { arr ->
            for (i in 0 until arr.length()) links.add(arr.getString(i))
        }

        val roomsArray = root.getJSONArray("rooms")
        val rooms = mutableListOf<Room>()
        for (i in 0 until roomsArray.length()) {
            val rObj = roomsArray.getJSONObject(i)
            val rawId = rObj.getInt("id")
            val name = rObj.optString("name", "")
            val nameEn = rObj.optString("name_en", "")
            val color = rObj.optString("color", "#cfe0e8")
            val textColor = rObj.optString("text_color", "#222222")
            val kind = if (rObj.has("kind")) rObj.getString("kind") else null
            val link = if (rObj.has("link")) rObj.getString("link") else null

            val polyArr = rObj.getJSONArray("polygon")
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
            if (rObj.has("label_at")) {
                val la = rObj.getJSONArray("label_at")
                labelAt = Point2D(la.getDouble(0).toFloat(), la.getDouble(1).toFloat())
            }

            val category = when {
                kind == "stair" -> RoomCategory.STAIRS
                kind == "elevator" -> RoomCategory.ELEVATOR
                name.contains("VỆ SINH", ignoreCase = true) -> RoomCategory.RESTROOM
                name.contains("QUẦY", ignoreCase = true) -> RoomCategory.RECEPTION
                name.contains("KHÁM", ignoreCase = true) -> RoomCategory.CLINIC
                name.contains("XÉT NGHIỆM", ignoreCase = true) || name.contains("SIÊU ÂM", ignoreCase = true) -> RoomCategory.DIAGNOSTIC
                name.contains("THUỐC", ignoreCase = true) -> RoomCategory.PHARMACY
                name.contains("CẤP CỨU", ignoreCase = true) || name.contains("MỔ", ignoreCase = true) || name.contains("HỒI SỨC", ignoreCase = true) -> RoomCategory.EMERGENCY
                else -> RoomCategory.FACILITY
            }

            val roomId = "${floorId}_$rawId"
            rooms.add(
                Room(
                    id = roomId,
                    code = "P$rawId",
                    name = name.ifEmpty { if (nameEn.isNotEmpty()) nameEn else "Phòng $rawId" },
                    floorLevel = level,
                    category = category,
                    description = if (nameEn.isNotEmpty()) nameEn else "$floorName - Phòng $rawId",
                    nodeId = "node_$roomId",
                    boundsLeft = minX,
                    boundsTop = minY,
                    boundsWidth = maxX - minX,
                    boundsHeight = maxY - minY,
                    nameEn = nameEn,
                    colorHex = color,
                    textColorHex = textColor,
                    kind = kind,
                    link = link,
                    polygon = poly,
                    labelAt = labelAt
                )
            )
        }

        val labels = mutableListOf<FloorLabel>()
        root.optJSONArray("labels")?.let { lArr ->
            for (i in 0 until lArr.length()) {
                val lObj = lArr.getJSONObject(i)
                val at = lObj.getJSONArray("at")
                labels.add(
                    FloorLabel(
                        name = lObj.getString("name"),
                        nameEn = lObj.optString("name_en", ""),
                        x = at.getDouble(0).toFloat(),
                        y = at.getDouble(1).toFloat(),
                        w = lObj.optDouble("w", 120.0).toFloat(),
                        h = lObj.optDouble("h", 30.0).toFloat()
                    )
                )
            }
        }

        return Floor(
            level = level,
            name = floorName,
            description = "$floorName · ${rooms.size} phòng",
            rooms = rooms,
            floorId = floorId,
            width = width,
            height = height,
            isTodo = false,
            labels = labels,
            links = links
        )
    }
}
