package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Shipment
import com.example.data.models.ShipmentStatus
import com.example.data.models.UserRole
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: DentalLabViewModel,
  onNavigateToNewShipment: () -> Unit,
  onNavigateToShipments: (ShipmentStatus?) -> Unit,
  onNavigateToShipmentDetail: (Long) -> Unit,
  onNavigateToLabs: () -> Unit,
  onNavigateToFinance: () -> Unit,
  onNavigateToReports: () -> Unit,
  onNavigateToDailyReport: () -> Unit = {},
  onNavigateToAnalytics: () -> Unit = {},
  onNavigateToInventory: () -> Unit = {},
  onNavigateToAuditLog: () -> Unit,
  onNavigateToCloudSync: () -> Unit = {},
  onNavigateToQrScanner: () -> Unit = {},
  onOpenUserSwitchDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isOnline by viewModel.isOnline.collectAsState()
  val syncState by viewModel.syncState.collectAsState()
  val activeUser by viewModel.activeUser.collectAsState()
  val stats by viewModel.dashboardStats.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val lowStockCount by viewModel.lowStockCount.collectAsState()
  val lowStockItems by viewModel.lowStockInventoryItems.collectAsState()

  var selectedCategoryFilter by remember { mutableStateOf("الكل") }
  var showSearchDialog by remember { mutableStateOf(false) }
  var showAlertsDialog by remember { mutableStateOf(false) }

  val lateShipments = remember(allShipments) {
    allShipments.filter { DateUtils.isLate(it.expectedDeliveryDate, it.status) }
  }

  val urgentShipments = remember(allShipments) {
    allShipments.filter { it.isUrgent && it.status != ShipmentStatus.RECEIVED && it.status != ShipmentStatus.CANCELLED }
  }

  val readyShipments = remember(allShipments) {
    allShipments.filter { it.status == ShipmentStatus.READY }
  }

  val displayShipments = remember(allShipments, selectedCategoryFilter) {
    if (selectedCategoryFilter == "الكل") {
      allShipments.take(8)
    } else {
      allShipments.filter {
        it.workTypeName.contains(selectedCategoryFilter, ignoreCase = true)
      }.take(8)
    }
  }

  val notificationCount = lateShipments.size + urgentShipments.size + readyShipments.size + lowStockCount

  Scaffold(
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Right: User Profile & Role Switcher
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
              modifier = Modifier
                .clickable { onOpenUserSwitchDialog() }
                .testTag("dashboard_user_profile_btn")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(activeUser.avatarColor)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = activeUser.fullName.take(1),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                Column {
                  Text(
                    text = activeUser.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = activeUser.role.titleAr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                  )
                }

                Icon(
                  imageVector = Icons.Default.SwapHoriz,
                  contentDescription = "تبديل الحساب",
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
              }
            }

            // Left: Quick Search & Notification Bell Icons & Cloud Sync & Lock App
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              // QR & Barcode Scanner Button
              IconButton(
                onClick = onNavigateToQrScanner,
                modifier = Modifier
                  .testTag("dashboard_qr_scanner_btn")
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.QrCodeScanner,
                  contentDescription = "ماسح الباركود والـ QR",
                  tint = MaterialTheme.colorScheme.primary
                )
              }

              // Lock App Button
              IconButton(
                onClick = { viewModel.lockApp() },
                modifier = Modifier
                  .testTag("dashboard_lock_app_btn")
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "قفل التطبيق",
                  tint = Color(0xFFD97706)
                )
              }

              // Cloud Sync Status Button
              IconButton(
                onClick = onNavigateToCloudSync,
                modifier = Modifier
                  .testTag("dashboard_cloud_sync_btn")
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                  contentDescription = "المزامنة السحابية",
                  tint = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
              }

              // Analytics & Trends Chart Button
              IconButton(
                onClick = onNavigateToAnalytics,
                modifier = Modifier
                  .testTag("dashboard_analytics_btn")
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ShowChart,
                  contentDescription = "الرسوم البيانية والتحليلات",
                  tint = MaterialTheme.colorScheme.primary
                )
              }

              // Quick Search Button
              IconButton(
                onClick = { showSearchDialog = true },
                modifier = Modifier
                  .testTag("dashboard_quick_search_btn")
                  .size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = "بحث سريع",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }

              // Notification Alerts Bell
              Box {
                IconButton(
                  onClick = { showAlertsDialog = true },
                  modifier = Modifier
                    .testTag("dashboard_alerts_bell_btn")
                    .size(40.dp)
                ) {
                  Icon(
                    imageVector = if (notificationCount > 0) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    contentDescription = "التنبيهات",
                    tint = if (notificationCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                  )
                }

                if (notificationCount > 0) {
                  Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .offset(x = (-4).dp, y = 4.dp)
                  ) {
                    Text("$notificationCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
      }
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onNavigateToNewShipment,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("إرسالية جديدة", fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(6.dp),
        modifier = Modifier.testTag("fab_new_shipment")
      )
    },
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(bottom = 90.dp, top = 12.dp)
    ) {
      // 1. Official Dr. Aqlan Clinic Brand Header Card
      item {
        AqlanClinicHeaderCard()
      }

      // 2. Urgent / Late Alert Banner (if any)
      if (lateShipments.isNotEmpty() || urgentShipments.isNotEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showAlertsDialog = true }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
              )

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "تنبيهات المتابعة العاجلة",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                  text = buildString {
                    if (lateShipments.isNotEmpty()) append("يوجد ${lateShipments.size} إرساليات متأخرة. ")
                    if (urgentShipments.isNotEmpty()) append("و ${urgentShipments.size} إرساليات عاجلة بحاجة للمتابعة.")
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }

              Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "عرض",
                tint = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }

      // 2.1 Low Stock Inventory Alert Banner (if any items reach threshold)
      if (lowStockCount > 0) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)), // Warm Amber
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onNavigateToInventory() }
              .testTag("dashboard_low_stock_banner")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Surface(
                shape = CircleShape,
                color = Color(0xFFD97706),
                modifier = Modifier.size(36.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "تنبيه نقص المخزون السني ($lowStockCount مواد)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF92400E)
                )
                Text(
                  text = "هناك مواد سنية ومستلزمات وصلت للحد الأدنى، اضغط للمعاينة وإعادة الطلب.",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFF78350F)
                )
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFD97706)
              ) {
                Text(
                  text = "المخزون",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }
        }
      }

      // 3. Status KPI Metric Cards (Interactive)
      item {
        Text(
          text = "مؤشرات وحالات الإرساليات",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Row 1: New & In Progress
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ModernKpiCard(
              title = "إرساليات جديدة",
              count = stats.newCount,
              subtitle = "بانتظار البدء بالمعمل",
              icon = Icons.Default.FiberNew,
              color = StatusNew,
              onClick = { onNavigateToShipments(ShipmentStatus.NEW) },
              modifier = Modifier.weight(1f)
            )

            ModernKpiCard(
              title = "قيد التنفيذ والعمل",
              count = stats.inProgressCount,
              subtitle = "جاري التصنيع والصب",
              icon = Icons.Default.Autorenew,
              color = StatusInProgress,
              onClick = { onNavigateToShipments(ShipmentStatus.IN_PROGRESS) },
              modifier = Modifier.weight(1f)
            )
          }

          // Row 2: Overdue & Ready
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ModernKpiCard(
              title = "متأخرة عن الموعد",
              count = stats.lateCount,
              subtitle = if (stats.lateCount > 0) "⚠️ تتطلب اتصال فوري" else "لا يوجد تأخير",
              icon = Icons.Default.Schedule,
              color = StatusLate,
              onClick = { showAlertsDialog = true },
              modifier = Modifier.weight(1f)
            )

            ModernKpiCard(
              title = "جاهزة للاستلام",
              count = stats.readyCount,
              subtitle = "جاهزة للتركيب للمريض",
              icon = Icons.Default.CheckCircle,
              color = StatusReady,
              onClick = { onNavigateToShipments(ShipmentStatus.READY) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      // 4. Quick Actions Hub (Functional Navigation Hub)
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = "الإجراءات والأقسام السريعة",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              QuickActionButton(
                title = "اليومية",
                icon = Icons.Default.Summarize,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToDailyReport
              )

              QuickActionButton(
                title = "إرسالية جديدة",
                icon = Icons.Default.AddCircleOutline,
                tint = Color(0xFF00897B),
                onClick = onNavigateToNewShipment
              )

              QuickActionButton(
                title = "المخزون",
                icon = Icons.Default.Inventory2,
                tint = Color(0xFFD97706),
                onClick = onNavigateToInventory
              )

              QuickActionButton(
                title = "المعامل",
                icon = Icons.Default.Apartment,
                tint = Color(0xFF1976D2),
                onClick = onNavigateToLabs
              )

              if (activeUser.role != UserRole.STAFF) {
                QuickActionButton(
                  title = "المالية",
                  icon = Icons.Default.AccountBalanceWallet,
                  tint = Color(0xFF7C3AED),
                  onClick = onNavigateToFinance
                )
              }

              QuickActionButton(
                title = "التقارير",
                icon = Icons.Default.BarChart,
                tint = Color(0xFFE65100),
                onClick = onNavigateToReports
              )
            }
          }
        }
      }

      // 5. Recent Dental Cases Section & Category Chips
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "آخر الإرساليات والطلبات",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            TextButton(
              onClick = { onNavigateToShipments(null) },
              modifier = Modifier.testTag("view_all_shipments_btn")
            ) {
              Text("عرض الكل (${allShipments.size})")
              Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          // Category filter row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf("الكل", "Zirconia", "E-max", "بورسلين", "فينير", "تقويم", "زراعة", "أطقم").forEach { cat ->
              FilterChip(
                selected = selectedCategoryFilter == cat,
                onClick = { selectedCategoryFilter = cat },
                label = { Text(cat, fontSize = 12.sp, fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal) }
              )
            }
          }
        }
      }

      // Recent Shipments Items
      if (displayShipments.isEmpty()) {
        item {
          EmptyStateView(
            title = if (selectedCategoryFilter != "الكل") "لا توجد إرساليات في فئة $selectedCategoryFilter" else "لا توجد إرساليات حتى الآن",
            description = "اضغط على زر إرسالية جديدة لبدء إنشاء أول طلب معمل للأسنان",
            actionButton = {
              Button(onClick = onNavigateToNewShipment) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("إضافة إرسالية جديدة")
              }
            }
          )
        }
      } else {
        items(displayShipments, key = { it.id }) { shipment ->
          ShipmentCardItem(
            shipment = shipment,
            userRole = activeUser.role,
            currency = currency,
            onClick = { onNavigateToShipmentDetail(shipment.id) },
            modifier = Modifier.testTag("recent_shipment_${shipment.id}")
          )
        }
      }
    }
  }

  // Quick Search Dialog
  if (showSearchDialog) {
    QuickSearchDialog(
      shipments = allShipments,
      labs = allLabs,
      onSelectShipment = { id ->
        showSearchDialog = false
        onNavigateToShipmentDetail(id)
      },
      onDismiss = { showSearchDialog = false }
    )
  }

  // Alerts Dialog (Late & Urgent Cases)
  if (showAlertsDialog) {
    AlertDialog(
      onDismissRequest = { showAlertsDialog = false },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("مركز التنبيهات والمتابعة", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 350.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (lateShipments.isEmpty() && urgentShipments.isEmpty() && readyShipments.isEmpty() && lowStockItems.isEmpty()) {
            item {
              Text(
                text = "لا توجد تنبيهات عاجلة حالياً. جميع الأعمال تسير وفق المواعيد والمخزون مكتمل.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
              )
            }
          }

          if (lowStockItems.isNotEmpty()) {
            item {
              Text(
                text = "📦 مواد سنية وصلت لحد إعادة الطلب (${lowStockItems.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFD97706),
                fontWeight = FontWeight.Bold
              )
            }
            items(lowStockItems) { item ->
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF3C7),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    showAlertsDialog = false
                    onNavigateToInventory()
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFD97706).copy(alpha = 0.2f)
                  ) {
                    Text(
                      text = "نقص مخزون",
                      color = Color(0xFF92400E),
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${item.name} (${item.currentStock.toInt()} ${item.unit})",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF78350F)
                    )
                    Text(
                      text = "الحد الأدنى: ${item.minThreshold.toInt()} | المورد: ${item.supplierName}",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF92400E)
                    )
                  }

                  Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF92400E)
                  )
                }
              }
            }
          }

          if (lateShipments.isNotEmpty()) {
            item {
              Text(
                text = "⚠️ إرساليات متأخرة عن التسليم (${lateShipments.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
              )
            }
            items(lateShipments) { s ->
              AlertShipmentRow(
                shipment = s,
                tag = "متأخرة",
                tagColor = MaterialTheme.colorScheme.error,
                onClick = {
                  showAlertsDialog = false
                  onNavigateToShipmentDetail(s.id)
                }
              )
            }
          }

          if (urgentShipments.isNotEmpty()) {
            item {
              Spacer(Modifier.height(8.dp))
              Text(
                text = "⚡ إرساليات عاجلة (${urgentShipments.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = StatusInProgress,
                fontWeight = FontWeight.Bold
              )
            }
            items(urgentShipments) { s ->
              AlertShipmentRow(
                shipment = s,
                tag = "عاجلة",
                tagColor = StatusInProgress,
                onClick = {
                  showAlertsDialog = false
                  onNavigateToShipmentDetail(s.id)
                }
              )
            }
          }

          if (readyShipments.isNotEmpty()) {
            item {
              Spacer(Modifier.height(8.dp))
              Text(
                text = "✨ إرساليات جاهزة للاستلام (${readyShipments.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = StatusReady,
                fontWeight = FontWeight.Bold
              )
            }
            items(readyShipments) { s ->
              AlertShipmentRow(
                shipment = s,
                tag = "جاهزة",
                tagColor = StatusReady,
                onClick = {
                  showAlertsDialog = false
                  onNavigateToShipmentDetail(s.id)
                }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { showAlertsDialog = false }) {
          Text("إغلاق")
        }
      }
    )
  }
}

