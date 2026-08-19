package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.UserRole
import com.example.ui.components.AqlanLogo
import com.example.ui.components.ClinicInfo
import com.example.ui.viewmodel.DentalLabViewModel

@Composable
fun AppSecurityLockScreen(
  viewModel: DentalLabViewModel,
  onUnlocked: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val allUsers by viewModel.allUsers.collectAsState()
  var enteredPin by remember { mutableStateOf("") }
  var isError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var attemptsCount by remember { mutableIntStateOf(0) }
  var showLicenseDialog by remember { mutableStateOf(false) }
  var showQuickAccountDialog by remember { mutableStateOf(false) }

  val maxPinLength = 4

  fun handleKeyInput(char: Char) {
    if (enteredPin.length < maxPinLength) {
      val newPin = enteredPin + char
      enteredPin = newPin
      isError = false
      errorMessage = ""

      if (newPin.length == maxPinLength) {
        val success = viewModel.unlockAppWithPin(newPin)
        if (success) {
          onUnlocked()
        } else {
          isError = true
          attemptsCount++
          errorMessage = "رمز المرور غير صحيح! يرجى إدخال الرمز المعتمد"
          enteredPin = ""
        }
      }
    }
  }

  fun handleBackspace() {
    if (enteredPin.isNotEmpty()) {
      enteredPin = enteredPin.dropLast(1)
      isError = false
      errorMessage = ""
    }
  }

  fun handleBiometricUnlock() {
    val success = viewModel.unlockAppWithBiometric()
    if (success) {
      Toast.makeText(context, "تم التحقق من بصمة المالك المعتمد: د. عقلان الكامل", Toast.LENGTH_SHORT).show()
      onUnlocked()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF1E293B),
            Color(0xFF0B192C)
          )
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 20.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // 1. Top Exclusive Header & Branding
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
      ) {
        // App Lock Badge
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF1E3A8A).copy(alpha = 0.6f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f)),
          modifier = Modifier.padding(bottom = 12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "تطبيق محمي ومرخص حصرياً",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFF1F5F9)
            )
          }
        }

        // Center Golden Crest Logo
        AqlanLogo(size = 80.dp)

        Spacer(Modifier.height(12.dp))

        Text(
          text = ClinicInfo.CLINIC_NAME,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
          text = "نظام إدارة المعامل والتعويضات السنية",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF94A3B8),
          textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        // Owner Info Card
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF1E293B).copy(alpha = 0.8f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "المالك المعتمد: ${ClinicInfo.DOCTOR_NAME}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
              )
              Text(
                text = "Aqlanf10@gmail.com | 770245745",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
              )
            }

            IconButton(
              onClick = { showLicenseDialog = true },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "الترخيص",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      // 2. PIN Indicators & Status
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
      ) {
        Text(
          text = "أدخل رمز المرور لفتح التطبيق",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFFE2E8F0),
          fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(14.dp))

        // PIN Indicator Dots
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (i in 0 until maxPinLength) {
            val isFilled = i < enteredPin.length
            Box(
              modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                  if (isError) Color(0xFFEF4444)
                  else if (isFilled) Color(0xFF38BDF8)
                  else Color(0xFF334155)
                )
                .border(
                  width = 2.dp,
                  color = if (isError) Color(0xFFDC2626) else if (isFilled) Color(0xFF60A5FA) else Color(0xFF475569),
                  shape = CircleShape
                )
            )
          }
        }

        // Error message or prompt
        AnimatedVisibility(visible = isError) {
          Text(
            text = errorMessage,
            color = Color(0xFFF87171),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp)
          )
        }

        if (!isError && attemptsCount == 0) {
          Text(
            text = "رمز مرور الطبيب الافتراضي: 1111 (يمكن تغييره من الإعدادات)",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp)
          )
        }
      }

      // 3. Modern Numeric Keypad
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 12.dp)
      ) {
        val keys = listOf(
          listOf('1', '2', '3'),
          listOf('4', '5', '6'),
          listOf('7', '8', '9'),
          listOf('B', '0', '<') // B: Biometric, <: Backspace
        )

        keys.forEach { row ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            row.forEach { key ->
              when (key) {
                '<' -> {
                  // Backspace Key
                  Surface(
                    onClick = { handleBackspace() },
                    shape = CircleShape,
                    color = Color(0xFF334155).copy(alpha = 0.5f),
                    modifier = Modifier.size(68.dp).testTag("key_backspace")
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "مسح",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(24.dp)
                      )
                    }
                  }
                }
                'B' -> {
                  // Biometric Key
                  Surface(
                    onClick = { handleBiometricUnlock() },
                    shape = CircleShape,
                    color = Color(0xFF1E3A8A).copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                    modifier = Modifier.size(68.dp).testTag("key_biometric")
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "البصمة",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(30.dp)
                      )
                    }
                  }
                }
                else -> {
                  // Number Key
                  Surface(
                    onClick = { handleKeyInput(key) },
                    shape = CircleShape,
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(68.dp).testTag("key_$key")
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = key.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 4. Quick Account Switching & Staff Authorization
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = { showQuickAccountDialog = true }
        ) {
          Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("تبديل الحساب / تفويض موظف", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }

        TextButton(
          onClick = { showLicenseDialog = true }
        ) {
          Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("حالة الحماية", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
      }
    }
  }

  // --- License & Device Protection Dialog ---
  if (showLicenseDialog) {
    AlertDialog(
      onDismissRequest = { showLicenseDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = Color(0xFF10B981),
          modifier = Modifier.size(40.dp)
        )
      },
      title = {
        Text(
          text = "بيانات الترخيص والحماية الخاصة",
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("👑 المالك الحصري: ${ClinicInfo.DOCTOR_NAME}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text("🏥 المركز: ${ClinicInfo.CLINIC_NAME}", fontSize = 12.sp)
              Text("📧 البريد المعتمد: Aqlanf10@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
              Text("📞 الهاتف المعتمد: 770245745 - 711752823", fontSize = 12.sp)
              Text("🔒 التشفير: AES-256 On-Device Database", fontSize = 11.sp, color = Color(0xFF059669))
              Text("☁️ مزامنة Firestore: مرتبطة بحساب المركز", fontSize = 11.sp, color = Color(0xFF2563EB))
            }
          }
          Text(
            text = "هذا البرنامج محمي بقفل أمان خاص بمركز الدكتور عقلان الكامل. لا يمكن لأي شخص غير مصرح له الدخول إلى الحسابات أو الاطلاع على الإرساليات بدون الرمز السري.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
          )
        }
      },
      confirmButton = {
        Button(onClick = { showLicenseDialog = false }) {
          Text("إغلاق")
        }
      }
    )
  }

  // --- Quick User Switcher Dialog ---
  if (showQuickAccountDialog) {
    AlertDialog(
      onDismissRequest = { showQuickAccountDialog = false },
      title = {
        Text(
          text = "اختيار المستخدم المصرح له",
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "اختر المستخدم وأدخل رمزه الخاص لفتح البرنامج بصلاحياته المحددة:",
            style = MaterialTheme.typography.bodySmall
          )

          allUsers.forEach { user ->
            Surface(
              onClick = {
                showQuickAccountDialog = false
                val success = viewModel.unlockAppWithPin(user.pinCode)
                if (success) {
                  Toast.makeText(context, "مرحباً ${user.fullName} (${user.role.titleAr})", Toast.LENGTH_SHORT).show()
                  onUnlocked()
                }
              },
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Icon(
                    imageVector = if (user.role == UserRole.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (user.role == UserRole.ADMIN) Color(0xFFD97706) else MaterialTheme.colorScheme.primary
                  )
                  Column {
                    Text(user.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(user.role.titleAr, fontSize = 11.sp, color = Color.Gray)
                  }
                }

                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                  Text(
                    text = "رمز: ${user.pinCode}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showQuickAccountDialog = false }) {
          Text("إلغاء")
        }
      }
    )
  }
}
