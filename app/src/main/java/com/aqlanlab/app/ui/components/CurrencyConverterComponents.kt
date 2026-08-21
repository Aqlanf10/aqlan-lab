package com.aqlanlab.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.AppCurrency
import com.aqlanlab.app.data.models.ExchangeRates
import com.aqlanlab.app.data.network.ExchangeRateService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Real-Time Currency Converter Card & Calculator
 */
@Composable
fun LiveCurrencyConverterCard(
  exchangeRates: ExchangeRates,
  onFetchLiveRates: () -> Unit,
  onApplyPreset: (ExchangeRateService.RatePreset) -> Unit,
  onCustomRatesChange: (usdToYer: Double, sarToYer: Double, usdToSar: Double) -> Unit,
  modifier: Modifier = Modifier,
  initialFromCurrency: AppCurrency = AppCurrency.SAR,
  initialToCurrency: AppCurrency = AppCurrency.YER,
  initialAmount: Double = 1000.0,
  onApplyConvertedAmount: ((amount: Double, from: AppCurrency, to: AppCurrency) -> Unit)? = null,
  isFetchingLive: Boolean = false
) {
  var fromCurrency by remember { mutableStateOf(initialFromCurrency) }
  var toCurrency by remember { mutableStateOf(initialToCurrency) }
  var amountInput by remember { mutableStateOf(if (initialAmount > 0) initialAmount.toString() else "1000") }
  var showCustomRatesDialog by remember { mutableStateOf(false) }

  val formattedDate = remember(exchangeRates.lastUpdated) {
    SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date(exchangeRates.lastUpdated))
  }

  val amount = amountInput.toDoubleOrNull() ?: 0.0
  val convertedAmount = remember(amount, fromCurrency, toCurrency, exchangeRates) {
    exchangeRates.convert(amount, fromCurrency, toCurrency)
  }
  val rate1 = remember(fromCurrency, toCurrency, exchangeRates) {
    exchangeRates.getRateBetween(fromCurrency, toCurrency)
  }
  val rateInverse = remember(fromCurrency, toCurrency, exchangeRates) {
    exchangeRates.getRateBetween(toCurrency, fromCurrency)
  }

  Card(
    modifier = modifier.fillMaxWidth().testTag("live_currency_converter_card"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header: Title + Live Status & Refresh
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
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.CurrencyExchange,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          }
          Column {
            Text(
              text = "محول العملات وأسعار الصرف المباشرة",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "المصدر: ${exchangeRates.source} • $formattedDate",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Live Refresh Button
        IconButton(
          onClick = onFetchLiveRates,
          enabled = !isFetchingLive,
          modifier = Modifier.testTag("btn_refresh_exchange_rates")
        ) {
          if (isFetchingLive) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "تحديث أسعار الصرف",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      // Quick Market Presets (Sana'a / Aden / Custom)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = exchangeRates.source.contains("صنعاء"),
          onClick = { onApplyPreset(ExchangeRateService.RatePreset.SanaaMarket) },
          label = { Text("سوق صنعاء (142 / 535)", style = MaterialTheme.typography.labelSmall) },
          modifier = Modifier.weight(1f)
        )
        FilterChip(
          selected = exchangeRates.source.contains("عدن"),
          onClick = { onApplyPreset(ExchangeRateService.RatePreset.AdenMarket) },
          label = { Text("سوق عدن (515 / 1950)", style = MaterialTheme.typography.labelSmall) },
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = { showCustomRatesDialog = true },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            Icons.Default.Tune,
            contentDescription = "تخصيص أسعار الصرف",
            tint = MaterialTheme.colorScheme.outline
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Input Section: Amount + From Currency
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = amountInput,
          onValueChange = { amountInput = it },
          label = { Text("المبلغ المراد تحويله") },
          placeholder = { Text("0.0") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1.3f).testTag("input_converter_amount"),
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        // From Currency Selector Dropdown
        CurrencyChipDropdown(
          selectedCurrency = fromCurrency,
          onCurrencySelected = { fromCurrency = it },
          label = "من عملة",
          modifier = Modifier.weight(1f)
        )
      }

      // Swap button in center
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        FilledTonalIconButton(
          onClick = {
            val temp = fromCurrency
            fromCurrency = toCurrency
            toCurrency = temp
          },
          modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(38.dp)
            .testTag("btn_swap_currencies")
        ) {
          Icon(Icons.Default.SwapVert, contentDescription = "تبديل العملات", modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      }

      // Output Section: To Currency + Result
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
          modifier = Modifier.weight(1.3f).height(56.dp)
        ) {
          Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
          ) {
            Column {
              Text("المبلغ المعادل المحسوب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = toCurrency.formatAmount(convertedAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        // To Currency Selector Dropdown
        CurrencyChipDropdown(
          selectedCurrency = toCurrency,
          onCurrencySelected = { toCurrency = it },
          label = "إلى عملة",
          modifier = Modifier.weight(1f)
        )
      }

      // Rate Breakdown Ticker
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = exchangeRates.formatRateDescription(fromCurrency, toCurrency),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = exchangeRates.formatRateDescription(toCurrency, fromCurrency),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
          )
        }
      }

      // Optional Apply to Action Button (e.g. within Record Payment Dialog)
      if (onApplyConvertedAmount != null) {
        Button(
          onClick = { onApplyConvertedAmount(convertedAmount, fromCurrency, toCurrency) },
          modifier = Modifier.fillMaxWidth().testTag("btn_apply_converter_result"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "اعتماد المبلغ المحسوب (${toCurrency.formatAmount(convertedAmount)}) للدفعة",
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }

  if (showCustomRatesDialog) {
    CustomRatesDialog(
      currentRates = exchangeRates,
      onDismiss = { showCustomRatesDialog = false },
      onSave = { usdToYer, sarToYer, usdToSar ->
        onCustomRatesChange(usdToYer, sarToYer, usdToSar)
        showCustomRatesDialog = false
      }
    )
  }
}

/**
 * Dropdown selector for currency
 */
@Composable
fun CurrencyChipDropdown(
  selectedCurrency: AppCurrency,
  onCurrencySelected: (AppCurrency) -> Unit,
  label: String,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    OutlinedCard(
      onClick = { expanded = true },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "${selectedCurrency.flag} ${selectedCurrency.symbolAr}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      AppCurrency.ALL.forEach { currency ->
        DropdownMenuItem(
          text = {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(currency.flag, fontSize = 18.sp)
              Column {
                Text(currency.nameAr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("رمز: ${currency.code} (${currency.symbolAr})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          },
          onClick = {
            onCurrencySelected(currency)
            expanded = false
          },
          leadingIcon = {
            if (currency == selectedCurrency) {
              Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
          }
        )
      }
    }
  }
}

/**
 * Live Currency Converter Dialog
 */
@Composable
fun LiveCurrencyConverterDialog(
  exchangeRates: ExchangeRates,
  onDismiss: () -> Unit,
  onFetchLiveRates: () -> Unit,
  onApplyPreset: (ExchangeRateService.RatePreset) -> Unit,
  onCustomRatesChange: (usdToYer: Double, sarToYer: Double, usdToSar: Double) -> Unit,
  initialFromCurrency: AppCurrency = AppCurrency.SAR,
  initialToCurrency: AppCurrency = AppCurrency.YER,
  initialAmount: Double = 0.0,
  onApplyConvertedAmount: ((amount: Double, from: AppCurrency, to: AppCurrency) -> Unit)? = null,
  isFetchingLive: Boolean = false
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("حاسبة ومحول العملات المباشر", fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = "إغلاق")
        }
      }
    },
    text = {
      LiveCurrencyConverterCard(
        exchangeRates = exchangeRates,
        onFetchLiveRates = onFetchLiveRates,
        onApplyPreset = onApplyPreset,
        onCustomRatesChange = onCustomRatesChange,
        initialFromCurrency = initialFromCurrency,
        initialToCurrency = initialToCurrency,
        initialAmount = initialAmount,
        onApplyConvertedAmount = { amount, from, to ->
          onApplyConvertedAmount?.invoke(amount, from, to)
          onDismiss()
        },
        isFetchingLive = isFetchingLive
      )
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إغلاق")
      }
    }
  )
}

/**
 * Dialog to manually customize exchange rates
 */
@Composable
fun CustomRatesDialog(
  currentRates: ExchangeRates,
  onDismiss: () -> Unit,
  onSave: (usdToYer: Double, sarToYer: Double, usdToSar: Double) -> Unit
) {
  var sarToYerText by remember { mutableStateOf(currentRates.sarToYer.toString()) }
  var usdToYerText by remember { mutableStateOf(currentRates.usdToYer.toString()) }
  var usdToSarText by remember { mutableStateOf(currentRates.usdToSar.toString()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("تخصيص أسعار الصرف اليدوية", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "يمكنك تعديل أسعار الصرف يدوياً حسب سعر السوق اللحظي المعتمد في عيادتك:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = sarToYerText,
          onValueChange = { sarToYerText = it },
          label = { Text("سعر صرف 1 ريال سعودي = ؟ ريال يمني") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        OutlinedTextField(
          value = usdToYerText,
          onValueChange = { usdToYerText = it },
          label = { Text("سعر صرف 1 دولار أمريكي = ؟ ريال يمني") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        OutlinedTextField(
          value = usdToSarText,
          onValueChange = { usdToSarText = it },
          label = { Text("سعر صرف 1 دولار أمريكي = ؟ ريال سعودي") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val sarToYer = sarToYerText.toDoubleOrNull() ?: currentRates.sarToYer
          val usdToYer = usdToYerText.toDoubleOrNull() ?: currentRates.usdToYer
          val usdToSar = usdToSarText.toDoubleOrNull() ?: currentRates.usdToSar
          onSave(usdToYer, sarToYer, usdToSar)
        }
      ) {
        Text("حفظ الأسعار")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}
