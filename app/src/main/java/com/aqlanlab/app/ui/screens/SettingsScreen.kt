package com.aqlanlab.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.BuildConfig
import com.aqlanlab.app.data.models.User
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.network.AppUpdateStatus
import com.aqlanlab.app.network.AppVersionConfig
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.ui.components.RoleBadge
import com.aqlanlab.app.ui.theme.RoleAdminColor
import com.aqlanlab.app.ui.theme.RoleStaffColor
import com.aqlanlab.app.ui.theme.RoleAccountantColor
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: DentalLabViewModel,
  onNavigateToWorkTypes: () -> Unit,
  onNavigateToInventory: () -> Unit = {},
  onNavigateToAnalytics: () -> Unit = {},
  onNavigateToAuditLog: () -> Unit,
  onNavigateToCloudSync: () -> Unit,
  onNavigateToUserManagement: () -> Unit,
  onNavigateToMessagingGateways: () -> Unit = {},
  onOpenUserSwitchDialog: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val isOnline by viewModel.isOnline.collectAsState()
  val syncState by viewModel.syncState.collectAsState()
  val activeUser = viewModel.activeUser.collectAsState().value ?: viewModel.getActiveUserSafe()
  val currency by viewModel.currency.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val labSummaries by viewModel.labAccountSummaries.collectAsState()
  val lowStockCount by viewModel.lowStockCount.collectAsState()

  var showClearDemoConfirm by remember { mutableStateOf(false) }
  var showWipeTransactionsConfirm by remember { mutableStateOf(false) }
  var showFactoryResetConfirm by remember { mutableStateOf(false) }
  var showResetDemoConfirm by remember { mutableStateOf(false) }
  var enteredPin by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf("") }

  var showExportDialog by remember { mutableStateOf(false) }
  var exportTextContent by remember { mutableStateOf("") }
  var exportTitle by remember { mutableStateOf("") }
  var snackbarMessage by remember { mutableStateOf("") }

  val appLockEnabled by viewModel.appLockEnabled.collectAsState()
  val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
  var showChangePinDialog by remember { mutableStateOf(false) }
  var newDoctorPin by remember { mutableStateOf("") }
  var confirmDoctorPin by remember { mutableStateOf("") }
  var changePinError by remember { mutableStateOf("") }

  // App In-App Update States
  val updateStatus by viewModel.updateStatus.collectAsState()
  val versionConfig by viewModel.versionConfig.collectAsState()
  var isCheckingUpdatesManually by remember { mutableStateOf(false) }
  var showPublishVersionDialog by remember { mutableStateOf(false) }
  var pubLatestVersionName by remember(versionConfig) { mutableStateOf(versionConfig.latestVersionName) }
  var pubLatestVersionCode by remember(versionConfig) { mutableStateOf(versionConfig.latestVersionCode.toString()) }
  var pubMinVersionCode by remember(versionConfig) { mutableStateOf(versionConfig.minimumSupportedVersionCode.toString()) }
  var pubUpdateTitle by remember(versionConfig) { mutableStateOf(versionConfig.updateTitleAr) }
  var pubUpdateMessage by remember(versionConfig) { mutableStateOf(versionConfig.updateMessageAr) }
  var pubReleaseNotes by remember(versionConfig) { mutableStateOf(versionConfig.releaseNotesAr) }
  var pubUpdateUrl by remember(versionConfig) { mutableStateOf(versionConfig.updateUrl) }
  var isMandatorySwitch by remember(versionConfig) { mutableStateOf(versionConfig.isMandatoryUpdate) }
  var isPublishingVersion by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "إعدادات النظام والمستخدمين",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
      // 0. Official Center Profile & Logo Banner
      item {
        com.aqlanlab.app.ui.components.AqlanClinicHeaderCard()
      }

      // 1. Current Active User Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(activeUser.avatarColor)),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = activeUser.fullName.take(1),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = activeUser.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              RoleBadge(role = activeUser.role)
              Text(
                text = when (activeUser.role) {
                  UserRole.SUPER_ADMIN -> "المشرف العام والمالك (تحكم كامل بالحسابات والتراخيص والأسعار)"
                  UserRole.ADMIN -> "مدير النظام (إدارة العمليات والأسعار والحسابات)"
                  UserRole.STAFF -> "صلاحيات تشغيلية فقط (الأسعار مخفية ومحمية)"
                  UserRole.ACCOUNTANT -> "صلاحيات مالية ومحاسبية"
                  UserRole.TECHNICIAN -> "فني معمل (متابعة وتحديث حالة الأعمال)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            FilledTonalButton(onClick = onOpenUserSwitchDialog) {
              Text("تبديل")
            }
          }
        }
      }

      // 1.5. Exclusive Doctor Ownership & App Lock Security Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
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
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2563EB))
                Text(
                  text = "أمان وقفل التطبيق الحصري",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1E3A8A)
                )
              }
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE0E7FF)
              ) {
                Text(
                  text = "حماية المالك 🔒",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1D4ED8),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            Text(
              text = "هذا البرنامج محمي ومرخص حصرياً لـ ${com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME} (${com.aqlanlab.app.ui.components.ClinicInfo.CLINIC_NAME}). لا يمكن لأي شخص فتح التطبيق أو كشف الأسعار والحسابات بدون إذنك.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Switch: Require PIN on app launch
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("قفل التطبيق عند الفتح (App Lock)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("طلب الرمز السري دائماً عند تشغيل البرنامج", fontSize = 11.sp, color = Color.Gray)
              }
              Switch(
                checked = appLockEnabled,
                onCheckedChange = { viewModel.setAppLockEnabled(it) }
              )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Firebase Auth Account info & Sign Out
            val firebaseUser by viewModel.firebaseCurrentUser.collectAsState()
            if (firebaseUser != null) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text("حساب Firebase النشط:", fontSize = 11.sp, color = Color.Gray)
                    Text(firebaseUser?.email ?: "غير محدد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                  OutlinedButton(
                    onClick = { viewModel.signOutFirebase() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                  ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تسجيل الخروج", fontSize = 11.sp)
                  }
                }
              }
            }

            // Exclusive Owner Sync to Cloud & Backup Action
            if (activeUser.role == UserRole.SUPER_ADMIN || activeUser.role == UserRole.ADMIN) {
              val syncState by viewModel.syncState.collectAsState()
              var isOwnerSyncing by remember { mutableStateOf(false) }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
                    Text(
                      text = "مزامنة ونسخ الإرساليات إلى السحابة (Sync to Cloud)",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = Color(0xFF1E40AF)
                    )
                  }
                  Text(
                    text = "رفع وحفظ قاعدة بيانات الإرساليات المحلية (Room) إلى خوادم Firebase Firestore السحابية فوراً لحمايتها من الفقدان ومشاركتها مع الأجهزة المصرحة.",
                    fontSize = 11.sp,
                    color = Color(0xFF1E3A8A),
                    lineHeight = 16.sp
                  )
                  Button(
                    onClick = {
                      isOwnerSyncing = true
                      viewModel.syncShipmentsToFirestore { success, msg ->
                        isOwnerSyncing = false
                        android.widget.Toast.makeText(context, msg, if (success) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
                      }
                    },
                    enabled = syncState != com.aqlanlab.app.network.SyncState.SYNCING && !isOwnerSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("owner_sync_to_cloud_btn")
                  ) {
                    if (syncState == com.aqlanlab.app.network.SyncState.SYNCING || isOwnerSyncing) {
                      CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                      Spacer(Modifier.width(8.dp))
                      Text("جاري المزامنة السحابية لـ Firestore...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                      Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("مزامنة الإرساليات مع السحابة الآن (Sync to Cloud)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                  }
                }
              }
            }

            // Exclusive Owner Mock Data Wipe Quick Action
            if (activeUser.role == UserRole.SUPER_ADMIN || activeUser.role == UserRole.ADMIN) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF3C7),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                    Text(
                      text = "خاص بمالك البرنامج: مسح البيانات التجريبية",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = Color(0xFF92400E)
                    )
                  }
                  Text(
                    text = "يمسح كل الإرساليات والمدفوعات وسندات الصرف الوهمية التي تم تسجيلها للتجربة، مع الاحتفاظ الكامل بقائمة المعامل، والأسعار، وأنواع الأعمال، وإعدادات المستخدمين للبدء بسجلات نظيفة فوراً.",
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 16.sp
                  )
                  Button(
                    onClick = {
                      enteredPin = ""
                      pinError = ""
                      showClearDemoConfirm = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("owner_clear_mock_data_btn")
                  ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مسح البيانات الوهمية والتجريبية الآن", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Lock App Now button
              Button(
                onClick = { viewModel.lockApp() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("قفل البرنامج الآن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }

              // Change Doctor Master PIN button
              OutlinedButton(
                onClick = {
                  newDoctorPin = ""
                  confirmDoctorPin = ""
                  changePinError = ""
                  showChangePinDialog = true
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.2f)
              ) {
                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("تغيير رمز المرور", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // 2. Currency Selector Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                text = "العملة الأساسية للنظام",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf("USD" to "دولار ($)", "SAR" to "ريال سعودي (ر.س)", "YER" to "ريال يمني (ر.ي)").forEach { (code, label) ->
                FilterChip(
                  selected = currency == code,
                  onClick = { viewModel.setCurrency(code) },
                  label = { Text(label, fontWeight = FontWeight.Bold) },
                  modifier = Modifier.testTag("currency_$code")
                )
              }
            }
          }
        }
      }

      // 2.5. Local Notification Settings Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "نظام الإشعارات والتنبيهات المحلية",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }
              Switch(
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
              )
            }

            Text(
              text = "إرسال إشعارات فورية على شريط الهاتف عند إضافة إرسالية جديدة، تغيير حالة طلب معملي، وتنبيهات تلقائية ذكية عند اقتراب تاريخ استحقاق تسليم أي إرسالية مسجلة في قاعدة بيانات Room (خلال 24-48 ساعة أو عند التأخير).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  viewModel.checkDueDeliveriesNow { count ->
                    if (count > 0) {
                      android.widget.Toast.makeText(context, "تم العثور على ($count) إرسالية قريبة أو متأخرة وإرسال التنبيهات ⏰", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                      // Trigger sample delivery reminder for testing
                      com.aqlanlab.app.util.NotificationHelper.showDeliveryApproachingNotification(
                        context = context,
                        shipment = com.aqlanlab.app.data.models.Shipment(
                          id = 8888,
                          shipmentNumber = "AQL-DUE-ALERT",
                          clinicOrDoctorName = com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME,
                          patientName = "مريض موعد التسليم اليوم (تجربة)",
                          labId = 1,
                          labName = "معمل النخبة للأسنان",
                          workTypeId = 1,
                          workTypeName = "جسر زركونيا 4 قطع",
                          pieceCount = 4,
                          toothNumbers = "13-16",
                          shade = "A1",
                          expectedDeliveryDate = System.currentTimeMillis() + 3600000L * 5, // 5 hours from now
                          isUrgent = false,
                          status = com.aqlanlab.app.data.models.ShipmentStatus.IN_PROGRESS
                        ),
                        hoursRemaining = 5,
                        isOverdue = false
                      )
                      android.widget.Toast.makeText(context, "تم إرسال إشعار تجريبي لاقتراب موعد التسليم ⏰", android.widget.Toast.LENGTH_SHORT).show()
                    }
                  }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.2f).testTag("check_due_deliveries_btn")
              ) {
                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFD97706))
                Spacer(Modifier.width(4.dp))
                Text("فحص مواعيد التسليم ⏰", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }

              OutlinedButton(
                onClick = {
                  com.aqlanlab.app.util.NotificationHelper.showNewShipmentNotification(
                    context = context,
                    shipment = com.aqlanlab.app.data.models.Shipment(
                      id = 9999,
                      shipmentNumber = "AQL-TEST",
                      clinicOrDoctorName = com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME,
                      patientName = "مريض تجريبي (فحص الإشعارات)",
                      labId = 1,
                      labName = "معمل النخبة للأسنان",
                      workTypeId = 1,
                      workTypeName = "زركونيا متجانس (Monolithic Zirconia)",
                      pieceCount = 3,
                      toothNumbers = "11, 12, 21",
                      shade = "A2",
                      expectedDeliveryDate = System.currentTimeMillis() + 86400000L * 2,
                      isUrgent = true,
                      status = com.aqlanlab.app.data.models.ShipmentStatus.NEW
                    ),
                    createdByName = activeUser.fullName
                  )
                  android.widget.Toast.makeText(context, "تم إرسال إشعار تجريبي بنجاح 🔔", android.widget.Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("إشعار تجريبي", fontSize = 11.sp)
              }
            }
          }
        }
      }

      // 3. Quick System Catalog Links
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            SettingsMenuItem(
              title = "إدارة المستخدمين وكلمات المرور (User Management)",
              subtitle = "إضافة مستخدمين، صلاحيات الوصول، وتعيين كلمات السر",
              icon = Icons.Default.ManageAccounts,
              onClick = onNavigateToUserManagement
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsMenuItem(
              title = "بوابات الرسائل القصيرة والواتساب (SMS & WhatsApp Gateway)",
              subtitle = "ربط يمن موبايل، اسم المرسل المعتمد (Sender ID)، وبوابات الواتساب السحابية",
              icon = Icons.Default.Sms,
              onClick = onNavigateToMessagingGateways
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsMenuItem(
              title = "الرسوم البيانية والتحليلات (Charts & Trend Analytics)",
              subtitle = "منحنيات الإيرادات، حجم الإرساليات وتوزيع الأعمال",
              icon = Icons.Default.ShowChart,
              onClick = onNavigateToAnalytics
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsMenuItem(
              title = "المخزون والمواد السنية (Dental Supplies)",
              subtitle = if (lowStockCount > 0) "⚠️ يوجد $lowStockCount مواد سنية وصلت لحد إعادة الطلب" else "متابعة أرصدة المواد وتنبيهات النواقص",
              icon = Icons.Default.Inventory2,
              onClick = onNavigateToInventory
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsMenuItem(
              title = "دليل أنواع الأعمال والتركيبات (Work Types)",
              subtitle = "الزركونيا، الإيماكس، الفينير، الأطقم المتحركة، الزراعة",
              icon = Icons.Default.Category,
              onClick = onNavigateToWorkTypes
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsMenuItem(
              title = "سجل العمليات والرقابة (Audit Log)",
              subtitle = "متابعة وتدقيق الإجراءات والتعديلات المنفذة",
              icon = Icons.Default.History,
              onClick = onNavigateToAuditLog
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsMenuItem(
              title = "النسخ الاحتياطي السحابي والمزامنة (Firebase Storage & Firestore)",
              subtitle = if (isOnline) "🟢 متصل بالسحابة | حفظ تلقائي لبيانات المرضى والإرساليات لمنع ضياع البيانات" else "🔴 وضع عدم الاتصال (Offline) | حفظ محلي",
              icon = Icons.Default.CloudSync,
              onClick = onNavigateToCloudSync
            )
          }
        }
      }

      // 4. Data Export and Reporting (Admin / Accountant)
      if (activeUser.role != UserRole.STAFF) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = "تصدير البيانات والتقارير (Excel / CSV)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              Text(
                text = "يمكنك تصدير كشوفات الحساب وسجلات الإرساليات إلى ملفات نصية أو مشاركتها مع المحاسبة والواتساب.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                FilledTonalButton(
                  onClick = {
                    val csvText = buildString {
                      append("رقم الإرسالية,المعمل,نوع العمل,القطع,الأسنان,اللون,الطبيب,المريض,تاريخ الطلب,موعد التسليم,الحالة,السعر الإجمالي\n")
                      allShipments.forEach { s ->
                        append("${s.shipmentNumber},\"${s.labName}\",\"${s.workTypeName}\",${s.pieceCount},\"${s.toothNumbers}\",\"${s.shade}\",\"${s.clinicOrDoctorName}\",\"${s.patientName}\",\"${DateUtils.formatShortDate(s.orderDate)}\",\"${DateUtils.formatShortDate(s.expectedDeliveryDate)}\",\"${s.status.titleAr}\",${s.totalPrice}\n")
                      }
                    }
                    exportTitle = "تقرير الإرساليات (CSV)"
                    exportTextContent = csvText
                    showExportDialog = true
                  },
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("سجل الإرساليات")
                }

                FilledTonalButton(
                  onClick = {
                    val statementText = buildString {
                      append("═══════════════════════════════════════\n")
                      append("      تقرير وأرصدة معامل الأسنان       \n")
                      append("═══════════════════════════════════════\n\n")
                      labSummaries.forEach { summary ->
                        append("■ المعمل: ${summary.lab.name}\n")
                        append("  الهاتف: ${summary.lab.phone.ifEmpty { "غير مسجل" }}\n")
                        append("  إجمالي الإرساليات: ${summary.totalShipments} (${summary.totalPieces} قطعة)\n")
                        append("  إجمالي المطالبات: ${summary.totalBilled} $currency\n")
                        append("  إجمالي المسدد: ${summary.totalPaid} $currency\n")
                        append("  الرصيد المتبقي: ${summary.remainingBalance} $currency\n")
                        append("───────────────────────────────────────\n")
                      }
                    }
                    exportTitle = "كشف حساب وأرصدة المعامل"
                    exportTextContent = statementText
                    showExportDialog = true
                  },
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("أرصدة المعامل")
                }
              }
            }
          }
        }
      }

      // 4.5. In-App Updates & App Version Card (فحص وتحديث التطبيق)
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.testTag("app_updates_card")
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
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.SystemUpdate,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "تحديثات وإصدار التطبيق (In-App Updates)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              // Current Installed Version Badge
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = "v${BuildConfig.VERSION_NAME}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            // Version Status Card
            val isNewerAvailable = BuildConfig.VERSION_CODE < versionConfig.latestVersionCode
            val isMandatory = BuildConfig.VERSION_CODE < versionConfig.minimumSupportedVersionCode

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = when {
                isMandatory -> Color(0xFFFEE2E2)
                isNewerAvailable -> Color(0xFFFEF3C7)
                else -> Color(0xFFDCFCE7)
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = when {
                    isMandatory -> Icons.Default.Warning
                    isNewerAvailable -> Icons.Default.NewReleases
                    else -> Icons.Default.CheckCircle
                  },
                  contentDescription = null,
                  tint = when {
                    isMandatory -> Color(0xFFDC2626)
                    isNewerAvailable -> Color(0xFFD97706)
                    else -> Color(0xFF16A34A)
                  },
                  modifier = Modifier.size(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = when {
                      isMandatory -> "تحديث أمني إجباري مطلوب (v${versionConfig.latestVersionName})"
                      isNewerAvailable -> "يتوفر إصدار جديد جاهز للتنزيل! (v${versionConfig.latestVersionName})"
                      else -> "التطبيق محدث لأحدث إصدار متوفر (v${BuildConfig.VERSION_NAME})"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = when {
                      isMandatory -> Color(0xFF991B1B)
                      isNewerAvailable -> Color(0xFF92400E)
                      else -> Color(0xFF166534)
                    }
                  )
                  Text(
                    text = when {
                      isMandatory -> "الإصدار المثبت قديم، يرجى التحديث لمتابعة المزامنة السحابية."
                      isNewerAvailable -> "يتضمن الإصدار الجديد ميزات وتحسينات لضمان أفضل أداء."
                      else -> "رقم البناء الحالي: (${BuildConfig.VERSION_CODE}) متوافق تماماً مع السحابة."
                    },
                    fontSize = 11.sp,
                    color = when {
                      isMandatory -> Color(0xFF7F1D1D)
                      isNewerAvailable -> Color(0xFF78350F)
                      else -> Color(0xFF14532D)
                    }
                  )
                }
              }
            }

            // Release Notes snippet if available
            if (versionConfig.releaseNotesAr.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "📋 ما الجديد في الإصدار (v${versionConfig.latestVersionName}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = versionConfig.releaseNotesAr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                  )
                }
              }
            }

            // Action Buttons Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // 1. Manual Check Now Button
              OutlinedButton(
                onClick = {
                  isCheckingUpdatesManually = true
                  viewModel.checkForAppUpdates { status ->
                    isCheckingUpdatesManually = false
                    when (status) {
                      is AppUpdateStatus.UpToDate -> {
                        Toast.makeText(context, "✅ التطبيق محدث لأحدث إصدار (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                      }
                      is AppUpdateStatus.OptionalUpdateAvailable -> {
                        Toast.makeText(context, "🔔 يتوفر إصدار جديد جاهز للتنزيل (v${status.config.latestVersionName})", Toast.LENGTH_LONG).show()
                      }
                      is AppUpdateStatus.MandatoryUpdateRequired -> {
                        Toast.makeText(context, "⚠️ يتوفر تحديث إجباري مطلوب (v${status.config.latestVersionName})", Toast.LENGTH_LONG).show()
                      }
                      is AppUpdateStatus.CheckFailed -> {
                        Toast.makeText(context, "تعذر فحص التحديثات: ${status.error}", Toast.LENGTH_SHORT).show()
                      }
                      else -> {}
                    }
                  }
                },
                enabled = !isCheckingUpdatesManually,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("check_updates_now_btn")
              ) {
                if (isCheckingUpdatesManually) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(6.dp))
                  Text("جاري الفحص...", fontSize = 11.sp)
                } else {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("فحص التحديثات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              // 2. Direct Update / Download APK Button
              Button(
                onClick = {
                  viewModel.appVersionManager.openUpdateUrl(versionConfig.updateUrl)
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isNewerAvailable) Color(0xFF0D9488) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.2f).testTag("download_update_btn")
              ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                  text = if (isNewerAvailable) "تنزيل التحديث الجديد 📲" else "تنزيل آخر APK",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // 3. Super Admin Publish Release Config Button
            if (activeUser.role == UserRole.SUPER_ADMIN) {
              TextButton(
                onClick = {
                  pubLatestVersionName = versionConfig.latestVersionName
                  pubLatestVersionCode = versionConfig.latestVersionCode.toString()
                  pubMinVersionCode = versionConfig.minimumSupportedVersionCode.toString()
                  pubUpdateTitle = versionConfig.updateTitleAr
                  pubUpdateMessage = versionConfig.updateMessageAr
                  pubReleaseNotes = versionConfig.releaseNotesAr
                  pubUpdateUrl = versionConfig.updateUrl
                  isMandatorySwitch = versionConfig.isMandatoryUpdate
                  showPublishVersionDialog = true
                },
                modifier = Modifier.fillMaxWidth().testTag("publish_new_version_btn")
              ) {
                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("إدارة ونشر إصدار جديد للمركز (Super Admin)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }

      // 5. Data Management & Reset Section (تصفير السجلات وحذف البيانات)
      if (activeUser.role == UserRole.SUPER_ADMIN || activeUser.role == UserRole.ADMIN) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  Icons.Default.DeleteForever,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  text = "إدارة البيانات وتصفير التطبيق للبدء من جديد",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.error
                )
              }

              Text(
                text = "يمكنك تصفير السجلات وحذف البيانات القديمة للبدء ببيانات جديدة لعيادتك:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              // Option 1: Clear Mock/Demo Data (Owner's preferred safe clean)
              Button(
                onClick = {
                  enteredPin = ""
                  pinError = ""
                  showClearDemoConfirm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("wipe_demo_data_btn")
              ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("مسح البيانات الوهمية والتجريبية (لبدء سجلات جديدة)", fontWeight = FontWeight.Bold)
              }

              // Option 2: Full Factory Reset
              OutlinedButton(
                onClick = {
                  enteredPin = ""
                  pinError = ""
                  showFactoryResetConfirm = true
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("factory_reset_btn")
              ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصفير شامل لكافة البيانات (Factory Reset)", fontWeight = FontWeight.Bold)
              }

              // Option 3: Restore Demo Data
              TextButton(
                onClick = { showResetDemoConfirm = true },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("استعادة البيانات النموذجية والتجريبية")
              }
            }
          }
        }
      }
    }

    // --- Dialog 0: Dedicated Owner Clear Demo/Mock Data Confirmation ---
    if (showClearDemoConfirm) {
      AlertDialog(
        onDismissRequest = { showClearDemoConfirm = false },
        icon = {
          Icon(
            imageVector = Icons.Default.CleaningServices,
            contentDescription = null,
            tint = Color(0xFFD97706),
            modifier = Modifier.size(36.dp)
          )
        },
        title = {
          Text(
            text = "مسح البيانات التجريبية والبدء الفعلي 🧹",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        },
        text = {
          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "هذا الإجراء مخصص لمالك التطبيق لبدء العمل الحقيقي للعيادة بسجلات جديدة ونظيفة:",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold
            )

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFEE2E2),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🗑️ سيتم حذف:", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B), fontSize = 12.sp)
                Text("• كافة الإرساليات والطلبات التجريبية المسجلة", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                Text("• سندات الصرف والمدفوعات والحسابات التجريبية", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                Text("• سجل حركات المواد والمخزون وسجل التدقيق", fontSize = 11.sp, color = Color(0xFF7F1D1D))
              }
            }

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFDCFCE7),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🛡️ سيتم الإبقاء عليها دون حذف:", fontWeight = FontWeight.Bold, color = Color(0xFF166534), fontSize = 12.sp)
                Text("• قائمة معامل الأسنان وأرقام الهواتف", fontSize = 11.sp, color = Color(0xFF14532D))
                Text("• أسعار التركيبات وقوائم الخدمات المعتمدة", fontSize = 11.sp, color = Color(0xFF14532D))
                Text("• أنواع الأعمال (الزركونيا، الإيماكس، الفينير...)", fontSize = 11.sp, color = Color(0xFF14532D))
                Text("• حسابات المستخدمين وصلاحياتهم ورموز المرور", fontSize = 11.sp, color = Color(0xFF14532D))
              }
            }

            Text(
              text = "أدخل رمز المرور / PIN لتأكيد التنفيذ:",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
              value = enteredPin,
              onValueChange = { enteredPin = it; pinError = "" },
              label = { Text("رمز المرور / PIN") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
              ),
              isError = pinError.isNotEmpty(),
              modifier = Modifier.fillMaxWidth()
            )

            if (pinError.isNotEmpty()) {
              Text(text = pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (viewModel.verifyPin(activeUser, enteredPin)) {
                viewModel.clearMockDemoData {
                  showClearDemoConfirm = false
                  android.widget.Toast.makeText(
                    context,
                    "تم مسح البيانات التجريبية بنجاح! التطبيق جاهز الآن لتسجيل المرضى الحقيقيين 🦷✨",
                    android.widget.Toast.LENGTH_LONG
                  ).show()
                }
              } else {
                pinError = "رمز المرور غير صحيح"
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
          ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("تأكيد مسح البيانات التجريبية", fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showClearDemoConfirm = false }) {
            Text("إلغاء")
          }
        }
      )
    }

    // --- Dialog 1: Wipe Transactions Only Confirmation ---
    if (showWipeTransactionsConfirm) {
      AlertDialog(
        onDismissRequest = { showWipeTransactionsConfirm = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("تصفير وحذف جميع الإرساليات", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "سيتم حذف جميع الإرساليات والمدفوعات وسجل العمليات نهائياً لتصفير العدادات والبدء بسجلات جديدة، مع الإبقاء على قائمة المعامل وأنواع الأعمال.",
              style = MaterialTheme.typography.bodyMedium
            )

            Text(
              text = "أدخل كلمة المرور/السر لتأكيد التصفير:",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
              value = enteredPin,
              onValueChange = { enteredPin = it; pinError = "" },
              label = { Text("كلمة المرور / PIN") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
              ),
              isError = pinError.isNotEmpty(),
              modifier = Modifier.fillMaxWidth()
            )

            if (pinError.isNotEmpty()) {
              Text(text = pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (viewModel.verifyPin(activeUser, enteredPin)) {
                viewModel.wipeAllTransactions {
                  showWipeTransactionsConfirm = false
                  snackbarMessage = "تم تصفير جميع الإرساليات والمدفوعات بنجاح"
                }
              } else {
                pinError = "كلمة المرور غير صحيحة"
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("تأكيد التصفير والبدء من جديد")
          }
        },
        dismissButton = {
          TextButton(onClick = { showWipeTransactionsConfirm = false }) {
            Text("إلغاء")
          }
        }
      )
    }

    // --- Dialog 2: Full Factory Reset Confirmation ---
    if (showFactoryResetConfirm) {
      AlertDialog(
        onDismissRequest = { showFactoryResetConfirm = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("تصفير شامل لبيانات التطبيق", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "تحذير: سيتم مسح كافة البيانات بما في ذلك المعامل والأسعار والإرساليات والحسابات وإعادة ضبط التطبيق كلياً كأنه مثبت للتو!",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error
            )

            Text(
              text = "أدخل كلمة المرور/السر لتأكيد ضبط المصنع:",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
              value = enteredPin,
              onValueChange = { enteredPin = it; pinError = "" },
              label = { Text("كلمة المرور / PIN") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
              ),
              isError = pinError.isNotEmpty(),
              modifier = Modifier.fillMaxWidth()
            )

            if (pinError.isNotEmpty()) {
              Text(text = pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (viewModel.verifyPin(activeUser, enteredPin)) {
                viewModel.factoryResetApp {
                  showFactoryResetConfirm = false
                  snackbarMessage = "تمت إعادة ضبط المصنع بنجاح"
                }
              } else {
                pinError = "كلمة المرور غير صحيحة"
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("تأكيد التصفير الشامل")
          }
        },
        dismissButton = {
          TextButton(onClick = { showFactoryResetConfirm = false }) {
            Text("إلغاء")
          }
        }
      )
    }

    // --- Dialog 3: Demo Data Restore ---
    if (showResetDemoConfirm) {
      AlertDialog(
        onDismissRequest = { showResetDemoConfirm = false },
        title = { Text("استعادة البيانات التجريبية") },
        text = { Text("هل تريد استعادة البيانات النموذجية الأولية للمعامل والإرساليات؟") },
        confirmButton = {
          Button(
            onClick = {
              viewModel.resetToDemoData()
              showResetDemoConfirm = false
            }
          ) {
            Text("نعم، استعادة")
          }
        },
        dismissButton = {
          TextButton(onClick = { showResetDemoConfirm = false }) {
            Text("إلغاء")
          }
        }
      )
    }

    // --- Export Dialog ---
    if (showExportDialog) {
      AlertDialog(
        onDismissRequest = { showExportDialog = false },
        title = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(exportTitle, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
        },
        text = {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "تم توليد البيانات بنجاح، يمكنك نسخ المحتوى أو مشاركته مباشرة:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
              shape = RoundedCornerShape(8.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
            ) {
              SelectionContainer {
                Text(
                  text = exportTextContent,
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                  modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                )
              }
            }
          }
        },
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
              onClick = {
                val sendIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_TEXT, exportTextContent)
                  type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير"))
              }
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("مشاركة")
            }

            Button(
              onClick = {
                clipboardManager.setText(AnnotatedString(exportTextContent))
                showExportDialog = false
              }
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("نسخ")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showExportDialog = false }) {
            Text("إغلاق")
          }
        }
      )
    }

    // --- Change Doctor Master PIN Dialog ---
    if (showChangePinDialog) {
      AlertDialog(
        onDismissRequest = { showChangePinDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("تغيير رمز مرور الطبيب المعتمد", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "قم بتعيين رمز مرور / PIN جديد خاص بالطبيب (${com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME}) لحماية التطبيق من الاستخدام غير المصرح به:",
              style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
              value = newDoctorPin,
              onValueChange = { newDoctorPin = it; changePinError = "" },
              label = { Text("رمز المرور الجديد (4 أرقام)") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
              ),
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = confirmDoctorPin,
              onValueChange = { confirmDoctorPin = it; changePinError = "" },
              label = { Text("تأكيد رمز المرور الجديد") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
              ),
              modifier = Modifier.fillMaxWidth()
            )

            if (changePinError.isNotEmpty()) {
              Text(
                text = changePinError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (newDoctorPin.length < 4) {
                changePinError = "يجب أن يتكون رمز المرور من 4 أرقام على الأقل"
              } else if (newDoctorPin != confirmDoctorPin) {
                changePinError = "رمزي المرور غير متطابقين!"
              } else {
                viewModel.changeDoctorMasterPin(newDoctorPin)
                showChangePinDialog = false
                android.widget.Toast.makeText(context, "تم تحديث رمز مرور الطبيب بنجاح", android.widget.Toast.LENGTH_SHORT).show()
              }
            }
          ) {
            Text("حفظ الرمز الجديد")
          }
        },
        dismissButton = {
          TextButton(onClick = { showChangePinDialog = false }) {
            Text("إلغاء")
          }
        }
      )
    }

    // --- Dialog: Publish New App Version (Super Admin / Owner) ---
    if (showPublishVersionDialog) {
      AlertDialog(
        onDismissRequest = { if (!isPublishingVersion) showPublishVersionDialog = false },
        icon = {
          Icon(
            imageVector = Icons.Default.Publish,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
          )
        },
        title = {
          Text(
            text = "نشر وتحديث معلومات الإصدار في السحابة 🚀",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "عند نشر هذا الإصدار، سيتم تنبيه كافة أجهزة الموظفين والأطباء المسجلين بالمركز وتوفير رابط التنزيل المباشر لهم فوراً.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 16.sp
            )

            OutlinedTextField(
              value = pubLatestVersionName,
              onValueChange = { pubLatestVersionName = it },
              label = { Text("رقم الإصدار (Version Name)", fontSize = 12.sp) },
              placeholder = { Text("1.2.0") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = pubLatestVersionCode,
                onValueChange = { pubLatestVersionCode = it },
                label = { Text("كود الإصدار (Build Code)", fontSize = 11.sp) },
                placeholder = { Text("2") },
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = pubMinVersionCode,
                onValueChange = { pubMinVersionCode = it },
                label = { Text("الحد الأدنى المطلوب", fontSize = 11.sp) },
                placeholder = { Text("1") },
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
            }

            OutlinedTextField(
              value = pubUpdateTitle,
              onValueChange = { pubUpdateTitle = it },
              label = { Text("عنوان التنبيه للمستخدمين", fontSize = 12.sp) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = pubUpdateMessage,
              onValueChange = { pubUpdateMessage = it },
              label = { Text("رسالة التحديث", fontSize = 12.sp) },
              maxLines = 3,
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = pubReleaseNotes,
              onValueChange = { pubReleaseNotes = it },
              label = { Text("ما الجديد في هذا الإصدار (Release Notes)", fontSize = 12.sp) },
              placeholder = { Text("• ميزة 1\n• ميزة 2") },
              minLines = 3,
              maxLines = 5,
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = pubUpdateUrl,
              onValueChange = { pubUpdateUrl = it },
              label = { Text("رابط تنزيل التحديث المباشر (APK / Web URL)", fontSize = 12.sp) },
              placeholder = { Text("https://...") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("إلزام كافة الأجهزة بالتحديث (Mandatory)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Switch(
                checked = isMandatorySwitch,
                onCheckedChange = { isMandatorySwitch = it }
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val newCode = pubLatestVersionCode.toIntOrNull() ?: versionConfig.latestVersionCode
              val minCode = pubMinVersionCode.toIntOrNull() ?: versionConfig.minimumSupportedVersionCode
              val newConfig = AppVersionConfig(
                currentAppVersionCode = BuildConfig.VERSION_CODE,
                currentAppVersionName = BuildConfig.VERSION_NAME,
                minimumSupportedVersionCode = minCode,
                latestVersionCode = newCode,
                latestVersionName = pubLatestVersionName.trim().ifEmpty { BuildConfig.VERSION_NAME },
                isMandatoryUpdate = isMandatorySwitch,
                updateTitleAr = pubUpdateTitle.trim().ifEmpty { "تحديث جديد متوفر" },
                updateMessageAr = pubUpdateMessage.trim().ifEmpty { "يتوفر إصدار جديد جاهز للتنزيل" },
                releaseNotesAr = pubReleaseNotes.trim(),
                updateUrl = pubUpdateUrl.trim().ifEmpty { versionConfig.updateUrl }
              )
              isPublishingVersion = true
              viewModel.publishNewVersion(newConfig) { success, msg ->
                isPublishingVersion = false
                if (success) {
                  showPublishVersionDialog = false
                  Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
              }
            },
            enabled = !isPublishingVersion,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
            shape = RoundedCornerShape(10.dp)
          ) {
            if (isPublishingVersion) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(Modifier.width(6.dp))
              Text("جاري النشر...")
            } else {
              Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("حفظ ونشر التحديث بالسحابة", fontWeight = FontWeight.Bold)
            }
          }
        },
        dismissButton = {
          TextButton(
            onClick = { if (!isPublishingVersion) showPublishVersionDialog = false }
          ) {
            Text("إلغاء")
          }
        },
        shape = RoundedCornerShape(20.dp)
      )
    }
  }
}

@Composable
fun SettingsMenuItem(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
  }
}

