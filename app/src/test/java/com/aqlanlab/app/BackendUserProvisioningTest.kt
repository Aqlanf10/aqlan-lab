package com.aqlanlab.app

import com.aqlanlab.app.data.models.User
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.network.UserProvisioningResult
import org.junit.Assert.*
import org.junit.Test

class BackendUserProvisioningTest {

  data class MockBackendUserRecord(
    val uid: String,
    val email: String,
    val displayName: String,
    val disabled: Boolean
  )

  data class MockCustomClaims(
    val role: String,
    val permissions: List<String>
  )

  data class MockFirestoreUserDoc(
    val uid: String,
    val username: String,
    val fullName: String,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val isApproved: Boolean,
    val permissions: List<String>,
    val maxDevices: Int,
    val createdBy: String
  )

  class MockBackendAdminEngine {
    val authUsers = mutableMapOf<String, MockBackendUserRecord>()
    val customClaims = mutableMapOf<String, MockCustomClaims>()
    val firestoreAuthorizedUsers = mutableMapOf<String, MockFirestoreUserDoc>()
    val auditLogs = mutableListOf<String>()
    var simulateFirestoreFailure: Boolean = false

    fun createAuthorizedUser(
      callerRole: String,
      callerEmail: String,
      username: String,
      email: String,
      temporaryPass: String,
      fullName: String,
      role: String,
      permissions: List<String>,
      maxDevices: Int
    ): Result<UserProvisioningResult> {
      // 1. Verify caller is SUPER_ADMIN
      if (callerRole != "SUPER_ADMIN" && callerEmail != "aqlanf10@gmail.com") {
        return Result.failure(SecurityException("غير مصرح لك بتنفيذ هذه العملية. هذه الصلاحية للمشرف العام فقط."))
      }

      // 2. Validate inputs
      if (!email.contains("@") || temporaryPass.length < 6 || username.length < 3) {
        return Result.failure(IllegalArgumentException("البيانات المدخلة غير صالحة."))
      }

      // Check unique username
      if (firestoreAuthorizedUsers.values.any { it.username.equals(username, ignoreCase = true) }) {
        return Result.failure(IllegalStateException("اسم المستخدم مسجل مسبقاً."))
      }

      // 3. Create Auth User
      val generatedUid = "auth_uid_${System.nanoTime()}"
      val authRecord = MockBackendUserRecord(
        uid = generatedUid,
        email = email.lowercase(),
        displayName = fullName,
        disabled = false
      )
      authUsers[generatedUid] = authRecord

      try {
        // Simulated failure point
        if (simulateFirestoreFailure) {
          throw RuntimeException("Firestore write failed unexpectedly!")
        }

        // Set Custom Claims
        customClaims[generatedUid] = MockCustomClaims(role = role, permissions = permissions)

        // Write Firestore doc
        firestoreAuthorizedUsers[generatedUid] = MockFirestoreUserDoc(
          uid = generatedUid,
          username = username.lowercase(),
          fullName = fullName,
          email = email.lowercase(),
          role = role,
          isActive = true,
          isApproved = true,
          permissions = permissions,
          maxDevices = maxDevices,
          createdBy = callerEmail
        )

        // Write Audit Log
        auditLogs.add("CREATE_USER: $generatedUid by $callerEmail")

        return Result.success(
          UserProvisioningResult(
            success = true,
            uid = generatedUid,
            username = username,
            email = email,
            role = role,
            message = "تم إنشاء المستخدم بنجاح."
          )
        )
      } catch (e: Exception) {
        // ATOMIC ROLLBACK: Auth account is deleted if downstream steps fail
        authUsers.remove(generatedUid)
        customClaims.remove(generatedUid)
        firestoreAuthorizedUsers.remove(generatedUid)
        return Result.failure(e)
      }
    }

    fun setUserActiveStatus(callerRole: String, targetUid: String, isActive: Boolean): Result<String> {
      if (callerRole != "SUPER_ADMIN") {
        return Result.failure(SecurityException("Permission denied"))
      }
      val user = authUsers[targetUid] ?: return Result.failure(NoSuchElementException("User not found"))
      authUsers[targetUid] = user.copy(disabled = !isActive)

      val doc = firestoreAuthorizedUsers[targetUid]
      if (doc != null) {
        firestoreAuthorizedUsers[targetUid] = doc.copy(isActive = isActive)
      }
      auditLogs.add("SET_ACTIVE: $targetUid -> $isActive")
      return Result.success(if (isActive) "تم تفعيل الحساب." else "تم تعطيل الحساب وإنهاء الجلسات.")
    }
  }

