package com.aqlanlab.app.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.Payment
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.data.models.ShipmentStatus
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.ui.components.EmptyStateView
import com.aqlanlab.app.ui.components.PriceDisplay
import com.aqlanlab.app.ui.components.StatusBadge
import com.aqlanlab.app.ui.theme.DentalPrimary
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import com.aqlanlab.app.utils.DailyPdfGenerator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryReportScreen(
  viewModel: DentalLabViewModel,
  onNavigateToShipmentDetail: (Long) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val activeUser = viewModel.activeUser.collectAsState().value ?: viewModel.getActiveUserSafe()
  val currency by viewModel.currency.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val allPayments by viewModel.allPayments.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()

  // Selected Date State (Calendar day starting at 00:00:00)
  var selectedDateMillis by remember {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    mutableLongStateOf(cal.timeInMillis)
  }

  var selectedTab by remember { mutableIntStateOf(0) }
  var showDatePicker by remember { mutableStateOf(false) }
  var generatedPdfFile by remember { mutableStateOf<File?>(null) }
  var showPdfSuccessDialog by remember { mutableStateOf(false) }

  // Day Range Calculation (Start & End of the selected day)
  val (dayStartMillis, dayEndMillis) = remember(selectedDateMillis) {
    val cal = Calendar.getInstance().apply {
      timeInMillis = selectedDateMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    val end = cal.timeInMillis
    Pair(start, end)
  }

  // 1. Completed / Received / Ready Shipments for the day
  val completedShipmentsToday = remember(allShipments, dayStartMillis, dayEndMillis) {
    allShipments.filter { s ->
      (s.status == ShipmentStatus.RECEIVED || s.status == ShipmentStatus.READY) &&
        ((s.actualReceivedDate != null && s.actualReceivedDate in dayStartMillis..dayEndMillis) ||
         (s.actualReceivedDate == null && s.orderDate in dayStartMillis..dayEndMillis))
    }
  }

  // 2. All Shipments ordered or updated today
  val allShipmentsToday = remember(allShipments, dayStartMillis, dayEndMillis) {
    allShipments.filter { s ->
      s.orderDate in dayStartMillis..dayEndMillis || (s.actualReceivedDate != null && s.actualReceivedDate in dayStartMillis..dayEndMillis)
    }
  }

  // 3. Financial Payments recorded on the selected day
  val paymentsToday = remember(allPayments, dayStartMillis, dayEndMillis) {
    allPayments.filter { it.paymentDate in dayStartMillis..dayEndMillis }
  }

  // --- Day Totals Calculations ---
  val totalCompletedPieces = remember(completedShipmentsToday) {
    completedShipmentsToday.sumOf { it.pieceCount }
  }

  val totalCompletedBilled = remember(completedShipmentsToday) {
    completedShipmentsToday.sumOf { it.totalPrice }
  }

  val totalAllShipmentsCostToday = remember(allShipmentsToday) {
    allShipmentsToday.sumOf { it.totalPrice }
  }

  val totalAllPiecesToday = remember(allShipmentsToday) {
    allShipmentsToday.sumOf { it.pieceCount }
  }

  val totalPaymentsAmountToday = remember(paymentsToday) {
    paymentsToday.sumOf { it.amount }
  }

  val netDailyBalance = totalCompletedBilled - totalPaymentsAmountToday

  // Formatted date string in Arabic
  val formattedDayTitle = remember(selectedDateMillis) {
    val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
    sdf.format(Date(selectedDateMillis))
  }

  // Lab Breakdown for Today
  val labDailyBreakdown = remember(allLabs, completedShipmentsToday, paymentsToday) {
    allLabs.map { lab ->
      val labCompleted = completedShipmentsToday.filter { it.labId == lab.id }
      val labPay = paymentsToday.filter { it.labId == lab.id }
      val pieces = labCompleted.sumOf { it.pieceCount }
      val billed = labCompleted.sumOf { it.totalPrice }
      val paid = labPay.sumOf { it.amount }
      LabDaySummary(lab.id, lab.name, labCompleted.size, pieces, billed, paid)
    }.filter { it.completedShipmentsCount > 0 || it.totalPaid > 0 }
  }

  // PDF Export helper
  val exportPdfAndHandle: (shareDirectly: Boolean, printDirectly: Boolean) -> Unit = { shareDirectly, printDirectly ->
    val file = DailyPdfGenerator.generateDailySummaryPdf(
      context = context,
      dateTitle = formattedDayTitle,
      dateMillis = selectedDateMillis,
      completedShipments = completedShipmentsToday,
      allShipments = allShipmentsToday,
      payments = paymentsToday,
      labBreakdown = labDailyBreakdown,
      totalCompletedPieces = totalCompletedPieces,
      totalCompletedBilled = totalCompletedBilled,
      totalPayments = totalPaymentsAmountToday,
      netBalance = netDailyBalance,
      currency = currency,
      generatedByName = activeUser.fullName,
      userRole = activeUser.role
    )
    if (file != null) {
      generatedPdfFile = file
      if (shareDirectly) {
        DailyPdfGenerator.sharePdf(context, file)
      } else if (printDirectly) {
        DailyPdfGenerator.openOrPrintPdf(context, file)
      } else {
        showPdfSuccessDialog = true
      }
    } else {
      Toast.makeText(context, "حدث خطأ أثناء إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "التقرير اليومي الشامل",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = formattedDayTitle,
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
        actions = {
          // PDF Export Button
          IconButton(
            onClick = { exportPdfAndHandle(false, false) },
            modifier = Modifier.testTag("export_daily_pdf_btn")
          ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", tint = MaterialTheme.colorScheme.primary)
          }

          // Share Daily Report Text Button
          IconButton(
            onClick = {
              val shareText = generateDailyReportText(
                dateTitle = formattedDayTitle,
                completedShipments = completedShipmentsToday,
                allShipments = allShipmentsToday,
                payments = paymentsToday,
                labBreakdown = labDailyBreakdown,
                totalCompletedPieces = totalCompletedPieces,
                totalCompletedBilled = totalCompletedBilled,
                totalPayments = totalPaymentsAmountToday,
                netBalance = netDailyBalance,
                currency = currency
              )
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير اليومي"))
            },
            modifier = Modifier.testTag("share_daily_report_btn")
          ) {
            Icon(Icons.Default.Share, contentDescription = "مشاركة التقرير", tint = MaterialTheme.colorScheme.primary)
          }

          // Pick Calendar Date
          IconButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Default.CalendarMonth, contentDescription = "اختيار التاريخ")
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
    ) {
      // 1. Date Navigator Bar (< اليوم السابق | اليوم الحالي | اليوم التالي >)
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          // Previous Day
          IconButton(
            onClick = {
              val cal = Calendar.getInstance().apply {
                timeInMillis = selectedDateMillis
                add(Calendar.DAY_OF_YEAR, -1)
              }
              selectedDateMillis = cal.timeInMillis
            }
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "اليوم السابق")
          }

          // Today Button
          FilledTonalButton(
            onClick = {
              val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
              }
              selectedDateMillis = cal.timeInMillis
            },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
          ) {
            Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("اليوم", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }

          // Next Day
          IconButton(
            onClick = {
              val cal = Calendar.getInstance().apply {
                timeInMillis = selectedDateMillis
                add(Calendar.DAY_OF_YEAR, 1)
              }
              selectedDateMillis = cal.timeInMillis
            }
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "اليوم التالي")
          }
        }
      }

      // 2. Tab Navigation
      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 12.dp
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("الملخص والمؤشرات", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("المكتمل والمنجز (${completedShipmentsToday.size})", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("الحركات المالية (${paymentsToday.size})", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("كافة الإرساليات (${allShipmentsToday.size})", fontWeight = FontWeight.Bold) }
        )
      }

      when (selectedTab) {
        0 -> {
          // --- Overview & Totals Tab ---
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp)
          ) {
            // Official Center Branding Header
            item {
              com.aqlanlab.app.ui.components.AqlanClinicHeaderCard()
            }

            // Daily Grand Total Card
            item {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
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
                      Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                      Text(
                        text = "إجماليات اليوم الحسابية والإنتاجية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                    }
                    Text(
                      text = DateUtils.formatShortDate(selectedDateMillis),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold
                    )
                  }

                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                  // 2x2 Grid of Key Day Totals
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    DailyKpiMetricBox(
                      title = "القطع المكتملة",
                      value = "$totalCompletedPieces قطعة",
                      subtitle = "${completedShipmentsToday.size} إرسالية منجزة",
                      icon = Icons.Default.CheckCircle,
                      iconColor = Color(0xFF2E7D32),
                      modifier = Modifier.weight(1f)
                    )

                    DailyKpiMetricBox(
                      title = "تكلفة الأعمال المنجزة",
                      value = if (activeUser.role != UserRole.STAFF) "$totalCompletedBilled $currency" else "•••• $currency",
                      subtitle = "المطلوب للمختبرات",
                      icon = Icons.Default.MonetizationOn,
                      iconColor = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.weight(1f)
                    )
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    DailyKpiMetricBox(
                      title = "المدفوعات المسددة",
                      value = if (activeUser.role != UserRole.STAFF) "$totalPaymentsAmountToday $currency" else "•••• $currency",
                      subtitle = "${paymentsToday.size} سند دفع",
                      icon = Icons.Default.Payments,
                      iconColor = Color(0xFF1976D2),
                      modifier = Modifier.weight(1f)
                    )

                    DailyKpiMetricBox(
                      title = "صافي حركة اليوم",
                      value = if (activeUser.role != UserRole.STAFF) "$netDailyBalance $currency" else "•••• $currency",
                      subtitle = if (netDailyBalance >= 0) "مستحق إضافي" else "فائض سداد",
                      icon = Icons.Default.AccountBalance,
                      iconColor = if (netDailyBalance > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                      modifier = Modifier.weight(1f)
                    )
                  }
                }
              }
            }

            // Breakdown by Laboratory Card
            if (labDailyBreakdown.isNotEmpty()) {
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Text(
                      text = "توزيع إنتاجية ومستحقات المعامل لليوم",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )

                    labDailyBreakdown.forEach { labSummary ->
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Column {
                          Text(labSummary.labName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                          Text(
                            text = "${labSummary.completedShipmentsCount} إرساليات (${labSummary.totalPieces} قطعة)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                          )
                        }

                        if (activeUser.role != UserRole.STAFF) {
                          Column(horizontalAlignment = Alignment.End) {
                            Text(
                              text = "منجز: ${labSummary.totalBilled} $currency",
                              style = MaterialTheme.typography.bodySmall,
                              fontWeight = FontWeight.Bold
                            )
                            if (labSummary.totalPaid > 0) {
                              Text(
                                text = "مسدد: ${labSummary.totalPaid} $currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32)
                              )
                            }
                          }
                        }
                      }
                      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                  }
                }
              }
            }

            // Quick Share & Export Daily Report Card
            item {
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "تصدير ومشاركة التقرير اليومي",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                      Text(
                        text = "PDF & Text",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }

                  Text(
                    text = "يمكنك تصدير التقرير كملف PDF عالي الجودة للطباعة أو الإرسال المباشر، أو مشاركته كنص منسق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  // PDF Action Row
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Button(
                      onClick = { exportPdfAndHandle(false, true) },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("open_print_pdf_btn"),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                      Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("طباعة / فتح PDF")
                    }

                    FilledTonalButton(
                      onClick = { exportPdfAndHandle(true, false) },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("share_pdf_file_btn")
                    ) {
                      Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("مشاركة PDF")
                    }
                  }

                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                  // Text Action Row
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    OutlinedButton(
                      onClick = {
                        val shareText = generateDailyReportText(
                          dateTitle = formattedDayTitle,
                          completedShipments = completedShipmentsToday,
                          allShipments = allShipmentsToday,
                          payments = paymentsToday,
                          labBreakdown = labDailyBreakdown,
                          totalCompletedPieces = totalCompletedPieces,
                          totalCompletedBilled = totalCompletedBilled,
                          totalPayments = totalPaymentsAmountToday,
                          netBalance = netDailyBalance,
                          currency = currency
                        )
                        clipboardManager.setText(AnnotatedString(shareText))
                        Toast.makeText(context, "تم نسخ التقرير إلى الحافظة", Toast.LENGTH_SHORT).show()
                      },
                      modifier = Modifier.weight(1f)
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("نسخ النص")
                    }

                    OutlinedButton(
                      onClick = {
                        val shareText = generateDailyReportText(
                          dateTitle = formattedDayTitle,
                          completedShipments = completedShipmentsToday,
                          allShipments = allShipmentsToday,
                          payments = paymentsToday,
                          labBreakdown = labDailyBreakdown,
                          totalCompletedPieces = totalCompletedPieces,
                          totalCompletedBilled = totalCompletedBilled,
                          totalPayments = totalPaymentsAmountToday,
                          netBalance = netDailyBalance,
                          currency = currency
                        )
                        val sendIntent = Intent().apply {
                          action = Intent.ACTION_SEND
                          putExtra(Intent.EXTRA_TEXT, shareText)
                          type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير اليومي"))
                      },
                      modifier = Modifier.weight(1f)
                    ) {
                      Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("مشاركة النص")
                    }
                  }
                }
              }
            }
          }
        }

        1 -> {
          // --- Completed Shipments Tab ---
          if (completedShipmentsToday.isEmpty()) {
            EmptyStateView(
              title = "لا توجد إرساليات مكتملة في هذا اليوم",
              description = "لم يتم تسجيل إرساليات منجزة أو مستلمة بتاريخ $formattedDayTitle"
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp)
            ) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "الإرساليات المكتملة (${completedShipmentsToday.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "$totalCompletedPieces قطعة | $totalCompletedBilled $currency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              items(completedShipmentsToday, key = { it.id }) { shipment ->
                DailyShipmentItemCard(
                  shipment = shipment,
                  currencyCode = currency,
                  userRole = activeUser.role,
                  onClick = { onNavigateToShipmentDetail(shipment.id) }
                )
              }
            }
          }
        }

        2 -> {
          // --- Financial Transactions / Payments Tab ---
          if (paymentsToday.isEmpty()) {
            EmptyStateView(
              title = "لا توجد حركات مالية مسجلة اليوم",
              description = "لم يتم تسجيل سندات دفع أو سداد في تاريخ $formattedDayTitle"
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp)
            ) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "سندات الدفع والمسدد (${paymentsToday.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "المجموع: $totalPaymentsAmountToday $currency",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              items(paymentsToday, key = { it.id }) { payment ->
                DailyPaymentItemCard(
                  payment = payment,
                  currencyCode = currency
                )
              }
            }
          }
        }

        3 -> {
          // --- All Shipments for the Day Tab ---
          if (allShipmentsToday.isEmpty()) {
            EmptyStateView(
              title = "لا توجد إرساليات مسجلة في هذا اليوم",
              description = "لم يتم إنشاء أو تحديث أي إرسالية بتاريخ $formattedDayTitle"
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp)
            ) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "كافة إرساليات اليوم (${allShipmentsToday.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "$totalAllPiecesToday قطعة | $totalAllShipmentsCostToday $currency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              items(allShipmentsToday, key = { it.id }) { shipment ->
                DailyShipmentItemCard(
                  shipment = shipment,
                  currencyCode = currency,
                  userRole = activeUser.role,
                  onClick = { onNavigateToShipmentDetail(shipment.id) }
                )
              }
            }
          }
        }
      }
    }

    // --- Date Picker Dialog ---
    if (showDatePicker) {
      val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis
      )
      DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
          TextButton(
            onClick = {
              datePickerState.selectedDateMillis?.let {
                val cal = Calendar.getInstance().apply {
                  timeInMillis = it
                  set(Calendar.HOUR_OF_DAY, 0)
                  set(Calendar.MINUTE, 0)
                  set(Calendar.SECOND, 0)
                  set(Calendar.MILLISECOND, 0)
                }
                selectedDateMillis = cal.timeInMillis
              }
              showDatePicker = false
            }
          ) {
            Text("تحديد")
          }
        },
        dismissButton = {
          TextButton(onClick = { showDatePicker = false }) {
            Text("إلغاء")
          }
        }
      ) {
        DatePicker(state = datePickerState)
      }
    }

    // --- PDF Export Success Dialog ---
    if (showPdfSuccessDialog && generatedPdfFile != null) {
      val file = generatedPdfFile!!
      AlertDialog(
        onDismissRequest = { showPdfSuccessDialog = false },
        icon = {
          Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        },
        title = {
          Text("تم إنشاء ملف الـ PDF بنجاح", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "الملف جاهز: ${file.name}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.Center
            )
            Text(
              text = "يمكنك فتح التقرير للطباعة أو مشاركته فوراً عبر التطبيقات الأخرى.",
              style = MaterialTheme.typography.bodySmall,
              color = Color.Gray,
              textAlign = TextAlign.Center
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showPdfSuccessDialog = false
              DailyPdfGenerator.openOrPrintPdf(context, file)
            }
          ) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("فتح / طباعة")
          }
        },
        dismissButton = {
          FilledTonalButton(
            onClick = {
              showPdfSuccessDialog = false
              DailyPdfGenerator.sharePdf(context, file)
            }
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("مشاركة")
          }
        }
      )
    }
  }
}

