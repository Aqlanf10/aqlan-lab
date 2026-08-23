package com.aqlanlab.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.aqlanlab.app.data.models.Payment
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.ui.components.ClinicInfo
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class WhatsAppTemplateType(val id: String, val titleAr: String, val iconEmoji: String, val descriptionAr: String) {
  PATIENT_INVOICE(
    id = "invoice",
    titleAr = "فاتورة تفصيلية للمريض",
    iconEmoji = "🧾",
    descriptionAr = "فاتورة معتمدة بتفاصيل التركيبة والأسنان والأسعار والمتبقي"
  ),
  CASE_READY_ALERT(
    id = "ready_alert",
    titleAr = "إشعار جاهزية التركيبة",
    iconEmoji = "🦷",
    descriptionAr = "إشعار بوصول التركيبة من المعمل وجاهزيتها لتحديد موعد التركيب"
  ),
  APPOINTMENT_REMINDER(
    id = "appointment",
    titleAr = "تذكير بموعد الجلسة",
    iconEmoji = "📅",
    descriptionAr = "تذكير بموعد جلسة القياس، التجربة، أو التركيب النهائي"
  ),
  PAYMENT_RECEIPT(
    id = "receipt",
    titleAr = "سند قبض مالي إلكتروني",
    iconEmoji = "💳",
    descriptionAr = "سند توثيق دفعة مسددة للمريض أو لمعمل الأسنان"
  ),
  POST_FITTING_CARE(
    id = "care_instructions",
    titleAr = "إرشادات وتعليمات العناية",
    iconEmoji = "✨",
    descriptionAr = "نصائح طبية معتمدة للعناية بالتركيبات والزراعة وصحة الفم"
  ),
  LAB_FOLLOWUP(
    id = "lab_followup",
    titleAr = "متابعة معمل الأسنان",
    iconEmoji = "🔬",
    descriptionAr = "رسالة متابعة واستعجال لتسليم الإرسالية السنية"
  ),
  CUSTOM(
    id = "custom",
    titleAr = "رسالة مخصصة سريعة",
    iconEmoji = "💬",
    descriptionAr = "كتابة أو تعديل رسالة مخصصة وإرسالها فوراً"
  )
}

object WhatsAppMessagingManager {

