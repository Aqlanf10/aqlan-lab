package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AqlanLogo
import com.example.ui.components.ClinicInfo
import com.example.ui.viewmodel.DentalLabViewModel
import kotlinx.coroutines.launch

/**
 * شاشة الإعداد الأول.
 *
 * سبب وجودها: كان التطبيق يُنشئ عند أول تشغيل ثلاثة حسابات جاهزة برموز مرور
 * ثابتة (1111 للمدير، 2222 للموظف، 3333 للمحاسب) — وهي رموز موجودة في الكود
 * المصدري وكانت معروضة على شاشة الدخول نفسها. عملياً كان التطبيق بلا حماية.
 *
 * الآن: لا يوجد أي رمز مرور افتراضي في التطبيق، ويجب على مالك المركز تعيين رمز
 * مرور المدير بنفسه قبل أن تُفتح أي شاشة تحتوي بيانات.
 */
@Composable
fun InitialSetupScreen(
  viewModel: DentalLabViewModel,
  onSetupComplete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scope = rememberCoroutineScope()

  var fullName by remember { mutableStateOf(ClinicInfo.DOCTOR_NAME) }
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf("") }
  var isSaving by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0B192C))
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      AqlanLogo(size = 78.dp)

      Text(
        text = ClinicInfo.CLINIC_NAME,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        textAlign = TextAlign.Center
      )

      Text(
        text = "الإعداد الأول لنظام إدارة المعامل",
        color = Color(0xFF94A3B8),
        fontSize = 13.sp,
        textAlign = TextAlign.Center
      )

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              Icons.Default.AdminPanelSettings,
              contentDescription = null,
              tint = Color(0xFFFBBF24)
            )
            Text(
              text = "إنشاء حساب مدير النظام",
              fontWeight = FontWeight.Bold,
              color = Color(0xFFF8FAFC),
              fontSize = 15.sp
            )
          }

          Text(
            text = "اختر رمز مرور خاصاً بك لا يعرفه أحد غيرك. لا يوجد رمز افتراضي في التطبيق، ولا يمكن استرجاع الرمز إذا نسيته — احتفظ به في مكان آمن.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
          )

          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it; errorMessage = "" },
            label = { Text("اسم مدير النظام") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8)) },
            colors = setupFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { ch -> ch.isDigit() }; errorMessage = "" },
            label = { Text("رمز المرور (٤ أرقام على الأقل)") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = setupFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = confirmPin,
            onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }; errorMessage = "" },
            label = { Text("تأكيد رمز المرور") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = setupFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )

          if (errorMessage.isNotBlank()) {
            Text(
              text = errorMessage,
              color = Color(0xFFF87171),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Button(
            onClick = {
              if (isSaving) return@Button
              isSaving = true
              scope.launch {
                val error = viewModel.completeInitialSetup(fullName, pin, confirmPin)
                isSaving = false
                if (error == null) {
                  onSetupComplete()
                } else {
                  errorMessage = error
                }
              }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
          ) {
            if (isSaving) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
              Text("حفظ وبدء استخدام النظام", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      Text(
        text = "بعد الدخول يمكنك إضافة حسابات الموظفين والمحاسب من: الإعدادات ← إدارة المستخدمين",
        fontSize = 11.sp,
        color = Color(0xFF64748B),
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun setupFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedBorderColor = Color(0xFFF59E0B),
  unfocusedBorderColor = Color(0xFF475569),
  focusedLabelColor = Color(0xFFF59E0B),
  unfocusedLabelColor = Color(0xFF94A3B8),
  focusedTextColor = Color.White,
  unfocusedTextColor = Color.White
)
