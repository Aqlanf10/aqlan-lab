package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole(val titleAr: String, val titleEn: String) {
  ADMIN("مدير النظام", "Admin"),
  STAFF("موظف", "Staff"),
  ACCOUNTANT("محاسب", "Accountant")
}

@Entity(tableName = "users")
data class User(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val username: String,
  val fullName: String,
  val role: UserRole,
  val pinCode: String = "1234",
  val avatarColor: Long = 0xFF00687A
)
