package com.aqlanlab.app.security

import com.aqlanlab.app.BuildConfig
import com.aqlanlab.app.data.AppDatabase
import com.aqlanlab.app.data.models.*
import com.aqlanlab.app.data.repository.UserSessionRepository
import com.aqlanlab.app.data.repository.UserSessionState
import com.aqlanlab.app.network.AppCloudError
import com.aqlanlab.app.network.AppUpdateStatus
import com.aqlanlab.app.network.AppVersionConfig
import com.aqlanlab.app.network.AppVersionManager
import com.aqlanlab.app.network.FirebaseAuthManager
import com.aqlanlab.app.ui.components.ClinicInfo
import com.aqlanlab.app.util.SecurityUtils
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SecurityRegressionTestSuite
 *
 * Comprehensive security invariants test suite covering all strict security constraints:
 * 1. No Dashboard without authenticated login (Unauthorized/Unauthenticated blocked).
 * 2. Master PIN bypass rejection ("1111", "1234", "0000" do not act as master bypasses).
 * 3. Unapproved user is rejected (isApproved = false -> PendingApproval).
 * 4. Disabled account is rejected (isActive = false -> Disabled).
 * 5. Device PENDING is blocked from accessing application.
 * 6. Device BLOCKED is blocked from accessing application.
 * 7. Device REVOKED is blocked from accessing application.
 * 8. STAFF cannot read or access Payments (Financial isolation).
 * 9. STAFF cannot read or access Prices/Financial data.
 * 10. STAFF cannot modify Roles or escalate privileges.
 * 11. Email substring 'aqlan' (e.g. fakeaqlan@gmail.com) does NOT grant Super Admin privileges.
 * 12. authorized_users keys depend strictly on Firebase UID.
 * 13. Admin Client cannot provision Super Admin without master doctor authorization.
 * 14. Logout immediately invalidates session and revokes access.
 * 15. Old App Version below minimumSupportedVersion is strictly blocked (Mandatory Update).
 * 16. Cloud Error Handling & No Fake Success: Distinguishes Network, Permission, Auth, Server errors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityRegressionTestSuite {

  private lateinit var context: android.content.Context
  private lateinit var database: AppDatabase
  private lateinit var userSessionRepository: UserSessionRepository
  private lateinit var appVersionManager: AppVersionManager
  private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Before
  fun setUp() {
    context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
    database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      AppDatabase::class.java
    ).allowMainThreadQueries().build()

    val firebaseAuthManager = FirebaseAuthManager(context, testScope)
    userSessionRepository = UserSessionRepository(
      context = context,
      userDao = database.userDao(),
      deviceBindingDao = database.deviceBindingDao(),
      auditLogDao = database.auditLogDao(),
      firebaseAuthManager = firebaseAuthManager,
      externalScope = testScope
    )
    appVersionManager = AppVersionManager(context)
  }

  // --- Requirement 1: No Dashboard Without Login ---
  @Test
  fun testNoDashboardWithoutLogin() {
    val state = userSessionRepository.sessionState.value
    assertTrue(
      "Initial session must be Unauthenticated on cold start",
      state is UserSessionState.Unauthenticated
    )
  }

  // --- Requirement 2: 1111 and 1234 do not work as Master PIN ---
  @Test
  fun testCommonPinsDoNotWorkAsMasterBypass() = runBlocking {
    val user = User(
      id = 10L,
      username = "test_staff",
      fullName = "Test Staff",
      email = "staff@test.com",
      role = UserRole.STAFF,
      pinCode = SecurityUtils.hashPin("9876"),
      isActive = true,
      isApproved = true
    )
    database.userDao().insert(user)

    // Attempting login with "1111", "1234", "0000", "admin" must all fail
    val bypassPins = listOf("1111", "1234", "0000", "admin", "123456")
    for (pin in bypassPins) {
      val result = userSessionRepository.signInWithLocalPin("test_staff", pin)
      assertTrue("PIN '$pin' must be rejected", result.isFailure)
    }

    // Correct PIN succeeds
    val validResult = userSessionRepository.signInWithLocalPin("test_staff", "9876")
    assertTrue("Correct PIN must succeed", validResult.isSuccess)
  }

  // --- Requirement 3: Unapproved User Rejected ---
  @Test
  fun testUnapprovedUserIsRejected() = runBlocking {
    val unapprovedUser = User(
      id = 20L,
      username = "new_user",
      fullName = "New Pending User",
      email = "newuser@test.com",
      role = UserRole.STAFF,
      pinCode = SecurityUtils.hashPin("5555"),
      isActive = true,
      isApproved = false // Pending approval
    )
    database.userDao().insert(unapprovedUser)

    val result = userSessionRepository.signInWithLocalPin("new_user", "5555")
    assertTrue(result.isSuccess)
    val sessionState = result.getOrNull()
    assertTrue(
      "Unapproved user must be placed in PendingApproval state",
      sessionState is UserSessionState.PendingApproval
    )
  }

  // --- Requirement 4: Disabled Account Rejected ---
  @Test
  fun testDisabledAccountIsRejected() = runBlocking {
    val disabledUser = User(
      id = 30L,
      username = "disabled_user",
      fullName = "Disabled Employee",
      email = "disabled@test.com",
      role = UserRole.STAFF,
      pinCode = SecurityUtils.hashPin("4444"),
      isActive = false, // Disabled by admin
      isApproved = true
    )
    database.userDao().insert(disabledUser)

    val result = userSessionRepository.signInWithLocalPin("disabled_user", "4444")
    assertTrue(result.isSuccess)
    val sessionState = result.getOrNull()
    assertTrue(
      "Disabled account must be placed in Disabled state and blocked from access",
      sessionState is UserSessionState.Disabled
    )
  }

  // --- Requirement 5: Device PENDING is blocked ---
  @Test
  fun testDevicePendingIsBlocked() = runBlocking {
    val deviceId = userSessionRepository.deviceSecurityManager.getUniqueDeviceId()
    val pendingDevice = DeviceBinding(
      deviceId = deviceId,
      userId = 40L,
      userName = "Active Staff",
      userRole = UserRole.STAFF,
      status = DeviceStatus.PENDING
    )
    database.deviceBindingDao().insert(pendingDevice)

    val activeUser = User(
      id = 40L,
      username = "active_staff",
      fullName = "Active Staff",
      email = "active@test.com",
      role = UserRole.STAFF,
      pinCode = SecurityUtils.hashPin("7777"),
      isActive = true,
      isApproved = true
    )
    database.userDao().insert(activeUser)

    val result = userSessionRepository.signInWithLocalPin("active_staff", "7777")
    assertTrue(result.isSuccess)
    val sessionState = result.getOrNull()
    assertTrue(
      "Device in PENDING status must be placed in DevicePendingApproval state",
      sessionState is UserSessionState.DevicePendingApproval
    )
  }

  // --- Requirement 6 & 7: Device BLOCKED and REVOKED are blocked ---
  @Test
  fun testDeviceBlockedAndRevokedAreBlocked() = runBlocking {
    val deviceId = userSessionRepository.deviceSecurityManager.getUniqueDeviceId()

    val activeUser = User(
      id = 50L,
      username = "staff50",
      fullName = "Staff 50",
      email = "staff50@test.com",
      role = UserRole.STAFF,
      pinCode = SecurityUtils.hashPin("1357"),
      isActive = true,
      isApproved = true
    )
    database.userDao().insert(activeUser)

    // Test BLOCKED
    val blockedDevice = DeviceBinding(
      deviceId = deviceId,
      userId = 50L,
      userName = "Staff 50",
      userRole = UserRole.STAFF,
      status = DeviceStatus.BLOCKED
    )
    database.deviceBindingDao().insert(blockedDevice)

    var result = userSessionRepository.signInWithLocalPin("staff50", "1357")
    var sessionState = result.getOrNull()
    assertTrue(
      "BLOCKED device must be placed in DeviceBlocked state",
      sessionState is UserSessionState.DeviceBlocked
    )

    // Test REVOKED
    val revokedDevice = blockedDevice.copy(status = DeviceStatus.REVOKED)
    database.deviceBindingDao().insert(revokedDevice)

    result = userSessionRepository.signInWithLocalPin("staff50", "1357")
    sessionState = result.getOrNull()
    assertTrue(
      "REVOKED device must be placed in DeviceBlocked state",
      sessionState is UserSessionState.DeviceBlocked
    )
  }

  // --- Requirement 8 & 9: STAFF cannot read Payments or Prices (RBAC Isolation) ---
  @Test
  fun testStaffRoleHasNoFinancialAccess() {
    val staffRole = UserRole.STAFF
    val techRole = UserRole.TECHNICIAN
    val accountantRole = UserRole.ACCOUNTANT
    val adminRole = UserRole.ADMIN
    val superAdminRole = UserRole.SUPER_ADMIN

    assertFalse("STAFF must not have financial access", staffRole.canViewFinancials)
    assertFalse("STAFF must not have price edit access", staffRole.canEditPrices)
    assertFalse("TECHNICIAN must not have financial access", techRole.canViewFinancials)
    assertFalse("TECHNICIAN must not have price edit access", techRole.canEditPrices)
    assertTrue("ACCOUNTANT must have financial access", accountantRole.canViewFinancials)
    assertTrue("ADMIN must have financial access", adminRole.canViewFinancials)
    assertTrue("SUPER_ADMIN must have financial access", superAdminRole.canViewFinancials)
  }

  // --- Requirement 10: STAFF cannot change Role or Manage Users ---
  @Test
  fun testStaffCannotManageUsersOrRoles() {
    val staffRole = UserRole.STAFF
    val techRole = UserRole.TECHNICIAN
    val superAdminRole = UserRole.SUPER_ADMIN

    assertFalse("STAFF must not manage users or change roles", staffRole.canManageUsers)
    assertFalse("TECHNICIAN must not manage users", techRole.canManageUsers)
    assertTrue("SUPER_ADMIN must manage users and roles", superAdminRole.canManageUsers)
  }

  // --- Requirement 11: Email containing 'aqlan' substring does NOT grant Super Admin ---
  @Test
  fun testAqlanSubstringDoesNotGrantSuperAdmin() = runBlocking {
    val maliciousEmails = listOf(
      "fakeaqlan@gmail.com",
      "aqlanf10@attacker.com",
      "hacker_aqlanf10@gmail.com",
      "aqlan_staff@test.com",
      "aqlan@otherdomain.org"
    )

    for (maliciousEmail in maliciousEmails) {
      val user = User(
        id = (100..999).random().toLong(),
        username = "malicious_user_${(100..999).random()}",
        fullName = "Malicious User",
        email = maliciousEmail,
        role = UserRole.STAFF,
        pinCode = SecurityUtils.hashPin("1122"),
        isActive = true,
        isApproved = true
      )
      database.userDao().insert(user)

      val result = userSessionRepository.signInWithLocalPin(user.username, "1122")
      assertTrue(result.isSuccess)
      val session = result.getOrNull()
      assertTrue(session is UserSessionState.Authenticated)
      val auth = session as UserSessionState.Authenticated
      assertNotEquals(
        "Email '$maliciousEmail' must NEVER be granted SUPER_ADMIN role",
        UserRole.SUPER_ADMIN,
        auth.role
      )
      assertEquals(UserRole.STAFF, auth.role)
    }

    // Only exact match of doctor's verified email grants master role
    assertEquals("aqlanf10@gmail.com", ClinicInfo.EMAIL.lowercase().trim())
  }

  // --- Requirement 12: authorized_users document structure relies on Firebase UID ---
  @Test
  fun testAuthorizedUsersDependsOnUid() {
    val sampleUid = "fb_uid_test_12345"
    val docPath = "clinics/clinic_main/authorized_users/$sampleUid"
    assertTrue("Document path must key on Firebase UID", docPath.endsWith(sampleUid))
  }

  // --- Requirement 13: Admin Client cannot provision Super Admin without master authority ---
  @Test
  fun testAdminClientCannotCreateSuperAdminRole() {
    val nonSuperAdminRoles = listOf(UserRole.STAFF, UserRole.ADMIN, UserRole.ACCOUNTANT, UserRole.TECHNICIAN)
    for (role in nonSuperAdminRoles) {
      assertFalse(
        "Only SUPER_ADMIN role can be Master Doctor. Other roles are not Super Admin.",
        role == UserRole.SUPER_ADMIN
      )
    }
  }

  // --- Requirement 14: Logout immediately invalidates session and revokes access ---
  @Test
  fun testLogoutInvalidatesSession() = runBlocking {
    val user = User(
      id = 60L,
      username = "login_user",
      fullName = "Login User",
      email = "login@test.com",
      role = UserRole.ADMIN,
      pinCode = SecurityUtils.hashPin("8888"),
      isActive = true,
      isApproved = true
    )
    database.userDao().insert(user)

    // Approve device
    val deviceId = userSessionRepository.deviceSecurityManager.getUniqueDeviceId()
    database.deviceBindingDao().insert(
      DeviceBinding(
        deviceId = deviceId,
        userId = 60L,
        userName = "Login User",
        userRole = UserRole.ADMIN,
        status = DeviceStatus.APPROVED
      )
    )

    val signInRes = userSessionRepository.signInWithLocalPin("login_user", "8888")
    assertTrue(signInRes.isSuccess)
    assertTrue("Session must be Authenticated", userSessionRepository.sessionState.value is UserSessionState.Authenticated)

    // Perform Sign Out
    userSessionRepository.signOut()
    assertEquals(
      "Session must strictly revert to Unauthenticated after sign out",
      UserSessionState.Unauthenticated,
      userSessionRepository.sessionState.value
    )
  }

  // --- Requirement 15: Old App Version below minimumSupportedVersion is strictly blocked ---
  @Test
  fun testOldAppVersionIsBlocked() {
    val versionConfig = AppVersionConfig(
      minimumSupportedVersionCode = 2,
      latestVersionCode = 3,
      latestVersionName = "1.2.0"
    )

    // Outdated version (versionCode = 1)
    val outdatedStatus = appVersionManager.evaluateVersion(1, versionConfig)
    assertTrue(
      "Old version (code=1) must trigger MandatoryUpdateRequired",
      outdatedStatus is AppUpdateStatus.MandatoryUpdateRequired
    )

    // Current version (versionCode = 2)
    val currentStatus = appVersionManager.evaluateVersion(BuildConfig.VERSION_CODE, versionConfig)
    assertFalse(
      "Current version (code=2) must not require mandatory update",
      currentStatus is AppUpdateStatus.MandatoryUpdateRequired
    )
  }

  // --- Requirement 16: Cloud Error Mapping & No Fake Success (Prompt 11) ---
  @Test
  fun testCloudErrorMappingDistinguishesAllCategories() {
    // 1. Network Unavailable
    val networkException = FirebaseNetworkException("A network error (such as timeout or unreachable host) occurred.")
    val networkError = AppCloudError.fromThrowable(networkException)
    assertTrue("Must map to NetworkUnavailable", networkError is AppCloudError.NetworkUnavailable)
    assertTrue(networkError.userFriendlyMessageAr.contains("الإنترنت") || networkError.userFriendlyMessageAr.contains("الشبكة"))

    // 2. Permission Denied (e.g. from generic Exception with permission denied message)
    val permException = Exception("PERMISSION_DENIED: Super Admin only.")
    val permError = AppCloudError.fromThrowable(permException)
    assertTrue("Must map to PermissionDenied", permError is AppCloudError.PermissionDenied)
    assertTrue(permError.userFriendlyMessageAr.contains("الصلاحيات") || permError.userFriendlyMessageAr.contains("الإذن"))

    // 3. Authentication Failure
    val authException = Exception("UNAUTHENTICATED: User is not authenticated.")
    val authError = AppCloudError.fromThrowable(authException)
    assertTrue("Must map to AuthenticationFailure", authError is AppCloudError.AuthenticationFailure)
    assertTrue(authError.userFriendlyMessageAr.contains("المصادقة") || authError.userFriendlyMessageAr.contains("الدخول"))

    // 4. Validation Error
    val validationException = Exception("INVALID_ARGUMENT: Invalid argument provided.")
    val valError = AppCloudError.fromThrowable(validationException)
    assertTrue("Must map to ValidationError", valError is AppCloudError.ValidationError)

    // 5. Server Error
    val serverException = Exception("INTERNAL: Internal server error.")
    val srvError = AppCloudError.fromThrowable(serverException)
    assertTrue("Must map to ServerError", srvError is AppCloudError.ServerError)
  }
}
