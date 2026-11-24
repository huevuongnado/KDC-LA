package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.SampleData
import com.example.data.local.LocalMediaPrefs
import com.example.data.model.AdminRecordDoc
import com.example.data.model.CitizenAccountDoc
import com.example.data.model.ComplaintDoc
import com.example.data.model.DomainConstants
import com.example.data.model.HouseholdDoc
import com.example.data.model.HouseholdSurveyDoc
import com.example.data.model.InlineMemberDraft
import com.example.data.model.PersonDoc
import com.example.data.model.PortalSettings
import com.example.data.model.PortalSettingsDoc
import com.example.data.model.PostDoc
import com.example.data.model.UserProfileDoc
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.toVietnameseFirestoreMessage
import com.example.util.DateUtils
import com.example.util.PasswordHashHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CommunityRepository(
    private val db: FirebaseFirestore,
    private val localMediaPrefs: LocalMediaPrefs,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localMediaPrefs = LocalMediaPrefs(context.applicationContext),
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Vui lòng đăng nhập Google trước khi truy cập dữ liệu.")
    }

    private fun currentEmailNormalized(): String =
        auth.currentUser?.email?.trim()?.lowercase().orEmpty()

    private fun isEmailVerified(): Boolean =
        auth.currentUser?.isEmailVerified == true

    private fun isBootstrappedAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified &&
            user.email?.trim()?.lowercase() == DomainConstants.BOOTSTRAP_ADMIN_EMAIL.lowercase()
    }

    private fun <T> Flow<T>.handleSnapshotErrors(
        uid: String,
        operation: OperationType,
        path: String,
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operation, path)
        }
        throw error
    }

    fun observeIsAdmin(): Flow<Boolean> = flow {
        val uid = requireUserId()
        if (isBootstrappedAdmin()) {
            emit(true)
            return@flow
        }
        val path = "admins/$uid"
        emitAll(
            db.collection("admins").document(uid)
                .snapshots()
                .map { snapshot -> snapshot.exists() || isBootstrappedAdmin() }
                .handleSnapshotErrors(uid, OperationType.GET, path)
        )
    }

    fun observeUserProfile(): Flow<UserProfileDoc?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleSnapshotErrors(uid, OperationType.GET, path)
        )
    }

    fun observeSettings(): Flow<PortalSettings> = flow {
        val uid = requireUserId()
        val path = "settings/portal"
        val remoteFlow = db.collection("settings").document("portal")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(PortalSettingsDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PortalSettingsDoc()
                } else {
                    PortalSettingsDoc()
                }
            }
            .handleSnapshotErrors(uid, OperationType.GET, path)

        emitAll(
            combine(remoteFlow, localMediaPrefs.pathsFlow) { remote, local ->
                PortalSettings(
                    title = remote.title.ifBlank { PortalSettings().title },
                    slogan = remote.slogan.ifBlank { PortalSettings().slogan },
                    themeKey = remote.themeKey.ifBlank { "green" },
                    fontSize = remote.fontSize.coerceIn(13, 20),
                    logoPath = local.logoPath,
                    coverPath = local.coverPath,
                )
            }
        )
    }

    fun observeMyCitizenAccount(linkedPersonId: String): Flow<CitizenAccountDoc?> {
        if (linkedPersonId.isBlank()) return flowOf(null)
        return flow {
            val uid = requireUserId()
            val path = "citizen_accounts/$linkedPersonId"
            emitAll(
                db.collection("citizen_accounts").document(linkedPersonId)
                    .snapshots()
                    .map { snapshot ->
                        if (snapshot.exists()) {
                            snapshot.toObject(
                                CitizenAccountDoc::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )?.takeIf { it.boundUid == uid }
                        } else null
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            emit(null)
                        } else {
                            if (firestoreError == null && error is CancellationException) throw error
                            throw error
                        }
                    }
            )
        }
    }

    fun observeAllCitizenAccounts(isAdmin: Boolean): Flow<List<CitizenAccountDoc>> {
        if (!isAdmin) return flowOf(emptyList())
        return flow {
            val uid = requireUserId()
            val path = "citizen_accounts"
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            CitizenAccountDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        }
    }

    fun observeHouseholds(
        isAdmin: Boolean,
        linkedHouseholdId: String = "",
    ): Flow<List<HouseholdDoc>> = flow {
        val uid = requireUserId()
        val path = "households"
        if (isAdmin) {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(HouseholdDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.id }
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        } else {
            val email = currentEmailNormalized()
            val byUidFlow = db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { it.toObjects(HouseholdDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(uid, OperationType.LIST, path)

            val byEmailFlow: Flow<List<HouseholdDoc>> = if (isEmailVerified() && email.isNotBlank()) {
                db.collection(path)
                    .whereArrayContains("memberEmails", email)
                    .snapshots()
                    .map { it.toObjects(HouseholdDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            } else {
                flowOf(emptyList())
            }

            val byLinkedDocFlow: Flow<List<HouseholdDoc>> = if (linkedHouseholdId.isNotBlank()) {
                db.collection(path).document(linkedHouseholdId)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            listOfNotNull(
                                snap.toObject(
                                    HouseholdDoc::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                            )
                        } else emptyList()
                    }
                    .catch { error ->
                        val fe = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            emit(emptyList())
                        } else {
                            if (fe == null && error is CancellationException) throw error
                            throw error
                        }
                    }
            } else {
                flowOf(emptyList())
            }

            emitAll(
                combine(byUidFlow, byEmailFlow, byLinkedDocFlow) { a, b, c ->
                    (a + b + c).distinctBy { it.id }.sortedBy { it.id }
                }
            )
        }
    }

    fun observeHouseholdSurveys(
        isAdmin: Boolean,
        linkedHouseholdIds: List<String> = emptyList(),
    ): Flow<List<HouseholdSurveyDoc>> = flow {
        val uid = requireUserId()
        val path = "household_surveys"
        if (isAdmin) {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            HouseholdSurveyDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedBy { it.householdId.ifBlank { it.id } }
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        } else {
            val cleanIds = linkedHouseholdIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (cleanIds.isEmpty()) {
                emit(emptyList())
            } else {
                val docFlows = cleanIds.map { hhId ->
                    db.collection(path).document(hhId)
                        .snapshots()
                        .map { snap ->
                            if (snap.exists()) {
                                snap.toObject(
                                    HouseholdSurveyDoc::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                            } else null
                        }
                        .catch { error ->
                            val fe = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (fe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                emit(null)
                            } else {
                                if (fe == null && error is CancellationException) throw error
                                throw error
                            }
                        }
                }
                emitAll(
                    combine(docFlows) { items ->
                        items.filterNotNull().sortedBy { it.householdId.ifBlank { it.id } }
                    }
                )
            }
        }
    }

    fun observePeople(
        isAdmin: Boolean,
        linkedHouseholdId: String = "",
    ): Flow<List<PersonDoc>> = flow {
        val uid = requireUserId()
        val path = "people"
        if (isAdmin) {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(PersonDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.name }
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        } else {
            val email = currentEmailNormalized()
            val byUidFlow = db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { it.toObjects(PersonDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(uid, OperationType.LIST, path)

            val byEmailFlow: Flow<List<PersonDoc>> = if (isEmailVerified() && email.isNotBlank()) {
                db.collection(path)
                    .whereArrayContains("memberEmails", email)
                    .snapshots()
                    .map { it.toObjects(PersonDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            } else {
                flowOf(emptyList())
            }

            val byLinkedHouseholdFlow: Flow<List<PersonDoc>> = if (linkedHouseholdId.isNotBlank()) {
                db.collection(path)
                    .whereEqualTo("householdId", linkedHouseholdId)
                    .snapshots()
                    .map { it.toObjects(PersonDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .catch { error ->
                        val fe = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            emit(emptyList())
                        } else {
                            if (fe == null && error is CancellationException) throw error
                            throw error
                        }
                    }
            } else {
                flowOf(emptyList())
            }

            emitAll(
                combine(byUidFlow, byEmailFlow, byLinkedHouseholdFlow) { a, b, c ->
                    (a + b + c).distinctBy { it.id }.sortedBy { it.name }
                }
            )
        }
    }

    fun observePosts(isAdmin: Boolean): Flow<List<PostDoc>> = flow {
        val uid = requireUserId()
        val path = "posts"
        if (isAdmin) {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(PostDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedWith(compareByDescending<PostDoc> { it.pinned }.thenByDescending { it.dateIso })
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        } else {
            val email = currentEmailNormalized()
            val publicFlow = db.collection(path)
                .whereEqualTo("scope", "Toàn thôn")
                .snapshots()
                .map { it.toObjects(PostDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(uid, OperationType.LIST, path)

            if (isEmailVerified() && email.isNotBlank()) {
                val targetedFlow = db.collection(path)
                    .whereArrayContains("allowedEmails", email)
                    .snapshots()
                    .map { it.toObjects(PostDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
                emitAll(
                    combine(publicFlow, targetedFlow) { a, b ->
                        (a + b).distinctBy { it.id }
                            .sortedWith(compareByDescending<PostDoc> { it.pinned }.thenByDescending { it.dateIso })
                    }
                )
            } else {
                emitAll(
                    publicFlow.map { list ->
                        list.sortedWith(compareByDescending<PostDoc> { it.pinned }.thenByDescending { it.dateIso })
                    }
                )
            }
        }
    }

    fun observeComplaints(isAdmin: Boolean): Flow<List<ComplaintDoc>> = flow {
        val uid = requireUserId()
        val path = "complaints"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("userId", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(ComplaintDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.dateIso }
                }
                .handleSnapshotErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeAllUsers(isAdmin: Boolean): Flow<List<UserProfileDoc>> {
        if (!isAdmin) return flowOf(emptyList())
        return flow {
            val uid = requireUserId()
            val path = "users"
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.name }
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        }
    }

    fun observeAdmins(isAdmin: Boolean): Flow<List<AdminRecordDoc>> {
        if (!isAdmin) return flowOf(emptyList())
        return flow {
            val uid = requireUserId()
            val path = "admins"
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(AdminRecordDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .handleSnapshotErrors(uid, OperationType.LIST, path)
            )
        }
    }

    suspend fun ensureUserProfileOnStart(): Result<Boolean> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("Chưa đăng nhập.")
            val uid = user.uid
            val email = user.email?.trim()?.lowercase().orEmpty()
            val displayName = user.displayName?.trim().orEmpty()
                .ifBlank { email.substringBefore("@").ifBlank { "Cư dân Thôn Long An" } }
                .take(120)

            val adminDocExists = try {
                db.collection("admins").document(uid).get().await().exists()
            } catch (_: Exception) {
                false
            }
            val isAdmin = isBootstrappedAdmin() || adminDocExists
            val desiredRole = if (isAdmin) UserRole.ADMIN.key else UserRole.CITIZEN.key

            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            val existingSnap = userRef.get().await()

            // Try to auto-discover linked household if not already linked
            var matchedHouseholdId = existingSnap.getString("householdId").orEmpty()
            var matchedCluster = existingSnap.getString("cluster").orEmpty()
            if (matchedHouseholdId.isBlank() && email.isNotBlank() && isEmailVerified()) {
                val hhSnap = runCatching {
                    db.collection("households")
                        .whereArrayContains("memberEmails", email)
                        .limit(1)
                        .get()
                        .await()
                }.getOrNull()
                val firstHh = hhSnap?.documents?.firstOrNull()
                if (firstHh != null) {
                    matchedHouseholdId = firstHh.id
                    matchedCluster = firstHh.getString("cluster").orEmpty()
                }
            }

            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "name" to displayName,
                    "phone" to "",
                    "cluster" to matchedCluster,
                    "householdId" to matchedHouseholdId,
                    "linkedPersonId" to "",
                    "role" to desiredRole,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                userRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>()
                val existingRole = existingSnap.getString("role")
                val existingHh = existingSnap.getString("householdId").orEmpty()
                if (isAdmin && existingRole != UserRole.ADMIN.key) {
                    updates["role"] = UserRole.ADMIN.key
                }
                if (existingHh.isBlank() && matchedHouseholdId.isNotBlank()) {
                    updates["householdId"] = matchedHouseholdId
                    updates["cluster"] = matchedCluster
                }
                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    userRef.update(updates).await()
                }
            }

            Result.success(isAdmin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedSampleDataIfEmpty(): Result<Unit> {
        var docPath = "households"
        return try {
            requireUserId()
            if (!isBootstrappedAdmin()) {
                val uid = requireUserId()
                if (!db.collection("admins").document(uid).get().await().exists()) {
                    return Result.success(Unit)
                }
            }
            val existingHh = db.collection("households").limit(1).get().await()
            val existingPosts = db.collection("posts").limit(1).get().await()
            if (existingHh.isEmpty && existingPosts.isEmpty) {
                writeSampleDataset()
            } else {
                syncMissingCitizenAccounts()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncMissingCitizenAccounts() {
        runCatching {
            val peopleSnap = db.collection("people").get().await()
            val accountSnap = db.collection("citizen_accounts").get().await()
            val existingIds = accountSnap.documents.map { it.id }.toSet()
            val missing = peopleSnap.toObjects(PersonDoc::class.java)
                .filter { p ->
                    p.id.isNotBlank() &&
                        p.id !in existingIds &&
                        p.cccd.trim().isNotBlank() &&
                        p.householdId.isNotBlank() &&
                        p.cluster.isNotBlank()
                }
            if (missing.isEmpty()) return
            missing.chunked(200).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { p ->
                    val defaultPwd = p.cccd.trim()
                    val hashed = PasswordHashHelper.hashPassword(defaultPwd)
                    batch.set(
                        db.collection("citizen_accounts").document(p.id),
                        buildCitizenAccountCreateMap(
                            personId = p.id,
                            householdId = p.householdId,
                            cluster = p.cluster,
                            name = p.name,
                            phone = p.phone,
                            cccd = p.cccd,
                            saltHex = hashed.saltHex,
                            hashHex = hashed.hashHex,
                            mustChangePassword = true,
                        )
                    )
                }
                batch.commit().await()
            }
        }
    }

    private fun buildCitizenAccountCreateMap(
        personId: String,
        householdId: String,
        cluster: String,
        name: String,
        phone: String,
        cccd: String,
        saltHex: String,
        hashHex: String,
        mustChangePassword: Boolean,
    ): Map<String, Any> {
        val cleanPhone = phone.trim().take(30)
        val cleanCccd = cccd.trim().take(30)
        return mapOf(
            "personId" to personId,
            "householdId" to householdId.take(64),
            "cluster" to cluster.take(100),
            "name" to name.trim().ifBlank { "Cư dân" }.take(120),
            "phone" to cleanPhone,
            "cccd" to cleanCccd,
            "loginKeys" to PasswordHashHelper.buildLoginKeys(cleanPhone, cleanCccd),
            "passwordSalt" to saltHex,
            "passwordHash" to hashHex,
            "mustChangePassword" to mustChangePassword,
            "boundUid" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }

    suspend fun restoreSampleData(): Result<String> {
        var docPath = "households"
        return try {
            requireUserId()
            // Clear existing citizen_accounts, people, households, posts
            val accountDocs = db.collection("citizen_accounts").get().await()
            accountDocs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            val peopleDocs = db.collection("people").get().await()
            peopleDocs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            val hhDocs = db.collection("households").get().await()
            hhDocs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            val postDocs = db.collection("posts").get().await()
            postDocs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            writeSampleDataset()
            docPath = "settings/portal"
            val defaultSettings = PortalSettingsDoc()
            db.collection("settings").document("portal").set(
                mapOf(
                    "title" to defaultSettings.title,
                    "slogan" to defaultSettings.slogan,
                    "themeKey" to defaultSettings.themeKey,
                    "fontSize" to defaultSettings.fontSize,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Result.success("Đã khôi phục dữ liệu mẫu trên máy chủ đám mây.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể khôi phục dữ liệu mẫu.")))
        }
    }

    private suspend fun writeSampleDataset() {
        // Create households FIRST and await before writing people (rules check exists(/households/{id}))
        val hhBatch = db.batch()
        SampleData.households().forEach { h ->
            val ref = db.collection("households").document(h.id)
            hhBatch.set(
                ref,
                mapOf(
                    "head" to h.head,
                    "cluster" to h.cluster,
                    "status" to h.status,
                    "classify" to h.classify,
                    "address" to h.address,
                    "memberEmails" to emptyList<String>(),
                    "memberUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        hhBatch.commit().await()

        val peoplePostsBatch = db.batch()
        SampleData.people().forEach { p ->
            val ref = db.collection("people").document(p.id)
            peoplePostsBatch.set(
                ref,
                mapOf(
                    "name" to p.name,
                    "relation" to p.relation,
                    "dob" to p.dob,
                    "gender" to p.gender,
                    "cccd" to p.cccd,
                    "phone" to p.phone,
                    "householdId" to p.householdId,
                    "cluster" to p.cluster,
                    "residence" to p.residence,
                    "education" to p.education,
                    "profession" to p.profession,
                    "religion" to p.religion,
                    "partyMember" to p.partyMember,
                    "job" to p.job,
                    "tempStart" to p.tempStart,
                    "tempEnd" to p.tempEnd,
                    "memberEmails" to emptyList<String>(),
                    "memberUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            if (p.cccd.trim().isNotBlank()) {
                val hashed = PasswordHashHelper.hashPassword(p.cccd.trim())
                peoplePostsBatch.set(
                    db.collection("citizen_accounts").document(p.id),
                    buildCitizenAccountCreateMap(
                        personId = p.id,
                        householdId = p.householdId,
                        cluster = p.cluster,
                        name = p.name,
                        phone = p.phone,
                        cccd = p.cccd,
                        saltHex = hashed.saltHex,
                        hashHex = hashed.hashHex,
                        mustChangePassword = true,
                    )
                )
            }
        }
        SampleData.posts().forEach { post ->
            val ref = db.collection("posts").document(post.id)
            peoplePostsBatch.set(
                ref,
                mapOf(
                    "title" to post.title,
                    "category" to post.category,
                    "scope" to post.scope,
                    "cluster" to post.cluster,
                    "content" to post.content,
                    "dateIso" to post.dateIso,
                    "pinned" to post.pinned,
                    "imageData" to post.imageData,
                    "allowedEmails" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }
        peoplePostsBatch.commit().await()
    }

    // --- Posts ---
    suspend fun savePost(
        existing: PostDoc?,
        title: String,
        category: String,
        scope: String,
        cluster: String,
        content: String,
        imageData: String,
        allHouseholds: List<HouseholdDoc>,
    ): Result<String> {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập tiêu đề thông báo."))
        if (cleanContent.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập nội dung thông báo."))
        if (imageData.length > 250_000) {
            return Result.failure(IllegalArgumentException("Ảnh quá lớn để đồng bộ. Vui lòng chọn ảnh nhỏ hơn."))
        }

        val normalizedScope = if (scope == "Toàn thôn") "Toàn thôn" else "Theo cụm dân cư"
        val normalizedCluster = if (normalizedScope == "Toàn thôn") "" else cluster
        val allowedEmails = if (normalizedScope == "Toàn thôn") {
            emptyList()
        } else {
            allHouseholds
                .filter { it.cluster == normalizedCluster }
                .flatMap { it.memberEmails }
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(50)
        }

        val postId = existing?.id?.takeIf { it.isNotBlank() } ?: "P${System.currentTimeMillis()}"
        val docRef = db.collection("posts").document(postId)
        return try {
            requireUserId()
            if (existing == null) {
                val payload = mapOf(
                    "title" to cleanTitle.take(200),
                    "category" to category.take(80),
                    "scope" to normalizedScope,
                    "cluster" to normalizedCluster.take(100),
                    "content" to cleanContent.take(5000),
                    "dateIso" to DateUtils.nowIso(),
                    "pinned" to false,
                    "imageData" to imageData,
                    "allowedEmails" to allowedEmails,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "title" to cleanTitle.take(200),
                    "category" to category.take(80),
                    "scope" to normalizedScope,
                    "cluster" to normalizedCluster.take(100),
                    "content" to cleanContent.take(5000),
                    "dateIso" to existing.dateIso.ifBlank { DateUtils.nowIso() },
                    "pinned" to existing.pinned,
                    "imageData" to imageData,
                    "allowedEmails" to allowedEmails,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updates).await()
            }
            Result.success(if (existing == null) "Đã đăng thông báo trực tuyến." else "Đã cập nhật thông báo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể lưu thông báo.")))
        }
    }

    suspend fun togglePinPost(post: PostDoc): Result<String> {
        val docRef = db.collection("posts").document(post.id)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "pinned" to !post.pinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(if (post.pinned) "Đã bỏ ghim thông báo." else "Đã ghim thông báo lên đầu.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể cập nhật trạng thái ghim.")))
        }
    }

    suspend fun deletePost(postId: String): Result<String> {
        val docRef = db.collection("posts").document(postId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success("Đã xóa thông báo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể xóa thông báo.")))
        }
    }

    // --- Households ---
    suspend fun saveHousehold(
        existing: HouseholdDoc?,
        idInput: String,
        head: String,
        cluster: String,
        status: String,
        classify: String,
        address: String,
        memberEmailsRaw: String,
        inlineMembers: List<InlineMemberDraft>,
        knownUsers: List<UserProfileDoc>,
    ): Result<String> {
        val cleanId = (existing?.id ?: idInput.trim().uppercase()).replace(Regex("[^a-zA-Z0-9_-]"), "")
        val cleanHead = head.trim()
        if (cleanId.isBlank()) return Result.failure(IllegalArgumentException("Mã hộ chỉ gồm chữ, số, dấu gạch ngang/dưới."))
        if (cleanHead.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập họ tên chủ hộ."))

        val parsedEmails = memberEmailsRaw
            .split(',', ';', '\n', ' ')
            .map { it.trim().lowercase() }
            .filter { it.contains("@") && it.length >= 3 }
            .distinct()
            .take(20)

        val resolvedUids = buildList {
            if (existing != null) addAll(existing.memberUids)
            parsedEmails.forEach { email ->
                knownUsers.firstOrNull { it.email.trim().lowercase() == email }?.userId?.let { add(it) }
            }
        }.filter { it.isNotBlank() }.distinct().take(20)

        val hhRef = db.collection("households").document(cleanId)
        return try {
            requireUserId()
            if (existing == null) {
                if (hhRef.get().await().exists()) {
                    return Result.failure(IllegalStateException("Mã hộ $cleanId đã tồn tại trên hệ thống."))
                }
                val hhPayload = mapOf(
                    "head" to cleanHead.take(120),
                    "cluster" to cluster.take(100),
                    "status" to status,
                    "classify" to classify,
                    "address" to address.trim().take(250),
                    "memberEmails" to parsedEmails,
                    "memberUids" to resolvedUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                // Must await household write BEFORE creating inline people so exists() passes in rules
                hhRef.set(hhPayload).await()

                val validMembers = inlineMembers.filter { it.name.isNotBlank() }
                if (validMembers.isNotEmpty()) {
                    val base = System.currentTimeMillis()
                    val batch = db.batch()
                    validMembers.forEachIndexed { idx, draft ->
                        val pid = "NK${base + idx}"
                        val pRef = db.collection("people").document(pid)
                        batch.set(
                            pRef,
                            mapOf(
                                "name" to draft.name.trim().take(120),
                                "relation" to "Thành viên",
                                "dob" to draft.dob.trim().take(30),
                                "gender" to draft.gender,
                                "cccd" to "",
                                "phone" to "",
                                "householdId" to cleanId,
                                "cluster" to cluster.take(100),
                                "residence" to status,
                                "education" to "Chưa đi học",
                                "profession" to "Chưa qua đào tạo",
                                "religion" to "Không",
                                "partyMember" to false,
                                "job" to draft.job.ifBlank { "Khác" },
                                "tempStart" to "",
                                "tempEnd" to "",
                                "memberEmails" to parsedEmails,
                                "memberUids" to resolvedUids,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }
                    batch.commit().await()
                }
                Result.success("Đã thêm hộ $cleanId lên cơ sở dữ liệu chung.")
            } else {
                hhRef.update(
                    mapOf(
                        "head" to cleanHead.take(120),
                        "cluster" to cluster.take(100),
                        "status" to status,
                        "classify" to classify,
                        "address" to address.trim().take(250),
                        "memberEmails" to parsedEmails,
                        "memberUids" to resolvedUids,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                // Propagate updated memberEmails & memberUids to all residents of this household
                val membersSnap = db.collection("people").whereEqualTo("householdId", cleanId).get().await()
                if (!membersSnap.isEmpty) {
                    val batch = db.batch()
                    membersSnap.documents.forEach { doc ->
                        batch.update(
                            doc.reference,
                            mapOf(
                                "memberEmails" to parsedEmails,
                                "memberUids" to resolvedUids,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }
                    batch.commit().await()
                }
                Result.success("Đã cập nhật hộ $cleanId.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, hhRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể lưu hộ gia đình.")))
        }
    }

    suspend fun deleteHousehold(householdId: String): Result<String> {
        val hhRef = db.collection("households").document(householdId)
        return try {
            requireUserId()
            val members = db.collection("people").whereEqualTo("householdId", householdId).get().await()
            if (!members.isEmpty) {
                val batch = db.batch()
                members.documents.forEach { doc ->
                    batch.delete(doc.reference)
                    batch.delete(db.collection("citizen_accounts").document(doc.id))
                }
                batch.commit().await()
            }
            hhRef.delete().await()
            runCatching { db.collection("household_surveys").document(householdId).delete().await() }
            Result.success("Đã xóa hộ $householdId và nhân khẩu liên quan.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, hhRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể xóa hộ gia đình.")))
        }
    }

    suspend fun splitHousehold(
        newHouseholdId: String,
        newHeadName: String,
        cluster: String,
        classify: String,
        selectedMemberIds: List<String>,
        allPeople: List<PersonDoc>,
    ): Result<String> {
        val cleanId = newHouseholdId.trim().uppercase().replace(Regex("[^a-zA-Z0-9_-]"), "")
        val cleanHead = newHeadName.trim()
        if (cleanId.isBlank()) return Result.failure(IllegalArgumentException("Mã hộ mới không hợp lệ."))
        if (cleanHead.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập tên chủ hộ mới."))
        if (selectedMemberIds.isEmpty()) return Result.failure(IllegalArgumentException("Vui lòng chọn ít nhất 1 thành viên để tách hộ."))

        val newHhRef = db.collection("households").document(cleanId)
        return try {
            requireUserId()
            if (newHhRef.get().await().exists()) {
                return Result.failure(IllegalStateException("Mã hộ $cleanId đã tồn tại."))
            }

            // Create new household doc FIRST
            newHhRef.set(
                mapOf(
                    "head" to cleanHead.take(120),
                    "cluster" to cluster.take(100),
                    "status" to "Thường trú",
                    "classify" to classify,
                    "address" to "",
                    "memberEmails" to emptyList<String>(),
                    "memberUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            val batch = db.batch()
            selectedMemberIds.forEach { pid ->
                val p = allPeople.firstOrNull { it.id == pid } ?: return@forEach
                val nextRelation = if (p.name.equals(cleanHead, ignoreCase = true)) "Chủ hộ" else p.relation.ifBlank { "Thành viên" }
                batch.update(
                    db.collection("people").document(pid),
                    mapOf(
                        "householdId" to cleanId,
                        "cluster" to cluster.take(100),
                        "relation" to nextRelation,
                        "memberEmails" to emptyList<String>(),
                        "memberUids" to emptyList<String>(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            selectedMemberIds.forEach { pid ->
                runCatching {
                    val accRef = db.collection("citizen_accounts").document(pid)
                    if (accRef.get().await().exists()) {
                        accRef.update(
                            mapOf(
                                "householdId" to cleanId,
                                "cluster" to cluster.take(100),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        ).await()
                    }
                }
            }
            Result.success("Đã tách ${selectedMemberIds.size} nhân khẩu sang hộ mới $cleanId.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, newHhRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể tách hộ gia đình.")))
        }
    }

    // --- People ---
    suspend fun savePerson(
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
    ): Result<String> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập họ và tên nhân khẩu."))
        if (householdId.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng chọn hộ gia đình hợp lệ."))

        val personId = existing?.id?.takeIf { it.isNotBlank() } ?: "NK${System.currentTimeMillis()}"
        val docRef = db.collection("people").document(personId)
        return try {
            requireUserId()
            val hhSnap = db.collection("households").document(householdId).get().await()
            if (!hhSnap.exists()) {
                return Result.failure(IllegalStateException("Hộ gia đình $householdId chưa tồn tại."))
            }
            val memberEmails = (hhSnap.get("memberEmails") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty()
                .take(20)
            val memberUids = (hhSnap.get("memberUids") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty()
                .take(20)

            val commonFields = mapOf(
                "name" to cleanName.take(120),
                "relation" to relation.trim().ifBlank { "Thành viên" }.take(60),
                "dob" to dob.trim().take(30),
                "gender" to gender,
                "cccd" to cccd.trim().take(30),
                "phone" to phone.trim().take(30),
                "householdId" to householdId,
                "cluster" to cluster.take(100),
                "residence" to residence,
                "education" to education.take(60),
                "profession" to profession.take(80),
                "religion" to religion.take(60),
                "partyMember" to partyMember,
                "job" to job.ifBlank { "Khác" }.take(100),
                "tempStart" to tempStart.trim().take(30),
                "tempEnd" to tempEnd.trim().take(30),
                "memberEmails" to memberEmails,
                "memberUids" to memberUids,
            )

            val cleanCccd = cccd.trim().take(30)
            val cleanPhone = phone.trim().take(30)
            val accRef = db.collection("citizen_accounts").document(personId)
            if (existing == null) {
                docRef.set(
                    commonFields + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                if (cleanCccd.isNotBlank()) {
                    val hashed = PasswordHashHelper.hashPassword(cleanCccd)
                    accRef.set(
                        buildCitizenAccountCreateMap(
                            personId = personId,
                            householdId = householdId,
                            cluster = cluster,
                            name = cleanName,
                            phone = cleanPhone,
                            cccd = cleanCccd,
                            saltHex = hashed.saltHex,
                            hashHex = hashed.hashHex,
                            mustChangePassword = true,
                        )
                    ).await()
                }
            } else {
                docRef.update(
                    commonFields + mapOf(
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                val existingAcc = runCatching { accRef.get().await() }.getOrNull()
                if (existingAcc != null && existingAcc.exists()) {
                    accRef.update(
                        mapOf(
                            "householdId" to householdId.take(64),
                            "cluster" to cluster.take(100),
                            "name" to cleanName.take(120),
                            "phone" to cleanPhone,
                            "cccd" to cleanCccd,
                            "loginKeys" to PasswordHashHelper.buildLoginKeys(cleanPhone, cleanCccd),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                } else if (cleanCccd.isNotBlank()) {
                    val hashed = PasswordHashHelper.hashPassword(cleanCccd)
                    accRef.set(
                        buildCitizenAccountCreateMap(
                            personId = personId,
                            householdId = householdId,
                            cluster = cluster,
                            name = cleanName,
                            phone = cleanPhone,
                            cccd = cleanCccd,
                            saltHex = hashed.saltHex,
                            hashHex = hashed.hashHex,
                            mustChangePassword = true,
                        )
                    ).await()
                }
            }
            Result.success(if (existing == null) "Đã thêm nhân khẩu $cleanName." else "Đã cập nhật nhân khẩu $cleanName.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể lưu thông tin nhân khẩu.")))
        }
    }

    suspend fun deletePerson(personId: String): Result<String> {
        val docRef = db.collection("people").document(personId)
        return try {
            requireUserId()
            docRef.delete().await()
            runCatching { db.collection("citizen_accounts").document(personId).delete().await() }
            Result.success("Đã xóa nhân khẩu.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể xóa nhân khẩu.")))
        }
    }

    // --- Citizen Accounts (SĐT / Mã định danh + Password) ---
    suspend fun loginCitizen(
        identifierInput: String,
        passwordInput: String,
    ): Result<CitizenAccountDoc> {
        val cleanIdentifier = PasswordHashHelper.normalizeIdentifier(identifierInput)
        val cleanPassword = passwordInput.trim()
        if (cleanIdentifier.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập Số điện thoại hoặc Mã định danh (CCCD)."))
        }
        if (cleanPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập mật khẩu."))
        }
        return try {
            val uid = requireUserId()
            val querySnap = db.collection("citizen_accounts")
                .whereArrayContains("loginKeys", cleanIdentifier)
                .limit(1)
                .get()
                .await()
            val doc = querySnap.documents.firstOrNull()
                ?: return Result.failure(IllegalStateException("Không tìm thấy tài khoản bà con với SĐT hoặc Mã định danh này."))
            val account = doc.toObject(CitizenAccountDoc::class.java)
                ?: return Result.failure(IllegalStateException("Dữ liệu tài khoản không hợp lệ."))

            if (!PasswordHashHelper.verifyPassword(cleanPassword, account.passwordSalt, account.passwordHash)) {
                return Result.failure(
                    IllegalArgumentException("Mật khẩu không đúng. Nếu đăng nhập lần đầu, mật khẩu mặc định là Mã định danh (CCCD).")
                )
            }
            if (account.boundUid.isNotBlank() && account.boundUid != uid) {
                return Result.failure(
                    IllegalStateException("Tài khoản này đang được sử dụng trên thiết bị/Google khác. Vui lòng liên hệ Trưởng thôn để đặt lại mật khẩu và mở khóa liên kết.")
                )
            }

            db.collection("citizen_accounts").document(account.personId).update(
                mapOf(
                    "boundUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "linkedPersonId" to account.personId,
                    "householdId" to account.householdId,
                    "cluster" to account.cluster,
                    "name" to account.name.take(120),
                    "phone" to account.phone.take(30),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Result.success(account.copy(boundUid = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "citizen_accounts")
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Đăng nhập tài khoản bà con thất bại.")))
        }
    }

    suspend fun logoutCitizenSession(linkedPersonId: String): Result<String> {
        return try {
            val uid = requireUserId()
            if (linkedPersonId.isNotBlank()) {
                runCatching {
                    db.collection("citizen_accounts").document(linkedPersonId).update(
                        mapOf(
                            "boundUid" to "",
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "linkedPersonId" to "",
                    "householdId" to "",
                    "cluster" to "",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success("Đã đăng xuất tài khoản bà con.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users")
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể đăng xuất tài khoản bà con.")))
        }
    }

    suspend fun changeOwnCitizenPassword(
        personId: String,
        currentPassword: String,
        newPassword: String,
    ): Result<String> {
        if (personId.isBlank()) {
            return Result.failure(IllegalStateException("Bạn chưa đăng nhập tài khoản bà con."))
        }
        val cleanCurrent = currentPassword.trim()
        val cleanNew = newPassword.trim()
        if (cleanCurrent.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập mật khẩu hiện tại."))
        }
        PasswordHashHelper.validateNewPassword(cleanNew)?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        if (cleanCurrent == cleanNew) {
            return Result.failure(IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại."))
        }
        val docRef = db.collection("citizen_accounts").document(personId)
        return try {
            requireUserId()
            val snap = docRef.get().await()
            val account = snap.toObject(CitizenAccountDoc::class.java)
                ?: return Result.failure(IllegalStateException("Không tìm thấy tài khoản."))
            if (!PasswordHashHelper.verifyPassword(cleanCurrent, account.passwordSalt, account.passwordHash)) {
                return Result.failure(IllegalArgumentException("Mật khẩu hiện tại không đúng."))
            }
            val hashed = PasswordHashHelper.hashPassword(cleanNew)
            docRef.update(
                mapOf(
                    "passwordSalt" to hashed.saltHex,
                    "passwordHash" to hashed.hashHex,
                    "mustChangePassword" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success("Đã đổi mật khẩu thành công.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể đổi mật khẩu.")))
        }
    }

    suspend fun adminSetCitizenPassword(
        person: PersonDoc,
        newPasswordInput: String,
        resetToDefaultCccd: Boolean,
    ): Result<String> {
        val cleanPhone = person.phone.trim()
        val cleanCccd = person.cccd.trim()
        val loginKeys = PasswordHashHelper.buildLoginKeys(cleanPhone, cleanCccd)
        if (loginKeys.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Nhân khẩu ${person.name} chưa có SĐT hoặc Mã định danh (CCCD) để đăng nhập.")
            )
        }
        val targetPassword = if (resetToDefaultCccd) {
            cleanCccd.ifBlank {
                return Result.failure(IllegalArgumentException("Nhân khẩu ${person.name} chưa có Mã định danh (CCCD)."))
            }
        } else {
            val clean = newPasswordInput.trim()
            PasswordHashHelper.validateNewPassword(clean)?.let { msg ->
                return Result.failure(IllegalArgumentException(msg))
            }
            clean
        }

        val docRef = db.collection("citizen_accounts").document(person.id)
        return try {
            requireUserId()
            val hashed = PasswordHashHelper.hashPassword(targetPassword)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "householdId" to person.householdId.take(64),
                        "cluster" to person.cluster.take(100),
                        "name" to person.name.trim().ifBlank { "Cư dân" }.take(120),
                        "phone" to cleanPhone.take(30),
                        "cccd" to cleanCccd.take(30),
                        "loginKeys" to loginKeys,
                        "passwordSalt" to hashed.saltHex,
                        "passwordHash" to hashed.hashHex,
                        "mustChangePassword" to true,
                        "boundUid" to "",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    buildCitizenAccountCreateMap(
                        personId = person.id,
                        householdId = person.householdId,
                        cluster = person.cluster,
                        name = person.name,
                        phone = cleanPhone,
                        cccd = cleanCccd,
                        saltHex = hashed.saltHex,
                        hashHex = hashed.hashHex,
                        mustChangePassword = true,
                    )
                ).await()
            }
            Result.success(
                if (resetToDefaultCccd) {
                    "Đã đặt lại mật khẩu của ${person.name} về Mã định danh (CCCD)."
                } else {
                    "Đã cập nhật mật khẩu mới cho tài khoản ${person.name}."
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể đổi mật khẩu cho bà con.")))
        }
    }

    // --- Complaints & Household Link Flow ---
    suspend fun submitComplaint(
        session: UserSession,
        senderName: String,
        subject: String,
        content: String,
        householdId: String = session.householdId,
    ): Result<String> {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập nội dung phản ánh."))
        }
        val cleanSender = senderName.trim().ifBlank { session.name }
        val complaintId = "PA${System.currentTimeMillis()}"
        val docRef = db.collection("complaints").document(complaintId)
        return try {
            val uid = requireUserId()
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "userEmail" to session.email.lowercase().take(200),
                    "senderName" to cleanSender.take(120),
                    "householdId" to householdId.trim().uppercase().take(64),
                    "subject" to subject.take(120),
                    "content" to cleanContent.take(3000),
                    "dateIso" to DateUtils.nowIso(),
                    "status" to "Chờ xử lý",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success("Đã gửi phản ánh đến Ban điều hành thôn.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể gửi phản ánh.")))
        }
    }

    suspend fun toggleComplaintStatus(complaint: ComplaintDoc): Result<String> {
        val docRef = db.collection("complaints").document(complaint.id)
        val nextStatus = if (complaint.status == "Đã xử lý") "Chờ xử lý" else "Đã xử lý"
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success("Đã cập nhật trạng thái phản ánh.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể cập nhật trạng thái phản ánh.")))
        }
    }

    suspend fun approveHouseholdLink(complaint: ComplaintDoc): Result<String> {
        val targetHouseholdId = complaint.householdId.trim().uppercase()
        if (targetHouseholdId.isBlank()) {
            return Result.failure(IllegalArgumentException("Phản ánh này chưa ghi mã hộ gia đình hợp lệ."))
        }
        val hhRef = db.collection("households").document(targetHouseholdId)
        return try {
            requireUserId()
            val hhSnap = hhRef.get().await()
            if (!hhSnap.exists()) {
                return Result.failure(IllegalStateException("Không tìm thấy mã hộ $targetHouseholdId."))
            }
            val currentEmails = (hhSnap.get("memberEmails") as? List<*>)
                ?.filterIsInstance<String>()
                ?.map { it.lowercase() }
                .orEmpty()
            val currentUids = (hhSnap.get("memberUids") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty()
            val nextEmails = (currentEmails + complaint.userEmail.trim().lowercase())
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)
            val nextUids = (currentUids + complaint.userId)
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)

            hhRef.update(
                mapOf(
                    "memberEmails" to nextEmails,
                    "memberUids" to nextUids,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            val peopleSnap = db.collection("people").whereEqualTo("householdId", targetHouseholdId).get().await()
            if (!peopleSnap.isEmpty) {
                val batch = db.batch()
                peopleSnap.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "memberEmails" to nextEmails,
                            "memberUids" to nextUids,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
                batch.commit().await()
            }

            // Update requester's /users/{uid} householdId & cluster
            val hhCluster = hhSnap.getString("cluster").orEmpty()
            runCatching {
                db.collection("users").document(complaint.userId).update(
                    mapOf(
                        "householdId" to targetHouseholdId,
                        "cluster" to hhCluster,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            // Mark complaint resolved
            db.collection("complaints").document(complaint.id).update(
                mapOf(
                    "status" to "Đã xử lý",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Result.success("Đã liên kết tài khoản ${complaint.userEmail} vào hộ $targetHouseholdId.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, hhRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể duyệt liên kết hộ.")))
        }
    }

    // --- Admin Management ---
    suspend fun grantAdminByEmail(emailInput: String, knownUsers: List<UserProfileDoc>): Result<String> {
        val cleanEmail = emailInput.trim().lowercase()
        if (!cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập email Google hợp lệ."))
        }
        val targetUser = knownUsers.firstOrNull { it.email.trim().lowercase() == cleanEmail }
            ?: return Result.failure(
                IllegalStateException("Người dùng $cleanEmail cần đăng nhập ứng dụng ít nhất 1 lần trước khi được cấp quyền.")
            )
        val adminRef = db.collection("admins").document(targetUser.userId)
        return try {
            val myUid = requireUserId()
            adminRef.set(
                mapOf(
                    "userId" to targetUser.userId,
                    "email" to cleanEmail,
                    "addedBy" to myUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            runCatching {
                db.collection("users").document(targetUser.userId).update(
                    mapOf(
                        "role" to UserRole.ADMIN.key,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success("Đã cấp quyền Trưởng thôn cho $cleanEmail.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, adminRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể cấp quyền quản trị.")))
        }
    }

    suspend fun revokeAdmin(targetUid: String): Result<String> {
        val adminRef = db.collection("admins").document(targetUid)
        return try {
            requireUserId()
            adminRef.delete().await()
            runCatching {
                db.collection("users").document(targetUid).update(
                    mapOf(
                        "role" to UserRole.CITIZEN.key,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success("Đã thu hồi quyền quản trị.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, adminRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể thu hồi quyền quản trị.")))
        }
    }

    // --- Settings ---
    suspend fun updateSettings(settings: PortalSettings): Result<String> {
        val cleanTitle = settings.title.trim()
        val cleanSlogan = settings.slogan.trim()
        if (cleanTitle.isBlank()) return Result.failure(IllegalArgumentException("Tên đơn vị / tiêu đề không được để trống."))
        if (cleanSlogan.isBlank()) return Result.failure(IllegalArgumentException("Khẩu hiệu không được để trống."))

        localMediaPrefs.savePaths(settings.logoPath, settings.coverPath)
        val docRef = db.collection("settings").document("portal")
        return try {
            requireUserId()
            docRef.set(
                mapOf(
                    "title" to cleanTitle.take(200),
                    "slogan" to cleanSlogan.take(500),
                    "themeKey" to settings.themeKey,
                    "fontSize" to settings.fontSize.coerceIn(13, 20),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success("Đã lưu cài đặt cổng thông tin lên máy chủ.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(Exception(toVietnameseFirestoreMessage(e, "Không thể lưu cài đặt.")))
        }
    }

    // --- Household Self-Reported Surveys (Kinh tế, Phương tiện, Máy móc, Vật nuôi) ---
    suspend fun saveHouseholdSurvey(
        existing: HouseholdSurveyDoc?,
        household: HouseholdDoc,
        updaterName: String,
        draft: HouseholdSurveyDoc,
    ): Result<String> {
        val cleanHouseholdId = household.id.trim()
        if (cleanHouseholdId.isBlank()) {
            return Result.failure(IllegalArgumentException("Không xác định được mã hộ gia đình."))
        }
        val docRef = db.collection("household_surveys").document(cleanHouseholdId)
        return try {
            val uid = requireUserId()
            val existsRemote = existing != null || docRef.get().await().exists()
            val cleanUpdater = updaterName.trim().ifBlank { household.head }.take(120)
            val basePayload = mapOf<String, Any>(
                "householdId" to cleanHouseholdId,
                "cluster" to household.cluster.ifBlank { DomainConstants.CLUSTERS.first() }.take(100),
                "headName" to household.head.ifBlank { "Chủ hộ" }.take(120),
                "updatedByName" to cleanUpdater,
                "updatedByUid" to uid,
                "annualIncomeMillion" to draft.annualIncomeMillion.coerceIn(0, 100_000),
                "incomeBracket" to draft.incomeBracket.trim().take(80),
                "mainIncomeSource" to draft.mainIncomeSource.trim().take(120),
                "farmLandNote" to draft.farmLandNote.trim().take(200),
                "motorbikes" to draft.motorbikes.coerceIn(0, 100),
                "cars" to draft.cars.coerceIn(0, 50),
                "trucks" to draft.trucks.coerceIn(0, 50),
                "waterPumps" to draft.waterPumps.coerceIn(0, 100),
                "tractors" to draft.tractors.coerceIn(0, 50),
                "riceMills" to draft.riceMills.coerceIn(0, 50),
                "sprayersOrCutters" to draft.sprayersOrCutters.coerceIn(0, 100),
                "otherEquipment" to draft.otherEquipment.trim().take(250),
                "cows" to draft.cows.coerceIn(0, 5_000),
                "buffaloes" to draft.buffaloes.coerceIn(0, 5_000),
                "pigs" to draft.pigs.coerceIn(0, 10_000),
                "chickens" to draft.chickens.coerceIn(0, 100_000),
                "ducks" to draft.ducks.coerceIn(0, 100_000),
                "otherLivestock" to draft.otherLivestock.trim().take(250),
                "houseType" to draft.houseType.trim().take(80),
                "waterSource" to draft.waterSource.trim().take(80),
                "hasCleanToilet" to draft.hasCleanToilet,
                "hasHealthInsuranceAll" to draft.hasHealthInsuranceAll,
                "internetConnected" to draft.internetConnected,
                "otherNotes" to draft.otherNotes.trim().take(1_000),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!existsRemote) {
                docRef.set(basePayload + ("createdAt" to FieldValue.serverTimestamp())).await()
            } else {
                docRef.update(basePayload).await()
            }
            Result.success("Đã lưu thông tin kinh tế, tài sản & vật nuôi cho hộ $cleanHouseholdId.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existing == null) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            Result.failure(
                Exception(toVietnameseFirestoreMessage(e, "Không thể lưu thông tin kê khai của hộ."))
            )
        }
    }
}
