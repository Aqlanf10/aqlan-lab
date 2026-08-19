package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.Payment
import com.example.data.models.Shipment
import com.example.data.models.UserRole
import com.example.ui.components.DateUtils
import com.example.ui.components.PriceDisplay
import com.example.ui.components.ClinicInfo
import com.example.ui.components.AqlanClinicHeaderCard
import com.example.ui.theme.DentalPrimary
import com.example.ui.viewmodel.DentalLabViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

private fun formatCurrency(amount: Double, currencyCode: String): String {
  val formattedAmount = if (amount % 1.0 == 0.0) {
    String.format(Locale.US, "%,.0f", amount)
  } else {
    String.format(Locale.US, "%,.2f", amount)
  }
  return "$formattedAmount $currencyCode"
}

enum class AnalyticsPeriod(val titleAr: String, val monthsCount: Int) {
  LAST_6_MONTHS("آخر 6 أشهر", 6),
  LAST_12_MONTHS("سنة كاملة (12 شهر)", 12),
  YEAR_TO_DATE("العام الحالي (YTD)", 0),
  ALL_DATA("كافة السجلات", 24)
}

data class MonthlyMetric(
  val monthKey: String, // e.g. "2026-08"
  val monthNameAr: String, // e.g. "أغسطس 2026"
  val monthShortAr: String, // e.g. "أغسطس"
  val revenue: Double,
  val payments: Double,
  val netBalance: Double,
  val shipmentsCount: Int,
  val piecesCount: Int,
  val avgPieceCost: Double,
  val growthPercent: Double? = null
)

data class WorkTypeStat(
  val name: String,
  val piecesCount: Int,
  val revenue: Double,
  val percentage: Float,
  val color: Color
)

