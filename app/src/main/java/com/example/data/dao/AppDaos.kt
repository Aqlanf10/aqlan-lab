package com.example.data.dao

import androidx.room.*
import com.example.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LabDao {
  @Query("SELECT * FROM laboratories ORDER BY name ASC")
  fun getAllLabs(): Flow<List<Laboratory>>

  @Query("SELECT * FROM laboratories ORDER BY name ASC")
  suspend fun getAllSync(): List<Laboratory>

  @Query("SELECT * FROM laboratories WHERE id = :id")
  suspend fun getLabById(id: Long): Laboratory?

  @Query("SELECT * FROM laboratories WHERE status = 'ACTIVE' ORDER BY name ASC")
  fun getActiveLabs(): Flow<List<Laboratory>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(lab: Laboratory): Long

  @Update
  suspend fun update(lab: Laboratory)

  @Delete
  suspend fun delete(lab: Laboratory)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(labs: List<Laboratory>)

  @Query("DELETE FROM laboratories")
  suspend fun deleteAllLabs()
}

@Dao
interface WorkTypeDao {
  @Query("SELECT * FROM work_types ORDER BY nameAr ASC")
  fun getAllWorkTypes(): Flow<List<WorkType>>

  @Query("SELECT * FROM work_types ORDER BY nameAr ASC")
  suspend fun getAllSync(): List<WorkType>

  @Query("SELECT * FROM work_types WHERE isActive = 1 ORDER BY nameAr ASC")
  fun getActiveWorkTypes(): Flow<List<WorkType>>

  @Query("SELECT * FROM work_types WHERE id = :id")
  suspend fun getWorkTypeById(id: Long): WorkType?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(workType: WorkType): Long

  @Update
  suspend fun update(workType: WorkType)

  @Delete
  suspend fun delete(workType: WorkType)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(workTypes: List<WorkType>)

  @Query("DELETE FROM work_types")
  suspend fun deleteAllWorkTypes()
}

@Dao
interface LabPriceDao {
  @Query("SELECT * FROM lab_prices")
  fun getAllPrices(): Flow<List<LabPrice>>

  @Query("SELECT * FROM lab_prices")
  suspend fun getAllSync(): List<LabPrice>

  @Query("SELECT * FROM lab_prices WHERE labId = :labId")
  fun getPricesForLab(labId: Long): Flow<List<LabPrice>>

  @Query("SELECT * FROM lab_prices WHERE labId = :labId AND workTypeId = :workTypeId LIMIT 1")
  suspend fun getPriceForLabAndWork(labId: Long, workTypeId: Long): LabPrice?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(price: LabPrice): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(prices: List<LabPrice>)

  @Query("DELETE FROM lab_prices WHERE labId = :labId AND workTypeId = :workTypeId")
  suspend fun deleteLabPrice(labId: Long, workTypeId: Long)

  @Query("DELETE FROM lab_prices")
  suspend fun deleteAllPrices()
}

@Dao
interface ShipmentDao {
  @Query("SELECT * FROM shipments ORDER BY orderDate DESC")
  fun getAllShipments(): Flow<List<Shipment>>

  @Query("SELECT * FROM shipments ORDER BY orderDate DESC")
  suspend fun getAllSync(): List<Shipment>

  @Query("SELECT * FROM shipments WHERE id = :id")
  suspend fun getShipmentById(id: Long): Shipment?

  @Query("SELECT * FROM shipments WHERE labId = :labId ORDER BY orderDate DESC")
  fun getShipmentsForLab(labId: Long): Flow<List<Shipment>>

  @Query("SELECT * FROM shipments WHERE status = :status ORDER BY orderDate DESC")
  fun getShipmentsByStatus(status: ShipmentStatus): Flow<List<Shipment>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(shipment: Shipment): Long

  @Update
  suspend fun update(shipment: Shipment)

  @Delete
  suspend fun delete(shipment: Shipment)

