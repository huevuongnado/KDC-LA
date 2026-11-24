package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRecordDoc
import com.example.data.model.AgeGroupFilter
import com.example.data.model.CitizenAccountDoc
import com.example.data.model.ComplaintDoc
import com.example.data.model.DomainConstants
import com.example.data.model.HouseholdDoc
import com.example.data.model.InlineMemberDraft
import com.example.data.model.PersonAddMode
import com.example.data.model.PersonDoc
import com.example.data.model.PortalSettings
import com.example.data.model.PostDoc
import com.example.data.model.UserProfileDoc
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.remote.toVietnameseFirestoreMessage
import com.example.data.repository.CommunityRepository
import com.example.util.CsvExportHelper
import com.example.util.ImageStorageHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PortalTab(val route: String, val label: String) {
    NEWS("news", "Bảng tin"),
    HOUSEHOLDS("households", "Hộ gia đình"),
    PEOPLE("people", "Nhân khẩu"),
    MINE("mine", "Hộ của tôi"),
}

sealed interface ActiveDialog {
    data object None : ActiveDialog
    data object AccountAndRoles : ActiveDialog
    data object ChangeOwnPassword : ActiveDialog
    data class AdminResetCitizenPassword(val person: PersonDoc) : ActiveDialog
    data class PostEditor(val post: PostDoc? = null) : ActiveDialog
    data class HouseholdEditor(val household: HouseholdDoc? = null) : ActiveDialog
    data class PersonEditor(
        val person: PersonDoc? = null,
        val mode: PersonAddMode = PersonAddMode.NORMAL,
    ) : ActiveDialog
    data object SplitHousehold : ActiveDialog
    data class ComplaintEditor(
        val defaultSubject: String = DomainConstants.COMPLAINT_SUBJECTS.first(),
        val defaultHouseholdId: String = "",
    ) : ActiveDialog
    data object SettingsEditor : ActiveDialog
    data class ConfirmDeletePost(val post: PostDoc) : ActiveDialog
    data class ConfirmDeleteHousehold(val household: HouseholdDoc) : ActiveDialog
    data class ConfirmDeletePerson(val person: PersonDoc) : ActiveDialog
    data object ConfirmRestoreSample : ActiveDialog
}

data class FilterState(
    val newsCategory: String = "",
    val houseSearch: String = "",
    val houseCluster: String = "",
    val houseStatus: String = "",
    val houseClassify: String = "",
    val housePoorOnly: Boolean = false,
    val peopleSearch: String = "",
    val peopleCluster: String = "",
    val peopleParty: String = "",
    val peopleEdu: String = "",
    val peoplePro: String = "",
    val peopleReligion: String = "",
    val peopleJob: String = "",
    val peopleAge: AgeGroupFilter = AgeGroupFilter.ALL,
    val peopleResidence: String = "",
    val peopleMale18To27Only: Boolean = false,
)

