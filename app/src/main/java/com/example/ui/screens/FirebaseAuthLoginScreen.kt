package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.UserRole
import com.example.network.AuthUiState
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricAuthenticator
import com.example.ui.components.AqlanLogo
import com.example.ui.components.ClinicInfo
import com.example.ui.viewmodel.DentalLabViewModel
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
  val coroutineScope = rememberCoroutineScope()

  val authState by viewModel.firebaseAuthState.collectAsState()
  val currentUser by viewModel.firebaseCurrentUser.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Login, 1: Doctor PIN, 2: Register Staff

  // Login Form States
  // لا يُملأ بريد المالك مسبقاً: كان يكشف حساب المدير لأي شخص يفتح الشاشة
  // ويترك نصف بيانات الاعتماد جاهزة للتخمين.
  var emailInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }

  // Register Form States
  var regFullName by remember { mutableStateOf("") }
  var regEmail by remember { mutableStateOf("") }
  var regPassword by remember { mutableStateOf("") }
  var regRole by remember { mutableStateOf(UserRole.STAFF) }

  // Quick Doctor PIN State
  var doctorPinInput by remember { mutableStateOf("") }

  val biometricEnabled by viewModel.biometricUnlockEnabled.collectAsState()
  val biometricAvailable = remember { BiometricAuthenticator.isAvailable(context) }

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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Official Header & Crest
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
      ) {
        // Firebase Protected Shield Badge
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF1E3A8A).copy(alpha = 0.7f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f)),
          modifier = Modifier.padding(bottom = 12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "محمي بالمصادقة السحابية (Firebase Auth)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFF1F5F9)
            )
          }
        }

        AqlanLogo(size = 72.dp)

        Spacer(Modifier.height(10.dp))

        Text(
          text = "بوابة الدخول لنظام إدارة المعامل",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          textAlign = TextAlign.Center
        )

        Text(
          text = ClinicInfo.CLINIC_NAME,
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF94A3B8),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      // 2. Navigation Tabs
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
          text = { Text("رمز الطبيب", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
          icon = { Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        // أُزيل تبويب «تسجيل موظف» من شاشة الدخول.
        //
        // كان يسمح لأي شخص — قبل أي مصادقة — بإنشاء حساب لنفسه في نظام المركز
        // والدخول مباشرة إلى بيانات المرضى والحسابات. إنشاء الحسابات أصبح
        // متاحاً لمدير النظام فقط من: الإعدادات ← إدارة المستخدمين.
      }

      Spacer(Modifier.height(16.dp))

      // 3. Error or Unauthorized Feedback Banner
      when (val state = authState) {
        is AuthUiState.Error -> {
          Surface(
            color = Color(0xFF7F1D1D).copy(alpha = 0.8f),
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
            color = Color(0xFF854D0E).copy(alpha = 0.8f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFDE047))
                Text("حساب غير مصرح له!", fontWeight = FontWeight.Bold, color = Color(0xFFFEF08A))
              }
              Text(state.message, color = Color(0xFFFEF9C3), fontSize = 12.sp)
            }
          }
        }
        else -> Unit
      }

      // 4. Tab Content
      when (selectedTab) {
        0 -> {
          // --- TAB 0: Email / Password Sign In ---
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
              Text(
                text = "تسجيل الدخول بحساب معتمد",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp
              )

              // Email Field
              OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("البريد الإلكتروني") },
                placeholder = { Text("example@gmail.com") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF94A3B8)) },
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Email,
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

              // Password Field
              OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("كلمة المرور") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8)) },
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
                  if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                    viewModel.signInWithFirebaseEmail(emailInput, passwordInput)
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

              // Forgot Password link
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "المالك: Aqlanf10@gmail.com",
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )

                TextButton(
                  onClick = {
                    resetEmailInput = emailInput
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
                  if (emailInput.isBlank() || passwordInput.isBlank()) {
                    Toast.makeText(context, "يرجى كتابة البريد وكلمة المرور", Toast.LENGTH_SHORT).show()
                  } else {
                    viewModel.signInWithFirebaseEmail(emailInput, passwordInput)
                  }
                },
                enabled = authState !is AuthUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_login_btn")
              ) {
                if (authState is AuthUiState.Loading) {
                  CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(8.dp))
                  Text("جاري التحقق والمصادقة...")
                } else {
                  Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("تسجيل الدخول المعتمد", fontWeight = FontWeight.Bold)
                }
              }

              // Divider
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
              ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                Text(
                  text = "  أو عبر خدمات Google  ",
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
              }

              // Google Sign-In Button
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
                Text("تسجيل الدخول عبر Google", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }

        1 -> {
          // --- TAB 1: Doctor Fast PIN ---
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
                text = "الدخول السريع لـ ${ClinicInfo.DOCTOR_NAME}",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp
              )

              Text(
                // أُزيل عرض رمز المرور الافتراضي ("الافتراضي: 1111") من الشاشة —
                // كان مكتوباً حرفياً فوق حقل الإدخال لأي شخص يمسك الجهاز.
                text = "أدخل رمز المرور الخاص بحسابك للدخول إلى النظام.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
              )

              OutlinedTextField(
                value = doctorPinInput,
                onValueChange = { doctorPinInput = it },
                label = { Text("رمز مرور الطبيب / PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.NumberPassword,
                  imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                  focusManager.clearFocus()
                  if (viewModel.unlockAppWithPin(doctorPinInput)) {
                    doctorPinInput = ""
                    onLoginSuccess()
                  } else {
                    doctorPinInput = ""
                    Toast.makeText(
                      context,
                      viewModel.unlockError.value.ifBlank { "رمز المرور غير صحيح" },
                      Toast.LENGTH_LONG
                    ).show()
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
                    doctorPinInput = ""
                    onLoginSuccess()
                  } else {
                    doctorPinInput = ""
                    Toast.makeText(
                      context,
                      viewModel.unlockError.value.ifBlank { "رمز المرور غير صحيح" },
                      Toast.LENGTH_LONG
                    ).show()
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("doctor_pin_btn")
              ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح النظام كطبيب مسؤول", fontWeight = FontWeight.Bold)
              }

              // --- الدخول بالبصمة ---
              //
              // الثغرة التي أُصلحت هنا كانت الأخطر في التطبيق كله: هذا الزر كان
              // يستدعي دالة تُرجع true دائماً بلا أي تحقق، ثم يفتح النظام
              // بصلاحيات مدير كاملة. أي شخص يمسك الجهاز — أو يجده مفقوداً —
              // كان يدخل إلى كل بيانات المرضى والحسابات بضغطة واحدة.
              // الآن: نافذة BiometricPrompt الحقيقية من نظام أندرويد، ولا يُفتح
              // التطبيق إلا بعد نجاح تحقق النظام، وفقط إذا فعّل المستخدم الميزة
              // مسبقاً بعد دخول ناجح برمز المرور.
              if (biometricEnabled && biometricAvailable) {
                OutlinedButton(
                  onClick = {
                    val activity = context as? FragmentActivity
                    if (activity == null) {
                      Toast.makeText(context, "تعذر فتح نافذة التحقق بالبصمة", Toast.LENGTH_SHORT).show()
                      return@OutlinedButton
                    }
                    BiometricAuthenticator.authenticate(
                      activity = activity,
                      onSuccess = {
                        if (viewModel.onBiometricAuthenticated()) {
                          onLoginSuccess()
                        } else {
                          Toast.makeText(
                            context,
                            viewModel.unlockError.value.ifBlank { "تعذر الدخول بالبصمة" },
                            Toast.LENGTH_LONG
                          ).show()
                        }
                      },
                      onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                      }
                    )
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth().testTag("biometric_btn")
                ) {
                  Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("الدخول عبر البصمة", color = Color(0xFFE2E8F0))
                }
              }
            }
          }
        }

        2 -> {
          // --- TAB 2: Register Staff Account ---
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
              Text(
                text = "تسجيل موظف أو فني جديد في المركز",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp
              )

              // Staff Full Name
              OutlinedTextField(
                value = regFullName,
                onValueChange = { regFullName = it },
                label = { Text("الاسم الكامل للموظف") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8)) },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFF38BDF8),
                  unfocusedBorderColor = Color(0xFF475569),
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("reg_fullname_input")
              )

              // Staff Email
              OutlinedTextField(
                value = regEmail,
                onValueChange = { regEmail = it },
                label = { Text("البريد الإلكتروني للموظف") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF94A3B8)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFF38BDF8),
                  unfocusedBorderColor = Color(0xFF475569),
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("reg_email_input")
              )

              // Staff Password
              OutlinedTextField(
                value = regPassword,
                onValueChange = { regPassword = it },
                label = { Text("كلمة المرور (6 خانات على الأقل)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFF38BDF8),
                  unfocusedBorderColor = Color(0xFF475569),
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("reg_password_input")
              )

              // Staff Role Selector
              Text("صلاحية الموظف في النظام:", fontSize = 12.sp, color = Color(0xFF94A3B8))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                listOf(UserRole.STAFF to "موظف استقبال / فني", UserRole.ACCOUNTANT to "محاسب مالي").forEach { (role, label) ->
                  FilterChip(
                    selected = regRole == role,
                    onClick = { regRole = role },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = Color(0xFF2563EB),
                      selectedLabelColor = Color.White
                    )
                  )
                }
              }

              // Register Button
              Button(
                onClick = {
                  Toast.makeText(
                    context,
                    "إنشاء حسابات الموظفين متاح لمدير النظام فقط من: الإعدادات ← إدارة المستخدمين",
                    Toast.LENGTH_LONG
                  ).show()
                },
                enabled = authState !is AuthUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reg_submit_btn")
              ) {
                if (authState is AuthUiState.Loading) {
                  CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                  Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("إنشاء واعتماد حساب الموظف", fontWeight = FontWeight.Bold)
                }
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
            text = "🔒 نظام إدارة المعامل محمي ومخصص حصرياً لمركز د. عقلان الكامل",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
          )
          Text(
            text = "للمساعدة والدعم الفني: ${ClinicInfo.PHONES}",
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
        Text("استعادة كلمة المرور عبر Firebase", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "أدخل بريدك الإلكتروني المعتمد وسيقوم نظام Firebase بإرسال رابط آمن لإعادة تعيين كلمة المرور فوراً:",
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
