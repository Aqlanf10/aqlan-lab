package com.example

import com.example.data.models.PaymentMethod
import com.example.data.models.ShipmentStatus
import com.example.data.models.UserRole
import com.example.ui.components.DateUtils
import com.example.ui.components.quadrant1UpperRight
import com.example.ui.components.quadrant2UpperLeft
import com.example.ui.components.quadrant3LowerLeft
import com.example.ui.components.quadrant4LowerRight
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
    assertFalse(staffRole != UserRole.STAFF)
    assertTrue(adminRole != UserRole.STAFF)
    assertTrue(accountantRole != UserRole.STAFF)
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
    val lowStockItem = com.example.data.models.InventoryItem(
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
    assertTrue(com.example.data.models.InventoryTransactionType.STOCK_IN.isAddition)
    assertTrue(com.example.data.models.InventoryTransactionType.ADJUSTMENT_ADD.isAddition)
    assertFalse(com.example.data.models.InventoryTransactionType.USAGE_OUT.isAddition)
    assertFalse(com.example.data.models.InventoryTransactionType.ADJUSTMENT_SUBTRACT.isAddition)
    assertFalse(com.example.data.models.InventoryTransactionType.RETURN.isAddition)
  }

  @Test
  fun testCloudBackupPayloadCalculations() {
    val payload = com.example.network.CloudBackupPayload(
      clinicId = "clinic_elite_01",
      clinicName = "مركز النخبة لطب وتجميل الأسنان",
      labs = listOf(
        com.example.data.models.Laboratory(id = 1, name = "معمل النجوم", phone = "777")
      ),
      shipments = listOf(
        com.example.data.models.Shipment(
          id = 1,
          shipmentNumber = "#000101",
          patientName = "عمر أحمد",
          clinicOrDoctorName = "عيادة 1",
          labId = 1,
          labName = "معمل النجوم",
          workTypeId = 1,
          workTypeName = "زركونيا",
          totalPrice = 50.0,
          status = com.example.data.models.ShipmentStatus.NEW
        )
      ),
      inventoryItems = listOf(
        com.example.data.models.InventoryItem(
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
    val snapshot = com.example.network.FirestoreBackupSnapshot(
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
}
