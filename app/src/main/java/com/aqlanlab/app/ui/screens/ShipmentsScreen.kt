package com.aqlanlab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.ShipmentStatus
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.*
import com.aqlanlab.app.ui.theme.*
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentsScreen(
  viewModel: DentalLabViewModel,
  initialStatusFilter: ShipmentStatus? = null,
  onNavigateToShipmentDetail: (Long) -> Unit,
  onNavigateToNewShipment: () -> Unit,
  onNavigateToQrScanner: () -> Unit = {},
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val activeUser = viewModel.activeUser.collectAsState().value ?: viewModel.getActiveUserSafe()
  val currency by viewModel.currency.collectAsState()
  val filteredShipments by viewModel.filteredShipments.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val searchQuery by viewModel.shipmentSearchQuery.collectAsState()
  val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsState()

  var onlyOverdue by remember { mutableStateOf(false) }
  var onlyUrgent by remember { mutableStateOf(false) }
  var selectedLabFilterId by remember { mutableStateOf<Long?>(null) }
  var sortOrder by remember { mutableStateOf("DUE_DATE_ASC") } // DUE_DATE_ASC, ORDER_DATE_DESC, NUMBER_DESC
  var showFilterMenu by remember { mutableStateOf(false) }

  LaunchedEffect(initialStatusFilter) {
    if (initialStatusFilter != null) {
      viewModel.setStatusFilter(initialStatusFilter)
    }
  }

  val finalDisplayShipments = remember(filteredShipments, onlyOverdue, onlyUrgent, selectedLabFilterId, sortOrder) {
    var list = filteredShipments

    if (onlyOverdue) {
      list = list.filter { DateUtils.isLate(it.expectedDeliveryDate, it.status) }
    }

    if (onlyUrgent) {
      list = list.filter { it.isUrgent }
    }

    if (selectedLabFilterId != null) {
      list = list.filter { it.labId == selectedLabFilterId }
    }

    when (sortOrder) {
      "DUE_DATE_ASC" -> list.sortedBy { it.expectedDeliveryDate }
      "ORDER_DATE_DESC" -> list.sortedByDescending { it.orderDate }
      "NUMBER_DESC" -> list.sortedByDescending { it.id }
      else -> list
    }
  }

  val overdueCount = remember(allShipments) {
    allShipments.count { DateUtils.isLate(it.expectedDeliveryDate, it.status) }
  }
  val urgentCount = remember(allShipments) {
    allShipments.count { it.isUrgent }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "إدارة الإرساليات والأعمال",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          IconButton(
            onClick = onNavigateToQrScanner,
            modifier = Modifier.testTag("scan_qr_btn")
          ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح كود QR والباركود", tint = MaterialTheme.colorScheme.primary)
          }

          Box {
            IconButton(
              onClick = { showFilterMenu = true },
              modifier = Modifier.testTag("shipments_sort_menu_btn")
            ) {
              Icon(Icons.Default.Sort, contentDescription = "ترتيب وفلترة")
            }
            DropdownMenu(
              expanded = showFilterMenu,
              onDismissRequest = { showFilterMenu = false }
            ) {
              DropdownMenuItem(
                text = { Text("الأقرب موعداً للتسليم", fontWeight = if (sortOrder == "DUE_DATE_ASC") FontWeight.Bold else FontWeight.Normal) },
                onClick = { sortOrder = "DUE_DATE_ASC"; showFilterMenu = false },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
              )
              DropdownMenuItem(
                text = { Text("الأحدث إنشاءً", fontWeight = if (sortOrder == "ORDER_DATE_DESC") FontWeight.Bold else FontWeight.Normal) },
                onClick = { sortOrder = "ORDER_DATE_DESC"; showFilterMenu = false },
                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) }
              )
              DropdownMenuItem(
                text = { Text("رقم الإرسالية", fontWeight = if (sortOrder == "NUMBER_DESC") FontWeight.Bold else FontWeight.Normal) },
                onClick = { sortOrder = "NUMBER_DESC"; showFilterMenu = false },
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) }
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onNavigateToNewShipment,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("إرسالية جديدة", fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_shipments_new")
      )
    },
    modifier = modifier
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { viewModel.setSearchQuery(it) },
        placeholder = { Text("بحث برقم الإرسالية، السن (11)، المعمل، الطبيب، اللون...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.setSearchQuery("") }) {
              Icon(Icons.Default.Clear, contentDescription = "مسح")
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("shipments_search_bar"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
      )

      // Status & Special Filter Tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        FilterChip(
          selected = selectedStatusFilter == null && !onlyOverdue && !onlyUrgent && selectedLabFilterId == null,
          onClick = {
            viewModel.setStatusFilter(null)
            onlyOverdue = false
            onlyUrgent = false
            selectedLabFilterId = null
          },
          label = { Text("الكل (${allShipments.size})") },
          modifier = Modifier.testTag("filter_all")
        )

        // Special Overdue Alert Chip
        if (overdueCount > 0) {
          FilterChip(
            selected = onlyOverdue,
            onClick = {
              onlyOverdue = !onlyOverdue
              if (onlyOverdue) {
                onlyUrgent = false
                viewModel.setStatusFilter(null)
              }
            },
            label = { Text("⚠️ متأخرة ($overdueCount)") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier.testTag("filter_overdue")
          )
        }

        // Special Urgent Chip
        if (urgentCount > 0) {
          FilterChip(
            selected = onlyUrgent,
            onClick = {
              onlyUrgent = !onlyUrgent
              if (onlyUrgent) {
                onlyOverdue = false
                viewModel.setStatusFilter(null)
              }
            },
            label = { Text("⚡ عاجلة ($urgentCount)") },
            modifier = Modifier.testTag("filter_urgent")
          )
        }

        FilterChip(
          selected = selectedStatusFilter == ShipmentStatus.NEW && !onlyOverdue && !onlyUrgent,
          onClick = {
            onlyOverdue = false
            onlyUrgent = false
            viewModel.setStatusFilter(ShipmentStatus.NEW)
          },
          label = { Text("جديدة (${allShipments.count { it.status == ShipmentStatus.NEW }})") },
          modifier = Modifier.testTag("filter_status_new")
        )

        FilterChip(
          selected = selectedStatusFilter == ShipmentStatus.IN_PROGRESS && !onlyOverdue && !onlyUrgent,
          onClick = {
            onlyOverdue = false
            onlyUrgent = false
            viewModel.setStatusFilter(ShipmentStatus.IN_PROGRESS)
          },
          label = { Text("قيد العمل (${allShipments.count { it.status == ShipmentStatus.IN_PROGRESS }})") },
          modifier = Modifier.testTag("filter_status_in_progress")
        )

        FilterChip(
          selected = selectedStatusFilter == ShipmentStatus.READY && !onlyOverdue && !onlyUrgent,
          onClick = {
            onlyOverdue = false
            onlyUrgent = false
            viewModel.setStatusFilter(ShipmentStatus.READY)
          },
          label = { Text("جاهزة (${allShipments.count { it.status == ShipmentStatus.READY }})") },
          modifier = Modifier.testTag("filter_status_ready")
        )

        FilterChip(
          selected = selectedStatusFilter == ShipmentStatus.RECEIVED && !onlyOverdue && !onlyUrgent,
          onClick = {
            onlyOverdue = false
            onlyUrgent = false
            viewModel.setStatusFilter(ShipmentStatus.RECEIVED)
          },
          label = { Text("تم الاستلام (${allShipments.count { it.status == ShipmentStatus.RECEIVED }})") },
          modifier = Modifier.testTag("filter_status_received")
        )

        FilterChip(
          selected = selectedStatusFilter == ShipmentStatus.CANCELLED && !onlyOverdue && !onlyUrgent,
          onClick = {
            onlyOverdue = false
            onlyUrgent = false
            viewModel.setStatusFilter(ShipmentStatus.CANCELLED)
          },
          label = { Text("ملغاة (${allShipments.count { it.status == ShipmentStatus.CANCELLED }})") },
          modifier = Modifier.testTag("filter_status_cancelled")
        )
      }

      // Filter by Laboratory Row if multiple labs exist
      if (allLabs.size > 1) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "المعمل:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
          )

          allLabs.forEach { lab ->
            InputChip(
              selected = selectedLabFilterId == lab.id,
              onClick = {
                selectedLabFilterId = if (selectedLabFilterId == lab.id) null else lab.id
              },
              label = { Text(lab.name, fontSize = 11.sp) }
            )
          }
        }
      }

      // Shipments List
      if (finalDisplayShipments.isEmpty()) {
        EmptyStateView(
          title = if (searchQuery.isNotEmpty() || onlyOverdue || onlyUrgent || selectedLabFilterId != null) "لا توجد نتائج مطابقة لبحثك أو للفلتر المحدد" else "لا توجد إرساليات في هذه الفئة",
          description = if (searchQuery.isNotEmpty()) "جرب البحث برقم السن أو اسم المعمل أو الطبيب" else "قم بإضافة إرسالية جديدة للمعمل",
          actionButton = {
            Button(onClick = onNavigateToNewShipment) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("إرسالية جديدة")
            }
          }
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
        ) {
          items(finalDisplayShipments, key = { it.id }) { shipment ->
            ShipmentCardItem(
              shipment = shipment,
              userRole = activeUser.role,
              currencyCode = currency,
              onClick = { onNavigateToShipmentDetail(shipment.id) },
              onQuickStatusChange = { newStatus ->
                viewModel.updateShipmentStatus(shipment.id, newStatus)
              }
            )
          }
        }
      }
    }
  }
}
