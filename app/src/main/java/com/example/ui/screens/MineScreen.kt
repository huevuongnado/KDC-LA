package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ComplaintDoc
import com.example.data.model.DomainConstants
import com.example.data.model.HouseholdSurveyDoc
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.PortalTheme
import com.example.ui.viewmodel.ActiveDialog
import com.example.ui.viewmodel.PortalUiState
import com.example.util.DateUtils

@Composable
fun MineScreen(
    state: PortalUiState,
    onOpenDialog: (ActiveDialog) -> Unit,
    onToggleComplaintStatus: (ComplaintDoc) -> Unit,
    onApproveHouseholdLink: (ComplaintDoc) -> Unit,
    onOpenCitizenLogin: () -> Unit,
    onLogoutCitizen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    val myHousehold = remember(state.households, session) {
        if (session == null) null
        else if (session.householdId.isNotBlank()) {
            state.households.firstOrNull { it.id == session.householdId }
        } else if (!state.isAdmin) {
            state.households.firstOrNull()
        } else null
    }
    val myMembers = remember(state.people, myHousehold) {
        myHousehold?.let { h -> state.people.filter { it.householdId == h.id } }.orEmpty()
    }
    val mySurvey = remember(state.householdSurveys, myHousehold) {
        myHousehold?.let { state.surveyForHousehold(it.id) }
    }
    val visibleComplaints = remember(state.complaints, session, state.isAdmin) {
        if (session == null) emptyList()
        else state.complaints.filter { state.isAdmin || it.userId == session.id }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hồ sơ hộ gia đình của tôi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = when {
                                    session?.hasCitizenLogin == true ->
                                        "${session.name} • SĐT: ${session.phone.ifBlank { "—" }}"
                                    !session?.email.isNullOrBlank() -> session?.email.orEmpty()
                                    else -> "Thông tin được đồng bộ với Ban điều hành thôn."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val semColors = PortalTheme.colors
                        StatusPill(
                            text = "Mã hộ: ${myHousehold?.id ?: "Chưa liên kết"}",
                            backgroundColor = semColors.infoBg,
                            textColor = semColors.infoText,
                        )
                    }

                    if (!state.isAdmin && session?.hasCitizenLogin == true) {
                        val semColors = PortalTheme.colors
                        if (session.mustChangePassword) {
                            StatusPill(
                                text = "Đang dùng mật khẩu mặc định — nên đổi mật khẩu mới",
                                backgroundColor = semColors.amberBg,
                                textColor = semColors.amberText,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenDialog(ActiveDialog.ChangeOwnPassword) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("mine_change_password_button"),
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Đổi mật khẩu")
                            }
                            OutlinedButton(
                                onClick = onLogoutCitizen,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("mine_logout_citizen_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Đổi tài khoản")
                            }
                        }
                    }

                    if (myHousehold != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chủ hộ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = myHousehold.head,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Diện hộ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = myHousehold.classify.ifBlank { "Không" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Text(
                            text = "Cụm dân cư: ${myHousehold.cluster}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider()
                        Text(
                            text = "Thành viên (${myMembers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        myMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${member.name} (${member.relation})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "Ngày sinh: ${DateUtils.formatDateForDisplay(member.dob)} • ${member.job}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val semColors = PortalTheme.colors
                                StatusPill(
                                    text = member.residence,
                                    backgroundColor = if (member.residence == "Thường trú") semColors.successBg else semColors.amberBg,
                                    textColor = if (member.residence == "Thường trú") semColors.successText else semColors.amberText,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (state.isAdmin) {
                                "Bạn đang đăng nhập với vai trò Trưởng thôn / Quản trị viên. Bạn có thể xem và cập nhật toàn bộ hộ trên tab Hộ gia đình & Nhân khẩu."
                            } else {
                                "Đăng nhập bằng Số điện thoại hoặc Mã định danh (CCCD) để xem thông tin hộ gia đình của bạn, hoặc gửi yêu cầu liên kết hộ đến Trưởng thôn."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isAdmin) MaterialTheme.colorScheme.onSurfaceVariant else PortalTheme.colors.amberText,
                        )
                        if (!state.isAdmin) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = onOpenCitizenLogin,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("mine_citizen_login_button"),
                                ) {
                                    Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Đăng nhập bằng SĐT / Mã định danh")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onOpenDialog(
                                            ActiveDialog.ComplaintEditor(
                                                defaultSubject = DomainConstants.LINK_REQUEST_SUBJECT,
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("request_household_link_button"),
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gửi yêu cầu liên kết hộ")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (myHousehold != null) {
            item {
                MineHouseholdSurveyCard(
                    survey = mySurvey,
                    onOpenSurvey = {
                        onOpenDialog(
                            ActiveDialog.HouseholdSurveyEditor(
                                household = myHousehold,
                                existing = mySurvey,
                            )
                        )
                    },
                )
            }
        }

        // Complaints Section
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Phản ánh / Biến động nhân khẩu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                onOpenDialog(
                                    ActiveDialog.ComplaintEditor(
                                        defaultHouseholdId = myHousehold?.id.orEmpty(),
                                    )
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_complaint_button"),
                        ) {
                            Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gửi phản ánh")
                        }
                    }
                    Text(
                        text = if (state.isAdmin) {
                            "Xem phản ánh của bà con và duyệt yêu cầu liên kết hộ gia đình."
                        } else {
                            "Thông tin được gửi trực tuyến đến Trưởng thôn để xem xét, cập nhật."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (visibleComplaints.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Feedback,
                    title = "Chưa có phản ánh nào",
                    subtitle = "Nhấn \"Gửi phản ánh\" để báo biến động nhân khẩu hoặc gửi kiến nghị tới Ban điều hành thôn.",
                )
            }
        } else {
            items(visibleComplaints, key = { it.id }) { complaint ->
                val isLinkRequest = complaint.subject == DomainConstants.LINK_REQUEST_SUBJECT &&
                    complaint.householdId.isNotBlank()
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = complaint.subject,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            val semColors = PortalTheme.colors
                            StatusPill(
                                text = complaint.status,
                                backgroundColor = if (complaint.status == "Đã xử lý") semColors.successBg else semColors.amberBg,
                                textColor = if (complaint.status == "Đã xử lý") semColors.successText else semColors.amberText,
                            )
                        }
                        if (complaint.householdId.isNotBlank()) {
                            val semColors = PortalTheme.colors
                            StatusPill(
                                text = "Mã hộ đề nghị: ${complaint.householdId}",
                                backgroundColor = semColors.infoBg,
                                textColor = semColors.infoText,
                            )
                        }
                        Text(
                            text = complaint.content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${complaint.senderName} (${complaint.userEmail.ifBlank { "Google" }}) • ${DateUtils.formatDateForDisplay(complaint.dateIso)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.isAdmin) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isLinkRequest && complaint.status != "Đã xử lý") {
                                    Button(
                                        onClick = { onApproveHouseholdLink(complaint) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Filled.HowToReg, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Duyệt liên kết hộ")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onToggleComplaintStatus(complaint) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (complaint.status == "Đã xử lý") "Mở lại" else "Đã xử lý")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MineHouseholdSurveyCard(
    survey: HouseholdSurveyDoc?,
    onOpenSurvey: () -> Unit,
) {
    val semColors = PortalTheme.colors
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Agriculture,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = "Kinh tế – Tài sản – Vật nuôi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (survey == null) "Chưa kê khai thông tin" else "Đã cập nhật dữ liệu hộ",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (survey == null) semColors.amberText else semColors.successText,
                        )
                    }
                }
                Button(
                    onClick = onOpenSurvey,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mine_edit_survey_button"),
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (survey == null) "Tự nhập" else "Cập nhật")
                }
            }

            if (survey == null) {
                Text(
                    text = "Bà con chủ động nhập thu nhập hằng năm, xe máy, ô tô, xe tải, máy bơm, máy cày, máy xát lúa, số lượng bò, trâu, heo, gà, vịt và các vấn đề khác của hộ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HorizontalDivider()
                val incomeLabel = if (survey.annualIncomeMillion > 0) {
                    "${survey.annualIncomeMillion} triệu đ/năm (${survey.incomeBracket})"
                } else {
                    survey.incomeBracket
                }
                Text(
                    text = "Thu nhập: $incomeLabel • Nguồn chính: ${survey.mainIncomeSource}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (survey.farmLandNote.isNotBlank()) {
                    Text(
                        text = "Đất sản xuất: ${survey.farmLandNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val equipmentParts = buildList {
                    if (survey.motorbikes > 0) add("${survey.motorbikes} xe máy")
                    if (survey.cars > 0) add("${survey.cars} ô tô")
                    if (survey.trucks > 0) add("${survey.trucks} xe tải")
                    if (survey.waterPumps > 0) add("${survey.waterPumps} máy bơm")
                    if (survey.tractors > 0) add("${survey.tractors} máy cày")
                    if (survey.riceMills > 0) add("${survey.riceMills} máy xát lúa")
                    if (survey.sprayersOrCutters > 0) add("${survey.sprayersOrCutters} máy cắt/phun")
                    if (survey.otherEquipment.isNotBlank()) add(survey.otherEquipment)
                }
                Text(
                    text = "Phương tiện & máy móc: ${if (equipmentParts.isEmpty()) "Chưa kê khai" else equipmentParts.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                )

                val livestockParts = buildList {
                    if (survey.cows > 0) add("${survey.cows} bò")
                    if (survey.buffaloes > 0) add("${survey.buffaloes} trâu")
                    if (survey.pigs > 0) add("${survey.pigs} heo")
                    if (survey.chickens > 0) add("${survey.chickens} gà")
                    if (survey.ducks > 0) add("${survey.ducks} vịt")
                    if (survey.otherLivestock.isNotBlank()) add(survey.otherLivestock)
                }
                Text(
                    text = "Vật nuôi: ${if (livestockParts.isEmpty()) "Không có" else livestockParts.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    text = "Nhà ở: ${survey.houseType} • Nước: ${survey.waterSource}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (survey.otherNotes.isNotBlank()) {
                    Text(
                        text = "Ghi chú khác: ${survey.otherNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