data class LabShareStat(
  val labName: String,
  val shipmentsCount: Int,
  val piecesCount: Int,
  val revenue: Double,
  val percentage: Float,
  val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
  viewModel: DentalLabViewModel,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
  val currency by viewModel.currency.collectAsStateWithLifecycle()
  val allShipments by viewModel.allShipments.collectAsStateWithLifecycle()
  val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
  val allLabs by viewModel.allLabs.collectAsStateWithLifecycle()
  val allWorkTypes by viewModel.allWorkTypes.collectAsStateWithLifecycle()

  var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.LAST_6_MONTHS) }
  var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }
  var chartViewMode by remember { mutableStateOf(0) } // 0: Revenue Area, 1: Shipments Bar, 2: Revenue vs Payment

  val isFinancialVisible = activeUser.role != UserRole.STAFF

  // 1. Process Monthly Aggregations
  val monthlyData = remember(allShipments, allPayments, selectedPeriod) {
    calculateMonthlyMetrics(allShipments, allPayments, selectedPeriod)
  }

  // Auto-select latest month if index out of bounds
  LaunchedEffect(monthlyData) {
    if (monthlyData.isNotEmpty() && (selectedMonthIndex == null || selectedMonthIndex!! >= monthlyData.size)) {
      selectedMonthIndex = monthlyData.lastIndex
    }
  }

  // 2. Summary KPIs
  val totalPeriodRevenue = remember(monthlyData) { monthlyData.sumOf { it.revenue } }
  val totalPeriodShipments = remember(monthlyData) { monthlyData.sumOf { it.shipmentsCount } }
  val totalPeriodPieces = remember(monthlyData) { monthlyData.sumOf { it.piecesCount } }
  val totalPeriodPayments = remember(monthlyData) { monthlyData.sumOf { it.payments } }
  val avgMonthlyRevenue = remember(monthlyData) {
    if (monthlyData.isNotEmpty()) totalPeriodRevenue / monthlyData.size else 0.0
  }
  val avgMonthlyShipments = remember(monthlyData) {
    if (monthlyData.isNotEmpty()) totalPeriodShipments.toDouble() / monthlyData.size else 0.0
  }
  val peakMonth = remember(monthlyData) {
    monthlyData.maxByOrNull { it.revenue }
  }

  // 3. Work Types Distribution
  val workTypeStats = remember(allShipments, allWorkTypes) {
    val totalRevenue = allShipments.sumOf { it.totalPrice }
    val colors = listOf(
      Color(0xFF2563EB), Color(0xFF10B981), Color(0xFFF59E0B),
      Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF06B6D4),
      Color(0xFF64748B), Color(0xFF14B8A6)
    )

    allWorkTypes.mapIndexedNotNull { index, wt ->
      val filtered = allShipments.filter { it.workTypeId == wt.id }
      val pieces = filtered.sumOf { it.pieceCount }
      val rev = filtered.sumOf { it.totalPrice }
      if (pieces > 0) {
        val pct = if (totalRevenue > 0) ((rev / totalRevenue) * 100).toFloat() else 0f
        WorkTypeStat(
          name = wt.nameAr,
          piecesCount = pieces,
          revenue = rev,
          percentage = pct,
          color = colors[index % colors.size]
        )
      } else null
    }.sortedByDescending { it.revenue }
  }

  // 4. Labs Share Distribution
  val labShareStats = remember(allShipments, allLabs) {
    val totalShipments = allShipments.size
    val colors = listOf(
      Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF97316),
      Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEAB308)
    )

    allLabs.mapIndexedNotNull { index, lab ->
      val labShipments = allShipments.filter { it.labId == lab.id }
      val count = labShipments.size
      val pieces = labShipments.sumOf { it.pieceCount }
      val rev = labShipments.sumOf { it.totalPrice }
      if (count > 0) {
        val pct = if (totalShipments > 0) ((count.toFloat() / totalShipments) * 100) else 0f
        LabShareStat(
          labName = lab.name,
          shipmentsCount = count,
          piecesCount = pieces,
          revenue = rev,
          percentage = pct,
          color = colors[index % colors.size]
        )
      } else null
    }.sortedByDescending { it.shipmentsCount }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("تحليلات ورسوم بيانية (Data Analytics)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("متابعة اتجاهات الإيرادات وحجم الإرساليات عبر الزمن", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          IconButton(
            onClick = {
              val summaryText = buildAnalyticsReportText(
                period = selectedPeriod.titleAr,
                totalRevenue = totalPeriodRevenue,
                totalShipments = totalPeriodShipments,
                totalPieces = totalPeriodPieces,
                currency = currency,
                monthlyData = monthlyData
              )
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Analytics Summary", summaryText))
              Toast.makeText(context, "تم نسخ تقرير التحليلات البيانية!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.testTag("share_analytics_btn")
          ) {
            Icon(Icons.Default.Share, contentDescription = "مشاركة التقرير")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Period Selector Filter Chips
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          AnalyticsPeriod.values().forEach { period ->
            val isSelected = selectedPeriod == period
            FilterChip(
              selected = isSelected,
              onClick = { selectedPeriod = period },
              label = { Text(period.titleAr, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
              leadingIcon = if (isSelected) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
              } else null,
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
              ),
              modifier = Modifier.testTag("period_chip_${period.name}")
            )
          }
        }
      }

      // 2. High-Level KPI Summary Cards
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Revenue KPI
            if (isFinancialVisible) {
              AnalyticsKpiCard(
                title = "إجمالي إيرادات الفترة",
                value = formatCurrency(totalPeriodRevenue, currency),
                subtitle = "معدل شهري: ${formatCurrency(avgMonthlyRevenue, currency)}",
                icon = Icons.Default.TrendingUp,
                accentColor = Color(0xFF2563EB),
                modifier = Modifier.weight(1f).testTag("kpi_total_revenue")
              )
            }

            // Shipments KPI
            AnalyticsKpiCard(
              title = "إجمالي الإرساليات",
              value = "$totalPeriodShipments إرسالية",
              subtitle = "$totalPeriodPieces سن/قطعة (${String.format(Locale.US, "%.1f", avgMonthlyShipments)}/شهر)",
              icon = Icons.Default.LocalShipping,
              accentColor = Color(0xFF10B981),
              modifier = Modifier.weight(1f).testTag("kpi_total_shipments")
            )
          }

          if (peakMonth != null && isFinancialVisible) {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Stars, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text("ذروة النشاط المالي (Peak Month)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                  Text(
                    text = "${peakMonth.monthNameAr}: حقق أعلى إيراد بقيمة ${formatCurrency(peakMonth.revenue, currency)} بإجمالي ${peakMonth.shipmentsCount} إرسالية (${peakMonth.piecesCount} سن).",
                    fontSize = 12.sp,
                    color = Color(0xFF1E3A8A)
                  )
                }
              }
            }
          }
        }
      }

      // 3. Interactive Recharts-Style Chart Section
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            // Chart Header & Mode Switcher
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  when (chartViewMode) {
                    0 -> "منحنى الإيرادات الشهري (Revenue Trend)"
                    1 -> "حجم الإرساليات والقطع (Shipment Volume)"
                    else -> "مقارنة المطالبات والمسدد (Revenue vs Payments)"
                  },
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp
                )
                Text(
                  "انقر أو مرر على أي نقطة لعرض التفاصيل اللحظية",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              // Toggle Chart Type Buttons
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                  .padding(2.dp)
              ) {
                IconButton(
                  onClick = { chartViewMode = 0 },
                  modifier = Modifier
                    .size(32.dp)
                    .background(if (chartViewMode == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                ) {
                  Icon(Icons.Default.ShowChart, contentDescription = "منحنى الإيرادات", modifier = Modifier.size(18.dp), tint = if (chartViewMode == 0) MaterialTheme.colorScheme.primary else Color.Gray)
                }
                IconButton(
                  onClick = { chartViewMode = 1 },
                  modifier = Modifier
                    .size(32.dp)
                    .background(if (chartViewMode == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                ) {
                  Icon(Icons.Default.BarChart, contentDescription = "أعمدة الإرساليات", modifier = Modifier.size(18.dp), tint = if (chartViewMode == 1) MaterialTheme.colorScheme.primary else Color.Gray)
                }
                if (isFinancialVisible) {
                  IconButton(
                    onClick = { chartViewMode = 2 },
                    modifier = Modifier
                      .size(32.dp)
                      .background(if (chartViewMode == 2) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                  ) {
                    Icon(Icons.Default.StackedLineChart, contentDescription = "مقارنة السداد", modifier = Modifier.size(18.dp), tint = if (chartViewMode == 2) MaterialTheme.colorScheme.primary else Color.Gray)
                  }
                }
              }
            }

            // The Canvas Chart
            if (monthlyData.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(200.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("لا توجد بيانات مسجلة في هذه الفترة", color = Color.Gray, fontSize = 13.sp)
              }
            } else {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(220.dp)
              ) {
                when (chartViewMode) {
                  0 -> {
                    InteractiveAreaTrendChart(
                      data = monthlyData,
                      selectedIndex = selectedMonthIndex,
                      onSelectIndex = { selectedMonthIndex = it },
                      currency = currency,
                      modifier = Modifier.fillMaxSize().testTag("interactive_revenue_area_chart")
                    )
                  }
                  1 -> {
                    InteractiveBarVolumeChart(
                      data = monthlyData,
                      selectedIndex = selectedMonthIndex,
                      onSelectIndex = { selectedMonthIndex = it },
                      modifier = Modifier.fillMaxSize().testTag("interactive_shipments_bar_chart")
                    )
                  }
                  else -> {
                    InteractiveDualLineChart(
                      data = monthlyData,
                      selectedIndex = selectedMonthIndex,
                      onSelectIndex = { selectedMonthIndex = it },
                      currency = currency,
                      modifier = Modifier.fillMaxSize().testTag("interactive_dual_line_chart")
                    )
                  }
                }
              }

              // Selected Month Live Detail Tooltip Card
              if (selectedMonthIndex != null && selectedMonthIndex!! in monthlyData.indices) {
                val sel = monthlyData[selectedMonthIndex!!]
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(sel.monthNameAr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📦 ${sel.shipmentsCount} إرسالية", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("🦷 ${sel.piecesCount} سن", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }

                    if (isFinancialVisible) {
                      Column(horizontalAlignment = Alignment.End) {
                        Text(formatCurrency(sel.revenue, currency), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        if (sel.growthPercent != null) {
                          val isPositive = sel.growthPercent >= 0
                          Text(
                            text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.1f", sel.growthPercent)}% مقارنة بالسابق",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 4. Work Types Distribution (Donut / Progress Breakdown)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
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
              Text("توزيع الإيرادات حسب نوع العمل (Work Types)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              Text("${workTypeStats.size} أنواع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (workTypeStats.isEmpty()) {
              Text("لا توجد بيانات متاحة لأنواع الأعمال", color = Color.Gray, fontSize = 12.sp)
            } else {
              // Stacked Progress Bar (Recharts Donut/Bar equivalent)
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(14.dp)
                  .clip(RoundedCornerShape(7.dp))
                  .background(Color(0xFFE2E8F0))
              ) {
                Row(modifier = Modifier.fillMaxSize()) {
                  workTypeStats.forEach { stat ->
                    if (stat.percentage > 0) {
                      Box(
                        modifier = Modifier
                          .weight(stat.percentage.coerceAtLeast(0.01f))
                          .fillMaxHeight()
                          .background(stat.color)
                      )
                    }
                  }
                }
              }

              // List of Work Types
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workTypeStats.take(5).forEach { stat ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(10.dp)
                          .clip(CircleShape)
                          .background(stat.color)
                      )
                      Text(stat.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                      Text("(${stat.piecesCount} قطعة)", fontSize = 11.sp, color = Color.Gray)
                    }

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      if (isFinancialVisible) {
                        Text(formatCurrency(stat.revenue, currency), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                      }
                      Text(
                        "${String.format(Locale.US, "%.1f", stat.percentage)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = stat.color
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 5. Partner Laboratories Volume Breakdown
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
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
              Text("حصة المعامل من الإرساليات (Labs Share)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              Text("${labShareStats.size} معامل شريكة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (labShareStats.isEmpty()) {
              Text("لا توجد بيانات متاحة للمعامل", color = Color.Gray, fontSize = 12.sp)
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labShareStats.forEach { stat ->
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(stat.labName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                      Text(
                        "${stat.shipmentsCount} إرسالية (${String.format(Locale.US, "%.1f", stat.percentage)}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = stat.color
                      )
                    }
                    LinearProgressIndicator(
                      progress = { (stat.percentage / 100f).coerceIn(0f, 1f) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                      color = stat.color,
                      trackColor = Color(0xFFE2E8F0)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 6. Monthly Data Breakdown Table
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
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
              Text("جدول البيانات الشهرية بالتفصيل", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              Text("${monthlyData.size} شهر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Table Header
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("الشهر", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("الإرساليات", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
                if (isFinancialVisible) {
                  Text("الإيراد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                  Text("المسدد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                }
              }
            }

            // Table Rows
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              monthlyData.reversed().forEach { row ->
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (selectedMonthIndex != null && monthlyData.getOrNull(selectedMonthIndex!!)?.monthKey == row.monthKey)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  else
                    Color.Transparent,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      selectedMonthIndex = monthlyData.indexOfFirst { it.monthKey == row.monthKey }
                    }
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(row.monthShortAr, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
                    Text("${row.shipmentsCount} (${row.piecesCount})", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
                    if (isFinancialVisible) {
                      Text(formatCurrency(row.revenue, currency), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                      Text(formatCurrency(row.payments, currency), fontSize = 12.sp, color = Color(0xFF10B981), textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                    }
                  }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AnalyticsKpiCard(
  title: String,
  value: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
      }

      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = Color.Gray,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

// ----------------------------------------------------
// Recharts-Style Interactive Canvas Visualizations
// ----------------------------------------------------

@Composable
fun InteractiveAreaTrendChart(
  data: List<MonthlyMetric>,
  selectedIndex: Int?,
  onSelectIndex: (Int) -> Unit,
  currency: String,
  modifier: Modifier = Modifier
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = Color(0xFF60A5FA)
  val gridColor = Color(0xFFE2E8F0)

  val maxVal = remember(data) { (data.maxOfOrNull { it.revenue } ?: 100.0).coerceAtLeast(10.0) * 1.15 }

  Canvas(
    modifier = modifier
      .pointerInput(data) {
        detectTapGestures { offset ->
          val stepX = size.width / (data.size.coerceAtLeast(1))
          val clickedIndex = (offset.x / stepX).toInt().coerceIn(0, data.lastIndex)
          onSelectIndex(clickedIndex)
        }
      }
  ) {
    val width = size.width
    val height = size.height - 30.dp.toPx() // Reserve bottom for X labels
    val bottomY = height
    val stepX = width / (data.size - 1).coerceAtLeast(1)

    // 1. Draw Horizontal Grid lines
    val gridLinesCount = 4
    for (i in 0..gridLinesCount) {
      val y = bottomY - (i * bottomY / gridLinesCount)
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
      )
    }

    if (data.size < 2) {
      // Single point center
      val ptX = width / 2f
      val ptY = bottomY - (data.firstOrNull()?.revenue?.toFloat() ?: 0f) / maxVal.toFloat() * bottomY
      drawCircle(primaryColor, radius = 6.dp.toPx(), center = Offset(ptX, ptY))
      return@Canvas
    }

    // 2. Build Smooth Spline / Cubic Bézier Path
    val points = data.mapIndexed { idx, item ->
      val x = idx * stepX
      val y = bottomY - (item.revenue.toFloat() / maxVal.toFloat()) * bottomY
      Offset(x, y)
    }

    val strokePath = Path().apply {
      moveTo(points.first().x, points.first().y)
      for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val controlX1 = p0.x + (p1.x - p0.x) / 2
        val controlY1 = p0.y
        val controlX2 = p0.x + (p1.x - p0.x) / 2
        val controlY2 = p1.y
        cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
      }
    }

    // 3. Build Filled Gradient Area under curve
    val fillPath = Path().apply {
      addPath(strokePath)
      lineTo(points.last().x, bottomY)
      lineTo(points.first().x, bottomY)
      close()
    }

    val areaGradient = Brush.verticalGradient(
      colors = listOf(
        primaryColor.copy(alpha = 0.45f),
        primaryColor.copy(alpha = 0.05f)
      ),
      startY = 0f,
      endY = bottomY
    )

    drawPath(path = fillPath, brush = areaGradient)
    drawPath(path = strokePath, color = primaryColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    // 4. Draw Data Points and Selected Marker
    points.forEachIndexed { index, pt ->
      val isSelected = selectedIndex == index
      if (isSelected) {
        // Vertical dashed indicator line
        drawLine(
          color = primaryColor.copy(alpha = 0.5f),
          start = Offset(pt.x, 0f),
          end = Offset(pt.x, bottomY),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )
        // Outer halo
        drawCircle(color = primaryColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = pt)
        // Inner circle
        drawCircle(color = primaryColor, radius = 5.5.dp.toPx(), center = pt)
        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = pt)
      } else {
        drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = pt)
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
      }
    }

    // 5. Draw X-Axis Labels (Short Month Names)
    val textPaint = Paint().apply {
      color = android.graphics.Color.GRAY
      textSize = 28f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.DEFAULT_BOLD
    }

    data.forEachIndexed { index, item ->
      val x = index * stepX
      drawContext.canvas.nativeCanvas.drawText(
        item.monthShortAr,
        x,
        size.height - 4.dp.toPx(),
        textPaint
      )
    }
  }
}

@Composable
fun InteractiveBarVolumeChart(
  data: List<MonthlyMetric>,
  selectedIndex: Int?,
  onSelectIndex: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val barColor = Color(0xFF10B981)
  val selectedBarColor = Color(0xFF059669)
  val pieceColor = Color(0xFF3B82F6)
  val gridColor = Color(0xFFE2E8F0)

  val maxShipments = remember(data) { (data.maxOfOrNull { it.shipmentsCount } ?: 10).coerceAtLeast(5) * 1.25f }

  Canvas(
    modifier = modifier
      .pointerInput(data) {
        detectTapGestures { offset ->
          val stepX = size.width / (data.size.coerceAtLeast(1))
          val clickedIndex = (offset.x / stepX).toInt().coerceIn(0, data.lastIndex)
          onSelectIndex(clickedIndex)
        }
      }
  ) {
    val width = size.width
    val height = size.height - 30.dp.toPx()
    val bottomY = height
    val groupWidth = width / data.size.coerceAtLeast(1)
    val barWidth = (groupWidth * 0.45f).coerceAtMost(36.dp.toPx())

    // Draw Grid
    for (i in 0..3) {
      val y = bottomY - (i * bottomY / 3)
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
      )
    }

    val textPaint = Paint().apply {
      color = android.graphics.Color.GRAY
      textSize = 26f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.DEFAULT_BOLD
    }

    val valuePaint = Paint().apply {
      color = android.graphics.Color.DKGRAY
      textSize = 24f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.DEFAULT_BOLD
    }

    data.forEachIndexed { index, item ->
      val centerX = (index * groupWidth) + (groupWidth / 2f)
      val barHeight = (item.shipmentsCount / maxShipments) * bottomY
      val topY = bottomY - barHeight
      val isSelected = selectedIndex == index

      // Bar Background/Highlight if selected
      if (isSelected) {
        drawRoundRect(
          color = Color(0xFFD1FAE5).copy(alpha = 0.5f),
          topLeft = Offset(centerX - (groupWidth * 0.45f), 0f),
          size = Size(groupWidth * 0.9f, bottomY),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
      }

      // Draw Main Rounded Bar (Shipments)
      drawRoundRect(
        color = if (isSelected) selectedBarColor else barColor,
        topLeft = Offset(centerX - (barWidth / 2f), topY),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
      )

      // Value label on top of bar
      if (item.shipmentsCount > 0) {
        drawContext.canvas.nativeCanvas.drawText(
          "${item.shipmentsCount}",
          centerX,
          topY - 6.dp.toPx(),
          valuePaint
        )
      }

      // X label
      drawContext.canvas.nativeCanvas.drawText(
        item.monthShortAr,
        centerX,
        size.height - 4.dp.toPx(),
        textPaint
      )
    }
  }
}

@Composable
fun InteractiveDualLineChart(
  data: List<MonthlyMetric>,
  selectedIndex: Int?,
  onSelectIndex: (Int) -> Unit,
  currency: String,
  modifier: Modifier = Modifier
) {
  val billedColor = Color(0xFF2563EB)
  val paidColor = Color(0xFF10B981)
  val gridColor = Color(0xFFE2E8F0)

  val maxVal = remember(data) {
    val maxBilled = data.maxOfOrNull { it.revenue } ?: 100.0
    val maxPaid = data.maxOfOrNull { it.payments } ?: 100.0
    max(maxBilled, maxPaid).coerceAtLeast(10.0) * 1.15
  }

  Canvas(
    modifier = modifier
      .pointerInput(data) {
        detectTapGestures { offset ->
          val stepX = size.width / (data.size.coerceAtLeast(1))
          val clickedIndex = (offset.x / stepX).toInt().coerceIn(0, data.lastIndex)
          onSelectIndex(clickedIndex)
        }
      }
  ) {
    val width = size.width
    val height = size.height - 30.dp.toPx()
    val bottomY = height
    val stepX = width / (data.size - 1).coerceAtLeast(1)

    // Grid
    for (i in 0..4) {
      val y = bottomY - (i * bottomY / 4)
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
      )
    }

    if (data.size < 2) return@Canvas

    // 1. Revenue Path
    val billedPoints = data.mapIndexed { idx, item ->
      Offset(idx * stepX, bottomY - (item.revenue.toFloat() / maxVal.toFloat()) * bottomY)
    }
    val billedPath = Path().apply {
      moveTo(billedPoints.first().x, billedPoints.first().y)
      for (i in 0 until billedPoints.size - 1) {
        val p0 = billedPoints[i]
        val p1 = billedPoints[i + 1]
        cubicTo(p0.x + (p1.x - p0.x) / 2, p0.y, p0.x + (p1.x - p0.x) / 2, p1.y, p1.x, p1.y)
      }
    }
    drawPath(billedPath, color = billedColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

    // 2. Paid Path
    val paidPoints = data.mapIndexed { idx, item ->
      Offset(idx * stepX, bottomY - (item.payments.toFloat() / maxVal.toFloat()) * bottomY)
    }
    val paidPath = Path().apply {
      moveTo(paidPoints.first().x, paidPoints.first().y)
      for (i in 0 until paidPoints.size - 1) {
        val p0 = paidPoints[i]
        val p1 = paidPoints[i + 1]
        cubicTo(p0.x + (p1.x - p0.x) / 2, p0.y, p0.x + (p1.x - p0.x) / 2, p1.y, p1.x, p1.y)
      }
    }
    drawPath(paidPath, color = paidColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

    // Markers
    billedPoints.forEachIndexed { idx, pt ->
      drawCircle(billedColor, radius = 3.5.dp.toPx(), center = pt)
      drawCircle(Color.White, radius = 1.5.dp.toPx(), center = pt)
    }
    paidPoints.forEachIndexed { idx, pt ->
      drawCircle(paidColor, radius = 3.5.dp.toPx(), center = pt)
      drawCircle(Color.White, radius = 1.5.dp.toPx(), center = pt)
    }

    // X Labels
    val textPaint = Paint().apply {
      color = android.graphics.Color.GRAY
      textSize = 28f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.DEFAULT_BOLD
    }
    data.forEachIndexed { index, item ->
      drawContext.canvas.nativeCanvas.drawText(item.monthShortAr, index * stepX, size.height - 4.dp.toPx(), textPaint)
    }
  }
}

// ----------------------------------------------------
// Aggregation & Helper Functions
// ----------------------------------------------------

fun calculateMonthlyMetrics(
  shipments: List<Shipment>,
  payments: List<Payment>,
  period: AnalyticsPeriod
): List<MonthlyMetric> {
  val cal = Calendar.getInstance()
  val count = if (period.monthsCount > 0) period.monthsCount else (cal.get(Calendar.MONTH) + 1)

  // Generate sequence of months
  val monthSlots = mutableListOf<Calendar>()
  for (i in (count - 1) downTo 0) {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, -i)
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    monthSlots.add(c)
  }

  val arabicMonthNames = arrayOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
  )

  var prevRevenue: Double? = null

  return monthSlots.map { slotCal ->
    val year = slotCal.get(Calendar.YEAR)
    val month = slotCal.get(Calendar.MONTH)
    val monthKey = String.format(Locale.US, "%d-%02d", year, month + 1)
    val monthShort = arabicMonthNames[month]
    val monthFull = "$monthShort $year"

    val nextMonthCal = (slotCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    val startMs = slotCal.timeInMillis
    val endMs = nextMonthCal.timeInMillis

    val monthShipments = shipments.filter { it.orderDate in startMs until endMs }
    val monthPayments = payments.filter { it.paymentDate in startMs until endMs }

    val revenue = monthShipments.sumOf { it.totalPrice }
    val paid = monthPayments.sumOf { it.amount }
    val shipmentsCount = monthShipments.size
    val piecesCount = monthShipments.sumOf { it.pieceCount }
    val avgPieceCost = if (piecesCount > 0) revenue / piecesCount else 0.0

    val growth = if (prevRevenue != null && prevRevenue!! > 0) {
      ((revenue - prevRevenue!!) / prevRevenue!!) * 100.0
    } else null

    prevRevenue = revenue

    MonthlyMetric(
      monthKey = monthKey,
      monthNameAr = monthFull,
      monthShortAr = monthShort,
      revenue = revenue,
      payments = paid,
      netBalance = revenue - paid,
      shipmentsCount = shipmentsCount,
      piecesCount = piecesCount,
      avgPieceCost = avgPieceCost,
      growthPercent = growth
    )
  }
}

fun buildAnalyticsReportText(
  period: String,
  totalRevenue: Double,
  totalShipments: Int,
  totalPieces: Int,
  currency: String,
  monthlyData: List<MonthlyMetric>
): String {
  val sb = StringBuilder()
  sb.appendLine("═══════════════════════════════════════")
  sb.appendLine("🏥 ${ClinicInfo.CLINIC_NAME}")
  sb.appendLine("📍 ${ClinicInfo.ADDRESS} | 📞 ${ClinicInfo.PHONES}")
  sb.appendLine("═══════════════════════════════════════")
  sb.appendLine("📊 *تقرير تحليلات ورسوم بيانية وإحصائيات المعامل*")
  sb.appendLine("--------------------------------------------")
  sb.appendLine("📅 الفترة: $period")
  sb.appendLine("💰 إجمالي الإيرادات: ${formatCurrency(totalRevenue, currency)}")
  sb.appendLine("📦 إجمالي الإرساليات: $totalShipments ($totalPieces سن/قطعة)")
  sb.appendLine("--------------------------------------------")
  sb.appendLine("📈 *الملخص الشهري:*")
  monthlyData.reversed().forEach { m ->
    sb.appendLine("• ${m.monthNameAr}: ${formatCurrency(m.revenue, currency)} | ${m.shipmentsCount} إرسالية (${m.piecesCount} سن)")
  }
  sb.appendLine("--------------------------------------------")
  sb.appendLine("📍 ${ClinicInfo.ADDRESS}")
  sb.appendLine("📞 للتواصل: ${ClinicInfo.PHONES}")
  sb.appendLine("═══════════════════════════════════════")
  return sb.toString()
}
