package com.aqlanlab.app.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.ClinicInfo
import com.aqlanlab.app.ui.components.DateUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

  private const val PAGE_WIDTH = 595 // Standard A4 width in points (72 DPI)
  private const val PAGE_HEIGHT = 842 // Standard A4 height in points (72 DPI)

  /**
   * Generates a professionally branded PDF file for a dental shipment
   */
  fun generateShipmentPdf(
    context: Context,
    shipment: Shipment,
    userRole: UserRole = UserRole.ADMIN,
    currency: String = "USD"
  ): File? {
    return try {
      val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
      val cleanShipmentNumber = shipment.shipmentNumber.replace("#", "").replace("/", "-")
      val pdfFile = File(reportsDir, "Shipment_${cleanShipmentNumber}.pdf")

      val document = PdfDocument()
      val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
      val page = document.startPage(pageInfo)
      val canvas = page.canvas

      // Draw background and borders
      drawPageBackground(canvas)

      // Draw Header with Clinic Branding
      drawClinicHeader(canvas)

      // Draw Title & QR Code
      drawTitleAndQrCode(canvas, shipment)

      // Draw Patient & Doctor Details Table
      var currentY = 220f
      currentY = drawPatientDetailsSection(canvas, shipment, currentY)

      // Draw Dental Work Specifications Section
      currentY = drawWorkSpecsSection(canvas, shipment, currentY)

      // Draw Special Clinical Notes Section
      currentY = drawClinicalNotesSection(canvas, shipment, currentY)

      // Draw Financial Summary (if authorized)
      if (userRole != UserRole.STAFF) {
        currentY = drawFinancialSection(canvas, shipment, currentY, currency)
      }

      // Draw Official Signatures Section
      drawSignaturesSection(canvas, currentY)

      // Draw Footer
      drawFooter(canvas)

      document.finishPage(page)

      FileOutputStream(pdfFile).use { out ->
        document.writeTo(out)
      }
      document.close()

      pdfFile
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  /**
   * Generates a comprehensive periodic / daily summary report in PDF format
   */
  fun generatePeriodicSummaryPdf(
    context: Context,
    title: String,
    periodName: String,
    shipments: List<Shipment>,
    totalCost: Double,
    currency: String,
    userRole: UserRole
  ): File? {
    return try {
      val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
      val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val pdfFile = File(reportsDir, "Report_${periodName}_${timeStamp}.pdf")

      val document = PdfDocument()
      val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
      val page = document.startPage(pageInfo)
      val canvas = page.canvas

      drawPageBackground(canvas)
      drawClinicHeader(canvas)

      // Report Header Box
      val titleBgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
      canvas.drawRoundRect(RectF(30f, 120f, PAGE_WIDTH - 30f, 175f), 8f, 8f, titleBgPaint)

      val borderPaint = Paint().apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 1f
      }
      canvas.drawRoundRect(RectF(30f, 120f, PAGE_WIDTH - 30f, 175f), 8f, 8f, borderPaint)

      val titlePaint = TextPaint().apply {
        color = Color.parseColor("#0F2B48")
        textSize = 13f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
      }
      canvas.drawText(title, PAGE_WIDTH - 45f, 142f, titlePaint)

      val periodPaint = TextPaint().apply {
        color = Color.parseColor("#475569")
        textSize = 9f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
      }
      val dateFormatted = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date())
      canvas.drawText("الفترة الزمنية: $periodName  •  تاريخ التقرير: $dateFormatted", PAGE_WIDTH - 45f, 160f, periodPaint)

      // Summary Stats Cards
      val statBoxRect = RectF(30f, 185f, PAGE_WIDTH - 30f, 230f)
      drawDataBox(canvas, statBoxRect, bgColor = "#F1F5F9")

      val totalPieces = shipments.sumOf { it.pieceCount }
      val totalShipments = shipments.size
      val colWidth = (PAGE_WIDTH - 60f) / 3f

      drawTableCell(canvas, "إجمالي الإرساليات:", "$totalShipments إرسالية", 30f, 195f, colWidth, isBold = true)
      drawTableCell(canvas, "إجمالي القطع:", "$totalPieces قطعة سنية", 30f + colWidth, 195f, colWidth, isBold = true)
      if (userRole != UserRole.STAFF) {
        drawTableCell(
          canvas,
          "إجمالي التكلفة:",
          "$totalCost $currency",
          30f + (2 * colWidth),
          195f,
          colWidth,
          isBold = true,
          valueColor = Color.parseColor("#059669")
        )
      }

      // Shipments Table List
      drawSectionHeader(canvas, "تفاصيل الإرساليات (${shipments.size} إرسالية)", 240f)

      var tableY = 265f
      val tableHeaderPaint = Paint().apply { color = Color.parseColor("#1E3A8A") }
      canvas.drawRoundRect(RectF(30f, tableY, PAGE_WIDTH - 30f, tableY + 20f), 4f, 4f, tableHeaderPaint)

      val thTextPaint = TextPaint().apply {
        color = Color.WHITE
        textSize = 8.5f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
      }
      canvas.drawText("رقم الإرسالية", PAGE_WIDTH - 38f, tableY + 14f, thTextPaint)
      canvas.drawText("المريض والطبيب", PAGE_WIDTH - 120f, tableY + 14f, thTextPaint)
      canvas.drawText("نوع العمل / المعمل", PAGE_WIDTH - 260f, tableY + 14f, thTextPaint)
      canvas.drawText("القطع", PAGE_WIDTH - 390f, tableY + 14f, thTextPaint)
      canvas.drawText("الحالة", PAGE_WIDTH - 440f, tableY + 14f, thTextPaint)
      if (userRole != UserRole.STAFF) {
        canvas.drawText("المبلغ", 65f, tableY + 14f, thTextPaint)
      }

      tableY += 22f

      val rowPaint = TextPaint().apply {
        color = Color.parseColor("#1E293B")
        textSize = 8f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
      }

      val maxRows = 16
      val displayedShipments = shipments.take(maxRows)

      displayedShipments.forEachIndexed { index, ship ->
        val rowBg = if (index % 2 == 0) "#FFFFFF" else "#F8FAFC"
        canvas.drawRect(RectF(30f, tableY, PAGE_WIDTH - 30f, tableY + 18f), Paint().apply { color = Color.parseColor(rowBg) })

        canvas.drawText(ship.shipmentNumber, PAGE_WIDTH - 38f, tableY + 13f, rowPaint)
        val patientDoctor = "${ship.patientName} (${ship.clinicOrDoctorName.ifEmpty { "العيادة" }})"
        canvas.drawText(patientDoctor.take(22), PAGE_WIDTH - 120f, tableY + 13f, rowPaint)
        val workLab = "${ship.workTypeName} - ${ship.labName}"
        canvas.drawText(workLab.take(24), PAGE_WIDTH - 260f, tableY + 13f, rowPaint)
        canvas.drawText("${ship.pieceCount}", PAGE_WIDTH - 390f, tableY + 13f, rowPaint)
        canvas.drawText(ship.status.titleAr, PAGE_WIDTH - 440f, tableY + 13f, rowPaint)

        if (userRole != UserRole.STAFF) {
          canvas.drawText("${ship.totalPrice} $currency", 65f, tableY + 13f, rowPaint)
        }

        tableY += 18f
      }

      if (shipments.size > maxRows) {
        val extraText = "... ويوجد عدد ${shipments.size - maxRows} إرسالية إضافية في النظام"
        canvas.drawText(extraText, PAGE_WIDTH / 2f, tableY + 14f, TextPaint().apply {
          color = Color.GRAY
          textSize = 7.5f
          textAlign = Paint.Align.CENTER
          isAntiAlias = true
        })
      }

      drawSignaturesSection(canvas, (PAGE_HEIGHT - 135f))
      drawFooter(canvas)

      document.finishPage(page)

      FileOutputStream(pdfFile).use { out ->
        document.writeTo(out)
      }
      document.close()

      pdfFile
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  /**
   * Background frame and subtle gradient accents
   */
  private fun drawPageBackground(canvas: Canvas) {
    // Fill white
    val bgPaint = Paint().apply { color = Color.WHITE }
    canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

    // Outer frame border
    val borderPaint = Paint().apply {
      color = Color.parseColor("#E2E8F0")
      style = Paint.Style.STROKE
      strokeWidth = 1.5f
    }
    canvas.drawRoundRect(16f, 16f, PAGE_WIDTH - 16f, PAGE_HEIGHT - 16f, 8f, 8f, borderPaint)

    // Inner subtle border
    val innerBorderPaint = Paint().apply {
      color = Color.parseColor("#0F2B48")
      style = Paint.Style.STROKE
      strokeWidth = 0.5f
    }
    canvas.drawRoundRect(20f, 20f, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f, 6f, 6f, innerBorderPaint)
  }

  /**
   * Draws the official clinic letterhead banner
   */
  private fun drawClinicHeader(canvas: Canvas) {
    // Header background banner
    val headerPaint = Paint().apply {
      shader = LinearGradient(
        0f, 20f, PAGE_WIDTH.toFloat(), 105f,
        Color.parseColor("#0F2B48"),
        Color.parseColor("#1E3A8A"),
        Shader.TileMode.CLAMP
      )
    }
    val headerRect = RectF(20f, 20f, PAGE_WIDTH - 20f, 110f)
    canvas.drawRoundRect(headerRect, 6f, 6f, headerPaint)

    // Gold accent bottom line
    val accentPaint = Paint().apply {
      color = Color.parseColor("#F59E0B")
      strokeWidth = 3f
    }
    canvas.drawLine(20f, 110f, PAGE_WIDTH - 20f, 110f, accentPaint)

    // Arabic Clinic Name (Centered / Right-aligned)
    val textPaint = TextPaint().apply {
      color = Color.WHITE
      textSize = 15f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(ClinicInfo.CLINIC_NAME, PAGE_WIDTH / 2f, 48f, textPaint)

    // Doctor & Specialties
    val subTextPaint = TextPaint().apply {
      color = Color.parseColor("#93C5FD")
      textSize = 10f
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(
      "إشراف: ${ClinicInfo.DOCTOR_NAME}  •  ${ClinicInfo.SPECIALTIES}",
      PAGE_WIDTH / 2f,
      68f,
      subTextPaint
    )

    // Address & Phone numbers
    val contactTextPaint = TextPaint().apply {
      color = Color.parseColor("#E2E8F0")
      textSize = 8.5f
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(
      "📍 ${ClinicInfo.ADDRESS}  |  📞 ${ClinicInfo.PHONES}",
      PAGE_WIDTH / 2f,
      88f,
      contactTextPaint
    )
  }

  /**
   * Draws document title, barcode number, and QR code
   */
  private fun drawTitleAndQrCode(canvas: Canvas, shipment: Shipment) {
    val titleBgPaint = Paint().apply {
      color = Color.parseColor("#F1F5F9")
    }
    canvas.drawRoundRect(RectF(30f, 120f, PAGE_WIDTH - 30f, 205f), 8f, 8f, titleBgPaint)

    val borderPaint = Paint().apply {
      color = Color.parseColor("#CBD5E1")
      style = Paint.Style.STROKE
      strokeWidth = 1f
    }
    canvas.drawRoundRect(RectF(30f, 120f, PAGE_WIDTH - 30f, 205f), 8f, 8f, borderPaint)

    // Draw QR Code on the left side
    val qrBitmap = QrCodeGenerator.generateQrBitmap(shipment.shipmentNumber, sizePx = 240)
    if (qrBitmap != null) {
      val qrDest = Rect(42, 126, 114, 198)
      canvas.drawBitmap(qrBitmap, null, qrDest, null)

      // QR label under
      val qrLabelPaint = Paint().apply {
        color = Color.parseColor("#475569")
        textSize = 6.5f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
      canvas.drawText("مسح للتحقق والطلب", 78f, 202f, qrLabelPaint)
    }

    // Title & Shipment Number Info on the right
    val titlePaint = TextPaint().apply {
      color = Color.parseColor("#0F2B48")
      textSize = 14f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("سند واستمارة إرسالية معمل أسنان رسمية", PAGE_WIDTH - 45f, 142f, titlePaint)

    val numLabelPaint = TextPaint().apply {
      color = Color.parseColor("#1E40AF")
      textSize = 11f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("رقم الإرسالية: ${shipment.shipmentNumber}", PAGE_WIDTH - 45f, 160f, numLabelPaint)

    val datePaint = TextPaint().apply {
      color = Color.parseColor("#64748B")
      textSize = 8.5f
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(
      "تاريخ الإنشاء: ${DateUtils.formatDateTime(shipment.orderDate)}  |  الحالة: [ ${shipment.status.titleAr} ]",
      PAGE_WIDTH - 45f,
      176f,
      datePaint
    )

    if (shipment.isUrgent) {
      val urgentBadgePaint = Paint().apply {
        color = Color.parseColor("#FEF2F2")
      }
      val urgentBorderPaint = Paint().apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.STROKE
        strokeWidth = 1f
      }
      val urgentTextPaint = TextPaint().apply {
        color = Color.parseColor("#DC2626")
        textSize = 8f
        isFakeBoldText = true
        isAntiAlias = true
      }
      canvas.drawRoundRect(RectF(PAGE_WIDTH - 150f, 184f, PAGE_WIDTH - 45f, 198f), 4f, 4f, urgentBadgePaint)
      canvas.drawRoundRect(RectF(PAGE_WIDTH - 150f, 184f, PAGE_WIDTH - 45f, 198f), 4f, 4f, urgentBorderPaint)
      canvas.drawText("⚡ حالة عاجلة جداً (URGENT)", PAGE_WIDTH - 142f, 194f, urgentTextPaint)
    }
  }

  /**
   * Section 1: Patient and Clinical Details
   */
  private fun drawPatientDetailsSection(canvas: Canvas, shipment: Shipment, startY: Float): Float {
    var y = startY
    drawSectionHeader(canvas, "1. بيانات المريض والعيادة المشرفة", y)
    y += 20f

    val rowHeight = 22f
    val boxRect = RectF(30f, y, PAGE_WIDTH - 30f, y + (rowHeight * 2) + 10f)
    drawDataBox(canvas, boxRect)

    // Row 1: Patient Name & Doctor Name
    drawTableCell(canvas, "اسم المريض:", shipment.patientName, 30f, y + 5f, (PAGE_WIDTH - 60f) / 2f, isBold = true)
    drawTableCell(
      canvas,
      "الطبيب المشرف:",
      shipment.clinicOrDoctorName.ifEmpty { ClinicInfo.DOCTOR_NAME },
      30f + (PAGE_WIDTH - 60f) / 2f,
      y + 5f,
      (PAGE_WIDTH - 60f) / 2f
    )

    y += rowHeight

    // Row 2: Expected Delivery Date & Created By
    drawTableCell(
      canvas,
      "موعد التسليم المطلوب:",
      DateUtils.formatShortDate(shipment.expectedDeliveryDate),
      30f,
      y + 5f,
      (PAGE_WIDTH - 60f) / 2f,
      isBold = true,
      valueColor = Color.parseColor("#B91C1C")
    )
    drawTableCell(
      canvas,
      "مسؤول الإرسال بالعيادة:",
      shipment.createdByName,
      30f + (PAGE_WIDTH - 60f) / 2f,
      y + 5f,
      (PAGE_WIDTH - 60f) / 2f
    )

    return y + rowHeight + 15f
  }

  /**
   * Section 2: Dental Work Specifications
   */
  private fun drawWorkSpecsSection(canvas: Canvas, shipment: Shipment, startY: Float): Float {
    var y = startY
    drawSectionHeader(canvas, "2. المواصفات الفنية للعمل المعملي (Laboratory Specifications)", y)
    y += 20f

    val rowHeight = 22f
    val boxRect = RectF(30f, y, PAGE_WIDTH - 30f, y + (rowHeight * 3) + 12f)
    drawDataBox(canvas, boxRect)

    // Row 1: Lab Name & Work Type
    drawTableCell(canvas, "المعمل المنفذ:", shipment.labName, 30f, y + 5f, (PAGE_WIDTH - 60f) / 2f, isBold = true)
    drawTableCell(canvas, "نوع العمل / التركيبة:", shipment.workTypeName, 30f + (PAGE_WIDTH - 60f) / 2f, y + 5f, (PAGE_WIDTH - 60f) / 2f, isBold = true)

    y += rowHeight

    // Row 2: Piece Count & Teeth Numbers
    drawTableCell(canvas, "عدد القطع / الأسنان:", "${shipment.pieceCount} قطع", 30f, y + 5f, (PAGE_WIDTH - 60f) / 2f)
    val teethText = if (shipment.toothNumbers.isNotBlank()) shipment.toothNumbers else "حسب الطبعة والنموذج"
    drawTableCell(canvas, "أرقام الأسنان:", teethText, 30f + (PAGE_WIDTH - 60f) / 2f, y + 5f, (PAGE_WIDTH - 60f) / 2f, isBold = true)

    y += rowHeight

    // Row 3: Shade & Shade Notes
    drawTableCell(canvas, "درجة اللون (Shade):", shipment.shade, 30f, y + 5f, (PAGE_WIDTH - 60f) / 2f, isBold = true, valueColor = Color.parseColor("#1E3A8A"))
    val shadeNoteText = if (shipment.shadeNotes.isNotBlank()) shipment.shadeNotes else "مطابق للمواصفة القياسية"
    drawTableCell(canvas, "ملاحظات اللون والشفافية:", shadeNoteText, 30f + (PAGE_WIDTH - 60f) / 2f, y + 5f, (PAGE_WIDTH - 60f) / 2f)

    return y + rowHeight + 15f
  }

  /**
   * Section 3: Clinical Notes
   */
  private fun drawClinicalNotesSection(canvas: Canvas, shipment: Shipment, startY: Float): Float {
    var y = startY
    drawSectionHeader(canvas, "3. التعليمات والملاحظات السريرية لفني المختبر", y)
    y += 20f

    val noteContent = if (shipment.notes.isNotBlank()) shipment.notes else "يرجى مراعاة دقة الحواف الإطباقية ونقاط التماس الجانبية حسب طبعة العيادة المرفقة."
    val boxHeight = 44f
    val boxRect = RectF(30f, y, PAGE_WIDTH - 30f, y + boxHeight)
    drawDataBox(canvas, boxRect)

    val notePaint = TextPaint().apply {
      color = Color.parseColor("#1E293B")
      textSize = 8.5f
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("📝 $noteContent", PAGE_WIDTH - 42f, y + 24f, notePaint)

    return y + boxHeight + 15f
  }

  /**
   * Section 4: Financial Overview (for authorized roles)
   */
  private fun drawFinancialSection(canvas: Canvas, shipment: Shipment, startY: Float, currency: String): Float {
    var y = startY
    drawSectionHeader(canvas, "4. البيان المالي للإرسالية (خاص بالإدارة والمحاسبة)", y)
    y += 20f

    val rowHeight = 22f
    val boxRect = RectF(30f, y, PAGE_WIDTH - 30f, y + rowHeight + 10f)
    drawDataBox(canvas, boxRect, bgColor = "#F8FAFC")

    drawTableCell(canvas, "سعر القطعة:", "${shipment.unitPrice} $currency", 30f, y + 5f, (PAGE_WIDTH - 60f) / 3f)
    drawTableCell(canvas, "الخصم:", "${shipment.discount} $currency", 30f + (PAGE_WIDTH - 60f) / 3f, y + 5f, (PAGE_WIDTH - 60f) / 3f)
    drawTableCell(
      canvas,
      "إجمالي المبلغ:",
      "${shipment.totalPrice} $currency",
      30f + 2 * (PAGE_WIDTH - 60f) / 3f,
      y + 5f,
      (PAGE_WIDTH - 60f) / 3f,
      isBold = true,
      valueColor = Color.parseColor("#059669")
    )

    return y + rowHeight + 15f
  }

  /**
   * Section 5: Official Signatures
   */
  private fun drawSignaturesSection(canvas: Canvas, startY: Float) {
    val y = (PAGE_HEIGHT - 130f).coerceAtLeast(startY)
    drawSectionHeader(canvas, "5. الاعتماد والتواقيع الرسمية", y)

    val signatureBoxTop = y + 16f
    val sigBoxHeight = 55f

    // Doctor Signature Box (Right)
    val docRect = RectF(PAGE_WIDTH / 2f + 10f, signatureBoxTop, PAGE_WIDTH - 30f, signatureBoxTop + sigBoxHeight)
    drawDataBox(canvas, docRect)
    val labelPaint = TextPaint().apply {
      color = Color.parseColor("#334155")
      textSize = 8.5f
      isFakeBoldText = true
      textAlign = Paint.Align.CENTER
      isAntiAlias = true
    }
    canvas.drawText("ختم وتوقيع المركز الطبي (${ClinicInfo.DOCTOR_NAME})", docRect.centerX(), signatureBoxTop + 16f, labelPaint)
    canvas.drawText("........................................................", docRect.centerX(), signatureBoxTop + 45f, labelPaint)

    // Lab Receiver Signature Box (Left)
    val labRect = RectF(30f, signatureBoxTop, PAGE_WIDTH / 2f - 10f, signatureBoxTop + sigBoxHeight)
    drawDataBox(canvas, labRect)
    canvas.drawText("استلام وتوقيع فني المختبر المنفذ", labRect.centerX(), signatureBoxTop + 16f, labelPaint)
    canvas.drawText("........................................................", labRect.centerX(), signatureBoxTop + 45f, labelPaint)
  }

  /**
   * Official Bottom Footer
   */
  private fun drawFooter(canvas: Canvas) {
    val footerY = PAGE_HEIGHT - 38f

    val linePaint = Paint().apply {
      color = Color.parseColor("#CBD5E1")
      strokeWidth = 1f
    }
    canvas.drawLine(30f, footerY, PAGE_WIDTH - 30f, footerY, linePaint)

    val footerTextPaint = TextPaint().apply {
      color = Color.parseColor("#64748B")
      textSize = 7.5f
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(
      "🏥 ${ClinicInfo.CLINIC_NAME}  •  ${ClinicInfo.ADDRESS}  •  هواتف: ${ClinicInfo.PHONES}",
      PAGE_WIDTH / 2f,
      footerY + 14f,
      footerTextPaint
    )

    val copyTextPaint = TextPaint().apply {
      color = Color.parseColor("#94A3B8")
      textSize = 6.5f
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(
      "تم إصدار وتوثيق هذا السند عبر المنظومة الرقمية لمركز د. عقلان الكامل • صفحة 1 من 1",
      PAGE_WIDTH / 2f,
      footerY + 25f,
      copyTextPaint
    )
  }

  private fun drawSectionHeader(canvas: Canvas, title: String, y: Float) {
    val paint = TextPaint().apply {
      color = Color.parseColor("#0F2B48")
      textSize = 10f
      isFakeBoldText = true
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(title, PAGE_WIDTH - 32f, y + 10f, paint)

    val linePaint = Paint().apply {
      color = Color.parseColor("#38BDF8")
      strokeWidth = 2f
    }
    canvas.drawLine(30f, y + 14f, PAGE_WIDTH - 30f, y + 14f, linePaint)
  }

  private fun drawDataBox(canvas: Canvas, rect: RectF, bgColor: String = "#FFFFFF") {
    val fillPaint = Paint().apply {
      color = Color.parseColor(bgColor)
    }
    canvas.drawRoundRect(rect, 6f, 6f, fillPaint)

    val borderPaint = Paint().apply {
      color = Color.parseColor("#E2E8F0")
      style = Paint.Style.STROKE
      strokeWidth = 1f
    }
    canvas.drawRoundRect(rect, 6f, 6f, borderPaint)
  }

  private fun drawTableCell(
    canvas: Canvas,
    label: String,
    value: String,
    x: Float,
    y: Float,
    width: Float,
    isBold: Boolean = false,
    valueColor: Int = Color.parseColor("#0F172A")
  ) {
    val labelPaint = TextPaint().apply {
      color = Color.parseColor("#64748B")
      textSize = 8.5f
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }
    val valuePaint = TextPaint().apply {
      color = valueColor
      textSize = 9f
      isFakeBoldText = isBold
      isAntiAlias = true
      textAlign = Paint.Align.RIGHT
    }

    val rightEdge = x + width - 10f
    canvas.drawText(label, rightEdge, y + 12f, labelPaint)

    // Calculate value position
    val labelWidth = labelPaint.measureText(label)
    canvas.drawText(value, rightEdge - labelWidth - 8f, y + 12f, valuePaint)
  }

  /**
   * Opens the PDF file using an external viewer application
   */
  fun openPdfFile(context: Context, pdfFile: File) {
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
      context.startActivity(intent)
    } catch (e: Exception) {
      Toast.makeText(context, "لم يتم العثور على تطبيق لعرض ملفات PDF، يرجى مشاركة الملف", Toast.LENGTH_LONG).show()
      sharePdfFile(context, pdfFile)
    }
  }

  /**
   * Shares the PDF file via WhatsApp, Telegram, Email, Bluetooth, etc.
   */
  fun sharePdfFile(context: Context, pdfFile: File, title: String = "مشاركة سند الإرسالية PDF") {
    try {
      val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
      )
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "مرفق سند إرسالية معمل الأسنان - ${ClinicInfo.CLINIC_NAME}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(context, "فشلت مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Prints the PDF directly using Android's native PrintManager
   */
  fun printPdf(context: Context, pdfFile: File, jobName: String = "Shipment_Report") {
    try {
      val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      if (printManager != null) {
        val printAdapter = object : PrintDocumentAdapter() {
          override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
          ) {
            if (cancellationSignal?.isCanceled == true) {
              callback?.onLayoutCancelled()
              return
            }
            val info = PrintDocumentInfo.Builder(pdfFile.name)
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
              .setPageCount(1)
              .build()
            callback?.onLayoutFinished(info, true)
          }

          override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
          ) {
            try {
              FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                  val buffer = ByteArray(1024)
                  var bytesRead: Int
                  while (input.read(buffer).also { bytesRead = it } >= 0) {
                    output.write(buffer, 0, bytesRead)
                  }
                }
              }
              callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
              callback?.onWriteFailed(e.message)
            }
          }
        }

        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
      } else {
        Toast.makeText(context, "خدمة الطباعة غير متاحة في هذا الجهاز", Toast.LENGTH_SHORT).show()
      }
    } catch (e: Exception) {
      Toast.makeText(context, "حدث خطأ أثناء إرسال أمر الطباعة", Toast.LENGTH_SHORT).show()
    }
  }
}
