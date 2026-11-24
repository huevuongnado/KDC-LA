package com.example.ui.dialogs

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DomainConstants
import com.example.data.model.HouseholdDoc
import com.example.data.model.InlineMemberDraft
import com.example.data.model.PersonAddMode
import com.example.data.model.PersonDoc
import com.example.data.model.PostDoc
import com.example.data.model.UserProfileDoc
import com.example.ui.components.PortalDateField
import com.example.ui.components.PortalDropdownField
import com.example.ui.theme.PortalTheme
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.launch

@Composable
fun PostEditorDialog(
    existing: PostDoc?,
    onCompressPostImage: suspend (Context, Uri) -> String?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: String,
        scope: String,
        cluster: String,
        content: String,
        imageData: String,
    ) -> Unit,
) {
    val context = LocalContext.current
    val scopeCoroutine = rememberCoroutineScope()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: DomainConstants.POST_CATEGORIES.first()) }
    var scope by remember {
        mutableStateOf(
            if (existing?.scope == "Theo cụm dân cư") "Theo cụm dân cư" else "Toàn thôn"
        )
    }
    var cluster by remember {
        mutableStateOf(
            existing?.cluster?.takeIf { it.isNotBlank() } ?: DomainConstants.CLUSTERS.first()
        )
    }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var imageData by remember { mutableStateOf(existing?.imageData ?: "") }
    val previewBitmap = remember(imageData) { ImageStorageHelper.decodeBase64Bitmap(imageData) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scopeCoroutine.launch {
                onCompressPostImage(context, uri)?.let { imageData = it }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (existing != null) "Chỉnh sửa thông báo" else "Đăng tin mới",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_title_input"),
                )

                PortalDropdownField(
                    label = "Chuyên mục",
                    selectedValue = category,
                    options = DomainConstants.POST_CATEGORIES,
                    onSelected = { category = it },
                )

                PortalDropdownField(
                    label = "Phạm vi gửi",
                    selectedValue = scope,
                    options = DomainConstants.POST_SCOPES,
                    onSelected = { scope = it },
                )

                if (scope == "Theo cụm dân cư") {
                    PortalDropdownField(
                        label = "Cụm dân cư",
                        selectedValue = cluster,
                        options = DomainConstants.CLUSTERS,
                        onSelected = { cluster = it },
                    )
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Nội dung thông báo") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_content_input"),
                )

                Text(
                    text = "Mẫu soạn nhanh",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        title = "Thông báo lịch họp thôn"
                        category = "Lịch họp thôn"
                        content = "Kính mời toàn thể bà con nhân dân tham dự cuộc họp thôn.\nThời gian: …\nĐịa điểm: …\nNội dung: …\nĐề nghị các hộ gia đình tham dự đầy đủ, đúng giờ."
                    }) {
                        Text("Mẫu lịch họp")
                    }
                    OutlinedButton(onClick = {
                        title = "Tuyên truyền thực hiện chính sách, pháp luật"
                        category = "Tuyên truyền chính sách"
                        content = "Ban điều hành thôn đề nghị bà con nhân dân quan tâm tìm hiểu, thực hiện nghiêm các chủ trương của Đảng, chính sách, pháp luật của Nhà nước; tích cực tham gia các phong trào tại địa phương."
                    }) {
                        Text("Mẫu tuyên truyền")
                    }
                    OutlinedButton(onClick = {
                        title = "Chủ động phòng, chống thiên tai"
                        category = "Phòng chống thiên tai"
                        content = "Đề nghị bà con thường xuyên theo dõi dự báo thời tiết; kiểm tra, gia cố nhà cửa; chủ động phương án bảo vệ người, tài sản, vật nuôi và thông báo kịp thời khi có tình huống khẩn cấp."
                    }) {
                        Text("Mẫu phòng chống thiên tai")
                    }
                }

                OutlinedButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageData.isBlank()) "Chọn ảnh đính kèm (tự động nén đồng bộ)" else "Thay đổi ảnh đính kèm")
                }

                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Ảnh bài đăng",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    TextButton(onClick = { imageData = "" }) {
                        Text("Gỡ ảnh")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(title, category, scope, cluster, content, imageData) },
                        modifier = Modifier.testTag("post_save_button"),
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
fun HouseholdEditorDialog(
    existing: HouseholdDoc?,
    suggestedId: String,
    knownUsers: List<UserProfileDoc>,
    onDismiss: () -> Unit,
    onSave: (
        id: String,
        head: String,
        cluster: String,
        status: String,
        classify: String,
        address: String,
        memberEmailsRaw: String,
        inlineMembers: List<InlineMemberDraft>,
    ) -> Unit,
) {
    val isEdit = existing != null
    var idInput by remember { mutableStateOf(existing?.id ?: suggestedId) }
    var head by remember { mutableStateOf(existing?.head ?: "") }
    var cluster by remember { mutableStateOf(existing?.cluster ?: DomainConstants.CLUSTERS.first()) }
    var status by remember { mutableStateOf(existing?.status ?: "Thường trú") }
    var classify by remember { mutableStateOf(existing?.classify ?: "Không") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var memberEmailsRaw by remember {
        mutableStateOf(existing?.memberEmails?.joinToString(", ") ?: "")
    }
    val inlineMembers = remember { mutableStateListOf<InlineMemberDraft>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (isEdit) "Cập nhật hộ gia đình" else "Thêm hộ gia đình mới",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = idInput,
                    onValueChange = { if (!isEdit) idInput = it },
                    readOnly = isEdit,
                    label = { Text("Mã hộ gia đình") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = head,
                    onValueChange = { head = it },
                    label = { Text("Tên chủ hộ") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("household_head_input"),
                )
                PortalDropdownField(
                    label = "Cụm dân cư",
                    selectedValue = cluster,
                    options = DomainConstants.CLUSTERS,
                    onSelected = { cluster = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PortalDropdownField(
                        label = "Tình trạng hộ",
                        selectedValue = status,
                        options = DomainConstants.HOUSEHOLD_STATUSES,
                        onSelected = { status = it },
                        modifier = Modifier.weight(1f),
                    )
                    PortalDropdownField(
                        label = "Diện hộ",
                        selectedValue = classify,
                        options = DomainConstants.HOUSEHOLD_CLASSIFICATIONS,
                        onSelected = { classify = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Địa chỉ / Xóm, tổ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = memberEmailsRaw,
                    onValueChange = { memberEmailsRaw = it },
                    label = { Text("Email Google bà con được xem hộ này") },
                    placeholder = { Text("vd: nguyenvana@gmail.com, tranthib@gmail.com") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                val candidateEmails = remember(knownUsers) {
                    knownUsers.map { it.email.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
                }
                if (candidateEmails.isNotEmpty()) {
                    Text(
                        text = "Chọn nhanh email đã đăng nhập:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        candidateEmails.take(8).forEach { email ->
                            AssistChip(
                                onClick = {
                                    val current = memberEmailsRaw
                                        .split(',', ';', ' ', '\n')
                                        .map { it.trim().lowercase() }
                                        .filter { it.isNotBlank() }
                                        .toMutableList()
                                    if (email !in current) {
                                        current.add(email)
                                        memberEmailsRaw = current.joinToString(", ")
                                    }
                                },
                                label = { Text(email) },
                            )
                        }
                    }
                }

                if (!isEdit) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Thêm thành viên ngay (không bắt buộc)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { inlineMembers.add(InlineMemberDraft()) }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thêm")
                        }
                    }

                    inlineMembers.forEachIndexed { idx, draft ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Thành viên #${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    IconButton(onClick = { inlineMembers.removeAt(idx) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Xóa dòng")
                                    }
                                }
                                OutlinedTextField(
                                    value = draft.name,
                                    onValueChange = { inlineMembers[idx] = draft.copy(name = it) },
                                    label = { Text("Họ tên thành viên") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                PortalDateField(
                                    label = "Ngày sinh",
                                    value = draft.dob,
                                    onValueChange = { inlineMembers[idx] = draft.copy(dob = it) },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PortalDropdownField(
                                        label = "Giới tính",
                                        selectedValue = draft.gender,
                                        options = listOf("Nam", "Nữ"),
                                        onSelected = { inlineMembers[idx] = draft.copy(gender = it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    PortalDropdownField(
                                        label = "Nghề nghiệp",
                                        selectedValue = draft.job,
                                        options = DomainConstants.JOBS,
                                        onSelected = { inlineMembers[idx] = draft.copy(job = it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                idInput,
                                head,
                                cluster,
                                status,
                                classify,
                                address,
                                memberEmailsRaw,
                                inlineMembers.toList(),
                            )
                        },
                        modifier = Modifier.testTag("household_save_button"),
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
fun PersonEditorDialog(
    existing: PersonDoc?,
    initialMode: PersonAddMode,
    households: List<HouseholdDoc>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        relation: String,
        dob: String,
        gender: String,
        cccd: String,
        phone: String,
        householdId: String,
        cluster: String,
        residence: String,
        education: String,
        profession: String,
        religion: String,
        partyMember: Boolean,
        job: String,
        tempStart: String,
        tempEnd: String,
        mode: PersonAddMode,
    ) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var relation by remember { mutableStateOf(existing?.relation ?: "Thành viên") }
    var dob by remember { mutableStateOf(existing?.dob ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "Nam") }
    var cccd by remember { mutableStateOf(existing?.cccd ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var householdId by remember {
        mutableStateOf(existing?.householdId ?: households.firstOrNull()?.id.orEmpty())
    }
    var cluster by remember {
        mutableStateOf(existing?.cluster ?: households.firstOrNull()?.cluster ?: DomainConstants.CLUSTERS.first())
    }
    var residence by remember {
        mutableStateOf(existing?.residence ?: "Thường trú")
    }
    var education by remember {
        mutableStateOf(existing?.education ?: "Chưa đi học")
    }
    var profession by remember {
        mutableStateOf(existing?.profession ?: "Chưa qua đào tạo")
    }
    var religion by remember {
        mutableStateOf(existing?.religion ?: "Không")
    }
    var job by remember {
        mutableStateOf(existing?.job ?: "Làm nông / Trồng rừng")
    }
    var partyMember by remember {
        mutableStateOf(existing?.partyMember ?: false)
    }
    var tempStart by remember { mutableStateOf(existing?.tempStart ?: "") }
    var tempEnd by remember { mutableStateOf(existing?.tempEnd ?: "") }

    val householdOptions = remember(households) {
        households.map { it.id }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (existing != null) "Cập nhật nhân khẩu" else "Thêm nhân khẩu mới",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PersonAddMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = {
                                mode = m
                                if (m == PersonAddMode.TEMPORARY) {
                                    residence = "Tạm trú"
                                }
                            },
                            label = { Text(m.label) },
                        )
                    }
                }

                val semColors = PortalTheme.colors
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = semColors.infoBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val hint = when (mode) {
                        PersonAddMode.NEWBORN -> "Chế độ trẻ mới sinh: có thể bỏ trống CCCD; hệ thống gắn nhãn chờ bổ sung định danh."
                        PersonAddMode.TEMPORARY -> "Chế độ tạm trú: vui lòng điền ngày bắt đầu và ngày hết hạn tạm trú."
                        PersonAddMode.NORMAL -> "Thêm nhân khẩu hoặc ghi nhận trường hợp chuyển đến."
                    }
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = semColors.infoText,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Họ và tên") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("person_name_input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = relation,
                        onValueChange = { relation = it },
                        label = { Text("Quan hệ với chủ hộ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    PortalDropdownField(
                        label = "Giới tính",
                        selectedValue = gender,
                        options = DomainConstants.GENDERS,
                        onSelected = { gender = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                PortalDateField(
                    label = "Ngày sinh",
                    value = dob,
                    onValueChange = { dob = it },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = cccd,
                        onValueChange = { cccd = it },
                        label = { Text("Số CCCD / Định danh") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Số điện thoại") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (householdOptions.isNotEmpty()) {
                    PortalDropdownField(
                        label = "Hộ gia đình",
                        selectedValue = householdId,
                        options = householdOptions,
                        onSelected = { id ->
                            householdId = id
                            households.firstOrNull { it.id == id }?.let { cluster = it.cluster }
                        },
                        displayMapper = { id ->
                            val h = households.firstOrNull { it.id == id }
                            if (h != null) "${h.id} – ${h.head}" else id
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PortalDropdownField(
                        label = "Cụm dân cư",
                        selectedValue = cluster,
                        options = DomainConstants.CLUSTERS,
                        onSelected = { cluster = it },
                        displayMapper = { DomainConstants.shortClusterName(it) },
                        modifier = Modifier.weight(1f),
                    )
                    PortalDropdownField(
                        label = "Tình trạng cư trú",
                        selectedValue = residence,
                        options = DomainConstants.RESIDENCE_STATUSES,
                        onSelected = { residence = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PortalDropdownField(
                        label = "Trình độ học vấn",
                        selectedValue = education,
                        options = DomainConstants.EDUCATION_LEVELS,
                        onSelected = { education = it },
                        modifier = Modifier.weight(1f),
                    )
                    PortalDropdownField(
                        label = "Trình độ chuyên môn",
                        selectedValue = profession,
                        options = DomainConstants.PROFESSION_LEVELS,
                        onSelected = { profession = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PortalDropdownField(
                        label = "Tôn giáo",
                        selectedValue = religion,
                        options = DomainConstants.RELIGIONS,
                        onSelected = { religion = it },
                        modifier = Modifier.weight(1f),
                    )
                    PortalDropdownField(
                        label = "Nghề nghiệp",
                        selectedValue = job,
                        options = DomainConstants.JOBS,
                        onSelected = { job = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { partyMember = !partyMember }
                        .minimumInteractiveComponentSize(),
                ) {
                    Checkbox(
                        checked = partyMember,
                        onCheckedChange = { partyMember = it },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Là Đảng viên", style = MaterialTheme.typography.bodyMedium)
                }

                if (mode == PersonAddMode.TEMPORARY || residence == "Tạm trú" || tempStart.isNotBlank() || tempEnd.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PortalDateField(
                            label = "Bắt đầu tạm trú",
                            value = tempStart,
                            onValueChange = { tempStart = it },
                            modifier = Modifier.weight(1f),
                        )
                        PortalDateField(
                            label = "Hết hạn tạm trú",
                            value = tempEnd,
                            onValueChange = { tempEnd = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                name,
                                relation,
                                dob,
                                gender,
                                cccd,
                                phone,
                                householdId,
                                cluster,
                                residence,
                                education,
                                profession,
                                religion,
                                partyMember,
                                job,
                                tempStart,
                                tempEnd,
                                mode,
                            )
                        },
                        modifier = Modifier.testTag("person_save_button"),
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
fun SplitHouseholdDialog(
    households: List<HouseholdDoc>,
    people: List<PersonDoc>,
    suggestedId: String,
    onDismiss: () -> Unit,
    onConfirmSplit: (
        newId: String,
        newHead: String,
        cluster: String,
        classify: String,
        selectedIds: List<String>,
    ) -> Unit,
) {
    var sourceId by remember { mutableStateOf(households.firstOrNull()?.id.orEmpty()) }
    var newId by remember { mutableStateOf(suggestedId) }
    var newHead by remember { mutableStateOf("") }
    var cluster by remember { mutableStateOf(DomainConstants.CLUSTERS.first()) }
    var classify by remember { mutableStateOf("Không") }
    val selectedMemberIds = remember { mutableStateListOf<String>() }

    val sourceMembers = remember(sourceId, people) {
        people.filter { it.householdId == sourceId }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tách hộ gia đình",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                if (households.isNotEmpty()) {
                    PortalDropdownField(
                        label = "Chọn hộ gốc",
                        selectedValue = sourceId,
                        options = households.map { it.id },
                        onSelected = { pickedId ->
                            sourceId = pickedId
                            selectedMemberIds.clear()
                        },
                        displayMapper = { id ->
                            val h = households.firstOrNull { it.id == id }
                            if (h != null) "${h.id} – ${h.head}" else id
                        },
                    )
                }

                Text(
                    text = "Chọn thành viên tách sang hộ mới",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sourceMembers.isEmpty()) {
                    Text(
                        text = "Hộ chưa có thành viên.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sourceMembers.forEach { member ->
                        val checked = member.id in selectedMemberIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedMemberIds.remove(member.id)
                                    else selectedMemberIds.add(member.id)
                                }
                                .minimumInteractiveComponentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedMemberIds.add(member.id)
                                    else selectedMemberIds.remove(member.id)
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${member.name} (${member.relation})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = newId,
                    onValueChange = { newId = it },
                    label = { Text("Mã hộ mới (để trống tự sinh)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newHead,
                    onValueChange = { newHead = it },
                    label = { Text("Tên chủ hộ mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PortalDropdownField(
                        label = "Cụm dân cư",
                        selectedValue = cluster,
                        options = DomainConstants.CLUSTERS,
                        onSelected = { cluster = it },
                        displayMapper = { DomainConstants.shortClusterName(it) },
                        modifier = Modifier.weight(1f),
                    )
                    PortalDropdownField(
                        label = "Diện hộ",
                        selectedValue = classify,
                        options = DomainConstants.HOUSEHOLD_CLASSIFICATIONS,
                        onSelected = { classify = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirmSplit(
                                newId,
                                newHead,
                                cluster,
                                classify,
                                selectedMemberIds.toList(),
                            )
                        },
                        modifier = Modifier.testTag("confirm_split_button"),
                    ) {
                        Text("Tách hộ")
                    }
                }
            }
        }
    }
}
