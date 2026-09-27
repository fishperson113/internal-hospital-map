package com.example.data.model

data class ApiEndpointInfo(
    val method: String,
    val path: String,
    val description: String,
    val samplePayload: String
)

data class ApiConfig(
    val baseUrl: String = "https://api.mapflow.internal/v1/",
    val apiKey: String = "",
    val isMockMode: Boolean = true,
    val endpoints: List<ApiEndpointInfo> = listOf(
        ApiEndpointInfo(
            method = "GET",
            path = "places/nearby?lat={lat}&lng={lng}&radius={radius}&category={cat}",
            description = "Lấy danh sách các địa điểm xung quanh toạ độ hiện tại",
            samplePayload = """[{"id":"1","name":"Nhà thờ Đức Bà","lat":10.7797,"lng":106.6990,"category":"attraction"}]"""
        ),
        ApiEndpointInfo(
            method = "GET",
            path = "places/search?query={q}&lat={lat}&lng={lng}",
            description = "Tìm kiếm địa điểm theo từ khóa và bộ lọc",
            samplePayload = """[{"id":"2","name":"Chợ Bến Thành","lat":10.7725,"lng":106.6980,"category":"attraction"}]"""
        ),
        ApiEndpointInfo(
            method = "GET",
            path = "directions?origin={lat,lng}&destination={lat,lng}&mode=driving",
            description = "Tính toán lộ trình di chuyển, khoảng cách và thời gian dự kiến",
            samplePayload = """{"distance_km":3.4,"duration_min":12,"points":[[10.7769,106.7009],[10.7797,106.6990]]}"""
        ),
        ApiEndpointInfo(
            method = "GET",
            path = "geocode/reverse?lat={lat}&lng={lng}",
            description = "Chuyển đổi toạ độ địa lý thành địa chỉ cụ thể",
            samplePayload = """{"address":"01 Công Xã Paris, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"}"""
        )
    )
)
