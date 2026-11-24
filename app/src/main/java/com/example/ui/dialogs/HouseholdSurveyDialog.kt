package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DomainConstants
import com.example.data.model.HouseholdDoc
import com.example.data.model.HouseholdSurveyDoc
import com.example.ui.components.PortalDropdownField

@Composable
fun HouseholdSurveyDialog(
    household: HouseholdDoc,
    existing: HouseholdSurveyDoc?,
    onDismiss: () -> Unit,
    onSave: (HouseholdSurveyDoc) -> Unit,
) {
    var annualIncomeText by remember {
        mutableStateOf(
            existing?.annualIncomeMillion?.takeIf { it > 0 }?.toString() ?: ""
        )
    }
    var incomeBracket by remember {
        mutableStateOf(existing?.incomeBracket?.ifBlank { DomainConstants.INCOME_BRACKETS[1] } ?: DomainConstants.INCOME_BRACKETS[1])
    }
    var mainIncomeSource by remember {
        mutableStateOf(existing?.mainIncomeSource?.ifBlank { DomainConstants.MAIN_INCOME_SOURCES.first() } ?: DomainConstants.MAIN_INCOME_SOURCES.first())
    }
    var farmLandNote by remember { mutableStateOf(existing?.farmLandNote.orEmpty()) }

    var motorbikes by remember { mutableIntStateOf(existing?.motorbikes ?: 0) }
    var cars by remember { mutableIntStateOf(existing?.cars ?: 0) }
    var trucks by remember { mutableIntStateOf(existing?.trucks ?: 0) }
    var waterPumps by remember { mutableIntStateOf(existing?.waterPumps ?: 0) }
    var tractors by remember { mutableIntStateOf(existing?.tractors ?: 0) }
    var riceMills by remember { mutableIntStateOf(existing?.riceMills ?: 0) }
    var sprayersOrCutters by remember { mutableIntStateOf(existing?.sprayersOrCutters ?: 0) }
    var otherEquipment by remember { mutableStateOf(existing?.otherEquipment.orEmpty()) }

    var cows by remember { mutableIntStateOf(existing?.cows ?: 0) }
    var buffaloes by remember { mutableIntStateOf(existing?.buffaloes ?: 0) }
    var pigs by remember { mutableIntStateOf(existing?.pigs ?: 0) }
    var chickens by remember { mutableIntStateOf(existing?.chickens ?: 0) }
    var ducks by remember { mutableIntStateOf(existing?.ducks ?: 0) }
    var otherLivestock by remember { mutableStateOf(existing?.otherLivestock.orEmpty()) }

    var houseType by remember {
        mutableStateOf(existing?.houseType?.ifBlank { DomainConstants.HOUSE_TYPES.first() } ?: DomainConstants.HOUSE_TYPES.first())
    }
    var waterSource by remember {
        mutableStateOf(existing?.waterSource?.ifBlank { DomainConstants.WATER_SOURCES.first() } ?: DomainConstants.WATER_SOURCES.first())
    }
    var hasCleanToilet by remember { mutableStateOf(existing?.hasCleanToilet ?: true) }
    var hasHealthInsuranceAll by remember { mutableStateOf(existing?.hasHealthInsuranceAll ?: true) }
    var internetConnected by remember { mutableStateOf(existing?.internetConnected ?: true) }
    var otherNotes by remember { mutableStateOf(existing?.otherNotes.orEmpty()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Kê khai thông tin hộ gia đình",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Hộ ${household.id} • Chủ hộ: ${household.head} (${DomainConstants.shortClusterName(household.cluster)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                // 1. Thu nhập & Đất sản xuất
                SurveySectionHeader(
                    icon = Icons.Filled.AttachMoney,
                    title = "1. Thu nhập & Đất sản xuất",
                )

                OutlinedTextField(
                    value = annualIncomeText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        annualIncomeText = digits
                        val parsed = digits.toIntOrNull()
                        if (parsed != null) {
                            incomeBracket = when {
                                parsed < 30 -> DomainConstants.INCOME_BRACKETS[0]
                                parsed <= 70 -> DomainConstants.INCOME_BRACKETS[1]
                                parsed <= 150 -> DomainConstants.INCOME_BRACKETS[2]
                                parsed <= 300 -> DomainConstants.INCOME_BRACKETS[3]
                                else -> DomainConstants.INCOME_BRACKETS[4]
                            }
                        }
                    },
                    label = { Text("Tổng thu nhập ước tính (triệu đồng/năm)") },
                    placeholder = { Text("Ví dụ: 85") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("survey_income_input"),
                )

                PortalDropdownField(
                    label = "Mức thu nhập hằng năm",
                    selectedValue = incomeBracket,
                    options = DomainConstants.INCOME_BRACKETS,
                    onSelected = { incomeBracket = it },
                )

                PortalDropdownField(
                    label = "Nguồn thu nhập chính",
                    selectedValue = mainIncomeSource,
                    options = DomainConstants.MAIN_INCOME_SOURCES,
                    onSelected = { mainIncomeSource = it },
                )

                OutlinedTextField(
                    value = farmLandNote,
                    onValueChange = { farmLandNote = it },
                    label = { Text("Đất ruộng / Đất rẫy / Rừng keo (nếu có)") },
                    placeholder = { Text("Ví dụ: 3 sào ruộng, 1.5 ha keo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // 2. Phương tiện & Máy móc sản xuất
                SurveySectionHeader(
                    icon = Icons.Filled.Agriculture,
                    title = "2. Phương tiện & Máy móc sản xuất",
                )

                SurveyCounterRow(
                    label = "Xe máy",
                    value = motorbikes,
                    maxVal = 100,
                    testTagPrefix = "survey_motorbikes",
                    onValueChange = { motorbikes = it },
                )
                SurveyCounterRow(
                    label = "Ô tô con",
                    value = cars,
                    maxVal = 50,
                    testTagPrefix = "survey_cars",
                    onValueChange = { cars = it },
                )
                SurveyCounterRow(
                    label = "Xe tải / Công nông",
                    value = trucks,
                    maxVal = 50,
                    testTagPrefix = "survey_trucks",
                    onValueChange = { trucks = it },
                )
                SurveyCounterRow(
                    label = "Máy bơm nước",
                    value = waterPumps,
                    maxVal = 100,
                    testTagPrefix = "survey_water_pumps",
                    onValueChange = { waterPumps = it },
                )
                SurveyCounterRow(
                    label = "Máy cày / Máy kéo",
                    value = tractors,
                    maxVal = 50,
                    testTagPrefix = "survey_tractors",
                    onValueChange = { tractors = it },
                )
                SurveyCounterRow(
                    label = "Máy xát lúa / Tuốt lúa",
                    value = riceMills,
                    maxVal = 50,
                    testTagPrefix = "survey_rice_mills",
                    onValueChange = { riceMills = it },
                )
                SurveyCounterRow(
                    label = "Máy cắt cỏ / Bình phun thuốc",
                    value = sprayersOrCutters,
                    maxVal = 100,
                    testTagPrefix = "survey_sprayers",
                    onValueChange = { sprayersOrCutters = it },
                )

                OutlinedTextField(
                    value = otherEquipment,
                    onValueChange = { otherEquipment = it },
                    label = { Text("Máy móc / phương tiện khác") },
                    placeholder = { Text("Ví dụ: máy phát điện, xe lôi, máy cưa...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // 3. Chăn nuôi & Vật nuôi
                SurveySectionHeader(
                    icon = Icons.Filled.Pets,
                    title = "3. Số lượng vật nuôi",
                )

                SurveyCounterRow(
                    label = "Bò",
                    value = cows,
                    maxVal = 5000,
                    testTagPrefix = "survey_cows",
                    onValueChange = { cows = it },
                )
                SurveyCounterRow(
                    label = "Trâu",
                    value = buffaloes,
                    maxVal = 5000,
                    testTagPrefix = "survey_buffaloes",
                    onValueChange = { buffaloes = it },
                )
                SurveyCounterRow(
                    label = "Heo (Lợn)",
                    value = pigs,
                    maxVal = 10000,
                    testTagPrefix = "survey_pigs",
                    onValueChange = { pigs = it },
                )
                SurveyCounterRow(
                    label = "Gà",
                    value = chickens,
                    maxVal = 100000,
                    step = 5,
                    testTagPrefix = "survey_chickens",
                    onValueChange = { chickens = it },
                )
                SurveyCounterRow(
                    label = "Vịt / Ngan",
                    value = ducks,
                    maxVal = 100000,
                    step = 5,
                    testTagPrefix = "survey_ducks",
                    onValueChange = { ducks = it },
                )

                OutlinedTextField(
                    value = otherLivestock,
                    onValueChange = { otherLivestock = it },
                    label = { Text("Vật nuôi khác") },
                    placeholder = { Text("Ví dụ: 5 con dê, 2 ao cá, 4 thùng ong...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // 4. Nhà ở, Đời sống & Vấn đề khác
                SurveySectionHeader(
                    icon = Icons.Filled.HomeWork,
                    title = "4. Nhà ở, Sinh hoạt & Vấn đề khác",
                )

                PortalDropdownField(
                    label = "Loại nhà ở hiện nay",
                    selectedValue = houseType,
                    options = DomainConstants.HOUSE_TYPES,
                    onSelected = { houseType = it },
                )

                PortalDropdownField(
                    label = "Nguồn nước sinh hoạt chính",
                    selectedValue = waterSource,
                    options = DomainConstants.WATER_SOURCES,
                    onSelected = { waterSource = it },
                )

                SurveySwitchRow(
                    label = "Có nhà tiêu hợp vệ sinh",
                    checked = hasCleanToilet,
                    onCheckedChange = { hasCleanToilet = it },
                )
                SurveySwitchRow(
                    label = "100% thành viên có thẻ BHYT",
                    checked = hasHealthInsuranceAll,
                    onCheckedChange = { hasHealthInsuranceAll = it },
                )
                SurveySwitchRow(
                    label = "Gia đình có kết nối Internet / Wi-Fi",
                    checked = internetConnected,
                    onCheckedChange = { internetConnected = it },
                )

                OutlinedTextField(
                    value = otherNotes,
                    onValueChange = { otherNotes = it },
                    label = { Text("Các vấn đề khác / Đề xuất hỗ trợ của gia đình") },
                    placeholder = { Text("Nhập thông tin bổ sung về sản xuất, vay vốn, nước sạch, đường đi, dịch bệnh...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("survey_other_notes_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Đóng")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                HouseholdSurveyDoc(
                                    id = household.id,
                                    householdId = household.id,
                                    cluster = household.cluster,
                                    headName = household.head,
                                    annualIncomeMillion = (annualIncomeText.toIntOrNull() ?: 0).coerceIn(0, 100_000),
                                    incomeBracket = incomeBracket,
                                    mainIncomeSource = mainIncomeSource,
                                    farmLandNote = farmLandNote.trim(),
                                    motorbikes = motorbikes.coerceIn(0, 100),
                                    cars = cars.coerceIn(0, 50),
                                    trucks = trucks.coerceIn(0, 50),
                                    waterPumps = waterPumps.coerceIn(0, 100),
                                    tractors = tractors.coerceIn(0, 50),
                                    riceMills = riceMills.coerceIn(0, 50),
                                    sprayersOrCutters = sprayersOrCutters.coerceIn(0, 100),
                                    otherEquipment = otherEquipment.trim(),
                                    cows = cows.coerceIn(0, 5000),
                                    buffaloes = buffaloes.coerceIn(0, 5000),
                                    pigs = pigs.coerceIn(0, 10000),
                                    chickens = chickens.coerceIn(0, 100000),
                                    ducks = ducks.coerceIn(0, 100000),
                                    otherLivestock = otherLivestock.trim(),
                                    houseType = houseType,
                                    waterSource = waterSource,
                                    hasCleanToilet = hasCleanToilet,
                                    hasHealthInsuranceAll = hasHealthInsuranceAll,
                                    internetConnected = internetConnected,
                                    otherNotes = otherNotes.trim(),
                                )
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_household_survey_button"),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lưu thông tin hộ")
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveySectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SurveyCounterRow(
    label: String,
    value: Int,
    maxVal: Int,
    step: Int = 1,
    testTagPrefix: String,
    onValueChange: (Int) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { onValueChange((value - step).coerceAtLeast(0)) },
                    enabled = value > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_minus"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Giảm $label",
                    )
                }
                OutlinedTextField(
                    value = value.toString(),
                    onValueChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onValueChange(parsed.coerceIn(0, maxVal))
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(74.dp)
                        .testTag("${testTagPrefix}_input"),
                )
                IconButton(
                    onClick = { onValueChange((value + step).coerceAtMost(maxVal)) },
                    enabled = value < maxVal,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_plus"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Tăng $label",
                    )
                }
            }
        }
    }
}

@Composable
private fun SurveySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
