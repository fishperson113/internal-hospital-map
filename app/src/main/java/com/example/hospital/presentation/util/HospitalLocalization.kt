package com.example.hospital.presentation.util

enum class AppLanguage(val code: String, val displayName: String) {
    VIETNAMESE("vi", "Tiếng Việt"),
    ENGLISH("en", "English")
}

object HospitalLocalization {
    fun getHospitalName(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Bệnh viện Đa khoa Hoàn Mỹ"
        AppLanguage.ENGLISH -> "Hoan My General Hospital"
    }

    fun getSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Sơ đồ chỉ đường Tầng 1"
        AppLanguage.ENGLISH -> "Floor 1 Indoor Navigation"
    }

    fun getSearchHint(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Tìm phòng khám, khoa..."
        AppLanguage.ENGLISH -> "Search clinics, rooms..."
    }

    fun getFromLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Từ"
        AppLanguage.ENGLISH -> "From"
    }

    fun getToLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đến"
        AppLanguage.ENGLISH -> "To"
    }

    fun getAllFloorsLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Tất cả"
        AppLanguage.ENGLISH -> "All"
    }

    fun getFloorLabel(level: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Tầng $level"
        AppLanguage.ENGLISH -> "Floor $level"
    }

    fun getDetailedRouteLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Lộ trình chi tiết"
        AppLanguage.ENGLISH -> "Route Details"
    }

    fun getEstimatedTimeLabel(minutes: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Tổng thời gian ước tính: ~ $minutes phút"
        AppLanguage.ENGLISH -> "Est. duration: ~ $minutes min"
    }

    fun getStartNavLabel(isNavigating: Boolean, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> if (isNavigating) "Dừng chỉ đường" else "Bắt đầu chỉ đường"
        AppLanguage.ENGLISH -> if (isNavigating) "Stop Navigation" else "Start Navigation"
    }

    fun getExitLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "LỐI THOÁT HIỂM"
        AppLanguage.ENGLISH -> "EMERGENCY EXIT"
    }

    fun getLegendLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "CHÚ THÍCH"
        AppLanguage.ENGLISH -> "NOTE / LEGEND"
    }

    fun getYouAreHereLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Bạn ở đây"
        AppLanguage.ENGLISH -> "You are here"
    }

    fun getMainEntranceLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "TIỀN SẢNH"
        AppLanguage.ENGLISH -> "MAIN ENTRANCE"
    }

    fun getSearchDialogTitle(isSelectingStart: Boolean, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> if (isSelectingStart) "Chọn điểm xuất phát (Từ)" else "Chọn điểm đến (Đến)"
        AppLanguage.ENGLISH -> if (isSelectingStart) "Select starting point (From)" else "Select destination (To)"
    }

    fun getSelectStartPrompt(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Chọn điểm đi"
        AppLanguage.ENGLISH -> "Select Start"
    }

    fun getSelectDestPrompt(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Chọn điểm đến"
        AppLanguage.ENGLISH -> "Select Destination"
    }

    fun getCloseButtonLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đóng"
        AppLanguage.ENGLISH -> "Close"
    }

    fun getClearSearchLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Xóa tìm kiếm"
        AppLanguage.ENGLISH -> "Clear search"
    }

    fun getFloorTodoNotice(level: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Tầng $level - Đang cập nhật (TODO)"
        AppLanguage.ENGLISH -> "Floor $level - In development (TODO)"
    }

    fun getStepWalkFromInstruction(roomName: String, floorLevel: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đi từ $roomName (Tầng $floorLevel)"
        AppLanguage.ENGLISH -> "Depart from $roomName (Floor $floorLevel)"
    }

    fun getStepWalkStraightInstruction(lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đi thẳng theo hành lang"
        AppLanguage.ENGLISH -> "Walk straight along corridor"
    }

    fun getStepElevatorInstruction(isUp: Boolean, targetFloor: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đi thang máy ${if (isUp) "lên" else "xuống"} Tầng $targetFloor"
        AppLanguage.ENGLISH -> "Take elevator ${if (isUp) "up" else "down"} to Floor $targetFloor"
    }

    fun getStepStairsInstruction(isUp: Boolean, targetFloor: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đi thang bộ ${if (isUp) "lên" else "xuống"} Tầng $targetFloor"
        AppLanguage.ENGLISH -> "Take stairs ${if (isUp) "up" else "down"} to Floor $targetFloor"
    }

    fun getStepDestinationInstruction(destName: String, floorLevel: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.VIETNAMESE -> "Đi đến $destName (Tầng $floorLevel)"
        AppLanguage.ENGLISH -> "Arrive at $destName (Floor $floorLevel)"
    }

    fun getRoomDisplayName(roomId: String, fallback: String, lang: AppLanguage): String {
        return when (roomId) {
            "room_you_are_here", "node_you_are_here" -> if (lang == AppLanguage.VIETNAMESE) "Bạn ở đây" else "You are here"
            "room_main_entrance", "node_main_entrance" -> if (lang == AppLanguage.VIETNAMESE) "Tiền sảnh" else "Main Entrance"
            "room_emergency_b", "node_emergency_b" -> if (lang == AppLanguage.VIETNAMESE) "CẤP CỨU B" else "EMERGENCY B"
            "room_ae_dept", "node_ae_dept" -> if (lang == AppLanguage.VIETNAMESE) "SẢNH CẤP CỨU" else "A&E DEPT"
            "room_isolation", "node_isolation" -> if (lang == AppLanguage.VIETNAMESE) "CÁCH LY" else "ISOLATION"
            "room_equipment", "node_equipment" -> if (lang == AppLanguage.VIETNAMESE) "KHO THIẾT BỊ" else "EQUIPMENT"
            "room_reception", "node_reception", "node_reception_door" -> if (lang == AppLanguage.VIETNAMESE) "QUẦY ĐĂNG KÝ" else "RECEPTION"
            "room_pharmacy_service", "node_pharmacy_service" -> if (lang == AppLanguage.VIETNAMESE) "NHÀ THUỐC DỊCH VỤ" else "PHARMACY"
            "room_pharmacy_insurance", "node_pharmacy_insurance" -> if (lang == AppLanguage.VIETNAMESE) "NHÀ THUỐC BẢO HIỂM" else "INSURANCE PHARMACY"
            "room_pharmacy_storage", "node_pharmacy_storage" -> if (lang == AppLanguage.VIETNAMESE) "KHO DƯỢC" else "DRUG STORAGE"
            "room_ultrasound_1", "node_ultrasound_1" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM 1" else "ULTRASOUND 1"
            "room_ultrasound_2", "node_ultrasound_2" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM 2" else "ULTRASOUND 2"
            "room_ultrasound_3", "node_ultrasound_3" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM 3" else "ULTRASOUND 3"
            "room_ultrasound_heart", "node_ultrasound_heart" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM TIM" else "ECHO CARDIOGRAM"
            "room_mammography", "node_mammography" -> if (lang == AppLanguage.VIETNAMESE) "NHŨ ẢNH" else "MAMMOGRAPHY"
            "room_xray", "node_xray" -> if (lang == AppLanguage.VIETNAMESE) "X-QUANG" else "X-RAY"
            "room_ct", "node_ct" -> if (lang == AppLanguage.VIETNAMESE) "CẮT LỚP VI TÍNH" else "CT SCANNER"
            "room_mri", "node_mri" -> if (lang == AppLanguage.VIETNAMESE) "CỘNG HƯỞNG TỪ" else "MRI ROOM"
            "room_diagnostic_imaging", "node_diagnostic_imaging" -> if (lang == AppLanguage.VIETNAMESE) "CHẨN ĐOÁN HÌNH ẢNH" else "DIAGNOSTIC IMAGING"
            "room_cashier", "node_cashier" -> if (lang == AppLanguage.VIETNAMESE) "QUẦY THU NGÂN" else "CASHIER"
            "room_insurance", "node_insurance" -> if (lang == AppLanguage.VIETNAMESE) "BẢO HIỂM & NHẬP VIỆN" else "INSURANCE & ADMISSION"
            "room_customer_care", "node_customer_care" -> if (lang == AppLanguage.VIETNAMESE) "CHĂM SÓC KHÁCH HÀNG" else "CUSTOMER SERVICE"
            "room_101", "node_101" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG A (NỘI 1)" else "ROOM A (INTERNAL 1)"
            "room_102", "node_102" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM NỘI 2" else "CLINIC ROOM 2"
            "room_minor_surgery", "node_minor_surgery" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG TIỂU PHẪU" else "MINOR SURGERY"
            "room_dept_office", "node_dept_office" -> if (lang == AppLanguage.VIETNAMESE) "VĂN PHÒNG KHOA" else "MEETING ROOM"
            "room_consulting_1", "node_consulting_1" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 1" else "CLINIC 1"
            "room_consulting_2", "node_consulting_2" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 2" else "CLINIC 2"
            "room_consulting_3", "node_consulting_3" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 3" else "CLINIC 3"
            "room_consulting_4", "node_consulting_4" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 4" else "CLINIC 4"
            "room_elevator_1", "node_elevator_f1" -> if (lang == AppLanguage.VIETNAMESE) "THANG MÁY" else "ELEVATOR"
            "room_stairs_1", "node_stairs_f1" -> if (lang == AppLanguage.VIETNAMESE) "THANG BỘ" else "STAIRS"
            "room_502", "node_502" -> if (lang == AppLanguage.VIETNAMESE) "Phòng B" else "Room B"
            else -> {
                if (lang == AppLanguage.ENGLISH) {
                    when {
                        fallback.contains("Bạn ở đây", ignoreCase = true) -> "You are here"
                        fallback.contains("Tiền sảnh", ignoreCase = true) -> "Main Entrance"
                        fallback.contains("Cấp cứu B", ignoreCase = true) -> "Emergency B"
                        fallback.contains("Quầy đăng ký", ignoreCase = true) -> "Reception"
                        else -> fallback
                    }
                } else {
                    fallback
                }
            }
        }
    }

    fun getRoomDescription(roomId: String, fallback: String, lang: AppLanguage): String {
        return when (roomId) {
            "room_you_are_here" -> if (lang == AppLanguage.VIETNAMESE) "Vị trí mốc tại sảnh" else "Current location marker"
            "room_emergency_b" -> if (lang == AppLanguage.VIETNAMESE) "Khoa cấp cứu 24/7" else "Emergency department 24/7"
            "room_pharmacy_service" -> if (lang == AppLanguage.VIETNAMESE) "Cấp phát thuốc dịch vụ" else "Hospital pharmacy service"
            "room_reception" -> if (lang == AppLanguage.VIETNAMESE) "Tiếp đón bệnh nhân ban đầu" else "Patient admission & reception"
            "room_101" -> if (lang == AppLanguage.VIETNAMESE) "Khám Nội tổng quát 1" else "General Internal Medicine 1"
            "room_502" -> if (lang == AppLanguage.VIETNAMESE) "Chấn thương chỉnh hình 2" else "Orthopedics Clinic 2"
            else -> if (lang == AppLanguage.ENGLISH) "Floor 1" else fallback
        }
    }
}
