package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Shipment
import com.example.data.models.ShipmentStatus
import com.example.data.models.UserRole
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DentalLabViewModel
import com.example.util.PdfReportGenerator
import com.example.util.QrCodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentDetailScreen(
  shipmentId: Long,
  viewModel: DentalLabViewModel,
  onNavigateToEdit: (Long) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val activeUser by viewModel.activeUser.collectAsState()
  val currency by viewModel.currency.collectAsState()
  val allShipments by viewModel.allShipments.collectAsState()

  val shipment = allShipments.find { it.id == shipmentId }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showVoucherDialog by remember { mutableStateOf(false) }

  if (shipment == null) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("تفاصيل الإرسالية") },
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
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    }
    return
  }

  val isLate = DateUtils.isLate(shipment.expectedDeliveryDate, shipment.status)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "إرسالية ${shipment.shipmentNumber}",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = DateUtils.formatDateTime(shipment.orderDate),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
              val pdfFile = PdfReportGenerator.generateShipmentPdf(
                context = context,
                shipment = shipment,
                userRole = activeUser.role,
                currency = currency
              )
              if (pdfFile != null) {
                PdfReportGenerator.openPdfFile(context, pdfFile)
              } else {
                android.widget.Toast.makeText(context, "فشل إنشاء تقرير PDF", android.widget.Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.testTag("pdf_report_btn")
          ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = "تقرير واستمارة PDF رسمية", tint = Color(0xFFEF4444))
          }
          IconButton(
            onClick = { showVoucherDialog = true },
            modifier = Modifier.testTag("print_voucher_btn")
          ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = "سند الإرسالية")
          }
          IconButton(
            onClick = {
              val shareText = buildString {
                append("🏥 *${ClinicInfo.CLINIC_NAME}*\n")
                append("📍 ${ClinicInfo.ADDRESS} | 📞 ${ClinicInfo.PHONES}\n")
                append("───────────────────────────────────\n")
                append("📋 *سند إرسالية معمل أسنان*\n")
                append("رقم الإرسالية: ${shipment.shipmentNumber}\n")
                append("المعمل المنفذ: ${shipment.labName}\n")
                append("نوع العمل: ${shipment.workTypeName} (${shipment.pieceCount} قطع)\n")
                if (shipment.toothNumbers.isNotEmpty()) append("أرقام الأسنان: ${shipment.toothNumbers}\n")
                append("اللون Shade: ${shipment.shade}\n")
                append("الطبيب: ${shipment.clinicOrDoctorName.ifEmpty { ClinicInfo.DOCTOR_NAME }}\n")
                append("موعد التسليم: ${DateUtils.formatShortDate(shipment.expectedDeliveryDate)}\n")
                if (shipment.isUrgent) append("⚠️ *حالة عاجلة جداً*\n")
                if (shipment.notes.isNotEmpty()) append("ملاحظات: ${shipment.notes}\n")
                append("───────────────────────────────────\n")
                append("📞 للتواصل: ${ClinicInfo.PHONES}\n")
              }
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "مشاركة بيانات الإرسالية"))
            },
            modifier = Modifier.testTag("share_shipment_btn")
          ) {
            Icon(Icons.Default.Share, contentDescription = "مشاركة")
          }
          IconButton(
            onClick = { onNavigateToEdit(shipment.id) },
            modifier = Modifier.testTag("edit_shipment_btn")
          ) {
            Icon(Icons.Default.Edit, contentDescription = "تعديل")
          }
          if (activeUser.role == UserRole.ADMIN) {
            IconButton(
              onClick = { showDeleteDialog = true },
              modifier = Modifier.testTag("delete_shipment_btn")
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
            }
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
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Status Progress Tracker Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "مراحل إنجاز العمل",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            StatusBadge(status = shipment.status, isLate = isLate)
          }

          // Step Progress Bar
          val steps = listOf(
            Pair(ShipmentStatus.NEW, "جديدة"),
            Pair(ShipmentStatus.IN_PROGRESS, "قيد العمل"),
            Pair(ShipmentStatus.READY, "جاهزة"),
            Pair(ShipmentStatus.RECEIVED, "تم الاستلام")
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            steps.forEachIndexed { index, (stepStatus, stepTitle) ->
              val isDone = shipment.status.stepIndex >= stepStatus.stepIndex && shipment.status != ShipmentStatus.CANCELLED
              val isCurrent = shipment.status == stepStatus

              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                      if (isDone) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  if (isDone) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.size(16.dp)
                    )
                  } else {
                    Text(
                      text = "${index + 1}",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                  text = stepTitle,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                  color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          // Status Action Buttons
          HorizontalDivider()

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (shipment.status != ShipmentStatus.IN_PROGRESS) {
              Button(
                onClick = { viewModel.updateShipmentStatus(shipment.id, ShipmentStatus.IN_PROGRESS) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusInProgress)
              ) {
                Icon(Icons.Default.Autorenew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("قيد العمل بالمختبر")
              }
            }

            if (shipment.status != ShipmentStatus.READY) {
              Button(
                onClick = { viewModel.updateShipmentStatus(shipment.id, ShipmentStatus.READY) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusReady)
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("جاهزة للاستلام")
              }
            }

            if (shipment.status != ShipmentStatus.RECEIVED) {
              Button(
                onClick = { viewModel.updateShipmentStatus(shipment.id, ShipmentStatus.RECEIVED) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusReceived)
              ) {
                Icon(Icons.Default.TaskAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("تم الاستلام بالعيادة")
              }
            }

            if (shipment.status != ShipmentStatus.CANCELLED) {
              OutlinedButton(
                onClick = { viewModel.updateShipmentStatus(shipment.id, ShipmentStatus.CANCELLED) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled)
              ) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("إلغاء الإرسالية")
              }
            }
          }
        }
      }

      // 2. Work & Laboratory Details Card
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
            Text(
              text = "تفاصيل العمل والطلب",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            FilledTonalButton(
              onClick = { showVoucherDialog = true },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.height(32.dp)
            ) {
              Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("سند التسليم", fontSize = 11.sp)
            }
          }

          DetailRow(label = "نوع العمل المطلوب", value = shipment.workTypeName)
          DetailRow(label = "المعمل المنفذ", value = shipment.labName)
          DetailRow(label = "عدد القطع (الوحدات)", value = "${shipment.pieceCount} قطعة")

          if (shipment.toothNumbers.isNotEmpty()) {
            DetailRow(label = "أرقام الأسنان (FDI)", value = shipment.toothNumbers)
          }

          // Shade info with swatch
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "لون الشغل (Shade):",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              val matchingShade = standardShades.find { it.code.equals(shipment.shade, ignoreCase = true) }
              Box(
                modifier = Modifier
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(matchingShade?.tintColor ?: Color(0xFFF4EFE6))
                  .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
              )
              Text(
                text = shipment.shade,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }

          if (shipment.shadeNotes.isNotEmpty()) {
            DetailRow(label = "ملاحظات اللون", value = shipment.shadeNotes)
          }

          HorizontalDivider()

          DetailRow(label = "اسم الطبيب / العيادة", value = shipment.clinicOrDoctorName.ifEmpty { "غير محدد" })
          DetailRow(label = "اسم المريض / الملف", value = shipment.patientName.ifEmpty { "غير محدد" })
          DetailRow(label = "تاريخ الطلب", value = DateUtils.formatDateTime(shipment.orderDate))
          DetailRow(label = "تاريخ التسليم المتوقع", value = DateUtils.formatShortDate(shipment.expectedDeliveryDate))

          if (shipment.actualReceivedDate != null) {
            DetailRow(label = "تاريخ الاستلام الفعلي", value = DateUtils.formatDateTime(shipment.actualReceivedDate))
          }

          if (shipment.isUrgent) {
            DetailRow(label = "حالة الاستعجال", value = "⚡ حالة عاجلة جداً")
          }

          if (shipment.notes.isNotEmpty()) {
            DetailRow(label = "ملاحظات الفني", value = shipment.notes)
          }

          DetailRow(label = "تم الإنشاء بواسطة", value = "${shipment.createdByName}")
        }
      }

      // 2.5 QR Code Package Label Card (ملصق الباركود وQR لطرد الإرسالية)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
              Icon(
                imageVector = Icons.Default.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = "ملصق كود QR لطرد الإرسالية",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.primaryContainer
            ) {
              Text(
                text = shipment.shipmentNumber,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Text(
            text = "امسح هذا الكود بكاميرا الهاتف للوصول الفوري لسجل وتفاصيل هذه الإرسالية وحالتها.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            QrCodeView(
              content = shipment.shipmentNumber,
              size = 160.dp,
              modifier = Modifier.padding(vertical = 4.dp)
            )
          }
        }
      }

      // 3. Smart Financial Section (Admin / Accountant ONLY)
      if (activeUser.role != UserRole.STAFF) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
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
              Text(
                text = "الحساب المالي للإرسالية (خاص بالإدارة)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              RoleBadge(role = activeUser.role)
            }

            DetailRow(
              label = "سعر القطعة من المعمل",
              value = "${shipment.unitPrice} $currency"
            )
            DetailRow(
              label = "إجمالي القطع (${shipment.pieceCount} × ${shipment.unitPrice})",
              value = "${shipment.pieceCount * shipment.unitPrice} $currency"
            )
            if (shipment.discount > 0) {
              DetailRow(
                label = "خصم خاص للمعمل",
                value = "- ${shipment.discount} $currency"
              )
            }

            HorizontalDivider()

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "إجمالي تكلفة الإرسالية:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              PriceDisplay(
                amount = shipment.totalPrice,
                userRole = activeUser.role,
                currencyCode = currency,
                style = MaterialTheme.typography.titleLarge
              )
            }
          }
        }
      } else {
        // Safe placeholder banner for Staff
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text(
              text = "البيانات والأسعار المالية محجوبة ومخصصة للإدارة والمحاسبة فقط.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
      }

      // 4. Official Branded PDF Report & Print Center Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
              Surface(
                shape = CircleShape,
                color = Color(0xFFEF4444)
              ) {
                Icon(
                  imageVector = Icons.Default.PictureAsPdf,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.padding(6.dp).size(20.dp)
                )
              }
              Column {
                Text(
                  text = "استمارة وسند الإرسالية الرسمي (PDF)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "مطبوع بترويسة وشعار ${ClinicInfo.CLINIC_SHORT_NAME}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          Text(
            text = "توليد ملف PDF عالي الجودة يحتوي على كافة بيانات المريض، نوع التركيبة، كود الـ QR، أرقام التواصل والتواقيع الرسمية.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

          // 3 Action Buttons: Open/Preview, Share PDF, Direct Print
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // View / Open PDF
            Button(
              onClick = {
                val pdfFile = PdfReportGenerator.generateShipmentPdf(
                  context = context,
                  shipment = shipment,
                  userRole = activeUser.role,
                  currency = currency
                )
                if (pdfFile != null) {
                  PdfReportGenerator.openPdfFile(context, pdfFile)
                } else {
                  android.widget.Toast.makeText(context, "فشل إنشاء ملف PDF", android.widget.Toast.LENGTH_SHORT).show()
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).testTag("open_pdf_btn")
            ) {
              Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("فتح PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Share PDF
            OutlinedButton(
              onClick = {
                val pdfFile = PdfReportGenerator.generateShipmentPdf(
                  context = context,
                  shipment = shipment,
                  userRole = activeUser.role,
                  currency = currency
                )
                if (pdfFile != null) {
                  PdfReportGenerator.sharePdfFile(context, pdfFile, title = "مشاركة إرسالية ${shipment.shipmentNumber}")
                }
              },
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.weight(1f).testTag("share_pdf_btn")
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("مشاركة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Direct Print
            FilledTonalButton(
              onClick = {
                val pdfFile = PdfReportGenerator.generateShipmentPdf(
                  context = context,
                  shipment = shipment,
                  userRole = activeUser.role,
                  currency = currency
                )
                if (pdfFile != null) {
                  PdfReportGenerator.printPdf(context, pdfFile, jobName = "Shipment_${shipment.shipmentNumber}")
                }
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).testTag("direct_print_btn")
            ) {
              Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("طباعة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Official Delivery Voucher Dialog
    if (showVoucherDialog) {
      AlertDialog(
        onDismissRequest = { showVoucherDialog = false },
        title = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("سند تسليم إرسالية المختبر", fontWeight = FontWeight.Bold)
            Icon(Icons.Default.LocalPrintshop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                AqlanPrintableHeader(title = "سند تسليم إرسالية المختبر #${shipment.shipmentNumber}")

                DetailRow(label = "المعمل:", value = shipment.labName)
                DetailRow(label = "الطبيب المعالج:", value = shipment.clinicOrDoctorName.ifEmpty { ClinicInfo.DOCTOR_NAME })
                DetailRow(label = "المريض:", value = shipment.patientName.ifEmpty { "حالة عيادة" })
                DetailRow(label = "العمل:", value = "${shipment.pieceCount} × ${shipment.workTypeName}")
                if (shipment.toothNumbers.isNotEmpty()) {
                  DetailRow(label = "الأسنان FDI:", value = shipment.toothNumbers)
                }
                DetailRow(label = "درجة اللون:", value = shipment.shade)
                if (shipment.shadeNotes.isNotEmpty()) {
                  DetailRow(label = "ملاحظات اللون:", value = shipment.shadeNotes)
                }
                DetailRow(label = "تاريخ الإرسال:", value = DateUtils.formatShortDate(shipment.orderDate))
                DetailRow(label = "موعد الاستلام:", value = DateUtils.formatShortDate(shipment.expectedDeliveryDate))
                if (shipment.isUrgent) {
                  DetailRow(label = "الأولوية:", value = "⚡ عاجل جداً")
                }

                HorizontalDivider()

                // QR Code on Printable Voucher
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  QrCodeView(
                    content = shipment.shipmentNumber,
                    size = 100.dp
                  )
                  Text(
                    text = shipment.shipmentNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                  )
                }

                HorizontalDivider()

                // Signatures placeholder
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("توقيع العيادة", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(18.dp))
                    Text("..................", style = MaterialTheme.typography.labelSmall)
                  }
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("استلام فني المختبر", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(18.dp))
                    Text("..................", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                val pdfFile = PdfReportGenerator.generateShipmentPdf(
                  context = context,
                  shipment = shipment,
                  userRole = activeUser.role,
                  currency = currency
                )
                if (pdfFile != null) {
                  PdfReportGenerator.openPdfFile(context, pdfFile)
                }
                showVoucherDialog = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
              Icon(Icons.Default.PictureAsPdf, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("تصدير PDF")
            }

            Button(
              onClick = {
                val lab = allShipments.firstOrNull()?.let { null }
                viewModel.sendShipmentToWhatsApp(context, shipment, null)
                showVoucherDialog = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) {
              Icon(Icons.Default.Send, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("واتساب أونلاين")
            }

            Button(
              onClick = {
                val voucherText = buildString {
                  append("═══════════════════════════════════════\n")
                  append("🏥 ${ClinicInfo.CLINIC_NAME}\n")
                  append("📍 ${ClinicInfo.ADDRESS}\n")
                  append("📞 ${ClinicInfo.PHONES}\n")
                  append("═══════════════════════════════════════\n")
                  append("    سند تسليم إرسالية مختبر أسنان\n")
                  append("───────────────────────────────────────\n")
                  append("رقم السند: ${shipment.shipmentNumber}\n")
                  append("المعمل المنفذ: ${shipment.labName}\n")
                  append("الطبيب: ${shipment.clinicOrDoctorName.ifEmpty { ClinicInfo.DOCTOR_NAME }}\n")
                  append("المريض: ${shipment.patientName}\n")
                  append("نوع العمل: ${shipment.workTypeName} (${shipment.pieceCount} قطع)\n")
                  append("الأسنان: ${shipment.toothNumbers}\n")
                  append("اللون: ${shipment.shade} ${if (shipment.shadeNotes.isNotEmpty()) "(${shipment.shadeNotes})" else ""}\n")
                  append("موعد التسليم المطلوب: ${DateUtils.formatShortDate(shipment.expectedDeliveryDate)}\n")
                  if (shipment.notes.isNotEmpty()) append("الملاحظات: ${shipment.notes}\n")
                  append("───────────────────────────────────────\n")
                  append("📞 للتواصل والمتابعة: ${ClinicInfo.PHONES}\n")
                  append("═══════════════════════════════════════\n")
                }
                clipboardManager.setText(AnnotatedString(voucherText))
                showVoucherDialog = false
              }
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("نسخ السند")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showVoucherDialog = false }) {
            Text("إغلاق")
          }
        }
      )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("حذف الإرسالية") },
        text = { Text("هل أنت متأكد من حذف الإرسالية ${shipment.shipmentNumber}؟ سيتم تسجيل هذا الإجراء في سجل العمليات.") },
        confirmButton = {
          Button(
            onClick = {
              viewModel.deleteShipment(shipment)
              showDeleteDialog = false
              onBack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("تأكيد الحذف")
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteDialog = false }) {
            Text("إلغاء")
          }
        }
      )
    }
  }
}

@Composable
private fun DetailRow(
  label: String,
  value: String,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold
    )
  }
}
