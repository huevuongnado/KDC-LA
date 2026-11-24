package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.model.DomainConstants
import com.example.data.model.HouseholdDoc
import com.example.data.model.HouseholdSurveyDoc
import com.example.data.model.PersonDoc
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PortalDropdownField
import com.example.ui.components.StatusPill
import com.example.ui.theme.PortalTheme
import com.example.ui.viewmodel.ActiveDialog
import com.example.ui.viewmodel.FilterState
import com.example.ui.viewmodel.PortalUiState

@Composable
fun HouseholdsScreen(
    state: PortalUiState,
    onUpdateFilters: ((FilterState) -> FilterState) -> Unit,
    onClearFilters: () -> Unit,
    onOpenDialog: (ActiveDialog) -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilters by remember { mutableStateOf(false) }
    val filters = state.filters

    val totalCount = state.households.size
    val permanentCount = remember(state.households) {
        state.households.count { it.status == "Thường trú" }
    }
    val tempCount = remember(state.households) {
        state.households.count { it.status == "Tạm trú" }
    }
    val poorCount = remember(state.households) {
        state.households.count { it.classify == "Hộ nghèo" || it.classify == "Hộ cận nghèo" }
    }

    val memberCountsByHousehold = remember(state.people) {
        state.people.groupBy { it.householdId }
    }
    val surveysByHousehold = remember(state.householdSurveys) {
        state.householdSurveys.associateBy { it.householdId.ifBlank { it.id } }
    }

    val filteredHouseholds = remember(state.households, filters) {
        val term = filters.houseSearch.trim().lowercase()
        state.households.filter { h ->
            val matchesTerm = term.isBlank() ||
                "${h.id} ${h.head} ${h.address}".lowercase().contains(term)
            val matchesCluster = filters.houseCluster.isBlank() || h.cluster == filters.houseCluster
            val matchesStatus = filters.houseStatus.isBlank() || h.status == filters.houseStatus
            val matchesClassify = filters.houseClassify.isBlank() || h.classify == filters.houseClassify
            val matchesPoor = !filters.housePoorOnly ||
                h.classify == "Hộ nghèo" || h.classify == "Hộ cận nghèo"
            matchesTerm && matchesCluster && matchesStatus && matchesClassify && matchesPoor
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isAdmin || state.households.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiMetricCard(
                            label = if (state.isAdmin) "Tổng số hộ" else "Hộ đã liên kết",
                            value = totalCount.toString(),
                            icon = Icons.Filled.Home,
                            selected = filters.houseStatus.isBlank() && !filters.housePoorOnly,
                            onClick = { onClearFilters() },
                            modifier = Modifier.weight(1f),
                        )
                        KpiMetricCard(
                            label = "Thường trú",
                            value = permanentCount.toString(),
                            icon = Icons.Filled.Apartment,
                            selected = filters.houseStatus == "Thường trú" && !filters.housePoorOnly,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(
                                        houseStatus = if (it.houseStatus == "Thường trú") "" else "Thường trú",
                                        housePoorOnly = false,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiMetricCard(
                            label = "Tạm trú",
                            value = tempCount.toString(),
                            icon = Icons.Filled.Luggage,
                            selected = filters.houseStatus == "Tạm trú" && !filters.housePoorOnly,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(
                                        houseStatus = if (it.houseStatus == "Tạm trú") "" else "Tạm trú",
                                        housePoorOnly = false,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        KpiMetricCard(
                            label = "Nghèo / cận nghèo",
                            value = poorCount.toString(),
                            icon = Icons.Filled.VolunteerActivism,
                            selected = filters.housePoorOnly,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(
                                        housePoorOnly = !it.housePoorOnly,
                                        houseStatus = "",
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (state.isAdmin) {
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
                            Text(
                                text = "Danh sách hộ gia đình (${filteredHouseholds.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showFilters = !showFilters },
                                    modifier = Modifier.testTag("toggle_house_filters"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "Lọc danh sách hộ",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                OutlinedButton(
                                    onClick = onExportCsv,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("export_households_csv"),
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Xuất CSV")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = filters.houseSearch,
                            onValueChange = { q -> onUpdateFilters { it.copy(houseSearch = q) } },
                            label = { Text("Tìm mã hộ, chủ hộ...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (filters.houseSearch.isNotBlank()) {
                                    IconButton(onClick = { onUpdateFilters { it.copy(houseSearch = "") } }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Xóa tìm kiếm")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("house_search_input"),
                        )

                        AnimatedVisibility(visible = showFilters) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PortalDropdownField(
                                    label = "Cụm dân cư",
                                    selectedValue = filters.houseCluster,
                                    options = listOf("") + DomainConstants.CLUSTERS,
                                    onSelected = { c -> onUpdateFilters { it.copy(houseCluster = c) } },
                                    displayMapper = { if (it.isBlank()) "Tất cả cụm dân cư" else it },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PortalDropdownField(
                                        label = "Tình trạng",
                                        selectedValue = filters.houseStatus,
                                        options = listOf("") + DomainConstants.HOUSEHOLD_STATUSES,
                                        onSelected = { s -> onUpdateFilters { it.copy(houseStatus = s) } },
                                        displayMapper = { if (it.isBlank()) "Mọi tình trạng" else it },
                                        modifier = Modifier.weight(1f),
                                    )
                                    PortalDropdownField(
                                        label = "Diện hộ",
                                        selectedValue = filters.houseClassify,
                                        options = listOf("") + DomainConstants.HOUSEHOLD_CLASSIFICATIONS,
                                        onSelected = { cs -> onUpdateFilters { it.copy(houseClassify = cs, housePoorOnly = false) } },
                                        displayMapper = { if (it.isBlank()) "Mọi diện hộ" else it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.householdSurveys.isNotEmpty()) {
                item {
                    VillageSurveySummaryCard(surveys = state.householdSurveys)
                }
            }
        }

        if (!state.isAdmin && state.households.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Link,
                    title = "Chưa liên kết hộ gia đình",
                    subtitle = "Vui lòng đăng nhập bằng SĐT / Mã định danh (CCCD) trong mục Hộ của tôi, hoặc gửi yêu cầu kèm Mã hộ để Trưởng thôn duyệt.",
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
        } else if (filteredHouseholds.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.FamilyRestroom,
                    title = "Không tìm thấy hộ gia đình",
                    subtitle = "Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc cụm dân cư.",
                    actionLabel = "Xóa bộ lọc",
                    onAction = onClearFilters,
                )
            }
        } else {
            items(filteredHouseholds, key = { it.id }) { household ->
                val members = memberCountsByHousehold[household.id].orEmpty()
                val survey = surveysByHousehold[household.id]
                HouseholdCard(
                    household = household,
                    members = members,
                    survey = survey,
                    isAdmin = state.isAdmin,
                    onOpenSurvey = {
                        onOpenDialog(
                            ActiveDialog.HouseholdSurveyEditor(
                                household = household,
                                existing = survey,
                            )
                        )
                    },
                    onEdit = { onOpenDialog(ActiveDialog.HouseholdEditor(household)) },
                    onDelete = { onOpenDialog(ActiveDialog.ConfirmDeleteHousehold(household)) },
                )
            }
        }
    }
}

@Composable
private fun VillageSurveySummaryCard(surveys: List<HouseholdSurveyDoc>) {
    val totalCows = surveys.sumOf { it.cows }
    val totalBuffaloes = surveys.sumOf { it.buffaloes }
    val totalPigs = surveys.sumOf { it.pigs }
    val totalPoultry = surveys.sumOf { it.chickens + it.ducks }
    val totalMotorbikes = surveys.sumOf { it.motorbikes }
    val totalCarsAndTrucks = surveys.sumOf { it.cars + it.trucks }
    val totalMachines = surveys.sumOf { it.waterPumps + it.tractors + it.riceMills + it.sprayersOrCutters }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Agriculture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Tổng hợp kê khai toàn thôn (${surveys.size} hộ đã nhập)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Phương tiện & máy móc: $totalMotorbikes xe máy • $totalCarsAndTrucks ô tô/xe tải • $totalMachines máy sản xuất (bơm, cày, xát, phun...)",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Vật nuôi: $totalCows bò • $totalBuffaloes trâu • $totalPigs heo • $totalPoultry gia cầm (gà, vịt)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HouseholdCard(
    household: HouseholdDoc,
    members: List<PersonDoc>,
    survey: HouseholdSurveyDoc?,
    isAdmin: Boolean,
    onOpenSurvey: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(!isAdmin) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("household_card_${household.id}"),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    StatusPill(
                        text = household.id,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = household.head,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusPill(
                    text = "${members.size} nhân khẩu",
                    backgroundColor = semColors.infoBg,
                    textColor = semColors.infoText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DomainConstants.shortClusterName(household.cluster),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = household.status,
                    backgroundColor = if (household.status == "Thường trú") semColors.successBg else semColors.amberBg,
                    textColor = if (household.status == "Thường trú") semColors.successText else semColors.amberText,
                )
                if (household.classify.isNotBlank() && household.classify != "Không") {
                    StatusPill(
                        text = household.classify,
                        backgroundColor = semColors.amberBg,
                        textColor = semColors.amberText,
                    )
                }
            }

            if (household.address.isNotBlank()) {
                Text(
                    text = "Địa chỉ: ${household.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isAdmin && household.memberEmails.isNotEmpty()) {
                Text(
                    text = "Email liên kết: ${household.memberEmails.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = semColors.infoText,
                )
            }

            if (survey != null) {
                val incomeSummary = if (survey.annualIncomeMillion > 0) {
                    "${survey.annualIncomeMillion} tr/năm"
                } else {
                    survey.incomeBracket
                }
                val assetSummary = buildList {
                    if (survey.motorbikes > 0) add("${survey.motorbikes} xe máy")
                    val fourWheels = survey.cars + survey.trucks
                    if (fourWheels > 0) add("$fourWheels ô tô/tải")
                    val machines = survey.waterPumps + survey.tractors + survey.riceMills + survey.sprayersOrCutters
                    if (machines > 0) add("$machines máy NN")
                    if (survey.cows > 0) add("${survey.cows} bò")
                    if (survey.buffaloes > 0) add("${survey.buffaloes} trâu")
                    if (survey.pigs > 0) add("${survey.pigs} heo")
                    val poultry = survey.chickens + survey.ducks
                    if (poultry > 0) add("$poultry gà/vịt")
                }.joinToString(" • ")
                Text(
                    text = "Kê khai: $incomeSummary" + if (assetSummary.isNotBlank()) " • $assetSummary" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = semColors.successText,
                    fontWeight = FontWeight.Medium,
                )
            }

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "Ẩn thành viên" else "Thành viên (${members.size})")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onOpenSurvey,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_household_survey_${household.id}"),
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (survey == null) "Tự kê khai" else "Tài sản/Vật nuôi")
                    }
                    if (isAdmin) {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sửa")
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Xóa hộ ${household.id}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    if (members.isEmpty()) {
                        Text(
                            text = "Chưa có nhân khẩu nào trong hộ này.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        members.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${member.name} (${member.relation})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = member.residence,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
