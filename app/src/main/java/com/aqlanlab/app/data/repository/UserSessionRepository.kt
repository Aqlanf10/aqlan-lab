package com.aqlanlab.app.data.repository

import android.content.Context
import android.util.Log
import com.aqlanlab.app.data.dao.AuditLogDao
import com.aqlanlab.app.data.dao.DeviceBindingDao
import com.aqlanlab.app.data.dao.UserDao
import com.aqlanlab.app.data.models.*
import com.aqlanlab.app.network.FirebaseAuthManager
import com.aqlanlab.app.ui.components.ClinicInfo
import com.aqlanlab.app.util.DeviceSecurityManager
import com.aqlanlab.app.util.SecurityUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class UserSessionState {
  /**
   * Initial state MUST be Unauthenticated so the app redirects to LoginScreen immediately.
   */
  object Unauthenticated : UserSessionState()
  object Loading : UserSessionState()
  data class Authenticated(
    val user: User,
    val firebaseUser: FirebaseUser?,
    val role: UserRole,
    val isApproved: Boolean
  ) : UserSessionState()

  data class PendingApproval(
    val user: User,
    val message: String = "الحساب بانتظار موافقة المشرف العام (د. عقلان)"
  ) : UserSessionState()

  data class Disabled(
    val user: User,
    val reason: String = "تم تعطيل هذا الحساب من قبل المشرف العام"
  ) : UserSessionState()

  data class DevicePendingApproval(
    val user: User,
    val deviceBinding: DeviceBinding,
    val message: String
  ) : UserSessionState()

  data class DeviceBlocked(
    val deviceId: String,
    val reason: String
  ) : UserSessionState()

  data class Error(val message: String) : UserSessionState()
}

