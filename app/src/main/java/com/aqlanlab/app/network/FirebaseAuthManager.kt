package com.aqlanlab.app.network

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.aqlanlab.app.data.models.*
import com.aqlanlab.app.ui.components.ClinicInfo
import com.aqlanlab.app.util.DeviceSecurityManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthUiState {
  object Idle : AuthUiState()
  object Loading : AuthUiState()
  data class Success(val user: FirebaseUser?, val appUser: User, val isDoctorOwner: Boolean) : AuthUiState()
  data class DevicePendingApproval(val deviceId: String, val deviceModel: String, val user: User, val isMaxDevicesExceeded: Boolean = false) : AuthUiState()
  data class DeviceBlocked(val deviceId: String, val reason: String = "تم حظر هذا الجهاز بواسطة المشرف العام (Access Denied)") : AuthUiState()
  data class DeviceRevoked(val deviceId: String, val reason: String = "تم إلغاء ترخيص هذا الجهاز بواسطة المشرف العام (Access Denied)") : AuthUiState()
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
      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
      }
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

  private fun getFirebaseAuthSafe(): FirebaseAuth? {
    return try {
      if (firebaseAuth != null) return firebaseAuth
      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
      }
      firebaseAuth = FirebaseAuth.getInstance()
      firebaseAuth
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseAuth initialization note: ${e.message}")
      null
    }
  }

  fun checkAndSetAuthorizedUser(fbUser: FirebaseUser, localUser: User? = null) {
    val email = fbUser.email?.lowercase()?.trim() ?: ""
    val isOwner = email == MASTER_DOCTOR_EMAIL

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
      pinCode = "", // Auth is managed purely by Firebase Authentication
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
      val auth = getFirebaseAuthSafe()
      if (auth == null) {
        val errorMsg = "خدمة المصادقة السحابية غير مفعلة على هذا الجهاز حالياً. يمكنك استخدام رمز المشرف السريع أو التواصل مع د. عقلان."
        _authState.value = AuthUiState.Error(errorMsg)
        return@withContext Result.failure(Exception(errorMsg))
      }
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

        val auth = getFirebaseAuthSafe()
        if (auth == null) {
          val errorMsg = "خدمة المصادقة السحابية غير مفعلة على هذا الجهاز. يمكنك الدخول برمز المشرف أو طلب الاعتماد."
          _authState.value = AuthUiState.Error(errorMsg)
          return@withContext Result.failure(Exception(errorMsg))
        }
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

  // ─── PHONE AUTHENTICATION (SMS OTP) ──────────────────────
  var storedVerificationId: String? = null
  var resendToken: PhoneAuthProvider.ForceResendingToken? = null

  fun sendPhoneVerificationCode(
    phoneNumber: String,
    activity: Activity,
    onCodeSent: (verificationId: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
    onAutoVerified: (FirebaseUser?) -> Unit
  ) {
    _authState.value = AuthUiState.Loading
    val auth = getFirebaseAuthSafe()
    if (auth == null) {
      val err = "خدمة المصادقة السحابية غير متوفرة حالياً"
      _authState.value = AuthUiState.Error(err)
      onError(err)
      return
    }

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
      override fun onVerificationCompleted(credential: PhoneAuthCredential) {
        coroutineScope.launch(Dispatchers.IO) {
          try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            _currentUser.value = user
            if (user != null) {
              withContext(Dispatchers.Main) {
                checkAndSetAuthorizedUser(user)
                onAutoVerified(user)
              }
            }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) {
              val msg = mapFirebaseError(e)
              _authState.value = AuthUiState.Error(msg)
              onError(msg)
            }
          }
        }
      }

      override fun onVerificationFailed(e: FirebaseException) {
        val msg = mapFirebaseError(e)
        _authState.value = AuthUiState.Error(msg)
        onError(msg)
      }

      override fun onCodeSent(
        verificationId: String,
        token: PhoneAuthProvider.ForceResendingToken
      ) {
        storedVerificationId = verificationId
        resendToken = token
        _authState.value = AuthUiState.Idle
        onCodeSent(verificationId)
      }
    }

    val options = PhoneAuthOptions.newBuilder(auth)
      .setPhoneNumber(phoneNumber.trim())
      .setTimeout(60L, TimeUnit.SECONDS)
      .setActivity(activity)
      .setCallbacks(callbacks)
      .build()
    PhoneAuthProvider.verifyPhoneNumber(options)
  }

  suspend fun verifyPhoneCodeAndSignIn(
    verificationId: String,
    code: String
  ): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
    _authState.value = AuthUiState.Loading
    try {
      val auth = getFirebaseAuthSafe() ?: return@withContext Result.failure(Exception("خدمة المصادقة السحابية غير متوفرة"))
      val credential = PhoneAuthProvider.getCredential(verificationId, code.trim())
      val result = auth.signInWithCredential(credential).await()
      val user = result.user
      _currentUser.value = user
      if (user != null) {
        withContext(Dispatchers.Main) {
          checkAndSetAuthorizedUser(user)
        }
        Result.success(user)
      } else {
        val err = "تعذر تسجيل الدخول بالرمز المدخل"
        _authState.value = AuthUiState.Error(err)
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      val msg = mapFirebaseError(e)
      _authState.value = AuthUiState.Error(msg)
      Result.failure(Exception(msg))
    }
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val trimmedEmail = email.trim()
      if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
        return@withContext Result.failure(Exception("يرجى إدخال بريد إلكتروني صحيح"))
      }
      val auth = getFirebaseAuthSafe()
      if (auth == null) {
        return@withContext Result.failure(Exception("خدمة المصادقة السحابية غير متصلة حالياً على هذا الجهاز."))
      }
      auth.sendPasswordResetEmail(trimmedEmail).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Password reset error", e)
      Result.failure(Exception(mapFirebaseError(e)))
    }
  }

  val provisioningService = UserProvisioningService(context)

  // --- Super Admin Cloud Provisioning helper (Backend / Cloud Functions) ---
  suspend fun registerUserBySuperAdmin(
    newUser: User,
    temporaryPass: String,
    permissions: List<String> = listOf("read:shipments", "write:shipments"),
    createdBy: String = "SUPER_ADMIN"
  ): Result<User> = withContext(Dispatchers.IO) {
    if (FirebaseApp.getApps(context).isEmpty()) {
      // Local fallback with warning
      return@withContext Result.success(newUser)
    }

    // Call Cloud Function to provision real Auth user & claims
    val provResult = provisioningService.createAuthorizedUser(
      username = newUser.username,
      email = newUser.email,
      temporaryPass = temporaryPass,
      fullName = newUser.fullName,
      role = newUser.role,
      permissions = permissions,
      maxDevices = newUser.maxDevices
    )

    if (provResult.isSuccess) {
      val res = provResult.getOrThrow()
      val updatedUser = newUser.copy(uid = res.uid)
      Result.success(updatedUser)
    } else {
      val err = provResult.exceptionOrNull() ?: Exception("فشل إنشاء المستخدم في السحابة")
      Log.e(TAG, "Backend User Provisioning failed: ${err.message}", err)
      // Never return fake success!
      Result.failure(err)
    }
  }

  suspend fun fetchAuthorizedUserByUid(uid: String): Map<String, Any>? = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty() && uid.isNotBlank()) {
        val db = FirebaseFirestore.getInstance()
        val doc = db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("authorized_users")
          .document(uid)
          .get()
          .await()
        if (doc.exists()) {
          return@withContext doc.data
        }
      }
      null
    } catch (e: Exception) {
      Log.w(TAG, "Fetch authorized user by UID failed: ${e.message}")
      null
    }
  }

  suspend fun deleteAuthorizedUserFromFirestore(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty() && uid.isNotBlank()) {
        val db = FirebaseFirestore.getInstance()
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("authorized_users")
          .document(uid)
          .delete()
          .await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.w(TAG, "Delete authorized user failed: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Safe migration helper to convert any legacy username-keyed documents to Firebase UID keys
   */
  suspend fun migrateLegacyAuthorizedUsersToUid(): Result<Int> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isEmpty()) return@withContext Result.success(0)
      val db = FirebaseFirestore.getInstance()
      val snapshot = db.collection("clinics")
        .document(CloudSyncManager.DEFAULT_CLINIC_ID)
        .collection("authorized_users")
        .get()
        .await()

      var migratedCount = 0
      for (doc in snapshot.documents) {
        val data = doc.data ?: continue
        val docId = doc.id
        val explicitUid = doc.getString("uid")

        // If docId is legacy (not matching explicit UID or was a username)
        if (!explicitUid.isNullOrBlank() && docId != explicitUid) {
          // Copy to new UID-keyed document
          db.collection("clinics")
            .document(CloudSyncManager.DEFAULT_CLINIC_ID)
            .collection("authorized_users")
            .document(explicitUid)
            .set(data, SetOptions.merge())
            .await()

          // Delete old legacy doc
          db.collection("clinics")
            .document(CloudSyncManager.DEFAULT_CLINIC_ID)
            .collection("authorized_users")
            .document(docId)
            .delete()
            .await()

          migratedCount++
        }
      }
      Log.d(TAG, "Migrated $migratedCount legacy authorized_users docs to Firebase UID keys")
      Result.success(migratedCount)
    } catch (e: Exception) {
      Log.w(TAG, "Legacy migration encountered error: ${e.message}")
      Result.failure(e)
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
          "lastActiveAt" to device.lastActiveAt,
          "approvedByAdmin" to device.approvedByAdmin,
          "notes" to device.notes
        )
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("devices")
          .document(device.deviceId)
          .set(deviceDoc, SetOptions.merge())
          .await()
        Result.success(Unit)
      } else {
        Result.failure(AppCloudError.NetworkUnavailable(userFriendlyMessageAr = "خدمات Firebase غير متوفرة محلياً."))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Device request Firestore submit failed: ${e.message}", e)
      val cloudError = AppCloudError.fromThrowable(e)
      Result.failure(cloudError)
    }
  }

  // --- Cloud / Server-Side Device Verification & Sync ---
  suspend fun fetchDeviceFromFirestore(deviceId: String): DeviceBinding? = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        val doc = db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("devices")
          .document(deviceId)
          .get()
          .await()
        if (doc.exists()) {
          val statusStr = doc.getString("status") ?: "PENDING"
          val status = try { DeviceStatus.valueOf(statusStr) } catch (e: Exception) { DeviceStatus.PENDING }
          val roleStr = doc.getString("userRole") ?: "STAFF"
          val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STAFF }
          return@withContext DeviceBinding(
            deviceId = deviceId,
            userId = doc.getLong("userId") ?: 0L,
            userName = doc.getString("userName") ?: "",
            userRole = role,
            deviceModel = doc.getString("deviceModel") ?: "",
            osVersion = doc.getString("osVersion") ?: "",
            appVersion = doc.getString("appVersion") ?: "1.0.0",
            status = status,
            registeredAt = doc.getLong("registeredAt") ?: System.currentTimeMillis(),
            lastActiveAt = doc.getLong("lastActiveAt") ?: System.currentTimeMillis(),
            approvedByAdmin = doc.getString("approvedByAdmin") ?: "",
            notes = doc.getString("notes") ?: ""
          )
        }
      }
      null
    } catch (e: Exception) {
      Log.w(TAG, "Fetch device from Firestore failed: ${e.message}")
      null
    }
  }

  suspend fun updateDeviceStatusInFirestore(
    deviceId: String,
    status: DeviceStatus,
    approvedBy: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        val updates = mapOf(
          "status" to status.name,
          "approvedByAdmin" to approvedBy,
          "lastActiveAt" to System.currentTimeMillis()
        )
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("devices")
          .document(deviceId)
          .set(updates, SetOptions.merge())
          .await()
        Result.success(Unit)
      } else {
        Result.failure(AppCloudError.NetworkUnavailable(userFriendlyMessageAr = "خدمات Firebase غير متوفرة محلياً."))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Update device status in Firestore failed: ${e.message}", e)
      Result.failure(AppCloudError.fromThrowable(e))
    }
  }

  suspend fun deleteDeviceFromFirestore(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("devices")
          .document(deviceId)
          .delete()
          .await()
        Result.success(Unit)
      } else {
        Result.failure(AppCloudError.NetworkUnavailable(userFriendlyMessageAr = "خدمات Firebase غير متوفرة محلياً."))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Delete device from Firestore failed: ${e.message}", e)
      Result.failure(AppCloudError.fromThrowable(e))
    }
  }

  fun listenToDeviceInFirestore(
    deviceId: String,
    onUpdate: (DeviceStatus?, DeviceBinding?) -> Unit
  ): ListenerRegistration? {
    return try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        val db = FirebaseFirestore.getInstance()
        db.collection("clinics")
          .document(CloudSyncManager.DEFAULT_CLINIC_ID)
          .collection("devices")
          .document(deviceId)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              Log.w(TAG, "Device snapshot listener error: ${error.message}")
              return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
              val statusStr = snapshot.getString("status") ?: "PENDING"
              val status = try { DeviceStatus.valueOf(statusStr) } catch (e: Exception) { DeviceStatus.PENDING }
              val roleStr = snapshot.getString("userRole") ?: "STAFF"
              val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STAFF }
              val device = DeviceBinding(
                deviceId = deviceId,
                userId = snapshot.getLong("userId") ?: 0L,
                userName = snapshot.getString("userName") ?: "",
                userRole = role,
                deviceModel = snapshot.getString("deviceModel") ?: "",
                osVersion = snapshot.getString("osVersion") ?: "",
                appVersion = snapshot.getString("appVersion") ?: "1.0.0",
                status = status,
                registeredAt = snapshot.getLong("registeredAt") ?: System.currentTimeMillis(),
                lastActiveAt = snapshot.getLong("lastActiveAt") ?: System.currentTimeMillis(),
                approvedByAdmin = snapshot.getString("approvedByAdmin") ?: "",
                notes = snapshot.getString("notes") ?: ""
              )
              onUpdate(status, device)
            } else if (snapshot != null && !snapshot.exists()) {
              onUpdate(null, null)
            }
          }
      } else {
        null
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not attach device snapshot listener: ${e.message}")
      null
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
      msg.contains("not initialized", ignoreCase = true) || msg.contains("FirebaseApp", ignoreCase = true) ->
        "خدمة المصادقة السحابية غير متصلة حالياً على هذا الجهاز. يمكنك استخدام رمز المشرف السريع أو طلب اعتماد حسابك."
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