  // FIX: SimpleDateFormat is not thread-safe; these singletons were shared between
  // the main thread and IO coroutines. Formatters are now per-thread.
  private val dateFormat: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
  }
  private val dateTimeFormat: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar"))
  }

  fun formatDate(timestamp: Long): String = dateFormat.get()!!.format(java.util.Date(timestamp))
  fun formatDateTime(timestamp: Long): String = dateTimeFormat.get()!!.format(java.util.Date(timestamp))

  /**
   * Formats a local or international phone number for WhatsApp URL
   * Handles Yemeni 9-digit formats (77xxxxxxx, 71xxxxxxx, 73xxxxxxx, 70xxxxxxx) by prepending 967
   */
  fun formatPhoneNumberForWhatsApp(phone: String): String {
    val clean = phone.replace(Regex("[^0-9]"), "")
    return when {
      clean.isEmpty() -> ""
      clean.startsWith("967") -> clean
      clean.startsWith("00967") -> clean.substring(2)
      clean.startsWith("0") && clean.length == 10 && clean.startsWith("07") -> "967" + clean.substring(1)
      clean.length == 9 && (clean.startsWith("77") || clean.startsWith("71") || clean.startsWith("73") || clean.startsWith("70") || clean.startsWith("78")) -> "967$clean"
      clean.startsWith("966") || clean.startsWith("971") || clean.startsWith("20") || clean.startsWith("1") -> clean
      else -> clean
    }
  }

  /**
   * Builds the formatted WhatsApp message based on the template type
   */
  fun buildMessage(
    templateType: WhatsAppTemplateType,
    shipment: Shipment? = null,
    payment: Payment? = null,
    patientName: String = shipment?.patientName ?: "",
    customNotes: String = "",
    appointmentDateText: String = "",
    clinicPhone: String = ClinicInfo.PHONE_PRIMARY,
    currencySymbol: String = shipment?.currency ?: "SAR"
  ): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
      maximumFractionDigits = 2
      minimumFractionDigits = 0
    }

    return when (templateType) {
      WhatsAppTemplateType.PATIENT_INVOICE -> {
        val teethText = if (!shipment?.toothNumbers.isNullOrBlank()) "🦷 الأسنان المعالجة: *${shipment?.toothNumbers}*" else ""
        val shadeText = if (!shipment?.shade.isNullOrBlank()) "🎨 اللون المعتمد: *${shipment?.shade}*" else ""
        val piecesText = if (shipment != null && shipment.pieceCount > 1) "📦 عدد الوحدات: *${shipment.pieceCount} قطع*" else ""
        val total = shipment?.totalPrice ?: 0.0
        val discount = shipment?.discount ?: 0.0
        val net = (total - discount).coerceAtLeast(0.0)

        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        👨‍⚕️ *إشراف: ${ClinicInfo.DOCTOR_NAME}*
        ╚════════════════════════════════╝

        📋 *فاتورة معالجة وتركيبات سنية*
        ──────────────────────
        👤 *اسم المريض:* ${patientName.ifBlank { "المحترم" }}
        🏷️ *رقم الإرسالية / الفاتورة:* ${shipment?.shipmentNumber ?: "---"}
        📅 *التاريخ:* ${formatDate(shipment?.orderDate ?: System.currentTimeMillis())}
        🛠️ *نوع العمل:* *${shipment?.workTypeName ?: "تركيبة أسنان"}*
        $piecesText
        $teethText
        $shadeText
        ──────────────────────
        💰 *إجمالي التكلفة:* ${numberFormat.format(total)} $currencySymbol
        ${if (discount > 0) "🎁 *الخصم الممنوح:* ${numberFormat.format(discount)} $currencySymbol\n" else ""}💵 *المبلغ الصافي:* *${numberFormat.format(net)} $currencySymbol*
        ──────────────────────
        ${if (customNotes.isNotBlank()) "📝 *ملاحظات خاصة:* $customNotes\n──────────────────────\n" else ""}📍 *العنوان:* ${ClinicInfo.ADDRESS}
        📞 *للتواصل والاستفسار:* ${ClinicInfo.PHONES}
        💚 *نتمنى لكم دوام الصحة والابتسامة المشرقة* ✨
        """.trimIndent()
      }

      WhatsAppTemplateType.CASE_READY_ALERT -> {
        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        👨‍⚕️ *${ClinicInfo.DOCTOR_NAME}*
        ╚════════════════════════════════╝

        🎉 *إشعار جاهزية التركيبة السنية*
        ──────────────────────
        عزيزنا المريض: *${patientName.ifBlank { "المحترم" }}*
        السلام عليكم ورحمة الله وبركاته،

        يسرنا إبلاغكم بأن التركيبة السنية الخاصة بكم:
        🛠️ *(${shipment?.workTypeName ?: "تركيبة الأسنان"})*
        ${if (!shipment?.toothNumbers.isNullOrBlank()) "🦷 الأسنان: *${shipment?.toothNumbers}*\n" else ""}
        ✨ *قد وصلت من المختبر وأصبحت جاهزة تماماً للتركيب في العيادة.*

        📅 نرجو منكم التواصل معنا لتأكيد أو حجز موعد جلسة التركيب النهائي بما يناسب وقتكم.
        ──────────────────────
        ${if (customNotes.isNotBlank()) "📌 *تنبيه:* $customNotes\n──────────────────────\n" else ""}📞 *أرقام العيادة:* ${ClinicInfo.PHONES}
        📍 *الموقع:* ${ClinicInfo.ADDRESS}
        🌷 *أهلاً وسهلاً بكم دائماً*
        """.trimIndent()
      }

      WhatsAppTemplateType.APPOINTMENT_REMINDER -> {
        val apptTime = if (appointmentDateText.isNotBlank()) appointmentDateText else "الموعد القادم المحدد"
        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        👨‍⚕️ *${ClinicInfo.DOCTOR_NAME}*
        ╚════════════════════════════════╝

        ⏰ *تذكير بموعد العيادة*
        ──────────────────────
        الأخ / الأخت: *${patientName.ifBlank { "المحترم" }}*
        السلام عليكم ورحمة الله وبركاته،

        نود تذكيركم بموعد جلستكم القادمة لدى العيادة:
        🗓️ *الموعد:* *$apptTime*
        🏥 *الغرض من الجلسة:* ${shipment?.workTypeName?.let { "متابعة وتركيب $it" } ?: "جلسة علاج وتركيبات سنية"}
        ${if (!shipment?.toothNumbers.isNullOrBlank()) "🦷 الأسنان: *${shipment?.toothNumbers}*\n" else ""}
        ${if (customNotes.isNotBlank()) "📝 *ملاحظة:* $customNotes\n" else ""}──────────────────────
        ⚠️ *يرجى الحضور قبل الموعد بـ 10 دقائق.*
        📞 في حال رغبتكم في تأجيل أو تعديل الموعد يرجى إشعارنا مسبقاً على: ${ClinicInfo.PHONE_PRIMARY}
        📍 *العنوان:* ${ClinicInfo.ADDRESS}
        """.trimIndent()
      }

      WhatsAppTemplateType.PAYMENT_RECEIPT -> {
        val paidAmount = payment?.paidAmount ?: payment?.amount ?: 0.0
        val pCurrency = payment?.paidCurrency ?: payment?.currency ?: "SAR"
        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        👨‍⚕️ *${ClinicInfo.DOCTOR_NAME}*
        ╚════════════════════════════════╝

        🧾 *سند قبض مالي إلكتروني*
        ──────────────────────
        🏷️ *رقم السند:* ${payment?.receiptNumber?.ifBlank { "#REC-${System.currentTimeMillis() % 100000}" } ?: "#REC"}
        📅 *تاريخ الدفع:* ${formatDateTime(payment?.paymentDate ?: System.currentTimeMillis())}
        👤 *المستلم منه / الحساب:* *${patientName.ifBlank { payment?.labName ?: "المحترم" }}*
        💳 *طريقة الدفع:* ${payment?.paymentMethod?.titleAr ?: "نقداً"}
        ──────────────────────
        💵 *المبلغ المسدد:* *${numberFormat.format(paidAmount)} $pCurrency*
        ${if (payment != null && payment.currency != payment.paidCurrency) "💱 *العملة المقيدة:* ${numberFormat.format(payment.amount)} ${payment.currency}\n" else ""}──────────────────────
        ${if (!payment?.notes.isNullOrBlank()) "📝 *البيان:* ${payment?.notes}\n──────────────────────\n" else ""}${if (customNotes.isNotBlank()) "📌 *ملاحظة:* $customNotes\n──────────────────────\n" else ""}✍️ *المستلم:* ${payment?.recordedByName ?: ClinicInfo.DOCTOR_NAME}
        📞 *هاتف العيادة:* ${ClinicInfo.PHONES}
        ✨ *شاكرين حسن تعاملكم معنا* ✨
        """.trimIndent()
      }

      WhatsAppTemplateType.POST_FITTING_CARE -> {
        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        👨‍⚕️ *نصائح طبية من ${ClinicInfo.DOCTOR_NAME}*
        ╚════════════════════════════════╝

        🌟 *إرشادات العناية بالتركيبات وزراعة الأسنان*
        ──────────────────────
        عزيزنا المريض: *${patientName.ifBlank { "المحترم" }}*
        تهانينا على إتمام تركيبتك السنية الجديدة! لضمان استمراريتها وصحة فمكم:

        1️⃣ *العناية اليومية:* نظّف أسنانك بالفرشاة ومعجون الفلورايد مرتين يومياً، مع استخدام خيط الأسنان الطبي لتنظيف ما بين التركيبات.
        2️⃣ *الأيام الأولى:* تجنب مضغ الأطعمة الصلبة جداً أو اللزجة خلال أول 24 ساعة لثبات المادة اللاصقة.
        3️⃣ *حساسية خفيفة:* من الطبيعي الشعور بحساسية بسيطة تجاه المشروبات الباردة/الساخنة في الأيام الأولى وتزول تدريجياً.
        4️⃣ *الفحص الدوري:* احرص على زيارة العيادة كل 6 أشهر للفحص الدوري والتنظيف الوقائي.
        ──────────────────────
        ${if (customNotes.isNotBlank()) "💡 *نصيحة إضافية:* $customNotes\n──────────────────────\n" else ""}📞 لأي استفسار أو طارئ نحن دائماً بخدمتكم: ${ClinicInfo.PHONES}
        📍 *شارع التحرير الأعلى - جوار جامع الأزهر*
        💐 *دمتم بابتسامة مشرقة وصحة دائمة* ✨
        """.trimIndent()
      }

      WhatsAppTemplateType.LAB_FOLLOWUP -> {
        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        🔬 *طلب متابعة معمل الأسنان*
        ╚════════════════════════════════╝

        إلى الزملاء في: *${shipment?.labName ?: "المختبر السني"}*
        تحية طيبة وبعد،،

        نرجو منكم الإفادة حول حالة الإرسالية السنية التالية:
        🏷️ *رقم الإرسالية:* *${shipment?.shipmentNumber ?: "---"}*
        👤 *المريض:* ${patientName.ifBlank { "حالة سنية" }}
        🛠️ *العمل:* *${shipment?.workTypeName ?: "تركيبة"}* (${shipment?.pieceCount ?: 1} قطع)
        🦷 *الأسنان:* ${shipment?.toothNumbers?.ifBlank { "محددة بالطلب" } ?: "محددة بالطلب"}
        🎨 *اللون:* ${shipment?.shade ?: "A2"}
        ⏰ *تاريخ التسليم المتوقع:* ${formatDate(shipment?.expectedDeliveryDate ?: System.currentTimeMillis())}
        ${if (shipment?.isUrgent == true) "🚨 *درجة الاستعجال:* عاجل جداً (Urgent) ⚡\n" else ""}──────────────────────
        ${if (customNotes.isNotBlank()) "📝 *ملاحظة الطبيب:* $customNotes\n──────────────────────\n" else ""}يرجى إبلاغنا فور الانتهاء من العمل لتنسيق استلامه مع المندوب.
        📞 للتنسيق: ${ClinicInfo.PHONES}
        شاكرين لكم حسن التعاون والاهتمام بالجودة والدقة.
        """.trimIndent()
      }

      WhatsAppTemplateType.CUSTOM -> {
        """
        ╔════════════════════════════════╗
        🦷 *${ClinicInfo.CLINIC_NAME}*
        👨‍⚕️ *${ClinicInfo.DOCTOR_NAME}*
        ╚════════════════════════════════╝

        عزيزنا: *${patientName.ifBlank { "المحترم" }}*
        السلام عليكم ورحمة الله وبركاته،

        $customNotes

        ──────────────────────
        📞 *للتواصل والاستفسار:* ${ClinicInfo.PHONES}
        📍 *العنوان:* ${ClinicInfo.ADDRESS}
        """.trimIndent()
      }
    }
  }

  /**
   * Opens WhatsApp app directly or falls back to Web WhatsApp URL
   */
  fun sendViaWhatsApp(context: Context, rawPhoneNumber: String, messageText: String): Boolean {
    val formattedPhone = formatPhoneNumberForWhatsApp(rawPhoneNumber)
    return try {
      val url = if (formattedPhone.isNotEmpty()) {
        "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(messageText)}"
      } else {
        "https://api.whatsapp.com/send?text=${Uri.encode(messageText)}"
      }

      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    } catch (e: Exception) {
      // Fallback: general share intent
      try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, messageText)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الرسالة عبر:"))
        true
      } catch (e2: Exception) {
        Toast.makeText(context, "تعذر فتح تطبيق الواتساب أو المشاركة", Toast.LENGTH_SHORT).show()
        false
      }
    }
  }

  /**
   * Copies the formatted text to the device clipboard
   */
  fun copyToClipboard(context: Context, text: String, toastMessage: String = "تم نسخ نص الرسالة بنجاح 📋") {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText("WhatsApp Message", text)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Toast.makeText(context, "فشل نسخ النص", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Shares the message via the Android system share sheet
   */
  fun shareGeneral(context: Context, messageText: String, title: String = "مشاركة الفاتورة / الإشعار") {
    try {
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, messageText)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(Intent.createChooser(intent, title))
    } catch (e: Exception) {
      Toast.makeText(context, "تعذر فتح قائمة المشاركة", Toast.LENGTH_SHORT).show()
    }
  }
}
