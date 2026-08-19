package com.example.data.network

import android.util.Log
import com.example.data.models.AppCurrency
import com.example.data.models.ExchangeRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service to fetch real-time exchange rates for YER, SAR, and USD
 * from public real-time exchange rate APIs with offline fallback and Yemen regional presets.
 */
object ExchangeRateService {
  private const val TAG = "ExchangeRateService"
  private const val PRIMARY_API_URL = "https://open.er-api.com/v6/latest/USD"
  private const val FALLBACK_API_URL = "https://api.exchangerate-api.com/v4/latest/USD"

  sealed class RatePreset(val nameAr: String, val descriptionAr: String, val rates: ExchangeRates) {
    data object SanaaMarket : RatePreset(
      nameAr = "سوق صنعاء والمحافظات المجاورة",
      descriptionAr = "سعر السوق المعتمد (الدولار ≈ 535 ر.ي | السعودي ≈ 142 ر.ي)",
      rates = ExchangeRates(
        usdToYer = 535.0,
        sarToYer = 142.0,
        usdToSar = 3.75,
        source = "سوق صنعاء",
        isLive = false,
        lastUpdated = System.currentTimeMillis()
      )
    )

    data object AdenMarket : RatePreset(
      nameAr = "سوق عدن والمحافظات الجنوبية",
      descriptionAr = "سعر السوق المعتمد (الدولار ≈ 1950 ر.ي | السعودي ≈ 515 ر.ي)",
      rates = ExchangeRates(
        usdToYer = 1950.0,
        sarToYer = 515.0,
        usdToSar = 3.75,
        source = "سوق عدن",
        isLive = false,
        lastUpdated = System.currentTimeMillis()
      )
    )
  }

  /**
   * Fetches the latest live exchange rates from the web.
   * Returns updated ExchangeRates or null if network is unavailable.
   */
  suspend fun fetchLiveRates(): ExchangeRates? = withContext(Dispatchers.IO) {
    try {
      val response = makeHttpRequest(PRIMARY_API_URL) ?: makeHttpRequest(FALLBACK_API_URL)
      if (response != null) {
        val json = JSONObject(response)
        val ratesObj = if (json.has("rates")) json.getJSONObject("rates") else null
        if (ratesObj != null) {
          val usdToSar = ratesObj.optDouble("SAR", 3.75)
          val usdToYer = ratesObj.optDouble("YER", 535.0)
          val sarToYer = if (usdToSar > 0) usdToYer / usdToSar else 142.0

          Log.d(TAG, "Successfully fetched live rates: USD->SAR: $usdToSar, USD->YER: $usdToYer, SAR->YER: $sarToYer")

          return@withContext ExchangeRates(
            usdToYer = usdToYer,
            sarToYer = sarToYer,
            usdToSar = usdToSar,
            source = "تحديث مباشر (Live API)",
            isLive = true,
            lastUpdated = System.currentTimeMillis()
          )
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching live exchange rates: ${e.message}", e)
    }
    null
  }

  private fun makeHttpRequest(urlString: String): String? {
    var connection: HttpURLConnection? = null
    var reader: BufferedReader? = null
    return try {
      val url = URL(urlString)
      connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "DentalLabManager/1.0")
      }

      if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        val stream = connection.inputStream
        reader = BufferedReader(InputStreamReader(stream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
          response.append(line)
        }
        response.toString()
      } else {
        Log.w(TAG, "HTTP response error: ${connection.responseCode}")
        null
      }
    } catch (e: Exception) {
      Log.w(TAG, "Network connection error for $urlString: ${e.message}")
      null
    } finally {
      reader?.close()
      connection?.disconnect()
    }
  }
}
