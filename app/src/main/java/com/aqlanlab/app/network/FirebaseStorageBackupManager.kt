package com.aqlanlab.app.network

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.aqlanlab.app.data.AppDatabase
import com.aqlanlab.app.data.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enterprise Automatic Firebase Storage Backup Manager for Dental Clinic & Lab Management
 * Automatically safeguards patients, dental shipments, lab accounts, invoices, and prices to Firebase Cloud Storage.
 */
class FirebaseStorageBackupManager(
  private val context: Context,
  private val database: AppDatabase
) {
  companion object {
    private const val TAG = "FirebaseStorageBackup"
    const val MAX_DOWNLOAD_BYTE_SIZE = 25 * 1024 * 1024L // 25 MB limit for single JSON backup
  }

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val payloadAdapter = moshi.adapter(CloudBackupPayload::class.java)

  // Reactive State Flows
  private val _backupState = MutableStateFlow(SyncState.IDLE)
  val backupState: StateFlow<SyncState> = _backupState.asStateFlow()

  private val _statusMessage = MutableStateFlow("نظام النسخ الاحتياطي السحابي على Firebase Storage جاهز")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private val _lastBackupTimestamp = MutableStateFlow<Long?>(null)
  val lastBackupTimestamp: StateFlow<Long?> = _lastBackupTimestamp.asStateFlow()

  private val _availableStorageBackups = MutableStateFlow<List<FirebaseStorageBackupInfo>>(emptyList())
  val availableStorageBackups: StateFlow<List<FirebaseStorageBackupInfo>> = _availableStorageBackups.asStateFlow()

  private val _isAutoBackupEnabled = MutableStateFlow(true)
  val isAutoBackupEnabled: StateFlow<Boolean> = _isAutoBackupEnabled.asStateFlow()

  private val _autoBackupFrequency = MutableStateFlow(AutoBackupFrequency.ON_EVERY_CHANGE)
  val autoBackupFrequency: StateFlow<AutoBackupFrequency> = _autoBackupFrequency.asStateFlow()

  private val _lastUploadedBackupInfo = MutableStateFlow<FirebaseStorageBackupInfo?>(null)
  val lastUploadedBackupInfo: StateFlow<FirebaseStorageBackupInfo?> = _lastUploadedBackupInfo.asStateFlow()

  private var autoBackupJob: Job? = null

  private fun isFirebaseAvailable(): Boolean {
    return try {
      FirebaseApp.getApps(context).isNotEmpty()
    } catch (e: Exception) {
      false
    }
  }

  fun setAutoBackupEnabled(enabled: Boolean) {
    _isAutoBackupEnabled.value = enabled
  }

  fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
    _autoBackupFrequency.value = frequency
  }

  /**
   * Generates and uploads a complete JSON backup archive to Firebase Storage.
   */
  suspend fun uploadBackupToStorage(
    clinicId: String = CloudSyncManager.DEFAULT_CLINIC_ID,
    clinicName: String = CloudSyncManager.DEFAULT_CLINIC_NAME,
    currentUser: User? = null,
    isAutoBackup: Boolean = false
  ): Result<FirebaseStorageBackupInfo> = withContext(Dispatchers.IO) {
    _backupState.value = SyncState.SYNCING
    val modeText = if (isAutoBackup) "النسخ التلقائي" else "النسخ الفوري"
    _statusMessage.value = "جاري إنشاء وتجهيز حزمة البيانات لرفعها إلى Firebase Storage ($modeText)..."

    try {
      val timestamp = System.currentTimeMillis()
      val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
      val backupId = "backup_${dateStr}_${timestamp % 1000}"
      val fileName = "${backupId}.json"
      val storagePath = "backups/$clinicId/$fileName"
      val latestStoragePath = "backups/$clinicId/latest_backup.json"

      // 1. Gather all database records
      // SECURITY FIX: PIN hashes are stripped from the uploaded backup (previously the
      // full users table including salted PIN hashes was uploaded to Firebase Storage,
      // where ADMIN/ACCOUNTANT roles could download and brute-force it offline).
      val payload = CloudBackupPayload(
        clinicId = clinicId,
        clinicName = clinicName,
        timestamp = timestamp,
        deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
        createdByName = currentUser?.fullName ?: if (isAutoBackup) "النسخ الاحتياطي التلقائي الذكي" else "مدير المركز",
        labs = database.labDao().getAllSync(),
        workTypes = database.workTypeDao().getAllSync(),
        labPrices = database.labPriceDao().getAllSync(),
        shipments = database.shipmentDao().getAllSync(),
        payments = database.paymentDao().getAllSync(),
        inventoryItems = database.inventoryDao().getAllSync(),
        inventoryTransactions = database.inventoryDao().getAllTransactionsSync(),
        users = database.userDao().getAllSync().map { it.copy(pinCode = "") }
      )

      val jsonString = payloadAdapter.toJson(payload)
      val jsonBytes = jsonString.toByteArray(StandardCharsets.UTF_8)
      val fileSizeBytes = jsonBytes.size.toLong()

      var downloadUrl = ""

      if (isFirebaseAvailable()) {
        val storage = FirebaseStorage.getInstance()
        val backupRef = storage.reference.child(storagePath)
        val latestRef = storage.reference.child(latestStoragePath)

        val metadata = StorageMetadata.Builder()
          .setContentType("application/json")
          .setCustomMetadata("clinicId", clinicId)
          .setCustomMetadata("clinicName", clinicName)
          .setCustomMetadata("totalRecords", payload.totalRecordCount.toString())
          .setCustomMetadata("shipmentsCount", payload.shipments.size.toString())
          .setCustomMetadata("labsCount", payload.labs.size.toString())
          .setCustomMetadata("isAutoBackup", isAutoBackup.toString())
          .setCustomMetadata("createdByName", payload.createdByName)
          .setCustomMetadata("timestamp", timestamp.toString())
          .build()

        // Upload primary timestamped file
        backupRef.putBytes(jsonBytes, metadata).await()

        // Upload latest pointer file for instant restore
        latestRef.putBytes(jsonBytes, metadata).await()

        try {
          downloadUrl = backupRef.downloadUrl.await().toString()
        } catch (e: Exception) {
          Log.w(TAG, "Could not get download URL: ${e.message}")
        }
      } else {
        Log.w(TAG, "Firebase is offline or not configured, saving metadata locally")
      }

      val backupInfo = FirebaseStorageBackupInfo(
        backupId = backupId,
        fileName = fileName,
        storagePath = storagePath,
        downloadUrl = downloadUrl,
        fileSizeBytes = fileSizeBytes,
        timestamp = timestamp,
        clinicId = clinicId,
        clinicName = clinicName,
        shipmentsCount = payload.shipments.size,
        labsCount = payload.labs.size,
        paymentsCount = payload.payments.size,
        inventoryCount = payload.inventoryItems.size,
        totalRecords = payload.totalRecordCount,
        isAutoBackup = isAutoBackup,
        createdByName = payload.createdByName
      )

      // Save metadata entry in Firestore for quick browsing & multi-device sync
      if (isFirebaseAvailable()) {
        try {
          val firestore = FirebaseFirestore.getInstance()
          val dataMap = hashMapOf(
            "backupId" to backupInfo.backupId,
            "fileName" to backupInfo.fileName,
            "storagePath" to backupInfo.storagePath,
            "downloadUrl" to backupInfo.downloadUrl,
            "fileSizeBytes" to backupInfo.fileSizeBytes,
            "timestamp" to backupInfo.timestamp,
            "clinicId" to backupInfo.clinicId,
            "clinicName" to backupInfo.clinicName,
            "shipmentsCount" to backupInfo.shipmentsCount,
            "labsCount" to backupInfo.labsCount,
            "paymentsCount" to backupInfo.paymentsCount,
            "inventoryCount" to backupInfo.inventoryCount,
            "totalRecords" to backupInfo.totalRecords,
            "isAutoBackup" to backupInfo.isAutoBackup,
            "createdByName" to backupInfo.createdByName
          )

          firestore.collection("clinics")
            .document(clinicId)
            .collection("storage_backups")
            .document(backupId)
            .set(dataMap, SetOptions.merge())
            .await()
        } catch (e: Exception) {
          Log.e(TAG, "Error saving backup metadata to Firestore: ${e.message}")
        }
      }

      _lastBackupTimestamp.value = timestamp
      _lastUploadedBackupInfo.value = backupInfo
      _backupState.value = SyncState.SUCCESS
      _statusMessage.value = "تم رفع النسخة الاحتياطية بنجاح إلى Firebase Storage (${backupInfo.formattedSize} • ${payload.totalRecordCount} سجل)."

      // Refresh list
      fetchAvailableStorageBackups(clinicId)

      Result.success(backupInfo)
    } catch (e: Exception) {
      Log.e(TAG, "Error during Firebase Storage backup: ${e.message}", e)
      _backupState.value = SyncState.ERROR
      _statusMessage.value = "فشل رفع النسخة إلى Firebase Storage: ${e.localizedMessage ?: "خطأ غير متوقع"}"
      Result.failure(e)
    }
  }

  /**
   * Fetches the list of all storage backups for this clinic.
   */
  suspend fun fetchAvailableStorageBackups(clinicId: String = CloudSyncManager.DEFAULT_CLINIC_ID): List<FirebaseStorageBackupInfo> = withContext(Dispatchers.IO) {
    try {
      if (isFirebaseAvailable()) {
        val firestore = FirebaseFirestore.getInstance()
        val snapshot = firestore.collection("clinics")
          .document(clinicId)
          .collection("storage_backups")
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .limit(20)
          .get()
          .await()

        val list = snapshot.documents.mapNotNull { doc ->
          try {
            FirebaseStorageBackupInfo(
              backupId = doc.getString("backupId") ?: doc.id,
              fileName = doc.getString("fileName") ?: "${doc.id}.json",
              storagePath = doc.getString("storagePath") ?: "backups/$clinicId/${doc.id}.json",
              downloadUrl = doc.getString("downloadUrl") ?: "",
              fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L,
              timestamp = doc.getLong("timestamp") ?: 0L,
              clinicId = doc.getString("clinicId") ?: clinicId,
              clinicName = doc.getString("clinicName") ?: "",
              shipmentsCount = doc.getLong("shipmentsCount")?.toInt() ?: 0,
              labsCount = doc.getLong("labsCount")?.toInt() ?: 0,
              paymentsCount = doc.getLong("paymentsCount")?.toInt() ?: 0,
              inventoryCount = doc.getLong("inventoryCount")?.toInt() ?: 0,
              totalRecords = doc.getLong("totalRecords")?.toInt() ?: 0,
              isAutoBackup = doc.getBoolean("isAutoBackup") ?: false,
              createdByName = doc.getString("createdByName") ?: "مدير المركز"
            )
          } catch (e: Exception) {
            null
          }
        }

        _availableStorageBackups.value = list
        return@withContext list
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching storage backups list: ${e.message}")
    }
    _availableStorageBackups.value
  }

  /**
   * Downloads and restores a backup from Firebase Storage into the local Room database.
   */
  suspend fun restoreFromStorageBackup(backupInfo: FirebaseStorageBackupInfo): Result<Int> = withContext(Dispatchers.IO) {
    _backupState.value = SyncState.SYNCING
    _statusMessage.value = "جاري تنزيل النسخة الاحتياطية من Firebase Storage (${backupInfo.fileName})..."

    try {
      var jsonContent: String? = null

      if (isFirebaseAvailable()) {
        val storage = FirebaseStorage.getInstance()
        val fileRef = storage.reference.child(backupInfo.storagePath)
        val bytes = fileRef.getBytes(MAX_DOWNLOAD_BYTE_SIZE).await()
        jsonContent = String(bytes, StandardCharsets.UTF_8)
      }

      if (jsonContent.isNullOrBlank()) {
        throw IllegalStateException("تعذر تنزيل محتوى النسخة الاحتياطية من السحابة")
      }

      _statusMessage.value = "جاري استعادة البيانات إلى قاعدة البيانات المحلية..."
      val payload = payloadAdapter.fromJson(jsonContent) ?: throw IllegalStateException("صيغة النسخة الاحتياطية غير صالحة")

      // FIX: restore REPLACES the operational data inside a single transaction and
      // preserves locally stored PIN hashes (backups no longer contain them — see
      // uploadBackupToStorage). Previously rows were merged without clearing tables,
      // resurrecting deleted records.
      database.withTransaction {
        if (payload.labs.isNotEmpty() || payload.shipments.isNotEmpty()) {
          database.shipmentDao().deleteAllShipments()
          database.paymentDao().deleteAllPayments()
          database.labDao().deleteAllLabs()
          database.workTypeDao().deleteAllWorkTypes()
          database.labPriceDao().deleteAllPrices()
          database.inventoryDao().deleteAllItems()
          database.inventoryDao().deleteAllTransactions()
        }
        if (payload.labs.isNotEmpty()) database.labDao().insertAll(payload.labs)
        if (payload.workTypes.isNotEmpty()) database.workTypeDao().insertAll(payload.workTypes)
        if (payload.labPrices.isNotEmpty()) database.labPriceDao().insertAll(payload.labPrices)
        if (payload.shipments.isNotEmpty()) database.shipmentDao().insertAll(payload.shipments)
        if (payload.payments.isNotEmpty()) database.paymentDao().insertAll(payload.payments)
        if (payload.inventoryItems.isNotEmpty()) database.inventoryDao().insertAll(payload.inventoryItems)
        if (payload.inventoryTransactions.isNotEmpty()) database.inventoryDao().insertAllTransactions(payload.inventoryTransactions)
        if (payload.users.isNotEmpty()) {
          val existingPins = database.userDao().getAllSync().associate { it.id to it.pinCode }
          database.userDao().insertAll(payload.users.map { u ->
            if (u.pinCode.isBlank()) u.copy(pinCode = existingPins[u.id] ?: "") else u
          })
        }
      }

      _backupState.value = SyncState.SUCCESS
      val totalRestored = payload.totalRecordCount
      _statusMessage.value = "تمت استعادة النسخة الاحتياطية بنجاح ($totalRestored سجل: ${payload.shipments.size} إرسالية، ${payload.labs.size} معمل)."
      Result.success(totalRestored)
    } catch (e: Exception) {
      Log.e(TAG, "Error restoring from Firebase Storage: ${e.message}", e)
      _backupState.value = SyncState.ERROR
      _statusMessage.value = "فشل استعادة النسخة من Firebase Storage: ${e.localizedMessage ?: "خطأ أثناء المعالجة"}"
      Result.failure(e)
    }
  }

  /**
   * Debounced Auto-Backup Trigger called on every shipment, payment, or data modification.
   */
  fun triggerAutoBackupDebounced(
    coroutineScope: CoroutineScope,
    clinicId: String = CloudSyncManager.DEFAULT_CLINIC_ID,
    clinicName: String = CloudSyncManager.DEFAULT_CLINIC_NAME,
    currentUser: User? = null
  ) {
    if (!_isAutoBackupEnabled.value) return
    // SECURITY FIX: auto-backup (which contains payments/prices) is now restricted to
    // financial roles. Previously it fired for every role after each change and then
    // failed against the Storage security rules.
    val isFinancialUser = currentUser?.role?.canViewFinancials == true
    if (!isFinancialUser) return

    autoBackupJob?.cancel()
    autoBackupJob = coroutineScope.launch {
      delay(3000L) // 3 seconds debounce window to consolidate batch changes
      uploadBackupToStorage(
        clinicId = clinicId,
        clinicName = clinicName,
        currentUser = currentUser,
        isAutoBackup = true
      )
    }
  }
}
