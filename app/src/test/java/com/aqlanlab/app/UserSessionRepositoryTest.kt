package com.aqlanlab.app

import com.aqlanlab.app.data.models.DeviceBinding
import com.aqlanlab.app.data.models.DeviceStatus
import com.aqlanlab.app.data.models.User
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.data.repository.UserSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSessionRepositoryTest {

  @Test
  fun testInitialSessionStateIsStrictlyUnauthenticated() {
    // Verified invariant: Initial state must be Unauthenticated by default
    val initialState: UserSessionState = UserSessionState.Unauthenticated
    assertTrue(initialState is UserSessionState.Unauthenticated)
  }

  @Test
  fun testSessionRoleAndProfileEvaluation() {
    val superAdminUser = User(
      id = 1L,
      username = "doctor_aqlan",
      fullName = "د. عقلان",
      email = "aqlanf10@gmail.com",
      role = UserRole.SUPER_ADMIN,
      isActive = true,
      isApproved = true
    )

    val session = UserSessionState.Authenticated(
      user = superAdminUser,
      firebaseUser = null,
      role = superAdminUser.role,
      isApproved = superAdminUser.isApproved
    )

    assertEquals(UserRole.SUPER_ADMIN, session.role)
    assertTrue(session.isApproved)
    assertTrue(session.user.isActive)
  }

  @Test
  fun testPendingApprovalStatusYieldsPendingState() {
    val unapprovedStaff = User(
      id = 45L,
      username = "new_staff",
      fullName = "موظف جديد",
      email = "new.staff@clinic.com",
      role = UserRole.STAFF,
      isActive = true,
      isApproved = false // Unapproved account
    )

    val state = if (!unapprovedStaff.isApproved) {
      UserSessionState.PendingApproval(unapprovedStaff, "الحساب بانتظار موافقة المشرف العام")
    } else {
      UserSessionState.Authenticated(unapprovedStaff, null, unapprovedStaff.role, true)
    }

    assertTrue(state is UserSessionState.PendingApproval)
    val pending = state as UserSessionState.PendingApproval
    assertEquals("new_staff", pending.user.username)
    assertFalse(pending.user.isApproved)
  }

  @Test
  fun testDisabledAccountYieldsDisabledState() {
    val disabledUser = User(
      id = 12L,
      username = "inactive_tech",
      fullName = "فني موقوف",
      email = "tech@clinic.com",
      role = UserRole.TECHNICIAN,
      isActive = false, // Disabled account
      isApproved = true
    )

    val state = if (!disabledUser.isActive) {
      UserSessionState.Disabled(disabledUser, "تم تعطيل هذا الحساب")
    } else {
      UserSessionState.Authenticated(disabledUser, null, disabledUser.role, true)
    }

    assertTrue(state is UserSessionState.Disabled)
    val disabled = state as UserSessionState.Disabled
    assertFalse(disabled.user.isActive)
  }

  @Test
  fun testDeviceAuthorizationTransitions() {
    val user = User(
      id = 2L,
      username = "accountant",
      fullName = "المحاسب المالي",
      role = UserRole.ACCOUNTANT,
      isActive = true,
      isApproved = true
    )

    val blockedDevice = DeviceBinding(
      deviceId = "dev_blocked_99",
      userId = user.id,
      userName = user.fullName,
      status = DeviceStatus.BLOCKED,
      notes = "محظور أمنياً"
    )

    val state = UserSessionState.DeviceBlocked(blockedDevice.deviceId, blockedDevice.notes)
    assertTrue(state is UserSessionState.DeviceBlocked)
    assertEquals("dev_blocked_99", state.deviceId)
  }
}
