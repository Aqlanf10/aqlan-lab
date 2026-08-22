package com.aqlanlab.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.R

object ClinicInfo {
  const val CLINIC_NAME = "مركز الدكتور عقلان الكامل لتقويم وزراعة وتجميل الأسنان"
  const val CLINIC_SHORT_NAME = "مركز د. عقلان الكامل"
  const val DOCTOR_NAME = "د. عقلان الكامل"
  const val PHONES = "770245745 - 711752823 - 04253028"
  const val PHONE_PRIMARY = "770245745"
  const val PHONE_SECONDARY = "711752823"
  const val PHONE_LANDLINE = "04253028"
  const val ADDRESS = "شارع التحرير الأعلى - جوار جامع الأزهر"
  const val SPECIALTIES = "تقويم • زراعة • تجميل وتركيبات الأسنان"
  const val APP_FULL_TITLE = "برنامج إدارة المعامل - مركز الدكتور عقلان الكامل"
  const val EMAIL = "Aqlanf10@gmail.com"

  fun openDialer(context: Context, phoneNumber: String) {
    try {
      val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      Toast.makeText(context, "تعذر فتح لوحة الاتصال", Toast.LENGTH_SHORT).show()
    }
  }

  fun openWhatsApp(context: Context, phoneNumber: String, message: String = "") {
    try {
      val cleanPhone = phoneNumber.replace("+", "").replace("-", "").replace(" ", "")
      val formattedPhone = if (!cleanPhone.startsWith("967") && cleanPhone.length == 9) "967$cleanPhone" else cleanPhone
      val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}"
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    } catch (e: Exception) {
      Toast.makeText(context, "تطبيق واتساب غير مثبت", Toast.LENGTH_SHORT).show()
    }
  }

  fun sendSms(context: Context, phoneNumber: String, message: String = "") {
    try {
      val cleanPhone = phoneNumber.replace("-", "").replace(" ", "")
      val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:$cleanPhone")
        putExtra("sms_body", message)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      try {
        val backupIntent = Intent(Intent.ACTION_VIEW).apply {
          data = Uri.parse("sms:$phoneNumber")
          putExtra("sms_body", message)
        }
        context.startActivity(backupIntent)
      } catch (ex: Exception) {
        Toast.makeText(context, "تعذر فتح تطبيق الرسائل القصيرة (SMS)", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun formatOfficialHeader(title: String): String {
    return """
      ═══════════════════════════════════════
      🏥 $CLINIC_NAME
      🦷 $SPECIALTIES
      📍 $ADDRESS
      📞 $PHONES
      ═══════════════════════════════════════
      📋 $title
      ───────────────────────────────────────
    """.trimIndent()
  }

  fun formatOfficialFooter(): String {
    return """
      ───────────────────────────────────────
      📍 $ADDRESS
      📞 هاتف/واتساب: $PHONES
      ✨ $CLINIC_SHORT_NAME - لابتسامة مشرقة وثقة دائمة
      ═══════════════════════════════════════
    """.trimIndent()
  }
}

/**
 * Custom Vector Logo for Dr. Aqlan Al-Kamel Dental Center
 * Featuring golden orange tooth contour + navy calligraphy script
 */
@Composable
fun AqlanLogo(
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
  showArabicText: Boolean = false,
  textColor: Color = MaterialTheme.colorScheme.onSurface
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(Color(0xFFFFFBEB))
        .border(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_aqlan_logo),
        contentDescription = "شعار مركز د. عقلان الكامل",
        tint = Color.Unspecified,
        modifier = Modifier
          .fillMaxSize()
          .padding((size.value * 0.1f).dp)
      )
    }

    if (showArabicText) {
      Column {
        Text(
          text = ClinicInfo.CLINIC_SHORT_NAME,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = textColor,
          fontSize = 15.sp
        )
        Text(
          text = ClinicInfo.SPECIALTIES,
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFFD97706),
          fontWeight = FontWeight.Medium,
          fontSize = 10.sp
        )
      }
    }
  }
}

/**
 * Official Clinic Brand Header Banner with Logo, Title, Address & Direct Phone Actions
 */
@Composable
fun AqlanClinicHeaderCard(
  modifier: Modifier = Modifier,
  onCallRequested: ((String) -> Unit)? = null
) {
  val context = LocalContext.current

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFFFFFDF5),
              Color(0xFFFFFFFF)
            )
          )
        )
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Official Logo
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFEF3C7))
            .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_aqlan_logo),
            contentDescription = "شعار د. عقلان الكامل",
            tint = Color.Unspecified,
            modifier = Modifier
              .size(46.dp)
              .padding(2.dp)
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = ClinicInfo.CLINIC_NAME,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            fontSize = 14.sp,
            lineHeight = 20.sp
          )
          Text(
            text = ClinicInfo.SPECIALTIES,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD97706),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp)
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFF64748B),
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = ClinicInfo.ADDRESS,
              fontSize = 11.sp,
              color = Color(0xFF64748B)
            )
          }
        }
      }

      HorizontalDivider(color = Color(0xFFF1F5F9))

      // Contact Numbers & Quick Call Row
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
            Icons.Default.Phone,
            contentDescription = null,
            tint = Color(0xFF0284C7),
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = ClinicInfo.PHONES,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          // Primary Mobile Dial
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE0F2FE),
            modifier = Modifier
              .clickable {
                onCallRequested?.invoke(ClinicInfo.PHONE_PRIMARY) ?: ClinicInfo.openDialer(context, ClinicInfo.PHONE_PRIMARY)
              }
              .testTag("call_primary_phone_btn")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF0369A1), modifier = Modifier.size(14.dp))
              Text("اتصال", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
            }
          }

          // WhatsApp Button
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFDCFCE7),
            modifier = Modifier
              .clickable {
                ClinicInfo.openWhatsApp(context, ClinicInfo.PHONE_PRIMARY, "السلام عليكم - مركز د. عقلان الكامل")
              }
              .testTag("whatsapp_primary_phone_btn")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
              Text("واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
            }
          }
        }
      }
    }
  }
}

/**
 * Compact Printable / Exportable Header for receipts and detail dialogs
 */
@Composable
fun AqlanPrintableHeader(
  title: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFFF8FAFC),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_aqlan_logo),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = ClinicInfo.CLINIC_NAME,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
          )
          Text(
            text = "${ClinicInfo.ADDRESS} | هاتف: ${ClinicInfo.PHONES}",
            fontSize = 10.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
          )
        }
      }

      HorizontalDivider(color = Color(0xFFCBD5E1))

      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = Color(0xFF1E3A8A)
      )
    }
  }
}