@Composable
fun DailyKpiMetricBox(
  title: String,
  value: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = Color.Gray,
        fontSize = 11.sp
      )
    }
  }
}

@Composable
fun DailyShipmentItemCard(
  shipment: Shipment,
  currencyCode: String,
  userRole: UserRole,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = shipment.shipmentNumber,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          StatusBadge(status = shipment.status)
        }

        Text(
          text = "${shipment.patientName.ifEmpty { "مريض بدون اسم" }} • ${shipment.clinicOrDoctorName.ifEmpty { "الطبيب" }}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "معمل: ${shipment.labName}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
          )
          Text("•", color = Color.Gray)
          Text(
            text = "${shipment.pieceCount} قطع (${shipment.workTypeName})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PriceDisplay(
          amount = shipment.totalPrice,
          userRole = userRole,
          currencyCode = currencyCode,
          style = MaterialTheme.typography.titleSmall
        )

        Text(
          text = DateUtils.formatShortDate(shipment.orderDate),
          style = MaterialTheme.typography.labelSmall,
          color = Color.Gray,
          fontSize = 10.sp
        )
      }
    }
  }
}

@Composable
fun DailyPaymentItemCard(
  payment: Payment,
  currencyCode: String
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "سداد إلى: ${payment.labName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${payment.paymentMethod.titleAr} ${if (payment.receiptNumber.isNotEmpty()) "• سند #${payment.receiptNumber}" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
          )
          if (payment.notes.isNotEmpty()) {
            Text(
              text = payment.notes,
              style = MaterialTheme.typography.labelSmall,
              color = Color.DarkGray
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "${payment.amount} $currencyCode",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF2E7D32)
        )
        Text(
          text = DateUtils.formatShortDate(payment.paymentDate),
          style = MaterialTheme.typography.labelSmall,
          color = Color.Gray,
          fontSize = 10.sp
        )
      }
    }
  }
}