@Composable
private fun ModernKpiCard(
  title: String,
  count: Int,
  subtitle: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.clickable { onClick() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
          )
        }

        Text(
          text = "$count",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = color
        )
      }

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun QuickActionButton(
  title: String,
  icon: ImageVector,
  tint: Color,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(tint.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = tint,
        modifier = Modifier.size(24.dp)
      )
    }
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      fontSize = 11.sp
    )
  }
}

@Composable
private fun AlertShipmentRow(
  shipment: Shipment,
  tag: String,
  tagColor: Color,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = tagColor.copy(alpha = 0.2f)
      ) {
        Text(
          text = tag,
          color = tagColor,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${shipment.shipmentNumber} - ${shipment.workTypeName}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "المعمل: ${shipment.labName} | د. ${shipment.clinicOrDoctorName}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSearchDialog(
  shipments: List<Shipment>,
  labs: List<com.example.data.models.Laboratory>,
  onSelectShipment: (Long) -> Unit,
  onDismiss: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }

  val filteredShipments = remember(searchQuery, shipments) {
    if (searchQuery.isBlank()) {
      shipments.take(5)
    } else {
      shipments.filter {
        it.shipmentNumber.contains(searchQuery, ignoreCase = true) ||
        it.patientName.contains(searchQuery, ignoreCase = true) ||
        it.clinicOrDoctorName.contains(searchQuery, ignoreCase = true) ||
        it.labName.contains(searchQuery, ignoreCase = true) ||
        it.toothNumbers.contains(searchQuery) ||
        it.workTypeName.contains(searchQuery, ignoreCase = true)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("البحث السريع في النظام", fontWeight = FontWeight.Bold)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("ابحث برقم الإرسالية، السن، المريض، الطبيب...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "مسح")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = "النتائج (${filteredShipments.size}):",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        LazyColumn(
          modifier = Modifier.weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (filteredShipments.isEmpty()) {
            item {
              Text(
                text = "لا توجد نتائج مطابقة لبحثك",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
              )
            }
          }

          items(filteredShipments) { shipment ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectShipment(shipment.id) }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                StatusBadge(status = shipment.status)

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${shipment.shipmentNumber} • ${shipment.workTypeName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "الأسنان: [${shipment.toothNumbers}] | المعمل: ${shipment.labName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Icon(
                  Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
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
