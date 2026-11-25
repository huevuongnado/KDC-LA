package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.data.model.PendingCitizenCredentials
import com.example.data.repository.CommunityRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CitizenAuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.AdminActionStrip
import com.example.ui.components.NewsScreen
import com.example.ui.components.PortalHeader
import com.example.ui.dialogs.AccountAndRolesDialog
import com.example.ui.dialogs.AdminResetCitizenPasswordDialog
import com.example.ui.dialogs.AppUpdateRequiredDialog
import com.example.ui.dialogs.ChangeOwnPasswordDialog
import com.example.ui.dialogs.ComplaintDialog
import com.example.ui.dialogs.ConfirmActionDialog
import com.example.ui.dialogs.HouseholdEditorDialog
import com.example.ui.dialogs.HouseholdSurveyDialog
import com.example.ui.dialogs.PersonEditorDialog
import com.example.ui.dialogs.PostEditorDialog
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.dialogs.SplitHouseholdDialog
import com.example.ui.screens.HouseholdsScreen
import com.example.ui.screens.MineScreen
import com.example.ui.screens.PeopleScreen
import com.example.ui.theme.ThonLongAnTheme
import com.example.ui.viewmodel.ActiveDialog
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PortalTab
import com.example.ui.viewmodel.PortalUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var pendingCitizenLogin by remember { mutableStateOf<PendingCitizenCredentials?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        ThonLongAnTheme {
            AuthScreen(
                onAuthSuccess = { pending ->
                    if (pending != null) {
                        pendingCitizenLogin = pending
                    }
                    currentUser = Firebase.auth.currentUser
                },
                onPendingCitizenChanged = { pending ->
                    pendingCitizenLogin = pending
                },
            )
        }
    } else {
        val uid = currentUser!!.uid
        val email = currentUser?.email.orEmpty()
        val displayName = currentUser?.displayName.orEmpty()
        val appContext = LocalContext.current.applicationContext

        key(uid) {
            val viewModel: MainViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        MainViewModel(
                            repository = CommunityRepository(appContext),
                            currentUserId = uid,
                            currentUserEmail = email,
                            currentUserName = displayName,
                        )
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uid, pendingCitizenLogin, uiState.isReady) {
                val pending = pendingCitizenLogin
                if (pending != null && uiState.isReady) {
                    pendingCitizenLogin = null
                    if (uiState.isAdmin) {
                        viewModel.notifyMessage("Đã đăng nhập bằng tài khoản Trưởng thôn.")
                    } else {
                        viewModel.loginCitizen(pending.identifier, pending.password)
                    }
                }
            }

            ThonLongAnTheme(
                themeKey = uiState.settings.themeKey,
                baseFontSize = uiState.settings.fontSize,
            ) {
                ThonLongAnApp(
                    uiState = uiState,
                    viewModel = viewModel,
                    hasPendingCitizenLogin = pendingCitizenLogin != null,
                    onClearPendingCitizen = { pendingCitizenLogin = null },
                )
            }
        }
    }
}

