package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val cluster: String = "",
    val householdId: String = "",
    val linkedPersonId: String = "",
    val role: String = UserRole.CITIZEN.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CitizenAccountDoc(
    @DocumentId val id: String = "",
    val personId: String = "",
    val householdId: String = "",
    val cluster: String = "",
    val name: String = "",
    val phone: String = "",
    val cccd: String = "",
    val loginKeys: List<String> = emptyList(),
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val mustChangePassword: Boolean = true,
    val boundUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)


@Keep
@IgnoreExtraProperties
data class AdminRecordDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val addedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class HouseholdDoc(
    @DocumentId val id: String = "",
    val head: String = "",
    val cluster: String = "",
    val status: String = "Thường trú",
    val classify: String = "Không",
    val address: String = "",
    val memberEmails: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PersonDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val relation: String = "Thành viên",
    val dob: String = "",
    val gender: String = "Nam",
    val cccd: String = "",
    val phone: String = "",
    val householdId: String = "",
    val cluster: String = "",
    val residence: String = "Thường trú",
    val education: String = "Chưa đi học",
    val profession: String = "Chưa qua đào tạo",
    val religion: String = "Không",
    val partyMember: Boolean = false,
    val job: String = "Làm nông / Trồng rừng",
    val tempStart: String = "",
    val tempEnd: String = "",
    val memberEmails: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PostDoc(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = "",
    val scope: String = "Toàn thôn",
    val cluster: String = "",
    val content: String = "",
    val dateIso: String = "",
    val pinned: Boolean = false,
    val imageData: String = "",
    val allowedEmails: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ComplaintDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val senderName: String = "",
    val householdId: String = "",
    val subject: String = "",
    val content: String = "",
    val dateIso: String = "",
    val status: String = "Chờ xử lý",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PortalSettingsDoc(
    @DocumentId val id: String = "",
    val title: String = "Thôn Long An, xã Minh Long, tỉnh Quảng Ngãi",
    val slogan: String = "Đoàn kết xây dựng khu dân cư văn minh, an toàn, nghĩa tình • Chấp hành tốt chủ trương của Đảng, chính sách pháp luật của Nhà nước • Chung tay xây dựng nông thôn mới",
    val themeKey: String = "green",
    val fontSize: Int = 15,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class HouseholdSurveyDoc(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val cluster: String = "",
    val headName: String = "",
    val updatedByName: String = "",
    val updatedByUid: String = "",
    val annualIncomeMillion: Int = 0,
    val incomeBracket: String = DomainConstants.INCOME_BRACKETS[1],
    val mainIncomeSource: String = DomainConstants.MAIN_INCOME_SOURCES.first(),
    val farmLandNote: String = "",
    val motorbikes: Int = 0,
    val cars: Int = 0,
    val trucks: Int = 0,
    val waterPumps: Int = 0,
    val tractors: Int = 0,
    val riceMills: Int = 0,
    val sprayersOrCutters: Int = 0,
    val otherEquipment: String = "",
    val cows: Int = 0,
    val buffaloes: Int = 0,
    val pigs: Int = 0,
    val chickens: Int = 0,
    val ducks: Int = 0,
    val otherLivestock: String = "",
    val houseType: String = DomainConstants.HOUSE_TYPES.first(),
    val waterSource: String = DomainConstants.WATER_SOURCES.first(),
    val hasCleanToilet: Boolean = true,
    val hasHealthInsuranceAll: Boolean = true,
    val internetConnected: Boolean = true,
    val otherNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
