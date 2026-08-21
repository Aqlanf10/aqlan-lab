package com.aqlanlab.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.DeviceBinding
import com.aqlanlab.app.data.models.DeviceStatus
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.network.AuthUiState
import com.aqlanlab.app.ui.components.AqlanLogo
import com.aqlanlab.app.ui.components.ClinicInfo
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseAuthLoginScreen(
  viewModel: DentalLabViewModel,
  onLoginSuccess: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()

  val authState by viewModel.firebaseAuthState.collectAsState()
  val currentUser by viewModel.firebaseCurrentUser.collectAsState()
  val currentDeviceBinding by viewModel.currentDeviceBinding.collectAsState()
  val allDevices by viewModel.allDevices.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Private Login, 1: Doctor PIN, 2: Device Hardware Licensing

  // Login Form States
  var usernameOrEmailInput by remember { mutableStateOf("aqlan") }
  var passwordInput by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var isAuthenticatingLocally by remember { mutableStateOf(false) }

  // Quick Doctor PIN State
  var doctorPinInput by remember { mutableStateOf("") }

  // Forgot Password Dialog State
  var showForgotPasswordDialog by remember { mutableStateOf(false) }
  var resetEmailInput by remember { mutableStateOf("") }
  var isResettingPassword by remember { mutableStateOf(false) }

  // Check if already authenticated and authorized
  LaunchedEffect(authState) {
    if (authState is AuthUiState.Success) {
      onLoginSuccess()
    }
  }

  val currentDeviceId = viewModel.currentDeviceId
  val currentDevice = currentDeviceBinding ?: allDevices.find { it.deviceId == currentDeviceId }
  val deviceStatus = currentDevice?.status ?: DeviceStatus.PENDING

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF0A0F1D),
            Color(0xFF131E35),
            Color(0xFF0F172A)
          )
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Official Private System Crest
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF0284C7).copy(alpha = 0.2f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
          modifier = Modifier.padding(bottom = 12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.VpnKey,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "نظام خاص مغلق ومحمي (Private Access System)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFE0F2FE)
            )
          }
        }

        AqlanLogo(size = 68.dp)

        Spacer(Modifier.height(8.dp))

        Text(
          text = "بوابة الوصول الحصري - معمل د. عقلان",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          textAlign = TextAlign.Center
        )

        Text(
          text = "${ClinicInfo.CLINIC_NAME} - بإشراف ${ClinicInfo.DOCTOR_NAME}",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF94A3B8),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      // 2. Navigation Tabs (Private Access & Hardware Security)
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color(0xFF1E293B),
        contentColor = Color(0xFF38BDF8),
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("تسجيل الدخول", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
          icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("رمز المشرف", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
          icon = { Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("ترخيص الجهاز", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
          icon = { Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
      }

      Spacer(Modifier.height(16.dp))

      // 3. Error / Status Banner
      when (val state = authState) {
        is AuthUiState.Error -> {
          Surface(
            color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5))
              Text(
                text = state.message,
                color = Color(0xFFFEE2E2),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
        is AuthUiState.Unauthorized -> {
          Surface(
            color = Color(0xFF854D0E).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFFDE047))
                Text("حساب غير مصرح له أو معلق!", fontWeight = FontWeight.Bold, color = Color(0xFFFEF08A))
              }
              Text(state.message, color = Color(0xFFFEF9C3), fontSize = 12.sp)
            }
          }
        }
        is AuthUiState.DevicePendingApproval -> {
          Surface(
            color = Color(0xFF1E3A8A).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFF93C5FD))
                Text("هذا الجهاز بانتظار ترخيص المشرف العام", fontWeight = FontWeight.Bold, color = Color.White)
              }
              Text(
                text = if (state.isMaxDevicesExceeded) {
                  "⚠️ تم تسجيل الجهاز، ولكن الحساب وصل للحد الأقصى للأجهزة المصرح بها. يتطلب ترخيص المشرف العام (${ClinicInfo.DOCTOR_NAME}) أو إلغاء ربط جهاز قديم."
                } else {
                  "تم تسجيل طلب الترخيص للجهاز (${state.deviceId}). بانتظار اعتماد المشرف العام ${ClinicInfo.DOCTOR_NAME} من لوحة إدارة الأجهزة والتراخيص."
                },
                color = Color(0xFFDBEAFE),
                fontSize = 12.sp,
                lineHeight = 17.sp
              )
              Button(
                onClick = {
                  viewModel.refreshDeviceAuthorization { status, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (status == DeviceStatus.APPROVED) {
                      onLoginSuccess()
                    }
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(38.dp)
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("فحص حالة الاعتماد الآن (تحديث من السيرفر)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
        is AuthUiState.DeviceBlocked -> {
          Surface(
            color = Color(0xFF7F1D1D).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFCA5A5))
                Text("🚫 الوصول محظور (Access Denied)", fontWeight = FontWeight.Bold, color = Color.White)
              }
              Text(
                text = state.reason,
                color = Color(0xFFFEE2E2),
                fontSize = 12.sp
              )
            }
          }
        }
        is AuthUiState.DeviceRevoked -> {
          Surface(
            color = Color(0xFF7F1D1D).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFFCA5A5))
                Text("⛔ ترخيص الجهاز ملغى (Access Denied)", fontWeight = FontWeight.Bold, color = Color.White)
              }
              Text(
                text = state.reason,
                color = Color(0xFFFEE2E2),
                fontSize = 12.sp
              )
            }
          }
        }
        is AuthUiState.AccountDisabled -> {
          Surface(
            color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFCA5A5))
              Text(
                text = "تم تعطيل حساب (${state.user.fullName}) بواسطة المشرف العام. يُرجى مراجعة إدارة المعمل.",
                color = Color(0xFFFEE2E2),
                fontSize = 12.sp
              )
            }
          }
        }
        else -> Unit
      }

      // 4. Tab Content
      when (selectedTab) {
        0 -> {
          // --- TAB 0: Private Access Sign In ---
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "تسجيل الدخول للمصرح لهم فقط",
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFF8FAFC),
                  fontSize = 15.sp
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color(0xFF047857).copy(alpha = 0.3f),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                  Text(
                    text = "🔒 وصول خاص",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              Text(
                text = "الحسابات يتم إنشاؤها وتفعيلها حصراً من قبل المشرف العام (د. عقلان). التسجيل العام معطل تماماً.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
              )

              // Username / Email Field
              OutlinedTextField(
                value = usernameOrEmailInput,
                onValueChange = { usernameOrEmailInput = it },
                label = { Text("اسم المستخدم أو البريد المعتمد") },
                placeholder = { Text("aqlan أو staff1") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF38BDF8)) },
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Text,
                  imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFF38BDF8),
                  unfocusedBorderColor = Color(0xFF475569),
                  focusedLabelColor = Color(0xFF38BDF8),
                  unfocusedLabelColor = Color(0xFF94A3B8),
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
              )

              // Password / PIN Field
              OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("كلمة المرور / الرمز السري") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF38BDF8)) },
                trailingIcon = {
                  IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                      imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                      contentDescription = if (isPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                      tint = Color(0xFF94A3B8)
                    )
                  }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Password,
                  imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                  focusManager.clearFocus()
                  if (usernameOrEmailInput.isNotBlank()) {
                    isAuthenticatingLocally = true
                    viewModel.signInWithPrivateAccount(usernameOrEmailInput, passwordInput) { success, msg ->
                      isAuthenticatingLocally = false
                      Toast.makeText(context, msg, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                      if (success) onLoginSuccess()
                    }
                  }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFF38BDF8),
                  unfocusedBorderColor = Color(0xFF475569),
                  focusedLabelColor = Color(0xFF38BDF8),
                  unfocusedLabelColor = Color(0xFF94A3B8),
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
              )

              // Forgot Password link & Super Admin indicator
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "المشرف العام: د. عقلان",
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )

                TextButton(
                  onClick = {
                    resetEmailInput = if (usernameOrEmailInput.contains("@")) usernameOrEmailInput else ClinicInfo.EMAIL
                    showForgotPasswordDialog = true
                  },
                  contentPadding = PaddingValues(0.dp)
                ) {
                  Text("نسيت كلمة المرور؟", fontSize = 12.sp, color = Color(0xFF38BDF8))
                }
              }

              // Login Button
              Button(
                onClick = {
                  focusManager.clearFocus()
                  if (usernameOrEmailInput.isBlank()) {
                    Toast.makeText(context, "يرجى إدخال اسم المستخدم أو البريد", Toast.LENGTH_SHORT).show()
                  } else {
                    isAuthenticatingLocally = true
                    viewModel.signInWithPrivateAccount(usernameOrEmailInput, passwordInput) { success, msg ->
                      isAuthenticatingLocally = false
                      Toast.makeText(context, msg, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                      if (success) onLoginSuccess()
                    }
                  }
                },
                enabled = authState !is AuthUiState.Loading && !isAuthenticatingLocally,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_login_btn")
              ) {
                if (authState is AuthUiState.Loading || isAuthenticatingLocally) {
                  CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(8.dp))
                  Text("جاري التحقق من الترخيص...")
                } else {
                  Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("تسجيل الدخول للنظام", fontWeight = FontWeight.Bold)
                }
              }

              // Divider
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
              ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                Text(
                  text = "  أو حساب المشرف عبر Google  ",
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
              }

              // Google Sign-In Button (Super Admin / Whitelisted Accounts)
              OutlinedButton(
                onClick = {
                  viewModel.signInWithGoogle(context)
                },
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_google_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.AccountCircle,
                  contentDescription = "Google",
                  tint = Color(0xFF60A5FA),
                  modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الدخول السريع عبر Google", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }

        1 -> {
          // --- TAB 1: Super Admin Fast Access PIN ---
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                shape = CircleShape,
                color = Color(0xFFD97706).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.size(56.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(28.dp)
                  )
                }
              }

              Text(
                text = "الدخول المباشر للمشرف العام (د. عقلان)",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp
              )

              Text(
                text = "خاص بالطبيب المالك للدخول السريع والفوري بكلمة المرور السريعة",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
              )

              OutlinedTextField(
                value = doctorPinInput,
                onValueChange = { doctorPinInput = it },
                label = { Text("رمز مرور المشرف العام / PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.NumberPassword,
                  imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                  focusManager.clearFocus()
                  if (viewModel.unlockAppWithPin(doctorPinInput)) {
                    Toast.makeText(context, "مرحباً د. عقلان الكامل (المشرف العام)", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                  } else {
                    Toast.makeText(context, "رمز المرور غير صحيح", Toast.LENGTH_SHORT).show()
                  }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFFF59E0B),
                  unfocusedBorderColor = Color(0xFF475569),
                  focusedLabelColor = Color(0xFFF59E0B),
                  unfocusedLabelColor = Color(0xFF94A3B8),
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("doctor_pin_input")
              )

              Button(
                onClick = {
                  focusManager.clearFocus()
                  if (viewModel.unlockAppWithPin(doctorPinInput)) {
                    Toast.makeText(context, "مرحباً د. عقلان الكامل (المشرف العام)", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                  } else {
                    Toast.makeText(context, "رمز المرور غير صحيح", Toast.LENGTH_SHORT).show()
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("doctor_pin_btn")
              ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح النظام بصلاحية Super Admin", fontWeight = FontWeight.Bold)
              }

              // Biometric option
              OutlinedButton(
                onClick = {
                  viewModel.unlockAppWithBiometric()
                  Toast.makeText(context, "تم التحقق بالبصمة الحيوية", Toast.LENGTH_SHORT).show()
                  onLoginSuccess()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("الدخول بالبصمة الحيوية (Biometric)", color = Color(0xFFE2E8F0))
              }
            }
          }
        }

        2 -> {
          // --- TAB 2: Device Hardware Licensing Card ---
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "بصمة الجهاز والترخيص Hardware ID",
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFF8FAFC),
                  fontSize = 15.sp
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = when (deviceStatus) {
                    DeviceStatus.APPROVED -> Color(0xFF047857).copy(alpha = 0.3f)
                    DeviceStatus.BLOCKED, DeviceStatus.REVOKED -> Color(0xFFB91C1C).copy(alpha = 0.3f)
                    else -> Color(0xFFD97706).copy(alpha = 0.3f)
                  },
                  border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (deviceStatus) {
                      DeviceStatus.APPROVED -> Color(0xFF10B981)
                      DeviceStatus.BLOCKED, DeviceStatus.REVOKED -> Color(0xFFEF4444)
                      else -> Color(0xFFF59E0B)
                    }
                  )
                ) {
                  Text(
                    text = deviceStatus.titleAr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (deviceStatus) {
                      DeviceStatus.APPROVED -> Color(0xFF34D399)
                      DeviceStatus.BLOCKED, DeviceStatus.REVOKED -> Color(0xFFF87171)
                      else -> Color(0xFFFBBF24)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }

              Text(
                text = "كل جهاز يحتاج إلى موافقة وترخيص مباشر من المشرف العام لضمان عدم تسريب البيانات أو الاستخدام من أجهزة غير مصرح بها.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 17.sp
              )

              // Hardware Info Box
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("معرف الجهاز (Device ID):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = currentDeviceId,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                      )
                      IconButton(
                        onClick = {
                          clipboardManager.setText(AnnotatedString(currentDeviceId))
                          Toast.makeText(context, "تم نسخ معرف الجهاز بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                      ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                      }
                    }
                  }

                  HorizontalDivider(color = Color(0xFF1E293B))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("نوع الجهاز وطرازه:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                      text = viewModel.deviceSecurityManager.getDeviceModelName(),
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = Color.White
                    )
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("نظام التشغيل:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                      text = viewModel.deviceSecurityManager.getAndroidOsVersion(),
                      fontSize = 11.sp,
                      color = Color(0xFFCBD5E1)
                    )
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("إصدار التطبيق:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                      text = "v${viewModel.deviceSecurityManager.getAppVersion()}",
                      fontSize = 11.sp,
                      color = Color(0xFF34D399),
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }

              // Server Status Check Button
              OutlinedButton(
                onClick = {
                  viewModel.refreshDeviceAuthorization { status, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (status == DeviceStatus.APPROVED) {
                      onLoginSuccess()
                    }
                  }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
              ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فحص وتحديث حالة الترخيص من السيرفر", color = Color(0xFFE2E8F0), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              }

              // WhatsApp request button to Super Admin
              Button(
                onClick = {
                  val reqMessage = """
                    🔐 *طلب اعتماد وترخيص جهاز جديد - معمل عقلان*
                    ---------------------------------
                    👨‍⚕️ المشرف العام: د. عقلان الكامل
                    📱 نوع الجهاز: ${viewModel.deviceSecurityManager.getDeviceModelName()}
                    🔑 معرف الجهاز (Hardware ID):
                    $currentDeviceId
                    📲 النظام: ${viewModel.deviceSecurityManager.getAndroidOsVersion()}
                    📦 الإصدار: v${viewModel.deviceSecurityManager.getAppVersion()}
                    ---------------------------------
                    يرجى التكرم باعتماد وتفعيل الجهاز من خلال لوحة إدارة الأجهزة والتراخيص في التطبيق.
                  """.trimIndent()
                  viewModel.cloudSyncManager.shareViaWhatsApp(context, ClinicInfo.PHONES, reqMessage)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
              ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إرسال طلب الترخيص للدكتور عبر واتساب", fontWeight = FontWeight.Bold, color = Color.White)
              }
            }
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      // Footer Security Info
      Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "🔒 نظام إدارة المعامل مغلق ومحمي حصرياً لمركز د. عقلان الكامل",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
          )
          Text(
            text = "للتواصل مع المشرف العام: ${ClinicInfo.PHONES} | ${ClinicInfo.EMAIL}",
            fontSize = 10.sp,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }

  // Forgot Password Dialog
  if (showForgotPasswordDialog) {
    AlertDialog(
      onDismissRequest = { showForgotPasswordDialog = false },
      icon = {
        Icon(Icons.Default.LockReset, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(36.dp))
      },
      title = {
        Text("استعادة كلمة المرور للمشرف", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "أدخل بريدك الإلكتروني المعتمد وسيقوم النظام بإرسال رابط آمن لإعادة تعيين كلمة المرور:",
            style = MaterialTheme.typography.bodySmall
          )

          OutlinedTextField(
            value = resetEmailInput,
            onValueChange = { resetEmailInput = it },
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (resetEmailInput.isBlank()) {
              Toast.makeText(context, "يرجى كتابة البريد الإلكتروني", Toast.LENGTH_SHORT).show()
            } else {
              isResettingPassword = true
              coroutineScope.launch {
                val result = viewModel.sendPasswordReset(resetEmailInput)
                isResettingPassword = false
                showForgotPasswordDialog = false
                if (result.isSuccess) {
                  Toast.makeText(context, "تم إرسال رابط إعادة تعيين كلمة السر إلى بريدك بنجاح", Toast.LENGTH_LONG).show()
                } else {
                  Toast.makeText(context, result.exceptionOrNull()?.message ?: "حدث خطأ", Toast.LENGTH_LONG).show()
                }
              }
            }
          },
          enabled = !isResettingPassword
        ) {
          if (isResettingPassword) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
          } else {
            Text("إرسال الرابط")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showForgotPasswordDialog = false }) {
          Text("إلغاء")
        }
      }
    )
  }
}
