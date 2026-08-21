package com.aqlanlab.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeviceStatus(val titleAr: String, val isAllowed: Boolean) {
  APPROVED("معتمد ومصرح", true),
  PENDING("قيد انتظار موافقة المشرف", false),
  BLOCKED("محظور من النظام", false),
  REVOKED("ملغى الترخيص", false)
}

sealed class DeviceAuthOutcome {
  data class Allowed(val device: DeviceBinding) : DeviceAuthOutcome()
  data class PendingApproval(val device: DeviceBinding, val isMaxDevicesExceeded: Boolean = false, val message: String = "") : DeviceAuthOutcome()
  data class Blocked(val device: DeviceBinding, val reason: String = "") : DeviceAuthOutcome()
  data class Revoked(val device: DeviceBinding, val reason: String = "") : DeviceAuthOutcome()
}

@Entity(tableName = "device_bindings")
data class DeviceBinding(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val deviceId: String, // Unique installation / hardware UUID
  val userId: Long = 0,
  val userName: String = "",
  val userRole: UserRole = UserRole.STAFF,
  val deviceModel: String = "", // e.g. "Samsung Galaxy S24 Ultra"
  val osVersion: String = "", // e.g. "Android 14 (API 34)"
  val appVersion: String = "1.0.0",
  val status: DeviceStatus = DeviceStatus.PENDING,
  val registeredAt: Long = System.currentTimeMillis(),
  val lastActiveAt: Long = System.currentTimeMillis(),
  val ipAddress: String = "",
  val approvedByAdmin: String = "",
  val notes: String = ""
)
