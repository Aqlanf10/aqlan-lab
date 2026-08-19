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
import com.example.data.models.Laboratory
import com.example.data.models.Payment
import com.example.data.models.PaymentMethod
import com.example.data.models.UserRole
import com.example.ui.components.*
import com.example.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
  viewModel: DentalLabViewModel,
  onNavigateToLabDetail: (Long) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val exchangeRates by viewModel.exchangeRates.collectAsState()
  val stats by viewModel.dashboardStats.collectAsState()
  val labSummaries by viewModel.labAccountSummaries.collectAsState()
  val allPayments by viewModel.allPayments.collectAsState()
  val allWorkTypes by viewModel.allWorkTypes.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()
  val allLabPrices by viewModel.allLabPrices.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedStatementLabId by remember { mutableStateOf<Long?>(null) }
  var selectedStatementCurrency by remember { mutableStateOf<com.example.data.models.AppCurrency?>(null) }
  var paymentLabTarget by remember { mutableStateOf<Laboratory?>(null) }
  var selectedSummaryCurrency by remember { mutableStateOf(com.example.data.models.AppCurrency.SAR) }
  var showQuickConverterDialog by remember { mutableStateOf(false) }
  val isFetchingLiveRates by viewModel.isFetchingExchangeRates.collectAsState()

  // Set default lab for statement if available
  LaunchedEffect(allLabs) {
    if (selectedStatementLabId == null && allLabs.isNotEmpty()) {
      selectedStatementLabId = allLabs.first().id
    }
  }

  // Restrict access if somehow accessed by Staff
  if (activeUser.role == UserRole.STAFF) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("النظام المالي") },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
          }
        )
      }
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        EmptyStateView(
          title = "غير مصرح بالدخول",
          description = "النظام المالي وكشوفات حساب المعامل مخصصة فقط لمدير النظام والمحاسب.",
          icon = Icons.Default.Lock
        )
      }
    }
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "الإدارة المالية وحسابات المعامل",
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
          IconButton(onClick = { showQuickConverterDialog = true }) {
            Icon(
              imageVector = Icons.Default.Calculate,
              contentDescription = "محول العملات اللحظي",
              tint = MaterialTheme.colorScheme.primary
            )
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
      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 12.dp
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("أرصدة المعامل", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("كشوف الحساب التفصيلية", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("مصفوفة الأسعار الذكية", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("سجل الدفعات (${allPayments.size})", fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 4,
          onClick = { selectedTab = 4 },
          text = { Text("محول العملات وأسعار الصرف", fontWeight = FontWeight.Bold) }
        )
      }

      when (selectedTab) {
        0 -> {
          // Lab Balances Tab
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
          ) {
            // Official Center Branding Card
            item {
              com.example.ui.components.AqlanClinicHeaderCard()
            }

            // Overall Financial Header Cards
            item {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
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
                      text = "الوضع المالي الشامل (3 عملات)",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )

                    // Currency Toggle Tabs
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      com.example.data.models.AppCurrency.ALL.forEach { curr ->
                        val isSel = selectedSummaryCurrency == curr
                        FilterChip(
                          selected = isSel,
                          onClick = { selectedSummaryCurrency = curr },
                          label = { Text("${curr.flag} ${curr.symbolAr}", fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                        )
                      }
                    }
                  }

                  val activeCurrBalance = when (selectedSummaryCurrency) {
                    com.example.data.models.AppCurrency.YER -> stats.yerStats
                    com.example.data.models.AppCurrency.SAR -> stats.sarStats
                    com.example.data.models.AppCurrency.USD -> stats.usdStats
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column {
                      Text("إجمالي الأعمال (${selectedSummaryCurrency.symbolAr})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      PriceDisplay(amount = activeCurrBalance.totalBilled, userRole = activeUser.role, currencyCode = selectedSummaryCurrency.name, style = MaterialTheme.typography.titleLarge)
                    }
                    Column {
                      Text("إجمالي المسدد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      PriceDisplay(amount = activeCurrBalance.totalPaid, userRole = activeUser.role, currencyCode = selectedSummaryCurrency.name, color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleLarge)
                    }
                    Column {
                      Text("المستحق المتبقي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      PriceDisplay(amount = activeCurrBalance.remainingBalance, userRole = activeUser.role, currencyCode = selectedSummaryCurrency.name, color = Color(0xFFD32F2F), style = MaterialTheme.typography.titleLarge)
                    }
                  }

                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                  // All currencies mini-summary strip
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "أرصدة العملات المتبقية:",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE3F2FD)
                      ) {
                        Text(
                          text = "🇸🇦 ${com.example.data.models.AppCurrency.SAR.formatAmount(stats.sarStats.remainingBalance)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = Color(0xFF1565C0),
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }

                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE8F5E9)
                      ) {
                        Text(
                          text = "🇾🇪 ${com.example.data.models.AppCurrency.YER.formatAmount(stats.yerStats.remainingBalance)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = Color(0xFF2E7D32),
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }

                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3E0)
                      ) {
                        Text(
                          text = "🇺🇸 ${com.example.data.models.AppCurrency.USD.formatAmount(stats.usdStats.remainingBalance)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = Color(0xFFE65100),
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

            item {
              Text(
                text = "كشف أرصدة ومستحقات كل معمل (حسب العملة)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            items(labSummaries, key = { it.lab.id }) { summary ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onNavigateToLabDetail(summary.lab.id) }
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(summary.lab.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        CurrencyBadge(currency = summary.defaultCurrency)
                      }
                      Text("${summary.totalShipments} إرساليات (${summary.totalPieces} قطعة)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Button(
                      onClick = { paymentLabTarget = summary.lab },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier.height(36.dp)
                    ) {
                      Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("سداد دفعة", fontSize = 12.sp)
                    }
                  }

                  // Multi-currency balances breakdown chips for this lab
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    summary.currencyBalances.forEach { (curr, bal) ->
                      if (bal.totalBilled > 0 || bal.totalPaid > 0) {
                        Surface(
                          shape = RoundedCornerShape(8.dp),
                          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                          modifier = Modifier.weight(1f)
                        ) {
                          Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${curr.flag} ${curr.symbolAr}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                              text = curr.formatAmount(bal.remainingBalance),
                              style = MaterialTheme.typography.labelMedium,
                              fontWeight = FontWeight.Bold,
                              color = if (bal.remainingBalance > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                            )
                          }
                        }
                      }
                    }
                  }

                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column {
                      Text("إجمالي العملة الأساسية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      PriceDisplay(amount = summary.totalBilled, userRole = activeUser.role, currencyCode = summary.defaultCurrency.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                      Text("المسدد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      PriceDisplay(amount = summary.totalPaid, userRole = activeUser.role, currencyCode = summary.defaultCurrency.name, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                      Text("الرصيد المتبقي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      PriceDisplay(amount = summary.remainingBalance, userRole = activeUser.role, currencyCode = summary.defaultCurrency.name, color = if (summary.remainingBalance > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32), style = MaterialTheme.typography.titleMedium)
                    }
                  }
                }
              }
            }
          }
        }
        1 -> {
          // Detailed Statement Tab (كشف حساب مدين / دائن / قطع)
          val selectedLab = allLabs.find { it.id == selectedStatementLabId } ?: allLabs.firstOrNull()
          val targetShipments = remember(allShipments, selectedLab, selectedStatementCurrency) {
            if (selectedLab != null) {
              allShipments
                .filter { it.labId == selectedLab.id }
                .filter { selectedStatementCurrency == null || com.example.data.models.AppCurrency.fromCode(it.currency) == selectedStatementCurrency }
            } else emptyList()
          }
          val targetPayments = remember(allPayments, selectedLab, selectedStatementCurrency) {
            if (selectedLab != null) {
              allPayments
                .filter { it.labId == selectedLab.id }
                .filter { selectedStatementCurrency == null || com.example.data.models.AppCurrency.fromCode(it.currency) == selectedStatementCurrency }
            } else emptyList()
          }

          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
          ) {
            // Lab Selector Chips
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "1. اختر المعمل لعرض كشف الحساب والقطع:",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  allLabs.forEach { lab ->
                    val isSelected = lab.id == selectedLab?.id
                    FilterChip(
                      selected = isSelected,
                      onClick = { selectedStatementLabId = lab.id },
                      label = { Text(lab.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                      leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                      } else null
                    )
                  }
                }

                Text(
                  text = "2. تصفية الكشف حسب العملة:",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  FilterChip(
                    selected = selectedStatementCurrency == null,
                    onClick = { selectedStatementCurrency = null },
                    label = { Text("جميع العملات", fontSize = 11.sp, fontWeight = if (selectedStatementCurrency == null) FontWeight.Bold else FontWeight.Normal) }
                  )
                  com.example.data.models.AppCurrency.ALL.forEach { curr ->
                    val isSel = selectedStatementCurrency == curr
                    FilterChip(
                      selected = isSel,
                      onClick = { selectedStatementCurrency = curr },
                      label = { Text("${curr.flag} ${curr.symbolAr}", fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                    )
                  }
                }
              }
            }

            if (selectedLab != null) {
              item {
                val statementCurrencyCode = selectedStatementCurrency?.name ?: selectedLab.defaultCurrency
                StatementTableView(
                  shipments = targetShipments,
                  payments = targetPayments,
                  currencyCode = statementCurrencyCode,
                  labName = selectedLab.name,
                  labPhone = selectedLab.phone
                )
              }
            } else {
              item {
                EmptyStateView(
                  title = "لا توجد معامل مسجلة",
                  description = "أضف معملاً أولاً لعرض كشوفات الحساب"
                )
              }
            }
          }
        }
        2 -> {
          // Smart Pricing Matrix Tab
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
          ) {
            item {
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Text(
                    text = "نظام الأسعار الذكي يحسب تلقائياً تكلفة كل إرسالية عند اختيار المعمل ونوع العمل بناء على هذه القائمة.",
                    style = MaterialTheme.typography.bodySmall
                  )
                }
              }
            }

            items(allWorkTypes, key = { it.id }) { workType ->
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(workType.nameAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                      Text(workType.category, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                      Text(
                        text = "الأساسي: ${workType.defaultPrice} $currency",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                      )
                    }
                  }

                  Text("الأسعار المخصصة للمعامل:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                  allLabs.forEach { lab ->
                    val customPriceObj = allLabPrices.find { it.labId == lab.id && it.workTypeId == workType.id }
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text("• ${lab.name}", style = MaterialTheme.typography.bodySmall)
                      Text(
                        text = if (customPriceObj != null) "${customPriceObj.customPrice} $currency (مخصص)" else "${workType.defaultPrice} $currency (افتراضي)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (customPriceObj != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (customPriceObj != null) MaterialTheme.colorScheme.primary else Color.Gray
                      )
                    }
                  }
                }
              }
            }
          }
        }
        3 -> {
          // Payments Ledger Tab
          if (allPayments.isEmpty()) {
            EmptyStateView(
              title = "لا توجد دفعات مسجلة",
              description = "سجل دفعاتك للمختبرات لتتبع المسدد والمتبقي بدقة"
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
            ) {
              items(allPayments, key = { it.id }) { payment ->
                PaymentCardItem(
                  payment = payment,
                  currencyCode = currency,
                  onDelete = { viewModel.deletePayment(payment) }
                )
              }
            }
          }
        }
        4 -> {
          // Real-Time Currency Converter & Rates Tab
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
          ) {
            item {
              LiveCurrencyConverterCard(
                exchangeRates = exchangeRates,
                onFetchLiveRates = { viewModel.fetchLiveExchangeRates() },
                onApplyPreset = { preset -> viewModel.applyExchangeRatePreset(preset) },
                onCustomRatesChange = { usdToYer, sarToYer, usdToSar ->
                  viewModel.updateExchangeRates(usdToYer, sarToYer, usdToSar)
                },
                isFetchingLive = isFetchingLiveRates
              )
            }
          }
        }
      }
    }

    if (showQuickConverterDialog) {
      LiveCurrencyConverterDialog(
        exchangeRates = exchangeRates,
        onDismiss = { showQuickConverterDialog = false },
        onFetchLiveRates = { viewModel.fetchLiveExchangeRates() },
        onApplyPreset = { preset -> viewModel.applyExchangeRatePreset(preset) },
        onCustomRatesChange = { usdToYer, sarToYer, usdToSar ->
          viewModel.updateExchangeRates(usdToYer, sarToYer, usdToSar)
        },
        isFetchingLive = isFetchingLiveRates
      )
    }

    if (paymentLabTarget != null) {
      RecordPaymentDialog(
        lab = paymentLabTarget!!,
        currencyCode = paymentLabTarget!!.defaultCurrency,
        exchangeRates = exchangeRates,
        onDismiss = { paymentLabTarget = null },
        onSave = { amount, method, receiptNo, notes ->
          viewModel.recordPayment(
            labId = paymentLabTarget!!.id,
            labName = paymentLabTarget!!.name,
            amount = amount,
            currency = paymentLabTarget!!.defaultCurrency,
            paymentMethod = method,
            receiptNumber = receiptNo,
            notes = notes
          )
          paymentLabTarget = null
        },
        onSaveMultiCurrency = { amount, targetCurr, paidAmount, paidCurr, exRate, method, receiptNo, notes ->
          viewModel.recordPayment(
            labId = paymentLabTarget!!.id,
            labName = paymentLabTarget!!.name,
            amount = amount,
            currency = targetCurr,
            paidAmount = paidAmount,
            paidCurrency = paidCurr,
            exchangeRate = exRate,
            paymentMethod = method,
            receiptNumber = receiptNo,
            notes = notes
          )
          paymentLabTarget = null
        }
      )
    }
  }
}