class UserSessionRepository(
  private val context: Context,
  private val userDao: UserDao,
  private val deviceBindingDao: DeviceBindingDao,
  private val auditLogDao: AuditLogDao,
  private val firebaseAuthManager: FirebaseAuthManager,
  private val externalScope: CoroutineScope
) {
  private val TAG = "UserSessionRepo"

  // Guaranteed initial unauthenticated state
  private val _sessionState = MutableStateFlow<UserSessionState>(UserSessionState.Unauthenticated)
  val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

  private var firebaseAuth: FirebaseAuth? = null
  val deviceSecurityManager = DeviceSecurityManager(context)

  init {
    initializeFirebaseAuth()
  }

  /**
   * Initializes Firebase Auth instance safely.
   * Keeps session state strictly Unauthenticated on cold start.
   */
  fun initializeFirebaseAuth() {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        firebaseAuth = FirebaseAuth.getInstance()
        Log.d(TAG, "Firebase Authentication initialized successfully.")
      } else {
        Log.w(TAG, "FirebaseApp is not initialized yet in this environment.")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error initializing FirebaseAuth: ${e.message}")
    }
  }

  /**
   * Evaluates user profile, role claims, and approval status.
   */
  suspend fun loadUserProfileAndRole(
    firebaseUser: FirebaseUser?,
    localUserCandidate: User? = null
  ): UserSessionState = withContext(Dispatchers.IO) {
    try {
      val email = firebaseUser?.email?.lowercase()?.trim() ?: localUserCandidate?.email?.lowercase()?.trim() ?: ""
      val isMasterDoctor = email == ClinicInfo.EMAIL.lowercase().trim()

      // 1. Resolve or match User entity from local Room DB
      var matchedUser = localUserCandidate
      if (matchedUser == null && firebaseUser != null) {
        matchedUser = userDao.getUserByUid(firebaseUser.uid)
          ?: userDao.getUserByEmail(email)
          ?: if (isMasterDoctor) userDao.getUserById(1L) else null
      }

      // Check cloud authorized_users document keyed by Firebase UID
      var cloudAuthorizedDoc: Map<String, Any>? = null
      if (firebaseUser != null) {
        cloudAuthorizedDoc = firebaseAuthManager.fetchAuthorizedUserByUid(firebaseUser.uid)
      }

      // If user is not yet in Room DB, synthesize/insert profile
      var finalUser = if (matchedUser != null) {
        matchedUser
      } else if (firebaseUser != null) {
        val defaultRole = if (isMasterDoctor) UserRole.SUPER_ADMIN else UserRole.STAFF
        val synthesized = User(
          id = if (isMasterDoctor) 1L else (System.currentTimeMillis() % 100000),
          uid = firebaseUser.uid,
          username = (cloudAuthorizedDoc?.get("username") as? String) ?: email.substringBefore("@").ifEmpty { "user" },
          fullName = (cloudAuthorizedDoc?.get("fullName") as? String) ?: if (isMasterDoctor) ClinicInfo.DOCTOR_NAME else (firebaseUser.displayName ?: "موظف المركز"),
          email = email,
          role = defaultRole,
          pinCode = "",
          isActive = (cloudAuthorizedDoc?.get("isActive") as? Boolean) ?: true,
          isApproved = (cloudAuthorizedDoc?.get("isApproved") as? Boolean) ?: isMasterDoctor,
          maxDevices = (cloudAuthorizedDoc?.get("maxDevices") as? Long)?.toInt() ?: if (isMasterDoctor) 5 else 2,
          createdAt = System.currentTimeMillis()
        )
        try {
          userDao.insert(synthesized)
        } catch (e: Exception) {
          Log.w(TAG, "Error caching synthesized user: ${e.message}")
        }
        synthesized
      } else {
        return@withContext UserSessionState.Unauthenticated
      }

      // Reconcile with cloud document if available
      if (cloudAuthorizedDoc != null) {
        val cloudActive = (cloudAuthorizedDoc["isActive"] as? Boolean) ?: finalUser.isActive
        val cloudApproved = (cloudAuthorizedDoc["isApproved"] as? Boolean) ?: finalUser.isApproved
        val cloudMaxDevices = (cloudAuthorizedDoc["maxDevices"] as? Long)?.toInt() ?: finalUser.maxDevices
        val cloudRoleStr = cloudAuthorizedDoc["role"] as? String
        val cloudRole = if (!cloudRoleStr.isNullOrBlank()) {
          try { UserRole.valueOf(cloudRoleStr) } catch (e: Exception) { finalUser.role }
        } else finalUser.role

        finalUser = finalUser.copy(
          isActive = cloudActive,
          isApproved = cloudApproved,
          maxDevices = cloudMaxDevices,
          role = if (isMasterDoctor) UserRole.SUPER_ADMIN else cloudRole
        )
      }

      // 2. Resolve Role from Firebase Custom Claims (if Firebase authenticated)
      val effectiveRole = if (firebaseUser != null) {
        try {
          val tokenResult: GetTokenResult = firebaseUser.getIdToken(false).await()
          val roleClaim = tokenResult.claims["role"] as? String
          when (roleClaim) {
            "SUPER_ADMIN" -> UserRole.SUPER_ADMIN
            "ADMIN" -> UserRole.ADMIN
            "ACCOUNTANT" -> UserRole.ACCOUNTANT
            "TECHNICIAN" -> UserRole.TECHNICIAN
            "STAFF" -> UserRole.STAFF
            else -> if (isMasterDoctor) UserRole.SUPER_ADMIN else finalUser.role
          }
        } catch (e: Exception) {
          Log.w(TAG, "Could not fetch custom claims: ${e.message}")
          if (isMasterDoctor) UserRole.SUPER_ADMIN else finalUser.role
        }
      } else {
        if (isMasterDoctor) UserRole.SUPER_ADMIN else finalUser.role
      }

      // 3. Verify Account Active Status
      if (!finalUser.isActive) {
        val disabledState = UserSessionState.Disabled(finalUser, "تم إيقاف أو تجميد هذا الحساب من قبل الإدارة.")
        _sessionState.value = disabledState
        return@withContext disabledState
      }

      // 4. Verify Account Approval Status
      if (!finalUser.isApproved && !isMasterDoctor && effectiveRole != UserRole.SUPER_ADMIN) {
        val pendingState = UserSessionState.PendingApproval(
          finalUser,
          "حساب المستخدم قيد المراجعة وبانتظار موافقة المشرف العام (${ClinicInfo.DOCTOR_NAME})."
        )
        _sessionState.value = pendingState
        return@withContext pendingState
      }

      // 5. Verify Device Binding & Authorization
      val deviceId = deviceSecurityManager.getUniqueDeviceId()
      var cloudDevice = firebaseAuthManager.fetchDeviceFromFirestore(deviceId)
      var localDevice = deviceBindingDao.getDeviceById(deviceId)

      if (cloudDevice != null) {
        deviceBindingDao.insert(cloudDevice)
        localDevice = cloudDevice
      }

      val deviceBinding = localDevice ?: cloudDevice
      if (deviceBinding != null) {
        when (deviceBinding.status) {
          DeviceStatus.BLOCKED -> {
            val blockedState = UserSessionState.DeviceBlocked(deviceId, deviceBinding.notes.ifEmpty { "تم حظر هذا الجهاز بواسطة المشرف العام." })
            _sessionState.value = blockedState
            return@withContext blockedState
          }
          DeviceStatus.REVOKED -> {
            val revokedState = UserSessionState.DeviceBlocked(deviceId, deviceBinding.notes.ifEmpty { "تم إلغاء ترخيص هذا الجهاز بواسطة المشرف العام." })
            _sessionState.value = revokedState
            return@withContext revokedState
          }
          DeviceStatus.PENDING -> {
            val pendingDevState = UserSessionState.DevicePendingApproval(
              user = finalUser,
              deviceBinding = deviceBinding,
              message = "الجهاز قيد انتظار موافقة وترخيص المشرف العام."
            )
            _sessionState.value = pendingDevState
            return@withContext pendingDevState
          }
          DeviceStatus.APPROVED -> {
            // Update last active timestamp
            val updated = deviceBinding.copy(
              userId = finalUser.id,
              userName = finalUser.fullName,
              lastActiveAt = System.currentTimeMillis()
            )
            deviceBindingDao.insert(updated)
          }
        }
      }

      val authenticatedUser = finalUser.copy(role = effectiveRole, lastLoginAt = System.currentTimeMillis())
      try {
        userDao.update(authenticatedUser)
      } catch (e: Exception) {
        // Ignored
      }

      val authState = UserSessionState.Authenticated(
        user = authenticatedUser,
        firebaseUser = firebaseUser,
        role = effectiveRole,
        isApproved = authenticatedUser.isApproved
      )
      _sessionState.value = authState
      authState
    } catch (e: Exception) {
      Log.e(TAG, "Error in loadUserProfileAndRole: ${e.message}", e)
      val errorState = UserSessionState.Error("فشل في تحميل بيانات الجلسة: ${e.message}")
      _sessionState.value = errorState
      errorState
    }
  }

  /**
   * Signs in using Firebase Email & Password credentials.
   */
  suspend fun signInWithEmail(email: String, password: String): Result<UserSessionState> = withContext(Dispatchers.IO) {
    _sessionState.value = UserSessionState.Loading
    val res = firebaseAuthManager.signInWithEmail(email, password)
    if (res.isSuccess) {
      val fbUser = res.getOrNull()
      val session = loadUserProfileAndRole(fbUser)
      Result.success(session)
    } else {
      val err = res.exceptionOrNull()?.message ?: "بيانات تسجيل الدخول غير صحيحة"
      _sessionState.value = UserSessionState.Error(err)
      Result.failure(Exception(err))
    }
  }

  /**
   * Signs in using local username/PIN.
   */
  suspend fun signInWithLocalPin(usernameOrPin: String, pin: String): Result<UserSessionState> = withContext(Dispatchers.IO) {
    _sessionState.value = UserSessionState.Loading
    val users = userDao.getAllSync()
    val matched = users.find {
      (it.username.equals(usernameOrPin, ignoreCase = true) || it.email.equals(usernameOrPin, ignoreCase = true))
    } ?: users.find { SecurityUtils.verifyPin(pin, it.pinCode) }

    if (matched != null && SecurityUtils.verifyPin(pin, matched.pinCode)) {
      val session = loadUserProfileAndRole(null, matched)
      Result.success(session)
    } else {
      val err = "اسم المستخدم أو رمز PIN غير صحيح"
      _sessionState.value = UserSessionState.Error(err)
      Result.failure(Exception(err))
    }
  }

  /**
   * Signs out and resets session state strictly to Unauthenticated.
   */
  suspend fun signOut() = withContext(Dispatchers.IO) {
    try {
      firebaseAuthManager.signOut()
      firebaseAuth?.signOut()
    } catch (e: Exception) {
      Log.w(TAG, "Sign out exception: ${e.message}")
    }
    _sessionState.value = UserSessionState.Unauthenticated
  }

  /**
   * Refreshes active user session state.
   */
  suspend fun refreshSession(): UserSessionState = withContext(Dispatchers.IO) {
    val current = firebaseAuth?.currentUser
    val currentState = _sessionState.value
    if (currentState is UserSessionState.Authenticated) {
      loadUserProfileAndRole(current, currentState.user)
    } else if (current != null) {
      loadUserProfileAndRole(current)
    } else {
      UserSessionState.Unauthenticated
    }
  }
}
