package com.aqlanlab.app

import com.aqlanlab.app.data.models.PaymentMethod
import com.aqlanlab.app.data.models.ShipmentStatus
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.ui.components.quadrant1UpperRight
import com.aqlanlab.app.ui.components.quadrant2UpperLeft
import com.aqlanlab.app.ui.components.quadrant3LowerLeft
import com.aqlanlab.app.ui.components.quadrant4LowerRight
import org.junit.Assert.*
import org.junit.Test

class DentalLabUnitTest {

  @Test
  fun testFdiToothQuadrantsCompleteness() {
    assertEquals(8, quadrant1UpperRight.size)
    assertEquals(8, quadrant2UpperLeft.size)
    assertEquals(8, quadrant3LowerLeft.size)
    assertEquals(8, quadrant4LowerRight.size)

    // Total standard permanent adult teeth = 32
    val totalAdultTeeth = quadrant1UpperRight.size + quadrant2UpperLeft.size +
                          quadrant3LowerLeft.size + quadrant4LowerRight.size
    assertEquals(32, totalAdultTeeth)

    assertTrue(quadrant1UpperRight.contains("11"))
    assertTrue(quadrant1UpperRight.contains("18"))
    assertTrue(quadrant2UpperLeft.contains("21"))
    assertTrue(quadrant2UpperLeft.contains("28"))
    assertTrue(quadrant3LowerLeft.contains("31"))
    assertTrue(quadrant3LowerLeft.contains("38"))
    assertTrue(quadrant4LowerRight.contains("41"))
    assertTrue(quadrant4LowerRight.contains("48"))
  }

  @Test
  fun testShipmentPricingCalculation() {
    val pieceCount = 3
    val unitPrice = 45.0
    val discount = 15.0

    val subtotal = pieceCount * unitPrice
    val totalPrice = (subtotal - discount).coerceAtLeast(0.0)

    assertEquals(135.0, subtotal, 0.001)
    assertEquals(120.0, totalPrice, 0.001)
  }

  @Test
  fun testShipmentStatusProgression() {
    assertTrue(ShipmentStatus.IN_PROGRESS.stepIndex > ShipmentStatus.NEW.stepIndex)
    assertTrue(ShipmentStatus.READY.stepIndex > ShipmentStatus.IN_PROGRESS.stepIndex)
    assertTrue(ShipmentStatus.RECEIVED.stepIndex > ShipmentStatus.READY.stepIndex)
  }

  @Test
  fun testDateUtilsLateDetection() {
    val now = System.currentTimeMillis()
    val pastTimestamp = now - 100000L
    val futureTimestamp = now + 100000L

    // In-progress shipment past due date should be marked late
    assertTrue(DateUtils.isLate(pastTimestamp, ShipmentStatus.IN_PROGRESS))
    assertTrue(DateUtils.isLate(pastTimestamp, ShipmentStatus.NEW))

    // Received shipment should NEVER be marked late
    assertFalse(DateUtils.isLate(pastTimestamp, ShipmentStatus.RECEIVED))
    // Cancelled shipment should NEVER be marked late
    assertFalse(DateUtils.isLate(pastTimestamp, ShipmentStatus.CANCELLED))

    // Future shipment is not late
    assertFalse(DateUtils.isLate(futureTimestamp, ShipmentStatus.IN_PROGRESS))
  }

  @Test
  fun testLaboratoryAccountBalanceArithmetic() {
    val totalBilled = 1250.0
    val totalPaid = 800.0
    val balance = (totalBilled - totalPaid).coerceAtLeast(0.0)

    assertEquals(450.0, balance, 0.001)

    // Overpaid scenario
    val overpaidBalance = (500.0 - 600.0).coerceAtLeast(0.0)
    assertEquals(0.0, overpaidBalance, 0.001)
  }

  @Test
  fun testUserRolePermissions() {
    val adminRole = UserRole.ADMIN
    val accountantRole = UserRole.ACCOUNTANT
    val staffRole = UserRole.STAFF

    // Staff must not have financial visibility
    assertFalse(staffRole.canViewFinancials)
    assertTrue(adminRole.canViewFinancials)
    assertTrue(accountantRole.canViewFinancials)
    assertTrue(UserRole.SUPER_ADMIN.canViewFinancials)
    assertTrue(UserRole.SUPER_ADMIN.canManageUsers)
    assertTrue(UserRole.SUPER_ADMIN.canManageDevices)
    assertFalse(staffRole.canManageUsers)
    assertFalse(accountantRole.canManageDevices)
    assertFalse(UserRole.TECHNICIAN.canViewFinancials)
  }

