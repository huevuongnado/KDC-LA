package com.example.ui.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.PendingCitizenCredentials
import java.security.MessageDigest
import com.example.ui.theme.PortalTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val EXPECTED_FIREBASE_SHA1 =
    "D4:A4:7F:5A:11:9B:33:30:56:96:AE:2A:60:9B:0D:BF:6F:66:27:9E"

@Suppress("DEPRECATION")
private fun getAppSigningSha1(context: Context): String {
    return runCatching {
        val pm = context.packageManager
        val pkgName = context.packageName
        val signatureBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            val certs = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
            certs?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        }
        signatureBytes?.let { bytes ->
            MessageDigest.getInstance("SHA-1")
                .digest(bytes)
                .joinToString(":") { "%02X".format(it) }
        }.orEmpty()
    }.getOrDefault("")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activityOrContext = context.findActivity() ?: context
            val cred = credentialManager.getCredential(activityOrContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Thiếu cấu hình đăng nhập Google (default_web_client_id).")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Không tìm thấy Activity để mở hộp thoại đăng nhập Google.")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Loại thông tin đăng nhập không được hỗ trợ.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val installedSha1 = getAppSigningSha1(context)
            val detail = e.errorMessage?.toString()?.trim().orEmpty()
            if (installedSha1.isNotBlank() && !installedSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)) {
                onAuthError(
                    "Không thể hoàn tất đăng nhập Google vì mã SHA-1 của bản APK này ($installedSha1) chưa được thêm vào Firebase Console (project gen-lang-client-0789931015). Vui lòng sao chép mã SHA-1 bên dưới và thêm vào Firebase."
                )
            } else if (detail.isNotBlank()) {
                onAuthError("Chưa hoàn tất đăng nhập Google ($detail).")
            } else {
                onAuthCancelled()
            }
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google account credential available on device: ${e.message}", e)
            val installedSha1 = getAppSigningSha1(context)
            val shaHint = if (installedSha1.isNotBlank() && !installedSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)) {
                " Lưu ý: Bản APK này có SHA-1 ($installedSha1) khác với cấu hình Firebase mặc định — hãy thêm SHA-1 này vào Firebase Console."
            } else {
                ""
            }
            onAuthError(
                "Thiết bị hiện chưa lấy được thông tin tài khoản Google khả dụng.$shaHint"
            )
        } catch (e: GetCredentialException) {
            Log.e("Auth", "CredentialManager error [${e.type}]: ${e.errorMessage}", e)
            val installedSha1 = getAppSigningSha1(context)
            val rawMsg = e.errorMessage?.toString()?.trim().orEmpty().ifBlank { e.localizedMessage.orEmpty() }
            val shaNote = if (installedSha1.isNotBlank() && !installedSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)) {
                "\nMã SHA-1 bản cài đặt: $installedSha1 (cần thêm vào Firebase Console)."
            } else ""
            onAuthError(
                "Đăng nhập Google thất bại${if (rawMsg.isNotBlank()) ": $rawMsg" else " (${e.type})"}.$shaNote"
            )
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Đăng nhập Google thất bại. Vui lòng thử lại.")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: (PendingCitizenCredentials?) -> Unit,
    onPendingCitizenChanged: (PendingCitizenCredentials?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingSilent by remember { mutableStateOf(true) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }
    var googleErrorMessage by remember { mutableStateOf<String?>(null) }

    var citizenIdentifier by remember { mutableStateOf("") }
    var citizenPassword by remember { mutableStateOf("") }
    var showCitizenPassword by remember { mutableStateOf(false) }
    var isCitizenConnecting by remember { mutableStateOf(false) }
    var citizenErrorMessage by remember { mutableStateOf<String?>(null) }
    val appSha1 = remember(context) { getAppSigningSha1(context) }
    val sha1Mismatch = appSha1.isNotBlank() && !appSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true)
    var showSha1Helper by remember(sha1Mismatch) { mutableStateOf(sha1Mismatch) }
    var sha1Copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSilent = false
                onAuthSuccess(null)
            },
            onUnauthenticated = {
                isCheckingSilent = false
            },
            scope = scope,
        )
    }

    val submitCitizenLogin: () -> Unit = {
        val cleanId = citizenIdentifier.trim()
        val cleanPwd = citizenPassword.trim()
        when {
            cleanId.isBlank() -> {
                citizenErrorMessage = "Vui lòng nhập Số điện thoại hoặc Mã định danh (CCCD)."
            }
            cleanPwd.isBlank() -> {
                citizenErrorMessage = "Vui lòng nhập mật khẩu (lần đầu là Số CCCD / Mã định danh)."
            }
            Firebase.auth.currentUser != null -> {
                val pending = PendingCitizenCredentials(cleanId, cleanPwd)
                onPendingCitizenChanged(pending)
                onAuthSuccess(pending)
            }
            else -> {
                val pending = PendingCitizenCredentials(cleanId, cleanPwd)
                citizenErrorMessage = null
                googleErrorMessage = null
                isCitizenConnecting = true
                onPendingCitizenChanged(pending)
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                        isCitizenConnecting = false
                        onAuthSuccess(pending)
                    },
                    onAuthError = { msg ->
                        isCitizenConnecting = false
                        onPendingCitizenChanged(null)
                        citizenErrorMessage = msg
                    },
                    scope = scope,
                    onAuthCancelled = {
                        isCitizenConnecting = false
                        onPendingCitizenChanged(null)
                        citizenErrorMessage =
                            "Đã hủy bước kết nối phiên bảo mật trên máy. Vui lòng chọn tài khoản kết nối thiết bị để xác thực SĐT / Mã định danh."
                    },
                )
            }
        }
    }

    val preset = PortalTheme.colors.themePreset
    val semColors = PortalTheme.colors
    val busy = isGoogleSigningIn || isCitizenConnecting

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Hero Banner Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Brush.linearGradient(listOf(preset.headerStart, preset.headerEnd)))
                        .padding(20.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HomeWork,
                                contentDescription = "Biểu tượng Thôn Long An",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Text(
                            text = "CỔNG THÔNG TIN TRỰC TUYẾN",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.88f),
                        )
                        Text(
                            text = "Thôn Long An",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Xã Minh Long, tỉnh Quảng Ngãi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.90f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // 1. Google Sign-In Card (On Top)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Đăng nhập bằng Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Dành cho Trưởng thôn, Ban điều hành hoặc bà con sử dụng tài khoản Google.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (!googleErrorMessage.isNullOrBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = googleErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        if (isCheckingSilent) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Đang kiểm tra phiên đăng nhập...",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    googleErrorMessage = null
                                    citizenErrorMessage = null
                                    onPendingCitizenChanged(null)
                                    isGoogleSigningIn = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isGoogleSigningIn = false
                                            onAuthSuccess(null)
                                        },
                                        onAuthError = { msg ->
                                            isGoogleSigningIn = false
                                            googleErrorMessage = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isGoogleSigningIn = false
                                        },
                                    )
                                },
                                enabled = !busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("google_sign_in_button"),
                            ) {
                                if (isGoogleSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Filled.Login, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Đăng nhập bằng Google", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Divider between Google Sign-In and Citizen SĐT/CCCD Login
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "HOẶC",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                // 2. Citizen SĐT / Mã định danh + Password Card (Below)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Bà con không có tài khoản Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Text(
                            text = "Đăng nhập bằng Số điện thoại hoặc Số định danh điện tử (CCCD) cùng với mật khẩu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = semColors.infoBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = semColors.infoText,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Mật khẩu đăng nhập lần đầu chính là Số CCCD / Mã định danh. Sau khi đăng nhập có thể đổi mật khẩu mới; nếu quên, nhờ Trưởng thôn cấp lại.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semColors.infoText,
                                )
                            }
                        }

                        OutlinedTextField(
                            value = citizenIdentifier,
                            onValueChange = {
                                citizenIdentifier = it
                                citizenErrorMessage = null
                            },
                            label = { Text("Số điện thoại hoặc Số định danh (CCCD)") },
                            placeholder = { Text("VD: 0912000001 hoặc 049070001001") },
                            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_citizen_identifier_input"),
                        )

                        OutlinedTextField(
                            value = citizenPassword,
                            onValueChange = {
                                citizenPassword = it
                                citizenErrorMessage = null
                            },
                            label = { Text("Mật khẩu") },
                            placeholder = { Text("Lần đầu nhập Số CCCD / Mã định danh") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showCitizenPassword = !showCitizenPassword }) {
                                    Icon(
                                        imageVector = if (showCitizenPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showCitizenPassword) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                    )
                                }
                            },
                            visualTransformation = if (showCitizenPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (!busy && !isCheckingSilent) submitCitizenLogin()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_citizen_password_input"),
                        )

                        if (!citizenErrorMessage.isNullOrBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = citizenErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = submitCitizenLogin,
                            enabled = !busy && !isCheckingSilent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("auth_citizen_login_button"),
                        ) {
                            if (isCitizenConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.PersonPin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Đăng nhập bằng SĐT / Mã định danh", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (appSha1.isNotBlank()) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (sha1Mismatch) semColors.amberBg else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = { showSha1Helper = !showSha1Helper },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize(),
                            ) {
                                Text(
                                    text = if (sha1Mismatch) {
                                        "APK cài ngoài cần thêm SHA-1 vào Firebase để đăng nhập"
                                    } else {
                                        "Thông tin chữ ký ứng dụng (SHA-1)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sha1Mismatch) semColors.amberText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = if (showSha1Helper) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                )
                            }
                            AnimatedVisibility(visible = showSha1Helper) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (sha1Mismatch) {
                                            "Bản APK này được ký bằng khóa khác (thường gặp khi build trên GitHub chưa có tệp keystore). Hãy sao chép mã SHA-1 dưới đây và thêm vào Firebase Console → Project Settings → Ứng dụng Android (com.aistudio.longan.kdcxmp) → Add fingerprint."
                                        } else {
                                            "Mã SHA-1 hiện tại của bản cài đặt đã khớp với cấu hình Firebase."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = appSha1,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(10.dp),
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("APK SHA-1", appSha1))
                                            sha1Copied = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (sha1Copied) "Đã sao chép mã SHA-1" else "Sao chép mã SHA-1")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