  @Query("DELETE FROM shipments WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(shipments: List<Shipment>)

  @Query("SELECT COUNT(*) FROM shipments")
  suspend fun getShipmentCount(): Int

  @Query("DELETE FROM shipments")
  suspend fun deleteAllShipments()
}

@Dao
interface PaymentDao {
  @Query("SELECT * FROM payments ORDER BY paymentDate DESC")
  fun getAllPayments(): Flow<List<Payment>>

  @Query("SELECT * FROM payments ORDER BY paymentDate DESC")
  suspend fun getAllSync(): List<Payment>

  @Query("SELECT * FROM payments WHERE labId = :labId ORDER BY paymentDate DESC")
  fun getPaymentsForLab(labId: Long): Flow<List<Payment>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(payment: Payment): Long

  @Delete
  suspend fun delete(payment: Payment)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(payments: List<Payment>)

  @Query("DELETE FROM payments")
  suspend fun deleteAllPayments()
}

@Dao
interface AuditLogDao {
  @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
  fun getRecentLogs(): Flow<List<AuditLog>>

  @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 300")
  suspend fun getAllSync(): List<AuditLog>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(log: AuditLog): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(logs: List<AuditLog>)

  @Query("DELETE FROM audit_logs")
  suspend fun deleteAllAuditLogs()
}

@Dao
interface UserDao {
  @Query("SELECT * FROM users ORDER BY id ASC")
  fun getAllUsers(): Flow<List<User>>

  @Query("SELECT * FROM users ORDER BY id ASC")
  suspend fun getAllSync(): List<User>

  @Query("SELECT * FROM users WHERE id = :id")
  suspend fun getUserById(id: Long): User?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(user: User): Long

  @Update
  suspend fun update(user: User)

  @Delete
  suspend fun delete(user: User)

  @Query("DELETE FROM users WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(users: List<User>)
}

@Dao
interface SettingDao {
  @Query("SELECT * FROM app_settings")
  fun getAllSettings(): Flow<List<AppSetting>>

  @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
  suspend fun getSetting(key: String): String?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun setSetting(setting: AppSetting)
}

@Dao
interface InventoryDao {
  @Query("SELECT * FROM inventory_items ORDER BY name ASC")
  fun getAllItems(): Flow<List<InventoryItem>>

  @Query("SELECT * FROM inventory_items ORDER BY name ASC")
  suspend fun getAllSync(): List<InventoryItem>

  @Query("SELECT * FROM inventory_items WHERE currentStock <= minThreshold ORDER BY (currentStock - minThreshold) ASC")
  fun getLowStockItems(): Flow<List<InventoryItem>>

  @Query("SELECT * FROM inventory_items WHERE id = :id")
  suspend fun getItemById(id: Long): InventoryItem?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: InventoryItem): Long

  @Update
  suspend fun update(item: InventoryItem)

  @Delete
  suspend fun delete(item: InventoryItem)

  @Query("DELETE FROM inventory_items WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(items: List<InventoryItem>)

  @Query("SELECT COUNT(*) FROM inventory_items")
  suspend fun getItemCount(): Int

  @Query("DELETE FROM inventory_items")
  suspend fun deleteAllItems()

  // Transactions
  @Query("SELECT * FROM inventory_transactions ORDER BY date DESC LIMIT 300")
  fun getAllTransactions(): Flow<List<InventoryTransaction>>

  @Query("SELECT * FROM inventory_transactions ORDER BY date DESC LIMIT 300")
  suspend fun getAllTransactionsSync(): List<InventoryTransaction>

  @Query("SELECT * FROM inventory_transactions WHERE itemId = :itemId ORDER BY date DESC")
  fun getTransactionsForItem(itemId: Long): Flow<List<InventoryTransaction>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTransaction(transaction: InventoryTransaction): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAllTransactions(transactions: List<InventoryTransaction>)

  @Query("DELETE FROM inventory_transactions")
  suspend fun deleteAllTransactions()
}

