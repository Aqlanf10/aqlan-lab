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

  // Tabs: 0: Welcome & Guide, 1: Sign In, 2: Doctor PIN, 3: Device License
  var selectedTab by remember { mutableIntStateOf(0) }

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

  // Phone Auth Dialog State
  var showPhoneAuthDialog by remember { mutableStateOf(false) }
  var phoneInput by remember { mutableStateOf("+967") }
  var phoneOtpInput by remember { mutableStateOf("") }
  var isPhoneCodeSent by remember { mutableStateOf(false) }
  var phoneVerificationId by remember { mutableStateOf("") }
  var isSendingPhoneCode by remember { mutableStateOf(false) }
  var isVerifyingPhoneCode by remember { mutableStateOf(false) }
  var phoneAuthError by remember { mutableStateOf<String?>(null) }

  // Request New Account Dialog State
  var showRequestAccountDialog by remember { mutableStateOf(false) }
  var reqFullName by remember { mutableStateOf("") }
  var reqPhone by remember { mutableStateOf("") }
  var reqRole by remember { mutableStateOf("فني معمل / فني تركيبات") }
  var reqEmailOrUsername by remember { mutableStateOf("") }
  var reqNotes by remember { mutableStateOf("") }

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
            Color(0xFF070B14),
            Color(0xFF0F172A),
            Color(0xFF131E35)
          )
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Official Header & Crest
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF0284C7).copy(alpha = 0.18f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
          modifier = Modifier.padding(bottom = 10.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = "نظام سحابي طبي مغلق ومحمي (Private Access)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFE0F2FE)
            )
          }
        }

        AqlanLogo(size = 64.dp)

        Spacer(Modifier.height(8.dp))

        Text(
          text = "مركز د. عقلان الكامل لطب وجراحة الأسنان",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          textAlign = TextAlign.Center,
          fontSize = 18.sp
        )

        Text(
          text = "المنصة المركزية لإدارة المعامل والتركيبات والحسابات",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF94A3B8),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 3.dp)
        )
      }

      // 2. Navigation Tabs (Welcome, Sign In, Doctor PIN, Device License)
      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color(0xFF1E293B),
        contentColor = Color(0xFF38BDF8),
        edgePadding = 6.dp,
        divider = {},
        modifier = Modifier
          .clip(RoundedCornerShape(14.dp))
          .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("مرحباً بك والدليل", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
          icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("تسجيل الدخول", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
          icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("رمز المشرف", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
          icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("ترخيص الجهاز", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
          icon = { Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
      }

      Spacer(Modifier.height(14.dp))

      // 3. Error / Status Banner
      when (val state = authState) {
        is AuthUiState.Error -> {
          Surface(
            color = Color(0xFF7F1D1D).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
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
            color = Color(0xFF854D0E).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFFDE047))
                Text("حساب غير مصرح له أو بانتظار الترخيص", fontWeight = FontWeight.Bold, color = Color(0xFFFEF08A), fontSize = 13.sp)
              }
              Text(state.message, color = Color(0xFFFEF9C3), fontSize = 12.sp, lineHeight = 16.sp)
              Button(
                onClick = { showRequestAccountDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
              ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("طلب اعتماد الحساب من المشرف العام الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
        is AuthUiState.DevicePendingApproval -> {
          Surface(
            color = Color(0xFF1E3A8A).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFF93C5FD))
                Text("هذا الجهاز بانتظار ترخيص المشرف العام", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
              }
              Text(
                text = if (state.isMaxDevicesExceeded) {
                  "⚠️ تم تسجيل الجهاز، ولكن الحساب وصل للحد الأقصى للأجهزة المصرح بها. يتطلب ترخيص المشرف العام (${ClinicInfo.DOCTOR_NAME}) أو إلغاء ربط جهاز قديم."
                } else {
                  "تم تسجيل طلب الترخيص للجهاز (${state.deviceId}). بانتظار اعتماد المشرف العام ${ClinicInfo.DOCTOR_NAME} من لوحة إدارة الأجهزة."
                },
                color = Color(0xFFDBEAFE),
                fontSize = 12.sp,
                lineHeight = 17.sp
              )
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                  modifier = Modifier.weight(1f).height(38.dp)
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("فحص الحالة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                  onClick = {
                    val reqMsg = """
                      🔐 *طلب اعتماد جهاز - معمل د. عقلان الكامل*
                      ---------------------------------
                      📱 الجهاز: ${viewModel.deviceSecurityManager.getDeviceModelName()}
                      🔑 معرف الجهاز: $currentDeviceId
                      يرجى التكرم باعتماد الجهاز للدخول إلى النظام.
                    """.trimIndent()
                    ClinicInfo.openWhatsApp(context, ClinicInfo.PHONE_PRIMARY, reqMsg)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.weight(1f).height(38.dp)
                ) {
                  Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("طلب بالواتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
        else -> Unit
      }

      // 4. Tab Content Switcher
      AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
          fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) togetherWith
              fadeOut(animationSpec = androidx.compose.animation.core.tween(180))
        },
        label = "AuthTabAnimation"
      ) { tabIndex ->
        when (tabIndex) {
          0 -> {
            // ==========================================
            // TAB 0: WELCOME & ONBOARDING GUIDE
            // ==========================================
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // Hero Welcome Card
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = Color(0xFF0284C7).copy(alpha = 0.2f),
                      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                      modifier = Modifier.size(42.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.MedicalServices,
                          contentDescription = null,
                          tint = Color(0xFF38BDF8),
                          modifier = Modifier.size(22.dp)
                        )
                      }
                    }
                    Column {
                      Text(
                        text = "مرحباً بكم في نظام المعمل الطبي",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                      )
                      Text(
                        text = "دليل البدء وخطوات التسجيل والاعتماد",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                      )
                    }
                  }

                  Text(
                    text = "هذا النظام منصة طبية وإدارية خاصة ومغلقة (Closed Medical Cloud) لحفظ وتتبع إرساليات تركيبات وزراعة الأسنان، حسابات المعامل، والنسخ الاحتياطي السحابي تحت إشراف الدكتور عقلان الكامل.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                  )

                  // Fast Action Buttons in Hero
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Button(
                      onClick = { selectedTab = 1 },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                      Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("تسجيل الدخول", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                      onClick = { showRequestAccountDialog = true },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                      Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("طلب حساب جديد", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                  }
                }
              }

              // Step 1: How to get an account
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = Color(0xFF0284C7),
                      modifier = Modifier.size(24.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      }
                    }
                    Text(
                      text = "كيف أحصل على حساب مستخدم جديد؟",
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFF8FAFC),
                      fontSize = 13.sp
                    )
                  }

                  Text(
                    text = "• التسجيل المفتوح معطل تلقائياً لضمان سرية السجلات المالية والطبية.\n• يتم إصدار الحسابات وصلاحيات الوصول (طبيب، فني، محاسب، استقبال) حصرياً من قبل المشرف العام (${ClinicInfo.DOCTOR_NAME}).\n• يمكنك الضغط أدناه لتقديم طلب حساب جديد مباشرة عبر واتساب.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 17.sp
                  )

                  OutlinedButton(
                    onClick = { showRequestAccountDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                  ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إرسال طلب تسجيل حساب للمشرف العام", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                  }
                }
              }

              // Step 2: Device Licensing & Hardware ID
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = Color(0xFFD97706),
                      modifier = Modifier.size(24.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      }
                    }
                    Text(
                      text = "نظام ترخيص وربط الأجهزة (Device Security)",
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFF8FAFC),
                      fontSize = 13.sp
                    )
                  }

                  Text(
                    text = "كل جهاز محمول أو لوحي يملك بصمة رقمية فريدة (Hardware ID). بمجرد فتح التطبيق لأول مرة يُصنف الجهاز كـ (قيد الانتظار) حتى يعتمده الدكتور عقلان من لوحة التراخيص.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 17.sp
                  )

                  // Device ID snippet
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Column {
                        Text("معرف جهازك الحالي:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(
                          text = currentDeviceId,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF38BDF8)
                        )
                      }
                      IconButton(
                        onClick = {
                          clipboardManager.setText(AnnotatedString(currentDeviceId))
                          Toast.makeText(context, "تم نسخ معرف الجهاز بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                      ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                      }
                    }
                  }
                }
              }

              // Step 3: Contact Channels to Dr. Aqlan
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Surface(
                      shape = CircleShape,
                      color = Color(0xFF2563EB),
                      modifier = Modifier.size(24.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      }
                    }
                    Text(
                      text = "التواصل المباشر مع إدارة المعمل",
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFF8FAFC),
                      fontSize = 13.sp
                    )
                  }

                  Text(
                    text = "للحصول على المساعدة، استعادة كلمة المرور، أو تفعيل الحسابات:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                  )

                  // Phone & WhatsApp quick actions
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    OutlinedButton(
                      onClick = { ClinicInfo.openDialer(context, ClinicInfo.PHONE_PRIMARY) },
                      shape = RoundedCornerShape(8.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                      modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                      Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("اتصال: ${ClinicInfo.PHONE_PRIMARY}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                      onClick = {
                        val helpMsg = "السلام عليكم د. عقلان الكامل، أحتاج مساعدة في تفعيل حسابي في نظام إدارة المعامل."
                        ClinicInfo.openWhatsApp(context, ClinicInfo.PHONE_PRIMARY, helpMsg)
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                      Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("محادثة واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                  }

                  // Center Info
                  Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(
                      modifier = Modifier.padding(8.dp),
                      verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                      Text("📍 ${ClinicInfo.ADDRESS}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                      Text("✉️ ${ClinicInfo.EMAIL}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                  }
                }
              }
            }
          }

          1 -> {
            // ==========================================
            // TAB 1: SIGN IN FORM
            // ==========================================
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
                  text = "أدخل اسم المستخدم المعتمد أو البريد الإلكتروني مع كلمة المرور الخاصة بك.",
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
                    text = "المشرف: د. عقلان الكامل",
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
                    text = "  أو تسجيل عبر Google  ",
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
                  modifier = Modifier.fillMaxWidth().height(46.dp).testTag("auth_google_btn")
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

                // Phone Authentication (SMS OTP) Button
                OutlinedButton(
                  onClick = {
                    showPhoneAuthDialog = true
                    phoneAuthError = null
                  },
                  shape = RoundedCornerShape(10.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D9488)),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2DD4BF)),
                  modifier = Modifier.fillMaxWidth().height(46.dp).testTag("auth_phone_btn")
                ) {
                  Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Phone Auth",
                    tint = Color(0xFF2DD4BF),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(Modifier.width(8.dp))
                  Text("تسجيل الدخول برقم الهاتف ورمز SMS", fontWeight = FontWeight.SemiBold)
                }

                // Request Account Helper in Sign In
                TextButton(
                  onClick = { showRequestAccountDialog = true },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "ليس لديك حساب معتمد؟ اضغط هنا لطلب حساب جديد",
                    fontSize = 11.sp,
                    color = Color(0xFF34D399),
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          }

          2 -> {
            // ==========================================
            // TAB 2: DOCTOR MASTER PIN FAST ACCESS
            // ==========================================
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
                  text = "خاص بالطبيب المالك للدخول السريع والفوري بكلمة المرور أو البصمة الحيوية",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFF94A3B8),
                  textAlign = TextAlign.Center
                )

                OutlinedTextField(
                  value = doctorPinInput,
                  onValueChange = { doctorPinInput = it },
                  label = { Text("رمز مرور المشرف العام / PIN") },
                  placeholder = { Text("أدخل رمز المرور السريع") },
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

          3 -> {
            // ==========================================
            // TAB 3: DEVICE HARDWARE LICENSING
            // ==========================================
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
                  text = "كل جهاز يحتاج إلى ترخيص مباشر لضمان عدم تسريب البيانات الطبية أو الاستخدام من أجهزة غير مصرح بها.",
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
                    ClinicInfo.openWhatsApp(context, ClinicInfo.PHONE_PRIMARY, reqMessage)
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
      }

      Spacer(Modifier.height(18.dp))

      // Footer Security Info & Contact Info
      Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "🔒 ${ClinicInfo.CLINIC_NAME}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
          )
          Text(
            text = "للتواصل مع المشرف العام: ${ClinicInfo.PHONES} | ${ClinicInfo.EMAIL}",
            fontSize = 10.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }

  // ==========================================
  // REQUEST NEW ACCOUNT DIALOG
  // ==========================================
  if (showRequestAccountDialog) {
    AlertDialog(
      onDismissRequest = { showRequestAccountDialog = false },
      icon = {
        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(36.dp))
      },
      title = {
        Text("طلب تسجيل حساب جديد في المركز", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 16.sp)
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "أدخل بياناتك وسيتم إنشاء رسالة طلب رسمية منسقة لإرسالها مباشرة إلى المشرف العام (${ClinicInfo.DOCTOR_NAME}):",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1)
          )

          OutlinedTextField(
            value = reqFullName,
            onValueChange = { reqFullName = it },
            label = { Text("الاسم الكامل") },
            placeholder = { Text("مثال: فني/ أحمد الشميري") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = reqPhone,
            onValueChange = { reqPhone = it },
            label = { Text("رقم الهاتف / الواتساب") },
            placeholder = { Text("مثال: 771234567") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = reqRole,
            onValueChange = { reqRole = it },
            label = { Text("الوظيفة أو الدور المطلوب") },
            placeholder = { Text("فني معمل / محاسب / استقبال / طبيب") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = reqEmailOrUsername,
            onValueChange = { reqEmailOrUsername = it },
            label = { Text("اسم المستخدم أو البريد المقترح (اختياري)") },
            placeholder = { Text("user@aqlanlab.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = reqNotes,
            onValueChange = { reqNotes = it },
            label = { Text("ملاحظات إضافية") },
            placeholder = { Text("القسم، فرع العمل، إلخ...") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (reqFullName.isBlank() || reqPhone.isBlank()) {
              Toast.makeText(context, "يرجى كتابة الاسم ورقم الهاتف", Toast.LENGTH_SHORT).show()
            } else {
              val reqMsg = """
                📋 *طلب تسجيل حساب جديد في نظام المعمل*
                ═════════════════════════════
                👨‍⚕️ المشرف العام: د. عقلان الكامل
                👤 الاسم الكامل: $reqFullName
                📞 الهاتف: $reqPhone
                🏷️ الوظيفة/الدور: $reqRole
                📧 البريد/المستخدم: ${reqEmailOrUsername.ifEmpty { "غير محدد" }}
                📱 معرف الجهاز: $currentDeviceId (${viewModel.deviceSecurityManager.getDeviceModelName()})
                📝 ملاحظات: ${reqNotes.ifEmpty { "طلب اعتماد حساب جديد" }}
                ═════════════════════════════
                يرجى التكرم بإنشاء الحساب وتحديد الصلاحيات المطلوبة.
              """.trimIndent()

              ClinicInfo.openWhatsApp(context, ClinicInfo.PHONE_PRIMARY, reqMsg)
              showRequestAccountDialog = false
              Toast.makeText(context, "تم تجهيز رسالة الطلب وإرسالها عبر واتساب", Toast.LENGTH_LONG).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
        ) {
          Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("إرسال عبر واتساب")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRequestAccountDialog = false }) {
          Text("إلغاء")
        }
      }
    )
  }

  // ==========================================
  // FORGOT PASSWORD DIALOG (FIREBASE AUTHENTICATION)
  // ==========================================
  if (showForgotPasswordDialog) {
    var dialogErrorMsg by remember { mutableStateOf<String?>(null) }
    var dialogSuccessMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = {
        if (!isResettingPassword) {
          showForgotPasswordDialog = false
          dialogErrorMsg = null
          dialogSuccessMsg = null
        }
      },
      icon = {
        Surface(
          shape = CircleShape,
          color = Color(0xFF0284C7).copy(alpha = 0.2f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
          modifier = Modifier.size(48.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.LockReset,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(26.dp)
            )
          }
        }
      },
      title = {
        Text(
          text = "استعادة كلمة المرور عبر البريد",
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          fontSize = 16.sp,
          color = Color.White
        )
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "أدخل بريدك الإلكتروني المسجل في النظام وسيقوم Firebase بإرسال رسالة تحتوي على رابط رسمي وآمن لتعيين كلمة مرور جديدة:",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1),
            lineHeight = 17.sp
          )

          // Shortcut chip for Admin email
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("بريد المشرف:", fontSize = 10.sp, color = Color(0xFF94A3B8))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFF1E293B),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
              modifier = Modifier.clickable {
                resetEmailInput = ClinicInfo.EMAIL
                dialogErrorMsg = null
              }
            ) {
              Text(
                text = ClinicInfo.EMAIL,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              )
            }
          }

          OutlinedTextField(
            value = resetEmailInput,
            onValueChange = {
              resetEmailInput = it
              dialogErrorMsg = null
              dialogSuccessMsg = null
            },
            label = { Text("البريد الإلكتروني المسجل") },
            placeholder = { Text("example@domain.com") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF38BDF8)) },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
              if (resetEmailInput.isBlank()) {
                dialogErrorMsg = "يرجى كتابة البريد الإلكتروني"
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
            modifier = Modifier.fillMaxWidth().testTag("reset_email_input")
          )

          if (dialogErrorMsg != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF7F1D1D).copy(alpha = 0.8f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                Text(
                  text = dialogErrorMsg ?: "",
                  fontSize = 11.sp,
                  color = Color(0xFFFEE2E2),
                  lineHeight = 15.sp
                )
              }
            }
          }

          if (dialogSuccessMsg != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF064E3B).copy(alpha = 0.9f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF6EE7B7), modifier = Modifier.size(16.dp))
                Text(
                  text = dialogSuccessMsg ?: "",
                  fontSize = 11.sp,
                  color = Color(0xFFECFDF5),
                  lineHeight = 15.sp
                )
              }
            }
          }

          HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 2.dp))

          // Direct Admin support fallback for staff with usernames
          Text(
            text = "إذا لم تتمكن من الوصول للبريد، يمكنك طلب إعادة تعيين الرمز السري مباشرة من د. عقلان الكامل:",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                val resetMsg = """
                  🔐 *طلب إعادة تعيين كلمة المرور / الرمز السري*
                  ---------------------------------
                  👤 الحساب / البريد: ${resetEmailInput.ifEmpty { "غير محدد" }}
                  📱 معرف الجهاز: $currentDeviceId
                  يرجى التكرم بإعادة تعيين كلمة المرور أو تزويدي برمز مؤقت.
                """.trimIndent()
                ClinicInfo.openWhatsApp(context, ClinicInfo.PHONE_PRIMARY, resetMsg)
              },
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
              modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
              Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(6.dp))
              Text("طلب إعادة التعيين عبر واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val emailToSend = resetEmailInput.trim()
            if (emailToSend.isBlank() || !emailToSend.contains("@")) {
              dialogErrorMsg = "يرجى إدخال بريد إلكتروني صحيح يحتوي على @"
            } else {
              isResettingPassword = true
              dialogErrorMsg = null
              dialogSuccessMsg = null
              coroutineScope.launch {
                val result = viewModel.sendPasswordReset(emailToSend)
                isResettingPassword = false
                if (result.isSuccess) {
                  dialogSuccessMsg = "تم إرسال رابط إعادة تعيين كلمة المرور إلى ($emailToSend). يرجى فحص صندوق الوارد ورسائل الترويج/Spam."
                  Toast.makeText(context, "تم إرسال رابط إعادة تعيين كلمة المرور بنجاح", Toast.LENGTH_LONG).show()
                } else {
                  val err = result.exceptionOrNull()?.message ?: "تعذر إرسال الرابط. تحقق من الاتصال."
                  dialogErrorMsg = err
                  Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
              }
            }
          },
          enabled = !isResettingPassword,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("send_reset_link_btn")
        ) {
          if (isResettingPassword) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("جاري الإرسال...")
          } else {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("إرسال الرابط عبر البريد", fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            if (!isResettingPassword) {
              showForgotPasswordDialog = false
              dialogErrorMsg = null
              dialogSuccessMsg = null
            }
          }
        ) {
          Text("إغلاق", color = Color(0xFF94A3B8))
        }
      },
      containerColor = Color(0xFF1E293B),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.testTag("forgot_password_dialog")
    )
  }

  // Phone Authentication (SMS OTP) Dialog
  if (showPhoneAuthDialog) {
    val activity = context as? android.app.Activity
    AlertDialog(
      onDismissRequest = {
        if (!isSendingPhoneCode && !isVerifyingPhoneCode) {
          showPhoneAuthDialog = false
          isPhoneCodeSent = false
          phoneOtpInput = ""
          phoneAuthError = null
        }
      },
      icon = {
        Icon(
          imageVector = Icons.Default.PhoneAndroid,
          contentDescription = null,
          tint = Color(0xFF2DD4BF),
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text(
          text = if (!isPhoneCodeSent) "تسجيل الدخول برقم الهاتف (SMS OTP)" else "إدخال رمز التحقق SMS",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (phoneAuthError != null) {
            Surface(
              color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = phoneAuthError ?: "",
                color = Color(0xFFFEE2E2),
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
              )
            }
          }

          if (!isPhoneCodeSent) {
            Text(
              text = "أدخل رقم هاتفك مسبوقاً بمفتاح الدولة (مثال: +967770000000 أو +966500000000) لتلقي رمز التحقق السريع عبر رسالة نصية SMS.",
              fontSize = 12.sp,
              color = Color(0xFFCBD5E1),
              lineHeight = 17.sp
            )

            OutlinedTextField(
              value = phoneInput,
              onValueChange = { phoneInput = it },
              label = { Text("رقم الهاتف الدولي", fontSize = 12.sp) },
              placeholder = { Text("+967770000000", fontSize = 12.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
              leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF2DD4BF)) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2DD4BF),
                unfocusedBorderColor = Color(0xFF475569)
              ),
              modifier = Modifier.fillMaxWidth().testTag("phone_auth_number_input")
            )
          } else {
            Text(
              text = "تم إرسال رمز التحقق المكون من 6 أرقام إلى الرقم ($phoneInput). يرجى إدخال الرمز لتسجيل الدخول فوراً.",
              fontSize = 12.sp,
              color = Color(0xFFCBD5E1),
              lineHeight = 17.sp
            )

            OutlinedTextField(
              value = phoneOtpInput,
              onValueChange = { if (it.length <= 6) phoneOtpInput = it },
              label = { Text("رمز التحقق (6 أرقام)", fontSize = 12.sp) },
              placeholder = { Text("123456", fontSize = 12.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
              leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null, tint = Color(0xFF2DD4BF)) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2DD4BF),
                unfocusedBorderColor = Color(0xFF475569)
              ),
              modifier = Modifier.fillMaxWidth().testTag("phone_auth_otp_input")
            )

            TextButton(
              onClick = {
                isPhoneCodeSent = false
                phoneOtpInput = ""
                phoneAuthError = null
              },
              modifier = Modifier.align(Alignment.End)
            ) {
              Text("تغيير رقم الهاتف أو إعادة الإرسال", fontSize = 11.sp, color = Color(0xFF38BDF8))
            }
          }
        }
      },
      confirmButton = {
        if (!isPhoneCodeSent) {
          Button(
            onClick = {
              val phone = phoneInput.trim()
              if (phone.length < 9 || !phone.startsWith("+")) {
                phoneAuthError = "يرجى إدخال رقم هاتف صحيح يبدأ بـ + ومفتاح الدولة (مثال: +967...)"
              } else if (activity == null) {
                phoneAuthError = "خدمة المصادقة بالهاتف تتطلب نشاط واجهة مستخدم نشط."
              } else {
                isSendingPhoneCode = true
                phoneAuthError = null
                viewModel.sendPhoneVerificationCode(
                  phoneNumber = phone,
                  activity = activity,
                  onCodeSent = { vId ->
                    isSendingPhoneCode = false
                    phoneVerificationId = vId
                    isPhoneCodeSent = true
                    Toast.makeText(context, "تم إرسال رمز التحقق SMS بنجاح", Toast.LENGTH_SHORT).show()
                  },
                  onError = { err ->
                    isSendingPhoneCode = false
                    phoneAuthError = err
                  },
                  onAutoVerified = {
                    isSendingPhoneCode = false
                    showPhoneAuthDialog = false
                    Toast.makeText(context, "تم التحقق التلقائي وتسجيل الدخول بنجاح", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                  }
                )
              }
            },
            enabled = !isSendingPhoneCode,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("send_phone_otp_btn")
          ) {
            if (isSendingPhoneCode) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(Modifier.width(6.dp))
              Text("جاري الإرسال...")
            } else {
              Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("إرسال رمز SMS", fontWeight = FontWeight.Bold)
            }
          }
        } else {
          Button(
            onClick = {
              val otp = phoneOtpInput.trim()
              if (otp.length < 6) {
                phoneAuthError = "يرجى إدخال الرمز المكون من 6 أرقام"
              } else {
                isVerifyingPhoneCode = true
                phoneAuthError = null
                viewModel.verifyPhoneCodeAndSignIn(phoneVerificationId, otp) { success, msg ->
                  isVerifyingPhoneCode = false
                  if (success) {
                    showPhoneAuthDialog = false
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                  } else {
                    phoneAuthError = msg
                  }
                }
              }
            },
            enabled = !isVerifyingPhoneCode,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("verify_phone_otp_btn")
          ) {
            if (isVerifyingPhoneCode) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(Modifier.width(6.dp))
              Text("جاري التحقق...")
            } else {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("تأكيد وتسجيل الدخول", fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            if (!isSendingPhoneCode && !isVerifyingPhoneCode) {
              showPhoneAuthDialog = false
              isPhoneCodeSent = false
              phoneOtpInput = ""
              phoneAuthError = null
            }
          }
        ) {
          Text("إلغاء", color = Color(0xFF94A3B8))
        }
      },
      containerColor = Color(0xFF1E293B),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.testTag("phone_auth_dialog")
    )
  }
}
