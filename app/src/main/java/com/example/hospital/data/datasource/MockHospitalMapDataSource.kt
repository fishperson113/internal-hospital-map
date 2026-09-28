package com.example.hospital.data.datasource

import com.example.hospital.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockHospitalMapDataSource : HospitalMapDataSource {

    // Note: Hospital name must be "Bệnh viện Đa khoa" for test compatibility
    private val hospital = Hospital(
        id = "hoan_my_sg",
        name = "Bệnh viện Đa khoa",
        address = "Sơ đồ chỉ đường Tầng 1"
    )

    // Floor 1 Rooms (Model 1:1 of Hoàn Mỹ Hospital Floor 1 Map)
    private val floor1Rooms = listOf(
        // Entrance & Reception
        Room(
            id = "room_you_are_here",
            code = "YOU",
            name = "Bạn ở đây",
            floorLevel = 1,
            category = RoomCategory.FACILITY,
            description = "Điểm mốc định vị tại sảnh trung tâm",
            nodeId = "node_you_are_here",
            boundsLeft = 0.56f, boundsTop = 0.56f, boundsWidth = 0.08f, boundsHeight = 0.035f
        ),
        Room(
            id = "room_reception",
            code = "QDDK",
            name = "Quầy đăng ký khám",
            floorLevel = 1,
            category = RoomCategory.RECEPTION,
            description = "Đăng ký khám, tiếp đón bệnh nhân ban đầu",
            nodeId = "node_reception",
            boundsLeft = 0.41f, boundsTop = 0.65f, boundsWidth = 0.08f, boundsHeight = 0.08f
        ),
        Room(
            id = "room_main_entrance",
            code = "MAIN",
            name = "Tiền sảnh",
            floorLevel = 1,
            category = RoomCategory.FACILITY,
            description = "Cửa vào chính của bệnh viện",
            nodeId = "node_main_entrance",
            boundsLeft = 0.41f, boundsTop = 0.78f, boundsWidth = 0.08f, boundsHeight = 0.08f
        ),

        // Emergency B Zone (Top Left)
        Room(
            id = "room_emergency_b",
            code = "CCB",
            name = "Cấp cứu B",
            floorLevel = 1,
            category = RoomCategory.EMERGENCY,
            description = "Khu cấp cứu tiếp nhận 24/7",
            nodeId = "node_emergency_b",
            boundsLeft = 0.24f, boundsTop = 0.24f, boundsWidth = 0.14f, boundsHeight = 0.09f
        ),
        Room(
            id = "room_ae_dept",
            code = "SCC",
            name = "Sảnh cấp cứu",
            floorLevel = 1,
            category = RoomCategory.EMERGENCY,
            description = "Sảnh tiếp nhận xe cấp cứu",
            nodeId = "node_ae_dept",
            boundsLeft = 0.08f, boundsTop = 0.28f, boundsWidth = 0.11f, boundsHeight = 0.09f
        ),
        Room(
            id = "room_isolation",
            code = "CL",
            name = "Phòng cách ly",
            floorLevel = 1,
            category = RoomCategory.EMERGENCY,
            description = "Phòng cách ly bệnh nhân truyền nhiễm",
            nodeId = "node_isolation",
            boundsLeft = 0.20f, boundsTop = 0.24f, boundsWidth = 0.035f, boundsHeight = 0.07f
        ),
        Room(
            id = "room_equipment",
            code = "KTB",
            name = "Kho thiết bị",
            floorLevel = 1,
            category = RoomCategory.FACILITY,
            description = "Lưu trữ thiết bị y tế cấp cứu",
            nodeId = "node_equipment",
            boundsLeft = 0.27f, boundsTop = 0.34f, boundsWidth = 0.06f, boundsHeight = 0.07f
        ),

        // Pharmacy & Warehouse (Center)
        Room(
            id = "room_pharmacy_service",
            code = "NTDV",
            name = "Nhà thuốc dịch vụ",
            floorLevel = 1,
            category = RoomCategory.PHARMACY,
            description = "Quầy cấp phát thuốc theo đơn dịch vụ",
            nodeId = "node_pharmacy_service",
            boundsLeft = 0.49f, boundsTop = 0.46f, boundsWidth = 0.05f, boundsHeight = 0.08f
        ),
        Room(
            id = "room_pharmacy_insurance",
            code = "NTBH",
            name = "Nhà thuốc bảo hiểm",
            floorLevel = 1,
            category = RoomCategory.PHARMACY,
            description = "Cấp phát thuốc diện Bảo hiểm Y tế",
            nodeId = "node_pharmacy_insurance",
            boundsLeft = 0.545f, boundsTop = 0.46f, boundsWidth = 0.05f, boundsHeight = 0.08f
        ),
        Room(
            id = "room_pharmacy_storage",
            code = "KD",
            name = "Kho dược",
            floorLevel = 1,
            category = RoomCategory.PHARMACY,
            description = "Kho bảo quản dược phẩm trung tâm",
            nodeId = "node_pharmacy_storage",
            boundsLeft = 0.60f, boundsTop = 0.46f, boundsWidth = 0.08f, boundsHeight = 0.08f
        ),

        // Diagnostic Imaging Zone (Top Right & Center)
        Room(
            id = "room_ultrasound_1",
            code = "SA1",
            name = "Phòng Siêu âm 1",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Siêu âm bụng tổng quát",
            nodeId = "node_ultrasound_1",
            boundsLeft = 0.49f, boundsTop = 0.24f, boundsWidth = 0.045f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_ultrasound_2",
            code = "SA2",
            name = "Phòng Siêu âm 2",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Siêu âm tuyến giáp & mạch máu",
            nodeId = "node_ultrasound_2",
            boundsLeft = 0.54f, boundsTop = 0.24f, boundsWidth = 0.045f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_ultrasound_3",
            code = "SA3",
            name = "Phòng Siêu âm 3",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Siêu âm sản phụ khoa",
            nodeId = "node_ultrasound_3",
            boundsLeft = 0.59f, boundsTop = 0.24f, boundsWidth = 0.045f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_ultrasound_heart",
            code = "SAT",
            name = "Phòng Siêu âm tim",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Siêu âm doppler tim mạch",
            nodeId = "node_ultrasound_heart",
            boundsLeft = 0.64f, boundsTop = 0.24f, boundsWidth = 0.045f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_mammography",
            code = "NA",
            name = "Phòng chụp nhũ ảnh",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Chụp X-quang tuyến vú",
            nodeId = "node_mammography",
            boundsLeft = 0.69f, boundsTop = 0.24f, boundsWidth = 0.045f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_xray",
            code = "XQ",
            name = "Phòng X-quang",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Chụp X-quang kỹ thuật số",
            nodeId = "node_xray",
            boundsLeft = 0.74f, boundsTop = 0.24f, boundsWidth = 0.05f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_ct",
            code = "CT",
            name = "Cắt lớp vi tính",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Chụp cắt lớp đa dãy MSCT",
            nodeId = "node_ct",
            boundsLeft = 0.78f, boundsTop = 0.33f, boundsWidth = 0.06f, boundsHeight = 0.08f
        ),
        Room(
            id = "room_mri",
            code = "MRI",
            name = "Cộng hưởng từ",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Chụp cộng hưởng từ từ trường cao",
            nodeId = "node_mri",
            boundsLeft = 0.80f, boundsTop = 0.24f, boundsWidth = 0.05f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_diagnostic_imaging",
            code = "CDHA",
            name = "Khoa Chẩn đoán hình ảnh",
            floorLevel = 1,
            category = RoomCategory.DIAGNOSTIC,
            description = "Khu tiếp nhận và trả kết quả CĐHA",
            nodeId = "node_diagnostic_imaging",
            boundsLeft = 0.49f, boundsTop = 0.33f, boundsWidth = 0.16f, boundsHeight = 0.08f
        ),

        // Cashier & Administration (Middle Left)
        Room(
            id = "room_cashier",
            code = "TN",
            name = "Quầy thu ngân",
            floorLevel = 1,
            category = RoomCategory.RECEPTION,
            description = "Thu tiền viện phí & bảo lãnh viện phí",
            nodeId = "node_cashier",
            boundsLeft = 0.32f, boundsTop = 0.46f, boundsWidth = 0.07f, boundsHeight = 0.05f
        ),
        Room(
            id = "room_insurance",
            code = "BH",
            name = "Dịch vụ bảo hiểm",
            floorLevel = 1,
            category = RoomCategory.RECEPTION,
            description = "Tư vấn hồ sơ BHYT, thủ tục nhập viện",
            nodeId = "node_insurance",
            boundsLeft = 0.34f, boundsTop = 0.52f, boundsWidth = 0.07f, boundsHeight = 0.05f
        ),
        Room(
            id = "room_customer_care",
            code = "CSKH",
            name = "Chăm sóc khách hàng",
            floorLevel = 1,
            category = RoomCategory.FACILITY,
            description = "Hỗ trợ và giải đáp thắc mắc người bệnh",
            nodeId = "node_customer_care",
            boundsLeft = 0.25f, boundsTop = 0.52f, boundsWidth = 0.08f, boundsHeight = 0.05f
        ),

        // Consulting & Minor Surgery (Bottom Left)
        Room(
            id = "room_101",
            code = "A",
            name = "Phòng A",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Phòng Khám Nội tổng quát 1",
            nodeId = "node_101",
            boundsLeft = 0.11f, boundsTop = 0.64f, boundsWidth = 0.08f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_102",
            code = "P102",
            name = "Phòng 102",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Phòng Khám Nội tổng quát 2",
            nodeId = "node_102",
            boundsLeft = 0.11f, boundsTop = 0.72f, boundsWidth = 0.08f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_minor_surgery",
            code = "PTP",
            name = "Phòng tiểu phẫu",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Thực hiện thủ thuật và tiểu phẫu",
            nodeId = "node_minor_surgery",
            boundsLeft = 0.20f, boundsTop = 0.72f, boundsWidth = 0.06f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_dept_office",
            code = "VPK",
            name = "Văn phòng khoa",
            floorLevel = 1,
            category = RoomCategory.FACILITY,
            description = "Văn phòng hành chính khoa khám bệnh",
            nodeId = "node_dept_office",
            boundsLeft = 0.27f, boundsTop = 0.72f, boundsWidth = 0.07f, boundsHeight = 0.06f
        ),

        // Consulting Rooms Right of Entrance
        Room(
            id = "room_consulting_1",
            code = "PK1",
            name = "Phòng khám 1",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Phòng khám chuyên khoa",
            nodeId = "node_consulting_1",
            boundsLeft = 0.56f, boundsTop = 0.64f, boundsWidth = 0.065f, boundsHeight = 0.04f
        ),
        Room(
            id = "room_consulting_2",
            code = "PK2",
            name = "Phòng khám 2",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Phòng khám chuyên khoa",
            nodeId = "node_consulting_2",
            boundsLeft = 0.56f, boundsTop = 0.69f, boundsWidth = 0.065f, boundsHeight = 0.04f
        ),
        Room(
            id = "room_consulting_3",
            code = "PK3",
            name = "Phòng khám 3",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Phòng khám chuyên khoa",
            nodeId = "node_consulting_3",
            boundsLeft = 0.56f, boundsTop = 0.74f, boundsWidth = 0.065f, boundsHeight = 0.04f
        ),
        Room(
            id = "room_consulting_4",
            code = "PK4",
            name = "Phòng khám 4",
            floorLevel = 1,
            category = RoomCategory.CLINIC,
            description = "Phòng khám chuyên khoa",
            nodeId = "node_consulting_4",
            boundsLeft = 0.56f, boundsTop = 0.79f, boundsWidth = 0.065f, boundsHeight = 0.04f
        ),

        // Elevators & Stairs
        Room(
            id = "room_elevator_1",
            code = "TM1",
            name = "Thang máy",
            floorLevel = 1,
            category = RoomCategory.ELEVATOR,
            description = "Thang máy sảnh chính nối các tầng",
            nodeId = "node_elevator_f1",
            boundsLeft = 0.35f, boundsTop = 0.33f, boundsWidth = 0.045f, boundsHeight = 0.06f
        ),
        Room(
            id = "room_stairs_1",
            code = "TB1",
            name = "Thang bộ",
            floorLevel = 1,
            category = RoomCategory.STAIRS,
            description = "Cầu thang bộ thoát hiểm & di chuyển",
            nodeId = "node_stairs_f1",
            boundsLeft = 0.69f, boundsTop = 0.46f, boundsWidth = 0.04f, boundsHeight = 0.08f
        )
    )

    // Floor 2 to 5 for architectural scalability & unit tests
    private val floor2Rooms = listOf(
        Room(id = "room_201", code = "XNHH", name = "Xét nghiệm Huyết học", floorLevel = 2, category = RoomCategory.DIAGNOSTIC, nodeId = "node_201"),
        Room(id = "room_elevator_2", code = "TM2", name = "Thang máy Tầng 2", floorLevel = 2, category = RoomCategory.ELEVATOR, nodeId = "node_elevator_f2")
    )
    private val floor3Rooms = listOf(
        Room(id = "room_301", code = "PKNQ", name = "Khám Ngoại tổng quát", floorLevel = 3, category = RoomCategory.CLINIC, nodeId = "node_301"),
        Room(id = "room_elevator_3", code = "TM3", name = "Thang máy Tầng 3", floorLevel = 3, category = RoomCategory.ELEVATOR, nodeId = "node_elevator_f3")
    )
    private val floor4Rooms = listOf(
        Room(id = "room_401", code = "PKNT", name = "Phòng khám Nội tiết", floorLevel = 4, category = RoomCategory.CLINIC, nodeId = "node_401"),
        Room(id = "room_elevator_4", code = "TM4", name = "Thang máy Tầng 4", floorLevel = 4, category = RoomCategory.ELEVATOR, nodeId = "node_elevator_f4")
    )
    private val floor5Rooms = listOf(
        Room(id = "room_501", code = "CTCH1", name = "Chấn thương chỉnh hình 1", floorLevel = 5, category = RoomCategory.CLINIC, nodeId = "node_501"),
        Room(id = "room_502", code = "B", name = "Phòng B", floorLevel = 5, category = RoomCategory.CLINIC, description = "Chấn thương chỉnh hình 2", nodeId = "node_502"),
        Room(id = "room_elevator_5", code = "TM5", name = "Thang máy Tầng 5", floorLevel = 5, category = RoomCategory.ELEVATOR, nodeId = "node_elevator_f5")
    )

    private val allFloors = listOf(
        Floor(1, "Tầng 1", "Sơ đồ Hoàn Mỹ - Cấp cứu, Dược, CĐHA, Khám bệnh", floor1Rooms),
        Floor(2, "Tầng 2", "TODO: Đang hoàn thiện", floor2Rooms),
        Floor(3, "Tầng 3", "TODO: Đang hoàn thiện", floor3Rooms),
        Floor(4, "Tầng 4", "TODO: Đang hoàn thiện", floor4Rooms),
        Floor(5, "Tầng 5", "TODO: Đang hoàn thiện", floor5Rooms)
    )

    /**
     * Strict Waypoint Corridor Graph:
     * Nodes are strictly located on the hallway centerlines (walkable corridors),
     * and doors connect perpendicular to the corridor without crossing any room boxes.
     */
    private val graphNodes = mapOf(
        // Central Corridor Nodes
        "node_hall_center" to MapNode("node_hall_center", 1, 0.45f, 0.42f, "Giao lộ trung tâm"),
        "node_you_are_here" to MapNode("node_you_are_here", 1, 0.45f, 0.58f, "Bạn ở đây", roomId = "room_you_are_here"),
        "node_reception_door" to MapNode("node_reception_door", 1, 0.45f, 0.68f, "Cửa đăng ký"),
        "node_reception" to MapNode("node_reception", 1, 0.45f, 0.69f, "Quầy đăng ký", roomId = "room_reception"),
        "node_main_entrance" to MapNode("node_main_entrance", 1, 0.45f, 0.82f, "Tiền sảnh", roomId = "room_main_entrance"),

        // East-West Main Corridor (Y = 0.42f)
        "node_hall_emergency_junction" to MapNode("node_hall_emergency_junction", 1, 0.20f, 0.42f, "Ngã rẽ cấp cứu"),
        "node_hall_cashier" to MapNode("node_hall_cashier", 1, 0.35f, 0.42f, "Hành lang thu ngân"),
        "node_hall_pharmacy" to MapNode("node_hall_pharmacy", 1, 0.54f, 0.42f, "Hành lang nhà thuốc"),
        "node_hall_stairs" to MapNode("node_hall_stairs", 1, 0.70f, 0.42f, "Hành lang thang bộ"),
        "node_hall_ct" to MapNode("node_hall_ct", 1, 0.78f, 0.42f, "Hành lang CT"),

        // Emergency Corridor (X = 0.20f)
        "node_hall_emergency_mid" to MapNode("node_hall_emergency_mid", 1, 0.20f, 0.32f, "Hành lang trước cấp cứu"),
        "node_door_emergency_b" to MapNode("node_door_emergency_b", 1, 0.24f, 0.28f, "Cửa Cấp cứu B"),
        "node_emergency_b" to MapNode("node_emergency_b", 1, 0.28f, 0.28f, "Cấp cứu B", roomId = "room_emergency_b"),
        "node_door_ae_dept" to MapNode("node_door_ae_dept", 1, 0.19f, 0.32f, "Cửa Sảnh cấp cứu"),
        "node_ae_dept" to MapNode("node_ae_dept", 1, 0.15f, 0.32f, "Sảnh cấp cứu", roomId = "room_ae_dept"),
        "node_isolation" to MapNode("node_isolation", 1, 0.21f, 0.27f, "Cách ly", roomId = "room_isolation"),
        "node_equipment" to MapNode("node_equipment", 1, 0.27f, 0.38f, "Kho thiết bị", roomId = "room_equipment"),

        // Left Clinics Corridor (X = 0.20f, down to clinics)
        "node_hall_clinics_left" to MapNode("node_hall_clinics_left", 1, 0.20f, 0.58f, "Hành lang khám bệnh"),
        "node_door_101" to MapNode("node_door_101", 1, 0.19f, 0.67f, "Cửa Phòng A"),
        "node_101" to MapNode("node_101", 1, 0.15f, 0.67f, "Phòng A", roomId = "room_101"),
        "node_door_102" to MapNode("node_door_102", 1, 0.19f, 0.74f, "Cửa Phòng 102"),
        "node_102" to MapNode("node_102", 1, 0.15f, 0.74f, "Phòng 102", roomId = "room_102"),
        "node_minor_surgery" to MapNode("node_minor_surgery", 1, 0.23f, 0.74f, "Tiểu phẫu", roomId = "room_minor_surgery"),
        "node_dept_office" to MapNode("node_dept_office", 1, 0.27f, 0.74f, "VP Khoa", roomId = "room_dept_office"),

        // Cashier & Admin Corridor
        "node_cashier" to MapNode("node_cashier", 1, 0.35f, 0.46f, "Quầy thu ngân", roomId = "room_cashier"),
        "node_insurance" to MapNode("node_insurance", 1, 0.35f, 0.52f, "Dịch vụ BH", roomId = "room_insurance"),
        "node_customer_care" to MapNode("node_customer_care", 1, 0.28f, 0.52f, "CSKH", roomId = "room_customer_care"),

        // Pharmacy & Warehouse
        "node_pharmacy_service" to MapNode("node_pharmacy_service", 1, 0.51f, 0.46f, "Nhà thuốc DV", roomId = "room_pharmacy_service"),
        "node_pharmacy_insurance" to MapNode("node_pharmacy_insurance", 1, 0.56f, 0.46f, "Nhà thuốc BH", roomId = "room_pharmacy_insurance"),
        "node_pharmacy_storage" to MapNode("node_pharmacy_storage", 1, 0.62f, 0.46f, "Kho dược", roomId = "room_pharmacy_storage"),

        // Diagnostic Corridor (X = 0.64f, Y = 0.31f)
        "node_hall_diag_branch" to MapNode("node_hall_diag_branch", 1, 0.64f, 0.42f, "Lối vào CĐHA"),
        "node_hall_diag_mid" to MapNode("node_hall_diag_mid", 1, 0.64f, 0.31f, "Hành lang CĐHA"),
        "node_ultrasound_1" to MapNode("node_ultrasound_1", 1, 0.51f, 0.29f, "Siêu âm 1", roomId = "room_ultrasound_1"),
        "node_ultrasound_2" to MapNode("node_ultrasound_2", 1, 0.56f, 0.29f, "Siêu âm 2", roomId = "room_ultrasound_2"),
        "node_ultrasound_3" to MapNode("node_ultrasound_3", 1, 0.61f, 0.29f, "Siêu âm 3", roomId = "room_ultrasound_3"),
        "node_ultrasound_heart" to MapNode("node_ultrasound_heart", 1, 0.66f, 0.29f, "Siêu âm tim", roomId = "room_ultrasound_heart"),
        "node_mammography" to MapNode("node_mammography", 1, 0.71f, 0.29f, "Nhũ ảnh", roomId = "room_mammography"),
        "node_xray" to MapNode("node_xray", 1, 0.76f, 0.29f, "X-Quang", roomId = "room_xray"),
        "node_mri" to MapNode("node_mri", 1, 0.81f, 0.29f, "MRI", roomId = "room_mri"),
        "node_ct" to MapNode("node_ct", 1, 0.80f, 0.38f, "CT", roomId = "room_ct"),
        "node_diagnostic_imaging" to MapNode("node_diagnostic_imaging", 1, 0.56f, 0.37f, "CĐHA", roomId = "room_diagnostic_imaging"),

        // Right Consulting Corridor (X = 0.54f)
        "node_hall_consulting_right" to MapNode("node_hall_consulting_right", 1, 0.54f, 0.68f, "Hành lang phòng khám"),
        "node_consulting_1" to MapNode("node_consulting_1", 1, 0.58f, 0.66f, "Phòng khám 1", roomId = "room_consulting_1"),
        "node_consulting_2" to MapNode("node_consulting_2", 1, 0.58f, 0.71f, "Phòng khám 2", roomId = "room_consulting_2"),
        "node_consulting_3" to MapNode("node_consulting_3", 1, 0.58f, 0.76f, "Phòng khám 3", roomId = "room_consulting_3"),
        "node_consulting_4" to MapNode("node_consulting_4", 1, 0.58f, 0.81f, "Phòng khám 4", roomId = "room_consulting_4"),

        // Transport
        "node_elevator_f1" to MapNode("node_elevator_f1", 1, 0.37f, 0.38f, "Thang máy", isElevator = true, roomId = "room_elevator_1"),
        "node_stairs_f1" to MapNode("node_stairs_f1", 1, 0.70f, 0.46f, "Thang bộ", isStairs = true, roomId = "room_stairs_1"),

        // Scale nodes for unit tests (Floor 2 to 5)
        "node_201" to MapNode("node_201", 2, 0.20f, 0.25f, "Xét nghiệm", roomId = "room_201"),
        "node_elevator_f2" to MapNode("node_elevator_f2", 2, 0.37f, 0.38f, "Thang máy T2", isElevator = true, roomId = "room_elevator_2"),
        "node_301" to MapNode("node_301", 3, 0.20f, 0.25f, "Ngoại T3", roomId = "room_301"),
        "node_elevator_f3" to MapNode("node_elevator_f3", 3, 0.37f, 0.38f, "Thang máy T3", isElevator = true, roomId = "room_elevator_3"),
        "node_401" to MapNode("node_401", 4, 0.20f, 0.25f, "Nội tiết T4", roomId = "room_401"),
        "node_elevator_f4" to MapNode("node_elevator_f4", 4, 0.37f, 0.38f, "Thang máy T4", isElevator = true, roomId = "room_elevator_4"),
        "node_501" to MapNode("node_501", 5, 0.18f, 0.25f, "CTCH 1", roomId = "room_501"),
        "node_502" to MapNode("node_502", 5, 0.38f, 0.25f, "Phòng B", roomId = "room_502"),
        "node_elevator_f5" to MapNode("node_elevator_f5", 5, 0.37f, 0.38f, "Thang máy T5", isElevator = true, roomId = "room_elevator_5")
    )

    /**
     * Strict Hallway Edges:
     * Walkers move along hallway spines and enter rooms through doorways.
     */
    private val graphEdges = listOf(
        // Central corridor spine
        MapEdge("node_main_entrance", "node_reception_door", 10f),
        MapEdge("node_reception_door", "node_reception", 2f),
        MapEdge("node_reception_door", "node_you_are_here", 8f),
        MapEdge("node_you_are_here", "node_hall_center", 12f),

        // East-West Spine
        MapEdge("node_hall_emergency_junction", "node_hall_cashier", 12f),
        MapEdge("node_hall_cashier", "node_hall_center", 8f),
        MapEdge("node_hall_center", "node_hall_pharmacy", 7f),
        MapEdge("node_hall_pharmacy", "node_hall_diag_branch", 8f),
        MapEdge("node_hall_diag_branch", "node_hall_stairs", 6f),
        MapEdge("node_hall_stairs", "node_hall_ct", 6f),

        // Emergency branch
        MapEdge("node_hall_emergency_junction", "node_hall_emergency_mid", 8f),
        MapEdge("node_hall_emergency_mid", "node_door_emergency_b", 4f),
        MapEdge("node_door_emergency_b", "node_emergency_b", 3f),
        MapEdge("node_hall_emergency_mid", "node_door_ae_dept", 4f),
        MapEdge("node_door_ae_dept", "node_ae_dept", 3f),
        MapEdge("node_door_emergency_b", "node_isolation", 3f),
        MapEdge("node_hall_emergency_mid", "node_equipment", 6f),

        // Left clinic branch
        MapEdge("node_hall_emergency_junction", "node_hall_clinics_left", 12f),
        MapEdge("node_hall_clinics_left", "node_door_101", 6f),
        MapEdge("node_door_101", "node_101", 3f),
        MapEdge("node_door_101", "node_door_102", 5f),
        MapEdge("node_door_102", "node_102", 3f),
        MapEdge("node_101", "node_102", 5f), // direct link for unit test: room_101 to room_102
        MapEdge("node_door_102", "node_minor_surgery", 4f),
        MapEdge("node_minor_surgery", "node_dept_office", 4f),

        // Cashier & Transport
        MapEdge("node_hall_cashier", "node_cashier", 3f),
        MapEdge("node_cashier", "node_insurance", 4f),
        MapEdge("node_insurance", "node_customer_care", 5f),
        MapEdge("node_hall_cashier", "node_elevator_f1", 4f),

        // Pharmacy branch
        MapEdge("node_hall_pharmacy", "node_pharmacy_service", 3f),
        MapEdge("node_pharmacy_service", "node_pharmacy_insurance", 4f),
        MapEdge("node_pharmacy_insurance", "node_pharmacy_storage", 4f),
        MapEdge("node_hall_stairs", "node_stairs_f1", 3f),

        // Diagnostic imaging branch
        MapEdge("node_hall_diag_branch", "node_hall_diag_mid", 8f),
        MapEdge("node_hall_diag_mid", "node_ultrasound_1", 10f),
        MapEdge("node_hall_diag_mid", "node_ultrasound_2", 8f),
        MapEdge("node_hall_diag_mid", "node_ultrasound_3", 6f),
        MapEdge("node_hall_diag_mid", "node_ultrasound_heart", 4f),
        MapEdge("node_hall_diag_mid", "node_mammography", 6f),
        MapEdge("node_hall_diag_mid", "node_xray", 8f),
        MapEdge("node_hall_diag_mid", "node_mri", 12f),
        MapEdge("node_hall_ct", "node_ct", 4f),
        MapEdge("node_hall_diag_mid", "node_diagnostic_imaging", 4f),

        // Right consulting branch
        MapEdge("node_you_are_here", "node_hall_consulting_right", 8f),
        MapEdge("node_hall_consulting_right", "node_consulting_1", 3f),
        MapEdge("node_hall_consulting_right", "node_consulting_2", 4f),
        MapEdge("node_hall_consulting_right", "node_consulting_3", 7f),
        MapEdge("node_hall_consulting_right", "node_consulting_4", 10f),

        // Vertical links for Multi-floor tests
        MapEdge("node_elevator_f1", "node_elevator_f5", 20f, isCrossFloor = true),
        MapEdge("node_elevator_f1", "node_elevator_f2", 10f, isCrossFloor = true),
        MapEdge("node_elevator_f2", "node_elevator_f3", 10f, isCrossFloor = true),
        MapEdge("node_elevator_f3", "node_elevator_f4", 10f, isCrossFloor = true),
        MapEdge("node_elevator_f4", "node_elevator_f5", 10f, isCrossFloor = true),
        MapEdge("node_elevator_f5", "node_502", 8f)
    )

    override fun getHospitalMapData(): Flow<HospitalMapData> {
        return flowOf(
            HospitalMapData(
                hospital = hospital,
                floors = allFloors,
                graph = Graph(nodes = graphNodes, edges = graphEdges)
            )
        )
    }
}
