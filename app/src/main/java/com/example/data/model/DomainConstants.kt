package com.example.data.model

object DomainConstants {
    const val BOOTSTRAP_ADMIN_EMAIL = "huevuongnado@gmail.com"
    const val LINK_REQUEST_SUBJECT = "Yêu cầu liên kết hộ gia đình"

    val CLUSTERS = listOf(
        "Cụm dân cư Hóc Nhiêu",
        "Cụm dân cư Làng Vang",
        "Cụm dân cư Ruộng Gò",
        "Cụm dân cư Đồng Rinh",
    )

    val JOBS = listOf(
        "Làm nông / Trồng rừng",
        "Công nhân",
        "Cán bộ / Công chức",
        "Giáo viên",
        "Buôn bán",
        "Học sinh / Sinh viên",
        "Nội trợ",
        "Lao động tự do",
        "Bộ đội / Công an",
        "Nghỉ hưu",
        "Khác",
    )

    val EDUCATION_LEVELS = listOf(
        "Chưa đi học",
        "Tiểu học",
        "THCS",
        "THPT",
        "Khác",
    )

    val PROFESSION_LEVELS = listOf(
        "Chưa qua đào tạo",
        "Sơ cấp / CNKT",
        "Trung cấp",
        "Cao đẳng",
        "Đại học",
        "Sau đại học",
    )

    val RELIGIONS = listOf(
        "Không",
        "Phật giáo",
        "Công giáo",
        "Tin Lành",
        "Cao Đài",
        "Khác",
    )

    val RESIDENCE_STATUSES = listOf(
        "Thường trú",
        "Tạm trú",
        "Đi làm ăn xa",
        "Đã chuyển đi",
    )

    val HOUSEHOLD_STATUSES = listOf(
        "Thường trú",
        "Tạm trú",
    )

    val HOUSEHOLD_CLASSIFICATIONS = listOf(
        "Không",
        "Hộ nghèo",
        "Hộ cận nghèo",
        "Hộ chính sách",
    )

    val GENDERS = listOf("Nam", "Nữ", "Khác")

    val POST_CATEGORIES = listOf(
        "Lịch họp thôn",
        "Tuyên truyền chính sách",
        "Phòng chống thiên tai",
        "Thu quỹ & Đóng góp",
        "Văn hóa - Đời sống",
    )

    val POST_SCOPES = listOf(
        "Toàn thôn",
        "Theo cụm dân cư",
    )

    val COMPLAINT_SUBJECTS = listOf(
        "Biến động nhân khẩu",
        "Chuyển đến / chuyển đi",
        LINK_REQUEST_SUBJECT,
        "Đề nghị xác nhận thông tin",
        "Môi trường - an ninh trật tự",
        "Kiến nghị khác",
    )

    val FONT_SIZES = listOf(13, 14, 15, 16, 17, 18, 19, 20)

    val INCOME_BRACKETS = listOf(
        "Dưới 30 triệu/năm",
        "30 - 70 triệu/năm",
        "70 - 150 triệu/năm",
        "150 - 300 triệu/năm",
        "Trên 300 triệu/năm",
    )

    val MAIN_INCOME_SOURCES = listOf(
        "Làm nông / Trồng keo, lúa",
        "Chăn nuôi",
        "Buôn bán / Dịch vụ",
        "Làm thuê / Công nhân",
        "Lương hưu / Trợ cấp",
        "Khác",
    )

    val HOUSE_TYPES = listOf(
        "Nhà cấp 4",
        "Nhà kiên cố / Mái bằng / Tầng",
        "Nhà bán kiên cố / Nhà gỗ",
        "Nhà tạm",
        "Khác",
    )

    val WATER_SOURCES = listOf(
        "Nước máy tập trung",
        "Giếng đào / Giếng khoan",
        "Nước suối / Tự chảy",
        "Khác",
    )

    fun shortClusterName(cluster: String): String =
        cluster.removePrefix("Cụm dân cư ").trim()
}

enum class UserRole(val key: String, val displayLabel: String) {
    ADMIN("admin", "Trưởng thôn / Quản trị viên"),
    CITIZEN("citizen", "Bà con nhân dân");

    companion object {
        fun fromKey(key: String?): UserRole = entries.firstOrNull { it.key == key } ?: CITIZEN
    }
}

enum class PersonAddMode(val label: String) {
    NORMAL("Thông thường / Chuyển đến"),
    NEWBORN("Trẻ mới sinh"),
    TEMPORARY("Đăng ký tạm trú"),
}

enum class AgeGroupFilter(val label: String) {
    ALL("Mọi nhóm tuổi"),
    UNDER_6("Dưới 6 tuổi"),
    SCHOOL_6_17("6–17 tuổi"),
    MILITARY_18_27("18–27 tuổi"),
    ADULT_28_59("28–59 tuổi"),
    SENIOR_60_PLUS("Từ 60 tuổi"),
}
