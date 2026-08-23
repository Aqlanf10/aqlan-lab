package com.aqlanlab.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqlanlab.app.data.AppDatabase
import com.aqlanlab.app.data.models.*
import com.aqlanlab.app.data.repository.DentalLabRepository
import com.aqlanlab.app.network.*
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.util.NotificationHelper
import com.aqlanlab.app.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DashboardStats(
  val totalShipments: Int = 0,
  val todayShipments: Int = 0,
  val totalPieces: Int = 0,
  val newCount: Int = 0,
  val inProgressCount: Int = 0,
  val readyCount: Int = 0,
  val receivedCount: Int = 0,
  val lateCount: Int = 0,
  // Financial stats per currency (Admin / Accountant)
  val yerStats: CurrencyBalance = CurrencyBalance(AppCurrency.YER),
  val sarStats: CurrencyBalance = CurrencyBalance(AppCurrency.SAR),
  val usdStats: CurrencyBalance = CurrencyBalance(AppCurrency.USD),
  // Legacy / Consolidated totals in Base Currency
  val totalBilled: Double = 0.0,
  val totalPaid: Double = 0.0,
  val totalOutstanding: Double = 0.0,
  val baseCurrency: AppCurrency = AppCurrency.SAR
)

data class LabAccountSummary(
  val lab: Laboratory,
  val totalShipments: Int,
  val totalPieces: Int,
  val totalBilled: Double,
  val totalPaid: Double,
  val remainingBalance: Double,
  val defaultCurrency: AppCurrency = AppCurrency.fromCode(lab.defaultCurrency),
  val currencyBalances: Map<AppCurrency, CurrencyBalance> = emptyMap(),
  val lastPaymentDate: Long? = null
) {
  fun balanceFor(curr: AppCurrency): Double = currencyBalances[curr]?.remainingBalance ?: 0.0
}

data class PieceCountReport(
  val label: String,
  val count: Int,
  val subLabel: String = "",
  val totalAmount: Double = 0.0
)

enum class ReportPeriod(val titleAr: String) {
  TODAY("اليوم"),
  THIS_WEEK("هذا الأسبوع"),
  THIS_MONTH("هذا الشهر"),
  ALL_TIME("كافة الفترات")
}

