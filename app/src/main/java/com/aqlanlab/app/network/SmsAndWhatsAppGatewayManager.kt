package com.aqlanlab.app.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aqlanlab.app.ui.components.ClinicInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SmsProvider(val id: String, val titleAr: String, val defaultEndpoint: String) {
  YEMEN_MOBILE("yemen_mobile", "يمن موبايل (Yemen Mobile Bulk SMS Gateway)", "https://api.yemenmobile.com.ye/sms/v1/send"),
  UNIFONIC("unifonic", "يونيفونيك (Unifonic Cloud SMS)", "https://api.unifonic.com/rest/SMS/messages"),
  TWILIO("twilio", "تويليو (Twilio Programmable SMS)", "https://api.twilio.com/2010-04-01/Accounts"),
  CUSTOM_HTTP("custom_http", "بوابة سحابية مخصصة (Custom REST / HTTP Gateway)", "https://sms-gateway.example.com/api/send")
}

enum class WhatsAppGatewayMode(val id: String, val titleAr: String) {
  DIRECT_APP("direct_app", "واتساب مباشر (تطبيق الهاتف الرسمي - مجاني)"),
  META_CLOUD_API("meta_cloud", "واتساب كلاود للأعمال (Meta WhatsApp Cloud API)"),
  ULTRAMSG("ultramsg", "بوابة ألترا مسج السحابية (UltraMsg WhatsApp API)"),
  CUSTOM_WEBHOOK("custom_webhook", "بوابة ويب هوك مخصصة (Custom Webhook Gateway)")
}

data class SmsGatewayConfig(
  val isEnabled: Boolean = false,
  val provider: SmsProvider = SmsProvider.YEMEN_MOBILE,
  val senderId: String = "AqlanDental",
  val apiUrl: String = SmsProvider.YEMEN_MOBILE.defaultEndpoint,
  val apiUsername: String = "",
  val apiKeyOrPassword: String = "",
  val accountSidOrToken: String = "",
  val autoSendOnStatusChange: Boolean = true,
  val defaultAdminPhone: String = ClinicInfo.PHONE_PRIMARY
)

data class WhatsAppGatewayConfig(
  val mode: WhatsAppGatewayMode = WhatsAppGatewayMode.DIRECT_APP,
  val instanceId: String = "",
  val apiToken: String = "",
  val phoneNumberId: String = "",
  val webhookUrl: String = "",
  val autoNotifyReadyCase: Boolean = true,
  val autoSendInvoiceReceipt: Boolean = true
)

class SmsAndWhatsAppGatewayManager(private val context: Context) {

  private val TAG = "GatewayManager"
  private val prefs: SharedPreferences = context.getSharedPreferences("aqlan_messaging_gateway_prefs", Context.MODE_PRIVATE)

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

  // ─── SMS CONFIGURATION LOAD / SAVE ──────────────────────────

  fun loadSmsConfig(): SmsGatewayConfig {
    val providerId = prefs.getString("sms_provider", SmsProvider.YEMEN_MOBILE.id) ?: SmsProvider.YEMEN_MOBILE.id
    val provider = SmsProvider.values().firstOrNull { it.id == providerId } ?: SmsProvider.YEMEN_MOBILE

    return SmsGatewayConfig(
      isEnabled = prefs.getBoolean("sms_enabled", false),
      provider = provider,
      senderId = prefs.getString("sms_sender_id", "AqlanDental") ?: "AqlanDental",
      apiUrl = prefs.getString("sms_api_url", provider.defaultEndpoint) ?: provider.defaultEndpoint,
      apiUsername = prefs.getString("sms_api_username", "") ?: "",
      apiKeyOrPassword = prefs.getString("sms_api_key", "") ?: "",
      accountSidOrToken = prefs.getString("sms_account_sid", "") ?: "",
      autoSendOnStatusChange = prefs.getBoolean("sms_auto_send", true),
      defaultAdminPhone = prefs.getString("sms_admin_phone", ClinicInfo.PHONE_PRIMARY) ?: ClinicInfo.PHONE_PRIMARY
    )
  }

  fun saveSmsConfig(config: SmsGatewayConfig) {
    // SECURITY FIX: reject non-HTTPS endpoints — gateway credentials (API keys) are
    // sent to this URL, and a plain-HTTP URL would leak them in cleartext.
    val safeUrl = config.apiUrl.trim().let { url ->
      if (url.isEmpty() || url.startsWith("https://")) url else providerDefaultHttps(config.provider)
    }
    prefs.edit()
      .putBoolean("sms_enabled", config.isEnabled)
      .putString("sms_provider", config.provider.id)
      .putString("sms_sender_id", config.senderId.trim())
      .putString("sms_api_url", safeUrl)
      .putString("sms_api_username", config.apiUsername.trim())
      .putString("sms_api_key", config.apiKeyOrPassword.trim())
      .putString("sms_account_sid", config.accountSidOrToken.trim())
      .putBoolean("sms_auto_send", config.autoSendOnStatusChange)
      .putString("sms_admin_phone", config.defaultAdminPhone.trim())
      .apply()
  }

