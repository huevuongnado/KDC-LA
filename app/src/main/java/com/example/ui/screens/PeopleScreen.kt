package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AgeGroupFilter
import com.example.data.model.CitizenAccountDoc
import com.example.data.model.DomainConstants
import com.example.data.model.PersonDoc
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PortalDropdownField
import com.example.ui.components.StatusPill
import com.example.ui.theme.PortalTheme
import com.example.ui.viewmodel.ActiveDialog
import com.example.ui.viewmodel.FilterState
import com.example.ui.viewmodel.PortalUiState
import com.example.util.DateUtils

@Composable
fun PeopleScreen(
    state: PortalUiState,
    onUpdateFilters: ((FilterState) -> FilterState) -> Unit,
    onClearFilters: () -> Unit,
    onOpenDialog: (ActiveDialog) -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdvancedFilters by remember { mutableStateOf(false) }
    val filters = state.filters
    val citizenAccountsByPersonId = remember(state.citizenAccounts) {
        state.citizenAccounts.associateBy { it.personId }
    }

    val kpiTotal = state.people.size
    val kpiLocal = remember(state.people) {
        state.people.count { it.residence == "Thường trú" || it.residence == "Tạm trú" }
    }
    val kpiAway = remember(state.people) {
        state.people.count { it.residence == "Đi làm ăn xa" }
    }
    val kpiParty = remember(state.people) {
        state.people.count { it.partyMember }
    }
    val kpiMilitary = remember(state.people) {
        state.people.count { p ->
            val a = DateUtils.calculateAge(p.dob) ?: -1
            p.gender == "Nam" && a in 18..27
        }
    }
    val kpiSenior = remember(state.people) {
        state.people.count { p -> (DateUtils.calculateAge(p.dob) ?: -1) >= 60 }
    }
    val kpiChild = remember(state.people) {
        state.people.count { p ->
            val a = DateUtils.calculateAge(p.dob)
            a != null && a < 6
        }
    }

    val filteredList = remember(state.people, filters) {
        val term = filters.peopleSearch.trim().lowercase()
        state.people.filter { p ->
            val ageVal = DateUtils.calculateAge(p.dob)
            val matchesTerm = term.isBlank() ||
                "${p.name} ${p.cccd} ${p.phone} ${p.job}".lowercase().contains(term)
            val matchesCluster = filters.peopleCluster.isBlank() || p.cluster == filters.peopleCluster
            val matchesParty = when (filters.peopleParty) {
                "yes" -> p.partyMember
                "no" -> !p.partyMember
                else -> true
            }
            val matchesEdu = filters.peopleEdu.isBlank() || p.education == filters.peopleEdu
            val matchesPro = filters.peoplePro.isBlank() || p.profession == filters.peoplePro
            val matchesRel = filters.peopleReligion.isBlank() || p.religion == filters.peopleReligion
            val matchesJob = filters.peopleJob.isBlank() || p.job == filters.peopleJob
            val matchesAge = when (filters.peopleAge) {
                AgeGroupFilter.ALL -> true
                AgeGroupFilter.UNDER_6 -> ageVal != null && ageVal < 6
                AgeGroupFilter.SCHOOL_6_17 -> ageVal != null && ageVal in 6..17
                AgeGroupFilter.MILITARY_18_27 -> ageVal != null && ageVal in 18..27
                AgeGroupFilter.ADULT_28_59 -> ageVal != null && ageVal in 28..59
                AgeGroupFilter.SENIOR_60_PLUS -> ageVal != null && ageVal >= 60
            }
            val matchesResidence = when (filters.peopleResidence) {
                "LOCAL" -> p.residence == "Thường trú" || p.residence == "Tạm trú"
                "AWAY" -> p.residence == "Đi làm ăn xa"
                else -> true
            }
            val matchesMilitary = !filters.peopleMale18To27Only ||
                (p.gender == "Nam" && ageVal != null && ageVal in 18..27)

            matchesTerm && matchesCluster && matchesParty && matchesEdu && matchesPro &&
                matchesRel && matchesJob && matchesAge && matchesResidence && matchesMilitary
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isAdmin || state.people.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KpiMetricCard(
                        label = "Tổng nhân khẩu",
                        value = kpiTotal.toString(),
                        icon = Icons.Filled.Groups,
                        selected = filters.peopleResidence.isBlank() &&
                            filters.peopleParty.isBlank() &&
                            !filters.peopleMale18To27Only &&
                            filters.peopleAge == AgeGroupFilter.ALL,
                        onClick = onClearFilters,
                        modifier = Modifier.width(156.dp),
                    )
                    KpiMetricCard(
                        label = "Tại địa phương",
                        value = kpiLocal.toString(),
                        icon = Icons.Filled.HomeWork,
                        selected = filters.peopleResidence == "LOCAL",
                        onClick = {
                            onUpdateFilters {
                                it.copy(
                                    peopleResidence = if (it.peopleResidence == "LOCAL") "" else "LOCAL",
                                    peopleMale18To27Only = false,
                                )
                            }
                        },
                        modifier = Modifier.width(156.dp),
                    )
                    KpiMetricCard(
                        label = "Đi làm ăn xa",
                        value = kpiAway.toString(),
                        icon = Icons.Filled.Luggage,
                        selected = filters.peopleResidence == "AWAY",
                        onClick = {
                            onUpdateFilters {
                                it.copy(
                                    peopleResidence = if (it.peopleResidence == "AWAY") "" else "AWAY",
                                    peopleMale18To27Only = false,
                                )
                            }
                        },
                        modifier = Modifier.width(156.dp),
                    )
                    KpiMetricCard(
                        label = "Đảng viên",
                        value = kpiParty.toString(),
                        icon = Icons.Filled.Star,
                        selected = filters.peopleParty == "yes",
                        onClick = {
                            onUpdateFilters {
                                it.copy(peopleParty = if (it.peopleParty == "yes") "" else "yes")
                            }
                        },
                        modifier = Modifier.width(156.dp),
                    )
                    KpiMetricCard(
                        label = "Nam 18–27 tuổi",
                        value = kpiMilitary.toString(),
                        icon = Icons.Filled.Shield,
                        selected = filters.peopleMale18To27Only,
                        onClick = {
                            onUpdateFilters {
                                it.copy(peopleMale18To27Only = !it.peopleMale18To27Only)
                            }
                        },
                        modifier = Modifier.width(156.dp),
                    )
                    KpiMetricCard(
                        label = "Người cao tuổi ≥60",
                        value = kpiSenior.toString(),
                        icon = Icons.Filled.Elderly,
                        selected = filters.peopleAge == AgeGroupFilter.SENIOR_60_PLUS,
                        onClick = {
                            onUpdateFilters {
                                it.copy(
                                    peopleAge = if (it.peopleAge == AgeGroupFilter.SENIOR_60_PLUS) {
                                        AgeGroupFilter.ALL
                                    } else {
                                        AgeGroupFilter.SENIOR_60_PLUS
                                    }
                                )
                            }
                        },
                        modifier = Modifier.width(164.dp),
                    )
                    KpiMetricCard(
                        label = "Trẻ em dưới 6 tuổi",
                        value = kpiChild.toString(),
                        icon = Icons.Filled.ChildCare,
                        selected = filters.peopleAge == AgeGroupFilter.UNDER_6,
                        onClick = {
                            onUpdateFilters {
                                it.copy(
                                    peopleAge = if (it.peopleAge == AgeGroupFilter.UNDER_6) {
                                        AgeGroupFilter.ALL
                                    } else {
                                        AgeGroupFilter.UNDER_6
                                    }
                                )
                            }
                        },
                        modifier = Modifier.width(164.dp),
                    )
                }
            }
        }

        if (state.isAdmin || state.people.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.isAdmin) "Danh sách nhân khẩu" else "Nhân khẩu trong hộ của bạn",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Hiển thị ${filteredList.size} / ${state.people.size} nhân khẩu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showAdvancedFilters = !showAdvancedFilters },
                                    modifier = Modifier.testTag("toggle_people_filters"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "Bộ lọc nâng cao",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (state.isAdmin) {
                                    OutlinedButton(
                                        onClick = onExportCsv,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("export_people_csv"),
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Xuất CSV")
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = filters.peopleSearch,
                            onValueChange = { q -> onUpdateFilters { it.copy(peopleSearch = q) } },
                            label = { Text("Tên, CCCD, SĐT, nghề nghiệp...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (filters.peopleSearch.isNotBlank()) {
                                    IconButton(onClick = { onUpdateFilters { it.copy(peopleSearch = "") } }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Xóa tìm kiếm")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("people_search_input"),
                        )

                        AnimatedVisibility(visible = showAdvancedFilters) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PortalDropdownField(
                                        label = "Cụm dân cư",
                                        selectedValue = filters.peopleCluster,
                                        options = listOf("") + DomainConstants.CLUSTERS,
                                        onSelected = { c -> onUpdateFilters { it.copy(peopleCluster = c) } },
                                        displayMapper = { if (it.isBlank()) "Tất cả cụm" else DomainConstants.shortClusterName(it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    PortalDropdownField(
                                        label = "Đảng viên",
                                        selectedValue = filters.peopleParty,
                                        options = listOf("", "yes", "no"),
                                        onSelected = { v -> onUpdateFilters { it.copy(peopleParty = v) } },
                                        displayMapper = {
                                            when (it) {
                                                "yes" -> "Đảng viên"
                                                "no" -> "Không"
                                                else -> "Tất cả"
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PortalDropdownField(
                                        label = "Học vấn",
                                        selectedValue = filters.peopleEdu,
                                        options = listOf("") + DomainConstants.EDUCATION_LEVELS,
                                        onSelected = { e -> onUpdateFilters { it.copy(peopleEdu = e) } },
                                        displayMapper = { if (it.isBlank()) "Mọi học vấn" else it },
                                        modifier = Modifier.weight(1f),
                                    )
                                    PortalDropdownField(
                                        label = "Chuyên môn",
                                        selectedValue = filters.peoplePro,
                                        options = listOf("") + DomainConstants.PROFESSION_LEVELS,
                                        onSelected = { pr -> onUpdateFilters { it.copy(peoplePro = pr) } },
                                        displayMapper = { if (it.isBlank()) "Mọi chuyên môn" else it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PortalDropdownField(
                                        label = "Tôn giáo",
                                        selectedValue = filters.peopleReligion,
                                        options = listOf("") + DomainConstants.RELIGIONS,
                                        onSelected = { r -> onUpdateFilters { it.copy(peopleReligion = r) } },
                                        displayMapper = { if (it.isBlank()) "Mọi tôn giáo" else it },
                                        modifier = Modifier.weight(1f),
                                    )
                                    PortalDropdownField(
                                        label = "Nghề nghiệp",
                                        selectedValue = filters.peopleJob,
                                        options = listOf("") + DomainConstants.JOBS,
                                        onSelected = { j -> onUpdateFilters { it.copy(peopleJob = j) } },
                                        displayMapper = { if (it.isBlank()) "Mọi nghề nghiệp" else it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                PortalDropdownField(
                                    label = "Nhóm tuổi",
                                    selectedValue = filters.peopleAge.name,
                                    options = AgeGroupFilter.entries.map { it.name },
                                    onSelected = { name ->
                                        val picked = AgeGroupFilter.entries.firstOrNull { it.name == name } ?: AgeGroupFilter.ALL
                                        onUpdateFilters { it.copy(peopleAge = picked, peopleMale18To27Only = false) }
                                    },
                                    displayMapper = { name ->
                                        AgeGroupFilter.entries.firstOrNull { it.name == name }?.label ?: "Mọi nhóm tuổi"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.isAdmin && state.people.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Link,
                    title = "Chưa có dữ liệu nhân khẩu được liên kết",
                    subtitle = "Vui lòng đăng nhập bằng SĐT / Mã định danh (CCCD) trong mục Hộ của tôi, hoặc gửi yêu cầu liên kết hộ đến Trưởng thôn.",
                    actionLabel = "Gửi yêu cầu liên kết hộ",
                    onAction = {
                        onOpenDialog(
                            ActiveDialog.ComplaintEditor(
                                defaultSubject = DomainConstants.LINK_REQUEST_SUBJECT,
                            )
                        )
                    },
                )
            }
        } else if (filteredList.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Groups,
                    title = "Không có nhân khẩu phù hợp",
                    subtitle = "Không tìm thấy nhân khẩu khớp với điều kiện lọc hiện tại.",
                    actionLabel = "Đặt lại bộ lọc",
                    onAction = onClearFilters,
                )
            }
        } else {
            items(filteredList, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    citizenAccount = citizenAccountsByPersonId[person.id],
                    isAdmin = state.isAdmin,
                    onEdit = { onOpenDialog(ActiveDialog.PersonEditor(person)) },
                    onResetPassword = { onOpenDialog(ActiveDialog.AdminResetCitizenPassword(person)) },
                    onDelete = { onOpenDialog(ActiveDialog.ConfirmDeletePerson(person)) },
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonDoc,
    citizenAccount: CitizenAccountDoc?,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    val ageVal = DateUtils.calculateAge(person.dob)
    val idText = "${person.cccd.ifBlank { "—" }} • ${person.phone.ifBlank { "—" }}"

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person_card_${person.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val semColors = PortalTheme.colors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${person.relation} • Hộ ${person.householdId} • ${DomainConstants.shortClusterName(person.cluster)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = person.residence,
                    backgroundColor = if (person.residence == "Thường trú") semColors.successBg else semColors.infoBg,
                    textColor = if (person.residence == "Thường trú") semColors.successText else semColors.infoText,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (person.cccd.isBlank()) {
                    StatusPill(
                        text = "Chờ bổ sung định danh",
                        backgroundColor = semColors.amberBg,
                        textColor = semColors.amberText,
                    )
                }
                if (person.partyMember) {
                    StatusPill(
                        text = "★ Đảng viên",
                        backgroundColor = semColors.amberBg,
                        textColor = semColors.amberText,
                    )
                }
                StatusPill(
                    text = "${person.gender} • ${ageVal?.let { "$it tuổi" } ?: "Chưa rõ tuổi"}",
                    backgroundColor = semColors.neutralPillBg,
                    textColor = semColors.neutralPillText,
                )
                if (isAdmin && citizenAccount != null) {
                    StatusPill(
                        text = if (citizenAccount.mustChangePassword) "MK mặc định (CCCD)" else "Đã đổi MK",
                        backgroundColor = if (citizenAccount.mustChangePassword) semColors.infoBg else semColors.successBg,
                        textColor = if (citizenAccount.mustChangePassword) semColors.infoText else semColors.successText,
                    )
                }
            }

            Text(
                text = "Ngày sinh: ${DateUtils.formatDateForDisplay(person.dob)} • Nghề nghiệp: ${person.job}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "CCCD / SĐT: $idText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Học vấn: ${person.education} • Chuyên môn: ${person.profession} • Tôn giáo: ${person.religion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (person.cccd.isNotBlank() || person.phone.isNotBlank()) {
                        TextButton(
                            onClick = onResetPassword,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("person_reset_pwd_${person.id}"),
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Đổi mật khẩu")
                        }
                    }
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sửa")
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Xóa", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
