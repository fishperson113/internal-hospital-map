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

    fun getRoomDisplayName(roomId: String, fallback: String, lang: AppLanguage): String {
        return when (roomId) {
            "room_emergency_b" -> if (lang == AppLanguage.VIETNAMESE) "CẤP CỨU B" else "EMERGENCY B"
            "room_ae_dept" -> if (lang == AppLanguage.VIETNAMESE) "SẢNH CẤP CỨU" else "A&E DEPT"
            "room_isolation" -> if (lang == AppLanguage.VIETNAMESE) "CÁCH LY" else "ISOLATION"
            "room_equipment" -> if (lang == AppLanguage.VIETNAMESE) "KHO THIẾT BỊ" else "EQUIPMENT"
            "room_reception" -> if (lang == AppLanguage.VIETNAMESE) "QUẦY ĐĂNG KÝ" else "RECEPTION"
            "room_pharmacy_service" -> if (lang == AppLanguage.VIETNAMESE) "NHÀ THUỐC DỊCH VỤ" else "PHARMACY"
            "room_pharmacy_insurance" -> if (lang == AppLanguage.VIETNAMESE) "NHÀ THUỐC BẢO HIỂM" else "INSURANCE PHARMACY"
            "room_pharmacy_storage" -> if (lang == AppLanguage.VIETNAMESE) "KHO DƯỢC" else "DRUG STORAGE"
            "room_ultrasound_1" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM 1" else "ULTRASOUND 1"
            "room_ultrasound_2" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM 2" else "ULTRASOUND 2"
            "room_ultrasound_3" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM 3" else "ULTRASOUND 3"
            "room_ultrasound_heart" -> if (lang == AppLanguage.VIETNAMESE) "SIÊU ÂM TIM" else "ECHO CARDIOGRAM"
            "room_mammography" -> if (lang == AppLanguage.VIETNAMESE) "NHŨ ẢNH" else "MAMMOGRAPHY"
            "room_xray" -> if (lang == AppLanguage.VIETNAMESE) "X-QUANG" else "X-RAY"
            "room_ct" -> if (lang == AppLanguage.VIETNAMESE) "CẮT LỚP VI TÍNH" else "CT SCANNER"
            "room_mri" -> if (lang == AppLanguage.VIETNAMESE) "CỘNG HƯỞNG TỪ" else "MRI ROOM"
            "room_diagnostic_imaging" -> if (lang == AppLanguage.VIETNAMESE) "CHẨN ĐOÁN HÌNH ẢNH" else "DIAGNOSTIC IMAGING"
            "room_cashier" -> if (lang == AppLanguage.VIETNAMESE) "QUẦY THU NGÂN" else "CASHIER"
            "room_insurance" -> if (lang == AppLanguage.VIETNAMESE) "BẢO HIỂM & NHẬP VIỆN" else "INSURANCE & ADMISSION"
            "room_customer_care" -> if (lang == AppLanguage.VIETNAMESE) "CHĂM SÓC KHÁCH HÀNG" else "CUSTOMER SERVICE"
            "room_101" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG A (NỘI 1)" else "ROOM A (INTERNAL 1)"
            "room_102" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM NỘI 2" else "CLINIC ROOM 2"
            "room_minor_surgery" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG TIỂU PHẪU" else "MINOR SURGERY"
            "room_dept_office" -> if (lang == AppLanguage.VIETNAMESE) "VĂN PHÒNG KHOA" else "MEETING ROOM"
            "room_consulting_1" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 1" else "CLINIC 1"
            "room_consulting_2" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 2" else "CLINIC 2"
            "room_consulting_3" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 3" else "CLINIC 3"
            "room_consulting_4" -> if (lang == AppLanguage.VIETNAMESE) "PHÒNG KHÁM 4" else "CLINIC 4"
            "room_elevator_1" -> if (lang == AppLanguage.VIETNAMESE) "THANG MÁY" else "ELEVATOR"
            "room_stairs_1" -> if (lang == AppLanguage.VIETNAMESE) "THANG BỘ" else "STAIRS"
            else -> fallback
        }
    }
}
