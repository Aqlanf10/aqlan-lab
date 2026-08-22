package com.aqlanlab.app.data.repository

import com.aqlanlab.app.data.AppDatabase
import com.aqlanlab.app.data.DatabaseSeedData
import com.aqlanlab.app.data.models.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DentalLabRepository(private val database: AppDatabase) {
  private val labDao = database.labDao()
  private val workTypeDao = database.workTypeDao()
  private val labPriceDao = database.labPriceDao()
  private val shipmentDao = database.shipmentDao()
  private val paymentDao = database.paymentDao()
  private val auditLogDao = database.auditLogDao()
  private val userDao = database.userDao()
  private val settingDao = database.settingDao()
  private val inventoryDao = database.inventoryDao()
  private val deviceBindingDao = database.deviceBindingDao()

  val allLabs: Flow<List<Laboratory>> = labDao.getAllLabs()
  val activeLabs: Flow<List<Laboratory>> = labDao.getActiveLabs()

  val allWorkTypes: Flow<List<WorkType>> = workTypeDao.getAllWorkTypes()
  val activeWorkTypes: Flow<List<WorkType>> = workTypeDao.getActiveWorkTypes()

  val allLabPrices: Flow<List<LabPrice>> = labPriceDao.getAllPrices()
  val allShipments: Flow<List<Shipment>> = shipmentDao.getAllShipments()
  val allPayments: Flow<List<Payment>> = paymentDao.getAllPayments()
  val recentAuditLogs: Flow<List<AuditLog>> = auditLogDao.getRecentLogs()
  val allUsers: Flow<List<User>> = userDao.getAllUsers()
  val allSettings: Flow<List<AppSetting>> = settingDao.getAllSettings()
  val allDevices: Flow<List<DeviceBinding>> = deviceBindingDao.getAllDevices()

  val allInventoryItems: Flow<List<InventoryItem>> = inventoryDao.getAllItems()
  val lowStockInventoryItems: Flow<List<InventoryItem>> = inventoryDao.getLowStockItems()
  val allInventoryTransactions: Flow<List<InventoryTransaction>> = inventoryDao.getAllTransactions()

  suspend fun checkAndSeedInitialData() {
    val count = shipmentDao.getShipmentCount()
    if (count == 0) {
      userDao.insertAll(DatabaseSeedData.defaultUsers)
      labDao.insertAll(DatabaseSeedData.defaultLabs)
      workTypeDao.insertAll(DatabaseSeedData.defaultWorkTypes)
      labPriceDao.insertAll(DatabaseSeedData.defaultLabPrices)
      shipmentDao.insertAll(DatabaseSeedData.defaultShipments)
      paymentDao.insertAll(DatabaseSeedData.defaultPayments)
      auditLogDao.insertAll(DatabaseSeedData.defaultAuditLogs)
      deviceBindingDao.insertAll(DatabaseSeedData.defaultDevices)
      inventoryDao.insertAll(DatabaseSeedData.defaultInventoryItems)
      inventoryDao.insertAllTransactions(DatabaseSeedData.defaultInventoryTransactions)
      for (setting in DatabaseSeedData.defaultSettings) {
        settingDao.setSetting(setting)
      }
    }
  }

  suspend fun resetToDefaultDemoData() {
    shipmentDao.deleteAllShipments()
    paymentDao.deleteAllPayments()
    auditLogDao.deleteAllAuditLogs()
    labPriceDao.deleteAllPrices()
    labDao.deleteAllLabs()
    workTypeDao.deleteAllWorkTypes()
    inventoryDao.deleteAllItems()
    inventoryDao.deleteAllTransactions()

    userDao.insertAll(DatabaseSeedData.defaultUsers)
    labDao.insertAll(DatabaseSeedData.defaultLabs)
    workTypeDao.insertAll(DatabaseSeedData.defaultWorkTypes)
    labPriceDao.insertAll(DatabaseSeedData.defaultLabPrices)
    shipmentDao.insertAll(DatabaseSeedData.defaultShipments)
    paymentDao.insertAll(DatabaseSeedData.defaultPayments)
    auditLogDao.insertAll(DatabaseSeedData.defaultAuditLogs)
    inventoryDao.insertAll(DatabaseSeedData.defaultInventoryItems)
    inventoryDao.insertAllTransactions(DatabaseSeedData.defaultInventoryTransactions)
  }

  suspend fun wipeAllTransactionsOnly(currentUser: User) {
    shipmentDao.deleteAllShipments()
    paymentDao.deleteAllPayments()
    auditLogDao.deleteAllAuditLogs()
    inventoryDao.deleteAllTransactions()

    logAudit(
      user = currentUser,
      action = AuditActionType.DELETE_SHIPMENT,
      description = "تم تصفير وحذف جميع الإرساليات والمدفوعات لبدء سجلات جديدة",
      entityType = "System"
    )
  }

  suspend fun factoryResetAll(currentUser: User) {
    shipmentDao.deleteAllShipments()
    paymentDao.deleteAllPayments()
    auditLogDao.deleteAllAuditLogs()
    labPriceDao.deleteAllPrices()
    labDao.deleteAllLabs()
    workTypeDao.deleteAllWorkTypes()

    // Ensure default admin user and essential default work types exist
    val defaultAdmin = User(
      id = 1,
      username = "admin",
      fullName = "المدير العام",
      role = UserRole.ADMIN,
      pinCode = "", // No default PIN
      avatarColor = 0xFF00687A
    )
    userDao.insert(defaultAdmin)
    workTypeDao.insertAll(DatabaseSeedData.defaultWorkTypes)

    logAudit(
      user = currentUser,
      action = AuditActionType.DELETE_SHIPMENT,
      description = "تم إجراء تصفير كامل لجميع سجلات التطبيق (Factory Reset)",
      entityType = "System"
    )
  }

  // --- Users Management ---
  suspend fun allUsersSync(): List<User> = userDao.getAllSync()

  suspend fun insertUser(user: User, currentUser: User): Long {
    val id = userDao.insert(user)
    logAudit(
      user = currentUser,
      action = AuditActionType.SWITCH_USER,
      description = "إضافة مستخدم جديد: ${user.fullName} (${user.role.titleAr})",
      entityId = id,
      entityType = "User"
    )
    return id
  }

  suspend fun updateUser(user: User, currentUser: User) {
    userDao.update(user)
    logAudit(
      user = currentUser,
      action = AuditActionType.SWITCH_USER,
      description = "تحديث بيانات المستخدم: ${user.fullName} (${user.role.titleAr})",
      entityId = user.id,
      entityType = "User"
    )
  }

  suspend fun deleteUser(user: User, currentUser: User) {
    if (user.id == 1L) return // Protect root admin
    userDao.delete(user)
    logAudit(
      user = currentUser,
      action = AuditActionType.SWITCH_USER,
      description = "حذف المستخدم: ${user.fullName}",
      entityId = user.id,
      entityType = "User"
    )
  }

  // --- Smart Pricing Calculation ---
  suspend fun calculatePrice(labId: Long, workTypeId: Long, pieceCount: Int, discount: Double = 0.0): Pair<Double, Double> {
    // 1. Check custom lab price
    val custom = labPriceDao.getPriceForLabAndWork(labId, workTypeId)
    val unitPrice = if (custom != null && custom.customPrice > 0) {
      custom.customPrice
    } else {
      val workType = workTypeDao.getWorkTypeById(workTypeId)
      workType?.defaultPrice ?: 0.0
    }
    val totalBeforeDiscount = unitPrice * pieceCount
    val total = (totalBeforeDiscount - discount).coerceAtLeast(0.0)
    return Pair(unitPrice, total)
  }

  // --- Labs ---
  suspend fun insertLab(lab: Laboratory, currentUser: User): Long {
    val id = labDao.insert(lab)
    logAudit(
      user = currentUser,
      action = AuditActionType.ADD_LAB,
      description = "إضافة معمل جديد: ${lab.name}",
      entityId = id,
      entityType = "Laboratory"
    )
    return id
  }

  suspend fun updateLab(lab: Laboratory, currentUser: User) {
    labDao.update(lab)
    logAudit(
      user = currentUser,
      action = AuditActionType.UPDATE_LAB,
      description = "تعديل بيانات المعمل: ${lab.name}",
      entityId = lab.id,
      entityType = "Laboratory"
    )
  }

  suspend fun deleteLab(lab: Laboratory) {
    labDao.delete(lab)
  }

  // --- Work Types ---
  suspend fun insertWorkType(workType: WorkType): Long = workTypeDao.insert(workType)
  suspend fun updateWorkType(workType: WorkType) = workTypeDao.update(workType)
  suspend fun deleteWorkType(workType: WorkType) = workTypeDao.delete(workType)

  // --- Lab Prices ---
  suspend fun setLabPrice(labId: Long, workTypeId: Long, price: Double, currentUser: User) {
    labPriceDao.insertOrUpdate(LabPrice(labId = labId, workTypeId = workTypeId, customPrice = price))
    logAudit(
      user = currentUser,
      action = AuditActionType.UPDATE_PRICE,
      description = "تحديد سعر مخصص للمعمل ID $labId لنوع العمل ID $workTypeId بمبلغ $price",
      entityId = labId,
      entityType = "LabPrice"
    )
  }

  // --- Shipments ---
  suspend fun generateNextShipmentNumber(): String {
    val count = shipmentDao.getShipmentCount()
    val nextNum = count + 125 + 1
    return String.format(Locale.US, "#%06d", nextNum)
  }

  suspend fun createShipment(shipment: Shipment, currentUser: User): Long {
    val (unitPrice, totalPrice) = calculatePrice(
      labId = shipment.labId,
      workTypeId = shipment.workTypeId,
      pieceCount = shipment.pieceCount,
      discount = shipment.discount
    )
    val finalShipment = shipment.copy(
      unitPrice = unitPrice,
      totalPrice = totalPrice,
      createdByUserId = currentUser.id,
      createdByName = currentUser.fullName
    )
    val id = shipmentDao.insert(finalShipment)
    logAudit(
      user = currentUser,
      action = AuditActionType.CREATE_SHIPMENT,
      description = "إنشاء إرسالية ${shipment.shipmentNumber} (${shipment.pieceCount} ${shipment.workTypeName} - ${shipment.labName})",
      entityId = id,
      entityType = "Shipment"
    )
    return id
  }

  suspend fun updateShipment(shipment: Shipment, currentUser: User) {
    val (unitPrice, totalPrice) = calculatePrice(
      labId = shipment.labId,
      workTypeId = shipment.workTypeId,
      pieceCount = shipment.pieceCount,
      discount = shipment.discount
    )
    val updated = shipment.copy(unitPrice = unitPrice, totalPrice = totalPrice)
    shipmentDao.update(updated)
    logAudit(
      user = currentUser,
      action = AuditActionType.EDIT_SHIPMENT,
      description = "تعديل بيانات الإرسالية ${shipment.shipmentNumber}",
      entityId = shipment.id,
      entityType = "Shipment"
    )
  }

  suspend fun updateShipmentStatus(shipmentId: Long, newStatus: ShipmentStatus, currentUser: User) {
    val current = shipmentDao.getShipmentById(shipmentId) ?: return
    val actualReceived = if (newStatus == ShipmentStatus.RECEIVED && current.actualReceivedDate == null) {
      System.currentTimeMillis()
    } else current.actualReceivedDate

    val updated = current.copy(status = newStatus, actualReceivedDate = actualReceived)
    shipmentDao.update(updated)

    logAudit(
      user = currentUser,
      action = AuditActionType.UPDATE_STATUS,
      description = "تحديث حالة الإرسالية ${current.shipmentNumber} إلى '${newStatus.titleAr}'",
      entityId = shipmentId,
      entityType = "Shipment"
    )
  }

  suspend fun deleteShipment(shipment: Shipment, currentUser: User) {
    shipmentDao.delete(shipment)
    logAudit(
      user = currentUser,
      action = AuditActionType.DELETE_SHIPMENT,
      description = "حذف الإرسالية ${shipment.shipmentNumber}",
      entityId = shipment.id,
      entityType = "Shipment"
    )
  }

  // --- Payments ---
  suspend fun recordPayment(payment: Payment, currentUser: User): Long {
    val finalPayment = payment.copy(
      recordedByUserId = currentUser.id,
      recordedByName = currentUser.fullName
    )
    val id = paymentDao.insert(finalPayment)
    logAudit(
      user = currentUser,
      action = AuditActionType.RECORD_PAYMENT,
      description = "تسجيل دفعة ${payment.amount} لمعمل ${payment.labName} (${payment.paymentMethod.titleAr})",
      entityId = id,
      entityType = "Payment"
    )
    return id
  }

  suspend fun deletePayment(payment: Payment) {
    paymentDao.delete(payment)
  }

  // --- Settings ---
  suspend fun getSetting(key: String, defaultValue: String): String {
    return settingDao.getSetting(key) ?: defaultValue
  }

  suspend fun setSetting(key: String, value: String) {
    settingDao.setSetting(AppSetting(key, value))
  }

  // --- Inventory Management ---
  suspend fun addInventoryItem(item: InventoryItem, currentUser: User): Long {
    val id = inventoryDao.insert(item)
    // Add initial transaction if quantity > 0
    if (item.currentStock > 0) {
      inventoryDao.insertTransaction(
        InventoryTransaction(
          itemId = id,
          itemName = item.name,
          type = InventoryTransactionType.STOCK_IN,
          quantityChange = item.currentStock,
          newStockLevel = item.currentStock,
          performedByUserId = currentUser.id,
          performedByName = currentUser.fullName,
          reasonOrReference = "رصيد افتتاحي / إضافة مادة جديدة"
        )
      )
    }
    logAudit(
      user = currentUser,
      action = AuditActionType.CREATE_SHIPMENT,
      description = "إضافة مادة مخزون جديدة: ${item.name} (الرصيد: ${item.currentStock} ${item.unit})",
      entityId = id,
      entityType = "InventoryItem"
    )
    return id
  }

  suspend fun updateInventoryItem(item: InventoryItem, currentUser: User) {
    val old = inventoryDao.getItemById(item.id)
    inventoryDao.update(item)
    if (old != null && old.currentStock != item.currentStock) {
      val diff = item.currentStock - old.currentStock
      val type = if (diff > 0) InventoryTransactionType.ADJUSTMENT_ADD else InventoryTransactionType.ADJUSTMENT_SUBTRACT
      inventoryDao.insertTransaction(
        InventoryTransaction(
          itemId = item.id,
          itemName = item.name,
          type = type,
          quantityChange = diff,
          newStockLevel = item.currentStock,
          performedByUserId = currentUser.id,
          performedByName = currentUser.fullName,
          reasonOrReference = "تعديل رصيد بطاقة الصنف"
        )
      )
    }
    logAudit(
      user = currentUser,
      action = AuditActionType.EDIT_SHIPMENT,
      description = "تعديل بيانات مادة المخزون: ${item.name}",
      entityId = item.id,
      entityType = "InventoryItem"
    )
  }

  suspend fun deleteInventoryItem(item: InventoryItem, currentUser: User) {
    inventoryDao.delete(item)
    logAudit(
      user = currentUser,
      action = AuditActionType.DELETE_SHIPMENT,
      description = "حذف مادة المخزون: ${item.name}",
      entityId = item.id,
      entityType = "InventoryItem"
    )
  }

  suspend fun adjustInventoryStock(
    itemId: Long,
    quantityChange: Double,
    type: InventoryTransactionType,
    reason: String,
    currentUser: User
  ): Boolean {
    val item = inventoryDao.getItemById(itemId) ?: return false
    val calculatedNewStock = if (type.isAddition) {
      item.currentStock + kotlin.math.abs(quantityChange)
    } else {
      (item.currentStock - kotlin.math.abs(quantityChange)).coerceAtLeast(0.0)
    }
    val updatedItem = item.copy(
      currentStock = calculatedNewStock,
      lastRestockedDate = if (type.isAddition) System.currentTimeMillis() else item.lastRestockedDate
    )
    inventoryDao.update(updatedItem)
    val actualChange = if (type.isAddition) kotlin.math.abs(quantityChange) else -kotlin.math.abs(quantityChange)
    inventoryDao.insertTransaction(
      InventoryTransaction(
        itemId = item.id,
        itemName = item.name,
        type = type,
        quantityChange = actualChange,
        newStockLevel = calculatedNewStock,
        performedByUserId = currentUser.id,
        performedByName = currentUser.fullName,
        reasonOrReference = reason.ifEmpty { type.titleAr }
      )
    )
    logAudit(
      user = currentUser,
      action = AuditActionType.UPDATE_STATUS,
      description = "${type.titleAr} للمادة: ${item.name} بمقدار ($actualChange ${item.unit}) - الرصيد الجديد: $calculatedNewStock",
      entityId = item.id,
      entityType = "InventoryItem"
    )
    return true
  }

  fun getItemTransactions(itemId: Long): Flow<List<InventoryTransaction>> {
    return inventoryDao.getTransactionsForItem(itemId)
  }

  // --- Devices & Security Management ---
  suspend fun insertOrUpdateDevice(device: DeviceBinding): Long {
    return deviceBindingDao.insert(device)
  }

  suspend fun updateDeviceStatus(
    deviceId: String,
    newStatus: DeviceStatus,
    approvedBy: String,
    currentUser: User
  ) {
    deviceBindingDao.updateStatus(deviceId, newStatus, approvedBy)
    logAudit(
      user = currentUser,
      action = if (newStatus == DeviceStatus.APPROVED) AuditActionType.DEVICE_APPROVAL else AuditActionType.DEVICE_BLOCKED,
      description = "تحديث حالة الجهاز ($deviceId) إلى: ${newStatus.titleAr} بواسطة ${currentUser.fullName}",
      entityType = "DeviceBinding"
    )
  }

  suspend fun deleteDevice(device: DeviceBinding, currentUser: User) {
    deviceBindingDao.delete(device)
    logAudit(
      user = currentUser,
      action = AuditActionType.DEVICE_BLOCKED,
      description = "إلغاء وحذف ترخيص الجهاز: ${device.deviceModel} (${device.deviceId})",
      entityType = "DeviceBinding"
    )
  }

  suspend fun getDeviceById(deviceId: String): DeviceBinding? {
    return deviceBindingDao.getDeviceById(deviceId)
  }

  suspend fun getApprovedDevicesCountForUser(userId: Long): Int {
    return deviceBindingDao.getApprovedDevicesCountForUser(userId)
  }

  fun observeDeviceById(deviceId: String): Flow<DeviceBinding?> {
    return deviceBindingDao.observeDeviceById(deviceId)
  }

  // --- Audit Logging helper ---
  suspend fun logAudit(
    user: User,
    action: AuditActionType,
    description: String,
    entityId: Long? = null,
    entityType: String = ""
  ) {
    val log = AuditLog(
      timestamp = System.currentTimeMillis(),
      userId = user.id,
      userName = user.fullName,
      userRole = user.role,
      actionType = action,
      description = description,
      entityId = entityId,
      entityType = entityType
    )
    auditLogDao.insert(log)
  }
}
