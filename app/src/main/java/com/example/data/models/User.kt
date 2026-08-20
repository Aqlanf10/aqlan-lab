package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole(val titleAr: String, val titleEn: String, val level: Int) {
  SUPER_ADMIN("المشرف العام (المالك)", "Super Admin", 0),
  ADMIN("مدير النظام", "Admin", 1),
  ACCOUNTANT("محاسب مالي", "Accountant", 2),
  STAFF("موظف استقبال", "Staff", 3),
  TECHNICIAN("فني معمل", "Technician", 4);

  val canManageUsers: Boolean get() = this == SUPER_ADMIN
  val canManageDevices: Boolean get() = this == SUPER_ADMIN
  val canViewFinancials: Boolean get() = this == SUPER_ADMIN || this == ADMIN || this == ACCOUNTANT
  val canEditPrices: Boolean get() = this == SUPER_ADMIN || this == ACCOUNTANT || this == ADMIN
  val canDeleteRecords: Boolean get() = this == SUPER_ADMIN || this == ADMIN
}

@Entity(tableName = "users")
data class User(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val uid: String = "", // Firebase UID if linked
  val username: String,
  val fullName: String,
  val email: String = "",
  val role: UserRole = UserRole.STAFF,
  val pinCode: String = "1234",
  val avatarColor: Long = 0xFF00687A,
  val isActive: Boolean = true,
  val isApproved: Boolean = true,
  val maxDevices: Int = 2,
  val createdAt: Long = System.currentTimeMillis(),
  val lastLoginAt: Long? = null,
  val phone: String = ""
)
