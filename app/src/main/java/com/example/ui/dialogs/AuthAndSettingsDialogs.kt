package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.BuildConfig
import com.example.data.model.AdminRecordDoc
import com.example.data.model.DomainConstants
import com.example.data.model.PersonDoc
import com.example.data.model.PortalSettings
import com.example.data.model.UserProfileDoc
import com.example.data.model.UserSession
import com.example.ui.components.PortalDropdownField
import com.example.ui.components.StatusPill
import com.example.ui.theme.PortalTheme
import com.example.ui.theme.PortalThemePreset
import com.example.util.DateUtils
import com.example.util.PasswordHashHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private data class DelegatedAccountItem(
    val uid: String,
    val email: String,
    val displayName: String,
    val roleTitle: String,
    val isBootstrap: Boolean,
)

private enum class PresenceFilter { ALL, ONLINE, OFFLINE }

@Composable
fun AccountAndRolesDialog(
    session: UserSession?,
    admins: List<AdminRecordDoc>,
    knownUsers: List<UserProfileDoc>,
    initialFilterOnlineOnly: Boolean = false,
    onDismiss: () -> Unit,
    onGrantAdmin: (email: String, roleTitle: String) -> Unit,
    onUpdateAdminRoleTitle: (uid: String, email: String, displayName: String, roleTitle: String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onOpenChangeOwnPassword: () -> Unit,
    onOpenCitizenLogin: () -> Unit,
    onLogoutCitizen: () -> Unit,
    onLogout: () -> Unit,
) {
    var targetEmail by remember { mutableStateOf("") }
    var selectedRoleTitle by remember { mutableStateOf(DomainConstants.OFFICIAL_ROLE_TITLES.first()) }
    var editingRoleTarget by remember { mutableStateOf<DelegatedAccountItem?>(null) }
    var editingRoleValue by remember { mutableStateOf(DomainConstants.OFFICIAL_ROLE_TITLES.first()) }
    var presenceFilter by remember(initialFilterOnlineOnly) {
        mutableStateOf(if (initialFilterOnlineOnly) PresenceFilter.ONLINE else PresenceFilter.ALL)
    }
    var userSearchQuery by remember { mutableStateOf("") }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val semColors = PortalTheme.colors

    val delegatedAccounts = remember(admins, knownUsers, session) {
        val bootstrapEmail = DomainConstants.BOOTSTRAP_ADMIN_EMAIL.lowercase()
        val bootstrapDoc = admins.firstOrNull { it.email.trim().lowercase() == bootstrapEmail }
        val bootstrapUser = knownUsers.firstOrNull { it.email.trim().lowercase() == bootstrapEmail }
        val bootstrapUid = bootstrapDoc?.userId?.takeIf { it.isNotBlank() }
            ?: bootstrapUser?.userId?.takeIf { it.isNotBlank() }
            ?: if (session?.email?.trim()?.lowercase() == bootstrapEmail) session.id else ""
        val bootstrapItem = DelegatedAccountItem(
            uid = bootstrapUid,
            email = DomainConstants.BOOTSTRAP_ADMIN_EMAIL,
            displayName = bootstrapDoc?.displayName?.takeIf { it.isNotBlank() }
                ?: bootstrapUser?.name?.takeIf { it.isNotBlank() }
                ?: "Quản trị gốc (Thôn Long An)",
            roleTitle = bootstrapDoc?.roleTitle?.takeIf { it.isNotBlank() } ?: "Trưởng thôn",
            isBootstrap = true,
        )
        val otherAdmins = admins
            .filter { it.email.trim().lowercase() != bootstrapEmail }
            .sortedWith(compareBy<AdminRecordDoc> { it.roleTitle }.thenBy { it.email })
            .map { admin ->
                val matchedUser = knownUsers.firstOrNull { u ->
                    u.userId == admin.userId || u.email.equals(admin.email, ignoreCase = true)
                }
                DelegatedAccountItem(
                    uid = admin.userId,
                    email = admin.email,
                    displayName = admin.displayName.takeIf { it.isNotBlank() }
                        ?: matchedUser?.name?.takeIf { it.isNotBlank() }
                        ?: admin.email.substringBefore("@"),
                    roleTitle = admin.roleTitle.takeIf { it.isNotBlank() } ?: "Trưởng thôn",
                    isBootstrap = false,
                )
            }
        listOf(bootstrapItem) + otherAdmins
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tài khoản & Phân quyền",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                }

                if (session != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = session.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = session.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(
                                    text = if (session.isAdmin && session.roleTitle.isNotBlank()) {
                                        session.roleTitle
                                    } else {
                                        session.role.displayLabel
                                    },
                                    backgroundColor = if (session.isAdmin) semColors.amberBg else semColors.infoBg,
                                    textColor = if (session.isAdmin) semColors.amberText else semColors.infoText,
                                )
                                if (session.householdId.isNotBlank()) {
                                    StatusPill(
                                        text = "Hộ ${session.householdId}",
                                        backgroundColor = semColors.successBg,
                                        textColor = semColors.successText,
                                    )
                                }
                            }
                        }
                    }
                }

                if (session?.isAdmin == true) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Danh sách tài khoản đã được phân quyền (${delegatedAccounts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    delegatedAccounts.forEach { item ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (item.isBootstrap) {
                                            StatusPill(
                                                text = "Quản trị gốc",
                                                backgroundColor = semColors.infoBg,
                                                textColor = semColors.infoText,
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val matchedProfile = knownUsers.firstOrNull { u ->
                                        (item.uid.isNotBlank() && u.userId == item.uid) ||
                                            u.email.equals(item.email, ignoreCase = true)
                                    }
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        StatusPill(
                                            text = item.roleTitle,
                                            backgroundColor = semColors.amberBg,
                                            textColor = semColors.amberText,
                                        )
                                        if (matchedProfile != null) {
                                            val isOfficialOnline = DateUtils.isOnlineActive(
                                                matchedProfile.online,
                                                matchedProfile.lastActiveAt?.toDate()?.time,
                                                nowMillis,
                                            )
                                            StatusPill(
                                                text = if (isOfficialOnline) "● Đang online" else "Ngoại tuyến",
                                                backgroundColor = if (isOfficialOnline) semColors.successBg else semColors.neutralPillBg,
                                                textColor = if (isOfficialOnline) semColors.successText else semColors.neutralPillText,
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.uid.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                editingRoleTarget = item
                                                editingRoleValue = item.roleTitle.takeIf {
                                                    it in DomainConstants.OFFICIAL_ROLE_TITLES
                                                } ?: DomainConstants.OFFICIAL_ROLE_TITLES.first()
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Đổi chức danh ${item.email}",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    if (!item.isBootstrap && item.uid.isNotBlank()) {
                                        IconButton(
                                            onClick = { onRevokeAdmin(item.uid) },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Thu hồi quyền ${item.email}",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text(
                        text = "Cấp quyền Ban chi ủy, Ban thôn & Chi hội đoàn thể",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Nên phân quyền cho Bí thư Chi bộ, Trưởng thôn, Phó thôn, Trưởng Ban công tác Mặt trận và các Chi hội trưởng (Phụ nữ, Nông dân, Cựu chiến binh, Thanh niên, Người cao tuổi...). Người được cấp cần mở app và đăng nhập Google 1 lần trước.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val nonAdminUsers = remember(knownUsers, delegatedAccounts) {
                        val existingAdminEmails = delegatedAccounts.map { it.email.trim().lowercase() }.toSet()
                        knownUsers.filter { u ->
                            u.email.isNotBlank() && u.email.trim().lowercase() !in existingAdminEmails
                        }
                    }
                    if (nonAdminUsers.isNotEmpty()) {
                        val quickOptions = remember(nonAdminUsers) {
                            listOf("") + nonAdminUsers.map { it.email }
                        }
                        PortalDropdownField(
                            label = "Chọn nhanh từ tài khoản đã đăng nhập",
                            selectedValue = if (targetEmail in nonAdminUsers.map { it.email }) targetEmail else "",
                            options = quickOptions,
                            displayMapper = { email ->
                                if (email.isBlank()) {
                                    "-- Chọn cán bộ đã đăng nhập --"
                                } else {
                                    val user = nonAdminUsers.firstOrNull { it.email == email }
                                    if (user != null) "${user.name} (${user.email})" else email
                                }
                            },
                            onSelected = { pickedEmail ->
                                if (pickedEmail.isNotBlank()) {
                                    targetEmail = pickedEmail
                                }
                            },
                        )
                    }

                    OutlinedTextField(
                        value = targetEmail,
                        onValueChange = { targetEmail = it },
                        label = { Text("Email Google cần cấp quyền") },
                        placeholder = { Text("canbo@gmail.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_admin_email_input"),
                    )
                    PortalDropdownField(
                        label = "Chức danh phân quyền",
                        selectedValue = selectedRoleTitle,
                        options = DomainConstants.OFFICIAL_ROLE_TITLES,
                        onSelected = { selectedRoleTitle = it },
                    )
                    Button(
                        onClick = {
                            onGrantAdmin(targetEmail, selectedRoleTitle)
                            targetEmail = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("grant_admin_button"),
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cấp quyền quản trị")
                    }

                    val citizenUsers = remember(knownUsers, delegatedAccounts, nowMillis) {
                        val adminEmails = delegatedAccounts.map { it.email.trim().lowercase() }.toSet()
                        knownUsers.filter {
                            it.role != "admin" &&
                                (it.email.isNotBlank() || it.linkedPersonId.isNotBlank() || it.name.isNotBlank()) &&
                                it.email.trim().lowercase() !in adminEmails
                        }.sortedWith(
                            compareByDescending<UserProfileDoc> { u ->
                                DateUtils.isOnlineActive(u.online, u.lastActiveAt?.toDate()?.time, nowMillis)
                            }.thenByDescending { u ->
                                u.lastActiveAt?.toDate()?.time ?: u.updatedAt?.toDate()?.time ?: 0L
                            }.thenBy { it.name }
                        )
                    }
                    val onlineCitizenCount = remember(citizenUsers, nowMillis) {
                        citizenUsers.count { u ->
                            DateUtils.isOnlineActive(u.online, u.lastActiveAt?.toDate()?.time, nowMillis)
                        }
                    }
                    val offlineCitizenCount = (citizenUsers.size - onlineCitizenCount).coerceAtLeast(0)
                    val filteredCitizenUsers = remember(citizenUsers, presenceFilter, userSearchQuery, nowMillis) {
                        val q = userSearchQuery.trim().lowercase()
                        citizenUsers.filter { u ->
                            val isOnline = DateUtils.isOnlineActive(u.online, u.lastActiveAt?.toDate()?.time, nowMillis)
                            val matchesPresence = when (presenceFilter) {
                                PresenceFilter.ALL -> true
                                PresenceFilter.ONLINE -> isOnline
                                PresenceFilter.OFFLINE -> !isOnline
                            }
                            val matchesQuery = q.isBlank() ||
                                u.name.lowercase().contains(q) ||
                                u.email.lowercase().contains(q) ||
                                u.phone.lowercase().contains(q) ||
                                u.householdId.lowercase().contains(q)
                            matchesPresence && matchesQuery
                        }
                    }

                    HorizontalDivider()
                    Text(
                        text = "Trạng thái đăng nhập của bà con (${citizenUsers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Đang online: $onlineCitizenCount • Ngoại tuyến: $offlineCitizenCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = presenceFilter == PresenceFilter.ALL,
                            onClick = { presenceFilter = PresenceFilter.ALL },
                            label = { Text("Tất cả (${citizenUsers.size})") },
                        )
                        FilterChip(
                            selected = presenceFilter == PresenceFilter.ONLINE,
                            onClick = { presenceFilter = PresenceFilter.ONLINE },
                            label = { Text("Đang online ($onlineCitizenCount)") },
                        )
                        FilterChip(
                            selected = presenceFilter == PresenceFilter.OFFLINE,
                            onClick = { presenceFilter = PresenceFilter.OFFLINE },
                            label = { Text("Ngoại tuyến ($offlineCitizenCount)") },
                        )
                    }
                    if (citizenUsers.size > 4) {
                        OutlinedTextField(
                            value = userSearchQuery,
                            onValueChange = { userSearchQuery = it },
                            label = { Text("Tìm bà con (tên, hộ, SĐT, email)") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (filteredCitizenUsers.isEmpty()) {
                        Text(
                            text = when (presenceFilter) {
                                PresenceFilter.ONLINE -> "Hiện chưa có bà con nào đang mở ứng dụng."
                                PresenceFilter.OFFLINE -> "Không có tài khoản ngoại tuyến phù hợp."
                                PresenceFilter.ALL -> "Chưa có bà con nào đăng nhập ứng dụng."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        filteredCitizenUsers.forEach { u ->
                            val lastActiveMs = u.lastActiveAt?.toDate()?.time ?: u.updatedAt?.toDate()?.time
                            val isOnline = DateUtils.isOnlineActive(
                                u.online,
                                u.lastActiveAt?.toDate()?.time,
                                nowMillis,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = u.name.ifBlank { u.email.ifBlank { "Cư dân" } },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusPill(
                                            text = if (isOnline) "● Đang online" else "Ngoại tuyến",
                                            backgroundColor = if (isOnline) semColors.successBg else semColors.neutralPillBg,
                                            textColor = if (isOnline) semColors.successText else semColors.neutralPillText,
                                        )
                                    }
                                    val contactLine = listOfNotNull(
                                        u.householdId.takeIf { it.isNotBlank() }?.let { "Hộ $it" } ?: "Chưa liên kết hộ",
                                        u.phone.takeIf { it.isNotBlank() },
                                        u.email.takeIf { it.isNotBlank() },
                                    ).joinToString(" • ")
                                    Text(
                                        text = contactLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = if (isOnline) {
                                            "Đang sử dụng ứng dụng"
                                        } else {
                                            DateUtils.formatRelativeActivity(lastActiveMs, nowMillis)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOnline) semColors.successText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (session != null && !session.isAdmin) {
                    HorizontalDivider()
                    if (session.hasCitizenLogin) {
                        Button(
                            onClick = onOpenChangeOwnPassword,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("dialog_change_password_button"),
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Đổi mật khẩu tài khoản bà con")
                        }
                        OutlinedButton(
                            onClick = onLogoutCitizen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("dialog_logout_citizen_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Đăng xuất tài khoản bà con (SĐT / CCCD)")
                        }
                    } else {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenCitizenLogin()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("dialog_open_citizen_login_button"),
                        ) {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Đăng nhập SĐT / Mã định danh")
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Phiên bản đang cài: v${BuildConfig.VERSION_NAME} (mã ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onLogout()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Đăng xuất tài khoản Google")
                }
            }
        }
    }

    editingRoleTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editingRoleTarget = null },
            title = { Text("Cập nhật chức danh") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${target.displayName} (${target.email})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PortalDropdownField(
                        label = "Chức danh phân quyền",
                        selectedValue = editingRoleValue,
                        options = DomainConstants.OFFICIAL_ROLE_TITLES,
                        onSelected = { editingRoleValue = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAdminRoleTitle(
                            target.uid,
                            target.email,
                            target.displayName,
                            editingRoleValue,
                        )
                        editingRoleTarget = null
                    },
                ) {
                    Text("Lưu chức danh")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRoleTarget = null }) {
                    Text("Hủy")
                }
            },
        )
    }
}

@Composable
fun ChangeOwnPasswordDialog(
    citizenName: String,
    mustChangePassword: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String) -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Đổi mật khẩu tài khoản",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                }

                if (citizenName.isNotBlank()) {
                    Text(
                        text = "Tài khoản: $citizenName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (mustChangePassword) {
                    val semColors = PortalTheme.colors
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = semColors.amberBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Bạn đang dùng mật khẩu mặc định (Mã định danh / CCCD hoặc mật khẩu do Trưởng thôn cấp). Hãy đặt mật khẩu mới dễ nhớ để bảo vệ thông tin hộ gia đình.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semColors.amberText,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                val transformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
                val toggleIcon = if (showPasswords) Icons.Filled.VisibilityOff else Icons.Filled.Visibility

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        localError = null
                    },
                    label = { Text("Mật khẩu hiện tại (hoặc Mã định danh lần đầu)") },
                    visualTransformation = transformation,
                    trailingIcon = {
                        IconButton(onClick = { showPasswords = !showPasswords }) {
                            Icon(toggleIcon, contentDescription = "Ẩn/hiện mật khẩu")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pwd_current_input"),
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text("Mật khẩu mới (tối thiểu 6 ký tự)") },
                    visualTransformation = transformation,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pwd_new_input"),
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Nhập lại mật khẩu mới") },
                    visualTransformation = transformation,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pwd_confirm_input"),
                )

                if (!localError.isNullOrBlank()) {
                    Text(
                        text = localError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (mustChangePassword) "Để sau" else "Hủy")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val err = when {
                                currentPassword.trim().isBlank() -> "Vui lòng nhập mật khẩu hiện tại."
                                else -> PasswordHashHelper.validateNewPassword(newPassword)
                                    ?: if (newPassword.trim() != confirmPassword.trim()) {
                                        "Mật khẩu nhập lại chưa khớp."
                                    } else null
                            }
                            if (err != null) {
                                localError = err
                            } else {
                                onSubmit(currentPassword, newPassword)
                            }
                        },
                        modifier = Modifier.testTag("change_pwd_submit_button"),
                    ) {
                        Text("Lưu mật khẩu mới")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminResetCitizenPasswordDialog(
    person: PersonDoc,
    onDismiss: () -> Unit,
    onSaveCustomPassword: (newPassword: String) -> Unit,
    onResetToCccd: () -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Đổi / Cấp lại mật khẩu bà con",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Hộ ${person.householdId} • SĐT: ${person.phone.ifBlank { "Chưa có" }} • CCCD: ${person.cccd.ifBlank { "Chưa có" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (person.cccd.isNotBlank()) {
                    FilledTonalButton(
                        onClick = onResetToCccd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_reset_cccd_button"),
                    ) {
                        Icon(Icons.Filled.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Đặt lại mật khẩu về Mã định danh (${person.cccd})")
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Hoặc nhập mật khẩu mới tùy chọn",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                val transformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
                val toggleIcon = if (showPasswords) Icons.Filled.VisibilityOff else Icons.Filled.Visibility

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text("Mật khẩu mới cho bà con (tối thiểu 6 ký tự)") },
                    visualTransformation = transformation,
                    trailingIcon = {
                        IconButton(onClick = { showPasswords = !showPasswords }) {
                            Icon(toggleIcon, contentDescription = "Ẩn/hiện mật khẩu")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_new_pwd_input"),
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Xác nhận mật khẩu mới") },
                    visualTransformation = transformation,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_confirm_pwd_input"),
                )

                if (!localError.isNullOrBlank()) {
                    Text(
                        text = localError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                            val err = PasswordHashHelper.validateNewPassword(newPassword)
                                ?: if (newPassword.trim() != confirmPassword.trim()) {
                                    "Mật khẩu xác nhận chưa khớp."
                                } else null
                            if (err != null) {
                                localError = err
                            } else {
                                onSaveCustomPassword(newPassword)
                            }
                        },
                        modifier = Modifier.testTag("admin_save_pwd_button"),
                    ) {
                        Text("Đổi mật khẩu")
                    }
                }
            }
        }
    }
}

@Composable
fun ComplaintDialog(
    defaultSenderName: String,
    defaultSubject: String,
    defaultHouseholdId: String,
    onDismiss: () -> Unit,
    onSubmit: (senderName: String, subject: String, content: String, householdId: String) -> Unit,
) {
    var senderName by remember { mutableStateOf(defaultSenderName) }
    var subject by remember { mutableStateOf(defaultSubject.ifBlank { DomainConstants.COMPLAINT_SUBJECTS.first() }) }
    var householdId by remember { mutableStateOf(defaultHouseholdId) }
    var content by remember {
        mutableStateOf(
            if (defaultSubject == DomainConstants.LINK_REQUEST_SUBJECT) {
                "Đề nghị Ban điều hành thôn liên kết tài khoản Google của tôi vào hộ gia đình để theo dõi thông tin nhân khẩu."
            } else ""
        )
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
                    text = "Gửi phản ánh đến Ban điều hành thôn",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Họ và tên") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PortalDropdownField(
                    label = "Loại phản ánh",
                    selectedValue = subject,
                    options = DomainConstants.COMPLAINT_SUBJECTS,
                    onSelected = { subject = it },
                )
                OutlinedTextField(
                    value = householdId,
                    onValueChange = { householdId = it.uppercase() },
                    label = { Text("Mã hộ gia đình (nếu biết, vd: LA001)") },
                    placeholder = { Text("Ví dụ: LA001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Nội dung phản ánh") },
                    placeholder = { Text("Mô tả cụ thể nội dung cần phản ánh hoặc đề nghị liên kết hộ...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complaint_content_input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(senderName, subject, content, householdId) },
                        modifier = Modifier.testTag("complaint_submit_button"),
                    ) {
                        Text("Gửi phản ánh")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    initialSettings: PortalSettings,
    onCompressImage: suspend (android.content.Context, android.net.Uri, String, Int) -> String?,
    onDismiss: () -> Unit,
    onSave: (PortalSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialSettings.title) }
    var slogan by remember { mutableStateOf(initialSettings.slogan) }
    var themeKey by remember { mutableStateOf(initialSettings.themeKey) }
    var fontSize by remember { mutableIntStateOf(initialSettings.fontSize) }
    var logoPath by remember { mutableStateOf(initialSettings.logoPath) }
    var coverPath by remember { mutableStateOf(initialSettings.coverPath) }
    var latestVersionCodeInput by remember {
        mutableStateOf(
            initialSettings.latestVersionCode.coerceAtLeast(BuildConfig.VERSION_CODE).toString()
        )
    }
    var latestVersionName by remember {
        mutableStateOf(initialSettings.latestVersionName.ifBlank { BuildConfig.VERSION_NAME })
    }
    var updateUrl by remember { mutableStateOf(initialSettings.updateUrl) }
    var updateMessage by remember { mutableStateOf(initialSettings.updateMessage) }
    var forceUpdate by remember { mutableStateOf(initialSettings.forceUpdate) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onCompressImage(context, uri, "logo", 500)?.let { logoPath = it }
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onCompressImage(context, uri, "cover", 1200)?.let { coverPath = it }
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
                    text = "Cài đặt giao diện & cổng thông tin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên đơn vị / tiêu đề (đồng bộ toàn thôn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = slogan,
                    onValueChange = { slogan = it },
                    label = { Text("Nội dung khẩu hiệu chạy ngang (đồng bộ toàn thôn)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Bảng màu chủ đề",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PortalThemePreset.entries.forEach { preset ->
                        val selected = themeKey == preset.key
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(preset.primary)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { themeKey = preset.key },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = preset.label,
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                PortalDropdownField(
                    label = "Cỡ chữ toàn trang",
                    selectedValue = fontSize.toString(),
                    options = DomainConstants.FONT_SIZES.map { it.toString() },
                    onSelected = { fontSize = it.toIntOrNull() ?: 15 },
                    displayMapper = { "$it pt" },
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Logo thôn (máy này)", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chọn logo")
                        }
                        if (logoPath.isNotBlank() && File(logoPath).exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(File(logoPath)),
                                contentDescription = "Xem trước logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            TextButton(onClick = { logoPath = "" }) {
                                Text("Gỡ logo")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Ảnh nền / Ảnh bìa (máy này)", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(
                            onClick = {
                                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chọn ảnh nền")
                        }
                        if (coverPath.isNotBlank() && File(coverPath).exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(File(coverPath)),
                                contentDescription = "Xem trước ảnh nền",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            TextButton(onClick = { coverPath = "" }) {
                                Text("Gỡ ảnh nền")
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Yêu cầu cập nhật ứng dụng cho bà con",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "Phiên bản đang cài trên máy bạn: v${BuildConfig.VERSION_NAME} (mã ${BuildConfig.VERSION_CODE}). Khi tăng mã phiên bản mới và điền liên kết tải (VD: link GitHub Releases), bà con mở app sẽ tự động nhận được thông báo cập nhật ngay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = latestVersionCodeInput,
                        onValueChange = { input ->
                            latestVersionCodeInput = input.filter { it.isDigit() }
                        },
                        label = { Text("Mã phiên bản mới") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = latestVersionName,
                        onValueChange = { latestVersionName = it },
                        label = { Text("Tên bản (VD: 1.2)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val currentTarget = latestVersionCodeInput.toIntOrNull() ?: BuildConfig.VERSION_CODE
                        val nextCode = maxOf(currentTarget, BuildConfig.VERSION_CODE) + 1
                        latestVersionCodeInput = nextCode.toString()
                        if (latestVersionName.isBlank() || latestVersionName == BuildConfig.VERSION_NAME) {
                            latestVersionName = "1.$nextCode"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tăng +1 mã phiên bản mới để nhắc bà con cập nhật")
                }

                OutlinedTextField(
                    value = updateUrl,
                    onValueChange = { updateUrl = it },
                    label = { Text("Liên kết tải bản cập nhật (https://...)") },
                    placeholder = { Text("https://github.com/ten-tai-khoan/KDC-LA/releases/latest") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_update_url_input"),
                )

                OutlinedTextField(
                    value = updateMessage,
                    onValueChange = { updateMessage = it },
                    label = { Text("Nội dung thay đổi / nâng cấp") },
                    placeholder = { Text("VD: Bổ sung khảo sát hộ, trạng thái online, sửa lỗi đăng nhập...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bắt buộc cập nhật mới dùng tiếp",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Nếu bật, bà con phải bấm cập nhật mới vào tiếp ứng dụng.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = forceUpdate,
                        onCheckedChange = { forceUpdate = it },
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
                            onSave(
                                PortalSettings(
                                    title = title.trim(),
                                    slogan = slogan.trim(),
                                    themeKey = themeKey,
                                    fontSize = fontSize,
                                    logoPath = logoPath,
                                    coverPath = coverPath,
                                    latestVersionCode = (latestVersionCodeInput.toIntOrNull() ?: 1).coerceAtLeast(1),
                                    latestVersionName = latestVersionName.trim(),
                                    updateUrl = updateUrl.trim(),
                                    updateMessage = updateMessage.trim(),
                                    forceUpdate = forceUpdate,
                                )
                            )
                        },
                        modifier = Modifier.testTag("settings_save_button"),
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateRequiredDialog(
    settings: PortalSettings,
    isAdmin: Boolean,
    onDismissForSession: () -> Unit,
) {
    val context = LocalContext.current
    var copiedLink by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf<String?>(null) }
    val mustWaitForUpdate = settings.forceUpdate && !isAdmin
    val semColors = PortalTheme.colors

    AlertDialog(
        onDismissRequest = {
            if (!mustWaitForUpdate) onDismissForSession()
        },
        properties = DialogProperties(
            dismissOnBackPress = !mustWaitForUpdate,
            dismissOnClickOutside = !mustWaitForUpdate,
        ),
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = if (mustWaitForUpdate) {
                    "Yêu cầu cập nhật phiên bản mới"
                } else {
                    "Đã có phiên bản ứng dụng mới"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = semColors.infoBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Phiên bản hiện tại: v${BuildConfig.VERSION_NAME} (mã ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = semColors.infoText,
                        )
                        val targetLabel = settings.latestVersionName.ifBlank { "${settings.latestVersionCode}.0" }
                        Text(
                            text = "Phiên bản mới nhất: v$targetLabel (mã ${settings.latestVersionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = semColors.infoText,
                        )
                    }
                }

                Text(
                    text = settings.updateMessage.ifBlank {
                        "Ban điều hành thôn vừa nâng cấp ứng dụng với các tính năng và cải tiến mới. Vui lòng tải và cài đặt bản mới để đồng bộ đầy đủ."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!launchError.isNullOrBlank()) {
                    Text(
                        text = launchError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Link cập nhật", settings.updateUrl))
                        copiedLink = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copiedLink) "Đã sao chép liên kết tải" else "Sao chép liên kết tải")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetUrl = settings.updateUrl.trim()
                    if (targetUrl.startsWith("https://", ignoreCase = true) ||
                        targetUrl.startsWith("http://", ignoreCase = true)
                    ) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            launchError = "Không mở được trình duyệt. Vui lòng sao chép liên kết bên trên để tải."
                        }
                    } else {
                        launchError = "Liên kết tải chưa hợp lệ."
                    }
                },
                modifier = Modifier.testTag("update_now_button"),
            ) {
                Text("Tải & Cập nhật ngay")
            }
        },
        dismissButton = if (!mustWaitForUpdate) {
            {
                TextButton(
                    onClick = onDismissForSession,
                    modifier = Modifier.testTag("update_later_button"),
                ) {
                    Text(if (isAdmin) "Đóng (Quản trị)" else "Để sau")
                }
            }
        } else null,
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    isDestructive: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}
