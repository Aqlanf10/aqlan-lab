package com.example.ui.screens

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
import com.example.data.models.Shipment
import com.example.data.models.UserRole
import com.example.ui.components.DateUtils
import com.example.ui.components.PriceDisplay
import com.example.ui.theme.DentalPrimary
import com.example.ui.viewmodel.DentalLabViewModel
import com.example.ui.viewmodel.ReportPeriod
import com.example.util.PdfReportGenerator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
  viewModel: DentalLabViewModel,
  onNavigateToDailyReport: () -> Unit = {},
  onNavigateToAnalytics: () -> Unit = {},
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val allWorkTypes by viewModel.allWorkTypes.collectAsState()
  val selectedPeriod by viewModel.reportPeriod.collectAsState()

  var showExportDialog by remember { mutableStateOf(false) }

  // Filter shipments based on selected period
  val periodFilteredShipments = remember(allShipments, selectedPeriod) {
    val calendar = Calendar.getInstance()
    val now = System.currentTimeMillis()
    val threshold = when (selectedPeriod) {
      ReportPeriod.TODAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.timeInMillis
      }
      ReportPeriod.THIS_WEEK -> {
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.timeInMillis
      }
      ReportPeriod.THIS_MONTH -> {
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.timeInMillis
      }
      ReportPeriod.ALL_TIME -> 0L
    }
    allShipments.filter { it.orderDate >= threshold }
  }

  val totalPieces = remember(periodFilteredShipments) {
    periodFilteredShipments.sumOf { it.pieceCount }
  }

  val totalCost = remember(periodFilteredShipments) {
    periodFilteredShipments.sumOf { it.totalPrice }
  }

  // Breakdown by Work Type
  val piecesByWorkType = remember(periodFilteredShipments, allWorkTypes) {
    allWorkTypes.map { wt ->
      val count = periodFilteredShipments.filter { it.workTypeId == wt.id }.sumOf { it.pieceCount }
      val amount = periodFilteredShipments.filter { it.workTypeId == wt.id }.sumOf { it.totalPrice }
      Triple(wt.nameAr, count, amount)
    }.filter { it.second > 0 }.sortedByDescending { it.second }
  }

  // Breakdown by Laboratory
  val piecesByLab = remember(periodFilteredShipments, allLabs) {
    allLabs.map { lab ->
      val labShipments = periodFilteredShipments.filter { it.labId == lab.id }
      val count = labShipments.sumOf { it.pieceCount }
      val amount = labShipments.sumOf { it.totalPrice }
      val crownsCount = labShipments.filter { it.workTypeName.contains("Crown", ignoreCase = true) || it.workTypeName.contains("زركونيا") || it.workTypeName.contains("إيماكس") }.sumOf { it.pieceCount }
      val bridgesCount = labShipments.filter { it.workTypeName.contains("جسر") || it.workTypeName.contains("Bridge", ignoreCase = true) }.sumOf { it.pieceCount }
      val veneersCount = labShipments.filter { it.workTypeName.contains("فينير") || it.workTypeName.contains("Veneer", ignoreCase = true) }.sumOf { it.pieceCount }
      val denturesCount = labShipments.filter { it.workTypeName.contains("طقم") || it.workTypeName.contains("Denture", ignoreCase = true) }.sumOf { it.pieceCount }

      LabReportData(
        labName = lab.name,
        totalPieces = count,
        totalAmount = amount,
        crowns = crownsCount,
        bridges = bridgesCount,
        veneers = veneersCount,
        dentures = denturesCount
      )
    }.filter { it.totalPieces > 0 }.sortedByDescending { it.totalPieces }
  }

  // Breakdown by Doctor
  val piecesByDoctor = remember(periodFilteredShipments) {
    periodFilteredShipments.groupBy { it.clinicOrDoctorName.ifEmpty { "طبيب عام" } }
      .map { (doc, ships) ->
        Pair(doc, ships.sumOf { it.pieceCount })
      }.sortedByDescending { it.second }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "التقارير والإحصائيات الشاملة",
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
          IconButton(onClick = { showExportDialog = true }) {
            Icon(Icons.Default.Share, contentDescription = "تصدير التقرير")
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
      // Official Clinic Brand Header
      item {
        com.example.ui.components.AqlanClinicHeaderCard()
      }

      // 0. Daily Summary Report Hero Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDailyReport() }
            .testTag("open_daily_summary_report_btn")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Summarize, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
              }

              Column {
                Text(
                  text = "التقرير اليومي الشامل (Daily Summary)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                Text(
                  text = "متابعة الإرساليات المكتملة، الحركات المالية، وإجماليات اليوم",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.85f)
                )
              }
            }

            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // 0.1 Data Visualization & Trend Analytics Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToAnalytics() }
            .testTag("open_analytics_charts_btn")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF3B82F6)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.ShowChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
              }

              Column {
                Text(
                  text = "الرسوم البيانية والتحليلات (Charts & Trends)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                Text(
                  text = "منحنى الإيرادات الشهري، حجم الإرساليات، وتوزيع الأعمال",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.85f)
                )
              }
            }

            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // 1. Period Selector Chips
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ReportPeriod.values().forEach { period ->
            FilterChip(
              selected = selectedPeriod == period,
              onClick = { viewModel.setReportPeriod(period) },
              label = { Text(period.titleAr, fontWeight = FontWeight.Bold) }
            )
          }
        }
      }

      // 2. Summary Overview Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "ملخص الفترة (${selectedPeriod.titleAr})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text("عدد الإرساليات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${periodFilteredShipments.size} إرسالية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              }
              Column {
                Text("إجمالي القطع المنجزة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$totalPieces قطعة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DentalPrimary)
              }
              if (activeUser.role != UserRole.STAFF) {
                Column {
                  Text("إجمالي القيمة المالية", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  PriceDisplay(amount = totalCost, userRole = activeUser.role, currencyCode = currency, style = MaterialTheme.typography.titleLarge)
                }
              }
            }
          }
        }
      }

      // 3. Report by Work Type (عدد القطع حسب نوع العمل)
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
                Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = "إحصائيات القطع حسب نوع العمل",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }
              Text("$totalPieces قطعة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            if (piecesByWorkType.isEmpty()) {
              Text("لا توجد أعمال منجزة في هذه الفترة", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
              piecesByWorkType.forEach { (name, count, amount) ->
                val fraction = if (totalPieces > 0) count.toFloat() / totalPieces else 0f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text("$count قطعة (${(fraction * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                      if (activeUser.role != UserRole.STAFF) {
                        PriceDisplay(amount = amount, userRole = activeUser.role, currencyCode = currency, style = MaterialTheme.typography.bodySmall)
                      }
                    }
                  }
                  LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(6.dp)
                      .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // 4. Report by Laboratory (التقرير حسب المعمل والأنواع)
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
              Icon(Icons.Default.Apartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                text = "تقرير وتوزيع القطع حسب المعمل",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            // Table Header
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("المعمل", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
              Text("Crown", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              Text("Bridge", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              Text("Veneer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              Text("الإجمالي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
            }

            piecesByLab.forEach { data ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 6.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(data.labName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
                Text("${data.crowns}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${data.bridges}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${data.veneers}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${data.totalPieces} قطعة", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.2f))
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
          }
        }
      }

      // 5. Report by Doctor / Clinic
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
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                text = "عدد الأعمال حسب الطبيب / العيادة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            piecesByDoctor.forEach { (doctor, count) ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(doctor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.primaryContainer
                ) {
                  Text(
                    text = "$count قطعة",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    if (showExportDialog) {
      val context = androidx.compose.ui.platform.LocalContext.current
      AlertDialog(
        onDismissRequest = { showExportDialog = false },
        icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444)) },
        title = { Text("تصدير وطباعة تقرير الفترة (PDF)", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تقرير إحصائي للفترة: ${selectedPeriod.titleAr}", fontWeight = FontWeight.SemiBold)
            Text("إجمالي الإرساليات: ${periodFilteredShipments.size}")
            Text("إجمالي القطع: $totalPieces وحدة سنية")
            if (activeUser.role != UserRole.STAFF) {
              Text("إجمالي القيمة: $totalCost $currency")
            }
            Text(
              "سيتم إنشاء ملف PDF احترافي يحمل ترويسة وهوية مركز الدكتور عقلان الكامل وجدول تفصيلي بالإرساليات.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        confirmButton = {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Button(
              onClick = {
                val pdfFile = PdfReportGenerator.generatePeriodicSummaryPdf(
                  context = context,
                  title = "تقرير إحصائيات معمل الأسنان",
                  periodName = selectedPeriod.titleAr,
                  shipments = periodFilteredShipments,
                  totalCost = totalCost,
                  currency = currency,
                  userRole = activeUser.role
                )
                if (pdfFile != null) {
                  PdfReportGenerator.openPdfFile(context, pdfFile)
                }
                showExportDialog = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
              Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("فتح PDF")
            }

            Button(
              onClick = {
                val pdfFile = PdfReportGenerator.generatePeriodicSummaryPdf(
                  context = context,
                  title = "تقرير إحصائيات معمل الأسنان",
                  periodName = selectedPeriod.titleAr,
                  shipments = periodFilteredShipments,
                  totalCost = totalCost,
                  currency = currency,
                  userRole = activeUser.role
                )
                if (pdfFile != null) {
                  PdfReportGenerator.sharePdfFile(context, pdfFile, title = "تقرير ${selectedPeriod.titleAr}")
                }
                showExportDialog = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("مشاركة")
            }

            FilledTonalButton(
              onClick = {
                val pdfFile = PdfReportGenerator.generatePeriodicSummaryPdf(
                  context = context,
                  title = "تقرير إحصائيات معمل الأسنان",
                  periodName = selectedPeriod.titleAr,
                  shipments = periodFilteredShipments,
                  totalCost = totalCost,
                  currency = currency,
                  userRole = activeUser.role
                )
                if (pdfFile != null) {
                  PdfReportGenerator.printPdf(context, pdfFile, jobName = "Report_${selectedPeriod.name}")
                }
                showExportDialog = false
              }
            ) {
              Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("طباعة")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showExportDialog = false }) {
            Text("إلغاء")
          }
        }
      )
    }
  }
}

data class LabReportData(
  val labName: String,
  val totalPieces: Int,
  val totalAmount: Double,
  val crowns: Int,
  val bridges: Int,
  val veneers: Int,
  val dentures: Int
)