  private fun providerDefaultHttps(provider: SmsProvider): String = provider.defaultEndpoint

  // ─── WHATSAPP CONFIGURATION LOAD / SAVE ──────────────────────

  fun loadWhatsAppConfig(): WhatsAppGatewayConfig {
    val modeId = prefs.getString("wa_mode", WhatsAppGatewayMode.DIRECT_APP.id) ?: WhatsAppGatewayMode.DIRECT_APP.id
    val mode = WhatsAppGatewayMode.values().firstOrNull { it.id == modeId } ?: WhatsAppGatewayMode.DIRECT_APP

    return WhatsAppGatewayConfig(
      mode = mode,
      instanceId = prefs.getString("wa_instance_id", "") ?: "",
      apiToken = prefs.getString("wa_api_token", "") ?: "",
      phoneNumberId = prefs.getString("wa_phone_id", "") ?: "",
      webhookUrl = prefs.getString("wa_webhook_url", "") ?: "",
      autoNotifyReadyCase = prefs.getBoolean("wa_auto_ready", true),
      autoSendInvoiceReceipt = prefs.getBoolean("wa_auto_invoice", true)
    )
  }

  fun saveWhatsAppConfig(config: WhatsAppGatewayConfig) {
    prefs.edit()
      .putString("wa_mode", config.mode.id)
      .putString("wa_instance_id", config.instanceId.trim())
      .putString("wa_api_token", config.apiToken.trim())
      .putString("wa_phone_id", config.phoneNumberId.trim())
      .putString("wa_webhook_url", config.webhookUrl.trim())
      .putBoolean("wa_auto_ready", config.autoNotifyReadyCase)
      .putBoolean("wa_auto_invoice", config.autoSendInvoiceReceipt)
      .apply()
  }

  // ─── SEND SMS VIA GATEWAY (BACKEND / CLOUD DISPATCH) ─────────

  suspend fun sendSmsViaGateway(
    recipientPhone: String,
    messageText: String
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val config = loadSmsConfig()
    val cleanPhone = recipientPhone.replace(Regex("[^0-9+]"), "")

    if (cleanPhone.isEmpty()) {
      return@withContext Pair(false, "رقم هاتف المستلم غير صحيح")
    }

    if (!config.isEnabled) {
      return@withContext Pair(false, "بوابة الرسائل SMS غير مفعلة في الإعدادات. يرجى تفعيلها وإدخال بيانات الربط.")
    }

    if (config.apiKeyOrPassword.isBlank() && config.apiUsername.isBlank()) {
      return@withContext Pair(false, "بيانات الاعتماد لمزود SMS (اسم المستخدم أو مفتاح الـ API) فارغة.")
    }

    try {
      when (config.provider) {
        SmsProvider.YEMEN_MOBILE -> {
          // Yemen Mobile Bulk SMS JSON Payload
          val json = JSONObject().apply {
            put("username", config.apiUsername)
            put("password", config.apiKeyOrPassword)
            put("sender", config.senderId.ifBlank { "AqlanDental" })
            put("recipient", cleanPhone)
            put("message", messageText)
          }

          val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
          val request = Request.Builder()
            .url(config.apiUrl.ifBlank { SmsProvider.YEMEN_MOBILE.defaultEndpoint })
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

          // FIX: response bodies must be closed (leaked connections under repeated use)
          httpClient.newCall(request).execute().use { response ->
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
              Log.d(TAG, "SMS sent via Yemen Mobile")
              Pair(true, "تم إرسال رسالة الـ SMS بنجاح باسم [${config.senderId}] عبر يمن موبايل 🚀")
            } else {
              Log.e(TAG, "Yemen Mobile SMS Failed: ${response.code}")
              Pair(false, "فشل الإرسال من بوابة يمن موبايل (كود: ${response.code}). يرجى التحقق من الرصيد والبيانات.")
            }
          }
        }

        SmsProvider.UNIFONIC -> {
          val json = JSONObject().apply {
            put("AppSid", config.apiKeyOrPassword)
            put("SenderID", config.senderId)
            put("Recipient", cleanPhone)
            put("Body", messageText)
          }
          val body = json.toString().toRequestBody("application/json".toMediaType())
          val request = Request.Builder()
            .url(config.apiUrl.ifBlank { SmsProvider.UNIFONIC.defaultEndpoint })
            .post(body)
            .build()

          httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              Pair(true, "تم إرسال الرسالة بنجاح عبر Unifonic باسم [${config.senderId}]")
            } else {
              Pair(false, "فشل الإرسال من Unifonic: ${response.message}")
            }
          }
        }

