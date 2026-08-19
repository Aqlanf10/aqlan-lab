package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.UserRole
import com.example.data.models.WorkType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PriceDisplay
import com.example.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkTypesScreen(
  viewModel: DentalLabViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val allWorkTypes by viewModel.allWorkTypes.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var showAddDialog by remember { mutableStateOf(false) }
  var editingWorkType by remember { mutableStateOf<WorkType?>(null) }

  val filteredWorkTypes = remember(allWorkTypes, searchQuery) {
    if (searchQuery.isBlank()) allWorkTypes
    else allWorkTypes.filter {
      it.nameAr.contains(searchQuery, ignoreCase = true) ||
      it.nameEn.contains(searchQuery, ignoreCase = true) ||
      it.category.contains(searchQuery, ignoreCase = true) ||
      it.description.contains(searchQuery, ignoreCase = true)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "أنواع وأعمال الأسنان (Work Types)",
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
      if (activeUser.role != UserRole.STAFF) {
        ExtendedFloatingActionButton(
          onClick = { showAddDialog = true },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text("إضافة نوع عمل", fontWeight = FontWeight.Bold) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("fab_add_work_type")
        )
      }
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
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("بحث عن نوع عمل (زركونيا، فينير، E-max...)") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "مسح")
            }
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("work_types_search"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
      )

      if (filteredWorkTypes.isEmpty()) {
        EmptyStateView(
          title = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة" else "لا توجد أنواع أعمال مضافة",
          description = if (searchQuery.isNotEmpty()) "جرب البحث بكلمة أخرى" else "أضف أنواع التركيبات والتعويضات السنية (زركونيا، إيماكس، فينير، أطقم...)",
          actionButton = {
            if (activeUser.role != UserRole.STAFF) {
              Button(onClick = { showAddDialog = true }) {
                Text("إضافة نوع عمل")
              }
            }
          }
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
        ) {
          items(filteredWorkTypes, key = { it.id }) { workType ->
            WorkTypeItemCard(
              workType = workType,
              userRole = activeUser.role,
              currencyCode = currency,
              onEdit = { editingWorkType = workType },
              onToggleActive = { isChecked ->
                viewModel.updateWorkType(workType.copy(isActive = isChecked))
              }
            )
          }
        }
      }
    }

    if (showAddDialog) {
      AddEditWorkTypeDialog(
        workType = null,
        onDismiss = { showAddDialog = false },
        onSave = { nameAr, nameEn, desc, price, cat ->
          viewModel.addWorkType(nameAr, nameEn, desc, price, cat)
          showAddDialog = false
        }
      )
    }

    editingWorkType?.let { wt ->
      AddEditWorkTypeDialog(
        workType = wt,
        onDismiss = { editingWorkType = null },
        onSave = { nameAr, nameEn, desc, price, cat ->
          viewModel.updateWorkType(
            wt.copy(
              nameAr = nameAr,
              nameEn = nameEn,
              description = desc,
              defaultPrice = price,
              category = cat
            )
          )
          editingWorkType = null
        }
      )
    }
  }
}

@Composable
fun WorkTypeItemCard(
  workType: WorkType,
  userRole: UserRole,
  currencyCode: String,
  onEdit: () -> Unit,
  onToggleActive: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = workType.nameAr,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          if (workType.nameEn.isNotEmpty()) {
            Text(
              text = "(${workType.nameEn})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (workType.description.isNotEmpty()) {
          Text(
            text = workType.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
          ) {
            Text(
              text = workType.category,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "السعر الافتراضي:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PriceDisplay(
              amount = workType.defaultPrice,
              userRole = userRole,
              currencyCode = currencyCode,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      if (userRole != UserRole.STAFF) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
          }
          Switch(
            checked = workType.isActive,
            onCheckedChange = onToggleActive
          )
        }
      }
    }
  }
}

@Composable
fun AddEditWorkTypeDialog(
  workType: WorkType?,
  onDismiss: () -> Unit,
  onSave: (nameAr: String, nameEn: String, desc: String, price: Double, category: String) -> Unit
) {
  var nameAr by remember { mutableStateOf(workType?.nameAr ?: "") }
  var nameEn by remember { mutableStateOf(workType?.nameEn ?: "") }
  var description by remember { mutableStateOf(workType?.description ?: "") }
  var priceStr by remember { mutableStateOf(workType?.defaultPrice?.toString() ?: "0.0") }
  var category by remember { mutableStateOf(workType?.category ?: "ثابت Fixed") }

  val categories = listOf("ثابت Fixed", "متحرك Removable", "تقويم Orthodontics", "زراعة Implant", "حشوات وتجميل Restorative")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (workType == null) "إضافة نوع عمل جديد" else "تعديل نوع العمل",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = nameAr,
          onValueChange = { nameAr = it },
          label = { Text("اسم العمل (عربي) *") },
          modifier = Modifier.fillMaxWidth().testTag("work_type_name_ar_input"),
          singleLine = true
        )

        OutlinedTextField(
          value = nameEn,
          onValueChange = { nameEn = it },
          label = { Text("اسم العمل (إنجليزي - اختياري)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        OutlinedTextField(
          value = priceStr,
          onValueChange = { priceStr = it },
          label = { Text("السعر الافتراضي للقطعة *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("work_type_price_input"),
          singleLine = true
        )

        Text("التصنيف الرئيسي:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          categories.take(3).forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.split(" ")[0], fontSize = 11.sp) }
            )
          }
        }

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("وصف مختصر أو مواصفات") },
          modifier = Modifier.fillMaxWidth(),
          maxLines = 2
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (nameAr.isNotBlank()) {
            val price = priceStr.toDoubleOrNull() ?: 0.0
            onSave(nameAr.trim(), nameEn.trim(), description.trim(), price, category)
          }
        },
        enabled = nameAr.isNotBlank()
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