data class PortalUiState(
    val isReady: Boolean = false,
    val errorMessage: String? = null,
    val settings: PortalSettings = PortalSettings(),
    val session: UserSession? = null,
    val myCitizenAccount: CitizenAccountDoc? = null,
    val citizenAccounts: List<CitizenAccountDoc> = emptyList(),
    val citizenGateSkipped: Boolean = false,
    val citizenLoginBusy: Boolean = false,
    val citizenLoginError: String? = null,
    val currentTab: PortalTab = PortalTab.NEWS,
    val households: List<HouseholdDoc> = emptyList(),
    val people: List<PersonDoc> = emptyList(),
    val posts: List<PostDoc> = emptyList(),
    val complaints: List<ComplaintDoc> = emptyList(),
    val knownUsers: List<UserProfileDoc> = emptyList(),
    val admins: List<AdminRecordDoc> = emptyList(),
    val filters: FilterState = FilterState(),
    val activeDialog: ActiveDialog = ActiveDialog.None,
    val pendingCsvContent: String? = null,
) {
    val isAdmin: Boolean get() = session?.isAdmin == true
    val requiresCitizenLogin: Boolean
        get() = isReady && !isAdmin && (
            (myCitizenAccount == null && !citizenGateSkipped) ||
                citizenLoginBusy ||
                !citizenLoginError.isNullOrBlank()
            )

    fun nextHouseholdId(): String {
        val maxNum = households.maxOfOrNull { h ->
            h.id.filter { it.isDigit() }.toIntOrNull() ?: 0
        } ?: 0
        return "LA" + (maxNum + 1).toString().padStart(3, '0')
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: CommunityRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String,
) : ViewModel() {

    private val isProfileInitializedFlow = MutableStateFlow(false)
    private val currentTabFlow = MutableStateFlow(PortalTab.NEWS)
    private val filtersFlow = MutableStateFlow(FilterState())
    private val activeDialogFlow = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    private val pendingCsvFlow = MutableStateFlow<String?>(null)
    private val citizenGateSkippedFlow = MutableStateFlow(false)
    private val citizenLoginBusyFlow = MutableStateFlow(false)
    private val citizenLoginErrorFlow = MutableStateFlow<String?>(null)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private sealed interface StreamSlice<out T> {
        data object Loading : StreamSlice<Nothing>
        data class Ready<T>(val data: T) : StreamSlice<T>
        data class Failed(val message: String) : StreamSlice<Nothing>
    }

    private fun <T> Flow<T>.asStreamSlice(fallbackError: String): Flow<StreamSlice<T>> =
        map<T, StreamSlice<T>> { StreamSlice.Ready(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(StreamSlice.Failed(toVietnameseFirestoreMessage(rootError, fallbackError)))
            }

    private val isAdminFlow = repository.observeIsAdmin()
        .asStreamSlice("Không thể kiểm tra quyền quản trị.")

    private val userProfileFlow = repository.observeUserProfile()
        .asStreamSlice("Không thể tải hồ sơ người dùng.")

    private val myCitizenAccountRawFlow = repository.observeUserProfile()
        .map { it?.linkedPersonId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { pid -> repository.observeMyCitizenAccount(pid) }

    private val myCitizenAccountFlow = myCitizenAccountRawFlow
        .asStreamSlice("Không thể kiểm tra tài khoản công dân.")

    private val settingsFlow = repository.observeSettings()
        .asStreamSlice("Không thể tải cài đặt cổng thông tin.")

    private val activeHouseholdScopeFlow = combine(
        repository.observeIsAdmin(),
        myCitizenAccountRawFlow,
    ) { isAdmin, account ->
        isAdmin to (if (isAdmin) "" else account?.householdId.orEmpty())
    }.distinctUntilChanged()

    private val householdsFlow = activeHouseholdScopeFlow
        .flatMapLatest { (isAdmin, linkedHhId) ->
            repository.observeHouseholds(isAdmin, linkedHhId)
        }
        .asStreamSlice("Không thể đồng bộ danh sách hộ gia đình.")

    private val peopleFlow = activeHouseholdScopeFlow
        .flatMapLatest { (isAdmin, linkedHhId) ->
            repository.observePeople(isAdmin, linkedHhId)
        }
        .asStreamSlice("Không thể đồng bộ danh sách nhân khẩu.")

    private val postsFlow = repository.observeIsAdmin()
        .flatMapLatest { isAdmin -> repository.observePosts(isAdmin) }
        .asStreamSlice("Không thể đồng bộ bảng tin.")

    private val complaintsFlow = repository.observeIsAdmin()
        .flatMapLatest { isAdmin -> repository.observeComplaints(isAdmin) }
        .asStreamSlice("Không thể đồng bộ danh sách phản ánh.")

    private val usersFlow = repository.observeIsAdmin()
        .flatMapLatest { isAdmin -> repository.observeAllUsers(isAdmin) }
        .asStreamSlice("Không thể tải danh sách tài khoản.")

    private val adminsFlow = repository.observeIsAdmin()
        .flatMapLatest { isAdmin -> repository.observeAdmins(isAdmin) }
        .asStreamSlice("Không thể tải danh sách quản trị viên.")

    private val citizenAccountsFlow = repository.observeIsAdmin()
        .flatMapLatest { isAdmin -> repository.observeAllCitizenAccounts(isAdmin) }
        .asStreamSlice("Không thể tải danh sách tài khoản bà con.")

    private val authAndSettingsBundle = combine(
        isAdminFlow,
        userProfileFlow,
        myCitizenAccountFlow,
        settingsFlow,
        isProfileInitializedFlow,
    ) { adminSlice, profileSlice, citizenAccountSlice, settingsSlice, initialized ->
        AuthBundle(adminSlice, profileSlice, citizenAccountSlice, settingsSlice, initialized)
    }

    private val domainDataBundle = combine(
        householdsFlow,
        peopleFlow,
        postsFlow,
        complaintsFlow,
        usersFlow,
        adminsFlow,
        citizenAccountsFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DomainBundle(
            households = values[0] as StreamSlice<List<HouseholdDoc>>,
            people = values[1] as StreamSlice<List<PersonDoc>>,
            posts = values[2] as StreamSlice<List<PostDoc>>,
            complaints = values[3] as StreamSlice<List<ComplaintDoc>>,
            users = values[4] as StreamSlice<List<UserProfileDoc>>,
            admins = values[5] as StreamSlice<List<AdminRecordDoc>>,
            citizenAccounts = values[6] as StreamSlice<List<CitizenAccountDoc>>,
        )
    }

    private val uiControlBundle = combine(
        currentTabFlow,
        filtersFlow,
        activeDialogFlow,
        pendingCsvFlow,
        citizenGateSkippedFlow,
        citizenLoginBusyFlow,
        citizenLoginErrorFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ControlBundle(
            tab = values[0] as PortalTab,
            filters = values[1] as FilterState,
            dialog = values[2] as ActiveDialog,
            pendingCsv = values[3] as String?,
            citizenGateSkipped = values[4] as Boolean,
            citizenLoginBusy = values[5] as Boolean,
            citizenLoginError = values[6] as String?,
        )
    }

    val uiState: StateFlow<PortalUiState> = combine(
        authAndSettingsBundle,
        domainDataBundle,
        uiControlBundle,
    ) { authBundle, domainBundle, controls ->
        val isAdmin = (authBundle.adminSlice as? StreamSlice.Ready)?.data == true
        val profile = (authBundle.profileSlice as? StreamSlice.Ready)?.data
        val myCitizenAccount = (authBundle.citizenAccountSlice as? StreamSlice.Ready)?.data
        val settings = (authBundle.settingsSlice as? StreamSlice.Ready)?.data ?: PortalSettings()
        val households = (domainBundle.households as? StreamSlice.Ready)?.data.orEmpty()
        val people = (domainBundle.people as? StreamSlice.Ready)?.data.orEmpty()
        val posts = (domainBundle.posts as? StreamSlice.Ready)?.data.orEmpty()
        val complaints = (domainBundle.complaints as? StreamSlice.Ready)?.data.orEmpty()
        val users = (domainBundle.users as? StreamSlice.Ready)?.data.orEmpty()
        val admins = (domainBundle.admins as? StreamSlice.Ready)?.data.orEmpty()
        val citizenAccounts = (domainBundle.citizenAccounts as? StreamSlice.Ready)?.data.orEmpty()

        val firstError = listOfNotNull(
            (authBundle.adminSlice as? StreamSlice.Failed)?.message,
            (authBundle.profileSlice as? StreamSlice.Failed)?.message,
            (authBundle.settingsSlice as? StreamSlice.Failed)?.message,
            (domainBundle.households as? StreamSlice.Failed)?.message,
            (domainBundle.people as? StreamSlice.Failed)?.message,
            (domainBundle.posts as? StreamSlice.Failed)?.message,
            (domainBundle.complaints as? StreamSlice.Failed)?.message,
        ).firstOrNull()

        val linkedHouseholdId = myCitizenAccount?.householdId?.takeIf { it.isNotBlank() }
            ?: profile?.householdId?.takeIf { it.isNotBlank() }
            ?: households.firstOrNull()?.id.orEmpty()
        val linkedCluster = myCitizenAccount?.cluster?.takeIf { it.isNotBlank() }
            ?: profile?.cluster?.takeIf { it.isNotBlank() }
            ?: households.firstOrNull { it.id == linkedHouseholdId }?.cluster.orEmpty()

        val session = UserSession(
            id = currentUserId,
            email = currentUserEmail,
            name = myCitizenAccount?.name?.takeIf { it.isNotBlank() }
                ?: profile?.name?.takeIf { it.isNotBlank() }
                ?: currentUserName.ifBlank { currentUserEmail },
            role = if (isAdmin) UserRole.ADMIN else UserRole.CITIZEN,
            householdId = linkedHouseholdId,
            cluster = linkedCluster,
            phone = myCitizenAccount?.phone?.takeIf { it.isNotBlank() } ?: profile?.phone.orEmpty(),
            linkedPersonId = myCitizenAccount?.personId.orEmpty(),
            cccd = myCitizenAccount?.cccd.orEmpty(),
            mustChangePassword = myCitizenAccount?.mustChangePassword == true,
        )

        val coreLoaded = authBundle.initialized &&
            authBundle.adminSlice !is StreamSlice.Loading &&
            authBundle.citizenAccountSlice !is StreamSlice.Loading &&
            domainBundle.posts !is StreamSlice.Loading

        PortalUiState(
            isReady = coreLoaded || firstError != null,
            errorMessage = firstError,
            settings = settings,
            session = session,
            myCitizenAccount = myCitizenAccount,
            citizenAccounts = citizenAccounts,
            citizenGateSkipped = controls.citizenGateSkipped,
            citizenLoginBusy = controls.citizenLoginBusy,
            citizenLoginError = controls.citizenLoginError,
            currentTab = controls.tab,
            households = households,
            people = people,
            posts = posts,
            complaints = complaints,
            knownUsers = users,
            admins = admins,
            filters = controls.filters,
            activeDialog = controls.dialog,
            pendingCsvContent = controls.pendingCsv,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PortalUiState(),
    )

    init {
        viewModelScope.launch {
            val profileResult = repository.ensureUserProfileOnStart()
            if (profileResult.getOrNull() == true) {
                repository.seedSampleDataIfEmpty()
            }
            isProfileInitializedFlow.value = true
        }
    }

    fun selectTab(tab: PortalTab) {
        currentTabFlow.value = tab
    }

    fun openDialog(dialog: ActiveDialog) {
        activeDialogFlow.value = dialog
    }

    fun closeDialog() {
        activeDialogFlow.value = ActiveDialog.None
    }

    fun updateFilters(transform: (FilterState) -> FilterState) {
        filtersFlow.update(transform)
    }

    fun clearPeopleFilters() {
        filtersFlow.update {
            it.copy(
                peopleSearch = "",
                peopleCluster = "",
                peopleParty = "",
                peopleEdu = "",
                peoplePro = "",
                peopleReligion = "",
                peopleJob = "",
                peopleAge = AgeGroupFilter.ALL,
                peopleResidence = "",
                peopleMale18To27Only = false,
            )
        }
    }

    fun clearHouseholdFilters() {
        filtersFlow.update {
            it.copy(
                houseSearch = "",
                houseCluster = "",
                houseStatus = "",
                houseClassify = "",
                housePoorOnly = false,
            )
        }
    }

    // Posts
    fun savePost(
        existing: PostDoc?,
        title: String,
        category: String,
        scope: String,
        cluster: String,
        content: String,
        imageData: String,
    ) {
        viewModelScope.launch {
            val result = repository.savePost(
                existing = existing,
                title = title,
                category = category,
                scope = scope,
                cluster = cluster,
                content = content,
                imageData = imageData,
                allHouseholds = uiState.value.households,
            )
            handleResult(result, closeOnSuccess = true)
        }
    }

    fun togglePinPost(post: PostDoc) {
        viewModelScope.launch {
            handleResult(repository.togglePinPost(post))
        }
    }

    fun deletePost(post: PostDoc) {
        viewModelScope.launch {
            handleResult(repository.deletePost(post.id), closeOnSuccess = true)
        }
    }

    // Households
    fun saveHousehold(
        existing: HouseholdDoc?,
        idInput: String,
        head: String,
        cluster: String,
        status: String,
        classify: String,
        address: String,
        memberEmailsRaw: String,
        inlineMembers: List<InlineMemberDraft>,
    ) {
        viewModelScope.launch {
            val result = repository.saveHousehold(
                existing = existing,
                idInput = idInput,
                head = head,
                cluster = cluster,
                status = status,
                classify = classify,
                address = address,
                memberEmailsRaw = memberEmailsRaw,
                inlineMembers = inlineMembers,
                knownUsers = uiState.value.knownUsers,
            )
            handleResult(result, closeOnSuccess = true)
        }
    }

    fun deleteHousehold(household: HouseholdDoc) {
        viewModelScope.launch {
            handleResult(repository.deleteHousehold(household.id), closeOnSuccess = true)
        }
    }

    fun splitHousehold(
        newIdInput: String,
        newHead: String,
        cluster: String,
        classify: String,
        selectedMemberIds: List<String>,
    ) {
        viewModelScope.launch {
            val targetId = newIdInput.trim().ifBlank { uiState.value.nextHouseholdId() }
            val result = repository.splitHousehold(
                newHouseholdId = targetId,
                newHeadName = newHead,
                cluster = cluster,
                classify = classify,
                selectedMemberIds = selectedMemberIds,
                allPeople = uiState.value.people,
            )
            handleResult(result, closeOnSuccess = true)
        }
    }

    // People
    fun savePerson(
        existing: PersonDoc?,
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
    ) {
        viewModelScope.launch {
            val effectiveResidence = if (mode == PersonAddMode.TEMPORARY) "Tạm trú" else residence
            val result = repository.savePerson(
                existing = existing,
                name = name,
                relation = relation,
                dob = dob,
                gender = gender,
                cccd = cccd,
                phone = phone,
                householdId = householdId,
                cluster = cluster,
                residence = effectiveResidence,
                education = education,
                profession = profession,
                religion = religion,
                partyMember = partyMember,
                job = job,
                tempStart = tempStart,
                tempEnd = tempEnd,
            )
            handleResult(result, closeOnSuccess = true)
        }
    }

    fun deletePerson(person: PersonDoc) {
        viewModelScope.launch {
            handleResult(repository.deletePerson(person.id), closeOnSuccess = true)
        }
    }

    // Citizen Accounts (SĐT / Mã định danh + Password)
    fun clearCitizenLoginError() {
        citizenLoginErrorFlow.value = null
    }

    fun skipCitizenLogin() {
        citizenLoginErrorFlow.value = null
        citizenGateSkippedFlow.value = true
        currentTabFlow.value = PortalTab.NEWS
    }

    fun openCitizenLoginPrompt() {
        citizenLoginErrorFlow.value = null
        citizenGateSkippedFlow.value = false
    }

    fun loginCitizen(identifier: String, password: String) {
        viewModelScope.launch {
            citizenLoginBusyFlow.value = true
            citizenLoginErrorFlow.value = null
            val result = repository.loginCitizen(identifier, password)
            citizenLoginBusyFlow.value = false
            result.onSuccess { account ->
                citizenGateSkippedFlow.value = false
                if (account.mustChangePassword) {
                    _snackbarEvents.tryEmit(
                        "Đăng nhập thành công (${account.name}). Bạn nên đổi mật khẩu mới để bảo mật tài khoản."
                    )
                    activeDialogFlow.value = ActiveDialog.ChangeOwnPassword
                } else {
                    _snackbarEvents.tryEmit("Chào mừng ${account.name} (Hộ ${account.householdId}).")
                }
            }.onFailure { err ->
                citizenLoginErrorFlow.value = err.localizedMessage ?: "Đăng nhập không thành công."
            }
        }
    }

    fun logoutCitizen() {
        val pid = uiState.value.session?.linkedPersonId.orEmpty()
        viewModelScope.launch {
            val result = repository.logoutCitizenSession(pid)
            result.onSuccess { msg ->
                citizenGateSkippedFlow.value = false
                citizenLoginErrorFlow.value = null
                closeDialog()
                _snackbarEvents.tryEmit(msg)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Không thể đăng xuất tài khoản bà con.")
            }
        }
    }

    fun changeOwnPassword(currentPassword: String, newPassword: String) {
        val pid = uiState.value.session?.linkedPersonId.orEmpty()
        viewModelScope.launch {
            handleResult(
                repository.changeOwnCitizenPassword(pid, currentPassword, newPassword),
                closeOnSuccess = true,
            )
        }
    }

    fun adminSetCitizenPassword(person: PersonDoc, newPassword: String, resetToDefaultCccd: Boolean) {
        viewModelScope.launch {
            handleResult(
                repository.adminSetCitizenPassword(person, newPassword, resetToDefaultCccd),
                closeOnSuccess = true,
            )
        }
    }

    // Complaints & Household Link
    fun submitComplaint(
        senderName: String,
        subject: String,
        content: String,
        householdId: String,
    ) {
        val session = uiState.value.session ?: return
        viewModelScope.launch {
            handleResult(
                repository.submitComplaint(session, senderName, subject, content, householdId),
                closeOnSuccess = true,
            )
        }
    }

    fun toggleComplaintStatus(complaint: ComplaintDoc) {
        viewModelScope.launch {
            handleResult(repository.toggleComplaintStatus(complaint))
        }
    }

    fun approveHouseholdLink(complaint: ComplaintDoc) {
        viewModelScope.launch {
            handleResult(repository.approveHouseholdLink(complaint))
        }
    }

    // Admin Access Management
    fun grantAdminByEmail(email: String) {
        viewModelScope.launch {
            handleResult(repository.grantAdminByEmail(email, uiState.value.knownUsers))
        }
    }

    fun revokeAdmin(targetUid: String) {
        viewModelScope.launch {
            handleResult(repository.revokeAdmin(targetUid))
        }
    }

    // Settings & Sample Data
    fun saveSettings(settings: PortalSettings) {
        viewModelScope.launch {
            handleResult(repository.updateSettings(settings), closeOnSuccess = true)
        }
    }

    fun restoreSampleData() {
        viewModelScope.launch {
            handleResult(repository.restoreSampleData(), closeOnSuccess = true)
        }
    }

    // Image & CSV Utilities
    suspend fun compressPostImageBase64(context: Context, uri: Uri): String? {
        return ImageStorageHelper.encodeCompressedBase64(context, uri, maxDimension = 720)
    }

    suspend fun compressLocalImage(context: Context, uri: Uri, prefix: String, maxDim: Int): String? {
        return ImageStorageHelper.saveCompressedImage(context, uri, prefix, maxDimension = maxDim)
    }

    fun prepareHouseholdsCsv(): String {
        val state = uiState.value
        val csv = CsvExportHelper.buildHouseholdsCsv(state.households, state.people)
        pendingCsvFlow.value = csv
        return csv
    }

    fun preparePeopleCsv(): String {
        val csv = CsvExportHelper.buildPeopleCsv(uiState.value.people)
        pendingCsvFlow.value = csv
        return csv
    }

    fun writePendingCsvToUri(context: Context, uri: Uri) {
        val content = pendingCsvFlow.value ?: return
        pendingCsvFlow.value = null
        viewModelScope.launch {
            val ok = CsvExportHelper.writeCsvToUri(context, uri, content)
            _snackbarEvents.tryEmit(
                if (ok) "Đã xuất tệp CSV UTF-8 thành công." else "Không thể ghi tệp CSV."
            )
        }
    }

    fun notifyMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private fun handleResult(result: Result<String>, closeOnSuccess: Boolean = false) {
        result.onSuccess { msg ->
            if (closeOnSuccess) closeDialog()
            _snackbarEvents.tryEmit(msg)
        }.onFailure { err ->
            _snackbarEvents.tryEmit(err.localizedMessage ?: "Thao tác không thành công.")
        }
    }

    private data class AuthBundle(
        val adminSlice: StreamSlice<Boolean>,
        val profileSlice: StreamSlice<UserProfileDoc?>,
        val citizenAccountSlice: StreamSlice<CitizenAccountDoc?>,
        val settingsSlice: StreamSlice<PortalSettings>,
        val initialized: Boolean,
    )

    private data class DomainBundle(
        val households: StreamSlice<List<HouseholdDoc>>,
        val people: StreamSlice<List<PersonDoc>>,
        val posts: StreamSlice<List<PostDoc>>,
        val complaints: StreamSlice<List<ComplaintDoc>>,
        val users: StreamSlice<List<UserProfileDoc>>,
        val admins: StreamSlice<List<AdminRecordDoc>>,
        val citizenAccounts: StreamSlice<List<CitizenAccountDoc>>,
    )

    private data class ControlBundle(
        val tab: PortalTab,
        val filters: FilterState,
        val dialog: ActiveDialog,
        val pendingCsv: String?,
        val citizenGateSkipped: Boolean,
        val citizenLoginBusy: Boolean,
        val citizenLoginError: String?,
    )
}
