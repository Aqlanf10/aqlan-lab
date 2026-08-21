package com.aqlanlab.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.AppCurrency
import com.aqlanlab.app.data.models.LabStatus
import com.aqlanlab.app.data.models.Laboratory
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.CurrencyBadge
import com.aqlanlab.app.ui.components.CurrencySelector
import com.aqlanlab.app.ui.components.EmptyStateView
import com.aqlanlab.app.ui.components.PriceDisplay
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import com.aqlanlab.app.ui.viewmodel.LabAccountSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaboratoriesScreen(
  viewModel: DentalLabViewModel,
  onNavigateToLabDetail: (Long) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeUser = viewModel.activeUser.collectAsState().value ?: viewModel.getActiveUserSafe()
  val currency by viewModel.currency.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val labSummaries by viewModel.labAccountSummaries.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var showAddLabDialog by remember { mutableStateOf(false) }

  val filteredSummaries = remember(labSummaries, searchQuery) {
    if (searchQuery.isBlank()) labSummaries
    else labSummaries.filter {
      it.lab.name.contains(searchQuery, ignoreCase = true) ||
      it.lab.managerName.contains(searchQuery, ignoreCase = true) ||
      it.lab.offeredWorkTypes.contains(searchQuery, ignoreCase = true) ||
      it.lab.address.contains(searchQuery, ignoreCase = true)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "دليل المعامل ومختبرات الأسنان",
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
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showAddLabDialog = true },
        icon = { Icon(Icons.Default.AddBusiness, contentDescription = null) },
        text = { Text("إضافة معمل جديد", fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_add_lab")
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
      // Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("بحث عن معمل، مسؤول، نوع عمل...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "مسح")
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("labs_search_input"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
      )

      if (filteredSummaries.isEmpty()) {
        EmptyStateView(
          title = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة" else "لا توجد معامل مضافة",
          description = if (searchQuery.isNotEmpty()) "جرب البحث باسم معمل آخر" else "اضغط على زر إضافة معمل لتسجيل بيانات المختبر",
          icon = Icons.Default.Apartment,
          actionButton = {
            Button(onClick = { showAddLabDialog = true }) {
              Text("إضافة أول معمل")
            }
          }
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
        ) {
          items(filteredSummaries, key = { it.lab.id }) { summary ->
            LabCardItem(
              summary = summary,
              userRole = activeUser.role,
              currencyCode = currency,
              onClick = { onNavigateToLabDetail(summary.lab.id) },
              onCallLab = { phone ->
                if (phone.isNotEmpty()) {
                  val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                  context.startActivity(dialIntent)
                }
              }
            )
          }
        }
      }
    }

    if (showAddLabDialog) {
      AddEditLabDialog(
        lab = null,
        onDismiss = { showAddLabDialog = false },
        onSave = { name, phone, address, manager, workTypes, defaultCurr, notes ->
          viewModel.addLaboratory(name, phone, address, manager, workTypes, notes, defaultCurr)
          showAddLabDialog = false
        }
      )
    }
  }
}

@Composable
fun LabCardItem(
  summary: LabAccountSummary,
  userRole: UserRole,
  currencyCode: String,
  onClick: () -> Unit,
  onCallLab: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val lab = summary.lab

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("lab_card_${lab.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header: Name + Status
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Apartment,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          }
          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = lab.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              CurrencyBadge(currency = summary.defaultCurrency)
            }
            if (lab.managerName.isNotEmpty()) {
              Text(
                text = "المسؤول: ${lab.managerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (lab.status == LabStatus.ACTIVE) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.12f)
        ) {
          Text(
            text = lab.status.titleAr,
            color = if (lab.status == LabStatus.ACTIVE) Color(0xFF2E7D32) else Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      if (lab.offeredWorkTypes.isNotEmpty()) {
        Text(
          text = "الأعمال: ${lab.offeredWorkTypes}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium
        )
      }

      if (lab.phone.isNotEmpty() || lab.address.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (lab.phone.isNotEmpty()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Text(lab.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            if (lab.address.isNotEmpty()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Text(lab.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          if (lab.phone.isNotEmpty() && onCallLab != null) {
            FilledTonalIconButton(
              onClick = { onCallLab(lab.phone) },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(Icons.Default.Call, contentDescription = "اتصال", modifier = Modifier.size(18.dp))
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Footer: Stats & (Financials for Admin)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant
        ) {
          Text(
            text = "${summary.totalShipments} إرساليات (${summary.totalPieces} قطعة)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }

        if (userRole != UserRole.STAFF) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "المتبقي:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PriceDisplay(
              amount = summary.remainingBalance,
              userRole = userRole,
              currencyCode = currencyCode,
              color = if (summary.remainingBalance > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32),
              style = MaterialTheme.typography.labelLarge
            )
          }
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "تفاصيل",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun AddEditLabDialog(
  lab: Laboratory?,
  onDismiss: () -> Unit,
  onSave: (name: String, phone: String, address: String, manager: String, workTypes: String, defaultCurrency: String, notes: String) -> Unit
) {
  var name by remember { mutableStateOf(lab?.name ?: "") }
  var phone by remember { mutableStateOf(lab?.phone ?: "") }
  var address by remember { mutableStateOf(lab?.address ?: "") }
  var managerName by remember { mutableStateOf(lab?.managerName ?: "") }
  var workTypes by remember { mutableStateOf(lab?.offeredWorkTypes ?: "") }
  var defaultCurrency by remember { mutableStateOf(AppCurrency.fromCode(lab?.defaultCurrency ?: "SAR")) }
  var notes by remember { mutableStateOf(lab?.notes ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (lab == null) "إضافة معمل جديد" else "تعديل بيانات المعمل",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("اسم المعمل / المختبر *") },
          modifier = Modifier.fillMaxWidth().testTag("lab_name_input"),
          singleLine = true
        )

        Text("العملة المعتمدة لحساب المعمل:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        CurrencySelector(
          selectedCurrency = defaultCurrency,
          onCurrencySelected = { defaultCurrency = it }
        )

        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("رقم الهاتف للتواصل") },
          modifier = Modifier.fillMaxWidth().testTag("lab_phone_input"),
          singleLine = true
        )
        OutlinedTextField(
          value = managerName,
          onValueChange = { managerName = it },
          label = { Text("اسم المسؤول / الفني الرئيسي") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("العنوان / المدينة") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        OutlinedTextField(
          value = workTypes,
          onValueChange = { workTypes = it },
          label = { Text("أنواع الأعمال (مثال: زركونيا، إيماكس، أطقم)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("ملاحظات") },
          modifier = Modifier.fillMaxWidth(),
          maxLines = 2
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank()) {
            onSave(name.trim(), phone.trim(), address.trim(), managerName.trim(), workTypes.trim(), defaultCurrency.name, notes.trim())
          }
        },
        enabled = name.isNotBlank()
      ) {
        Text("حفظ")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}

