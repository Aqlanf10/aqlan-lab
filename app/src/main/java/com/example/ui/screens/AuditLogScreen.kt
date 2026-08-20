package com.example.ui.screens

import androidx.compose.foundation.background
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
import com.example.data.models.AuditActionType
import com.example.data.models.AuditLog
import com.example.ui.components.DateUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RoleBadge
import com.example.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
  viewModel: DentalLabViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val recentLogs by viewModel.recentAuditLogs.collectAsState()
  var selectedActionFilter by remember { mutableStateOf<AuditActionType?>(null) }

  val filteredLogs = remember(recentLogs, selectedActionFilter) {
    if (selectedActionFilter == null) recentLogs
    else recentLogs.filter { it.actionType == selectedActionFilter }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "سجل العمليات والرقابة (Audit Log)",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "توثيق العمليات: من فعل ماذا ومتى",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Action Filter Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = selectedActionFilter == null,
          onClick = { selectedActionFilter = null },
          label = { Text("كافة العمليات (${recentLogs.size})") }
        )

        FilterChip(
          selected = selectedActionFilter == AuditActionType.CREATE_SHIPMENT,
          onClick = { selectedActionFilter = AuditActionType.CREATE_SHIPMENT },
          label = { Text("إنشاء إرسالية") }
        )

        FilterChip(
          selected = selectedActionFilter == AuditActionType.UPDATE_STATUS,
          onClick = { selectedActionFilter = AuditActionType.UPDATE_STATUS },
          label = { Text("تحديث الحالة") }
        )

        FilterChip(
          selected = selectedActionFilter == AuditActionType.RECORD_PAYMENT,
          onClick = { selectedActionFilter = AuditActionType.RECORD_PAYMENT },
          label = { Text("الدفعات المالية") }
        )

        FilterChip(
          selected = selectedActionFilter == AuditActionType.UPDATE_PRICE,
          onClick = { selectedActionFilter = AuditActionType.UPDATE_PRICE },
          label = { Text("تعديل الأسعار") }
        )
      }

      if (filteredLogs.isEmpty()) {
        EmptyStateView(
          title = "لا توجد سجلات",
          description = "يتم تسجيل كافة الحركات والإجراءات تلقائياً لمنع التلاعب والتدقيق الإداري"
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
        ) {
          items(filteredLogs, key = { it.id }) { log ->
            AuditLogCardItem(log = log)
          }
        }
      }
    }
  }
}

@Composable
fun AuditLogCardItem(log: AuditLog) {
  val (actionColor, actionIcon) = when (log.actionType) {
    AuditActionType.CREATE_SHIPMENT -> Pair(Color(0xFF0288D1), Icons.Default.AddCircleOutline)
    AuditActionType.UPDATE_STATUS -> Pair(Color(0xFFED6C02), Icons.Default.Autorenew)
    AuditActionType.RECORD_PAYMENT -> Pair(Color(0xFF2E7D32), Icons.Default.Receipt)
    AuditActionType.UPDATE_PRICE -> Pair(Color(0xFF7B1FA2), Icons.Default.PriceChange)
    AuditActionType.EDIT_SHIPMENT -> Pair(Color(0xFF00897B), Icons.Default.Edit)
    AuditActionType.DELETE_SHIPMENT -> Pair(Color(0xFFD32F2F), Icons.Default.Delete)
    AuditActionType.ADD_LAB, AuditActionType.UPDATE_LAB -> Pair(Color(0xFF3949AB), Icons.Default.Apartment)
    AuditActionType.SWITCH_USER -> Pair(Color(0xFF5E35B1), Icons.Default.Person)
    AuditActionType.DEVICE_REGISTRATION -> Pair(Color(0xFF0284C7), Icons.Default.PhonelinkSetup)
    AuditActionType.DEVICE_APPROVAL -> Pair(Color(0xFF10B981), Icons.Default.VerifiedUser)
    AuditActionType.DEVICE_BLOCKED -> Pair(Color(0xFFEF4444), Icons.Default.Block)
    AuditActionType.USER_STATUS_CHANGE -> Pair(Color(0xFFF59E0B), Icons.Default.ManageAccounts)
    AuditActionType.LOGIN_SUCCESS -> Pair(Color(0xFF10B981), Icons.Default.Login)
    AuditActionType.LOGIN_FAILED -> Pair(Color(0xFFDC2626), Icons.Default.NoEncryption)
    AuditActionType.SECURITY_WARNING -> Pair(Color(0xFFB91C1C), Icons.Default.Warning)
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.testTag("audit_item_${log.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(actionColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = actionIcon,
          contentDescription = null,
          tint = actionColor,
          modifier = Modifier.size(20.dp)
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = log.userName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            RoleBadge(role = log.userRole)
          }

          Text(
            text = DateUtils.formatDateTime(log.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
          )
        }

        Text(
          text = log.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = actionColor.copy(alpha = 0.1f)
        ) {
          Text(
            text = log.actionType.titleAr,
            color = actionColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }
  }
}
