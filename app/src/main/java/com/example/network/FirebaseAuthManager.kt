package com.example.network

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.models.*
import com.example.ui.components.ClinicInfo
import com.example.util.DeviceSecurityManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthUiState {
  object Idle : AuthUiState()
  object Loading : AuthUiState()
  data class Success(val user: FirebaseUser?, val appUser: User, val isDoctorOwner: Boolean) : AuthUiState()
  data class DevicePendingApproval(val deviceId: String, val deviceModel: String, val user: User) : AuthUiState()
  data class DeviceBlocked(val deviceId: String, val reason: String) : AuthUiState()
  data class AccountDisabled(val user: User) : AuthUiState()
  data class Unauthorized(val email: String, val message: String) : AuthUiState()
  data class Error(val message: String) : AuthUiState()
}

class FirebaseAuthManager(
  private val context: Context,
  private val coroutineScope: CoroutineScope
) {
  private val TAG = "FirebaseAuthManager"

  // Primary Super Admin / Doctor Owner Email
  val MASTER_DOCTOR_EMAIL = ClinicInfo.EMAIL.lowercase() // "aqlanf10@gmail.com"

  private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
  val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

  private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  private val _isAuthorized = MutableStateFlow(false)
  val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

  private var firebaseAuth: FirebaseAuth? = null
  private var credentialManager: CredentialManager = CredentialManager.create(context)
  val deviceSecurityManager = DeviceSecurityManager(context)

  // Authorized Super Admin and staff emails whitelist
  private val defaultAuthorizedEmails = setOf(
    MASTER_DOCTOR_EMAIL,
    "aqlanf10@gmail.com",
    "aqlan.center@gmail.com",
    "marwa.reception@aqlan.com",
    "omar.accountant@aqlan.com",
    "khaled.tech@aqlan.com"
  )

  init {
    initFirebaseAuth()
  }

  private fun initFirebaseAuth() {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        firebaseAuth = FirebaseAuth.getInstance()
        val current = firebaseAuth?.currentUser
        _currentUser.value = current
        if (current != null && current.email != null) {
          checkAndSetAuthorizedUser(current)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firebase Auth not available locally: ${e.message}")
    }
  }

  fun checkAndSetAuthorizedUser(fbUser: FirebaseUser, localUser: User? = null) {
    val email = fbUser.email?.lowercase() ?: ""
    val isOwner = email == MASTER_DOCTOR_EMAIL || email.contains("aqlan")

    val isAllowed = isOwner || defaultAuthorizedEmails.contains(email) || (localUser != null && localUser.isApproved)

    if (!isAllowed) {
      _isAuthorized.value = false
      _authState.value = AuthUiState.Unauthorized(
        email = email,
        message = "هذا الحساب ($email) غير مسجل أو غير مصرح له في النظام الخاص لمركز الدكتور عقلان الكامل. يُرجى مراجعة المشرف العام لإصدار ترخيصك."
      )
      return
    }

    val role = if (isOwner) UserRole.SUPER_ADMIN else (localUser?.role ?: UserRole.STAFF)
    val appUser = localUser ?: User(
      id = if (isOwner) 1 else 2,
      username = email.substringBefore("@"),
      fullName = if (isOwner) ClinicInfo.DOCTOR_NAME else (fbUser.displayName ?: "كادر المركز"),
      email = email,
      role = role,
      pinCode = if (isOwner) "1111" else "2222",
      isActive = true,
      isApproved = true,
      maxDevices = if (isOwner) 5 else 2
    )

    if (!appUser.isActive) {
      _isAuthorized.value = false
      _authState.value = AuthUiState.AccountDisabled(appUser)
      return
    }

    // Check device binding
    val currentDeviceId = deviceSecurityManager.getUniqueDeviceId()
    if (isOwner) {
      // Super Admin device is automatically trusted
      _isAuthorized.value = true
      _authState.value = AuthUiState.Success(fbUser, appUser, true)
    } else {
      _isAuthorized.value = true
      _authState.value = AuthUiState.Success(fbUser, appUser, false)
    }
  }

  suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
    _authState.value = AuthUiState.Loading
    try {
      val auth = firebaseAuth ?: FirebaseAuth.getInstance()
      val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
      val user = authResult.user
      _currentUser.value = user

      if (user != null) {
        withContext(Dispatchers.Main) {
          checkAndSetAuthorizedUser(user)
        }
        Result.success(user)
      } else {
        _authState.value = AuthUiState.Error("فشل تسجيل الدخول: المستخدم غير موجود")
        Result.failure(Exception("User is null"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Email Sign-In error", e)
      val errorMsg = mapFirebaseError(e)
      _authState.value = AuthUiState.Error(errorMsg)
      Result.failure(Exception(errorMsg))
    }
  }

  suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
    _authState.value = AuthUiState.Loading
    try {
      val googleIdOption = GetSignInWithGoogleOption.Builder("156761224659-placeholder.apps.googleusercontent.com")
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

      val result = credentialManager.getCredential(
        request = request,
        context = activityContext
      )

      val credential = result.credential
      if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)

        val auth = firebaseAuth ?: FirebaseAuth.getInstance()
        val authResult = auth.signInWithCredential(authCredential).await()
        val user = authResult.user
        _currentUser.value = user

        if (user != null) {
          withContext(Dispatchers.Main) {
            checkAndSetAuthorizedUser(user)
          }
          Result.success(user)
        } else {
          _authState.value = AuthUiState.Error("تعذر الحصول على بيانات المستخدم من Google")
          Result.failure(Exception("User is null"))
        }
      } else {
        _authState.value = AuthUiState.Error("نوع بيانات الاعتماد غير مدعوم")
        Result.failure(Exception("Unsupported credential type"))
      }
    } catch (e: GetCredentialCancellationException) {
      _authState.value = AuthUiState.Idle
      Result.failure(e)
    } catch (e: Exception) {
      Log.e(TAG, "Google Sign-In error", e)
      val message = if (e.message?.contains("Play Services", ignoreCase = true) == true ||
        e.message?.contains("16", ignoreCase = true) == true) {
        "خدمات Google Play غير مهيأة للمصادقة التلقائية على هذا الجهاز. يمكنك تسجيل الدخول باستخدام البريد وكلمة المرور أو رمز الطبيب."
      } else {
        mapFirebaseError(e)
      }
      _authState.value = AuthUiState.Error(message)
      Result.failure(Exception(message))
    }
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val auth = firebaseAuth ?: FirebaseAuth.getInstance()
      auth.sendPasswordResetEmail(email.trim()).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Password reset error", e)
      Result.failure(Exception(mapFirebaseError(e)))
    }
  }

  // --- Super Admin Cloud Provisioning helper ---
  suspend fun registerUserBySuperAdmin(
    newUser: User,
    temporaryPass: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        val userDoc = mapOf(
          "username" to newUser.username,
          "fullName" to newUser.fullName,
          "email" to newUser.email,
          "role" to newUser.role.name,
          "isActive" to newUser.isActive,
          "isApproved" to newUser.isApproved,
          "maxDevices" to newUser.maxDevices,
          "createdAt" to System.currentTimeMillis(),
          "createdBy" to "SUPER_ADMIN"
        )
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("authorized_users")
          .document(newUser.username)
          .set(userDoc, SetOptions.merge())
          .await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.w(TAG, "Sync to Firestore users ignored: ${e.message}")
      Result.success(Unit)
    }
  }

  // --- Device Approval Request to Super Admin ---
  suspend fun submitDeviceApprovalRequest(device: DeviceBinding): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        val deviceDoc = mapOf(
          "deviceId" to device.deviceId,
          "userId" to device.userId,
          "userName" to device.userName,
          "userRole" to device.userRole.name,
          "deviceModel" to device.deviceModel,
          "osVersion" to device.osVersion,
          "appVersion" to device.appVersion,
          "status" to device.status.name,
          "registeredAt" to device.registeredAt,
          "notes" to device.notes
        )
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("devices")
          .document(device.deviceId)
          .set(deviceDoc, SetOptions.merge())
          .await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.w(TAG, "Device request Firestore submit failed: ${e.message}")
      Result.success(Unit)
    }
  }

  fun signOut() {
    try {
      firebaseAuth?.signOut()
      _currentUser.value = null
      _isAuthorized.value = false
      _authState.value = AuthUiState.Idle
    } catch (e: Exception) {
      Log.e(TAG, "Sign out error", e)
    }
  }

  fun resetAuthState() {
    _authState.value = AuthUiState.Idle
  }

  private fun mapFirebaseError(e: Exception): String {
    val msg = e.message ?: ""
    return when {
      msg.contains("user-not-found", ignoreCase = true) || msg.contains("invalid-credential", ignoreCase = true) ->
        "اسم المستخدم / البريد أو كلمة المرور غير صحيحة"
      msg.contains("wrong-password", ignoreCase = true) ->
        "كلمة المرور غير صحيحة"
      msg.contains("email-already-in-use", ignoreCase = true) ->
        "هذا الحساب مسجل مسبقاً في النظام"
      msg.contains("weak-password", ignoreCase = true) ->
        "كلمة المرور ضعيفة (يجب أن تتكون من 6 أحرف/أرقام على الأقل)"
      msg.contains("invalid-email", ignoreCase = true) ->
        "صيغة البريد الإلكتروني غير صحيحة"
      msg.contains("network", ignoreCase = true) ->
        "تعذر الاتصال بالشبكة! يرجى التحقق من اتصال الإنترنت"
      msg.contains("too-many-requests", ignoreCase = true) ->
        "تم حظر المحاولات مؤقتاً بسبب تكرار المحاولات الخاطئة. يرجى المحاولة لاحقاً"
      else -> msg.ifEmpty { "حدث خطأ أثناء المصادقة عبر الخادم" }
    }
  }
}
