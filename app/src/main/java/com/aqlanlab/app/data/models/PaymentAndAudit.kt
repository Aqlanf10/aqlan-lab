package com.aqlanlab.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PaymentMethod(val titleAr: String) {
  CASH("نقداً"),
  BANK_TRANSFER("تحويل بنكي"),
  CHECK("شيك"),
  DIGITAL_WALLET("محفظة إلكترونية")
}

@Entity(tableName = "payments")
data class Payment(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val labId: Long,
  val labName: String,
  val amount: Double,
  val currency: String = "SAR", // The currency being credited to lab balance (YER, SAR, USD)
  val paidAmount: Double = amount, // The actual physical cash/transfer amount paid
  val paidCurrency: String = currency, // The physical currency handed over
  val exchangeRate: Double = 1.0, // Exchange rate applied (if paidCurrency != currency)
  val paymentDate: Long = System.currentTimeMillis(),
  val paymentMethod: PaymentMethod = PaymentMethod.CASH,
  val receiptNumber: String = "",
  val notes: String = "",
  val recordedByUserId: Long = 1,
  val recordedByName: String = "Admin"
)

enum class AuditActionType(val titleAr: String) {
  CREATE_SHIPMENT("إنشاء إرسالية"),
  UPDATE_STATUS("تحديث حالة"),
  UPDATE_PRICE("تعديل سعر"),
  RECORD_PAYMENT("تسجيل دفعة"),
  EDIT_SHIPMENT("تعديل إرسالية"),
  DELETE_SHIPMENT("حذف إرسالية"),
  ADD_LAB("إضافة معمل"),
  UPDATE_LAB("تعديل معمل"),
  SWITCH_USER("تبديل المستخدم"),
  DEVICE_REGISTRATION("طلب تسجيل جهاز"),
  DEVICE_APPROVAL("اعتماد جهاز مصرح"),
  DEVICE_BLOCKED("حظر جهاز"),
  USER_STATUS_CHANGE("تغيير حالة حساب"),
  LOGIN_SUCCESS("تسجيل دخول ناجح"),
  LOGIN_FAILED("محاولة دخول فاشلة"),
  SECURITY_WARNING("تنبيه أمان")
}

@Entity(tableName = "audit_logs")
data class AuditLog(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val userId: Long,
  val userName: String,
  val userRole: UserRole,
  val actionType: AuditActionType,
  val description: String,
  val entityId: Long? = null,
  val entityType: String = ""
)

@Entity(tableName = "app_settings")
data class AppSetting(
  @PrimaryKey val key: String,
  val value: String
)

data class DetailedStatementItem(
  val id: String,
  val date: Long,
  val type: String,
  val isShipment: Boolean,
  val referenceNumber: String,
  val doctorName: String,
  val patientName: String,
  val workDetails: String,
  val pieceCount: Int, // عدد القطع
  val toothNumbers: String,
  val shade: String,
  val currency: String = "SAR",
  val debit: Double, // مدين (فاتورة عمل)
  val credit: Double, // دائن (دفعة مسددة)
  val runningBalance: Double // الرصيد التراكمي
)
