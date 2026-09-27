package com.example.hospital.domain.model

enum class NodeType {
    ROOM,       // Phòng khám, phòng xét nghiệm, nhà thuốc, quầy tiếp đón
    JUNCTION,   // Ngã rẽ hành lang, cửa vào sảnh
    CONNECTOR   // Thang máy, thang bộ, thang cuốn nối giữa các tầng
}

enum class NodeStatus {
    ACTIVE,                 // Đang hoạt động bình thường
    UNDER_MAINTENANCE,      // Đang sửa chữa / bảo trì
    TEMPORARILY_CLOSED,     // Tạm đóng cửa
    RELOCATED               // Đã di dời sang vị trí/tầng khác
}

data class MapNode(
    val id: String,
    val floorLevel: Int,
    val x: Float, // Tọa độ X tương đối trên sơ đồ tầng (0..mapWidth)
    val y: Float, // Tọa độ Y tương đối trên sơ đồ tầng (0..mapHeight)
    val name: String,
    val type: NodeType,
    val roomCode: String = "",         // Ví dụ: "P.502", "P.101"
    val department: String = "",       // Ví dụ: "Khoa Ngoại", "Khoa Xét nghiệm"
    val connectorInfo: ConnectorInfo? = null, // Dành cho nút thang máy/thang bộ
    val isWheelchairAccessible: Boolean = true,
    val status: NodeStatus = NodeStatus.ACTIVE,
    val lastVerifiedAt: Long = System.currentTimeMillis(), // Thời điểm xác minh sơ đồ gần nhất
    val reportCount: Int = 0,          // Số lượng báo cáo sai lệch từ cộng đồng (chuẩn bị cho future feature)
    val notes: String = ""             // Ghi chú thêm nếu có
)
