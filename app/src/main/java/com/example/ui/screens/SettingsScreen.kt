package com.example.ui.screens

import android.content.Intent
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
import com.example.data.models.User
import com.example.data.models.UserRole
import com.example.ui.components.DateUtils
import com.example.ui.components.RoleBadge
import com.example.ui.theme.RoleAdminColor
import com.example.ui.theme.RoleStaffColor
import com.example.ui.theme.RoleAccountantColor
import com.example.ui.viewmodel.DentalLabViewModel
import kotlinx.coroutines.launch

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
  onOpenUserSwitchDialog: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
  val clipboardManager = LocalClipboardManager.current
  val isOnline by viewModel.isOnline.collectAsState()
  val syncState by viewModel.syncState.collectAsState()
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val labSummaries by viewModel.labAccountSummaries.collectAsState()
  val lowStockCount by viewModel.lowStockCount.collectAsState()

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
        com.example.ui.components.AqlanClinicHeaderCard()
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
                  UserRole.ADMIN -> "كامل الصلاحيات (الأسعار، المالية، المعامل)"
                  UserRole.STAFF -> "صلاحيات تشغيلية فقط (الأسعار مخفية ومحمية)"
                  UserRole.ACCOUNTANT -> "صلاحيات مالية ومحاسبية"
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
              text = "هذا البرنامج محمي ومرخص حصرياً لـ ${com.example.ui.components.ClinicInfo.DOCTOR_NAME} (${com.example.ui.components.ClinicInfo.CLINIC_NAME}). لا يمكن لأي شخص فتح التطبيق أو كشف الأسعار والحسابات بدون إذنك.",
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
              text = "إرسال إشعارات فورية على شريط الهاتف عند إضافة إرسالية جديدة، أو تغيير حالة طلب معملي (قيد التنفيذ / جاهز / تم الاستلام)، وتنبيهات الحالات المستعجلة.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              OutlinedButton(
                onClick = {
                  com.example.util.NotificationHelper.showNewShipmentNotification(
                    context = context,
                    shipment = com.example.data.models.Shipment(
                      id = 9999,
                      shipmentNumber = "AQL-TEST",
                      clinicOrDoctorName = com.example.ui.components.ClinicInfo.DOCTOR_NAME,
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
                      status = com.example.data.models.ShipmentStatus.NEW
                    ),
                    createdByName = activeUser.fullName
                  )
                  android.widget.Toast.makeText(context, "تم إرسال إشعار تجريبي بنجاح 🔔", android.widget.Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("إرسال إشعار تجريبي", fontSize = 12.sp)
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

      // 5. Data Management & Reset Section (تصفير السجلات وحذف البيانات)
      if (activeUser.role == UserRole.ADMIN) {
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

              // Option 1: Wipe transactions only
              Button(
                onClick = {
                  enteredPin = ""
                  pinError = ""
                  showWipeTransactionsConfirm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("wipe_transactions_btn")
              ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصفير الإرساليات والمدفوعات (لبدء سجلات جديدة)", fontWeight = FontWeight.Bold)
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
              text = "قم بتعيين رمز مرور / PIN جديد خاص بالطبيب (${com.example.ui.components.ClinicInfo.DOCTOR_NAME}) لحماية التطبيق من الاستخدام غير المصرح به:",
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
              // التحقق من قوة الرمز والحفظ الدائم يتمان الآن داخل الـ ViewModel:
              // سابقاً كان الرمز الجديد يُحفظ في متغير بالذاكرة فقط ويعود القديم
              // عند إعادة تشغيل التطبيق، بينما يظل الباب الخلفي "1111" يعمل.
              settingsScope.launch {
                val error = viewModel.changeDoctorMasterPin(newDoctorPin, confirmDoctorPin)
                if (error == null) {
                  showChangePinDialog = false
                  newDoctorPin = ""
                  confirmDoctorPin = ""
                  changePinError = ""
                  android.widget.Toast.makeText(context, "تم تحديث رمز المرور بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                  changePinError = error
                }
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
  viewModel: DentalLabViewModel,
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
            // أُزيل الباب الخلفي: كان الشرط يقبل الرمز "1234" لأي مستخدم،
            // أي أن أي موظف يعرف هذا الرقم كان يستطيع التبديل إلى حساب مدير
            // النظام والوصول إلى المالية وإدارة المستخدمين والنسخ السحابي.
            if (viewModel.switchUserWithPin(targetUser, pinInput)) {
              pinInput = ""
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
                    UserRole.ADMIN -> "المدير العام (صلاحيات كاملة وتصفير النظام)"
                    UserRole.STAFF -> "موظف / فني (إنشاء الإرساليات بدون أسعار)"
                    UserRole.ACCOUNTANT -> "المحاسب (كشوفات الحساب والدفعات)"
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
