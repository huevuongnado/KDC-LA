package com.example.data.model

data class PortalSettings(
    val title: String = "Thôn Long An, xã Minh Long, tỉnh Quảng Ngãi",
    val slogan: String = "Đoàn kết xây dựng khu dân cư văn minh, an toàn, nghĩa tình • Chấp hành tốt chủ trương của Đảng, chính sách pháp luật của Nhà nước • Chung tay xây dựng nông thôn mới",
    val themeKey: String = "green",
    val fontSize: Int = 15,
    val logoPath: String = "",
    val coverPath: String = "",
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "",
    val updateUrl: String = "",
    val updateMessage: String = "",
    val forceUpdate: Boolean = false,
)

data class UserSession(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole,
    val roleTitle: String = "",
    val householdId: String = "",
    val cluster: String = "",
    val phone: String = "",
    val linkedPersonId: String = "",
    val cccd: String = "",
    val mustChangePassword: Boolean = false,
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
    val hasCitizenLogin: Boolean get() = linkedPersonId.isNotBlank()
}

data class PendingCitizenCredentials(
    val identifier: String,
    val password: String,
)

data class InlineMemberDraft(
    val key: Long = System.nanoTime(),
    val name: String = "",
    val dob: String = "",
    val gender: String = "Nam",
    val job: String = DomainConstants.JOBS.first(),
)

sealed interface ActionOutcome {
    data class Success(val message: String) : ActionOutcome
    data class Error(val message: String) : ActionOutcome
}