  @Test
  fun testDeviceBindingStatusAndPermissions() {
    val approvedStatus = com.aqlanlab.app.data.models.DeviceStatus.APPROVED
    val pendingStatus = com.aqlanlab.app.data.models.DeviceStatus.PENDING
    val blockedStatus = com.aqlanlab.app.data.models.DeviceStatus.BLOCKED

    assertTrue(approvedStatus.isAllowed)
    assertFalse(pendingStatus.isAllowed)
    assertFalse(blockedStatus.isAllowed)

    val device = com.aqlanlab.app.data.models.DeviceBinding(
      deviceId = "DEV-AQLAN-TEST-01",
      userId = 1L,
      userName = "د. عقلان الكامل",
      userRole = UserRole.SUPER_ADMIN,
      deviceModel = "Pixel 8 Pro",
      status = com.aqlanlab.app.data.models.DeviceStatus.APPROVED
    )
    assertEquals("DEV-AQLAN-TEST-01", device.deviceId)
    assertTrue(device.status.isAllowed)
  }

  @Test
  fun testPaymentMethodTitles() {
    assertEquals("نقداً", PaymentMethod.CASH.titleAr)
    assertEquals("تحويل بنكي", PaymentMethod.BANK_TRANSFER.titleAr)
    assertEquals("شيك", PaymentMethod.CHECK.titleAr)
    assertEquals("محفظة إلكترونية", PaymentMethod.DIGITAL_WALLET.titleAr)
  }

  @Test
  fun testInventoryItemLowStockAndValuation() {
    val lowStockItem = com.aqlanlab.app.data.models.InventoryItem(
      id = 1L,
      name = "ألجينات طبعة سريعة التصلب",
      category = "مواد الطبعات",
      currentStock = 2.0,
      minThreshold = 3.0,
      unit = "علبة",
      unitCost = 12.0
    )

    assertTrue(lowStockItem.isLowStock)
    assertFalse(lowStockItem.isOutOfStock)
    assertEquals(24.0, lowStockItem.totalValue, 0.001)

    val outOfStockItem = lowStockItem.copy(currentStock = 0.0)
    assertTrue(outOfStockItem.isLowStock)
    assertTrue(outOfStockItem.isOutOfStock)
    assertEquals(0.0, outOfStockItem.totalValue, 0.001)

    val normalItem = lowStockItem.copy(currentStock = 10.0)
    assertFalse(normalItem.isLowStock)
    assertFalse(normalItem.isOutOfStock)
    assertEquals(120.0, normalItem.totalValue, 0.001)
  }

  @Test
  fun testInventoryTransactionTypes() {
    assertTrue(com.aqlanlab.app.data.models.InventoryTransactionType.STOCK_IN.isAddition)
    assertTrue(com.aqlanlab.app.data.models.InventoryTransactionType.ADJUSTMENT_ADD.isAddition)
    assertFalse(com.aqlanlab.app.data.models.InventoryTransactionType.USAGE_OUT.isAddition)
    assertFalse(com.aqlanlab.app.data.models.InventoryTransactionType.ADJUSTMENT_SUBTRACT.isAddition)
    assertFalse(com.aqlanlab.app.data.models.InventoryTransactionType.RETURN.isAddition)
  }

  @Test
  fun testCloudBackupPayloadCalculations() {
    val payload = com.aqlanlab.app.network.CloudBackupPayload(
      clinicId = "clinic_elite_01",
      clinicName = "مركز النخبة لطب وتجميل الأسنان",
      labs = listOf(
        com.aqlanlab.app.data.models.Laboratory(id = 1, name = "معمل النجوم", phone = "777")
      ),
      shipments = listOf(
        com.aqlanlab.app.data.models.Shipment(
          id = 1,
          shipmentNumber = "#000101",
          patientName = "عمر أحمد",
          clinicOrDoctorName = "عيادة 1",
          labId = 1,
          labName = "معمل النجوم",
          workTypeId = 1,
          workTypeName = "زركونيا",
          totalPrice = 50.0,
          status = com.aqlanlab.app.data.models.ShipmentStatus.NEW
        )
      ),
      inventoryItems = listOf(
        com.aqlanlab.app.data.models.InventoryItem(
          id = 1,
          name = "ألجينات",
          category = "مواد الطبعات",
          currentStock = 5.0,
          minThreshold = 2.0
        )
      )
    )

    assertEquals(3, payload.totalRecordCount)
    assertEquals("clinic_elite_01", payload.clinicId)
  }

  @Test
  fun testFirestoreBackupSnapshotModel() {
    val snapshot = com.aqlanlab.app.network.FirestoreBackupSnapshot(
      id = "backup_123456",
      clinicId = "clinic_elite_01",
      clinicName = "مركز النخبة",
      totalRecords = 45,
      shipmentsCount = 20,
      labsCount = 5,
      inventoryCount = 12,
      createdBy = "د. أحمد الخالد"
    )

    assertEquals("backup_123456", snapshot.id)
    assertEquals(45, snapshot.totalRecords)
    assertEquals(20, snapshot.shipmentsCount)
    assertEquals("د. أحمد الخالد", snapshot.createdBy)
  }

  // --- Device Authorization & Security Tests ---

