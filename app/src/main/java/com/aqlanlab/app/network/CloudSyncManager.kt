package com.aqlanlab.app.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.aqlanlab.app.data.AppDatabase
import com.aqlanlab.app.data.models.*
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

  // --- Save Single Shipment Directly to Firebase Firestore with Financial Partitioning ---
  suspend fun saveSingleShipmentToFirestore(shipment: Shipment, isFinancialUser: Boolean = false): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
      if (isFirebaseAvailable()) {
        val firestore = FirebaseFirestore.getInstance()
        val currentClinicId = _clinicId.value
        val clinicDocRef = firestore.collection("clinics").document(currentClinicId)

        // 1. Pure Operational Data (NO FINANCIAL FIELDS -> Accessible to Staff/Technicians)
        val operationalData = hashMapOf(
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
          "status" to shipment.status.name,
          "isUrgent" to shipment.isUrgent,
          "orderDate" to shipment.orderDate,
          "expectedDeliveryDate" to shipment.expectedDeliveryDate,
          "actualReceivedDate" to shipment.actualReceivedDate,
          "notes" to shipment.notes,
          "syncedAt" to System.currentTimeMillis(),
          "clinicName" to _clinicName.value
        )

        // Save under clinic's operational shipments subcollection
        clinicDocRef.collection("shipments")
          .document(shipment.id.toString())
          .set(operationalData, SetOptions.merge())
          .await()

        // Also save in global operational index for live tracking
        firestore.collection("all_shipments")
          .document("${currentClinicId}_${shipment.id}")
          .set(operationalData, SetOptions.merge())
          .await()

        // 2. Financial Partition (Only written if user has financial privileges)
        if (isFinancialUser) {
          val financialData = hashMapOf(
            "shipmentId" to shipment.id.toString(),
            "shipmentNumber" to shipment.shipmentNumber,
            "unitPrice" to shipment.unitPrice,
            "discount" to shipment.discount,
            "totalPrice" to shipment.totalPrice,
            "syncedAt" to System.currentTimeMillis()
          )
          try {
            clinicDocRef.collection("shipment_finance")
              .document(shipment.id.toString())
              .set(financialData, SetOptions.merge())
              .await()
          } catch (e: Exception) {
            Log.w(TAG, "Financial partition sync skipped or restricted: ${e.message}")
          }
        }

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

  private val _autoSyncEnabled = MutableStateFlow(
    try {
      context.getSharedPreferences("aqlan_cloud_sync_prefs", Context.MODE_PRIVATE)
        .getBoolean("auto_sync_enabled", true)
    } catch (e: Exception) {
      true
    }
  )
  val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

  private val _currentUserEmail = MutableStateFlow<String?>(null)
  val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

  init {
    checkFirebaseAuthUser()
    // FIX: keep currentUserEmail fresh — previously read once at construction and
    // stayed stale/null after sign-in/sign-out.
    try {
      if (isFirebaseAvailable()) {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
          _currentUserEmail.value = auth.currentUser?.email
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "AuthStateListener not attached: ${e.message}")
    }
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
    // FIX: persist the toggle so it survives app restarts (previously reset to true)
    try {
      context.getSharedPreferences("aqlan_cloud_sync_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean("auto_sync_enabled", enabled).apply()
    } catch (e: Exception) {
      Log.w(TAG, "Persist autoSync failed: ${e.message}")
    }
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
      // SECURITY FIX: PIN hashes are stripped from every cloud/JSON backup payload.
      // Previously the full users table (including salted PIN hashes) was uploaded to
      // Firestore/Storage where ADMIN/ACCOUNTANT roles could download it, enabling
      // offline brute-forcing of 4-6 digit PINs.
      users = database.userDao().getAllSync().map { it.copy(pinCode = "") }
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
    // FIX: restore now REPLACES the operational data inside a single transaction.
    // Previously it merged rows (REPLACE by PK) without clearing tables, so restoring
    // an older backup resurrected records deleted after that backup. Local PIN hashes
    // are preserved whenever the payload arrives without them (backups are stripped of
    // PINs for security — see createBackupPayload).
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

        // 3. Batch sync core collections for multi-device live access.
        // FIX: previously `shipments.take(100)` and `payments.take(50)` silently synced
        // only a prefix of the data while the UI reported a "full sync". Now every
        // record is uploaded in Firestore-batch-sized chunks (limit 500 ops per batch).
        val isFinancialUser = currentUser?.role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.ACCOUNTANT)

        fun buildOperationalShipmentData(shipment: Shipment): Map<String, Any?> = hashMapOf(
          "id" to shipment.id,
          "shipmentNumber" to shipment.shipmentNumber,
          "patientName" to shipment.patientName,
          "clinicOrDoctorName" to shipment.clinicOrDoctorName,
          "labId" to shipment.labId,
          "labName" to shipment.labName,
          "workTypeName" to shipment.workTypeName,
          "pieceCount" to shipment.pieceCount,
          "status" to shipment.status.name,
          "isUrgent" to shipment.isUrgent,
          "orderDate" to shipment.orderDate,
          "expectedDeliveryDate" to shipment.expectedDeliveryDate,
          "actualReceivedDate" to shipment.actualReceivedDate,
          "notes" to shipment.notes
        )

        // Shipments: at most 2 ops per shipment (operational + financial partition)
        payload.shipments.chunked(200).forEach { chunk ->
          val batch = firestore.batch()
          chunk.forEach { shipment ->
            val doc = clinicDocRef.collection("shipments").document(shipment.id.toString())
            batch.set(doc, buildOperationalShipmentData(shipment), SetOptions.merge())

            // Financial Partition (only for financial roles)
            if (isFinancialUser) {
              val finDoc = clinicDocRef.collection("shipment_finance").document(shipment.id.toString())
              val finData = hashMapOf(
                "shipmentId" to shipment.id.toString(),
                "unitPrice" to shipment.unitPrice,
                "discount" to shipment.discount,
                "totalPrice" to shipment.totalPrice,
                "syncedAt" to timestamp
              )
              batch.set(finDoc, finData, SetOptions.merge())
            }
          }
          batch.commit().await()
        }

        // Payments (only if financial role)
        if (isFinancialUser) {
          payload.payments.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { payment ->
              val payDoc = clinicDocRef.collection("payments").document(payment.id.toString())
              val payData = hashMapOf(
                "id" to payment.id,
                "labId" to payment.labId,
                "labName" to payment.labName,
                "amount" to payment.amount,
                "currency" to payment.currency,
                "paymentDate" to payment.paymentDate,
                "paymentMethod" to payment.paymentMethod.name,
                "receiptNumber" to payment.receiptNumber,
                "notes" to payment.notes
              )
              batch.set(payDoc, payData, SetOptions.merge())
            }
            batch.commit().await()
          }
        }

        // Labs + Inventory (1 op per record)
        val miscRecords = payload.labs.map { lab ->
          clinicDocRef.collection("labs").document(lab.id.toString()) to hashMapOf(
            "id" to lab.id,
            "name" to lab.name,
            "phone" to lab.phone,
            "address" to lab.address,
            "managerName" to lab.managerName,
            "status" to lab.status.name
          )
        } + payload.inventoryItems.map { item ->
          clinicDocRef.collection("inventory").document(item.id.toString()) to hashMapOf(
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
        }
        miscRecords.chunked(450).forEach { chunk ->
          val batch = firestore.batch()
          chunk.forEach { (docRef, data) ->
            batch.set(docRef, data, SetOptions.merge())
          }
          batch.commit().await()
        }

        // Also store full JSON payload in backup document for rapid 1-click restore.
        // FIX: only for financial roles — the payload contains payments/prices and
        // Firestore rules restrict this collection to financial roles; writing it as a
        // non-financial user made every sync fail with permission-denied.
        if (isFinancialUser) {
          val jsonPayload = jsonAdapter.toJson(payload)
          clinicDocRef.collection("backup_payloads")
            .document(snapshotId)
            .set(hashMapOf("jsonData" to jsonPayload, "timestamp" to timestamp))
            .await()
        }

        Log.d(TAG, "Successfully pushed Room DB to Firestore: ${payload.totalRecordCount} records.")
      } else {
        // If Firebase is in offline/simulated mode, fallback to REST API / local snapshot
        val httpOk = performHttpBackup(payload)
        if (!httpOk) {
          _syncState.value = SyncState.ERROR
          _syncMessage.value = "تعذر الوصول إلى خادم النسخ الاحتياطي البديل (${_cloudServerUrl.value}). يرجى التحقق من الاتصال."
          return@withContext false
        }
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

      // FIX: report the REAL outcome. Previously any network exception returned
      // `true` ("Simulated success"), so the UI showed a successful backup while
      // nothing had been uploaded — a dangerous false sense of data safety.
      try {
        httpClient.newCall(request).execute().use { response ->
          response.isSuccessful
        }
      } catch (e: Exception) {
        Log.w(TAG, "HTTP backup endpoint unreachable: ${e.message}")
        false
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
