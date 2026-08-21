package com.aqlanlab.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aqlanlab.app.data.models.Payment
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.util.WhatsAppMessagingManager
import com.aqlanlab.app.util.WhatsAppTemplateType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppNotificationDialog(
  shipment: Shipment? = null,
  payment: Payment? = null,
  initialTemplateType: WhatsAppTemplateType = if (payment != null) WhatsAppTemplateType.PAYMENT_RECEIPT else if (shipment?.status?.name == "READY") WhatsAppTemplateType.CASE_READY_ALERT else WhatsAppTemplateType.PATIENT_INVOICE,
  initialPhoneNumber: String = shipment?.patientPhone ?: "",
  initialPatientName: String = shipment?.patientName ?: "",
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  var selectedTemplate by remember { mutableStateOf(initialTemplateType) }
  var phoneNumber by remember { mutableStateOf(initialPhoneNumber) }
  var patientName by remember { mutableStateOf(initialPatientName) }
  var customNotes by remember { mutableStateOf("") }
  var appointmentDateText by remember { mutableStateOf("") }
  var currency by remember { mutableStateOf(shipment?.currency ?: payment?.currency ?: "SAR") }

  val formattedMessage = remember(selectedTemplate, shipment, payment, patientName, customNotes, appointmentDateText, currency) {
    WhatsAppMessagingManager.buildMessage(
      templateType = selectedTemplate,
      shipment = shipment,
      payment = payment,
      patientName = patientName,
      customNotes = customNotes,
      appointmentDateText = appointmentDateText,
      currencySymbol = currency
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.92f)
        .padding(vertical = 16.dp)
        .testTag("whatsapp_notification_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.surface)
      ) {
        // 1. Header with WhatsApp Green Styling & Clinic Emblem
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  Color(0xFF075E54),
                  Color(0xFF128C7E),
                  Color(0xFF25D366)
                )
              )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                  )
                }
              }

              Column {
                Text(
                  text = "نظام إرسال فواتير وإشعارات واتساب",
                  color = Color.White,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${ClinicInfo.CLINIC_SHORT_NAME} • ${ClinicInfo.DOCTOR_NAME}",
                  color = Color(0xFFDCFCE7),
                  fontSize = 12.sp
                )
              }
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_whatsapp_dialog_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.White
              )
            }
          }
        }

        // 2. Body with scroll
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

          // Template Type Tabs
          Text(
            text = "اختر نوع الرسالة أو الإشعار:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            WhatsAppTemplateType.values().forEach { template ->
              val isSelected = selectedTemplate == template
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Color(0xFF128C7E) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                  1.dp,
                  if (isSelected) Color(0xFF075E54) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                  .clickable { selectedTemplate = template }
                  .testTag("template_${template.id}")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(text = template.iconEmoji, fontSize = 16.sp)
                  Text(
                    text = template.titleAr,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          // Template Description Card
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF0FDF4),
            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF15803D),
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = selectedTemplate.descriptionAr,
                fontSize = 12.sp,
                color = Color(0xFF166534)
              )
            }
          }

          // Phone & Patient Name Fields
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Patient Name
            OutlinedTextField(
              value = patientName,
              onValueChange = { patientName = it },
              label = { Text("اسم المريض") },
              placeholder = { Text("مثال: محمد عبدالسلام") },
              leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF128C7E))
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("whatsapp_patient_name_input")
            )

            // Phone Number
            OutlinedTextField(
              value = phoneNumber,
              onValueChange = { phoneNumber = it },
              label = { Text("رقم الواتساب") },
              placeholder = { Text("77xxxxxxx") },
              leadingIcon = {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF25D366))
              },
              trailingIcon = {
                if (phoneNumber.isNotBlank()) {
                  IconButton(onClick = { phoneNumber = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                  }
                }
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1.1f)
                .testTag("whatsapp_phone_input")
            )
          }

          // Template Specific Fields
          if (selectedTemplate == WhatsAppTemplateType.APPOINTMENT_REMINDER) {
            OutlinedTextField(
              value = appointmentDateText,
              onValueChange = { appointmentDateText = it },
              label = { Text("تاريخ ووقت الموعد") },
              placeholder = { Text("مثال: غداً الأربعاء الساعة 5:00 مساءً") },
              leadingIcon = {
                Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF128C7E))
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("whatsapp_appointment_input")
            )
          }

          // Custom Notes / Remarks
          OutlinedTextField(
            value = customNotes,
            onValueChange = { customNotes = it },
            label = { Text(if (selectedTemplate == WhatsAppTemplateType.CUSTOM) "نص الرسالة المخصصة" else "ملاحظة إضافية للرسالة (اختياري)") },
            placeholder = { Text("أدخل أي ملاحظة أو تفاصيل إضافية...") },
            minLines = if (selectedTemplate == WhatsAppTemplateType.CUSTOM) 4 else 2,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("whatsapp_custom_notes_input")
          )

          // Live WhatsApp Message Preview
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Visibility,
                  contentDescription = null,
                  tint = Color(0xFF128C7E),
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "معاينة الرسالة (كما ستظهر في واتساب):",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }

              TextButton(
                onClick = {
                  WhatsAppMessagingManager.copyToClipboard(context, formattedMessage)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = null,
                  tint = Color(0xFF0284C7),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("نسخ النص", fontSize = 12.sp, color = Color(0xFF0284C7))
              }
            }

            // WhatsApp Chat Bubble Card
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFFEFEAE2), // WhatsApp wallpaper bg color
              border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFFDCF8C6), // WhatsApp sent message green bubble
                  tonalElevation = 2.dp,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                      text = formattedMessage,
                      fontSize = 13.sp,
                      color = Color(0xFF111827),
                      lineHeight = 20.sp,
                      fontFamily = FontFamily.Default
                    )
                  }
                }
              }
            }
          }
        }

        // 3. Action Buttons Footer
        Surface(
          tonalElevation = 6.dp,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Close button
            OutlinedButton(
              onClick = onDismiss,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(0.7f)
            ) {
              Text("إلغاء", fontSize = 13.sp)
            }

            // Share Sheet Button
            OutlinedButton(
              onClick = {
                WhatsAppMessagingManager.shareGeneral(context, formattedMessage)
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(0.8f)
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("مشاركة", fontSize = 13.sp)
            }

            // Primary Send WhatsApp Button
            Button(
              onClick = {
                WhatsAppMessagingManager.sendViaWhatsApp(context, phoneNumber, formattedMessage)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF25D366),
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1.5f)
                .testTag("send_whatsapp_primary_action_btn")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "إرسال عبر واتساب",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
          }
        }
      }
    }
  }
}
