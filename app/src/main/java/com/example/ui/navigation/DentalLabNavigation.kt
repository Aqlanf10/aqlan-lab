package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.models.ShipmentStatus
import com.example.data.models.UserRole
import com.example.ui.screens.*
import com.example.ui.viewmodel.DentalLabViewModel

sealed class Screen(val route: String, val titleAr: String, val icon: ImageVector) {
  object Dashboard : Screen("dashboard", "الرئيسية", Icons.Default.Dashboard)
  object Shipments : Screen("shipments", "الإرساليات", Icons.AutoMirrored.Filled.Assignment)
  object Laboratories : Screen("laboratories", "المعامل", Icons.Default.Apartment)
  object Reports : Screen("reports", "التقارير", Icons.Default.Assessment)
  object Finance : Screen("finance", "المالية", Icons.Default.Payments)
  object Settings : Screen("settings", "الإعدادات", Icons.Default.Settings)

  // Sub-screens
  object NewShipment : Screen("new_shipment", "إرسالية جديدة", Icons.Default.Add)
  object EditShipment : Screen("edit_shipment/{shipmentId}", "تعديل إرسالية", Icons.Default.Edit) {
    fun createRoute(shipmentId: Long) = "edit_shipment/$shipmentId"
  }
  object ShipmentDetail : Screen("shipment_detail/{shipmentId}", "تفاصيل الإرسالية", Icons.Default.Visibility) {
    fun createRoute(shipmentId: Long) = "shipment_detail/$shipmentId"
  }
  object LabDetail : Screen("lab_detail/{labId}", "تفاصيل المعمل", Icons.Default.Apartment) {
    fun createRoute(labId: Long) = "lab_detail/$labId"
  }
  object WorkTypes : Screen("work_types", "أنواع الأعمال", Icons.Default.Category)
  object AuditLog : Screen("audit_log", "سجل العمليات", Icons.Default.History)
  object CloudSync : Screen("cloud_sync", "المزامنة السحابية", Icons.Default.CloudSync)
  object UserManagement : Screen("user_management", "إدارة المستخدمين وكلمات المرور", Icons.Default.ManageAccounts)
  object DailyReport : Screen("daily_report", "التقرير اليومي الشامل", Icons.Default.Summarize)
  object Inventory : Screen("inventory", "المخزون والمواد", Icons.Default.Inventory2)
  object Analytics : Screen("analytics", "الرسوم البيانية والتحليلات", Icons.AutoMirrored.Filled.ShowChart)
  object QrScanner : Screen("qr_scanner", "قارئ الباركود والـ QR", Icons.Default.QrCodeScanner)
  object WhatsAppNotifications : Screen("whatsapp_notifications", "إشعارات وفواتير الواتساب", Icons.AutoMirrored.Filled.Send)
}

