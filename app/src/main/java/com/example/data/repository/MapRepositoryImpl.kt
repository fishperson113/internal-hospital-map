package com.example.data.repository

import com.example.data.model.ApiConfig
import com.example.data.model.LatLng
import com.example.data.model.Place
import com.example.data.model.PlaceCategory
import com.example.data.model.RouteInfo
import com.example.data.model.RouteStep
import com.example.data.remote.MapApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapRepositoryImpl(
    private val apiService: MapApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MapRepository {

    private val initialPlaces = listOf(
        Place(
            id = "p1",
            name = "Nhà thờ Đức Bà Sài Gòn",
            category = PlaceCategory.ATTRACTION,
            rating = 4.8f,
            reviewCount = 14230,
            address = "01 Công xã Paris, Bến Nghé, Quận 1, TP. Hồ Chí Minh",
            latitude = 10.77978,
            longitude = 106.69902,
            description = "Công trình kiến trúc Gothic tráng lệ xây dựng từ thế kỷ 19, một trong những biểu tượng nổi bật nhất của thành phố.",
            distanceKm = 0.4,
            isOpen = true,
            openingHours = "08:00 - 17:30",
            phoneNumber = "+84 28 3822 0477",
            isSaved = true,
            tags = listOf("Di tích lịch sử", "Kiến trúc", "Điểm check-in")
        ),
        Place(
            id = "p2",
            name = "Chợ Bến Thành",
            category = PlaceCategory.ATTRACTION,
            rating = 4.6f,
            reviewCount = 28540,
            address = "Đường Lê Lợi, Phường Bến Thành, Quận 1",
            latitude = 10.77254,
            longitude = 106.69805,
            description = "Khu chợ lịch sử sầm uất với tháp đồng hồ biểu tượng, bày bán ẩm thực đường phố, đồ thủ công và thời trang đa dạng.",
            distanceKm = 0.8,
            isOpen = true,
            openingHours = "06:00 - 22:00",
            phoneNumber = "+84 28 3829 2096",
            isSaved = false,
            tags = listOf("Chợ truyền thống", "Đặc sản", "Quà lưu niệm")
        ),
        Place(
            id = "p3",
            name = "Highlands Coffee - Nhà Hát Thành Phố",
            category = PlaceCategory.CAFE,
            rating = 4.5f,
            reviewCount = 3890,
            address = "07 Công Trường Lam Sơn, Bến Nghé, Quận 1",
            latitude = 10.77665,
            longitude = 106.70321,
            description = "Thưởng thức cà phê phin đậm đà phong cách Việt Nam với góc nhìn tuyệt đẹp sang Nhà Hát Lớn thành phố.",
            distanceKm = 0.3,
            isOpen = true,
            openingHours = "07:00 - 23:00",
            phoneNumber = "+84 28 3827 5000",
            isSaved = true,
            tags = listOf("Wifi nhanh", "View đẹp", "Cà phê sữa đá")
        ),
        Place(
            id = "p4",
            name = "Phở Lệ - Nguyễn Trãi",
            category = PlaceCategory.RESTAURANT,
            rating = 4.7f,
            reviewCount = 9420,
            address = "413-415 Nguyễn Trãi, Phường 7, Quận 5",
            latitude = 10.75620,
            longitude = 106.67130,
            description = "Thương hiệu phở bò gia truyền nổi danh với nước dùng ngọt thanh từ xương hầm và thịt bò tươi mềm.",
            distanceKm = 2.1,
            isOpen = true,
            openingHours = "06:00 - 01:00",
            phoneNumber = "+84 28 3923 4008",
            isSaved = false,
            tags = listOf("Phở bò", "Ẩm thực truyền thống", "Mở khuya")
        ),
        Place(
            id = "p5",
            name = "Petrolimex Cây Xăng Số 01",
            category = PlaceCategory.GAS,
            rating = 4.3f,
            reviewCount = 1250,
            address = "136 Hai Bà Trưng, Đa Kao, Quận 1",
            latitude = 10.78520,
            longitude = 106.69780,
            description = "Trạm xăng dầu chính hãng 24/7, có dịch vụ bơm lốp xe, rửa kính và cửa hàng tiện ích mini.",
            distanceKm = 1.1,
            isOpen = true,
            openingHours = "24/24",
            phoneNumber = "+84 28 3822 5599",
            isSaved = false,
            tags = listOf("24/7", "RON 95-V", "Thanh toán QR")
        ),
        Place(
            id = "p6",
            name = "The Reverie Saigon Hotel",
            category = PlaceCategory.HOTEL,
            rating = 4.9f,
            reviewCount = 2100,
            address = "22-36 Nguyễn Huệ, Bến Nghé, Quận 1",
            latitude = 10.77380,
            longitude = 106.70450,
            description = "Khách sạn 6 sao sang trọng bậc nhất tọa lạc tại phố đi bộ Nguyễn Huệ với tầm nhìn ôm trọn sông Sài Gòn.",
            distanceKm = 0.5,
            isOpen = true,
            openingHours = "24/24",
            phoneNumber = "+84 28 3823 6688",
            isSaved = false,
            tags = listOf("Hồ bơi vô cực", "Spa cao cấp", "Sky bar")
        ),
        Place(
            id = "p7",
            name = "Phố Đi Bộ Nguyễn Huệ",
            category = PlaceCategory.ATTRACTION,
            rating = 4.7f,
            reviewCount = 31200,
            address = "Nguyễn Huệ, Bến Nghé, Quận 1",
            latitude = 10.77450,
            longitude = 106.70310,
            description = "Quảng trường đi bộ trung tâm nhộn nhịp về đêm với đài phun nước nghệ thuật, biểu diễn âm nhạc đường phố.",
            distanceKm = 0.4,
            isOpen = true,
            openingHours = "24/24",
            phoneNumber = "",
            isSaved = false,
            tags = listOf("Đi dạo", "Về đêm", "Check-in")
        ),
        Place(
            id = "p8",
            name = "The Workshop Coffee",
            category = PlaceCategory.CAFE,
            rating = 4.6f,
            reviewCount = 1870,
            address = "27 Ngô Đức Kế, Bến Nghé, Quận 1",
            latitude = 10.77290,
            longitude = 106.70580,
            description = "Không gian specialty coffee phong cách công nghiệp (loft industrial), phục vụ các phương pháp pha V60, Aeropress.",
            distanceKm = 0.6,
            isOpen = true,
            openingHours = "08:00 - 21:00",
            phoneNumber = "+84 28 3824 6801",
            isSaved = false,
            tags = listOf("Specialty Coffee", "Không gian làm việc", "Yên tĩnh")
        )
    )

    private val placesState = MutableStateFlow<List<Place>>(initialPlaces)
    private val apiConfigState = MutableStateFlow(ApiConfig())

    override fun getPlacesStream(category: PlaceCategory, searchQuery: String): Flow<List<Place>> {
        return placesState.asStateFlow().map { places ->
            places.filter { place ->
                val matchesCategory = (category == PlaceCategory.ALL || place.category == category)
                val matchesQuery = searchQuery.isBlank() ||
                        place.name.contains(searchQuery, ignoreCase = true) ||
                        place.address.contains(searchQuery, ignoreCase = true) ||
                        place.tags.any { it.contains(searchQuery, ignoreCase = true) }
                matchesCategory && matchesQuery
            }
        }
    }

    override fun getPlaceByIdStream(placeId: String): Flow<Place?> {
        return placesState.asStateFlow().map { places ->
            places.find { it.id == placeId }
        }
    }

    override fun getRouteStream(origin: LatLng, destination: Place): Flow<RouteInfo> {
        val destLatLng = LatLng(destination.latitude, destination.longitude)
        val distanceKm = calculateHaversineDistance(origin, destLatLng)
        val durationMin = maxOf(3, (distanceKm * 3.5).toInt())

        // Generate realistic waypoints along city grid
        val waypoints = generateIntermediateWaypoints(origin, destLatLng)
        val steps = listOf(
            RouteStep("Khởi hành đi về hướng Đông Bắc", 150, 60),
            RouteStep("Rẽ phải vào đường chính", (distanceKm * 400).toInt(), durationMin * 30),
            RouteStep("Đi thẳng 800m qua ngã tư", 800, 180),
            RouteStep("Điểm đến \"${destination.name}\" ở bên tay phải", 50, 20)
        )

        val route = RouteInfo(
            origin = origin,
            destination = destLatLng,
            destinationName = destination.name,
            distanceKm = String.format("%.1f", distanceKm).toDoubleOrNull() ?: distanceKm,
            durationMinutes = durationMin,
            waypoints = waypoints,
            steps = steps
        )

        return MutableStateFlow(route).asStateFlow()
    }

    override fun getSavedPlacesStream(): Flow<List<Place>> {
        return placesState.asStateFlow().map { places ->
            places.filter { it.isSaved }
        }
    }

    override suspend fun toggleSavePlace(placeId: String) = withContext(ioDispatcher) {
        placesState.update { current ->
            current.map { place ->
                if (place.id == placeId) place.copy(isSaved = !place.isSaved) else place
            }
        }
    }

    override fun getApiConfigStream(): Flow<ApiConfig> {
        return apiConfigState.asStateFlow()
    }

    override suspend fun updateApiConfig(config: ApiConfig) = withContext(ioDispatcher) {
        apiConfigState.value = config
    }

    override suspend fun refreshPlaces() = withContext(ioDispatcher) {
        val config = apiConfigState.value
        if (!config.isMockMode && config.baseUrl.isNotBlank()) {
            try {
                val response = apiService.getNearbyPlaces(10.7769, 106.7009)
                if (response.isSuccessful && response.body() != null) {
                    val remotePlaces = response.body()!!.map { it.toDomain() }
                    placesState.value = remotePlaces
                }
            } catch (e: Exception) {
                // If API fails or is not yet connected, fallback gracefully to cached mock places
            }
        }
    }

    private fun calculateHaversineDistance(p1: LatLng, p2: LatLng): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun generateIntermediateWaypoints(start: LatLng, end: LatLng): List<LatLng> {
        val points = mutableListOf<LatLng>()
        points.add(start)
        // City grid waypoint L-shaped / zig-zag path simulation
        val midLat = start.latitude + (end.latitude - start.latitude) * 0.45
        val midLng = start.longitude + (end.longitude - start.longitude) * 0.15
        points.add(LatLng(midLat, midLng))

        val midLat2 = start.latitude + (end.latitude - start.latitude) * 0.55
        val midLng2 = start.longitude + (end.longitude - start.longitude) * 0.85
        points.add(LatLng(midLat2, midLng2))

        points.add(end)
        return points
    }
}