  @Test
  fun testSuperAdminCanProvisionUserSuccessfully() {
    val backend = MockBackendAdminEngine()

    val result = backend.createAuthorizedUser(
      callerRole = "SUPER_ADMIN",
      callerEmail = "aqlanf10@gmail.com",
      username = "omar_accountant",
      email = "omar@aqlanlab.com",
      temporaryPass = "Pass@1234",
      fullName = "عمر المحاسب",
      role = "ACCOUNTANT",
      permissions = listOf("read:accounting", "write:accounting"),
      maxDevices = 2
    )

    assertTrue(result.isSuccess)
    val res = result.getOrThrow()
    assertNotNull(res.uid)
    assertTrue(res.uid.startsWith("auth_uid_"))
    assertEquals("omar_accountant", res.username)

    // Verify backend state
    assertTrue(backend.authUsers.containsKey(res.uid))
    assertEquals("ACCOUNTANT", backend.customClaims[res.uid]?.role)
    assertTrue(backend.firestoreAuthorizedUsers.containsKey(res.uid))
    assertEquals(res.uid, backend.firestoreAuthorizedUsers[res.uid]?.uid)
    assertTrue(backend.auditLogs.any { it.contains(res.uid) })
  }

  @Test
  fun testNonSuperAdminIsDeniedFromProvisioning() {
    val backend = MockBackendAdminEngine()

    val result = backend.createAuthorizedUser(
      callerRole = "STAFF", // Unauthorized caller
      callerEmail = "staff@aqlanlab.com",
      username = "new_tech",
      email = "tech@aqlanlab.com",
      temporaryPass = "Pass@1234",
      fullName = "فني جديد",
      role = "TECHNICIAN",
      permissions = emptyList(),
      maxDevices = 1
    )

    assertTrue("Non-SuperAdmin must fail", result.isFailure)
    assertTrue(result.exceptionOrNull() is SecurityException)
    assertEquals(0, backend.authUsers.size)
    assertEquals(0, backend.firestoreAuthorizedUsers.size)
  }

  @Test
  fun testAtomicRollbackOnPartialFailureDoesNotLeaveOrphanAuthUsers() {
    val backend = MockBackendAdminEngine()
    backend.simulateFirestoreFailure = true // Simulate server-side failure during Firestore write

    val result = backend.createAuthorizedUser(
      callerRole = "SUPER_ADMIN",
      callerEmail = "aqlanf10@gmail.com",
      username = "fail_test_user",
      email = "fail@aqlanlab.com",
      temporaryPass = "Pass@1234",
      fullName = "مستخدم فاشل",
      role = "STAFF",
      permissions = emptyList(),
      maxDevices = 1
    )

    // Result MUST be Failure, NEVER fake success
    assertTrue("Must report failure", result.isFailure)
    assertFalse(result.isSuccess)

    // Invariant: Auth user was rolled back / deleted, no orphan records
    assertEquals("Auth users must be empty after rollback", 0, backend.authUsers.size)
    assertEquals("Custom claims must be empty", 0, backend.customClaims.size)
    assertEquals("Firestore docs must be empty", 0, backend.firestoreAuthorizedUsers.size)
  }

  @Test
  fun testDisablingUserDisablesAuthAndUpdatesStatus() {
    val backend = MockBackendAdminEngine()

    val provRes = backend.createAuthorizedUser(
      callerRole = "SUPER_ADMIN",
      callerEmail = "aqlanf10@gmail.com",
      username = "reception_staff",
      email = "reception@aqlanlab.com",
      temporaryPass = "Pass@1234",
      fullName = "موظفة الاستقبال",
      role = "STAFF",
      permissions = listOf("read:shipments"),
      maxDevices = 2
    ).getOrThrow()

    val uid = provRes.uid
    assertFalse(backend.authUsers[uid]!!.disabled)
    assertTrue(backend.firestoreAuthorizedUsers[uid]!!.isActive)

    // Super Admin disables user
    val disableResult = backend.setUserActiveStatus("SUPER_ADMIN", uid, isActive = false)
    assertTrue(disableResult.isSuccess)

    assertTrue(backend.authUsers[uid]!!.disabled)
    assertFalse(backend.firestoreAuthorizedUsers[uid]!!.isActive)
  }
}
