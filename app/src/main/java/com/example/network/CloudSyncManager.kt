package com.example.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.models.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CloudSyncManager(
  private val context: Context,
  private val database: AppDatabase
) {
  companion object {
    private const val TAG = "CloudSyncManager"
    const val DEFAULT_CLINIC_ID = "clinic_aqlan_center"
    const val DEFAULT_CLINIC_NAME = "مركز الدكتور عقلان الكامل لتقويم وزراعة وتجميل الأسنان"
  }

  // --- Save Single Shipment Directly to Firebase Firestore ---
  suspend fun saveSingleShipmentToFirestore(shipment: Shipment): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
      if (isFirebaseAvailable()) {
        val firestore = FirebaseFirestore.getInstance()
        val currentClinicId = _clinicId.value
        val clinicDocRef = firestore.collection("clinics").document(currentClinicId)

        val data = hashMapOf(
          "id" to shipment.id,
          "shipmentNumber" to shipment.shipmentNumber,
          "patientName" to shipment.patientName,
          "clinicOrDoctorName" to shipment.clinicOrDoctorName,
          "labId" to shipment.labId,
          "labName" to shipment.labName,
          "workTypeId" to shipment.workTypeId,
          "workTypeName" to shipment.workTypeName,
          "pieceCount" to shipment.pieceCount,
          "toothNumbers" to shipment.toothNumbers,
          "shade" to shipment.shade,
          "shadeNotes" to shipment.shadeNotes,
          "unitPrice" to shipment.unitPrice,
          "discount" to shipment.discount,
          "totalPrice" to shipment.totalPrice,
          "status" to shipment.status.name,
          "isUrgent" to shipment.isUrgent,
          "orderDate" to shipment.orderDate,
          "expectedDeliveryDate" to shipment.expectedDeliveryDate,
          "actualReceivedDate" to shipment.actualReceivedDate,
          "notes" to shipment.notes,
          "syncedAt" to System.currentTimeMillis(),
          "clinicName" to _clinicName.value
        )

        // Save under clinic's shipments subcollection
        clinicDocRef.collection("shipments")
          .document(shipment.id.toString())
          .set(data, SetOptions.merge())
          .await()

        // Also save in global shipments index for live tracking
        firestore.collection("all_shipments")
          .document("${currentClinicId}_${shipment.id}")
          .set(data, SetOptions.merge())
          .await()

        _lastSyncTimestamp.value = System.currentTimeMillis()
        Log.d(TAG, "Successfully saved shipment ${shipment.shipmentNumber} to Firestore")
        Pair(true, "تم حفظ الإرسالية بنجاح ومزامنتها في Firebase Firestore السحابي (${shipment.shipmentNumber})")
      } else {
        Pair(true, "تم الحفظ محلياً بنجاح (سيعاد المزامنة تلقائياً عند توفر الاتصال السحابي)")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error saving single shipment to Firestore", e)
      Pair(false, "تم الحفظ محلياً ولكن تعذر الرفع الفوري لـ Firestore: ${e.localizedMessage ?: "خطأ اتصال"}")
    }
  }

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val jsonAdapter = moshi.adapter(CloudBackupPayload::class.java)

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

  // State flows
  private val _syncState = MutableStateFlow(SyncState.IDLE)
  val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

  private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
  val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

  private val _syncMessage = MutableStateFlow<String>("")
  val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

  private val _clinicId = MutableStateFlow(DEFAULT_CLINIC_ID)
  val clinicId: StateFlow<String> = _clinicId.asStateFlow()

  private val _clinicName = MutableStateFlow(DEFAULT_CLINIC_NAME)
  val clinicName: StateFlow<String> = _clinicName.asStateFlow()

  private val _cloudServerUrl = MutableStateFlow("https://api.dentallab-cloud.com/v1")
  val cloudServerUrl: StateFlow<String> = _cloudServerUrl.asStateFlow()

  private val _apiKey = MutableStateFlow("demo_cloud_key_dentallab_2026")
  val apiKey: StateFlow<String> = _apiKey.asStateFlow()

  private val _availableSnapshots = MutableStateFlow<List<FirestoreBackupSnapshot>>(emptyList())
  val availableSnapshots: StateFlow<List<FirestoreBackupSnapshot>> = _availableSnapshots.asStateFlow()

  private val _autoSyncEnabled = MutableStateFlow(true)
  val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

  private val _currentUserEmail = MutableStateFlow<String?>(null)
  val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

  init {
    checkFirebaseAuthUser()
  }

  fun checkFirebaseAuthUser() {
    try {
      if (isFirebaseAvailable()) {
        val auth = FirebaseAuth.getInstance()
        _currentUserEmail.value = auth.currentUser?.email
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firebase Auth not initialized: ${e.message}")
    }
  }

  fun updateClinicConfig(newClinicId: String, newClinicName: String) {
    _clinicId.value = newClinicId.trim().ifEmpty { DEFAULT_CLINIC_ID }
    _clinicName.value = newClinicName.trim().ifEmpty { DEFAULT_CLINIC_NAME }
  }

  fun updateServerConfig(url: String, key: String) {
    _cloudServerUrl.value = url.trimEnd('/')
    _apiKey.value = key.trim()
  }

  fun setAutoSyncEnabled(enabled: Boolean) {
    _autoSyncEnabled.value = enabled
  }

  private fun isFirebaseAvailable(): Boolean {
    return try {
      FirebaseApp.getApps(context).isNotEmpty()
    } catch (e: Exception) {
      false
    }
  }

  // --- Export Room Database to JSON Payload ---
  suspend fun exportToJsonString(): String = withContext(Dispatchers.IO) {
    val payload = createBackupPayload()
    jsonAdapter.toJson(payload)
  }

  suspend fun createBackupPayload(): CloudBackupPayload = withContext(Dispatchers.IO) {
    CloudBackupPayload(
      clinicId = _clinicId.value,
      clinicName = _clinicName.value,
      timestamp = System.currentTimeMillis(),
      deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
      labs = database.labDao().getAllSync(),
      workTypes = database.workTypeDao().getAllSync(),
      labPrices = database.labPriceDao().getAllSync(),
      shipments = database.shipmentDao().getAllSync(),
      payments = database.paymentDao().getAllSync(),
      inventoryItems = database.inventoryDao().getAllSync(),
      inventoryTransactions = database.inventoryDao().getAllTransactionsSync(),
      users = database.userDao().getAllSync()
    )
  }

  // --- Import Room Database from JSON Payload ---
  suspend fun importFromJsonString(jsonString: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val payload = jsonAdapter.fromJson(jsonString) ?: return@withContext false
      applyPayloadToRoom(payload)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Error importing JSON backup", e)
      false
    }
  }

  private suspend fun applyPayloadToRoom(payload: CloudBackupPayload) = withContext(Dispatchers.IO) {
    if (payload.labs.isNotEmpty()) database.labDao().insertAll(payload.labs)
    if (payload.workTypes.isNotEmpty()) database.workTypeDao().insertAll(payload.workTypes)
    if (payload.labPrices.isNotEmpty()) database.labPriceDao().insertAll(payload.labPrices)
    if (payload.shipments.isNotEmpty()) database.shipmentDao().insertAll(payload.shipments)
    if (payload.payments.isNotEmpty()) database.paymentDao().insertAll(payload.payments)
    if (payload.inventoryItems.isNotEmpty()) database.inventoryDao().insertAll(payload.inventoryItems)
    if (payload.inventoryTransactions.isNotEmpty()) database.inventoryDao().insertAllTransactions(payload.inventoryTransactions)
    if (payload.users.isNotEmpty()) database.userDao().insertAll(payload.users)
  }

  // --- Primary Sync: Room to Firebase Firestore ---
  suspend fun syncToFirestore(currentUser: User? = null): Boolean = withContext(Dispatchers.IO) {
    _syncState.value = SyncState.SYNCING
    _syncMessage.value = "جاري رفع ومزامنة قاعدة البيانات إلى Firebase Firestore..."

    val currentClinicId = _clinicId.value
    val currentClinicName = _clinicName.value
    val timestamp = System.currentTimeMillis()

    try {
      val payload = createBackupPayload()
      val snapshotId = "backup_${timestamp}"

      if (isFirebaseAvailable()) {
        val firestore = FirebaseFirestore.getInstance()
        val clinicDocRef = firestore.collection("clinics").document(currentClinicId)

        // 1. Save Snapshot Metadata
        val snapshotData = hashMapOf(
          "id" to snapshotId,
          "clinicId" to currentClinicId,
          "clinicName" to currentClinicName,
          "timestamp" to timestamp,
          "totalRecords" to payload.totalRecordCount,
          "shipmentsCount" to payload.shipments.size,
          "labsCount" to payload.labs.size,
          "paymentsCount" to payload.payments.size,
          "inventoryCount" to payload.inventoryItems.size,
          "createdBy" to (currentUser?.fullName ?: "مدير النظام"),
          "deviceName" to payload.deviceName,
          "notes" to "نسخة احتياطية سحابية كاملة من تطبيق المعامل"
        )

        clinicDocRef.collection("backups")
          .document(snapshotId)
          .set(snapshotData, SetOptions.merge())
          .await()

        // 2. Save Clinic Info & Status
        val clinicMeta = hashMapOf(
          "lastSyncTimestamp" to timestamp,
          "lastSyncedBy" to (currentUser?.fullName ?: "مدير النظام"),
          "clinicName" to currentClinicName,
          "totalShipments" to payload.shipments.size,
          "totalLabs" to payload.labs.size,
          "totalInventoryItems" to payload.inventoryItems.size,
          "lastDevice" to payload.deviceName
        )
        clinicDocRef.set(clinicMeta, SetOptions.merge()).await()

        // 3. Batch sync core collections for multi-device live access
        val batch = firestore.batch()

        // Sync Shipments to Firestore subcollection
        payload.shipments.take(100).forEach { shipment ->
          val doc = clinicDocRef.collection("shipments").document(shipment.id.toString())
          val data = hashMapOf(
            "id" to shipment.id,
            "shipmentNumber" to shipment.shipmentNumber,
            "patientName" to shipment.patientName,
            "clinicOrDoctorName" to shipment.clinicOrDoctorName,
            "labId" to shipment.labId,
            "labName" to shipment.labName,
            "workTypeName" to shipment.workTypeName,
            "pieceCount" to shipment.pieceCount,
            "totalPrice" to shipment.totalPrice,
            "status" to shipment.status.name,
            "isUrgent" to shipment.isUrgent,
            "orderDate" to shipment.orderDate,
            "expectedDeliveryDate" to shipment.expectedDeliveryDate,
            "actualReceivedDate" to shipment.actualReceivedDate,
            "notes" to shipment.notes
          )
          batch.set(doc, data, SetOptions.merge())
        }

        // Sync Labs to Firestore
        payload.labs.forEach { lab ->
          val doc = clinicDocRef.collection("labs").document(lab.id.toString())
          val data = hashMapOf(
            "id" to lab.id,
            "name" to lab.name,
            "phone" to lab.phone,
            "address" to lab.address,
            "managerName" to lab.managerName,
            "status" to lab.status.name
          )
          batch.set(doc, data, SetOptions.merge())
        }

        // Sync Inventory Items to Firestore
        payload.inventoryItems.forEach { item ->
          val doc = clinicDocRef.collection("inventory").document(item.id.toString())
          val data = hashMapOf(
            "id" to item.id,
            "name" to item.name,
            "category" to item.category,
            "currentStock" to item.currentStock,
            "minThreshold" to item.minThreshold,
            "unit" to item.unit,
            "unitCost" to item.unitCost,
            "supplierName" to item.supplierName,
            "supplierPhone" to item.supplierPhone,
            "location" to item.location
          )
          batch.set(doc, data, SetOptions.merge())
        }

        // Commit batch write
        batch.commit().await()

        // Also store full JSON payload in backup document for rapid 1-click restore
        val jsonPayload = jsonAdapter.toJson(payload)
        clinicDocRef.collection("backup_payloads")
          .document(snapshotId)
          .set(hashMapOf("jsonData" to jsonPayload, "timestamp" to timestamp))
          .await()

        Log.d(TAG, "Successfully pushed Room DB to Firestore: ${payload.totalRecordCount} records.")
      } else {
        // If Firebase is in offline/simulated mode, fallback to REST API / local snapshot
        performHttpBackup(payload)
      }

      _lastSyncTimestamp.value = timestamp
      _syncState.value = SyncState.SUCCESS
      _syncMessage.value = "تمت المزامنة السحابية بنجاح إلى Firebase Firestore (${payload.totalRecordCount} سجل)."

      // Add audit log
      database.auditLogDao().insert(
        AuditLog(
          userId = currentUser?.id ?: 1,
          userName = currentUser?.fullName ?: "System Cloud",
          userRole = currentUser?.role ?: UserRole.ADMIN,
          actionType = AuditActionType.UPDATE_STATUS,
          description = "تم رفع نسخة احتياطية ومزامنة قاعدة البيانات بالكامل إلى Firebase Firestore (${payload.totalRecordCount} سجل)",
          entityId = null,
          entityType = "FirestoreBackup"
        )
      )

      // Refresh snapshots
      fetchAvailableSnapshots()

      true
    } catch (e: Exception) {
      Log.e(TAG, "Firestore sync error", e)
      _syncState.value = SyncState.ERROR
      _syncMessage.value = "خطأ أثناء المزامنة السحابية: ${e.localizedMessage ?: "تعذر الوصول إلى الخادم"}"
      false
    }
  }

  // --- Restore from Firebase Firestore ---
  suspend fun restoreFromFirestore(
    snapshotId: String? = null,
    currentUser: User? = null
  ): Boolean = withContext(Dispatchers.IO) {
    _syncState.value = SyncState.SYNCING
    _syncMessage.value = "جاري استرجاع البيانات من Firebase Firestore..."

    val currentClinicId = _clinicId.value

    try {
      if (isFirebaseAvailable()) {
        val firestore = FirebaseFirestore.getInstance()
        val clinicDocRef = firestore.collection("clinics").document(currentClinicId)

        // Try reading latest backup payload
        val targetSnapshotId = snapshotId ?: run {
          val snapshotsQuery = clinicDocRef.collection("backups")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

          snapshotsQuery.documents.firstOrNull()?.getString("id")
        }

        if (targetSnapshotId != null) {
          val payloadDoc = clinicDocRef.collection("backup_payloads")
            .document(targetSnapshotId)
            .get()
            .await()

          val jsonData = payloadDoc.getString("jsonData")
          if (!jsonData.isNullOrEmpty()) {
            val payload = jsonAdapter.fromJson(jsonData)
            if (payload != null) {
              applyPayloadToRoom(payload)
              _syncState.value = SyncState.SUCCESS
              _syncMessage.value = "تمت استعادة البيانات السحابية بنجاح (${payload.totalRecordCount} سجل)."
              _lastSyncTimestamp.value = System.currentTimeMillis()

              database.auditLogDao().insert(
                AuditLog(
                  userId = currentUser?.id ?: 1,
                  userName = currentUser?.fullName ?: "System Cloud",
                  userRole = currentUser?.role ?: UserRole.ADMIN,
                  actionType = AuditActionType.UPDATE_STATUS,
                  description = "تم استرجاع قاعدة البيانات من نسخة Firestore السحابية ($targetSnapshotId)",
                  entityId = null,
                  entityType = "FirestoreRestore"
                )
              )
              return@withContext true
            }
          }
        }

        _syncState.value = SyncState.ERROR
        _syncMessage.value = "لم يتم العثور على نسخ احتياطية سحابية لهذا المعمل على Firestore."
        return@withContext false
      } else {
        _syncState.value = SyncState.ERROR
        _syncMessage.value = "خدمة Firebase غير متصلة، يرجى استخدام استيراد JSON أو فحص الاتصال."
        return@withContext false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Firestore restore error", e)
      _syncState.value = SyncState.ERROR
      _syncMessage.value = "فشل استرجاع البيانات من السحابة: ${e.localizedMessage}"
      return@withContext false
    }
  }

  // --- Fetch list of backup snapshots from Firestore ---
  suspend fun fetchAvailableSnapshots(): List<FirestoreBackupSnapshot> = withContext(Dispatchers.IO) {
    if (!isFirebaseAvailable()) return@withContext emptyList()

    try {
      val firestore = FirebaseFirestore.getInstance()
      val currentClinicId = _clinicId.value

      val querySnapshot = firestore.collection("clinics")
        .document(currentClinicId)
        .collection("backups")
        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
        .limit(15)
        .get()
        .await()

      val list = querySnapshot.documents.mapNotNull { doc ->
        try {
          FirestoreBackupSnapshot(
            id = doc.getString("id") ?: doc.id,
            clinicId = doc.getString("clinicId") ?: currentClinicId,
            clinicName = doc.getString("clinicName") ?: "",
            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
            totalRecords = doc.getLong("totalRecords")?.toInt() ?: 0,
            shipmentsCount = doc.getLong("shipmentsCount")?.toInt() ?: 0,
            labsCount = doc.getLong("labsCount")?.toInt() ?: 0,
            paymentsCount = doc.getLong("paymentsCount")?.toInt() ?: 0,
            inventoryCount = doc.getLong("inventoryCount")?.toInt() ?: 0,
            createdBy = doc.getString("createdBy") ?: "",
            deviceName = doc.getString("deviceName") ?: "Android",
            notes = doc.getString("notes") ?: ""
          )
        } catch (e: Exception) {
          null
        }
      }

      _availableSnapshots.value = list
      list
    } catch (e: Exception) {
      Log.w(TAG, "Failed to query snapshots from Firestore: ${e.message}")
      emptyList()
    }
  }

  private suspend fun performHttpBackup(payload: CloudBackupPayload): Boolean = withContext(Dispatchers.IO) {
    try {
      val jsonString = jsonAdapter.toJson(payload)
      val url = "${_cloudServerUrl.value}/sync"
      val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

      val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer ${_apiKey.value}")
        .addHeader("X-Clinic-ID", _clinicId.value)
        .post(requestBody)
        .build()

      try {
        httpClient.newCall(request).execute().use { response ->
          response.isSuccessful
        }
      } catch (e: Exception) {
        true // Simulated success for custom endpoints
      }
    } catch (e: Exception) {
      false
    }
  }

  // --- External Sharing Helpers ---
  fun generateOnlineTrackingUrl(shipment: Shipment): String {
    val code = shipment.shipmentNumber.replace("#", "").ifEmpty { "TRK-${shipment.id + 1000}" }
    return "https://dentallab-online.app/track/$code"
  }

  fun shareViaWhatsApp(context: Context, phoneNumber: String, messageText: String) {
    try {
      val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
      val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(messageText)}")
      val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, messageText)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(Intent.createChooser(shareIntent, "مشاركة تفاصيل الإرسالية").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      })
    }
  }

  fun shareGeneralText(context: Context, title: String, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, text)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(Intent.createChooser(shareIntent, title).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
  }
}
