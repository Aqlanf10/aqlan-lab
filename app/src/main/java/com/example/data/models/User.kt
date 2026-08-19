package com.example.data.models

import androidx.room.ColumnInfo
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
  /**
   * تجزئة رمز المرور (PBKDF2) وليس الرمز نفسه.
   *
   * اسم العمود بقي `pinCode` للتوافق مع قواعد البيانات المثبّتة مسبقاً، لكن
   * المحتوى أصبح تجزئة. القيم القديمة المخزنة نصاً صريحاً تُرقّى تلقائياً إلى
   * تجزئة عند أول دخول ناجح (انظر `PinSecurity.needsUpgrade`).
   *
   * لا توجد قيمة افتراضية عمداً — كل مستخدم يجب أن يُنشأ برمز مرور صريح.
   */
  @ColumnInfo(name = "pinCode") val pinHash: String,
  val avatarColor: Long = 0xFF00687A
)