@Composable
fun MainAppScaffold(
  viewModel: DentalLabViewModel,
  modifier: Modifier = Modifier
) {
  val isAppLocked by viewModel.isAppLocked.collectAsState()
  val appLockEnabled by viewModel.appLockEnabled.collectAsState()

  // Protected Login Screen (Firebase Auth + Doctor Security)
  if (isAppLocked && appLockEnabled) {
    FirebaseAuthLoginScreen(
      viewModel = viewModel,
      onLoginSuccess = { /* Unlocked and proceeds to MainAppScaffold */ }
    )
    return
  }

  val navController = rememberNavController()
  val activeUser by viewModel.activeUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  var showUserSwitchDialog by remember { mutableStateOf(false) }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  val bottomNavItems = remember(activeUser.role) {
    if (activeUser.role != UserRole.STAFF) {
      listOf(
        Screen.Dashboard,
        Screen.Shipments,
        Screen.Laboratories,
        Screen.Finance,
        Screen.Reports,
        Screen.Settings
      )
    } else {
      listOf(
        Screen.Dashboard,
        Screen.Shipments,
        Screen.Laboratories,
        Screen.Reports,
        Screen.Settings
      )
    }
  }

  val isBottomBarVisible = currentRoute in listOf(
    Screen.Dashboard.route,
    Screen.Shipments.route,
    Screen.Laboratories.route,
    Screen.Finance.route,
    Screen.Reports.route,
    Screen.Settings.route
  )

  Scaffold(
    bottomBar = {
      if (isBottomBarVisible) {
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          tonalElevation = 6.dp
        ) {
          bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
              icon = {
                Icon(
                  imageVector = screen.icon,
                  contentDescription = screen.titleAr
                )
              },
              label = {
                Text(
                  text = screen.titleAr,
                  fontSize = 11.sp,
                  maxLines = 1,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              },
              alwaysShowLabel = true,
              selected = isSelected,
              onClick = {
                if (currentRoute != screen.route) {
                  navController.navigate(screen.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                      saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                  }
                }
              },
              modifier = Modifier.testTag("nav_${screen.route}")
            )
          }
        }
      }
    },
    modifier = modifier
  ) { paddingValues ->
    NavHost(
      navController = navController,
      startDestination = Screen.Dashboard.route,
      modifier = Modifier.padding(paddingValues)
    ) {
      // 1. Dashboard
      composable(Screen.Dashboard.route) {
        DashboardScreen(
          viewModel = viewModel,
          onNavigateToNewShipment = { navController.navigate(Screen.NewShipment.route) },
          onNavigateToShipments = { status ->
            if (status != null) {
              viewModel.setStatusFilter(status)
            }
            navController.navigate(Screen.Shipments.route)
          },
          onNavigateToShipmentDetail = { shipmentId ->
            navController.navigate(Screen.ShipmentDetail.createRoute(shipmentId))
          },
          onNavigateToLabs = { navController.navigate(Screen.Laboratories.route) },
          onNavigateToFinance = { navController.navigate(Screen.Finance.route) },
          onNavigateToReports = { navController.navigate(Screen.Reports.route) },
          onNavigateToDailyReport = { navController.navigate(Screen.DailyReport.route) },
          onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
          onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
          onNavigateToAuditLog = { navController.navigate(Screen.AuditLog.route) },
          onNavigateToCloudSync = { navController.navigate(Screen.CloudSync.route) },
          onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) },
          onNavigateToWhatsApp = { navController.navigate(Screen.WhatsAppNotifications.route) },
          onOpenUserSwitchDialog = { showUserSwitchDialog = true }
        )
      }

      // 2. Shipments List
      composable(Screen.Shipments.route) {
        ShipmentsScreen(
          viewModel = viewModel,
          onNavigateToShipmentDetail = { shipmentId ->
            navController.navigate(Screen.ShipmentDetail.createRoute(shipmentId))
          },
          onNavigateToNewShipment = { navController.navigate(Screen.NewShipment.route) },
          onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) },
          onBack = { navController.popBackStack() }
        )
      }

      // 3. New Shipment
      composable(Screen.NewShipment.route) {
        NewEditShipmentScreen(
          editShipmentId = null,
          viewModel = viewModel,
          onNavigateBack = { navController.popBackStack() }
        )
      }

      // 4. Edit Shipment
      composable(
        route = Screen.EditShipment.route,
        arguments = listOf(navArgument("shipmentId") { type = NavType.LongType })
      ) { backStack ->
        val shipmentId = backStack.arguments?.getLong("shipmentId")
        NewEditShipmentScreen(
          editShipmentId = shipmentId,
          viewModel = viewModel,
          onNavigateBack = { navController.popBackStack() }
        )
      }

      // 5. Shipment Detail
      composable(
        route = Screen.ShipmentDetail.route,
        arguments = listOf(navArgument("shipmentId") { type = NavType.LongType })
      ) { backStack ->
        val shipmentId = backStack.arguments?.getLong("shipmentId") ?: 0L
        ShipmentDetailScreen(
          shipmentId = shipmentId,
          viewModel = viewModel,
          onNavigateToEdit = { sId ->
            navController.navigate(Screen.EditShipment.createRoute(sId))
          },
          onBack = { navController.popBackStack() }
        )
      }

      // 6. Laboratories List
      composable(Screen.Laboratories.route) {
        LaboratoriesScreen(
          viewModel = viewModel,
          onNavigateToLabDetail = { labId ->
            navController.navigate(Screen.LabDetail.createRoute(labId))
          },
          onBack = { navController.popBackStack() }
        )
      }

      // 7. Lab Detail & Statement
      composable(
        route = Screen.LabDetail.route,
        arguments = listOf(navArgument("labId") { type = NavType.LongType })
      ) { backStack ->
        val labId = backStack.arguments?.getLong("labId") ?: 0L
        LabDetailScreen(
          labId = labId,
          viewModel = viewModel,
          onNavigateToShipmentDetail = { shipmentId ->
            navController.navigate(Screen.ShipmentDetail.createRoute(shipmentId))
          },
          onNavigateToNewShipment = { navController.navigate(Screen.NewShipment.route) },
          onBack = { navController.popBackStack() }
        )
      }

      // 8. Finance (Admin/Accountant)
      composable(Screen.Finance.route) {
        FinanceScreen(
          viewModel = viewModel,
          onNavigateToLabDetail = { labId ->
            navController.navigate(Screen.LabDetail.createRoute(labId))
          },
          onBack = { navController.popBackStack() }
        )
      }

      // 9. Reports & Statistics
      composable(Screen.Reports.route) {
        ReportsScreen(
          viewModel = viewModel,
          onNavigateToDailyReport = { navController.navigate(Screen.DailyReport.route) },
          onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
          onBack = { navController.popBackStack() }
        )
      }

      // 9.1 Daily Summary Report (تقرير اليومية الشامل)
      composable(Screen.DailyReport.route) {
        DailySummaryReportScreen(
          viewModel = viewModel,
          onNavigateToShipmentDetail = { shipmentId ->
            navController.navigate(Screen.ShipmentDetail.createRoute(shipmentId))
          },
          onBack = { navController.popBackStack() }
        )
      }

      // 9.2 Data Visualization & Analytics (الرسوم البيانية والتحليلات)
      composable(Screen.Analytics.route) {
        AnalyticsScreen(
          viewModel = viewModel,
          onBack = { navController.popBackStack() }
        )
      }

      // 10. Work Types Catalog
      composable(Screen.WorkTypes.route) {
        WorkTypesScreen(
          viewModel = viewModel,
          onBack = { navController.popBackStack() }
        )
      }

      // 11. Audit Log
      composable(Screen.AuditLog.route) {
        AuditLogScreen(
          viewModel = viewModel,
          onBack = { navController.popBackStack() }
        )
      }

      // 12. Settings
      composable(Screen.Settings.route) {
        SettingsScreen(
          viewModel = viewModel,
          onNavigateToWorkTypes = { navController.navigate(Screen.WorkTypes.route) },
          onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
          onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
          onNavigateToAuditLog = { navController.navigate(Screen.AuditLog.route) },
          onNavigateToCloudSync = { navController.navigate(Screen.CloudSync.route) },
          onNavigateToUserManagement = { navController.navigate(Screen.UserManagement.route) },
          onOpenUserSwitchDialog = { showUserSwitchDialog = true },
          onBack = { navController.popBackStack() }
        )
      }

      // 13. Cloud & Online Sync
      composable(Screen.CloudSync.route) {
        CloudSyncScreen(
          viewModel = viewModel,
          onNavigateBack = { navController.popBackStack() }
        )
      }

      // 14. User Management & Passwords
      composable(Screen.UserManagement.route) {
        UserManagementScreen(
          viewModel = viewModel,
          onBack = { navController.popBackStack() }
        )
      }

      // 15. Inventory & Supplies Tracking
      composable(Screen.Inventory.route) {
        InventoryScreen(
          viewModel = viewModel,
          onBack = { navController.popBackStack() }
        )
      }

      // 16. QR & Barcode Scanner (ماسح الباركود وQR)
      composable(Screen.QrScanner.route) {
        QrScannerScreen(
          viewModel = viewModel,
          onNavigateToShipment = { shipmentId ->
            navController.navigate(Screen.ShipmentDetail.createRoute(shipmentId))
          },
          onBack = { navController.popBackStack() }
        )
      }

      // 17. WhatsApp Notifications & Patient Invoices (إشعارات وفواتير الواتساب)
      composable(Screen.WhatsAppNotifications.route) {
        WhatsAppNotificationsScreen(
          viewModel = viewModel,
          onNavigateBack = { navController.popBackStack() },
          onNavigateToShipmentDetail = { shipmentId ->
            navController.navigate(Screen.ShipmentDetail.createRoute(shipmentId))
          }
        )
      }
    }

    if (showUserSwitchDialog) {
      UserSwitchDialog(
        users = allUsers,
        activeUser = activeUser,
        onUserSelected = { selectedUser ->
          viewModel.switchUser(selectedUser)
        },
        onDismiss = { showUserSwitchDialog = false }
      )
    }
  }
}