@Composable
fun UserSwitchDialog(
  users: List<User>,
  activeUser: User,
  onUserSelected: (User) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedUserToAuth by remember { mutableStateOf<User?>(null) }
  var pinInput by remember { mutableStateOf("") }
  var isPinError by remember { mutableStateOf(false) }

  if (selectedUserToAuth != null) {
    val targetUser = selectedUserToAuth!!
    AlertDialog(
      onDismissRequest = { selectedUserToAuth = null },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("تأكيد كلمة المرور")
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "يرجى إدخال كلمة المرور / كلمة السر للحساب (${targetUser.fullName}):",
            style = MaterialTheme.typography.bodyMedium
          )

          OutlinedTextField(
            value = pinInput,
            onValueChange = { pinInput = it; isPinError = false },
            label = { Text("كلمة المرور (PIN)") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
              keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            isError = isPinError,
            modifier = Modifier.fillMaxWidth()
          )

          if (isPinError) {
            Text(
              text = "كلمة المرور غير صحيحة",
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (targetUser.pinCode.isNotBlank() && com.aqlanlab.app.util.SecurityUtils.verifyPin(pinInput.trim(), targetUser.pinCode.trim())) {
              onUserSelected(targetUser)
              selectedUserToAuth = null
              onDismiss()
            } else {
              isPinError = true
            }
          }
        ) {
          Text("دخول")
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedUserToAuth = null }) {
          Text("رجوع")
        }
      }
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("تبديل المستخدم والصلاحية")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "اختر المستخدم لتسجيل الدخول بحسب الصلاحيات المحددة:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        users.forEach { user ->
          val isSelected = user.id == activeUser.id
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            ),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                if (user.id == activeUser.id) {
                  onDismiss()
                } else {
                  pinInput = ""
                  isPinError = false
                  selectedUserToAuth = user
                }
              }
              .testTag("select_user_${user.username}")
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(Color(user.avatarColor)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = user.fullName.take(1),
                  color = Color.White,
                  fontWeight = FontWeight.Bold
                )
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = user.fullName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                RoleBadge(role = user.role)
                Text(
                  text = when (user.role) {
                    UserRole.SUPER_ADMIN -> "المشرف العام والمالك (صلاحيات غير مقيدة والتحكم بالتراخيص)"
                    UserRole.ADMIN -> "المدير العام (صلاحيات كاملة وتصفير النظام)"
                    UserRole.STAFF -> "موظف / فني (إنشاء الإرساليات بدون أسعار)"
                    UserRole.ACCOUNTANT -> "المحاسب (كشوفات الحساب والدفعات)"
                    UserRole.TECHNICIAN -> "فني المعمل (تحديث الحالات والمراحل)"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "المحدد حالياً",
                  tint = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("إغلاق")
      }
    }
  )
}
