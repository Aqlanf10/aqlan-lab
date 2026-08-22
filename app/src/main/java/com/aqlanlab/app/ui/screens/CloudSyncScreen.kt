package com.aqlanlab.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.network.AutoBackupFrequency
import com.aqlanlab.app.network.FirebaseStorageBackupInfo
import com.aqlanlab.app.network.FirestoreBackupSnapshot
import com.aqlanlab.app.network.SyncState
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.ui.components.FirebaseStorageBackupSectionCard
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
  viewModel: DentalLabViewModel,
  onNavigateBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
  val syncState by viewModel.syncState.collectAsStateWithLifecycle()
  val lastSync by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
  val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
  val clinicId by viewModel.clinicId.collectAsStateWithLifecycle()
  val clinicName by viewModel.clinicName.collectAsStateWithLifecycle()
  val availableSnapshots by viewModel.availableSnapshots.collectAsStateWithLifecycle()
  val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsStateWithLifecycle()
  val currentUserEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()
  val activeUser = viewModel.activeUser.collectAsStateWithLifecycle().value ?: viewModel.getActiveUserSafe()

  val allShipments by viewModel.allShipments.collectAsStateWithLifecycle()
  val allLabs by viewModel.allLabs.collectAsStateWithLifecycle()
  val allInventoryItems by viewModel.allInventoryItems.collectAsStateWithLifecycle()

  // Firebase Storage State Flows
  val storageBackupState by viewModel.storageBackupState.collectAsStateWithLifecycle()
  val storageStatusMessage by viewModel.storageStatusMessage.collectAsStateWithLifecycle()
  val lastStorageBackupTimestamp by viewModel.lastStorageBackupTimestamp.collectAsStateWithLifecycle()
  val availableStorageBackups by viewModel.availableStorageBackups.collectAsStateWithLifecycle()
  val isAutoStorageBackupEnabled by viewModel.isAutoStorageBackupEnabled.collectAsStateWithLifecycle()
  val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()

  var showClinicIdDialog by remember { mutableStateOf(false) }
  var clinicIdInput by remember(clinicId) { mutableStateOf(clinicId) }
  var clinicNameInput by remember(clinicName) { mutableStateOf(clinicName) }

  var showExportDialog by remember { mutableStateOf(false) }
  var exportedJsonText by remember { mutableStateOf("") }
  var showImportDialog by remember { mutableStateOf(false) }
  var importJsonInput by remember { mutableStateOf("") }

  var snapshotToRestore by remember { mutableStateOf<FirestoreBackupSnapshot?>(null) }
  var showRestoreConfirmDialog by remember { mutableStateOf(false) }

  var storageBackupToRestore by remember { mutableStateOf<FirebaseStorageBackupInfo?>(null) }
  var showStorageRestoreConfirmDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    viewModel.refreshFirestoreSnapshots()
    viewModel.fetchStorageBackups()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("المزامنة السحابية (Firebase Firestore)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
              if (isOnline) "🟢 متصل بالسحابة (Firebase Online)" else "🔴 غير متصل - حفظ محلي (Offline)",
              fontSize = 12.sp,
              color = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          IconButton(
            onClick = { showClinicIdDialog = true },
            modifier = Modifier.testTag("clinic_workspace_config_btn")
          ) {
            Icon(Icons.Default.CloudSync, contentDescription = "معرف السحابة")
          }
          IconButton(
            onClick = { viewModel.refreshFirestoreSnapshots() },
            modifier = Modifier.testTag("refresh_snapshots_btn")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "تحديث السحابة")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Connection & Cloud Identity Banner Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = if (isOnline) Color(0xFFEFF6FF) else Color(0xFFFEF2F2)
          ),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(if (isOnline) Color(0xFF2563EB) else MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(26.dp)
                )
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  if (isOnline) "سحابة فايربيس متصلة (Firebase Firestore)" else "أنت تعمل بدون اتصال بالإنترنت",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = if (isOnline) Color(0xFF1E40AF) else Color(0xFF991B1B)
                )
                Text(
                  text = "المساحة السحابية: $clinicName",
                  fontSize = 12.sp,
                  color = if (isOnline) Color(0xFF1E3A8A) else Color(0xFF7F1D1D),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOnline) Color(0xFFDBEAFE) else Color(0xFFFEE2E2),
                modifier = Modifier.clickable { showClinicIdDialog = true }
              ) {
                Text(
                  text = "كود الربط",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isOnline) Color(0xFF1E40AF) else Color(0xFF991B1B),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            HorizontalDivider(color = if (isOnline) Color(0xFFBFDBFE) else Color(0xFFFECACA))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Text(
                  text = "معرف العيادة: $clinicId",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.DarkGray,
                  fontWeight = FontWeight.SemiBold
                )
              }

              if (currentUserEmail != null) {
                Text(
                  text = "👤 $currentUserEmail",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.DarkGray
                )
              } else {
                Text(
                  text = "👤 ${activeUser.fullName}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.DarkGray
                )
              }
            }
          }
        }
      }

      // 2. Enterprise Firebase Cloud Storage Automatic Backup Card
      item {
        FirebaseStorageBackupSectionCard(
          isOnline = isOnline,
          backupState = storageBackupState,
          statusMessage = storageStatusMessage,
          lastBackupTimestamp = lastStorageBackupTimestamp,
          isAutoBackupEnabled = isAutoStorageBackupEnabled,
          autoBackupFrequency = autoBackupFrequency,
          onAutoBackupToggle = { viewModel.setAutoStorageBackupEnabled(it) },
          onAutoBackupFrequencyChange = { viewModel.setAutoBackupFrequency(it) },
          onTriggerBackup = {
            viewModel.uploadBackupToStorage(isAuto = false) { success, msg ->
              Toast.makeText(context, msg, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
          },
          availableBackups = availableStorageBackups,
          onRestoreBackup = { backup ->
            storageBackupToRestore = backup
            showStorageRestoreConfirmDialog = true
          },
          onRefreshBackups = {
            viewModel.fetchStorageBackups()
            Toast.makeText(context, "تم تحديث أرشيف النسخ السحابية", Toast.LENGTH_SHORT).show()
          }
        )
      }

      // 3. Primary Cloud Sync & Backup Actions Card (Firestore Multi-Device)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("المزامنة والنسخ الاحتياطي السحابي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                  if (lastSync != null)
                    "آخر مزامنة: ${DateUtils.formatDateTime(lastSync!!)}"
                  else
                    "لم تتم المزامنة بعد",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              if (syncState == SyncState.SYNCING) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp))
              } else if (syncState == SyncState.SUCCESS) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
              }
            }

            if (syncMessage.isNotEmpty()) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (syncState == SyncState.ERROR) MaterialTheme.colorScheme.errorContainer else Color(0xFFDCFCE7),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = syncMessage,
                  fontSize = 12.sp,
                  color = if (syncState == SyncState.ERROR) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF166534),
                  modifier = Modifier.padding(10.dp)
                )
              }
            }

            // Stats summary chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("الإرساليات", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                  Text("${allShipments.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("المعامل", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                  Text("${allLabs.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("المخزون", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                  Text("${allInventoryItems.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
              }
            }

            // Primary Sync to Firestore Button
            Button(
              onClick = {
                viewModel.syncShipmentsToFirestore { success, msg ->
                  Toast.makeText(context, msg, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("sync_to_cloud_button"),
              enabled = syncState != SyncState.SYNCING
            ) {
              Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(8.dp))
              Text("مزامنة الإرساليات مع السحابة (Sync to Cloud)", fontWeight = FontWeight.Bold)
            }

            // Auto-Sync Toggle Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.SyncLock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("المزامنة التلقائية عبر الأجهزة", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
              }
              Switch(
                checked = autoSyncEnabled,
                onCheckedChange = { viewModel.setAutoSyncEnabled(it) },
                modifier = Modifier.testTag("auto_sync_switch")
              )
            }

            // Restore from Latest Cloud State Button
            OutlinedButton(
              onClick = {
                snapshotToRestore = null
                showRestoreConfirmDialog = true
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("restore_from_cloud_button"),
              enabled = syncState != SyncState.SYNCING
            ) {
              Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("استرجاع أحدث بيانات من Firestore")
            }
          }
        }
      }

      // 3. Multi-Device Setup Info Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "الربط متعدد الأجهزة (Multi-Device Sync)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
              Text(
                text = "لمزامنة هذا المعمل مع أجهزة الأطباء وموظفي الاستقبال، استخدم نفس كود المعمل ($clinicId) في كافة الأجهزة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
            IconButton(
              onClick = {
                val shareText = """
                  🏥 كود مزامنة معمل الأسنان السحابي:
                  🏢 اسم المركز: $clinicName
                  🔑 معرف الربط السحابي (Clinic ID): $clinicId
                  📲 أدخل هذا المعرف في شاشة المزامنة السحابية لمشاركة كافة البيانات والإرساليات مباشرة.
                """.trimIndent()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Clinic ID", shareText))
                Toast.makeText(context, "تم نسخ كود الربط السحابي!", Toast.LENGTH_SHORT).show()
              }
            ) {
              Icon(Icons.Default.Share, contentDescription = "مشاركة الكود", tint = MaterialTheme.colorScheme.tertiary)
            }
          }
        }
      }

      // 4. Firestore Snapshots History List
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            "نقاط الاسترجاع والنسخ السحابية (${availableSnapshots.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
          TextButton(onClick = { viewModel.refreshFirestoreSnapshots() }) {
            Text("تحديث القائمة")
          }
        }
      }

      if (availableSnapshots.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
              Text("لا توجد نسخ سحابية محفوظة حتى الآن على Firestore.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
              Text("اضغط على 'مزامنة ورفع إلى Firestore' لإنشاء أول نقطة استرجاع سحابية.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)
            }
          }
        }
      } else {
        items(availableSnapshots) { snapshot ->
          FirestoreSnapshotCard(
            snapshot = snapshot,
            onRestore = {
              snapshotToRestore = snapshot
              showRestoreConfirmDialog = true
            }
          )
        }
      }

      // 5. Offline JSON File Backup (Redundancy)
      item {
        Text(
          "النسخ الاحتياطي المحلي والتصدير اليدوي (JSON)",
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              coroutineScope.launch {
                exportedJsonText = viewModel.exportDataJson()
                showExportDialog = true
              }
            },
            modifier = Modifier
              .weight(1f)
              .testTag("export_backup_button")
          ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("تصدير JSON", fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = { showImportDialog = true },
            modifier = Modifier
              .weight(1f)
              .testTag("import_backup_button")
          ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("استيراد JSON", fontSize = 12.sp)
          }
        }
      }

      // 6. Online Tracking & WhatsApp Dispatch Header
      item {
        Text(
          "روابط تتبع العيادات عبر الإنترنت (Live Tracking Links)",
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      }

      if (allShipments.isEmpty()) {
        item {
          Text(
            "لا توجد إرساليات حالياً لإنشاء روابط تتبع لها.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        items(allShipments.take(5)) { shipment ->
          val lab = allLabs.find { it.id == shipment.labId }
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  "المريض: ${shipment.patientName} (${shipment.workTypeName})",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 14.sp
                )
                Text(
                  "المعمل: ${shipment.labName} | كود: ${shipment.shipmentNumber}",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              // WhatsApp Share
              IconButton(
                onClick = { viewModel.sendShipmentToWhatsApp(context, shipment, lab) },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  Icons.Default.Share,
                  contentDescription = "مشاركة عبر واتساب",
                  tint = Color(0xFF25D366)
                )
              }

              // Copy Link
              IconButton(
                onClick = {
                  val url = viewModel.cloudSyncManager.generateOnlineTrackingUrl(shipment)
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Tracking Link", url))
                  Toast.makeText(context, "تم نسخ رابط التتبع السحابي!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(Icons.Default.Link, contentDescription = "نسخ الرابط", tint = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }
  }

  // --- Dialogs ---

  // 1. Clinic Cloud Workspace Config Dialog
  if (showClinicIdDialog) {
    AlertDialog(
      onDismissRequest = { showClinicIdDialog = false },
      title = { Text("إعداد مساحة السحابة (Cloud Workspace)", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "أدخل معرف العيادة (Clinic ID) لربط ومزامنة هذا الجهاز مع باقي أجهزة المركز عبر Firebase Firestore:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = clinicIdInput,
            onValueChange = { clinicIdInput = it.replace(" ", "_") },
            label = { Text("معرف السحابة (Clinic ID) *") },
            placeholder = { Text("مثال: clinic_elite_01") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_clinic_id")
          )
          OutlinedTextField(
            value = clinicNameInput,
            onValueChange = { clinicNameInput = it },
            label = { Text("اسم المركز / العيادة") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_clinic_name")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateClinicConfig(clinicIdInput, clinicNameInput)
            showClinicIdDialog = false
            Toast.makeText(context, "تم تحديث معرف السحابة وتحديث نقاط الاسترجاع", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.testTag("save_clinic_config_btn")
        ) {
          Text("حفظ وتحديث")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClinicIdDialog = false }) {
          Text("إلغاء")
        }
      }
    )
  }

  // 2. Restore Confirmation Dialog
  if (showRestoreConfirmDialog) {
    val targetSnapshot = snapshotToRestore
    AlertDialog(
      onDismissRequest = {
        showRestoreConfirmDialog = false
        snapshotToRestore = null
      },
      icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B)) },
      title = { Text("تأكيد استرجاع البيانات من السحابة", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("هل أنت متأكد من استرجاع البيانات من Firestore؟")
          if (targetSnapshot != null) {
            Text(
              "نقطة الاسترجاع: ${DateUtils.formatDateTime(targetSnapshot.timestamp)}\nبواسطة: ${targetSnapshot.createdBy} (${targetSnapshot.totalRecords} سجل)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold
            )
          } else {
            Text("سيتم تحميل أحدث نسخة سحابية متوفرة لهذا المعمل.", style = MaterialTheme.typography.bodySmall)
          }
          Text(
            "تنبيه: سيتم دمج وتحديث السجلات في قاعدة البيانات المحلية.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showRestoreConfirmDialog = false
            viewModel.restoreFromFirestore(targetSnapshot?.id) { success ->
              if (success) {
                Toast.makeText(context, "تمت استعادة البيانات السحابية بنجاح!", Toast.LENGTH_LONG).show()
              } else {
                Toast.makeText(context, "تعذر استرجاع البيانات السحابية", Toast.LENGTH_SHORT).show()
              }
              snapshotToRestore = null
            }
          },
          modifier = Modifier.testTag("confirm_restore_btn")
        ) {
          Text("تأكيد الاسترجاع")
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showRestoreConfirmDialog = false
          snapshotToRestore = null
        }) {
          Text("إلغاء")
        }
      }
    )
  }

  // 2.5 Firebase Storage Backup Restore Confirmation Dialog
  if (showStorageRestoreConfirmDialog && storageBackupToRestore != null) {
    val backup = storageBackupToRestore!!
    val dateFormatted = remember(backup.timestamp) {
      java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(backup.timestamp))
    }

    AlertDialog(
      onDismissRequest = {
        showStorageRestoreConfirmDialog = false
        storageBackupToRestore = null
      },
      icon = {
        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(32.dp))
      },
      title = {
        Text("استعادة النسخة الاحتياطية من Firebase Storage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("هل أنت متأكد من استعادة هذه النسخة السحابية المحددة؟")

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("📁 الملف: ${backup.fileName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
              Text("📅 التاريخ: $dateFormatted", style = MaterialTheme.typography.labelSmall)
              Text("💾 الحجم: ${backup.formattedSize}", style = MaterialTheme.typography.labelSmall)
              Text("👤 المسؤول: ${backup.createdByName}", style = MaterialTheme.typography.labelSmall)
              Text("📊 البيانات: ${backup.shipmentsCount} إرسالية/مريض • ${backup.labsCount} معمل • ${backup.totalRecords} إجمالي السجلات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
          }

          Text(
            "⚠️ تنبيه: سيتم تحديث ودمج هذه السجلات في قاعدة البيانات دون حذف السجلات غير المتطابقة.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showStorageRestoreConfirmDialog = false
            viewModel.restoreFromStorageBackup(backup) { success, msg ->
              Toast.makeText(context, msg, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
              storageBackupToRestore = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
          modifier = Modifier.testTag("confirm_storage_restore_btn")
        ) {
          Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("بدء الاستعادة الآن")
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showStorageRestoreConfirmDialog = false
          storageBackupToRestore = null
        }) {
          Text("إلغاء")
        }
      }
    )
  }

  // 3. Export JSON Backup Dialog
  if (showExportDialog) {
    AlertDialog(
      onDismissRequest = { showExportDialog = false },
      title = { Text("النسخة الاحتياطية السحابية (JSON)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("تم تجهيز كامل بيانات المعامل والمخزون والإرساليات بتنسيق سحابي JSON:", fontSize = 12.sp)
          OutlinedTextField(
            value = exportedJsonText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            textStyle = MaterialTheme.typography.bodySmall
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Backup JSON", exportedJsonText))
            Toast.makeText(context, "تم نسخ النسخة السحابية إلى الحافظة!", Toast.LENGTH_SHORT).show()
            showExportDialog = false
          }
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("نسخ البيانات")
        }
      },
      dismissButton = {
        TextButton(onClick = { showExportDialog = false }) {
          Text("إغلاق")
        }
      }
    )
  }

  // 4. Import JSON Backup Dialog
  if (showImportDialog) {
    AlertDialog(
      onDismissRequest = { showImportDialog = false },
      title = { Text("استيراد بيانات من السحابة (Restore JSON)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("الصق نص النسخة الاحتياطية السحابية هنا لاستعادة كافة البيانات:", fontSize = 12.sp)
          OutlinedTextField(
            value = importJsonInput,
            onValueChange = { importJsonInput = it },
            placeholder = { Text("{\"version\":2, \"labs\":[...]}") },
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            textStyle = MaterialTheme.typography.bodySmall
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (importJsonInput.isNotBlank()) {
              viewModel.importDataJson(importJsonInput) { success ->
                if (success) {
                  Toast.makeText(context, "تم استيراد البيانات السحابية بنجاح!", Toast.LENGTH_LONG).show()
                  showImportDialog = false
                } else {
                  Toast.makeText(context, "فشل استيراد البيانات، تأكد من صحة التنسيق.", Toast.LENGTH_LONG).show()
                }
              }
            }
          }
        ) {
          Text("استعادة البيانات")
        }
      },
      dismissButton = {
        TextButton(onClick = { showImportDialog = false }) {
          Text("إلغاء")
        }
      }
    )
  }
}

@Composable
fun FirestoreSnapshotCard(
  snapshot: FirestoreBackupSnapshot,
  onRestore: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          shape = CircleShape,
          color = Color(0xFFEFF6FF),
          modifier = Modifier.size(38.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
          }
        }

        Column {
          Text(
            text = DateUtils.formatDateTime(snapshot.timestamp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "المسؤول: ${snapshot.createdBy.ifEmpty { "مدير النظام" }} | الجهاز: ${snapshot.deviceName}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
          )
          Text(
            text = "📦 إرساليات: ${snapshot.shipmentsCount} | 🏢 معامل: ${snapshot.labsCount} | 🧪 مواد: ${snapshot.inventoryCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Button(
        onClick = onRestore,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("استرجاع", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}
