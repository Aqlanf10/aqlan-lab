package com.aqlanlab.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aqlanlab.app.data.models.Payment
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.screens.LabDaySummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object DailyPdfGenerator {

  // Page dimensions (Standard A4 in PostScript points: 595 x 842 pt)
  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val MARGIN = 36f

  fun generateDailySummaryPdf(
    context: Context,
    dateTitle: String,
    dateMillis: Long,
    completedShipments: List<Shipment>,
    allShipments: List<Shipment>,
    payments: List<Payment>,
    labBreakdown: List<LabDaySummary>,
    totalCompletedPieces: Int,
    totalCompletedBilled: Double,
    totalPayments: Double,
    netBalance: Double,
    currency: String,
    generatedByName: String,
    userRole: UserRole
  ): File? {
    val pdfDocument = PdfDocument()

    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    // Paints
    val primaryPaint = Paint().apply {
      color = Color.rgb(14, 116, 144) // Dental Blue #0E7490
      isAntiAlias = true
    }

    val primaryDarkPaint = Paint().apply {
      color = Color.rgb(8, 47, 73) // #082F49
      isAntiAlias = true
    }

    val textDarkPaint = Paint().apply {
      color = Color.rgb(30, 41, 59) // Slate 800 #1E293B
      textSize = 10f
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val textHeaderPaint = Paint().apply {
      color = Color.WHITE
      textSize = 14f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val textSubHeaderPaint = Paint().apply {
      color = Color.rgb(224, 242, 254) // Sky 100
      textSize = 9.5f
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val textSectionTitle = Paint().apply {
      color = Color.rgb(14, 116, 144)
      textSize = 11.5f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val textTableHead = Paint().apply {
      color = Color.rgb(15, 23, 42)
      textSize = 9f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val textTableCell = Paint().apply {
      color = Color.rgb(51, 65, 85)
      textSize = 8.5f
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val textTableCellBold = Paint().apply {
      color = Color.rgb(15, 23, 42)
      textSize = 8.5f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val cardBgPaint = Paint().apply {
      color = Color.rgb(241, 245, 249) // Slate 100
      isAntiAlias = true
    }

    val linePaint = Paint().apply {
      color = Color.rgb(203, 213, 225) // Slate 300
      strokeWidth = 0.75f
      isAntiAlias = true
    }

    val greenPaint = Paint().apply {
      color = Color.rgb(22, 101, 52) // Green #166534
      textSize = 9f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val redPaint = Paint().apply {
      color = Color.rgb(185, 28, 28) // Red #B91C1C
      textSize = 9f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    var currentY = 0f

    // 1. Header Banner
    val headerHeight = 78f
    val headerRect = RectF(0f, 0f, PAGE_WIDTH.toFloat(), headerHeight)
    canvas.drawRect(headerRect, primaryPaint)

    // Clinic / App Title (RTL: drawn from right margin)
    canvas.drawText("مركز الدكتور عقلان الكامل لتقويم وزراعة وتجميل الأسنان", PAGE_WIDTH - MARGIN, 24f, textHeaderPaint)
    canvas.drawText("العنوان: شارع التحرير الأعلى - جوار جامع الأزهر | هاتف: 770245745 - 711752823 - 04253028", PAGE_WIDTH - MARGIN, 40f, textSubHeaderPaint)
    canvas.drawText("التقرير اليومي لحركات المعامل السنية • التاريخ: $dateTitle", PAGE_WIDTH - MARGIN, 56f, textSubHeaderPaint)

    // Left Header info (e.g. Generated time and user)
    val leftHeaderPaint = Paint().apply {
      color = Color.WHITE
      textSize = 8.5f
      isAntiAlias = true
      textAlign = Paint.Align.LEFT
    }
    val timeNow = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("وقت الإصدار: $timeNow", MARGIN, 24f, leftHeaderPaint)
    canvas.drawText("المستخدم: $generatedByName", MARGIN, 40f, leftHeaderPaint)
    canvas.drawText("العملة: $currency", MARGIN, 56f, leftHeaderPaint)

    currentY = headerHeight + 12f

    // 2. Summary KPI Metric Boxes (4 Boxes)
    val boxWidth = (PAGE_WIDTH - (MARGIN * 2) - (8f * 3)) / 4f
    val boxHeight = 44f

    val kpis = listOf(
      Triple("القطع المنجزة", "$totalCompletedPieces قطعة", "${completedShipments.size} إرسالية"),
      Triple("تكلفة المنجز", if (userRole != UserRole.STAFF) "$totalCompletedBilled $currency" else "••••", "المطالبات"),
      Triple("المدفوعات المسددة", if (userRole != UserRole.STAFF) "$totalPayments $currency" else "••••", "${payments.size} دفعة"),
      Triple("صافي حركة اليوم", if (userRole != UserRole.STAFF) "$netBalance $currency" else "••••", if (netBalance >= 0) "مستحق" else "فائض")
    )

    for (i in kpis.indices) {
      val boxLeft = MARGIN + (i * (boxWidth + 8f))
      val boxRect = RectF(boxLeft, currentY, boxLeft + boxWidth, currentY + boxHeight)

      val boxPaint = Paint().apply {
        color = when (i) {
          0 -> Color.rgb(240, 253, 244) // Light green
          1 -> Color.rgb(240, 249, 255) // Light blue
          2 -> Color.rgb(254, 242, 242) // Light red
          else -> Color.rgb(248, 250, 252) // Light gray
        }
        isAntiAlias = true
      }
      canvas.drawRoundRect(boxRect, 6f, 6f, boxPaint)

      val strokePaint = Paint().apply {
        color = when (i) {
          0 -> Color.rgb(187, 247, 208)
          1 -> Color.rgb(186, 230, 253)
          2 -> Color.rgb(254, 202, 202)
          else -> Color.rgb(226, 232, 240)
        }
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
      }
      canvas.drawRoundRect(boxRect, 6f, 6f, strokePaint)

      val (title, valStr, subStr) = kpis[i]

      val kpiTitlePaint = Paint().apply {
        color = Color.rgb(71, 85, 105)
        textSize = 7.5f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }
      val kpiValPaint = Paint().apply {
        color = when (i) {
          0 -> Color.rgb(22, 101, 52)
          1 -> Color.rgb(14, 116, 144)
          2 -> Color.rgb(185, 28, 28)
          else -> Color.rgb(15, 23, 42)
        }
        textSize = 9.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }
      val kpiSubPaint = Paint().apply {
        color = Color.rgb(100, 116, 139)
        textSize = 7f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }

      val centerX = boxLeft + (boxWidth / 2f)
      canvas.drawText(title, centerX, currentY + 12f, kpiTitlePaint)
      canvas.drawText(valStr, centerX, currentY + 26f, kpiValPaint)
      canvas.drawText(subStr, centerX, currentY + 38f, kpiSubPaint)
    }

    currentY += boxHeight + 14f

    // 3. Section: Completed Shipments Table
    canvas.drawText("1. الإرساليات المكتملة والمنجزة لليوم (${completedShipments.size})", PAGE_WIDTH - MARGIN, currentY + 10f, textSectionTitle)
    currentY += 16f

    // Table Header
    val tableWidth = PAGE_WIDTH - (MARGIN * 2)
    val headRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 18f)
    val headBgPaint = Paint().apply { color = Color.rgb(226, 232, 240); isAntiAlias = true }
    canvas.drawRect(headRect, headBgPaint)

    val rightEdge = PAGE_WIDTH - MARGIN
    canvas.drawText("رقم الإرسالية", rightEdge - 5f, currentY + 12f, textTableHead)
    canvas.drawText("المريض والطبيب", rightEdge - 75f, currentY + 12f, textTableHead)
    canvas.drawText("المعمل", rightEdge - 200f, currentY + 12f, textTableHead)
    canvas.drawText("نوع العمل واللون", rightEdge - 300f, currentY + 12f, textTableHead)
    canvas.drawText("القطع", rightEdge - 420f, currentY + 12f, textTableHead)
    canvas.drawText("التكلفة", rightEdge - 470f, currentY + 12f, textTableHead)

    currentY += 18f

    if (completedShipments.isEmpty()) {
      canvas.drawText("لا توجد إرساليات مكتملة مسجلة في هذا اليوم", rightEdge - 200f, currentY + 16f, textTableCell)
      currentY += 24f
    } else {
      val maxRows = 10.coerceAtMost(completedShipments.size)
      for (idx in 0 until maxRows) {
        val s = completedShipments[idx]
        val rowBg = if (idx % 2 == 1) Color.rgb(248, 250, 252) else Color.WHITE
        canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 18f, Paint().apply { color = rowBg })

        canvas.drawText(s.shipmentNumber, rightEdge - 5f, currentY + 12f, textTableCellBold)
        val patDoc = "${truncateText(s.patientName.ifEmpty { "مريض" }, 16)} (${truncateText(s.clinicOrDoctorName.ifEmpty { "طبيب" }, 12)})"
        canvas.drawText(patDoc, rightEdge - 75f, currentY + 12f, textTableCell)
        canvas.drawText(truncateText(s.labName, 18), rightEdge - 200f, currentY + 12f, textTableCell)
        val workShade = "${truncateText(s.workTypeName, 18)} [${s.shade}]"
        canvas.drawText(workShade, rightEdge - 300f, currentY + 12f, textTableCell)
        canvas.drawText("${s.pieceCount}", rightEdge - 420f, currentY + 12f, textTableCellBold)
        val costStr = if (userRole != UserRole.STAFF) "${s.totalPrice} $currency" else "••••"
        canvas.drawText(costStr, rightEdge - 470f, currentY + 12f, textTableCellBold)

        canvas.drawLine(MARGIN, currentY + 18f, PAGE_WIDTH - MARGIN, currentY + 18f, linePaint)
        currentY += 18f
      }

      if (completedShipments.size > 10) {
        val remaining = completedShipments.size - 10
        canvas.drawText("... وهناك $remaining إرساليات أخرى مكتملة اليوم", rightEdge - 200f, currentY + 12f, textTableCell)
        currentY += 16f
      }
    }

    currentY += 10f

    // 4. Section: Financial Payments Table
    canvas.drawText("2. الحركات المالية وسندات الدفع لليوم (${payments.size})", PAGE_WIDTH - MARGIN, currentY + 10f, textSectionTitle)
    currentY += 16f

    val payHeadRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 18f)
    canvas.drawRect(payHeadRect, headBgPaint)

    canvas.drawText("المعمل المستلم", rightEdge - 5f, currentY + 12f, textTableHead)
    canvas.drawText("المبلغ المسدد", rightEdge - 150f, currentY + 12f, textTableHead)
    canvas.drawText("طريقة السداد", rightEdge - 270f, currentY + 12f, textTableHead)
    canvas.drawText("رقم السند", rightEdge - 370f, currentY + 12f, textTableHead)
    canvas.drawText("البيان والملاحظات", rightEdge - 440f, currentY + 12f, textTableHead)

    currentY += 18f

    if (payments.isEmpty()) {
      canvas.drawText("لا توجد سندات دفع أو سداد مسجلة في هذا اليوم", rightEdge - 200f, currentY + 16f, textTableCell)
      currentY += 24f
    } else {
      val maxPayRows = 6.coerceAtMost(payments.size)
      for (pIdx in 0 until maxPayRows) {
        val p = payments[pIdx]
        val rowBg = if (pIdx % 2 == 1) Color.rgb(248, 250, 252) else Color.WHITE
        canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 18f, Paint().apply { color = rowBg })

        canvas.drawText(truncateText(p.labName, 22), rightEdge - 5f, currentY + 12f, textTableCellBold)
        canvas.drawText("${p.amount} $currency", rightEdge - 150f, currentY + 12f, greenPaint)
        canvas.drawText(p.paymentMethod.titleAr, rightEdge - 270f, currentY + 12f, textTableCell)
        canvas.drawText(p.receiptNumber.ifEmpty { "-" }, rightEdge - 370f, currentY + 12f, textTableCell)
        canvas.drawText(truncateText(p.notes.ifEmpty { "سداد حساب" }, 18), rightEdge - 440f, currentY + 12f, textTableCell)

        canvas.drawLine(MARGIN, currentY + 18f, PAGE_WIDTH - MARGIN, currentY + 18f, linePaint)
        currentY += 18f
      }
    }

    currentY += 10f

    // 5. Section: Laboratory Breakdown Summary
    if (labBreakdown.isNotEmpty()) {
      canvas.drawText("3. ملخص إنتاجية ومستحقات المعامل لليوم", PAGE_WIDTH - MARGIN, currentY + 10f, textSectionTitle)
      currentY += 16f

      val labHeadRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 18f)
      canvas.drawRect(labHeadRect, headBgPaint)

      canvas.drawText("اسم المعمل", rightEdge - 5f, currentY + 12f, textTableHead)
      canvas.drawText("الإرساليات", rightEdge - 160f, currentY + 12f, textTableHead)
      canvas.drawText("عدد القطع", rightEdge - 240f, currentY + 12f, textTableHead)
      canvas.drawText("قيمة الأعمال (مدين)", rightEdge - 330f, currentY + 12f, textTableHead)
      canvas.drawText("المسدد (دائن)", rightEdge - 440f, currentY + 12f, textTableHead)

      currentY += 18f

      for (lIdx in labBreakdown.indices) {
        val lab = labBreakdown[lIdx]
        val rowBg = if (lIdx % 2 == 1) Color.rgb(248, 250, 252) else Color.WHITE
        canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 18f, Paint().apply { color = rowBg })

        canvas.drawText(lab.labName, rightEdge - 5f, currentY + 12f, textTableCellBold)
        canvas.drawText("${lab.completedShipmentsCount}", rightEdge - 160f, currentY + 12f, textTableCell)
        canvas.drawText("${lab.totalPieces} قطعة", rightEdge - 240f, currentY + 12f, textTableCellBold)
        val billedStr = if (userRole != UserRole.STAFF) "${lab.totalBilled} $currency" else "••••"
        canvas.drawText(billedStr, rightEdge - 330f, currentY + 12f, textTableCellBold)
        val paidStr = if (userRole != UserRole.STAFF) "${lab.totalPaid} $currency" else "••••"
        canvas.drawText(paidStr, rightEdge - 440f, currentY + 12f, greenPaint)

        canvas.drawLine(MARGIN, currentY + 18f, PAGE_WIDTH - MARGIN, currentY + 18f, linePaint)
        currentY += 18f
      }
    }

    // 6. Footer (Signatures & Page Info)
    val footerY = PAGE_HEIGHT - 45f
    canvas.drawLine(MARGIN, footerY, PAGE_WIDTH - MARGIN, footerY, linePaint)

    val footerTextPaint = Paint().apply {
      color = Color.rgb(100, 116, 139)
      textSize = 8f
      isAntiAlias = true
    }

    // Right: Signature
    canvas.drawText("توقيع المحاسب المسؤول: ............................", PAGE_WIDTH - MARGIN, footerY + 18f, Paint(footerTextPaint).apply { textAlign = Paint.Align.RIGHT })
    // Center: Clinic stamp note
    canvas.drawText("ختم واعتماد مركز د. عقلان الكامل", PAGE_WIDTH / 2f, footerY + 18f, Paint(footerTextPaint).apply { textAlign = Paint.Align.CENTER })
    // Left: Generated info
    canvas.drawText("ت: 770245745 - 711752823 • شارع التحرير الأعلى", MARGIN, footerY + 18f, Paint(footerTextPaint).apply { textAlign = Paint.Align.LEFT })

    pdfDocument.finishPage(page)

    // Save to Cache file
    return try {
      val reportsDir = File(context.cacheDir, "reports")
      if (!reportsDir.exists()) {
        reportsDir.mkdirs()
      }
      val dateFileStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMillis))
      val file = File(reportsDir, "Daily_Report_$dateFileStr.pdf")
      val fos = FileOutputStream(file)
      pdfDocument.writeTo(fos)
      fos.flush()
      fos.close()
      pdfDocument.close()
      file
    } catch (e: Exception) {
      e.printStackTrace()
      pdfDocument.close()
      null
    }
  }

  fun sharePdf(context: Context, pdfFile: File) {
    try {
      val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
      )
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "التقرير اليومي الشامل")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(intent, "مشاركة التقرير اليومي PDF"))
    } catch (e: Exception) {
      Toast.makeText(context, "تعذر مشاركة ملف الـ PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun openOrPrintPdf(context: Context, pdfFile: File) {
    try {
      val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
      )
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(Intent.createChooser(intent, "فتح أو طباعة التقرير PDF"))
    } catch (e: Exception) {
      // Fallback to share
      sharePdf(context, pdfFile)
    }
  }

  private fun truncateText(text: String, maxLen: Int): String {
    return if (text.length > maxLen) text.take(maxLen - 2) + ".." else text
  }
}
