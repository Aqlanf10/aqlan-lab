package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Laboratory
import com.example.data.models.Shipment
import com.example.data.models.UserRole
import com.example.data.models.WorkType
import com.example.ui.components.*
import com.example.ui.viewmodel.DentalLabViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditShipmentScreen(
  editShipmentId: Long? = null,
  viewModel: DentalLabViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val activeLabs by viewModel.activeLabs.collectAsState()
  val activeWorkTypes by viewModel.activeWorkTypes.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()

  val existingShipment = remember(editShipmentId, allShipments) {
    if (editShipmentId != null) allShipments.find { it.id == editShipmentId } else null
  }

  var clinicOrDoctorName by remember(existingShipment) {
    mutableStateOf(existingShipment?.clinicOrDoctorName ?: ClinicInfo.DOCTOR_NAME)
  }
  var patientName by remember(existingShipment) {
    mutableStateOf(existingShipment?.patientName ?: "")
  }
  var selectedLab by remember(existingShipment, activeLabs) {
    mutableStateOf(
      if (existingShipment != null) activeLabs.find { it.id == existingShipment.labId } ?: activeLabs.firstOrNull()
      else activeLabs.firstOrNull()
    )
  }
  var selectedWorkType by remember(existingShipment, activeWorkTypes) {
    mutableStateOf(
      if (existingShipment != null) activeWorkTypes.find { it.id == existingShipment.workTypeId } ?: activeWorkTypes.firstOrNull()
      else activeWorkTypes.firstOrNull()
    )
  }
  var selectedCategoryFilter by remember { mutableStateOf("الكل") }

  var selectedTeeth by remember(existingShipment) {
    mutableStateOf(
      if (existingShipment != null && existingShipment.toothNumbers.isNotEmpty()) {
        existingShipment.toothNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
      } else emptyList()
    )
  }
  var pieceCount by remember(existingShipment) {
    mutableIntStateOf(existingShipment?.pieceCount ?: 1)
  }
  var shade by remember(existingShipment) {
    mutableStateOf(existingShipment?.shade ?: "A2")
  }
  var shadeNotes by remember(existingShipment) {
    mutableStateOf(existingShipment?.shadeNotes ?: "")
  }
  var notes by remember(existingShipment) {
    mutableStateOf(existingShipment?.notes ?: "")
  }
  var isUrgent by remember(existingShipment) {
    mutableStateOf(existingShipment?.isUrgent ?: false)
  }
  var discountText by remember(existingShipment) {
    mutableStateOf(existingShipment?.discount?.toString() ?: "0")
  }
  var customUnitPriceText by remember(existingShipment) {
    mutableStateOf(existingShipment?.unitPrice?.toString() ?: "")
  }
  var selectedCurrency by remember(existingShipment, selectedLab) {
    mutableStateOf(existingShipment?.currency ?: selectedLab?.defaultCurrency ?: "SAR")
  }

  // Update default currency when lab changes if creating new shipment
  LaunchedEffect(selectedLab) {
    if (existingShipment == null && selectedLab != null) {
      selectedCurrency = selectedLab!!.defaultCurrency
    }
  }

  // Delivery Date (defaults to +4 days)
  var deliveryDaysOffset by remember { mutableIntStateOf(4) }
  val expectedDeliveryDate = remember(deliveryDaysOffset) {
    System.currentTimeMillis() + (deliveryDaysOffset * 24 * 60 * 60 * 1000L)
  }

  // Dropdown expansion states
  var labDropdownExpanded by remember { mutableStateOf(false) }
  var workTypeDropdownExpanded by remember { mutableStateOf(false) }

  // Saving states & Dialogs
  var isSaving by remember { mutableStateOf(false) }
  var saveSuccessDialogData by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(ShipmentNo, Message)

  // Sync piece count with teeth selection
  LaunchedEffect(selectedTeeth) {
    if (selectedTeeth.isNotEmpty()) {
      pieceCount = selectedTeeth.size
    }
  }

  // Live estimated price calculation for Admin / Accountant
  var estimatedUnit by remember { mutableDoubleStateOf(0.0) }
  var estimatedTotal by remember { mutableDoubleStateOf(0.0) }

  LaunchedEffect(selectedLab, selectedWorkType, pieceCount, discountText, customUnitPriceText) {
    if (selectedLab != null && selectedWorkType != null) {
      val discount = discountText.toDoubleOrNull() ?: 0.0
      val customUnit = customUnitPriceText.toDoubleOrNull()
      if (customUnit != null && customUnit > 0) {
        estimatedUnit = customUnit
        estimatedTotal = ((customUnit * pieceCount) - discount).coerceAtLeast(0.0)
      } else {
        val (unit, total) = viewModel.getEstimatedPrice(
          labId = selectedLab!!.id,
          workTypeId = selectedWorkType!!.id,
          pieceCount = pieceCount,
          discount = discount
        )
        estimatedUnit = unit
        estimatedTotal = total
      }
    }
  }

  val categories = listOf("الكل", "زراعة (Implant)", "تقويم (Orthodontics)", "تجميلي (Cosmetic)", "ثابت (Fixed)", "متحرك (Removable)")

  val filteredWorkTypes = remember(selectedCategoryFilter, activeWorkTypes) {
    if (selectedCategoryFilter == "الكل") {
      activeWorkTypes
    } else {
      val catKeyword = when {
        selectedCategoryFilter.contains("زراعة") -> "زراعة"
        selectedCategoryFilter.contains("تقويم") -> "تقويم"
        selectedCategoryFilter.contains("تجميلي") -> "تجميل"
        selectedCategoryFilter.contains("ثابت") -> "ثابت"
        selectedCategoryFilter.contains("متحرك") -> "متحرك"
        else -> selectedCategoryFilter
      }
      activeWorkTypes.filter { it.category.contains(catKeyword, ignoreCase = true) || it.nameAr.contains(catKeyword, ignoreCase = true) }
    }
  }

  // Helper function to execute save action
  fun performSave(syncToFirestore: Boolean) {
    if (selectedLab == null || selectedWorkType == null) {
      Toast.makeText(context, "يرجى تحديد المعمل ونوع العمل أولاً", Toast.LENGTH_SHORT).show()
      return
    }

    isSaving = true
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val customUnit = customUnitPriceText.toDoubleOrNull()

    if (existingShipment != null) {
      val updated = existingShipment.copy(
        clinicOrDoctorName = clinicOrDoctorName.trim().ifEmpty { ClinicInfo.DOCTOR_NAME },
        patientName = patientName.trim(),
        labId = selectedLab!!.id,
        labName = selectedLab!!.name,
        workTypeId = selectedWorkType!!.id,
        workTypeName = selectedWorkType!!.nameAr,
        pieceCount = pieceCount,
        toothNumbers = selectedTeeth.joinToString(", "),
        shade = shade,
        shadeNotes = shadeNotes,
        expectedDeliveryDate = expectedDeliveryDate,
        notes = notes,
        isUrgent = isUrgent,
        currency = selectedCurrency,
        discount = discount,
        unitPrice = customUnit ?: estimatedUnit,
        totalPrice = estimatedTotal
      )

      viewModel.updateShipmentWithFirestore(
        shipment = updated,
        syncToFirestore = syncToFirestore
      ) { success, msg ->
        isSaving = false
        if (syncToFirestore) {
          saveSuccessDialogData = Pair(updated.shipmentNumber, msg)
        } else {
          Toast.makeText(context, "تم حفظ التعديلات بنجاح", Toast.LENGTH_SHORT).show()
          onNavigateBack()
        }
      }
    } else {
      viewModel.createShipmentWithFirestore(
        clinicOrDoctorName = clinicOrDoctorName.trim().ifEmpty { ClinicInfo.DOCTOR_NAME },
        patientName = patientName.trim(),
        labId = selectedLab!!.id,
        labName = selectedLab!!.name,
        workTypeId = selectedWorkType!!.id,
        workTypeName = selectedWorkType!!.nameAr,
        pieceCount = pieceCount,
        toothNumbers = selectedTeeth.joinToString(", "),
        shade = shade,
        shadeNotes = shadeNotes,
        expectedDeliveryDate = expectedDeliveryDate,
        notes = notes,
        isUrgent = isUrgent,
        currency = selectedCurrency,
        discount = discount,
        customUnitPrice = customUnit,
        syncToFirestore = syncToFirestore
      ) { newId, success, msg ->
        isSaving = false
        if (syncToFirestore) {
          saveSuccessDialogData = Pair("إرسالية جديدة #$newId", msg)
        } else {
          Toast.makeText(context, "تم إنشاء الإرسالية بنجاح", Toast.LENGTH_SHORT).show()
          onNavigateBack()
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = if (editShipmentId != null) "تعديل الإرسالية ${existingShipment?.shipmentNumber}" else "إرسالية جديدة للمعمل",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "مركز د. عقلان الكامل لتقويم وزراعة الأسنان",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          // Cloud sync status badge
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (isOnline) "Firestore متصل" else "محلي",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Price summary row (for Admin / Accountant)
          if (activeUser.role != UserRole.STAFF) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "الإجمالي التقديري للتكلفة:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "($pieceCount قطع × ${estimatedUnit.toInt()} $currency)",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.Gray
                )
              }

              PriceDisplay(
                amount = estimatedTotal,
                userRole = activeUser.role,
                currencyCode = currency,
                style = MaterialTheme.typography.titleLarge
              )
            }
          }

          // Action Buttons: 1) Save to Firestore (Primary) 2) Save locally
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Local Save Only (Secondary)
            OutlinedButton(
              onClick = { performSave(syncToFirestore = false) },
              enabled = !isSaving && selectedLab != null && selectedWorkType != null,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("save_locally_btn")
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("حفظ محلي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Save & Sync to Firebase Firestore (Primary)
            Button(
              onClick = { performSave(syncToFirestore = true) },
              enabled = !isSaving && selectedLab != null && selectedWorkType != null,
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E3A8A),
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1.5f)
                .height(48.dp)
                .testTag("save_firestore_btn")
            ) {
              if (isSaving) {
                CircularProgressIndicator(
                  color = Color.White,
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("جاري الحفظ...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("حفظ في Firestore سحابياً", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }
      }
    },
    modifier = modifier
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 0. Official Center Branding Header Card
      AqlanClinicHeaderCard()

      // 1. Client / Patient & Treating Doctor Information Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
            Icon(Icons.Default.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = "بيانات العميل والمريض والطبيب المعالج",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          // Patient / Client Name Input
          OutlinedTextField(
            value = patientName,
            onValueChange = { patientName = it },
            label = { Text("اسم العميل / المريض *") },
            placeholder = { Text("أدخل اسم المريض الكامل ورقم الملف") },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("patient_input"),
            singleLine = true
          )

          // Doctor Name Input
          OutlinedTextField(
            value = clinicOrDoctorName,
            onValueChange = { clinicOrDoctorName = it },
            label = { Text("الطبيب المعالج / القسم *") },
            placeholder = { Text(ClinicInfo.DOCTOR_NAME) },
            leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("doctor_input"),
            singleLine = true
          )
        }
      }

      // 2. Dental Laboratory & Work Type Selector Card (Implants, Orthodontics, Veneers, etc.)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
            Icon(Icons.Default.Apartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = "تحديد المعمل المنفذ ونوع العمل",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          // Dental Lab Dropdown
          ExposedDropdownMenuBox(
            expanded = labDropdownExpanded,
            onExpandedChange = { labDropdownExpanded = !labDropdownExpanded }
          ) {
            OutlinedTextField(
              value = selectedLab?.name ?: "اختر المعمل المنفذ",
              onValueChange = {},
              readOnly = true,
              label = { Text("المعمل المنفذ للطلب *") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = labDropdownExpanded) },
              leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = null) },
              modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .testTag("lab_selector_dropdown")
            )
            ExposedDropdownMenu(
              expanded = labDropdownExpanded,
              onDismissRequest = { labDropdownExpanded = false }
            ) {
              activeLabs.forEach { lab ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(lab.name, fontWeight = FontWeight.Bold)
                      if (lab.offeredWorkTypes.isNotEmpty()) {
                        Text(lab.offeredWorkTypes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                      }
                    }
                  },
                  onClick = {
                    selectedLab = lab
                    labDropdownExpanded = false
                  }
                )
              }
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          // Fast Work Type Category Selector (Implants, Ortho, Veneers, etc.)
          Text(
            text = "تصنيف ونوع العمل (زراعة، تقويم، تركيبات، فينير):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            categories.forEach { cat ->
              FilterChip(
                selected = selectedCategoryFilter == cat,
                onClick = { selectedCategoryFilter = cat },
                label = { Text(cat, fontSize = 12.sp, fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = {
                  when {
                    cat.contains("زراعة") -> Icon(Icons.Default.Hardware, contentDescription = null, modifier = Modifier.size(16.dp))
                    cat.contains("تقويم") -> Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(16.dp))
                    cat.contains("تجميلي") -> Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    cat.contains("ثابت") -> Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    cat.contains("متحرك") -> Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    else -> Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                  }
                }
              )
            }
          }

          // Work Type Dropdown
          ExposedDropdownMenuBox(
            expanded = workTypeDropdownExpanded,
            onExpandedChange = { workTypeDropdownExpanded = !workTypeDropdownExpanded }
          ) {
            OutlinedTextField(
              value = selectedWorkType?.nameAr ?: "اختر نوع العمل",
              onValueChange = {},
              readOnly = true,
              label = { Text("نوع العمل المطلوب بالتفصيل *") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workTypeDropdownExpanded) },
              leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
              modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .testTag("work_type_dropdown")
            )
            ExposedDropdownMenu(
              expanded = workTypeDropdownExpanded,
              onDismissRequest = { workTypeDropdownExpanded = false }
            ) {
              filteredWorkTypes.forEach { wt ->
                DropdownMenuItem(
                  text = {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column {
                        Text(wt.nameAr, fontWeight = FontWeight.Bold)
                        Text(wt.category, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                      }
                      if (activeUser.role != UserRole.STAFF) {
                        Text("${wt.defaultPrice} $currency", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                      }
                    }
                  },
                  onClick = {
                    selectedWorkType = wt
                    workTypeDropdownExpanded = false
                  }
                )
              }
            }
          }

          // Quick selection pill list of the filtered work types
          if (filteredWorkTypes.isNotEmpty()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              filteredWorkTypes.take(6).forEach { wt ->
                SuggestionChip(
                  onClick = { selectedWorkType = wt },
                  label = { Text(wt.nameAr.split("(").first().trim(), fontSize = 11.sp) },
                  colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (selectedWorkType?.id == wt.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                  ),
                  border = if (selectedWorkType?.id == wt.id) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                )
              }
            }
          }
        }
      }

      // 3. Tooth Shade & Natural Color Tint Selector (VITA Classic & 3D Master)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
            Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = "ظل ولون الأسنان (Tooth Shade Guide)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          ShadeSelector(
            selectedShade = shade,
            shadeNotes = shadeNotes,
            onShadeSelected = { shade = it },
            onShadeNotesChanged = { shadeNotes = it }
          )
        }
      }

      // 4. Interactive FDI Dental Tooth Chart Component
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
            Icon(Icons.Default.Grain, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = "مخطط أرقام ومواضع الأسنان (FDI Chart)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          DentalToothChart(
            selectedTeeth = selectedTeeth,
            onTeethSelectionChanged = { selectedTeeth = it }
          )
        }
      }

      // 5. Piece Count Counter Row
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "عدد القطع / الوحدات (Units):",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (selectedTeeth.isNotEmpty()) "تم التحديد عبر مخطط الأسنان (${selectedTeeth.joinToString()})" else "حدد عدد الأسنان أو اختر من المخطط",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilledTonalIconButton(
              onClick = { if (pieceCount > 1) pieceCount-- },
              modifier = Modifier.testTag("piece_count_minus")
            ) {
              Icon(Icons.Default.Remove, contentDescription = "تقليل")
            }
            Text(
              text = "$pieceCount",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              modifier = Modifier.padding(horizontal = 4.dp)
            )
            FilledTonalIconButton(
              onClick = { pieceCount++ },
              modifier = Modifier.testTag("piece_count_plus")
            ) {
              Icon(Icons.Default.Add, contentDescription = "زيادة")
            }
          }
        }
      }

      // 6. Expected Delivery Date & Urgent Toggle
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = "موعد التسليم وحالة الاستعجال",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          Text(
            text = "موعد التسليم المتوقع: ${DateUtils.formatShortDate(expectedDeliveryDate)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(1 to "غداً (عاجل)", 2 to "خلال يومين", 3 to "3 أيام", 5 to "5 أيام", 7 to "أسبوع").forEach { (days, label) ->
              FilterChip(
                selected = deliveryDaysOffset == days,
                onClick = { deliveryDaysOffset = days },
                label = { Text(label, fontSize = 11.sp) }
              )
            }
          }

          HorizontalDivider()

          // Urgent Switch
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
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = if (isUrgent) Color(0xFFD32F2F) else MaterialTheme.colorScheme.outline
              )
              Column {
                Text(
                  text = "إرسالية عاجلة (Urgent / Rush Case)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (isUrgent) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "وضع علامة أولوية قصوى للمعمل",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = isUrgent,
              onCheckedChange = { isUrgent = it },
              modifier = Modifier.testTag("urgent_switch")
            )
          }

          // General Notes & Lab Directions
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات إضافية وتوجيهات لفني المختبر") },
            placeholder = { Text("مثال: نوع الزرعة، ملاءمة الإطباق، تجربة شمعية، تدرج الشفافية...") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("shipment_notes_input"),
            maxLines = 3
          )
        }
      }

      // 7. Smart Pricing Section (Admin / Accountant ONLY)
      if (activeUser.role != UserRole.STAFF) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = "نظام التسعير الذكي والعملة",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }
              RoleBadge(role = activeUser.role)
            }

            Text(
              text = "اختر العملة المعتمدة لهذه المعاملة:",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )

            CurrencySelector(
              selectedCurrency = com.example.data.models.AppCurrency.fromCode(selectedCurrency),
              onCurrencySelected = { selectedCurrency = it.name }
            )

            Text(
              text = "يتم احتساب السعر تلقائياً وفق قائمة أسعار ${selectedLab?.name ?: "المعمل"} بعملة (${com.example.data.models.AppCurrency.fromCode(selectedCurrency).symbolAr})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = customUnitPriceText,
                onValueChange = { customUnitPriceText = it },
                label = { Text("سعر القطعة (${com.example.data.models.AppCurrency.fromCode(selectedCurrency).symbolAr})") },
                placeholder = { Text("$estimatedUnit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
              )

              OutlinedTextField(
                value = discountText,
                onValueChange = { discountText = it },
                label = { Text("الخصم (${com.example.data.models.AppCurrency.fromCode(selectedCurrency).symbolAr})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
              )
            }

            HorizontalDivider()

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "الإجمالي المحسوب:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              PriceDisplay(
                amount = estimatedTotal,
                userRole = activeUser.role,
                currencyCode = selectedCurrency,
                style = MaterialTheme.typography.titleLarge
              )
            }
          }
        }
      }
    }
  }

  // --- Firestore Save Success Confirmation Dialog ---
  if (saveSuccessDialogData != null) {
    AlertDialog(
      onDismissRequest = {
        saveSuccessDialogData = null
        onNavigateBack()
      },
      icon = {
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color(0xFFE8F5E9)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(32.dp)
          )
        }
      },
      title = {
        Text(
          text = "تم الحفظ والمزامنة السحابية بنجاح!",
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleMedium
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = saveSuccessDialogData!!.second,
            style = MaterialTheme.typography.bodyMedium
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("📋 رقم الإرسالية: ${saveSuccessDialogData!!.first}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              Text("☁️ قاعدة البيانات: Firebase Firestore (Online)", fontSize = 12.sp, color = Color(0xFF1E40AF))
              Text("🏥 المركز: ${ClinicInfo.CLINIC_NAME}", fontSize = 11.sp, color = Color.Gray)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            saveSuccessDialogData = null
            onNavigateBack()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Text("تم ومتابعة الإرساليات")
        }
      }
    )
  }
}
