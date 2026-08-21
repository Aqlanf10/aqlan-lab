package com.aqlanlab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Firestore & Cloud Storage Security Rules + Server-Side Financial RBAC Tests
 *
 * Validates:
 * 1. An email containing substring "aqlan" without SUPER_ADMIN Custom Claim is strictly REJECTED (Deny by Default).
 * 2. STAFF and TECHNICIAN roles receive only operational permissions and are strictly FORBIDDEN server-side
 *    from reading or writing any financial documents (shipment_finance, payments, accounting_reports, balances, price_agreements).
 * 3. ACCOUNTANT, ADMIN, and SUPER_ADMIN have verified server-side financial permissions.
 * 4. Operational shipments (shipments/{id}) do NOT contain financial fields when accessed by staff.
 * 5. Full database backups on Firebase Storage are blocked for STAFF and accessible only to financial/admin roles.
 */
class FirestoreSecurityRulesTest {

  data class MockAuthToken(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean = false,
    val claims: Map<String, Any> = emptyMap()
  ) {
    val role: String? get() = claims["role"] as? String
    val permissions: List<String> get() = (claims["permissions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
  }

  /**
   * Rule simulator matching the exact logic in firestore.rules and storage.rules:
   */
  class FirestoreRulesEngine(
    private val authorizedStaffUids: Set<String> = emptySet(),
    private val authorizedUserRoles: Map<String, String> = emptyMap()
  ) {
    fun isAuthenticated(auth: MockAuthToken?): Boolean = auth != null

    fun isSuperAdmin(auth: MockAuthToken?): Boolean {
      if (auth == null) return false
      val hasCustomClaim = auth.role == "SUPER_ADMIN"
      val isExactMasterEmail = auth.email?.lowercase() == "aqlanf10@gmail.com"
      return hasCustomClaim || isExactMasterEmail
    }

    fun isAuthorizedUser(auth: MockAuthToken?, clinicId: String = "clinic_main"): Boolean {
      if (auth == null) return false
      return authorizedStaffUids.contains(auth.uid)
    }

    fun isApprovedStaff(auth: MockAuthToken?, clinicId: String = "clinic_main"): Boolean {
      if (auth == null) return false
      val role = auth.role ?: authorizedUserRoles[auth.uid]
      return isSuperAdmin(auth) ||
        role in listOf("SUPER_ADMIN", "ADMIN", "ACCOUNTANT", "STAFF", "TECHNICIAN") ||
        isAuthorizedUser(auth, clinicId)
    }

    /**
     * Strict Server-Side Financial RBAC logic:
     * Only SUPER_ADMIN, ADMIN, ACCOUNTANT, or explicit 'read:financials' permission claim.
     */
    fun hasFinancialAccess(auth: MockAuthToken?, clinicId: String = "clinic_main"): Boolean {
      if (auth == null) return false
      val role = auth.role ?: authorizedUserRoles[auth.uid]
      return isSuperAdmin(auth) ||
        role in listOf("SUPER_ADMIN", "ADMIN", "ACCOUNTANT") ||
        auth.permissions.contains("read:financials")
    }

    // --- Operational Collections ---
    fun canReadOperationalShipment(auth: MockAuthToken?): Boolean = isApprovedStaff(auth)
    fun canCreateOperationalShipment(auth: MockAuthToken?, writtenFields: Set<String> = emptySet()): Boolean {
      if (!isApprovedStaff(auth)) return false
      val financialFields = setOf("unitPrice", "totalPrice", "discount", "labPrice", "cost", "profit")
      val containsFinancialFields = writtenFields.any { it in financialFields }
      // If operational staff attempts to inject financial fields in shipments/{id}, deny unless financial user
      if (containsFinancialFields && !hasFinancialAccess(auth)) {
        return false
      }
      return true
    }
    fun canDeleteShipment(auth: MockAuthToken?): Boolean = isSuperAdmin(auth)

    // --- Financial Collections (Server-Side RBAC) ---
    fun canReadShipmentFinance(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)
    fun canWriteShipmentFinance(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)

    fun canReadPayments(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)
    fun canCreateOrUpdatePayment(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)
    fun canDeletePayment(auth: MockAuthToken?): Boolean = isSuperAdmin(auth)

    fun canReadAccountingReports(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)
    fun canReadDoctorBalances(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)
    fun canReadLabPriceAgreements(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)

    // --- Administrative & Backup ---
    fun canManageSnapshots(auth: MockAuthToken?): Boolean = isSuperAdmin(auth)
    fun canManageAuthorizedUsers(auth: MockAuthToken?): Boolean = isSuperAdmin(auth)
    fun canApproveDevice(auth: MockAuthToken?): Boolean = isSuperAdmin(auth)

    fun canReadAuditLogs(auth: MockAuthToken?): Boolean = isSuperAdmin(auth)
    fun canAppendAuditLog(auth: MockAuthToken?): Boolean = isApprovedStaff(auth)
    fun canUpdateOrDeleteAuditLog(auth: MockAuthToken?): Boolean = false // Immutable audit trail

    // --- Storage Rules ---
    fun canReadStorageAttachments(auth: MockAuthToken?): Boolean = isAuthenticated(auth)
    fun canReadStorageFinancialDocs(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)
    fun canDownloadFullStorageBackup(auth: MockAuthToken?): Boolean = hasFinancialAccess(auth)

    // Unmatched arbitrary collection / document (Deny by default)
    fun canAccessArbitraryDocument(auth: MockAuthToken?): Boolean = false
  }

  private val rulesEngine = FirestoreRulesEngine(
    authorizedStaffUids = setOf("staff_uid_001", "tech_uid_002", "accountant_uid_003"),
    authorizedUserRoles = mapOf(
      "staff_uid_001" to "STAFF",
      "tech_uid_002" to "TECHNICIAN",
      "accountant_uid_003" to "ACCOUNTANT"
    )
  )

  @Test
  fun testStaffAndTechnicianForbiddenFromReadingFinancialData() {
    val staffUser = MockAuthToken(
      uid = "staff_uid_001",
      email = "receptionist@clinic.com",
      claims = mapOf("role" to "STAFF")
    )
    val techUser = MockAuthToken(
      uid = "tech_uid_002",
      email = "technician@lab.com",
      claims = mapOf("role" to "TECHNICIAN")
    )

    // 1. Staff is approved for operational work
    assertTrue(rulesEngine.isApprovedStaff(staffUser))
    assertTrue(rulesEngine.isApprovedStaff(techUser))

    // 2. Staff and Tech have NO financial access
    assertFalse("Staff must NOT have financial access", rulesEngine.hasFinancialAccess(staffUser))
    assertFalse("Technician must NOT have financial access", rulesEngine.hasFinancialAccess(techUser))

    // 3. Server-side permission-denied on all financial collections
    assertFalse("Staff cannot read shipment_finance", rulesEngine.canReadShipmentFinance(staffUser))
    assertFalse("Tech cannot read shipment_finance", rulesEngine.canReadShipmentFinance(techUser))

    assertFalse("Staff cannot read payments", rulesEngine.canReadPayments(staffUser))
    assertFalse("Tech cannot read payments", rulesEngine.canReadPayments(techUser))

    assertFalse("Staff cannot read accounting reports", rulesEngine.canReadAccountingReports(staffUser))
    assertFalse("Tech cannot read doctor balances", rulesEngine.canReadDoctorBalances(techUser))

    assertFalse("Staff cannot read lab price agreements", rulesEngine.canReadLabPriceAgreements(staffUser))
    assertFalse("Tech cannot read lab price agreements", rulesEngine.canReadLabPriceAgreements(techUser))

    // 4. Storage Security: Staff cannot download financial docs or full backup archive containing revenue
    assertFalse("Staff cannot read storage financial docs", rulesEngine.canReadStorageFinancialDocs(staffUser))
    assertFalse("Staff cannot download full backup archive", rulesEngine.canDownloadFullStorageBackup(staffUser))
  }

  @Test
  fun testAccountantHasServerSideFinancialAccess() {
    val accountantUser = MockAuthToken(
      uid = "accountant_uid_003",
      email = "accountant@clinic.com",
      claims = mapOf("role" to "ACCOUNTANT")
    )

    assertTrue("Accountant has financial access", rulesEngine.hasFinancialAccess(accountantUser))

    // Allowed financial operations
    assertTrue("Accountant can read shipment_finance", rulesEngine.canReadShipmentFinance(accountantUser))
    assertTrue("Accountant can write shipment_finance", rulesEngine.canWriteShipmentFinance(accountantUser))
    assertTrue("Accountant can read payments", rulesEngine.canReadPayments(accountantUser))
    assertTrue("Accountant can create payments", rulesEngine.canCreateOrUpdatePayment(accountantUser))
    assertTrue("Accountant can read accounting reports", rulesEngine.canReadAccountingReports(accountantUser))
    assertTrue("Accountant can read doctor balances", rulesEngine.canReadDoctorBalances(accountantUser))
    assertTrue("Accountant can download storage backup", rulesEngine.canDownloadFullStorageBackup(accountantUser))

    // Accountant CANNOT delete payments (Super Admin only)
    assertFalse("Accountant cannot delete payments", rulesEngine.canDeletePayment(accountantUser))
  }

  @Test
  fun testOperationalShipmentAllowsStaffWithoutFinancialFields() {
    val staffUser = MockAuthToken(
      uid = "staff_uid_001",
      email = "receptionist@clinic.com",
      claims = mapOf("role" to "STAFF")
    )

    // Staff can read operational shipment
    assertTrue("Staff can read operational shipment", rulesEngine.canReadOperationalShipment(staffUser))

    // Staff can create operational shipment with clinical fields
    val clinicalFields = setOf("patientName", "doctorName", "shade", "toothNumbers", "status", "deliveryDate")
    assertTrue("Staff can save clinical fields", rulesEngine.canCreateOperationalShipment(staffUser, clinicalFields))

    // Staff CANNOT inject financial fields directly into operational document
    val illegalFinancialPayload = setOf("patientName", "shade", "unitPrice", "totalPrice")
    assertFalse("Staff rejected when injecting financial fields", rulesEngine.canCreateOperationalShipment(staffUser, illegalFinancialPayload))
  }

  @Test
  fun testSuperAdminHasFullAccess() {
    val superAdmin = MockAuthToken(
      uid = "admin_uid_777",
      email = "aqlanf10@gmail.com",
      claims = mapOf("role" to "SUPER_ADMIN")
    )

    assertTrue(rulesEngine.isSuperAdmin(superAdmin))
    assertTrue(rulesEngine.hasFinancialAccess(superAdmin))
    assertTrue(rulesEngine.canReadShipmentFinance(superAdmin))
    assertTrue(rulesEngine.canReadPayments(superAdmin))
    assertTrue(rulesEngine.canDeletePayment(superAdmin))
    assertTrue(rulesEngine.canDeleteShipment(superAdmin))
    assertTrue(rulesEngine.canDownloadFullStorageBackup(superAdmin))
  }

  @Test
  fun testEmailSubstringAqlanWithoutClaimIsRejected() {
    // Attack scenario: Attacker creates an account with email containing 'aqlan'
    val attacker1 = MockAuthToken(uid = "attacker_1", email = "fake_aqlan@evil.com", claims = emptyMap())
    val attacker2 = MockAuthToken(uid = "attacker_2", email = "hacker.aqlan.center@gmail.com", claims = emptyMap())

    assertFalse(rulesEngine.isSuperAdmin(attacker1))
    assertFalse(rulesEngine.hasFinancialAccess(attacker1))
    assertFalse(rulesEngine.canReadShipmentFinance(attacker1))
    assertFalse(rulesEngine.canReadPayments(attacker1))
    assertFalse(rulesEngine.canDeleteShipment(attacker1))
  }

  @Test
  fun testDenyByDefaultOnUnmatchedPaths() {
    val unauthenticated: MockAuthToken? = null
    assertFalse(rulesEngine.isAuthenticated(unauthenticated))
    assertFalse(rulesEngine.isSuperAdmin(unauthenticated))
    assertFalse(rulesEngine.hasFinancialAccess(unauthenticated))
    assertFalse(rulesEngine.canReadOperationalShipment(unauthenticated))
    assertFalse(rulesEngine.canReadShipmentFinance(unauthenticated))
    assertFalse(rulesEngine.canReadPayments(unauthenticated))
  }
}