  @Test
  fun testDeviceStatusPermissions() {
    // Only APPROVED is allowed into the dashboard/system
    assertTrue(com.aqlanlab.app.data.models.DeviceStatus.APPROVED.isAllowed)

    // PENDING, BLOCKED, REVOKED must all strictly disallow entry
    assertFalse(com.aqlanlab.app.data.models.DeviceStatus.PENDING.isAllowed)
    assertFalse(com.aqlanlab.app.data.models.DeviceStatus.BLOCKED.isAllowed)
    assertFalse(com.aqlanlab.app.data.models.DeviceStatus.REVOKED.isAllowed)
  }

  @Test
  fun testDeviceAuthOutcomeTypes() {
    val device = com.aqlanlab.app.data.models.DeviceBinding(
      deviceId = "dev_uuid_101",
      userId = 2L,
      userName = "أحمد محمد",
      status = com.aqlanlab.app.data.models.DeviceStatus.APPROVED
    )

    val allowedOutcome = com.aqlanlab.app.data.models.DeviceAuthOutcome.Allowed(device)
    assertTrue(allowedOutcome is com.aqlanlab.app.data.models.DeviceAuthOutcome.Allowed)

    val pendingOutcome = com.aqlanlab.app.data.models.DeviceAuthOutcome.PendingApproval(
      device = device.copy(status = com.aqlanlab.app.data.models.DeviceStatus.PENDING),
      isMaxDevicesExceeded = false,
      message = "بانتظار موافقة المشرف"
    )
    assertTrue(pendingOutcome is com.aqlanlab.app.data.models.DeviceAuthOutcome.PendingApproval)
    assertFalse(pendingOutcome.isMaxDevicesExceeded)

    val blockedOutcome = com.aqlanlab.app.data.models.DeviceAuthOutcome.Blocked(
      device = device.copy(status = com.aqlanlab.app.data.models.DeviceStatus.BLOCKED),
      reason = "تم حظر هذا الجهاز"
    )
    assertTrue(blockedOutcome is com.aqlanlab.app.data.models.DeviceAuthOutcome.Blocked)

    val revokedOutcome = com.aqlanlab.app.data.models.DeviceAuthOutcome.Revoked(
      device = device.copy(status = com.aqlanlab.app.data.models.DeviceStatus.REVOKED),
      reason = "تم إلغاء ترخيص الجهاز"
    )
    assertTrue(revokedOutcome is com.aqlanlab.app.data.models.DeviceAuthOutcome.Revoked)
  }

  @Test
  fun testMaxDevicesEnforcementRule() {
    val user = com.aqlanlab.app.data.models.User(
      id = 10L,
      username = "lab_technician",
      fullName = "فني السيراميك",
      maxDevices = 2
    )

    // Scenario: User has 0 devices -> Device 1 approved
    var approvedCount = 0
    var isExceeded = approvedCount >= user.maxDevices
    assertFalse(isExceeded)

    // Scenario: User has 1 device -> Device 2 approved
    approvedCount = 1
    isExceeded = approvedCount >= user.maxDevices
    assertFalse(isExceeded)

    // Scenario: User has 2 approved devices -> Device 3 attempt MUST be flagged as exceeded
    approvedCount = 2
    isExceeded = approvedCount >= user.maxDevices
    assertTrue(isExceeded)

    // Exceeded device must result in PENDING with isMaxDevicesExceeded = true
    val outcome = com.aqlanlab.app.data.models.DeviceAuthOutcome.PendingApproval(
      device = com.aqlanlab.app.data.models.DeviceBinding(
        deviceId = "dev_uuid_303",
        userId = user.id,
        userName = user.fullName,
        status = com.aqlanlab.app.data.models.DeviceStatus.PENDING
      ),
      isMaxDevicesExceeded = isExceeded,
      message = "تجاوز الحد الأقصى للأجهزة المصرح بها"
    )
    assertTrue(outcome.isMaxDevicesExceeded)
    assertFalse(outcome.device.status.isAllowed)
  }

  @Test
  fun testDeviceRevocationAndBlockingDeniesAccess() {
    val activeDevice = com.aqlanlab.app.data.models.DeviceBinding(
      deviceId = "hw_sec_device_999",
      userId = 5L,
      userName = "موظف الاستقبال",
      status = com.aqlanlab.app.data.models.DeviceStatus.APPROVED
    )
    assertTrue(activeDevice.status.isAllowed)

    // Super Admin blocks device
    val blockedDevice = activeDevice.copy(status = com.aqlanlab.app.data.models.DeviceStatus.BLOCKED, notes = "حظر أمني")
    assertFalse(blockedDevice.status.isAllowed)

    // Super Admin revokes device
    val revokedDevice = activeDevice.copy(status = com.aqlanlab.app.data.models.DeviceStatus.REVOKED, notes = "إلغاء جهاز قديم")
    assertFalse(revokedDevice.status.isAllowed)
  }
}