private data class NavTabMeta(
    val tab: PortalTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val NAV_TABS = listOf(
    NavTabMeta(PortalTab.NEWS, Icons.Filled.Campaign, Icons.Outlined.Campaign),
    NavTabMeta(PortalTab.HOUSEHOLDS, Icons.Filled.Home, Icons.Outlined.Home),
    NavTabMeta(PortalTab.PEOPLE, Icons.Filled.Groups, Icons.Outlined.Groups),
    NavTabMeta(PortalTab.MINE, Icons.Filled.PersonPin, Icons.Outlined.PersonPin),
)

@Composable
private fun ThonLongAnApp(
    uiState: PortalUiState,
    viewModel: MainViewModel,
    hasPendingCitizenLogin: Boolean = false,
    onClearPendingCitizen: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val csvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.writePendingCsvToUri(context, uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = uiState.activeDialog != ActiveDialog.None) {
        viewModel.closeDialog()
    }

    BackHandler(
        enabled = uiState.activeDialog == ActiveDialog.None && uiState.currentTab != PortalTab.NEWS
    ) {
        viewModel.selectTab(PortalTab.NEWS)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setUserPresence(true)
                Lifecycle.Event.ON_STOP -> viewModel.setUserPresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(60_000L)
                viewModel.setUserPresence(true)
            }
        }
    }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.isAdmin) {
        if (uiState.isAdmin) {
            while (isActive) {
                nowMillis = System.currentTimeMillis()
                delay(20_000L)
            }
        }
    }
    val onlineUsersCount = remember(uiState.knownUsers, nowMillis) {
        uiState.knownUsers.count { u ->
            DateUtils.isOnlineActive(u.online, u.lastActiveAt?.toDate()?.time, nowMillis)
        }
    }

    val performLogout: () -> Unit = {
        onClearPendingCitizen()
        scope.launch {
            viewModel.markOfflineBeforeSignOut()
            signOut(
                credentialManager = credentialManager,
                scope = scope,
            )
        }
    }

    if (hasPendingCitizenLogin && !uiState.isReady) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Đang kết nối và xác thực tài khoản bà con...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    var dismissedUpdateVersion by rememberSaveable { mutableIntStateOf(0) }
    val updateUrlClean = uiState.settings.updateUrl.trim()
    val hasValidUpdateUrl = updateUrlClean.startsWith("https://", ignoreCase = true) ||
        updateUrlClean.startsWith("http://", ignoreCase = true)
    val shouldShowUpdatePrompt = uiState.isReady &&
        uiState.settings.latestVersionCode > BuildConfig.VERSION_CODE &&
        hasValidUpdateUrl &&
        ((uiState.settings.forceUpdate && !uiState.isAdmin) ||
            dismissedUpdateVersion < uiState.settings.latestVersionCode)

    if (uiState.requiresCitizenLogin) {
        CitizenAuthScreen(
            settings = uiState.settings,
            googleEmail = uiState.session?.email.orEmpty(),
            isLoading = uiState.citizenLoginBusy,
            errorMessage = uiState.citizenLoginError,
            onLoginSubmit = { identifier, pwd -> viewModel.loginCitizen(identifier, pwd) },
            onClearError = { viewModel.clearCitizenLoginError() },
            onSkipToNews = { viewModel.skipCitizenLogin() },
            onSwitchGoogleAccount = performLogout,
        )
        if (shouldShowUpdatePrompt) {
            AppUpdateRequiredDialog(
                settings = uiState.settings,
                isAdmin = uiState.isAdmin,
                onDismissForSession = {
                    dismissedUpdateVersion = uiState.settings.latestVersionCode
                },
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        NAV_TABS.forEach { item ->
                            val selected = uiState.currentTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.tab.label,
                                    )
                                },
                                label = { Text(item.tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${item.tab.route}"),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (uiState.isAdmin) {
                    val fabConfig = when (uiState.currentTab) {
                        PortalTab.NEWS -> "Đăng tin mới" to { viewModel.openDialog(ActiveDialog.PostEditor()) }
                        PortalTab.HOUSEHOLDS -> "Thêm hộ mới" to { viewModel.openDialog(ActiveDialog.HouseholdEditor()) }
                        PortalTab.PEOPLE -> "Thêm nhân khẩu" to { viewModel.openDialog(ActiveDialog.PersonEditor()) }
                        PortalTab.MINE -> null
                    }
                    if (fabConfig != null) {
                        ExtendedFloatingActionButton(
                            onClick = fabConfig.second,
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(fabConfig.first) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("admin_fab_${uiState.currentTab.route}"),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        NAV_TABS.forEach { item ->
                            val selected = uiState.currentTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.tab.label,
                                    )
                                },
                                label = { Text(item.tab.label) },
                                modifier = Modifier.testTag("rail_tab_${item.tab.route}"),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    PortalHeader(
                        settings = uiState.settings,
                        session = uiState.session,
                        onAccountClick = { viewModel.openDialog(ActiveDialog.AccountAndRoles()) },
                        onLogoutClick = performLogout,
                    )

                    if (uiState.isAdmin) {
                        AdminActionStrip(
                            onlineUsersCount = onlineUsersCount,
                            onOpenOnlineUsers = {
                                viewModel.openDialog(ActiveDialog.AccountAndRoles(initialFilterOnlineOnly = true))
                            },
                            onOpenSplit = { viewModel.openDialog(ActiveDialog.SplitHousehold) },
                            onOpenSettings = { viewModel.openDialog(ActiveDialog.SettingsEditor) },
                            onOpenRoles = { viewModel.openDialog(ActiveDialog.AccountAndRoles()) },
                            onRestoreSample = { viewModel.openDialog(ActiveDialog.ConfirmRestoreSample) },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (!uiState.isReady) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            val contentModifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 960.dp)

                            when (uiState.currentTab) {
                                PortalTab.NEWS -> NewsScreen(
                                    state = uiState,
                                    onSelectCategory = { cat ->
                                        viewModel.updateFilters { it.copy(newsCategory = cat) }
                                    },
                                    onTogglePin = { post -> viewModel.togglePinPost(post) },
                                    onOpenDialog = { dialog -> viewModel.openDialog(dialog) },
                                    onNotify = { msg -> viewModel.notifyMessage(msg) },
                                    modifier = contentModifier,
                                )
                                PortalTab.HOUSEHOLDS -> HouseholdsScreen(
                                    state = uiState,
                                    onUpdateFilters = { transform -> viewModel.updateFilters(transform) },
                                    onClearFilters = { viewModel.clearHouseholdFilters() },
                                    onOpenDialog = { dialog -> viewModel.openDialog(dialog) },
                                    onExportCsv = {
                                        viewModel.prepareHouseholdsCsv()
                                        csvDocumentLauncher.launch("ho-gia-dinh-long-an.csv")
                                    },
                                    modifier = contentModifier,
                                )
                                PortalTab.PEOPLE -> PeopleScreen(
                                    state = uiState,
                                    onUpdateFilters = { transform -> viewModel.updateFilters(transform) },
                                    onClearFilters = { viewModel.clearPeopleFilters() },
                                    onOpenDialog = { dialog -> viewModel.openDialog(dialog) },
                                    onExportCsv = {
                                        viewModel.preparePeopleCsv()
                                        csvDocumentLauncher.launch("nhan-khau-long-an.csv")
                                    },
                                    modifier = contentModifier,
                                )
                                PortalTab.MINE -> MineScreen(
                                    state = uiState,
                                    onOpenDialog = { dialog -> viewModel.openDialog(dialog) },
                                    onToggleComplaintStatus = { c -> viewModel.toggleComplaintStatus(c) },
                                    onApproveHouseholdLink = { c -> viewModel.approveHouseholdLink(c) },
                                    onOpenCitizenLogin = { viewModel.openCitizenLoginPrompt() },
                                    onLogoutCitizen = { viewModel.logoutCitizen() },
                                    modifier = contentModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val dialog = uiState.activeDialog) {
        ActiveDialog.None -> Unit

        is ActiveDialog.AccountAndRoles -> AccountAndRolesDialog(
            session = uiState.session,
            admins = uiState.admins,
            knownUsers = uiState.knownUsers,
            initialFilterOnlineOnly = dialog.initialFilterOnlineOnly,
            onDismiss = { viewModel.closeDialog() },
            onGrantAdmin = { email, roleTitle -> viewModel.grantAdminByEmail(email, roleTitle) },
            onUpdateAdminRoleTitle = { uid, email, name, roleTitle ->
                viewModel.updateAdminRoleTitle(uid, email, name, roleTitle)
            },
            onRevokeAdmin = { targetUid -> viewModel.revokeAdmin(targetUid) },
            onOpenChangeOwnPassword = { viewModel.openDialog(ActiveDialog.ChangeOwnPassword) },
            onOpenCitizenLogin = { viewModel.openCitizenLoginPrompt() },
            onLogoutCitizen = { viewModel.logoutCitizen() },
            onLogout = performLogout,
        )

        ActiveDialog.ChangeOwnPassword -> ChangeOwnPasswordDialog(
            citizenName = uiState.session?.name.orEmpty(),
            mustChangePassword = uiState.session?.mustChangePassword == true,
            onDismiss = { viewModel.closeDialog() },
            onSubmit = { currentPwd, newPwd ->
                viewModel.changeOwnPassword(currentPwd, newPwd)
            },
        )

        is ActiveDialog.AdminResetCitizenPassword -> AdminResetCitizenPasswordDialog(
            person = dialog.person,
            onDismiss = { viewModel.closeDialog() },
            onSaveCustomPassword = { newPwd ->
                viewModel.adminSetCitizenPassword(
                    person = dialog.person,
                    newPassword = newPwd,
                    resetToDefaultCccd = false,
                )
            },
            onResetToCccd = {
                viewModel.adminSetCitizenPassword(
                    person = dialog.person,
                    newPassword = "",
                    resetToDefaultCccd = true,
                )
            },
        )

        is ActiveDialog.PostEditor -> PostEditorDialog(
            existing = dialog.post,
            onCompressPostImage = { ctx, uri ->
                viewModel.compressPostImageBase64(ctx, uri)
            },
            onDismiss = { viewModel.closeDialog() },
            onSave = { title, category, scopeStr, cluster, content, imageData ->
                viewModel.savePost(dialog.post, title, category, scopeStr, cluster, content, imageData)
            },
        )

        is ActiveDialog.HouseholdEditor -> HouseholdEditorDialog(
            existing = dialog.household,
            suggestedId = uiState.nextHouseholdId(),
            knownUsers = uiState.knownUsers,
            onDismiss = { viewModel.closeDialog() },
            onSave = { id, head, cluster, status, classify, address, memberEmailsRaw, inlineMembers ->
                viewModel.saveHousehold(
                    existing = dialog.household,
                    idInput = id,
                    head = head,
                    cluster = cluster,
                    status = status,
                    classify = classify,
                    address = address,
                    memberEmailsRaw = memberEmailsRaw,
                    inlineMembers = inlineMembers,
                )
            },
        )

        is ActiveDialog.HouseholdSurveyEditor -> HouseholdSurveyDialog(
            household = dialog.household,
            existing = dialog.existing,
            onDismiss = { viewModel.closeDialog() },
            onSave = { draft ->
                viewModel.saveHouseholdSurvey(
                    household = dialog.household,
                    existing = dialog.existing,
                    draft = draft,
                )
            },
        )

        is ActiveDialog.PersonEditor -> PersonEditorDialog(
            existing = dialog.person,
            initialMode = dialog.mode,
            households = uiState.households,
            onDismiss = { viewModel.closeDialog() },
            onSave = { name, relation, dob, gender, cccd, phone, householdId, cluster, residence, education, profession, religion, partyMember, job, tempStart, tempEnd, mode ->
                viewModel.savePerson(
                    existing = dialog.person,
                    name = name,
                    relation = relation,
                    dob = dob,
                    gender = gender,
                    cccd = cccd,
                    phone = phone,
                    householdId = householdId,
                    cluster = cluster,
                    residence = residence,
                    education = education,
                    profession = profession,
                    religion = religion,
                    partyMember = partyMember,
                    job = job,
                    tempStart = tempStart,
                    tempEnd = tempEnd,
                    mode = mode,
                )
            },
        )

        ActiveDialog.SplitHousehold -> SplitHouseholdDialog(
            households = uiState.households,
            people = uiState.people,
            suggestedId = uiState.nextHouseholdId(),
            onDismiss = { viewModel.closeDialog() },
            onConfirmSplit = { newId, newHead, cluster, classify, selectedIds ->
                viewModel.splitHousehold(newId, newHead, cluster, classify, selectedIds)
            },
        )

        is ActiveDialog.ComplaintEditor -> ComplaintDialog(
            defaultSenderName = uiState.session?.name.orEmpty(),
            defaultSubject = dialog.defaultSubject,
            defaultHouseholdId = dialog.defaultHouseholdId.ifBlank { uiState.session?.householdId.orEmpty() },
            onDismiss = { viewModel.closeDialog() },
            onSubmit = { senderName, subject, content, hhId ->
                viewModel.submitComplaint(senderName, subject, content, hhId)
            },
        )

        ActiveDialog.SettingsEditor -> SettingsDialog(
            initialSettings = uiState.settings,
            onCompressImage = { ctx, uri, prefix, maxDim ->
                viewModel.compressLocalImage(ctx, uri, prefix, maxDim)
            },
            onDismiss = { viewModel.closeDialog() },
            onSave = { newSettings -> viewModel.saveSettings(newSettings) },
        )

        is ActiveDialog.ConfirmDeletePost -> ConfirmActionDialog(
            title = "Xóa thông báo",
            message = "Bạn có chắc muốn xóa thông báo \"${dialog.post.title}\"?",
            confirmText = "Xóa",
            onDismiss = { viewModel.closeDialog() },
            onConfirm = { viewModel.deletePost(dialog.post) },
        )

        is ActiveDialog.ConfirmDeleteHousehold -> ConfirmActionDialog(
            title = "Xóa hộ gia đình",
            message = "Xóa hộ ${dialog.household.id} (${dialog.household.head}) và toàn bộ nhân khẩu thuộc hộ này?",
            confirmText = "Xóa hộ",
            onDismiss = { viewModel.closeDialog() },
            onConfirm = { viewModel.deleteHousehold(dialog.household) },
        )

        is ActiveDialog.ConfirmDeletePerson -> ConfirmActionDialog(
            title = "Xóa nhân khẩu",
            message = "Bạn có chắc muốn xóa nhân khẩu \"${dialog.person.name}\"?",
            confirmText = "Xóa",
            onDismiss = { viewModel.closeDialog() },
            onConfirm = { viewModel.deletePerson(dialog.person) },
        )

        ActiveDialog.ConfirmRestoreSample -> ConfirmActionDialog(
            title = "Khôi phục dữ liệu mẫu",
            message = "Khôi phục dữ liệu mẫu sẽ thay thế dữ liệu hộ, nhân khẩu và bảng tin hiện có trên máy chủ đám mây. Bạn có chắc chắn?",
            confirmText = "Khôi phục",
            onDismiss = { viewModel.closeDialog() },
            onConfirm = { viewModel.restoreSampleData() },
        )
    }

    if (shouldShowUpdatePrompt) {
        AppUpdateRequiredDialog(
            settings = uiState.settings,
            isAdmin = uiState.isAdmin,
            onDismissForSession = {
                dismissedUpdateVersion = uiState.settings.latestVersionCode
            },
        )
    }
}
