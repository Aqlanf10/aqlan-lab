package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.data.models.*
import com.example.ui.components.*
import com.example.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabDetailScreen(
  labId: Long,
  viewModel: DentalLabViewModel,
  onNavigateToShipmentDetail: (Long) -> Unit,
  onNavigateToNewShipment: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val allPayments by viewModel.allPayments.collectAsState()
  val allWorkTypes by viewModel.allWorkTypes.collectAsState()
  val allLabPrices by viewModel.allLabPrices.collectAsState()

  val lab = allLabs.find { it.id == labId }

  var selectedTab by remember { mutableIntStateOf(0) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showPaymentDialog by remember { mutableStateOf(false) }

  if (lab == null) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("بيانات المعمل") },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
          }
        )
      }
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    return
  }

  val labShipments = remember(allShipments, labId) {
    allShipments.filter { it.labId == labId }
  }
  val labPayments = remember(allPayments, labId) {
    allPayments.filter { it.labId == labId }
  }

  var selectedStatementCurrency by remember { mutableStateOf<AppCurrency?>(null) }
  val defaultLabCurrency = remember(lab) { AppCurrency.fromCode(lab.defaultCurrency) }

  val currencyBalances = remember(labShipments, labPayments) {
    AppCurrency.ALL.associateWith { curr ->
      val cShipments = labShipments.filter { AppCurrency.fromCode(it.currency) == curr }
      val cPayments = labPayments.filter { AppCurrency.fromCode(it.currency) == curr }
      val billed = cShipments.sumOf { it.totalPrice }
      val paid = cPayments.sumOf { it.amount }
      CurrencyBalance(
        currency = curr,
        totalBilled = billed,
        totalPaid = paid,
        remainingBalance = (billed - paid).coerceAtLeast(0.0),
        shipmentCount = cShipments.size,
        pieceCount = cShipments.sumOf { it.pieceCount }
      )
    }
  }

  val activeCurrencyBalance = remember(currencyBalances, selectedStatementCurrency, defaultLabCurrency) {
    currencyBalances[selectedStatementCurrency ?: defaultLabCurrency] ?: CurrencyBalance(defaultLabCurrency)
  }

  val filteredShipments = remember(labShipments, selectedStatementCurrency) {
    if (selectedStatementCurrency == null) labShipments
    else labShipments.filter { AppCurrency.fromCode(it.currency) == selectedStatementCurrency }
  }

  val filteredPayments = remember(labPayments, selectedStatementCurrency) {
    if (selectedStatementCurrency == null) labPayments
    else labPayments.filter { AppCurrency.fromCode(it.currency) == selectedStatementCurrency }
  }

  val totalBilled = activeCurrencyBalance.totalBilled
  val totalPaid = activeCurrencyBalance.totalPaid
  val remainingBalance = activeCurrencyBalance.remainingBalance

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(lab.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (lab.managerName.isNotEmpty()) {
              Text("المسؤول: ${lab.managerName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          if (lab.phone.isNotEmpty()) {
            IconButton(
              onClick = {
                val dialIntent = android.content.Intent(
                  android.content.Intent.ACTION_DIAL,
                  android.net.Uri.parse("tel:${lab.phone}")
                )
                context.startActivity(dialIntent)
              }
            ) {
              Icon(Icons.Default.Call, contentDescription = "اتصال بالمعمل", tint = MaterialTheme.colorScheme.primary)
            }
          }
          IconButton(onClick = { showEditDialog = true }) {
            Icon(Icons.Default.Edit, contentDescription = "تعديل بيانات المعمل")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    floatingActionButton = {
      if (selectedTab == 0) {
        ExtendedFloatingActionButton(
          onClick = onNavigateToNewShipment,
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text("إرسالية جديدة", fontWeight = FontWeight.Bold) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        )
      } else if (selectedTab == 1 && activeUser.role != UserRole.STAFF) {
        ExtendedFloatingActionButton(
          onClick = { showPaymentDialog = true },
          icon = { Icon(Icons.Default.Payment, contentDescription = null) },
          text = { Text("تسجيل دفعة حساب", fontWeight = FontWeight.Bold) },
          containerColor = Color(0xFF2E7D32),
          contentColor = Color.White
        )
      }
    },
    modifier = modifier
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Top Tabs
      val tabs = if (activeUser.role != UserRole.STAFF) {
        listOf("الإرساليات (${labShipments.size})", "كشف الحساب والمالية", "أسعار المعمل")
      } else {
        listOf("الإرساليات (${labShipments.size})", "بيانات المعمل")
      }

      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface
      ) {
        tabs.forEachIndexed { index, title ->
          Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
          )
        }
      }

      when (selectedTab) {
        0 -> {
          // Shipments Tab
          if (labShipments.isEmpty()) {
            EmptyStateView(
              title = "لا توجد إرساليات لهذا المعمل",
              description = "اضغط على زر إرسالية جديدة لإنشاء طلب عمل لهذا المختبر",
              actionButton = {
                Button(onClick = onNavigateToNewShipment) {
                  Text("إرسالية جديدة")
                }
              }
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
            ) {
              items(labShipments, key = { it.id }) { shipment ->
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
        1 -> {
          if (activeUser.role != UserRole.STAFF) {
            // Statement of Account (كشف الحساب)
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
              contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
            ) {
              // Financial Balance Cards
              item {
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
                    Text(
                      text = "ملخص حساب ${lab.name}",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Column {
                        Text("إجمالي الأعمال", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PriceDisplay(amount = totalBilled, userRole = activeUser.role, currencyCode = currency, style = MaterialTheme.typography.titleLarge)
                      }
                      Column {
                        Text("إجمالي المسدد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PriceDisplay(amount = totalPaid, userRole = activeUser.role, currencyCode = currency, color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleLarge)
                      }
                      Column {
                        Text("الرصيد المتبقي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PriceDisplay(amount = remainingBalance, userRole = activeUser.role, currencyCode = currency, color = Color(0xFFD32F2F), style = MaterialTheme.typography.titleLarge)
                      }
                    }

                    Button(
                      onClick = { showPaymentDialog = true },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                      modifier = Modifier.fillMaxWidth(),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.AddCard, contentDescription = null)
                      Spacer(Modifier.width(8.dp))
                      Text("تسجيل دفعة نقدية / بنكية للمعمل", fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }

              // Payments History Section
              item {
                Text(
                  text = "سجل الدفعات المسددة للمعمل (${labPayments.size})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              if (labPayments.isEmpty()) {
                item {
                  Text(
                    text = "لم يتم تسجيل أي دفعات سابقة لهذا المعمل",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              } else {
                items(labPayments, key = { it.id }) { payment ->
                  PaymentCardItem(
                    payment = payment,
                    currencyCode = currency,
                    onDelete = { viewModel.deletePayment(payment) }
                  )
                }
              }

              // Statement breakdown table preview
              item {
                Text(
                  text = "كشف حساب الحركات المالية (Statement)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              item {
                StatementTableView(
                  shipments = labShipments,
                  payments = labPayments,
                  currencyCode = currency,
                  labName = lab.name,
                  labPhone = lab.phone
                )
              }
            }
          } else {
            // Lab info view for staff
            LabInfoDetailsView(lab = lab)
          }
        }
        2 -> {
          // Custom Pricing Tab (Admin / Accountant)
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
          ) {
            item {
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Text(
                    text = "يمكنك تخصيص أسعار خاصة لمعمل ${lab.name} لكل نوع عمل. إذا لم يتم تحديد سعر مخصص، سيتم استخدام السعر الافتراضي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }

            items(allWorkTypes, key = { it.id }) { workType ->
              val customPriceObj = allLabPrices.find { it.labId == labId && it.workTypeId == workType.id }
              LabWorkTypePriceRow(
                workType = workType,
                customPrice = customPriceObj?.customPrice,
                currencyCode = currency,
                onSavePrice = { newPrice ->
                  viewModel.setLabCustomPrice(labId, workType.id, newPrice)
                }
              )
            }
          }
        }
      }
    }

    if (showEditDialog) {
      AddEditLabDialog(
        lab = lab,
        onDismiss = { showEditDialog = false },
        onSave = { name, phone, address, manager, workTypes, defaultCurr, notes ->
          viewModel.updateLaboratory(
            lab.copy(
              name = name,
              phone = phone,
              address = address,
              managerName = manager,
              offeredWorkTypes = workTypes,
              defaultCurrency = defaultCurr,
              notes = notes
            )
          )
          showEditDialog = false
        }
      )
    }

    if (showPaymentDialog) {
      val exchangeRates by viewModel.exchangeRates.collectAsState()
      RecordPaymentDialog(
        lab = lab,
        currencyCode = lab.defaultCurrency,
        exchangeRates = exchangeRates,
        onDismiss = { showPaymentDialog = false },
        onSave = { amount, method, receiptNo, notes ->
          viewModel.recordPayment(
            labId = lab.id,
            labName = lab.name,
            amount = amount,
            currency = lab.defaultCurrency,
            paymentMethod = method,
            receiptNumber = receiptNo,
            notes = notes
          )
          showPaymentDialog = false
        },
        onSaveMultiCurrency = { amount, targetCurr, paidAmount, paidCurr, exRate, method, receiptNo, notes ->
          viewModel.recordPayment(
            labId = lab.id,
            labName = lab.name,
            amount = amount,
            currency = targetCurr,
            paidAmount = paidAmount,
            paidCurrency = paidCurr,
            exchangeRate = exRate,
            paymentMethod = method,
            receiptNumber = receiptNo,
            notes = notes
          )
          showPaymentDialog = false
        }
      )
    }
  }
}

@Composable
fun LabInfoDetailsView(lab: Laboratory) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("بيانات التواصل والموقع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("الهاتف: ${lab.phone.ifEmpty { "غير مسجل" }}")
        Text("المسؤول: ${lab.managerName.ifEmpty { "غير مسجل" }}")
        Text("العنوان: ${lab.address.ifEmpty { "غير مسجل" }}")
        Text("الأعمال المقدمة: ${lab.offeredWorkTypes.ifEmpty { "جميع الأعمال" }}")
        if (lab.notes.isNotEmpty()) {
          Text("ملاحظات: ${lab.notes}")
        }
      }
    }
  }
}

@Composable
fun PaymentCardItem(
  payment: Payment,
  currencyCode: String,
  onDelete: () -> Unit
) {
  val targetCurr = AppCurrency.fromCode(payment.currency)
  val paidCurr = if (payment.paidCurrency.isNotEmpty()) AppCurrency.fromCode(payment.paidCurrency) else targetCurr
  val isCrossCurrency = payment.paidCurrency.isNotEmpty() && payment.paidCurrency != payment.currency

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
        }

        Column {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "دفعة مسددة: ${payment.amount} ${targetCurr.symbolAr}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF2E7D32)
            )
            CurrencyBadge(currency = targetCurr)
          }
          if (isCrossCurrency) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            ) {
              Text(
                text = "المبلغ المدفوع فعلياً: ${payment.paidAmount} ${paidCurr.symbolAr} (سعر الصرف: ${payment.exchangeRate})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = "${payment.paymentMethod.titleAr} ${if (payment.receiptNumber.isNotEmpty()) "• سند #${payment.receiptNumber}" else ""} • ${DateUtils.formatShortDate(payment.paymentDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (payment.notes.isNotEmpty()) {
            Text(payment.notes, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
          }
        }
      }

      IconButton(onClick = onDelete) {
        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الدفعة", tint = MaterialTheme.colorScheme.outline)
      }
    }
  }
}

@Composable
fun StatementTableView(
  shipments: List<Shipment>,
  payments: List<Payment>,
  currencyCode: String,
  labName: String = "",
  labPhone: String = ""
) {
  val context = LocalContext.current

  // Merge shipments and payments chronologically
  data class StatementLedgerRow(
    val date: Long,
    val refNumber: String,
    val description: String,
    val pieceCount: Int,
    val debit: Double, // مدين (تكلفة العمل)
    val credit: Double, // دائن (الدفعة)
    val runningBalance: Double // الرصيد بعد الحركة
  )

  val (rows, totalPieces, totalDebit, totalCredit, finalBalance) = remember(shipments, payments) {
    val rawItems = mutableListOf<Pair<Long, Any>>()
    shipments.forEach { rawItems.add(Pair(it.orderDate, it)) }
    payments.forEach { rawItems.add(Pair(it.paymentDate, it)) }
    rawItems.sortBy { it.first }

    var currentBalance = 0.0
    var piecesSum = 0
    var debitSum = 0.0
    var creditSum = 0.0
    val list = mutableListOf<StatementLedgerRow>()

    rawItems.forEach { pair ->
      when (val item = pair.second) {
        is Shipment -> {
          currentBalance += item.totalPrice
          piecesSum += item.pieceCount
          debitSum += item.totalPrice
          list.add(
            StatementLedgerRow(
              date = item.orderDate,
              refNumber = item.shipmentNumber,
              description = "${item.workTypeName}${if (item.patientName.isNotEmpty()) " (${item.patientName})" else ""}",
              pieceCount = item.pieceCount,
              debit = item.totalPrice,
              credit = 0.0,
              runningBalance = currentBalance
            )
          )
        }
        is Payment -> {
          currentBalance -= item.amount
          creditSum += item.amount
          list.add(
            StatementLedgerRow(
              date = item.paymentDate,
              refNumber = if (item.receiptNumber.isNotEmpty()) "سند #${item.receiptNumber}" else "دفعة نقدية",
              description = "سداد (${item.paymentMethod.titleAr}) ${item.notes}",
              pieceCount = 0,
              debit = 0.0,
              credit = item.amount,
              runningBalance = currentBalance
            )
          )
        }
      }
    }
    Tuple5(list, piecesSum, debitSum, creditSum, currentBalance)
  }

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // Share Statement Button
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "كشف حساب الحركات والقطع (Ledger Statement)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )

      FilledTonalButton(
        onClick = {
          val statementText = buildString {
            append("═══════════════════════════════════════\n")
            append("      📋 كشف حساب معمل: $labName\n")
            append("═══════════════════════════════════════\n")
            append("📅 التاريخ: ${DateUtils.formatShortDate(System.currentTimeMillis())}\n")
            if (labPhone.isNotEmpty()) append("📞 الهاتف: $labPhone\n")
            append("───────────────────────────────────────\n")
            append("التاريخ | البيان | القطع | مدين | دائن | الرصيد\n")
            append("───────────────────────────────────────\n")
            rows.forEach { r ->
              val pcs = if (r.pieceCount > 0) "${r.pieceCount}" else "-"
              val deb = if (r.debit > 0) "${r.debit}" else "-"
              val crd = if (r.credit > 0) "${r.credit}" else "-"
              append("${DateUtils.formatShortDate(r.date)} | ${r.refNumber} (${r.description}) | قطع: $pcs | مدين: $deb | دائن: $crd | رصيد: ${r.runningBalance} $currencyCode\n")
            }
            append("───────────────────────────────────────\n")
            append("📊 الإجماليات:\n")
            append("• إجمالي عدد القطع: $totalPieces قطعة\n")
            append("• إجمالي المدين (المطالبات): $totalDebit $currencyCode\n")
            append("• إجمالي الدائن (المسدد): $totalCredit $currencyCode\n")
            append("• الرصيد المتبقي المستحق: $finalBalance $currencyCode\n")
            append("═══════════════════════════════════════\n")
          }

          val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, statementText)
            type = "text/plain"
          }
          context.startActivity(android.content.Intent.createChooser(sendIntent, "مشاركة كشف الحساب"))
        },
        shape = RoundedCornerShape(8.dp)
      ) {
        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("مشاركة كشف الحساب")
      }
    }

    Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
        // Table Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("التاريخ/البيان", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f))
          Text("القطع", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
          Text("مدين", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
          Text("دائن", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
          Text("الرصيد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }

        if (rows.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد حركات مسجلة لهذا المعمل حتى الآن", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
          }
        } else {
          rows.forEach { row ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1.8f)) {
                Text(row.refNumber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(row.description, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                Text(DateUtils.formatShortDate(row.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
              }

              // عدد القطع
              Text(
                text = if (row.pieceCount > 0) "${row.pieceCount}" else "-",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )

              // مدين
              Text(
                text = if (row.debit > 0) "${row.debit}" else "-",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (row.debit > 0) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                modifier = Modifier.weight(0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )

              // دائن
              Text(
                text = if (row.credit > 0) "${row.credit}" else "-",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (row.credit > 0) Color(0xFF2E7D32) else Color.LightGray,
                modifier = Modifier.weight(0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )

              // الرصيد التراكمي
              Text(
                text = "${row.runningBalance}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (row.runningBalance > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                modifier = Modifier.weight(1.0f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
          }

          // Table Footer Totals
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
              .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("المجموع", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.8f))
            Text("$totalPieces ق", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            Text("$totalDebit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("$totalCredit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color(0xFF2E7D32))
            Text("$finalBalance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.0f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Color(0xFFD32F2F))
          }
        }
      }
    }
  }
}

// Simple helper tuple
data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

@Composable
fun LabWorkTypePriceRow(
  workType: WorkType,
  customPrice: Double?,
  currencyCode: String,
  onSavePrice: (Double) -> Unit
) {
  var isEditing by remember { mutableStateOf(false) }
  var priceInput by remember(customPrice) {
    mutableStateOf(customPrice?.toString() ?: workType.defaultPrice.toString())
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(workType.nameAr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("السعر الافتراضي: ${workType.defaultPrice} $currencyCode", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
      }

      if (isEditing) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          OutlinedTextField(
            value = priceInput,
            onValueChange = { priceInput = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(90.dp),
            singleLine = true
          )
          IconButton(
            onClick = {
              val parsed = priceInput.toDoubleOrNull()
              if (parsed != null && parsed >= 0) {
                onSavePrice(parsed)
                isEditing = false
              }
            }
          ) {
            Icon(Icons.Default.Check, contentDescription = "حفظ", tint = MaterialTheme.colorScheme.primary)
          }
        }
      } else {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = if (customPrice != null) "$customPrice $currencyCode" else "${workType.defaultPrice} $currencyCode",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
              color = if (customPrice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (customPrice != null) {
              Text("سعر مخصص", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
          }
          IconButton(onClick = { isEditing = true }) {
            Icon(Icons.Default.Edit, contentDescription = "تعديل السعر", modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
fun RecordPaymentDialog(
  lab: Laboratory,
  currencyCode: String = lab.defaultCurrency,
  exchangeRates: com.example.data.models.ExchangeRates = com.example.data.models.ExchangeRates(),
  onDismiss: () -> Unit,
  onSave: (amount: Double, method: PaymentMethod, receiptNumber: String, notes: String) -> Unit,
  onSaveMultiCurrency: ((amount: Double, currency: String, paidAmount: Double, paidCurrency: String, exchangeRate: Double, method: PaymentMethod, receiptNumber: String, notes: String) -> Unit)? = null
) {
  var targetCurrency by remember { mutableStateOf(com.example.data.models.AppCurrency.fromCode(currencyCode)) }
  var paidCurrency by remember { mutableStateOf(com.example.data.models.AppCurrency.fromCode(currencyCode)) }
  var amountText by remember { mutableStateOf("") }
  var customRateText by remember { mutableStateOf("") }
  var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
  var receiptNumber by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var showLiveConverter by remember { mutableStateOf(false) }

  val baseRate = remember(targetCurrency, paidCurrency, exchangeRates) {
    if (targetCurrency == paidCurrency) 1.0
    else {
      // Rate from target to paid (e.g. 1 SAR = 142 YER)
      exchangeRates.convert(1.0, targetCurrency, paidCurrency)
    }
  }

  val activeRate = customRateText.toDoubleOrNull() ?: baseRate
  val parsedTargetAmount = amountText.toDoubleOrNull() ?: 0.0
  val calculatedPaidAmount = if (targetCurrency == paidCurrency) parsedTargetAmount else parsedTargetAmount * activeRate

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFF2E7D32))
        Text("تسجيل دفعة سداد لمعمل ${lab.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Quick Live Currency Converter Button
        OutlinedButton(
          onClick = { showLiveConverter = true },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
          Icon(Icons.Default.CurrencyExchange, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("حاسبة ومحول العملات اللحظي (سعودي/يمني/دولار)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        // Lab Target Account Currency
        Text("1. عملة حساب المعمل المراد تسويتها:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        CurrencySelector(
          selectedCurrency = targetCurrency,
          onCurrencySelected = { 
            targetCurrency = it
            customRateText = ""
          }
        )

        // Amount in Target Currency
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("المبلغ المخصوم من حساب المعمل (${targetCurrency.symbolAr}) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
          singleLine = true
        )

        // Actual Disbursed Currency
        Text("2. العملة المدفوعة فعلياً:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        CurrencySelector(
          selectedCurrency = paidCurrency,
          onCurrencySelected = { 
            paidCurrency = it
            customRateText = ""
          }
        )

        // Exchange rate info if currencies differ
        if (targetCurrency != paidCurrency) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "سعر الصرف (1 ${targetCurrency.symbolAr} =)",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = String.format(java.util.Locale.US, "%.2f %s", activeRate, paidCurrency.symbolAr),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "المبلغ الصادر نقداً / تحويلاً:",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = paidCurrency.formatAmount(calculatedPaidAmount),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF2E7D32)
                )
              }
            }
          }
        }

        Text("طريقة السداد:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          PaymentMethod.values().forEach { method ->
            FilterChip(
              selected = selectedMethod == method,
              onClick = { selectedMethod = method },
              label = { Text(method.titleAr, fontSize = 11.sp) }
            )
          }
        }

        OutlinedTextField(
          value = receiptNumber,
          onValueChange = { receiptNumber = it },
          label = { Text("رقم السند / الإيصال / الحوالة") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("ملاحظات / جهة التحويل") },
          modifier = Modifier.fillMaxWidth(),
          maxLines = 2
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amount = amountText.toDoubleOrNull()
          if (amount != null && amount > 0) {
            if (onSaveMultiCurrency != null) {
              onSaveMultiCurrency(
                amount,
                targetCurrency.name,
                calculatedPaidAmount,
                paidCurrency.name,
                activeRate,
                selectedMethod,
                receiptNumber,
                notes
              )
            } else {
              onSave(amount, selectedMethod, receiptNumber, notes)
            }
          }
        },
        enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
      ) {
        Text("تأكيد تسجيل الدفعة")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )

  if (showLiveConverter) {
    LiveCurrencyConverterDialog(
      exchangeRates = exchangeRates,
      onDismiss = { showLiveConverter = false },
      onFetchLiveRates = { /* handled via preset or live fetch */ },
      onApplyPreset = { preset -> /* preset applied */ },
      onCustomRatesChange = { _, _, _ -> },
      initialFromCurrency = paidCurrency,
      initialToCurrency = targetCurrency,
      initialAmount = amountText.toDoubleOrNull() ?: 1000.0,
      onApplyConvertedAmount = { converted, from, to ->
        paidCurrency = from
        targetCurrency = to
        amountText = converted.toString()
        showLiveConverter = false
      }
    )
  }
}