// Data class for Lab Daily Summary
data class LabDaySummary(
  val labId: Long,
  val labName: String,
  val completedShipmentsCount: Int,
  val totalPieces: Int,
  val totalBilled: Double,
  val totalPaid: Double
)

// Helper function to build daily summary text
private fun generateDailyReportText(
  dateTitle: String,
  completedShipments: List<Shipment>,
  allShipments: List<Shipment>,
  payments: List<Payment>,
  labBreakdown: List<LabDaySummary>,
  totalCompletedPieces: Int,
  totalCompletedBilled: Double,
  totalPayments: Double,
  netBalance: Double,
  currency: String
): String {
  return buildString {
    append("═══════════════════════════════════════\n")
    append("🏥 ${com.aqlanlab.app.ui.components.ClinicInfo.CLINIC_NAME}\n")
    append("🦷 ${com.aqlanlab.app.ui.components.ClinicInfo.SPECIALTIES}\n")
    append("📍 ${com.aqlanlab.app.ui.components.ClinicInfo.ADDRESS}\n")
    append("📞 ${com.aqlanlab.app.ui.components.ClinicInfo.PHONES}\n")
    append("═══════════════════════════════════════\n")
    append("      📋 التقرير اليومي الشامل لحركات المعامل      \n")
    append("───────────────────────────────────────\n")
    append("📅 اليوم والتاريخ: $dateTitle\n")
    append("───────────────────────────────────────\n")
    append("📊 ملخص المؤشرات الإجمالية:\n")
    append("• الإرساليات المكتملة والمنجزة: ${completedShipments.size} إرسالية\n")
    append("• إجمالي القطع المنجزة: $totalCompletedPieces قطعة\n")
    append("• إجمالي قيمة الأعمال المنجزة: $totalCompletedBilled $currency\n")
    append("• إجمالي المدفوعات المسددة: $totalPayments $currency\n")
    append("• صافي حركة اليوم: $netBalance $currency\n")
    append("───────────────────────────────────────\n")

    if (labBreakdown.isNotEmpty()) {
      append("🏥 توزيع المعامل لليوم:\n")
      labBreakdown.forEach { lab ->
        append("• ${lab.labName}: ${lab.completedShipmentsCount} إرسالية (${lab.totalPieces} قطعة) | منجز: ${lab.totalBilled} $currency | مسدد: ${lab.totalPaid} $currency\n")
      }
      append("───────────────────────────────────────\n")
    }

    if (completedShipments.isNotEmpty()) {
      append("📦 قائمة الإرساليات المكتملة لليوم:\n")
      completedShipments.forEachIndexed { i, s ->
        append("${i + 1}. #${s.shipmentNumber} | ${s.patientName} (${s.clinicOrDoctorName.ifEmpty { com.aqlanlab.app.ui.components.ClinicInfo.DOCTOR_NAME }}) | ${s.labName} | ${s.pieceCount} ق ${s.workTypeName} | ${s.totalPrice} $currency\n")
      }
      append("───────────────────────────────────────\n")
    }

    if (payments.isNotEmpty()) {
      append("💳 الحركات المالية وسندات الدفع لليوم:\n")
      payments.forEachIndexed { i, p ->
        val rec = if (p.receiptNumber.isNotEmpty()) " (سند #${p.receiptNumber})" else ""
        append("${i + 1}. سداد إلى ${p.labName}: ${p.amount} $currency | ${p.paymentMethod.titleAr}$rec\n")
      }
      append("───────────────────────────────────────\n")
    }

    append("📍 ${com.aqlanlab.app.ui.components.ClinicInfo.ADDRESS}\n")
    append("📞 هاتف / واتساب: ${com.aqlanlab.app.ui.components.ClinicInfo.PHONES}\n")
    append("═══════════════════════════════════════\n")
  }
}