class DentalLabViewModel(application: Application) : AndroidViewModel(application) {
  // Application-scoped scope for the DB singleton (survives onCleared; process-lifetime)
  private val appDbScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
  )
  private val database = AppDatabase.getDatabase(application, appDbScope)
  private val repository = DentalLabRepository(database)
  val networkMonitor = NetworkMonitor(application)
  val cloudSyncManager = CloudSyncManager(application, database)
  val firebaseAuthManager = com.aqlanlab.app.network.FirebaseAuthManager(application, viewModelScope)
  val firebaseStorageBackupManager = com.aqlanlab.app.network.FirebaseStorageBackupManager(application, database)
  val appVersionManager = com.aqlanlab.app.network.AppVersionManager(application)

  val updateStatus: StateFlow<AppUpdateStatus> = appVersionManager.updateStatus
  val versionConfig: StateFlow<AppVersionConfig> = appVersionManager.versionConfig

  val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    .stateIn(viewModelScope, SharingStarted.Eagerly, networkMonitor.isCurrentlyConnected())

  val syncState: StateFlow<SyncState> = cloudSyncManager.syncState
  val lastSyncTimestamp: StateFlow<Long?> = cloudSyncManager.lastSyncTimestamp
  val syncMessage: StateFlow<String> = cloudSyncManager.syncMessage
  val clinicId: StateFlow<String> = cloudSyncManager.clinicId
  val clinicName: StateFlow<String> = cloudSyncManager.clinicName
  val availableSnapshots: StateFlow<List<FirestoreBackupSnapshot>> = cloudSyncManager.availableSnapshots
  val autoSyncEnabled: StateFlow<Boolean> = cloudSyncManager.autoSyncEnabled
  val currentUserEmail: StateFlow<String?> = cloudSyncManager.currentUserEmail
  val cloudServerUrl: StateFlow<String> = cloudSyncManager.cloudServerUrl
  val apiKey: StateFlow<String> = cloudSyncManager.apiKey

  // Firebase Storage Streams
  val storageBackupState: StateFlow<SyncState> = firebaseStorageBackupManager.backupState
  val storageStatusMessage: StateFlow<String> = firebaseStorageBackupManager.statusMessage
  val lastStorageBackupTimestamp: StateFlow<Long?> = firebaseStorageBackupManager.lastBackupTimestamp
  val availableStorageBackups: StateFlow<List<FirebaseStorageBackupInfo>> = firebaseStorageBackupManager.availableStorageBackups
  val isAutoStorageBackupEnabled: StateFlow<Boolean> = firebaseStorageBackupManager.isAutoBackupEnabled
  val autoBackupFrequency: StateFlow<AutoBackupFrequency> = firebaseStorageBackupManager.autoBackupFrequency
  val lastUploadedBackupInfo: StateFlow<FirebaseStorageBackupInfo?> = firebaseStorageBackupManager.lastUploadedBackupInfo

  val userSessionRepository = com.aqlanlab.app.data.repository.UserSessionRepository(
    context = application,
    userDao = database.userDao(),
    deviceBindingDao = database.deviceBindingDao(),
    auditLogDao = database.auditLogDao(),
    firebaseAuthManager = firebaseAuthManager,
    externalScope = viewModelScope
  )
  val userSessionState: StateFlow<com.aqlanlab.app.data.repository.UserSessionState> = userSessionRepository.sessionState

  // Firebase Auth Streams
  val firebaseAuthState: StateFlow<com.aqlanlab.app.network.AuthUiState> = firebaseAuthManager.authState
  val firebaseCurrentUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = firebaseAuthManager.currentUser
  val isFirebaseAuthorized: StateFlow<Boolean> = firebaseAuthManager.isAuthorized

  val deviceSecurityManager = firebaseAuthManager.deviceSecurityManager
  val currentDeviceId: String get() = deviceSecurityManager.getUniqueDeviceId()

  val allUsers: StateFlow<List<User>> = repository.allUsers
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allDevices: StateFlow<List<DeviceBinding>> = repository.allDevices
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val currentDeviceBinding: StateFlow<DeviceBinding?> = repository.observeDeviceById(deviceSecurityManager.getUniqueDeviceId())
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _activeUser = MutableStateFlow<User?>(null)
  val activeUser: StateFlow<User?> = _activeUser.asStateFlow()

  // --- Exclusive Security & App Lock State ---
  private val _isAppLocked = MutableStateFlow(true)
  val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

  private val _appLockEnabled = MutableStateFlow(true)
  val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

  // Firestore device listener handle (removed in onCleared to avoid leaks)
  private var deviceListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

  // Deep-link route requested from a notification tap (consumed by MainAppScaffold)
  private val _pendingDeepLinkRoute = MutableStateFlow<String?>(null)
  val pendingDeepLinkRoute: StateFlow<String?> = _pendingDeepLinkRoute.asStateFlow()

  fun queueDeepLinkRoute(route: String) {
    if (route.isNotBlank()) _pendingDeepLinkRoute.value = route
  }

  fun consumeDeepLinkRoute() {
    _pendingDeepLinkRoute.value = null
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      appVersionManager.checkAppVersion()
      repository.checkAndSeedInitialData()
      cloudSyncManager.fetchAvailableSnapshots()
      firebaseStorageBackupManager.fetchAvailableStorageBackups(clinicId.value)
    }

    // NOTE: the notification monitor that collects `allShipments` was moved into a
    // dedicated init block placed AFTER the `allShipments` / `_notificationsEnabled`
    // declarations. Kotlin runs property initializers and init blocks in source order,
    // and viewModelScope uses Dispatchers.Main.immediate, so collecting a property that
    // is declared later inside this first init block caused a startup NullPointerException.

    // Monitor current device status in real-time (Forced session termination if Blocked/Revoked/Pending)
    viewModelScope.launch {
      repository.observeDeviceById(deviceSecurityManager.getUniqueDeviceId()).collect { dev ->
        if (_activeUser.value != null) {
          if (dev == null || dev.status != DeviceStatus.APPROVED) {
            terminateActiveSessionDueToDeviceStatus(dev?.status)
          }
        }
      }
    }

    // Real-time Firestore Snapshot Listener for Current Device
    try {
      deviceListenerRegistration = firebaseAuthManager.listenToDeviceInFirestore(deviceSecurityManager.getUniqueDeviceId()) { status, cloudBinding ->
        viewModelScope.launch(Dispatchers.IO) {
          if (cloudBinding != null) {
            repository.insertOrUpdateDevice(cloudBinding)
          } else if (status == null) {
            // Deleted from cloud
            repository.getDeviceById(deviceSecurityManager.getUniqueDeviceId())?.let {
              repository.deleteDevice(it, getActiveUserSafe())
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w("DentalLabViewModel", "Firestore device listener not started: ${e.message}")
    }
  }

  override fun onCleared() {
    try {
      deviceListenerRegistration?.remove()
      deviceListenerRegistration = null
    } catch (e: Exception) {
      Log.w("DentalLabViewModel", "Device listener removal failed: ${e.message}")
    }
    super.onCleared()
  }

  val isAuthenticated: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(_activeUser, _isAppLocked) { user, locked ->
    user != null && !locked
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  // Role permissions helpers for UI conditional display
  val isSuperAdmin: StateFlow<Boolean> = _activeUser.map { it?.role == UserRole.SUPER_ADMIN }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val canViewFinancials: StateFlow<Boolean> = _activeUser.map { it?.role?.canViewFinancials == true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val canManageUsers: StateFlow<Boolean> = _activeUser.map { it?.role?.canManageUsers == true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val canManageDevices: StateFlow<Boolean> = _activeUser.map { it?.role?.canManageDevices == true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val canEditPrices: StateFlow<Boolean> = _activeUser.map { it?.role?.canEditPrices == true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val canDeleteRecords: StateFlow<Boolean> = _activeUser.map { it?.role?.canDeleteRecords == true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  fun getActiveUserSafe(): User {
    // SECURITY FIX: previously fabricated a hardcoded SUPER_ADMIN (the owner's identity)
    // whenever no user was signed in, forging audit-log entries and granting privileged
    // writes. Now returns a clearly-marked, low-privilege "System" identity instead.
    return _activeUser.value ?: User(
      id = 0,
      username = "system",
      fullName = "النظام (بدون جلسة دخول)",
      email = "",
      role = UserRole.STAFF,
      pinCode = "",
      avatarColor = 0xFF64748B,
      isActive = true,
      isApproved = true
    )
  }

  // --- Session Termination on Device Invalidation ---
  fun terminateActiveSessionDueToDeviceStatus(status: DeviceStatus?) {
    _activeUser.value = null
    _isAppLocked.value = true
    firebaseAuthManager.signOut()
  }

  // --- Core Device Authorization Check (Cloud / Server + Local Room Verification) ---
  suspend fun verifyAndProcessDeviceAuthorization(user: User): DeviceAuthOutcome = withContext(Dispatchers.IO) {
    val deviceId = currentDeviceId

    // 1. Check Cloud / Firestore first (Authoritative server source)
    var cloudDevice = firebaseAuthManager.fetchDeviceFromFirestore(deviceId)
    var localDevice = repository.getDeviceById(deviceId)

    if (cloudDevice != null) {
      repository.insertOrUpdateDevice(cloudDevice)
      localDevice = cloudDevice
    }

    val currentBinding = localDevice ?: cloudDevice

    // 2. If device is completely new (not registered in cloud or local):
    if (currentBinding == null) {
      val approvedCount = repository.getApprovedDevicesCountForUser(user.id)
      val isOwner = user.role == UserRole.SUPER_ADMIN || user.email.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true)
      val isExceeded = approvedCount >= user.maxDevices

      // Only Doctor Owner initial device within maxDevices can be auto-approved
      val initialStatus = if (isOwner && !isExceeded) {
        DeviceStatus.APPROVED
      } else {
        DeviceStatus.PENDING
      }

      val notes = if (isExceeded) {
        "تجاوز الحد الأقصى للأجهزة المصرح بها ($approvedCount / ${user.maxDevices})"
      } else if (isOwner) {
        "الجهاز المعتمد للمشرف العام"
      } else {
        "طلب ترخيص واعتماد جهاز جديد (${deviceSecurityManager.getDeviceModelName()})"
      }

      val newBinding = DeviceBinding(
        deviceId = deviceId,
        userId = user.id,
        userName = user.fullName,
        userRole = user.role,
        deviceModel = deviceSecurityManager.getDeviceModelName(),
        osVersion = deviceSecurityManager.getAndroidOsVersion(),
        appVersion = deviceSecurityManager.getAppVersion(),
        status = initialStatus,
        approvedByAdmin = if (initialStatus == DeviceStatus.APPROVED) "SUPER_ADMIN" else "",
        notes = notes,
        registeredAt = System.currentTimeMillis(),
        lastActiveAt = System.currentTimeMillis()
      )

      repository.insertOrUpdateDevice(newBinding)
      firebaseAuthManager.submitDeviceApprovalRequest(newBinding)

      return@withContext if (initialStatus == DeviceStatus.APPROVED) {
        DeviceAuthOutcome.Allowed(newBinding)
      } else {
        DeviceAuthOutcome.PendingApproval(
          device = newBinding,
          isMaxDevicesExceeded = isExceeded,
          message = if (isExceeded) {
            "تم تسجيل الجهاز ولكن الحساب وصل للحد الأقصى للأجهزة المصرح بها (${user.maxDevices}). يتطلب إلغاء ربط جهاز قديم أو موافقة خاصة من المشرف العام."
          } else {
            "تم تسجيل الجهاز وهو بانتظار موافقة وترخيص المشرف العام (د. عقلان)."
          }
        )
      }
    }

    // 3. Existing Device Evaluation
    when (currentBinding.status) {
      DeviceStatus.APPROVED -> {
        val updated = currentBinding.copy(
          userId = user.id,
          userName = user.fullName,
          userRole = user.role,
          lastActiveAt = System.currentTimeMillis()
        )
        repository.insertOrUpdateDevice(updated)
        firebaseAuthManager.submitDeviceApprovalRequest(updated)
        DeviceAuthOutcome.Allowed(updated)
      }
      DeviceStatus.PENDING -> {
        val approvedCount = repository.getApprovedDevicesCountForUser(user.id)
        val isExceeded = approvedCount >= user.maxDevices
        DeviceAuthOutcome.PendingApproval(
          device = currentBinding,
          isMaxDevicesExceeded = isExceeded,
          message = if (isExceeded) {
            "الجهاز قيد انتظار الموافقة (تم الوصول للحد الأقصى للأجهزة المصرح بها ${user.maxDevices})."
          } else {
            "الجهاز قيد انتظار موافقة وترخيص المشرف العام (د. عقلان)."
          }
        )
      }
      DeviceStatus.BLOCKED -> {
        DeviceAuthOutcome.Blocked(
          device = currentBinding,
          reason = currentBinding.notes.ifEmpty { "تم حظر هذا الجهاز من قبل المشرف العام (Access Denied)" }
        )
      }
      DeviceStatus.REVOKED -> {
        DeviceAuthOutcome.Revoked(
          device = currentBinding,
          reason = currentBinding.notes.ifEmpty { "تم إلغاء ترخيص هذا الجهاز من قبل المشرف العام (Access Denied)" }
        )
      }
    }
  }

  fun unlockAppWithPin(pin: String): Boolean {
    if (pin.isBlank()) return false
    val userList = allUsers.value
    var matchingUser = userList.find { user ->
      user.isActive && user.isApproved && (user.pinCode.isNotBlank() && SecurityUtils.verifyPin(pin, user.pinCode))
    }
    if (matchingUser == null) {
      val admin = userList.find { it.role == UserRole.SUPER_ADMIN && it.isActive }
      if (admin != null && admin.pinCode.isBlank()) {
        val updated = admin.copy(pinCode = SecurityUtils.hashPin(pin.trim()))
        updateUser(updated)
        matchingUser = updated
      }
    }
    if (matchingUser == null) return false

    // Upgrade legacy (weak) PIN hashes to the stronger v2 format on successful login
    if (SecurityUtils.needsRehash(matchingUser.pinCode)) {
      val upgraded = matchingUser.copy(pinCode = SecurityUtils.hashPin(pin.trim()))
      updateUser(upgraded)
      matchingUser = upgraded
    }

    val localDevice = currentDeviceBinding.value
    if (localDevice != null && localDevice.status != DeviceStatus.APPROVED) {
      if (matchingUser.role == UserRole.SUPER_ADMIN) {
        approveDevice(localDevice.deviceId)
      } else {
        return false
      }
    }

    _activeUser.value = matchingUser
    _isAppLocked.value = false
    return true
  }

  fun unlockAppWithBiometric(): Boolean {
    val userList = allUsers.value
    val adminUser = userList.find { it.role == UserRole.SUPER_ADMIN && it.isActive && it.isApproved } ?: return false

    val localDevice = currentDeviceBinding.value
    if (localDevice != null && localDevice.status != DeviceStatus.APPROVED) {
      return false
    }

    _activeUser.value = adminUser
    _isAppLocked.value = false
    return true
  }

  fun lockApp() {
    // Honor the user-visible "app lock" setting (previously the toggle was a placebo)
    if (_appLockEnabled.value) {
      _isAppLocked.value = true
    }
  }

  fun setAppLockEnabled(enabled: Boolean) {
    _appLockEnabled.value = enabled
    viewModelScope.launch(Dispatchers.IO) {
      repository.setSetting("app_lock_enabled", enabled.toString())
    }
  }

  fun changeDoctorMasterPin(newPin: String) {
    if (newPin.isBlank()) return
    val hashedPin = SecurityUtils.hashPin(newPin)
    val adminUser = allUsers.value.find { it.role == UserRole.SUPER_ADMIN }
    if (adminUser != null) {
      updateUser(adminUser.copy(pinCode = hashedPin))
    }
  }

  // --- Device Management Actions (Super Admin Only) ---
  fun approveDevice(deviceId: String, onComplete: ((Boolean, String) -> Unit)? = null) {
    val currentUser = getActiveUserSafe()
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateDeviceStatus(
        deviceId = deviceId,
        newStatus = DeviceStatus.APPROVED,
        approvedBy = currentUser.fullName,
        currentUser = currentUser
      )
      firebaseAuthManager.updateDeviceStatusInFirestore(deviceId, DeviceStatus.APPROVED, currentUser.fullName)
      withContext(Dispatchers.Main) {
        onComplete?.invoke(true, "تم اعتماد وترخيص الجهاز بنجاح")
      }
    }
  }

  fun blockDevice(deviceId: String) {
    val currentUser = getActiveUserSafe()
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateDeviceStatus(
        deviceId = deviceId,
        newStatus = DeviceStatus.BLOCKED,
        approvedBy = currentUser.fullName,
        currentUser = currentUser
      )
      firebaseAuthManager.updateDeviceStatusInFirestore(deviceId, DeviceStatus.BLOCKED, currentUser.fullName)
      if (deviceId == currentDeviceId) {
        withContext(Dispatchers.Main) {
          terminateActiveSessionDueToDeviceStatus(DeviceStatus.BLOCKED)
        }
      }
    }
  }

  fun revokeDevice(deviceId: String) {
    val currentUser = getActiveUserSafe()
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateDeviceStatus(
        deviceId = deviceId,
        newStatus = DeviceStatus.REVOKED,
        approvedBy = currentUser.fullName,
        currentUser = currentUser
      )
      firebaseAuthManager.updateDeviceStatusInFirestore(deviceId, DeviceStatus.REVOKED, currentUser.fullName)
      if (deviceId == currentDeviceId) {
        withContext(Dispatchers.Main) {
          terminateActiveSessionDueToDeviceStatus(DeviceStatus.REVOKED)
        }
      }
    }
  }

  fun deleteDevice(device: DeviceBinding) {
    val currentUser = getActiveUserSafe()
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteDevice(device, currentUser)
      firebaseAuthManager.deleteDeviceFromFirestore(device.deviceId)
      if (device.deviceId == currentDeviceId) {
        withContext(Dispatchers.Main) {
          terminateActiveSessionDueToDeviceStatus(null)
        }
      }
    }
  }

  fun refreshDeviceAuthorization(onResult: (DeviceStatus, String) -> Unit) {
    viewModelScope.launch {
      val deviceId = currentDeviceId
      val cloudDevice = firebaseAuthManager.fetchDeviceFromFirestore(deviceId)
      if (cloudDevice != null) {
        repository.insertOrUpdateDevice(cloudDevice)
        val msg = when (cloudDevice.status) {
          DeviceStatus.APPROVED -> "تم اعتماد وترخيص هذا الجهاز بنجاح! يمكنك الدخول الآن."
          DeviceStatus.PENDING -> "الجهاز لا يزال قيد انتظار موافقة المشرف العام."
          DeviceStatus.BLOCKED -> "هذا الجهاز محظور من قبل المشرف العام."
          DeviceStatus.REVOKED -> "تم إلغاء ترخيص هذا الجهاز."
        }
        onResult(cloudDevice.status, msg)
      } else {
        val localDevice = repository.getDeviceById(deviceId)
        val status = localDevice?.status ?: DeviceStatus.PENDING
        onResult(status, "الحالة الحالية: ${status.titleAr}")
      }
    }
  }

  fun signInWithPrivateAccount(usernameOrEmail: String, pinOrPass: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val input = usernameOrEmail.trim()
      if (input.isBlank() || pinOrPass.isBlank()) {
        onResult(false, "يرجى إدخال اسم المستخدم وكلمة المرور")
        return@launch
      }
      val users = allUsers.value

      // Find local matched user by username or email
      val matchedUser = users.find {
        it.username.equals(input, ignoreCase = true) || it.email.equals(input, ignoreCase = true)
      }

      if (matchedUser != null) {
        if (!matchedUser.isActive) {
          onResult(false, "تم تعطيل هذا الحساب بواسطة المشرف العام.")
          return@launch
        }
        if (!matchedUser.isApproved) {
          onResult(false, "هذا الحساب قيد المراجعة ولم يتم اعتماده بعد.")
          return@launch
        }
        val isOwner = matchedUser.role == UserRole.SUPER_ADMIN || matchedUser.email.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true)
        var effectiveUser = matchedUser
        val isPinValid = if (matchedUser.pinCode.isBlank() && isOwner) {
          // If Dr. Aqlan has not configured a master PIN yet, set entered PIN as master PIN
          val updated = matchedUser.copy(pinCode = SecurityUtils.hashPin(pinOrPass.trim()))
          repository.updateUser(updated, matchedUser)
          effectiveUser = updated
          true
        } else {
          matchedUser.pinCode.isNotBlank() && SecurityUtils.verifyPin(pinOrPass, matchedUser.pinCode)
        }

        // Upgrade legacy (weak) PIN hashes to the stronger v2 format on successful login
        if (isPinValid && SecurityUtils.needsRehash(effectiveUser.pinCode)) {
          val upgraded = effectiveUser.copy(pinCode = SecurityUtils.hashPin(pinOrPass.trim()))
          repository.updateUser(upgraded, effectiveUser)
          effectiveUser = upgraded
        }

        if (isPinValid) {
          // Verify Device Authorization before granting access
          val authOutcome = verifyAndProcessDeviceAuthorization(effectiveUser)
          when (authOutcome) {
            is DeviceAuthOutcome.Allowed -> {
              _activeUser.value = effectiveUser
              _isAppLocked.value = false
              onResult(true, "مرحباً ${effectiveUser.fullName}")
            }
            is DeviceAuthOutcome.PendingApproval -> {
              _activeUser.value = null
              _isAppLocked.value = true
              onResult(false, authOutcome.message)
            }
            is DeviceAuthOutcome.Blocked -> {
              _activeUser.value = null
              _isAppLocked.value = true
              onResult(false, authOutcome.reason)
            }
            is DeviceAuthOutcome.Revoked -> {
              _activeUser.value = null
              _isAppLocked.value = true
              onResult(false, authOutcome.reason)
            }
          }
          return@launch
        }
      }

      // Check Firebase Auth if email provided
      if (input.contains("@")) {
        val res = firebaseAuthManager.signInWithEmail(input, pinOrPass)
        if (res.isSuccess) {
          val fbUser = res.getOrNull()
          val isOwner = fbUser?.email?.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true) == true
          // FIX: previously synthesized IDs via System.currentTimeMillis() % 10000, which
          // could collide with existing rows and silently overwrite them (REPLACE strategy).
          // Now the user is persisted through the repository and gets a real auto-generated ID.
          val userToSet = matchedUser ?: repository.upsertCloudUser(
            User(
              id = 0,
              username = fbUser?.email?.substringBefore("@") ?: "user",
              fullName = if (isOwner) com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME else (fbUser?.displayName ?: "موظف المركز"),
              email = fbUser?.email ?: "",
              role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.STAFF,
              pinCode = "",
              isActive = true,
              isApproved = true,
              maxDevices = if (isOwner) 5 else 2
            )
          )

          val authOutcome = verifyAndProcessDeviceAuthorization(userToSet)
          when (authOutcome) {
            is DeviceAuthOutcome.Allowed -> {
              _activeUser.value = userToSet
              _isAppLocked.value = false
              onResult(true, "تم تسجيل الدخول بنجاح")
            }
            is DeviceAuthOutcome.PendingApproval -> {
              _activeUser.value = null
              _isAppLocked.value = true
              onResult(false, authOutcome.message)
            }
            is DeviceAuthOutcome.Blocked -> {
              _activeUser.value = null
              _isAppLocked.value = true
              onResult(false, authOutcome.reason)
            }
            is DeviceAuthOutcome.Revoked -> {
              _activeUser.value = null
              _isAppLocked.value = true
              onResult(false, authOutcome.reason)
            }
          }
        } else {
          onResult(false, res.exceptionOrNull()?.message ?: "بيانات الدخول غير صحيحة")
        }
      } else {
        onResult(false, "اسم المستخدم أو كلمة المرور غير صحيحة")
      }
    }
  }

  fun signInWithFirebaseEmail(email: String, pass: String) {
    viewModelScope.launch {
      val result = firebaseAuthManager.signInWithEmail(email, pass)
      if (result.isSuccess) {
        val fbUser = result.getOrNull()
        val isOwner = fbUser?.email?.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true) == true
        val matchedUser = allUsers.value.find { it.username.equals(email.substringBefore("@"), ignoreCase = true) }
        // FIX: real auto-generated IDs via repository instead of timestamp collisions
        val finalUser = matchedUser ?: repository.upsertCloudUser(
          User(
            id = 0,
            username = fbUser?.email?.substringBefore("@") ?: "user",
            fullName = if (isOwner) com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME else (fbUser?.displayName ?: "موظف المركز"),
            email = fbUser?.email ?: "",
            role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.STAFF,
            pinCode = "",
            isActive = true,
            isApproved = true,
            maxDevices = if (isOwner) 5 else 2
          )
        )

        val authOutcome = verifyAndProcessDeviceAuthorization(finalUser)
        if (authOutcome is DeviceAuthOutcome.Allowed) {
          _activeUser.value = finalUser
          _isAppLocked.value = false
        } else {
          _activeUser.value = null
          _isAppLocked.value = true
        }
      }
    }
  }

  fun signInWithGoogle(context: android.content.Context) {
    viewModelScope.launch {
      val result = firebaseAuthManager.signInWithGoogle(context)
      if (result.isSuccess) {
        val fbUser = result.getOrNull()
        val isOwner = fbUser?.email?.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true) == true
        val matchedUser = allUsers.value.find { it.email.equals(fbUser?.email, ignoreCase = true) }
        // FIX: real auto-generated IDs via repository instead of timestamp collisions
        val finalUser = matchedUser ?: repository.upsertCloudUser(
          User(
            id = 0,
            username = fbUser?.email?.substringBefore("@") ?: "user",
            fullName = if (isOwner) com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME else (fbUser?.displayName ?: "موظف المركز"),
            email = fbUser?.email ?: "",
            role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.STAFF,
            pinCode = "",
            isActive = true,
            isApproved = true,
            maxDevices = if (isOwner) 5 else 2
          )
        )

        val authOutcome = verifyAndProcessDeviceAuthorization(finalUser)
        if (authOutcome is DeviceAuthOutcome.Allowed) {
          _activeUser.value = finalUser
          _isAppLocked.value = false
        } else {
          _activeUser.value = null
          _isAppLocked.value = true
        }
      }
    }
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> {
    return firebaseAuthManager.sendPasswordReset(email)
  }

  fun sendPhoneVerificationCode(
    phoneNumber: String,
    activity: android.app.Activity,
    onCodeSent: (verificationId: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
    onAutoVerified: () -> Unit
  ) {
    firebaseAuthManager.sendPhoneVerificationCode(
      phoneNumber = phoneNumber,
      activity = activity,
      onCodeSent = onCodeSent,
      onError = onError,
      onAutoVerified = { fbUser ->
        if (fbUser != null) {
          viewModelScope.launch {
            val isOwner = fbUser.email?.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true) == true
            // FIX: real auto-generated IDs via repository instead of timestamp collisions
            val finalUser = repository.upsertCloudUser(
              User(
                id = 0,
                username = fbUser.phoneNumber ?: "user",
                fullName = if (isOwner) com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME else (fbUser.displayName ?: "مستخدم المركز"),
                email = fbUser.email ?: "${fbUser.phoneNumber}@aqlanlab.com",
                role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.STAFF,
                pinCode = "",
                isActive = true,
                isApproved = true,
                maxDevices = if (isOwner) 5 else 2
              )
            )
            val authOutcome = verifyAndProcessDeviceAuthorization(finalUser)
            if (authOutcome is DeviceAuthOutcome.Allowed) {
              _activeUser.value = finalUser
              _isAppLocked.value = false
            }
            onAutoVerified()
          }
        }
      }
    )
  }

  fun verifyPhoneCodeAndSignIn(
    verificationId: String,
    code: String,
    onResult: (Boolean, String) -> Unit
  ) {
    viewModelScope.launch {
      val result = firebaseAuthManager.verifyPhoneCodeAndSignIn(verificationId, code)
      if (result.isSuccess) {
        val fbUser = result.getOrNull()
        val isOwner = fbUser?.email?.equals(com.aqlanlab.app.ui.components.ClinicInfo.EMAIL, ignoreCase = true) == true
        // FIX: real auto-generated IDs via repository instead of timestamp collisions
        val finalUser = repository.upsertCloudUser(
          User(
            id = 0,
            username = fbUser?.phoneNumber ?: "user",
            fullName = if (isOwner) com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME else (fbUser?.displayName ?: "مستخدم المركز"),
            email = fbUser?.email ?: "${fbUser?.phoneNumber}@aqlanlab.com",
            role = if (isOwner) UserRole.SUPER_ADMIN else UserRole.STAFF,
            pinCode = "",
            isActive = true,
            isApproved = true,
            maxDevices = if (isOwner) 5 else 2
          )
        )
        val authOutcome = verifyAndProcessDeviceAuthorization(finalUser)
        if (authOutcome is DeviceAuthOutcome.Allowed) {
          _activeUser.value = finalUser
          _isAppLocked.value = false
          onResult(true, "تم تسجيل الدخول بنجاح")
        } else {
          _activeUser.value = null
          _isAppLocked.value = true
          onResult(false, "الجهاز بانتظار موافقة المشرف العام")
        }
      } else {
        val errMsg = result.exceptionOrNull()?.message ?: "رمز التحقق غير صحيح"
        onResult(false, errMsg)
      }
    }
  }

  fun logout() {
    viewModelScope.launch {
      userSessionRepository.signOut()
      firebaseAuthManager.signOut()
      _activeUser.value = null
      _isAppLocked.value = true
      _appLockEnabled.value = true
    }
  }

  fun signOutFirebase() {
    logout()
  }

  private val _currency = MutableStateFlow("SAR") // Default base currency: SAR, YER, USD
  val currency: StateFlow<String> = _currency.asStateFlow()

  // --- Multi-Currency & Exchange Rates State ---
  private val _exchangeRates = MutableStateFlow(ExchangeRates())
  val exchangeRates: StateFlow<ExchangeRates> = _exchangeRates.asStateFlow()

  private val _selectedCurrencyFilter = MutableStateFlow<AppCurrency?>(null) // null = all currencies
  val selectedCurrencyFilter: StateFlow<AppCurrency?> = _selectedCurrencyFilter.asStateFlow()

  private val _notificationsEnabled = MutableStateFlow(true)
  val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

  fun setNotificationsEnabled(enabled: Boolean) {
    _notificationsEnabled.value = enabled
  }

  fun checkForAppUpdates(onResult: (AppUpdateStatus) -> Unit = {}) {
    viewModelScope.launch {
      val status = appVersionManager.checkAppVersion(forceCheck = true)
      onResult(status)
    }
  }

  fun publishNewVersion(config: AppVersionConfig, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val result = appVersionManager.publishVersionConfig(config)
      if (result.isSuccess) {
        onResult(true, "تم نشر وتحديث معلومات الإصدار الجديد بنجاح في السحابة")
      } else {
        onResult(false, result.exceptionOrNull()?.message ?: "فشل نشر معلومات الإصدار")
      }
    }
  }

  fun checkDueDeliveriesNow(onComplete: (Int) -> Unit = {}) {
    viewModelScope.launch {
      val shipments = allShipments.value
      val count = withContext(Dispatchers.Default) {
        NotificationHelper.checkAndNotifyUpcomingDeliveries(
          context = getApplication(),
          shipments = shipments,
          thresholdHours = 48
        )
      }
      onComplete(count)
    }
  }

  fun setCurrencyFilter(curr: AppCurrency?) {
    _selectedCurrencyFilter.value = curr
  }

  private val _isFetchingExchangeRates = MutableStateFlow(false)
  val isFetchingExchangeRates: StateFlow<Boolean> = _isFetchingExchangeRates.asStateFlow()

  fun fetchLiveExchangeRates() {
    viewModelScope.launch {
      _isFetchingExchangeRates.value = true
      val fetched = com.aqlanlab.app.data.network.ExchangeRateService.fetchLiveRates()
      if (fetched != null) {
        _exchangeRates.value = fetched
        withContext(Dispatchers.IO) {
          repository.setSetting("exchange_rate_usd_to_yer", fetched.usdToYer.toString())
          repository.setSetting("exchange_rate_sar_to_yer", fetched.sarToYer.toString())
          repository.setSetting("exchange_rate_usd_to_sar", fetched.usdToSar.toString())
          repository.setSetting("exchange_rate_source", fetched.source)
        }
      }
      _isFetchingExchangeRates.value = false
    }
  }

  fun applyExchangeRatePreset(preset: com.aqlanlab.app.data.network.ExchangeRateService.RatePreset) {
    _exchangeRates.value = preset.rates
    viewModelScope.launch(Dispatchers.IO) {
      repository.setSetting("exchange_rate_usd_to_yer", preset.rates.usdToYer.toString())
      repository.setSetting("exchange_rate_sar_to_yer", preset.rates.sarToYer.toString())
      repository.setSetting("exchange_rate_usd_to_sar", preset.rates.usdToSar.toString())
      repository.setSetting("exchange_rate_source", preset.rates.source)
    }
  }

  fun updateExchangeRates(usdToYer: Double, sarToYer: Double, usdToSar: Double = 3.75) {
    _exchangeRates.value = ExchangeRates(
      usdToYer = usdToYer.coerceAtLeast(1.0),
      sarToYer = sarToYer.coerceAtLeast(1.0),
      usdToSar = usdToSar.coerceAtLeast(0.1),
      source = "سعر مخصص",
      isLive = false,
      lastUpdated = System.currentTimeMillis()
    )
    viewModelScope.launch(Dispatchers.IO) {
      repository.setSetting("exchange_rate_usd_to_yer", usdToYer.toString())
      repository.setSetting("exchange_rate_sar_to_yer", sarToYer.toString())
      repository.setSetting("exchange_rate_usd_to_sar", usdToSar.toString())
      repository.setSetting("exchange_rate_source", "سعر مخصص")
    }
  }

  fun convertCurrency(amount: Double, from: AppCurrency, to: AppCurrency): Double {
    return _exchangeRates.value.convert(amount, from, to)
  }

  // --- Search & Filter States ---
  private val _shipmentSearchQuery = MutableStateFlow("")
  val shipmentSearchQuery: StateFlow<String> = _shipmentSearchQuery.asStateFlow()

  private val _selectedStatusFilter = MutableStateFlow<ShipmentStatus?>(null)
  val selectedStatusFilter: StateFlow<ShipmentStatus?> = _selectedStatusFilter.asStateFlow()

  private val _reportPeriod = MutableStateFlow(ReportPeriod.THIS_MONTH)
  val reportPeriod: StateFlow<ReportPeriod> = _reportPeriod.asStateFlow()

  // Inventory Filters
  private val _inventorySearchQuery = MutableStateFlow("")
  val inventorySearchQuery: StateFlow<String> = _inventorySearchQuery.asStateFlow()

  private val _inventoryCategoryFilter = MutableStateFlow("الكل")
  val inventoryCategoryFilter: StateFlow<String> = _inventoryCategoryFilter.asStateFlow()

  private val _inventoryStockFilter = MutableStateFlow("ALL") // ALL, LOW_STOCK, IN_STOCK
  val inventoryStockFilter: StateFlow<String> = _inventoryStockFilter.asStateFlow()

  // --- Data Streams ---
  val allLabs: StateFlow<List<Laboratory>> = repository.allLabs
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val activeLabs: StateFlow<List<Laboratory>> = repository.activeLabs
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allWorkTypes: StateFlow<List<WorkType>> = repository.allWorkTypes
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val activeWorkTypes: StateFlow<List<WorkType>> = repository.activeWorkTypes
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allLabPrices: StateFlow<List<LabPrice>> = repository.allLabPrices
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allShipments: StateFlow<List<Shipment>> = repository.allShipments
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allPayments: StateFlow<List<Payment>> = repository.allPayments
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // Secondary init block: intentionally placed AFTER the `allShipments` / `_currency` /
  // `_exchangeRates` / `_notificationsEnabled` property declarations so that the
  // collectors below can never observe an uninitialized property (startup-crash fix).
  init {
    // Restore persisted user preferences (previously: currency, exchange rates and the
    // app-lock setting were silently reset to defaults on every app restart).
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _currency.value = repository.getSetting("currency", "SAR")

        val appLockSetting = repository.getSetting("app_lock_enabled", "true") == "true"
        _appLockEnabled.value = appLockSetting
        if (!appLockSetting) {
          _isAppLocked.value = false
        }

        val usdToYer = repository.getSetting("exchange_rate_usd_to_yer", "0").toDoubleOrNull()
        val sarToYer = repository.getSetting("exchange_rate_sar_to_yer", "0").toDoubleOrNull()
        val usdToSar = repository.getSetting("exchange_rate_usd_to_sar", "0").toDoubleOrNull()
        if (usdToYer != null && usdToYer > 0 && sarToYer != null && sarToYer > 0 &&
          usdToSar != null && usdToSar > 0
        ) {
          val source = repository.getSetting("exchange_rate_source", "")
          _exchangeRates.value = ExchangeRates(
            usdToYer = usdToYer,
            sarToYer = sarToYer,
            usdToSar = usdToSar,
            source = source.ifEmpty { "معدلات محفوظة" },
            isLive = false,
            lastUpdated = System.currentTimeMillis()
          )
        }
      } catch (e: Exception) {
        Log.w("DentalLabViewModel", "Preference restore failed: ${e.message}")
      }
    }

    // Monitor real-time upcoming delivery dates and notify when delivery is due or
    // overdue (relocated from the first init block — see the note there).
    viewModelScope.launch {
      allShipments.collect { shipments ->
        if (_notificationsEnabled.value && _activeUser.value != null && shipments.isNotEmpty()) {
          withContext(Dispatchers.Default) {
            NotificationHelper.checkAndNotifyUpcomingDeliveries(getApplication(), shipments)
          }
        }
      }
    }
  }

  val recentAuditLogs: StateFlow<List<AuditLog>> = repository.recentAuditLogs
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allInventoryItems: StateFlow<List<InventoryItem>> = repository.allInventoryItems
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val lowStockInventoryItems: StateFlow<List<InventoryItem>> = repository.lowStockInventoryItems
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val lowStockCount: StateFlow<Int> = lowStockInventoryItems
    .map { it.size }
    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

  val allInventoryTransactions: StateFlow<List<InventoryTransaction>> = repository.allInventoryTransactions
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val filteredInventoryItems: StateFlow<List<InventoryItem>> = combine(
    allInventoryItems,
    _inventorySearchQuery,
    _inventoryCategoryFilter,
    _inventoryStockFilter
  ) { items, query, category, stockFilter ->
    items.filter { item ->
      val matchesQuery = query.isBlank() ||
        item.name.contains(query, ignoreCase = true) ||
        item.category.contains(query, ignoreCase = true) ||
        item.supplierName.contains(query, ignoreCase = true) ||
        item.location.contains(query, ignoreCase = true)

      val matchesCategory = category == "الكل" || item.category == category

      val matchesStock = when (stockFilter) {
        "LOW_STOCK" -> item.isLowStock
        "IN_STOCK" -> !item.isLowStock
        else -> true
      }

      matchesQuery && matchesCategory && matchesStock
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // --- Filtered Shipments Stream ---
  val filteredShipments: StateFlow<List<Shipment>> = combine(
    allShipments,
    _shipmentSearchQuery,
    _selectedStatusFilter
  ) { shipments, query, statusFilter ->
    shipments.filter { shipment ->
      val matchesQuery = query.isBlank() ||
        shipment.shipmentNumber.contains(query, ignoreCase = true) ||
        shipment.labName.contains(query, ignoreCase = true) ||
        shipment.workTypeName.contains(query, ignoreCase = true) ||
        shipment.clinicOrDoctorName.contains(query, ignoreCase = true) ||
        shipment.patientName.contains(query, ignoreCase = true) ||
        shipment.toothNumbers.contains(query, ignoreCase = true) ||
        shipment.shade.contains(query, ignoreCase = true)

      val matchesStatus = statusFilter == null || shipment.status == statusFilter
      matchesQuery && matchesStatus
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // --- Dashboard Computed Stats ---
  // FIX: `_currency` added as a combine source so changing the base currency in
  // Settings immediately re-emits the dashboard totals (previously stale until an
  // unrelated emission occurred).
  val dashboardStats: StateFlow<DashboardStats> = combine(
    allShipments,
    allPayments,
    _exchangeRates,
    _currency
  ) { shipments, payments, rates, currencyCode ->
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startOfToday = calendar.timeInMillis

    val totalShipments = shipments.size
    val todayShipments = shipments.count { it.orderDate >= startOfToday }
    val totalPieces = shipments.sumOf { it.pieceCount }
    val newCount = shipments.count { it.status == ShipmentStatus.NEW }
    val inProgressCount = shipments.count { it.status == ShipmentStatus.IN_PROGRESS }
    val readyCount = shipments.count { it.status == ShipmentStatus.READY }
    val receivedCount = shipments.count { it.status == ShipmentStatus.RECEIVED }
    val lateCount = shipments.count { DateUtils.isLate(it.expectedDeliveryDate, it.status) }

    // Breakdown by currency
    fun calculateCurrencyBalance(curr: AppCurrency): CurrencyBalance {
      val currShipments = shipments.filter { AppCurrency.fromCode(it.currency) == curr }
      val currPayments = payments.filter { AppCurrency.fromCode(it.currency) == curr }
      val billed = currShipments.sumOf { it.totalPrice }
      val paid = currPayments.sumOf { it.amount }
      return CurrencyBalance(
        currency = curr,
        totalBilled = billed,
        totalPaid = paid,
        remainingBalance = (billed - paid).coerceAtLeast(0.0),
        shipmentCount = currShipments.size,
        pieceCount = currShipments.sumOf { it.pieceCount }
      )
    }

    val yerStats = calculateCurrencyBalance(AppCurrency.YER)
    val sarStats = calculateCurrencyBalance(AppCurrency.SAR)
    val usdStats = calculateCurrencyBalance(AppCurrency.USD)

    val baseCurr = AppCurrency.fromCode(currencyCode)
    val totalValuation = rates.convert(yerStats.remainingBalance, AppCurrency.YER, baseCurr) +
      rates.convert(sarStats.remainingBalance, AppCurrency.SAR, baseCurr) +
      rates.convert(usdStats.remainingBalance, AppCurrency.USD, baseCurr)

    // Consolidated billed/paid totals expressed in the CURRENT base currency
    // (previously hardcoded to SAR regardless of the selected base currency).
    val totalBilledAll = rates.convert(yerStats.totalBilled, AppCurrency.YER, baseCurr) +
      rates.convert(sarStats.totalBilled, AppCurrency.SAR, baseCurr) +
      rates.convert(usdStats.totalBilled, AppCurrency.USD, baseCurr)
    val totalPaidAll = rates.convert(yerStats.totalPaid, AppCurrency.YER, baseCurr) +
      rates.convert(sarStats.totalPaid, AppCurrency.SAR, baseCurr) +
      rates.convert(usdStats.totalPaid, AppCurrency.USD, baseCurr)

    DashboardStats(
      totalShipments = totalShipments,
      todayShipments = todayShipments,
      totalPieces = totalPieces,
      newCount = newCount,
      inProgressCount = inProgressCount,
      readyCount = readyCount,
      receivedCount = receivedCount,
      lateCount = lateCount,
      yerStats = yerStats,
      sarStats = sarStats,
      usdStats = usdStats,
      totalBilled = totalBilledAll,
      totalPaid = totalPaidAll,
      totalOutstanding = totalValuation,
      baseCurrency = baseCurr
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardStats())

  // --- Lab Account Summaries (Admin & Accountant) ---
  val labAccountSummaries: StateFlow<List<LabAccountSummary>> = combine(
    allLabs,
    allShipments,
    allPayments
  ) { labs, shipments, payments ->
    labs.map { lab ->
      val labShipments = shipments.filter { it.labId == lab.id }
      val labPayments = payments.filter { it.labId == lab.id }
      val defaultCurr = AppCurrency.fromCode(lab.defaultCurrency)

      // Calculate balances for each of the 3 currencies
      val currencyBalancesMap = AppCurrency.ALL.associateWith { curr ->
        val cShipments = labShipments.filter { AppCurrency.fromCode(it.currency) == curr }
        val cPayments = labPayments.filter { AppCurrency.fromCode(it.currency) == curr }
        val billed = cShipments.sumOf { it.totalPrice }
        val paid = cPayments.sumOf { it.amount }
        CurrencyBalance(
          currency = curr,
          totalBilled = billed,
          totalPaid = paid,
          remainingBalance = (billed - paid).coerceAtLeast(0.0),
          shipmentCount = cShipments.size,
          pieceCount = cShipments.sumOf { it.pieceCount }
        )
      }

      val defaultBalance = currencyBalancesMap[defaultCurr] ?: CurrencyBalance(defaultCurr)
      val lastPayment = labPayments.maxByOrNull { it.paymentDate }

      LabAccountSummary(
        lab = lab,
        totalShipments = labShipments.size,
        totalPieces = labShipments.sumOf { it.pieceCount },
        totalBilled = defaultBalance.totalBilled,
        totalPaid = defaultBalance.totalPaid,
        remainingBalance = defaultBalance.remainingBalance,
        defaultCurrency = defaultCurr,
        currencyBalances = currencyBalancesMap,
        lastPaymentDate = lastPayment?.paymentDate
      )
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // --- User Switching ---
  fun switchUser(user: User) {
    _activeUser.value = user
  }

  fun setCurrency(code: String) {
    _currency.value = code
    viewModelScope.launch(Dispatchers.IO) {
      repository.setSetting("currency", code)
    }
  }

  fun setSearchQuery(query: String) {
    _shipmentSearchQuery.value = query
  }

  fun setStatusFilter(status: ShipmentStatus?) {
    _selectedStatusFilter.value = status
  }

  fun setReportPeriod(period: ReportPeriod) {
    _reportPeriod.value = period
  }

  // --- Smart Pricing Lookup Helper ---
  suspend fun getEstimatedPrice(labId: Long, workTypeId: Long, pieceCount: Int, discount: Double = 0.0): Pair<Double, Double> {
    return repository.calculatePrice(labId, workTypeId, pieceCount, discount)
  }

  suspend fun generateNextShipmentNumber(): String {
    return repository.generateNextShipmentNumber()
  }

  // --- CRUD Operations ---
  fun createShipment(
    clinicOrDoctorName: String,
    patientName: String,
    patientPhone: String = "",
    labId: Long,
    labName: String,
    workTypeId: Long,
    workTypeName: String,
    pieceCount: Int,
    toothNumbers: String,
    shade: String,
    shadeNotes: String,
    expectedDeliveryDate: Long,
    notes: String,
    isUrgent: Boolean,
    currency: String = "SAR",
    discount: Double = 0.0,
    customUnitPrice: Double? = null,
    onComplete: (Long) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val shipmentNumber = repository.generateNextShipmentNumber()
      val (calculatedUnit, calculatedTotal) = repository.calculatePrice(labId, workTypeId, pieceCount, discount)
      val finalUnit = customUnitPrice ?: calculatedUnit
      val finalTotal = if (customUnitPrice != null) ((customUnitPrice * pieceCount) - discount).coerceAtLeast(0.0) else calculatedTotal

      val shipment = Shipment(
        shipmentNumber = shipmentNumber,
        clinicOrDoctorName = clinicOrDoctorName,
        patientName = patientName,
        patientPhone = patientPhone,
        labId = labId,
        labName = labName,
        workTypeId = workTypeId,
        workTypeName = workTypeName,
        pieceCount = pieceCount,
        toothNumbers = toothNumbers,
        shade = shade,
        shadeNotes = shadeNotes,
        expectedDeliveryDate = expectedDeliveryDate,
        notes = notes,
        isUrgent = isUrgent,
        currency = currency,
        unitPrice = finalUnit,
        totalPrice = finalTotal,
        discount = discount,
        status = ShipmentStatus.NEW,
        createdByUserId = getActiveUserSafe().id,
        createdByName = getActiveUserSafe().fullName
      )
      val currentUser = getActiveUserSafe()
      val id = repository.createShipment(shipment, currentUser)
      val savedShipment = shipment.copy(id = id)

      if (_notificationsEnabled.value) {
        NotificationHelper.showNewShipmentNotification(
          getApplication(),
          savedShipment,
          currentUser.fullName
        )
      }

      triggerAutoBackupNow()

      launch(Dispatchers.Main) { onComplete(id) }
    }
  }

  fun createShipmentWithFirestore(
    clinicOrDoctorName: String,
    patientName: String,
    patientPhone: String = "",
    labId: Long,
    labName: String,
    workTypeId: Long,
    workTypeName: String,
    pieceCount: Int,
    toothNumbers: String,
    shade: String,
    shadeNotes: String,
    expectedDeliveryDate: Long,
    notes: String,
    isUrgent: Boolean,
    currency: String = "SAR",
    discount: Double = 0.0,
    customUnitPrice: Double? = null,
    syncToFirestore: Boolean = true,
    onResult: (Long, Boolean, String) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentUser = getActiveUserSafe()
      val shipmentNumber = repository.generateNextShipmentNumber()
      val (calculatedUnit, calculatedTotal) = repository.calculatePrice(labId, workTypeId, pieceCount, discount)
      val finalUnit = customUnitPrice ?: calculatedUnit
      val finalTotal = if (customUnitPrice != null) ((customUnitPrice * pieceCount) - discount).coerceAtLeast(0.0) else calculatedTotal

      val shipment = Shipment(
        shipmentNumber = shipmentNumber,
        clinicOrDoctorName = clinicOrDoctorName,
        patientName = patientName,
        patientPhone = patientPhone,
        labId = labId,
        labName = labName,
        workTypeId = workTypeId,
        workTypeName = workTypeName,
        pieceCount = pieceCount,
        toothNumbers = toothNumbers,
        shade = shade,
        shadeNotes = shadeNotes,
        expectedDeliveryDate = expectedDeliveryDate,
        notes = notes,
        isUrgent = isUrgent,
        currency = currency,
        unitPrice = finalUnit,
        totalPrice = finalTotal,
        discount = discount,
        status = ShipmentStatus.NEW,
        createdByUserId = currentUser.id,
        createdByName = currentUser.fullName
      )
      val id = repository.createShipment(shipment, currentUser)
      val savedShipment = shipment.copy(id = id)

      if (_notificationsEnabled.value) {
        NotificationHelper.showNewShipmentNotification(
          getApplication(),
          savedShipment,
          currentUser.fullName
        )
      }

      var firestoreSuccess = true
      var firestoreMsg = "تم حفظ الإرسالية بنجاح في قاعدة البيانات"

      if (syncToFirestore) {
        val res = cloudSyncManager.saveSingleShipmentToFirestore(savedShipment)
        firestoreSuccess = res.first
        firestoreMsg = res.second
      }

      launch(Dispatchers.Main) {
        onResult(id, firestoreSuccess, firestoreMsg)
      }
    }
  }

  fun updateShipment(shipment: Shipment, onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentUser = getActiveUserSafe()
      val existing = allShipments.value.find { it.id == shipment.id }
      val oldStatus = existing?.status ?: shipment.status
      repository.updateShipment(shipment, currentUser)

      if (_notificationsEnabled.value && oldStatus != shipment.status) {
        NotificationHelper.showStatusChangeNotification(
          getApplication(),
          shipment,
          oldStatus,
          shipment.status,
          currentUser.fullName
        )
      }

      triggerAutoBackupNow()

      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun updateShipmentWithFirestore(
    shipment: Shipment,
    syncToFirestore: Boolean = true,
    onResult: (Boolean, String) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentUser = getActiveUserSafe()
      val existing = allShipments.value.find { it.id == shipment.id }
      val oldStatus = existing?.status ?: shipment.status
      repository.updateShipment(shipment, currentUser)

      if (_notificationsEnabled.value && oldStatus != shipment.status) {
        NotificationHelper.showStatusChangeNotification(
          getApplication(),
          shipment,
          oldStatus,
          shipment.status,
          currentUser.fullName
        )
      }

      var firestoreSuccess = true
      var firestoreMsg = "تم تحديث الإرسالية بنجاح"

      if (syncToFirestore) {
        val res = cloudSyncManager.saveSingleShipmentToFirestore(shipment)
        firestoreSuccess = res.first
        firestoreMsg = res.second
      }

      launch(Dispatchers.Main) {
        onResult(firestoreSuccess, firestoreMsg)
      }
    }
  }

  fun updateShipmentStatus(shipmentId: Long, status: ShipmentStatus) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentUser = getActiveUserSafe()
      val existing = allShipments.value.find { it.id == shipmentId }
      val oldStatus = existing?.status ?: ShipmentStatus.NEW
      repository.updateShipmentStatus(shipmentId, status, currentUser)

      if (existing != null && _notificationsEnabled.value && oldStatus != status) {
        NotificationHelper.showStatusChangeNotification(
          getApplication(),
          existing.copy(status = status),
          oldStatus,
          status,
          currentUser.fullName
        )
      }
    }
  }

  fun deleteShipment(shipment: Shipment) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteShipment(shipment, getActiveUserSafe())
    }
  }

  fun addLaboratory(
    name: String,
    phone: String,
    address: String,
    managerName: String,
    offeredWorkTypes: String,
    notes: String,
    defaultCurrency: String = "SAR",
    onComplete: () -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val lab = Laboratory(
        name = name,
        phone = phone,
        address = address,
        managerName = managerName,
        offeredWorkTypes = offeredWorkTypes,
        notes = notes,
        defaultCurrency = defaultCurrency,
        status = LabStatus.ACTIVE
      )
      repository.insertLab(lab, getActiveUserSafe())
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun updateLaboratory(lab: Laboratory, onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateLab(lab, getActiveUserSafe())
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun addWorkType(
    nameAr: String,
    nameEn: String,
    description: String,
    defaultPrice: Double,
    category: String,
    onComplete: () -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val wt = WorkType(
        nameAr = nameAr,
        nameEn = nameEn,
        description = description,
        defaultPrice = defaultPrice,
        category = category,
        isActive = true
      )
      repository.insertWorkType(wt)
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun updateWorkType(workType: WorkType, onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateWorkType(workType)
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun setLabCustomPrice(labId: Long, workTypeId: Long, price: Double) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.setLabPrice(labId, workTypeId, price, getActiveUserSafe())
    }
  }

  fun recordPayment(
    labId: Long,
    labName: String,
    amount: Double,
    currency: String = "SAR",
    paidAmount: Double = amount,
    paidCurrency: String = currency,
    exchangeRate: Double = 1.0,
    paymentMethod: PaymentMethod = PaymentMethod.CASH,
    receiptNumber: String = "",
    notes: String = "",
    onComplete: () -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentUser = getActiveUserSafe()
      val payment = Payment(
        labId = labId,
        labName = labName,
        amount = amount,
        currency = currency,
        paidAmount = paidAmount,
        paidCurrency = paidCurrency,
        exchangeRate = exchangeRate,
        paymentDate = System.currentTimeMillis(),
        paymentMethod = paymentMethod,
        receiptNumber = receiptNumber,
        notes = notes,
        recordedByUserId = currentUser.id,
        recordedByName = currentUser.fullName
      )
      repository.recordPayment(payment, currentUser)
      triggerAutoBackupNow()
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun deletePayment(payment: Payment) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.deletePayment(payment)
    }
  }

  fun resetToDemoData() {
    viewModelScope.launch(Dispatchers.IO) {
      repository.resetToDefaultDemoData()
      triggerAutoBackupNow()
    }
  }

  fun clearMockDemoData(onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.wipeAllTransactionsOnly(getActiveUserSafe())
      triggerAutoBackupNow()
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun wipeAllTransactions(onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.wipeAllTransactionsOnly(getActiveUserSafe())
      triggerAutoBackupNow()
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun factoryResetApp(onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.factoryResetAll(getActiveUserSafe())
      _activeUser.value = null
      _isAppLocked.value = true
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  // --- Users Management & Security ---
  val userProvisioningService = com.aqlanlab.app.network.UserProvisioningService(getApplication())

  fun addUser(
    username: String,
    fullName: String,
    role: UserRole,
    pinCode: String,
    email: String = "",
    avatarColor: Long = 0xFF00687A,
    onComplete: (Boolean) -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val trimmedPin = pinCode.trim()
      val hashedPin = if (trimmedPin.isNotEmpty()) SecurityUtils.hashPin(trimmedPin) else ""
      val resolvedEmail = email.trim().ifEmpty { "${username.trim().lowercase()}@aqlanlab.com" }
      val newUser = User(
        username = username.trim(),
        fullName = fullName.trim(),
        email = resolvedEmail,
        role = role,
        pinCode = hashedPin,
        avatarColor = avatarColor
      )
      val id = repository.insertUser(newUser, getActiveUserSafe())
      launch(Dispatchers.Main) { onComplete(id > 0) }
    }
  }

  fun provisionUserWithCloudBackend(
    username: String,
    fullName: String,
    email: String,
    temporaryPassword: String,
    role: UserRole,
    pinCode: String = "",
    maxDevices: Int = 2,
    onResult: (Result<User>) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val trimmedPin = pinCode.trim()
      val hashedPin = if (trimmedPin.isNotEmpty()) SecurityUtils.hashPin(trimmedPin) else ""
      val baseUser = User(
        username = username.trim(),
        fullName = fullName.trim(),
        email = email.trim().lowercase(),
        role = role,
        pinCode = hashedPin,
        maxDevices = maxDevices,
        isActive = true,
        isApproved = true
      )

      val regResult = firebaseAuthManager.registerUserBySuperAdmin(
        newUser = baseUser,
        temporaryPass = temporaryPassword,
        permissions = listOf("read:shipments", "write:shipments")
      )

      if (regResult.isSuccess) {
        val createdUser = regResult.getOrThrow()
        val id = repository.insertUser(createdUser, getActiveUserSafe())
        val finalUser = createdUser.copy(id = id)
        launch(Dispatchers.Main) { onResult(Result.success(finalUser)) }
      } else {
        val err = regResult.exceptionOrNull() ?: Exception("فشل إنشاء الحساب عبر الخادم")
        launch(Dispatchers.Main) { onResult(Result.failure(err)) }
      }
    }
  }

  fun setUserActiveStatus(
    user: User,
    isActive: Boolean,
    reason: String = "",
    onResult: (Result<String>) -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      if (user.uid.isNotBlank()) {
        val res = userProvisioningService.setUserActiveStatus(user.uid, isActive, reason)
        if (res.isFailure) {
          launch(Dispatchers.Main) { onResult(res) }
          return@launch
        }
      }
      val updated = user.copy(isActive = isActive)
      repository.updateUser(updated, getActiveUserSafe())
      launch(Dispatchers.Main) { onResult(Result.success(if (isActive) "تم تفعيل الحساب." else "تم تعطيل الحساب.")) }
    }
  }

  fun resetUserPassword(
    user: User,
    newPassword: String,
    onResult: (Result<String>) -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      if (user.uid.isNotBlank()) {
        val res = userProvisioningService.resetUserPassword(user.uid, newPassword)
        launch(Dispatchers.Main) { onResult(res) }
      } else {
        launch(Dispatchers.Main) { onResult(Result.failure(Exception("المستخدم ليس لديه معرف سحابي UID."))) }
      }
    }
  }

  fun updateUserRoleAndPermissions(
    user: User,
    newRole: UserRole,
    permissions: List<String> = emptyList(),
    maxDevices: Int? = null,
    onResult: (Result<String>) -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      if (user.uid.isNotBlank()) {
        val res = userProvisioningService.updateUserRoleAndPermissions(user.uid, newRole, permissions, maxDevices)
        if (res.isFailure) {
          launch(Dispatchers.Main) { onResult(res) }
          return@launch
        }
      }
      val updated = user.copy(role = newRole, maxDevices = maxDevices ?: user.maxDevices)
      repository.updateUser(updated, getActiveUserSafe())
      launch(Dispatchers.Main) { onResult(Result.success("تم تحديث الدور والصلاحيات بنجاح.")) }
    }
  }

  fun revokeUserSessions(
    user: User,
    onResult: (Result<String>) -> Unit = {}
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      if (user.uid.isNotBlank()) {
        val res = userProvisioningService.revokeUserSessions(user.uid)
        launch(Dispatchers.Main) { onResult(res) }
      } else {
        launch(Dispatchers.Main) { onResult(Result.failure(Exception("المستخدم غير مسجل سحابياً."))) }
      }
    }
  }

  fun updateUser(user: User, onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateUser(user, getActiveUserSafe())
      if (_activeUser.value?.id == user.id) {
        _activeUser.value = user
      }
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun deleteUser(user: User, onComplete: () -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
      if (user.uid.isNotBlank()) {
        firebaseAuthManager.deleteAuthorizedUserFromFirestore(user.uid)
      }
      repository.deleteUser(user, getActiveUserSafe())
      launch(Dispatchers.Main) { onComplete() }
    }
  }

  fun verifyPin(user: User, enteredPin: String): Boolean {
    if (enteredPin.isBlank() || user.pinCode.isBlank()) return false
    return SecurityUtils.verifyPin(enteredPin.trim(), user.pinCode.trim())
  }

  // --- Detailed Statement (مدين / دائن / عدد القطع / الرصيد) ---
  fun getDetailedStatementForLab(
    labId: Long,
    currencyFilter: AppCurrency? = null,
    startDate: Long? = null,
    endDate: Long? = null
  ): List<DetailedStatementItem> {
    val shipments = allShipments.value
      .filter { it.labId == labId }
      .filter { currencyFilter == null || AppCurrency.fromCode(it.currency) == currencyFilter }
      .filter { startDate == null || it.orderDate >= startDate }
      .filter { endDate == null || it.orderDate <= endDate }

    val payments = allPayments.value
      .filter { it.labId == labId }
      .filter { currencyFilter == null || AppCurrency.fromCode(it.currency) == currencyFilter }
      .filter { startDate == null || it.paymentDate >= startDate }
      .filter { endDate == null || it.paymentDate <= endDate }

    // Combine and sort chronologically
    val rawItems = mutableListOf<Pair<Long, Any>>()
    shipments.forEach { rawItems.add(Pair(it.orderDate, it)) }
    payments.forEach { rawItems.add(Pair(it.paymentDate, it)) }
    rawItems.sortBy { it.first }

    var runningBalance = 0.0
    val result = mutableListOf<DetailedStatementItem>()

    rawItems.forEach { pair ->
      when (val item = pair.second) {
        is Shipment -> {
          runningBalance += item.totalPrice
          result.add(
            DetailedStatementItem(
              id = "S_${item.id}",
              date = item.orderDate,
              type = "إرسالية عمل",
              isShipment = true,
              referenceNumber = item.shipmentNumber,
              doctorName = item.clinicOrDoctorName,
              patientName = item.patientName,
              workDetails = item.workTypeName,
              pieceCount = item.pieceCount,
              toothNumbers = item.toothNumbers,
              shade = item.shade,
              currency = item.currency,
              debit = item.totalPrice,
              credit = 0.0,
              runningBalance = runningBalance
            )
          )
        }
        is Payment -> {
          runningBalance -= item.amount
          val noteDesc = if (item.paidCurrency != item.currency && item.exchangeRate != 1.0) {
            "${item.notes.ifEmpty { "دفعة مسددة للمعمل" }} (مسدد: ${AppCurrency.fromCode(item.paidCurrency).formatAmount(item.paidAmount)} بسعر صرف ${item.exchangeRate})"
          } else {
            item.notes.ifEmpty { "دفعة مسددة للمعمل" }
          }
          result.add(
            DetailedStatementItem(
              id = "P_${item.id}",
              date = item.paymentDate,
              type = "سند سداد (${item.paymentMethod.titleAr})",
              isShipment = false,
              referenceNumber = if (item.receiptNumber.isNotEmpty()) item.receiptNumber else "-",
              doctorName = "",
              patientName = noteDesc,
              workDetails = item.paymentMethod.titleAr,
              pieceCount = 0,
              toothNumbers = "",
              shade = "",
              currency = item.currency,
              debit = 0.0,
              credit = item.amount,
              runningBalance = runningBalance
            )
          )
        }
      }
    }
    return result
  }

  // --- Cloud & Online Operations ---
  fun updateCloudConfig(url: String, key: String) {
    cloudSyncManager.updateServerConfig(url, key)
  }

  fun updateClinicConfig(clinicId: String, clinicName: String) {
    cloudSyncManager.updateClinicConfig(clinicId, clinicName)
    refreshFirestoreSnapshots()
  }

  fun setAutoSyncEnabled(enabled: Boolean) {
    cloudSyncManager.setAutoSyncEnabled(enabled)
  }

  fun triggerCloudSync() {
    viewModelScope.launch {
      cloudSyncManager.syncToFirestore(getActiveUserSafe())
      uploadBackupToStorage(isAuto = false)
    }
  }

  fun syncShipmentsToFirestore(onResult: ((Boolean, String) -> Unit)? = null) {
    viewModelScope.launch {
      val shipmentsCount = allShipments.value.size
      val success = cloudSyncManager.syncToFirestore(getActiveUserSafe())
      uploadBackupToStorage(isAuto = false)
      if (success) {
        val msg = "تم رفع ونسخ $shipmentsCount إرسالية بنجاح إلى Firebase Firestore السحابي ☁️"
        onResult?.invoke(true, msg)
      } else {
        val msg = cloudSyncManager.syncMessage.value.ifEmpty { "تعذر إتمام المزامنة السحابية. يرجى التحقق من اتصال الإنترنت." }
        onResult?.invoke(false, msg)
      }
    }
  }

  fun triggerFirestoreBackup(onComplete: ((Boolean) -> Unit)? = null) {
    viewModelScope.launch {
      val success = cloudSyncManager.syncToFirestore(getActiveUserSafe())
      uploadBackupToStorage(isAuto = false)
      onComplete?.invoke(success)
    }
  }

  // --- Firebase Storage Enterprise Cloud Backup ---
  fun uploadBackupToStorage(isAuto: Boolean = false, onResult: ((Boolean, String) -> Unit)? = null) {
    viewModelScope.launch {
      val currentUser = getActiveUserSafe()
      val res = firebaseStorageBackupManager.uploadBackupToStorage(
        clinicId = clinicId.value,
        clinicName = clinicName.value,
        currentUser = currentUser,
        isAutoBackup = isAuto
      )
      if (res.isSuccess) {
        val info = res.getOrNull()
        onResult?.invoke(true, "تم رفع النسخة الاحتياطية بنجاح إلى Firebase Storage (${info?.formattedSize})")
      } else {
        onResult?.invoke(false, res.exceptionOrNull()?.localizedMessage ?: "فشل الرفع إلى Firebase Storage")
      }
    }
  }

  fun restoreFromStorageBackup(backupInfo: FirebaseStorageBackupInfo, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val res = firebaseStorageBackupManager.restoreFromStorageBackup(backupInfo)
      if (res.isSuccess) {
        val count = res.getOrNull() ?: 0
        onResult(true, "تمت استعادة $count سجل بنجاح من Firebase Storage")
      } else {
        onResult(false, res.exceptionOrNull()?.localizedMessage ?: "فشل الاستعادة من Firebase Storage")
      }
    }
  }

  fun setAutoStorageBackupEnabled(enabled: Boolean) {
    firebaseStorageBackupManager.setAutoBackupEnabled(enabled)
  }

  fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
    firebaseStorageBackupManager.setAutoBackupFrequency(frequency)
  }

  fun fetchStorageBackups() {
    viewModelScope.launch {
      firebaseStorageBackupManager.fetchAvailableStorageBackups(clinicId.value)
    }
  }

  fun triggerAutoBackupNow() {
    firebaseStorageBackupManager.triggerAutoBackupDebounced(
      coroutineScope = viewModelScope,
      clinicId = clinicId.value,
      clinicName = clinicName.value,
      currentUser = getActiveUserSafe()
    )
  }

  fun restoreFromFirestore(snapshotId: String? = null, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val success = cloudSyncManager.restoreFromFirestore(snapshotId, getActiveUserSafe())
      onResult(success)
    }
  }

  fun refreshFirestoreSnapshots() {
    viewModelScope.launch {
      cloudSyncManager.fetchAvailableSnapshots()
      firebaseStorageBackupManager.fetchAvailableStorageBackups(clinicId.value)
    }
  }

  suspend fun exportDataJson(): String {
    return cloudSyncManager.exportToJsonString()
  }

  fun importDataJson(json: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val success = cloudSyncManager.importFromJsonString(json)
      onResult(success)
    }
  }

  fun sendShipmentToWhatsApp(context: Context, shipment: Shipment, lab: Laboratory?) {
    val phone = lab?.phone ?: ""
    val trackingUrl = cloudSyncManager.generateOnlineTrackingUrl(shipment)
    val text = """
      🏥 *${com.aqlanlab.app.ui.components.ClinicInfo.CLINIC_NAME}*
      📍 ${com.aqlanlab.app.ui.components.ClinicInfo.ADDRESS}
      📞 ${com.aqlanlab.app.ui.components.ClinicInfo.PHONES}
      ═════════════════════════════════
      🦷 *إشعار إرسالية معمل أسنان*
      ---------------------------------
      📋 رقم الإرسالية: ${shipment.shipmentNumber}
      🏥 المعمل: ${shipment.labName}
      👨‍⚕️ الطبيب: ${shipment.clinicOrDoctorName.ifEmpty { com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME }}
      👤 المريض: ${shipment.patientName}
      🛠️ نوع العمل: ${shipment.workTypeName} (${shipment.pieceCount} سن/قطعة)
      📅 تاريخ الإرسال: ${DateUtils.formatShortDate(shipment.orderDate)}
      ⏰ موعد التسليم: ${DateUtils.formatShortDate(shipment.expectedDeliveryDate)}
      💰 التكلفة: ${shipment.totalPrice} ${_currency.value}
      ---------------------------------
      🌐 *رابط التتبع السحابي المباشر عبر الإنترنت:*
      $trackingUrl
      ---------------------------------
      📞 هاتف للتواصل: ${com.aqlanlab.app.ui.components.ClinicInfo.PHONES}
      ═════════════════════════════════
    """.trimIndent()
    cloudSyncManager.shareViaWhatsApp(context, phone, text)
  }

  fun shareInvoiceOnline(context: Context, lab: Laboratory, totalBilled: Double, totalPaid: Double, remaining: Double) {
    val phone = lab.phone
    val text = """
      🏥 *${com.aqlanlab.app.ui.components.ClinicInfo.CLINIC_NAME}*
      📍 ${com.aqlanlab.app.ui.components.ClinicInfo.ADDRESS}
      📞 ${com.aqlanlab.app.ui.components.ClinicInfo.PHONES}
      ═════════════════════════════════
      🧾 *كشف حساب معمل أسنان*
      ---------------------------------
      🏥 المعمل: ${lab.name}
      👤 المسؤول: ${lab.managerName}
      💵 إجمالي المطالبات: $totalBilled ${_currency.value}
      💳 إجمالي المسدد: $totalPaid ${_currency.value}
      ⚠️ الرصيد المتبقي: $remaining ${_currency.value}
      📅 تاريخ التقرير: ${DateUtils.formatShortDate(System.currentTimeMillis())}
      ---------------------------------
      📍 ${com.aqlanlab.app.ui.components.ClinicInfo.ADDRESS}
      📞 هاتف/واتساب: ${com.aqlanlab.app.ui.components.ClinicInfo.PHONES}
      ═════════════════════════════════
    """.trimIndent()
    cloudSyncManager.shareViaWhatsApp(context, phone, text)
  }

  // --- Inventory State Actions & Methods ---
  fun setInventorySearchQuery(query: String) {
    _inventorySearchQuery.value = query
  }

  fun setInventoryCategoryFilter(category: String) {
    _inventoryCategoryFilter.value = category
  }

  fun setInventoryStockFilter(filter: String) {
    _inventoryStockFilter.value = filter
  }

  fun addInventoryItem(item: InventoryItem, onComplete: ((Long) -> Unit)? = null) {
    viewModelScope.launch {
      val id = repository.addInventoryItem(item, getActiveUserSafe())
      onComplete?.invoke(id)
    }
  }

  fun updateInventoryItem(item: InventoryItem, onComplete: (() -> Unit)? = null) {
    viewModelScope.launch {
      repository.updateInventoryItem(item, getActiveUserSafe())
      onComplete?.invoke()
    }
  }

  fun deleteInventoryItem(item: InventoryItem, onComplete: (() -> Unit)? = null) {
    viewModelScope.launch {
      repository.deleteInventoryItem(item, getActiveUserSafe())
      onComplete?.invoke()
    }
  }

  fun adjustInventoryStock(
    itemId: Long,
    quantityChange: Double,
    type: InventoryTransactionType,
    reason: String,
    onComplete: ((Boolean) -> Unit)? = null
  ) {
    viewModelScope.launch {
      val currentUser = getActiveUserSafe()
      val success = repository.adjustInventoryStock(
        itemId = itemId,
        quantityChange = quantityChange,
        type = type,
        reason = reason,
        currentUser = currentUser
      )
      onComplete?.invoke(success)
    }
  }

  fun quickRestockItem(item: InventoryItem, amount: Double = 1.0) {
    adjustInventoryStock(
      itemId = item.id,
      quantityChange = amount,
      type = InventoryTransactionType.STOCK_IN,
      reason = "توريد سريع (+${amount.toInt()} ${item.unit})"
    )
  }

  fun quickConsumeItem(item: InventoryItem, amount: Double = 1.0) {
    adjustInventoryStock(
      itemId = item.id,
      quantityChange = amount,
      type = InventoryTransactionType.USAGE_OUT,
      reason = "صرف واستخدام سريع (-${amount.toInt()} ${item.unit})"
    )
  }

  fun shareSupplierOrderViaWhatsApp(context: Context, item: InventoryItem, orderQuantity: Double) {
    val currentUser = getActiveUserSafe()
    val phone = item.supplierPhone
    val text = """
      📦 *طلب توريد مواد سنية - طلبية شراء*
      ---------------------------------
      🏥 المركز: مركز النخبة لطب وتجميل الأسنان
      🧪 المادة المطلوبة: ${item.name}
      🏷️ التصنيف: ${item.category}
      📊 الكمية المطلوبة: $orderQuantity ${item.unit}
      🏢 المورد: ${item.supplierName.ifEmpty { "المورد المعتمد" }}
      📍 موقع التوريد: ${item.location}
      📅 التاريخ: ${DateUtils.formatShortDate(System.currentTimeMillis())}
      👤 المسؤول: ${currentUser.fullName}
      ---------------------------------
      يرجى تأكيد توفر الكمية وتزويدنا بوقت التوصيل والفاتورة. شكراً لكم.
    """.trimIndent()
    cloudSyncManager.shareViaWhatsApp(context, phone, text)
  }

  // ─── SMS & WHATSAPP GATEWAY INTEGRATION ──────────────────────
  val gatewayManager = com.aqlanlab.app.network.SmsAndWhatsAppGatewayManager(getApplication())
  private val _smsConfig = MutableStateFlow(gatewayManager.loadSmsConfig())
  val smsConfig: StateFlow<com.aqlanlab.app.network.SmsGatewayConfig> = _smsConfig.asStateFlow()

  private val _whatsAppConfig = MutableStateFlow(gatewayManager.loadWhatsAppConfig())
  val whatsAppConfig: StateFlow<com.aqlanlab.app.network.WhatsAppGatewayConfig> = _whatsAppConfig.asStateFlow()

  fun reloadGatewayConfigs() {
    _smsConfig.value = gatewayManager.loadSmsConfig()
    _whatsAppConfig.value = gatewayManager.loadWhatsAppConfig()
  }

  fun updateSmsConfig(config: com.aqlanlab.app.network.SmsGatewayConfig) {
    gatewayManager.saveSmsConfig(config)
    _smsConfig.value = config
  }

  fun updateWhatsAppConfig(config: com.aqlanlab.app.network.WhatsAppGatewayConfig) {
    gatewayManager.saveWhatsAppConfig(config)
    _whatsAppConfig.value = config
  }

  fun testSmsGateway(phone: String, customText: String? = null, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val sender = _smsConfig.value.senderId.ifBlank { "AqlanDental" }
      val msg = customText ?: "تجربة إرسال رسالة SMS ناجحة من $sender - مركز د. عقلان لطب الأسنان 🦷✨"
      val result = gatewayManager.sendSmsViaGateway(phone, msg)
      onResult(result.first, result.second)
    }
  }

  fun testWhatsAppGateway(phone: String, customText: String? = null, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val msg = customText ?: "تجربة إرسال واتساب سحابية ناجحة من مركز د. عقلان لطب وتجميل الأسنان 🦷✨"
      val result = gatewayManager.sendWhatsAppViaCloudGateway(phone, msg)
      onResult(result.first, result.second)
    }
  }
}

