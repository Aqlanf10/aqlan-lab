package com.aqlanlab.app.data.models

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * First-Class Multi-Currency Domain Model for Yemen Dental Clinic
 * Supports 3 primary currencies used in Yemen dental laboratories:
 * 1. Yemeni Rial (YER / ر.ي) - Local market currency
 * 2. Saudi Riyal (SAR / ر.س) - Primary pricing currency for most digital dental labs
 * 3. US Dollar (USD / $) - Premium implant, CAD/CAM milling & specialized components
 */
enum class AppCurrency(
  val code: String,
  val symbolAr: String,
  val nameAr: String,
  val nameEn: String,
  val flag: String,
  val defaultDecimals: Int
) {
  YER(
    code = "YER",
    symbolAr = "ر.ي",
    nameAr = "ريال يمني",
    nameEn = "Yemeni Rial",
    flag = "🇾🇪",
    defaultDecimals = 0
  ),
  SAR(
    code = "SAR",
    symbolAr = "ر.س",
    nameAr = "ريال سعودي",
    nameEn = "Saudi Riyal",
    flag = "🇸🇦",
    defaultDecimals = 2
  ),
  USD(
    code = "USD",
    symbolAr = "$",
    nameAr = "دولار أمريكي",
    nameEn = "US Dollar",
    flag = "🇺🇸",
    defaultDecimals = 2
  );

  val displayName: String
    get() = "$flag $nameAr ($symbolAr)"

  val shortLabel: String
    get() = "$flag $symbolAr"

  fun formatAmount(amount: Double, withSymbol: Boolean = true): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    val pattern = if (defaultDecimals == 0 || (amount % 1.0 == 0.0 && defaultDecimals == 0)) {
      "#,##0"
    } else {
      "#,##0.00"
    }
    val formatter = DecimalFormat(pattern, symbols)
    val formattedNumber = formatter.format(amount)
    return if (withSymbol) {
      if (this == USD) "$$formattedNumber" else "$formattedNumber $symbolAr"
    } else {
      formattedNumber
    }
  }

  companion object {
    val ALL = entries.toList()

    fun fromCode(code: String?): AppCurrency {
      if (code.isNullOrBlank()) return SAR
      val clean = code.trim().uppercase()
      return when {
        clean == "YER" || clean == "YR" || clean == "ر.ي" || clean == "ريال يمني" -> YER
        clean == "SAR" || clean == "SR" || clean == "ر.س" || clean == "ريال سعودي" -> SAR
        clean == "USD" || clean == "$" || clean == "دولار" || clean == "دولار أمريكي" -> USD
        else -> entries.find { it.code.equals(clean, ignoreCase = true) } ?: SAR
      }
    }
  }
}

/**
 * Exchange rate configuration and dynamic conversion helpers
 */
data class ExchangeRates(
  val usdToYer: Double = 535.0, // Sana'a market default
  val sarToYer: Double = 142.0, // Sana'a market default
  val usdToSar: Double = 3.75,  // Standard peg
  val source: String = "سوق صنعاء (افتراضي)",
  val isLive: Boolean = false,
  val lastUpdated: Long = System.currentTimeMillis()
) {
  fun convert(amount: Double, from: AppCurrency, to: AppCurrency): Double {
    if (from == to || amount == 0.0) return amount

    // Convert 'from' to YER base first
    val amountInYer = when (from) {
      AppCurrency.YER -> amount
      AppCurrency.SAR -> amount * sarToYer
      AppCurrency.USD -> amount * usdToYer
    }

    // Convert from YER base to 'to'
    return when (to) {
      AppCurrency.YER -> amountInYer
      AppCurrency.SAR -> if (sarToYer > 0) amountInYer / sarToYer else 0.0
      AppCurrency.USD -> if (usdToYer > 0) amountInYer / usdToYer else 0.0
    }
  }

  fun getRateBetween(from: AppCurrency, to: AppCurrency): Double {
    if (from == to) return 1.0
    return convert(1.0, from, to)
  }

  fun formatRateDescription(from: AppCurrency, to: AppCurrency): String {
    val rate = getRateBetween(from, to)
    val formattedRate = if (rate >= 10) String.format(Locale.US, "%.1f", rate) else String.format(Locale.US, "%.4f", rate)
    return "1 ${from.symbolAr} = $formattedRate ${to.symbolAr}"
  }
}

/**
 * Currency-specific balance container
 */
data class CurrencyBalance(
  val currency: AppCurrency,
  val totalBilled: Double = 0.0,
  val totalPaid: Double = 0.0,
  val remainingBalance: Double = 0.0,
  val shipmentCount: Int = 0,
  val pieceCount: Int = 0
) {
  val hasTransactions: Boolean
    get() = totalBilled > 0.0 || totalPaid > 0.0 || remainingBalance != 0.0
}