        SmsProvider.CUSTOM_HTTP, SmsProvider.TWILIO -> {
          // SECURITY FIX: send the credential ONCE (Authorization header) instead of
          // duplicating it in the JSON body, and require an HTTPS endpoint.
          if (!config.apiUrl.startsWith("https://")) {
            return@withContext Pair(false, "رابط البوابة المخصصة يجب أن يبدأ بـ https://")
          }
          val json = JSONObject().apply {
            put("sender_id", config.senderId)
            put("to", cleanPhone)
            put("text", messageText)
          }
          val body = json.toString().toRequestBody("application/json".toMediaType())
          val request = Request.Builder()
            .url(config.apiUrl)
            .post(body)
            .addHeader("Authorization", "Bearer ${config.apiKeyOrPassword}")
            .build()

          httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              Pair(true, "تم إرسال رسالة SMS بنجاح عبر البوابة السحابية باسم [${config.senderId}]")
            } else {
              Pair(false, "خطأ في الاتصال بالبوابة (${response.code})")
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error sending SMS: ${e.message}", e)
      Pair(false, "تعذر الاتصال بخادم الرسائل: ${e.localizedMessage ?: e.message}")
    }
  }

  // ─── SEND WHATSAPP VIA CLOUD GATEWAY ─────────────────────────

  suspend fun sendWhatsAppViaCloudGateway(
    recipientPhone: String,
    messageText: String
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val config = loadWhatsAppConfig()
    val cleanPhone = recipientPhone.replace(Regex("[^0-9]"), "")

    if (config.mode == WhatsAppGatewayMode.DIRECT_APP) {
      return@withContext Pair(false, "نمط الواتساب محدد على (تطبيق الهاتف المباشر).")
    }

    if (config.apiToken.isBlank()) {
      return@withContext Pair(false, "مفتاح الـ API Token للواتساب غير مدخل.")
    }

    try {
      when (config.mode) {
        WhatsAppGatewayMode.ULTRAMSG -> {
          val endpoint = "https://api.ultramsg.com/${config.instanceId}/messages/chat"
          val json = JSONObject().apply {
            put("token", config.apiToken)
            put("to", cleanPhone)
            put("body", messageText)
          }
          val body = json.toString().toRequestBody("application/json".toMediaType())
          val request = Request.Builder().url(endpoint).post(body).build()

          httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              Pair(true, "تم إرسال رسالة الواتساب السحابية آلياً بنجاح عبر UltraMsg 🟢")
            } else {
              Pair(false, "فشل الإرسال من خادم الواتساب: ${response.message}")
            }
          }
        }

        WhatsAppGatewayMode.META_CLOUD_API -> {
          val endpoint = "https://graph.facebook.com/v18.0/${config.phoneNumberId}/messages"
          val json = JSONObject().apply {
            put("messaging_product", "whatsapp")
            put("to", cleanPhone)
            put("type", "text")
            put("text", JSONObject().put("body", messageText))
          }
          val body = json.toString().toRequestBody("application/json".toMediaType())
          val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .addHeader("Authorization", "Bearer ${config.apiToken}")
            .build()

          httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              Pair(true, "تم إرسال رسالة الواتساب الرسمية عبر Meta Cloud API 🟢")
            } else {
              Pair(false, "خطأ من Meta Cloud API: ${response.code}")
            }
          }
        }

        WhatsAppGatewayMode.CUSTOM_WEBHOOK -> {
          // SECURITY FIX: require an HTTPS webhook (the message content and any token
          // would otherwise travel in cleartext)
          if (!config.webhookUrl.startsWith("https://")) {
            return@withContext Pair(false, "رابط الويب هوك يجب أن يبدأ بـ https://")
          }
          val json = JSONObject().apply {
            put("phone", cleanPhone)
            put("message", messageText)
          }
          val body = json.toString().toRequestBody("application/json".toMediaType())
          val request = Request.Builder().url(config.webhookUrl).post(body).build()

          httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              Pair(true, "تم إرسال رسالة الواتساب بنجاح عبر الويب هوك")
            } else {
              Pair(false, "تعذر الإرسال عبر الويب هوك: ${response.code}")
            }
          }
        }

        WhatsAppGatewayMode.DIRECT_APP -> Pair(false, "مباشر")
      }
    } catch (e: Exception) {
      Pair(false, "خطأ أثناء إرسال الواتساب السحابي: ${e.message}")
    }
  }
}
