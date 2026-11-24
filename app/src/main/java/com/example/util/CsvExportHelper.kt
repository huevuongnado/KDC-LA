package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.model.HouseholdDoc
import com.example.data.model.HouseholdSurveyDoc
import com.example.data.model.PersonDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CsvExportHelper {
    private fun escapeCsvCell(value: Any?): String {
        val raw = value?.toString() ?: ""
        return "\"${raw.replace("\"", "\"\"")}\""
    }

    private fun buildCsv(rows: List<List<Any?>>): String {
        val bom = "\uFEFF"
        return bom + rows.joinToString("\r\n") { row ->
            row.joinToString(",") { escapeCsvCell(it) }
        }
    }

    fun buildHouseholdsCsv(
        households: List<HouseholdDoc>,
        people: List<PersonDoc>,
        surveys: List<HouseholdSurveyDoc> = emptyList(),
    ): String {
        val surveysByHousehold = surveys.associateBy { it.householdId.ifBlank { it.id } }
        val header = listOf(
            "Mã hộ",
            "Chủ hộ",
            "Cụm dân cư",
            "Số nhân khẩu",
            "Diện hộ",
            "Tình trạng",
            "Địa chỉ",
            "Email thành viên liên kết",
            "Thu nhập (triệu/năm)",
            "Mức thu nhập",
            "Nguồn thu chính",
            "Đất sản xuất",
            "Xe máy",
            "Ô tô",
            "Xe tải",
            "Máy bơm",
            "Máy cày",
            "Máy xát lúa",
            "Máy cắt/phun",
            "Máy móc khác",
            "Bò",
            "Trâu",
            "Heo",
            "Gà",
            "Vịt",
            "Vật nuôi khác",
            "Loại nhà ở",
            "Nguồn nước",
            "Nhà tiêu HVS",
            "100% BHYT",
            "Internet",
            "Vấn đề / Ghi chú khác",
        )
        val rows = households.map { h ->
            val s = surveysByHousehold[h.id]
            listOf(
                h.id,
                h.head,
                h.cluster,
                people.count { it.householdId == h.id },
                h.classify,
                h.status,
                h.address,
                h.memberEmails.joinToString("; "),
                s?.annualIncomeMillion?.toString() ?: "",
                s?.incomeBracket ?: "",
                s?.mainIncomeSource ?: "",
                s?.farmLandNote ?: "",
                s?.motorbikes?.toString() ?: "",
                s?.cars?.toString() ?: "",
                s?.trucks?.toString() ?: "",
                s?.waterPumps?.toString() ?: "",
                s?.tractors?.toString() ?: "",
                s?.riceMills?.toString() ?: "",
                s?.sprayersOrCutters?.toString() ?: "",
                s?.otherEquipment ?: "",
                s?.cows?.toString() ?: "",
                s?.buffaloes?.toString() ?: "",
                s?.pigs?.toString() ?: "",
                s?.chickens?.toString() ?: "",
                s?.ducks?.toString() ?: "",
                s?.otherLivestock ?: "",
                s?.houseType ?: "",
                s?.waterSource ?: "",
                s?.let { if (it.hasCleanToilet) "Có" else "Chưa" } ?: "",
                s?.let { if (it.hasHealthInsuranceAll) "Có" else "Chưa" } ?: "",
                s?.let { if (it.internetConnected) "Có" else "Không" } ?: "",
                s?.otherNotes ?: "",
            )
        }
        return buildCsv(listOf(header) + rows)
    }

    fun buildPeopleCsv(people: List<PersonDoc>): String {
        val header = listOf(
            "Họ và tên",
            "Quan hệ",
            "Ngày sinh",
            "Tuổi",
            "Giới tính",
            "CCCD",
            "Điện thoại",
            "Mã hộ",
            "Cụm dân cư",
            "Cư trú",
            "Học vấn",
            "Chuyên môn",
            "Tôn giáo",
            "Đảng viên",
            "Nghề nghiệp",
        )
        val rows = people.map { p ->
            listOf(
                p.name,
                p.relation,
                p.dob,
                DateUtils.calculateAge(p.dob)?.toString() ?: "",
                p.gender,
                p.cccd,
                p.phone,
                p.householdId,
                p.cluster,
                p.residence,
                p.education,
                p.profession,
                p.religion,
                if (p.partyMember) "Có" else "Không",
                p.job,
            )
        }
        return buildCsv(listOf(header) + rows)
    }

    suspend fun writeCsvToUri(
        context: Context,
        uri: Uri,
        csvContent: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }
}
