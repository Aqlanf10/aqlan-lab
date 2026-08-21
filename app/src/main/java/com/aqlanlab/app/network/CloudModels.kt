package com.aqlanlab.app.network

import com.aqlanlab.app.data.models.*
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudBackupPayload(
  val version: Int = 2,
  val timestamp: Long = System.currentTimeMillis(),
  val clinicId: String = "clinic_elite_01",
  val clinicName: String = "مركز النخبة لطب وتجميل الأسنان",
  val deviceName: String = "Dental Lab Android Device",
  val createdByName: String = "مدير النظام",
  val labs: List<Laboratory> = emptyList(),
  val workTypes: List<WorkType> = emptyList(),
  val labPrices: List<LabPrice> = emptyList(),
  val shipments: List<Shipment> = emptyList(),
  val payments: List<Payment> = emptyList(),
  val inventoryItems: List<InventoryItem> = emptyList(),
  val inventoryTransactions: List<InventoryTransaction> = emptyList(),
  val auditLogs: List<AuditLog> = emptyList(),
  val users: List<User> = emptyList()
) {
  val totalRecordCount: Int
    get() = labs.size + workTypes.size + labPrices.size + shipments.size + payments.size + inventoryItems.size + inventoryTransactions.size + users.size
}

@JsonClass(generateAdapter = true)
data class CloudSyncResponse(
  val success: Boolean,
  val message: String,
  val syncedItemsCount: Int = 0,
  val serverTimestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class FirestoreBackupSnapshot(
  val id: String = "",
  val clinicId: String = "clinic_elite_01",
  val clinicName: String = "",
  val timestamp: Long = System.currentTimeMillis(),
  val totalRecords: Int = 0,
  val shipmentsCount: Int = 0,
  val labsCount: Int = 0,
  val paymentsCount: Int = 0,
  val inventoryCount: Int = 0,
  val createdBy: String = "",
  val deviceName: String = "Android Device",
  val notes: String = "نسخة احتياطية سحابية دورية"
)

@JsonClass(generateAdapter = true)
data class FirebaseStorageBackupInfo(
  val backupId: String = "",
  val fileName: String = "",
  val storagePath: String = "",
  val downloadUrl: String = "",
  val fileSizeBytes: Long = 0L,
  val timestamp: Long = System.currentTimeMillis(),
  val clinicId: String = "clinic_aqlan_center",
  val clinicName: String = "",
  val shipmentsCount: Int = 0,
  val labsCount: Int = 0,
  val paymentsCount: Int = 0,
  val inventoryCount: Int = 0,
  val totalRecords: Int = 0,
  val isAutoBackup: Boolean = false,
  val createdByName: String = "النسخ الاحتياطي التلقائي"
) {
  val formattedSize: String
    get() {
      return when {
        fileSizeBytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", fileSizeBytes / (1024.0 * 1024.0))
        fileSizeBytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", fileSizeBytes / 1024.0)
        fileSizeBytes > 0 -> "$fileSizeBytes Bytes"
        else -> "0 KB"
      }
    }
}

enum class SyncState {
  IDLE,
  SYNCING,
  SUCCESS,
  ERROR
}

enum class CloudSyncMode(val titleAr: String) {
  FIREBASE_STORAGE("تخزين فايربيس السحابي (Firebase Storage)"),
  FIRESTORE("سحابة فايربيس (Firestore Cloud)"),
  REST_API("خادم REST API مخصص"),
  LOCAL_EXPORT("تصدير/استيراد ملف JSON")
}

enum class AutoBackupFrequency(val titleAr: String, val intervalMillis: Long) {
  ON_EVERY_CHANGE("عند كل تعديل جديد فورياً (Live Auto-Sync)", 0L),
  EVERY_6_HOURS("كل 6 ساعات", 6 * 3600 * 1000L),
  EVERY_12_HOURS("كل 12 ساعة", 12 * 3600 * 1000L),
  DAILY("يومياً (كل 24 ساعة)", 24 * 3600 * 1000L)
}
