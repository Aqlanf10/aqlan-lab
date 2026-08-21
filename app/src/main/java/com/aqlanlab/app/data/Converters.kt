package com.aqlanlab.app.data

import androidx.room.TypeConverter
import com.aqlanlab.app.data.models.*

class Converters {
  @TypeConverter
  fun fromUserRole(value: UserRole): String = value.name

  @TypeConverter
  fun toUserRole(value: String): UserRole = runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.STAFF)

  @TypeConverter
  fun fromLabStatus(value: LabStatus): String = value.name

  @TypeConverter
  fun toLabStatus(value: String): LabStatus = runCatching { LabStatus.valueOf(value) }.getOrDefault(LabStatus.ACTIVE)

  @TypeConverter
  fun fromShipmentStatus(value: ShipmentStatus): String = value.name

  @TypeConverter
  fun toShipmentStatus(value: String): ShipmentStatus = runCatching { ShipmentStatus.valueOf(value) }.getOrDefault(ShipmentStatus.NEW)

  @TypeConverter
  fun fromPaymentMethod(value: PaymentMethod): String = value.name

  @TypeConverter
  fun toPaymentMethod(value: String): PaymentMethod = runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.CASH)

  @TypeConverter
  fun fromAuditActionType(value: AuditActionType): String = value.name

  @TypeConverter
  fun toAuditActionType(value: String): AuditActionType = runCatching { AuditActionType.valueOf(value) }.getOrDefault(AuditActionType.CREATE_SHIPMENT)

  @TypeConverter
  fun fromInventoryTransactionType(value: InventoryTransactionType): String = value.name

  @TypeConverter
  fun toInventoryTransactionType(value: String): InventoryTransactionType = runCatching { InventoryTransactionType.valueOf(value) }.getOrDefault(InventoryTransactionType.STOCK_IN)
}
