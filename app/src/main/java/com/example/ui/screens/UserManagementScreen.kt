package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.DeviceBinding
import com.example.data.models.DeviceStatus
import com.example.data.models.User
import com.example.data.models.UserRole
import com.example.ui.components.ClinicInfo
import com.example.ui.components.DateUtils
import com.example.ui.components.RoleBadge
import com.example.ui.viewmodel.DentalLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
  viewModel: DentalLabViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  val activeUser by viewModel.activeUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val allDevices by viewModel.allDevices.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Users, 1: Devices

  var showAddDialog by remember { mutableStateOf(false) }
  var userToEdit by remember { mutableStateOf<User?>(null) }
  var userToDelete by remember { mutableStateOf<User?>(null) }
  var deviceToDelete by remember { mutableStateOf<DeviceBinding?>(null) }

  val isSuperAdmin = activeUser.role == UserRole.SUPER_ADMIN || activeUser.role == UserRole.ADMIN

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "لوحة التحكم بالأمان والتراخيص",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "إشراف: ${ClinicInfo.DOCTOR_NAME} (Super Admin)",
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
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    floatingActionButton = {
      if (isSuperAdmin && selectedTab == 0) {
        FloatingActionButton(
          onClick = { showAddDialog = true },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("add_user_fab")
        ) {
          Icon(Icons.Default.PersonAdd, contentDescription = "إضافة مستخدم جديد")
        }
      }
    },
    modifier = modifier
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Tab Row
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("المستخدمون والحسابات (${allUsers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
          icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("الأجهزة المرخصة (${allDevices.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
          icon = { Icon(Icons.Default.PhonelinkLock, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
      ) {
        if (selectedTab == 0) {
          // --- TAB 0: USERS MANAGEMENT ---
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Icon(
                  Icons.Default.AdminPanelSettings,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(32.dp)
                )
                Column {
                  Text(
                    text = "نظام الوصول الخاص المغلق (RBAC)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "فقط المشرف العام يملك صلاحية إنشاء المستخدمين وتحديد كلمات المرور وتعطيل الحسابات فوراً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          items(allUsers, key = { it.id }) { user ->
            val isCurrent = user.id == activeUser.id
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
              ),
              border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
              else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    // Avatar
                    Box(
                      modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(user.avatarColor)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = user.fullName.take(1),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Text(
                          text = user.fullName,
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold
                        )
                        if (isCurrent) {
                          Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                          ) {
                            Text(
                              text = "أنت",
                              color = MaterialTheme.colorScheme.onPrimary,
                              fontSize = 9.sp,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                          }
                        }
                      }

                      Text(
                        text = "اسم الدخول: @${user.username} ${if (user.email.isNotEmpty()) "• ${user.email}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        RoleBadge(role = user.role)
                        Text(
                          text = "• كلمة السر: ${"•".repeat(user.pinCode.length.coerceAtLeast(4))}",
                          style = MaterialTheme.typography.labelSmall,
                          color = Color.Gray
                        )
                      }
                    }
                  }

                  // Super Admin Actions
                  if (isSuperAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                      IconButton(onClick = { userToEdit = user }) {
                        Icon(
                          Icons.Default.Edit,
                          contentDescription = "تعديل المستخدم",
                          tint = MaterialTheme.colorScheme.primary
                        )
                      }

                      if (user.id != 1L && user.role != UserRole.SUPER_ADMIN) {
                        IconButton(onClick = { userToDelete = user }) {
                          Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "حذف المستخدم",
                            tint = MaterialTheme.colorScheme.error
                          )
                        }
                      }
                    }
                  }
                }

                // Account Activation Switch
                if (isSuperAdmin && user.role != UserRole.SUPER_ADMIN) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Icon(
                        imageVector = if (user.isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (user.isActive) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                      )
                      Text(
                        text = if (user.isActive) "الحساب مفعل ومصرح له" else "الحساب معطل وممنوع من الدخول",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (user.isActive) Color(0xFF10B981) else Color(0xFFEF4444)
                      )
                    }

                    Switch(
                      checked = user.isActive,
                      onCheckedChange = { isChecked ->
                        viewModel.updateUser(user.copy(isActive = isChecked))
                        Toast.makeText(context, if (isChecked) "تم تفعيل حساب ${user.fullName}" else "تم تعطيل حساب ${user.fullName}", Toast.LENGTH_SHORT).show()
                      }
                    )
                  }
                }
              }
            }
          }
        } else {
          // --- TAB 1: DEVICE BINDING MANAGEMENT ---
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Icon(
                  Icons.Default.PhonelinkLock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(32.dp)
                )
                Column {
                  Text(
                    text = "إدارة تراخيص الأجهزة (Device Fingerprinting)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "الأجهزة المعتمدة فقط يمكنها الاتصال بقاعدة البيانات. يمكنك اعتماد أي جهاز جديد بضغطة زر أو حظره فوراً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          if (allDevices.isEmpty()) {
            item {
              Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "لا توجد أجهزة مسجلة حالياً.",
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          items(allDevices, key = { it.deviceId }) { device ->
            val isCurrentThisDevice = device.deviceId == viewModel.currentDeviceId
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isCurrentThisDevice) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
              ),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when (device.status) {
                  DeviceStatus.APPROVED -> Color(0xFF10B981)
                  DeviceStatus.BLOCKED, DeviceStatus.REVOKED -> Color(0xFFEF4444)
                  else -> Color(0xFFF59E0B)
                }
              )
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                      imageVector = Icons.Default.Smartphone,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(24.dp)
                    )
                    Column {
                      Text(
                        text = device.deviceModel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "المستخدم: ${device.userName} (${device.userRole.titleAr})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (device.status) {
                      DeviceStatus.APPROVED -> Color(0xFF10B981).copy(alpha = 0.15f)
                      DeviceStatus.BLOCKED, DeviceStatus.REVOKED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                      else -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                    }
                  ) {
                    Text(
                      text = device.status.titleAr,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = when (device.status) {
                        DeviceStatus.APPROVED -> Color(0xFF10B981)
                        DeviceStatus.BLOCKED, DeviceStatus.REVOKED -> Color(0xFFEF4444)
                        else -> Color(0xFFD97706)
                      },
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("معرف الجهاز (Hardware ID):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = device.deviceId,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                      onClick = {
                        clipboardManager.setText(AnnotatedString(device.deviceId))
                        Toast.makeText(context, "تم نسخ معرف الجهاز", Toast.LENGTH_SHORT).show()
                      },
                      modifier = Modifier.size(24.dp)
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("نظام التشغيل والإصدار:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(text = "${device.osVersion} • v${device.appVersion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                if (device.notes.isNotEmpty()) {
                  Text(
                    text = "ملاحظات: ${device.notes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                // Super Admin Action Buttons on Device
                if (isSuperAdmin) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    if (device.status != DeviceStatus.APPROVED) {
                      Button(
                        onClick = {
                          viewModel.approveDevice(device.deviceId)
                          Toast.makeText(context, "تم اعتماد وترخيص الجهاز بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                      ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("اعتماد الجهاز", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }
                    }

                    if (device.status != DeviceStatus.BLOCKED) {
                      OutlinedButton(
                        onClick = {
                          viewModel.blockDevice(device.deviceId)
                          Toast.makeText(context, "تم حظر الجهاز", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                      ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("حظر الجهاز", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }
                    }

                    IconButton(
                      onClick = { deviceToDelete = device },
                      modifier = Modifier.size(36.dp)
                    ) {
                      Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الترخيص", tint = MaterialTheme.colorScheme.error)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Add User Dialog
  if (showAddDialog) {
    UserFormDialog(
      user = null,
      onDismiss = { showAddDialog = false },
      onSave = { username, fullName, email, role, pin, color ->
        viewModel.addUser(username, fullName, role, pin, color)
        showAddDialog = false
        Toast.makeText(context, "تم إضافة المستخدم ($fullName) بنجاح", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // Edit User Dialog
  if (userToEdit != null) {
    UserFormDialog(
      user = userToEdit,
      onDismiss = { userToEdit = null },
      onSave = { username, fullName, email, role, pin, color ->
        userToEdit?.let { existing ->
          viewModel.updateUser(
            existing.copy(
              username = username,
              fullName = fullName,
              email = email,
              role = role,
              pinCode = pin,
              avatarColor = color
            )
          )
        }
        userToEdit = null
        Toast.makeText(context, "تم تحديث بيانات المستخدم", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // Delete User Confirmation Dialog
  if (userToDelete != null) {
    AlertDialog(
      onDismissRequest = { userToDelete = null },
      title = { Text("حذف المستخدم") },
      text = { Text("هل أنت متأكد من حذف حساب المستخدم (${userToDelete?.fullName})؟ لن يتمكن من تسجيل الدخول بعد الآن.") },
      confirmButton = {
        Button(
          onClick = {
            userToDelete?.let { viewModel.deleteUser(it) }
            userToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("تأكيد الحذف")
        }
      },
      dismissButton = {
        TextButton(onClick = { userToDelete = null }) {
          Text("إلغاء")
        }
      }
    )
  }

  // Delete Device Confirmation Dialog
  if (deviceToDelete != null) {
    AlertDialog(
      onDismissRequest = { deviceToDelete = null },
      title = { Text("إلغاء ترخيص الجهاز") },
      text = { Text("هل أنت متأكد من حذف ترخيص الجهاز (${deviceToDelete?.deviceModel})؟ سيتم منعه من الدخول.") },
      confirmButton = {
        Button(
          onClick = {
            deviceToDelete?.let { viewModel.deleteDevice(it) }
            deviceToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("تأكيد الحذف")
        }
      },
      dismissButton = {
        TextButton(onClick = { deviceToDelete = null }) {
          Text("إلغاء")
        }
      }
    )
  }
}

@Composable
fun UserFormDialog(
  user: User?,
  onDismiss: () -> Unit,
  onSave: (username: String, fullName: String, email: String, role: UserRole, pin: String, color: Long) -> Unit
) {
  var username by remember(user) { mutableStateOf(user?.username ?: "") }
  var fullName by remember(user) { mutableStateOf(user?.fullName ?: "") }
  var email by remember(user) { mutableStateOf(user?.email ?: "") }
  var role by remember(user) { mutableStateOf(user?.role ?: UserRole.STAFF) }
  var pinCode by remember(user) { mutableStateOf(user?.pinCode ?: "") }
  var isPasswordVisible by remember { mutableStateOf(false) }

  val avatarColors = listOf(
    0xFFD32F2F, // Red
    0xFF1976D2, // Blue
    0xFF388E3C, // Green
    0xFF7B1FA2, // Purple
    0xFFF57C00, // Orange
    0xFF0097A7, // Teal
    0xFF455A64  // Slate
  )
  var selectedColor by remember(user) { mutableStateOf(user?.avatarColor ?: avatarColors.first()) }
  var errorMessage by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (user == null) "إضافة مستخدم معتمد جديد" else "تعديل بيانات المستخدم والترخيص",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (errorMessage.isNotEmpty()) {
          Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
          )
        }

        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it; errorMessage = "" },
          label = { Text("الاسم الكامل (مثال: مروة العريقي)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = username,
          onValueChange = { username = it; errorMessage = "" },
          label = { Text("اسم المستخدم للدخول (مثال: staff1)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("البريد الإلكتروني (اختياري للربط السحابي)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = pinCode,
          onValueChange = { pinCode = it; errorMessage = "" },
          label = { Text("كلمة المرور / الرمز السري (PIN)") },
          singleLine = true,
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null
              )
            }
          },
          modifier = Modifier.fillMaxWidth()
        )

        // Role Selector
        Text("الصلاحية والدور في المعمل:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(UserRole.STAFF, UserRole.ACCOUNTANT, UserRole.TECHNICIAN, UserRole.ADMIN).forEach { r ->
            FilterChip(
              selected = role == r,
              onClick = { role = r },
              label = { Text(r.titleAr, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
          }
        }

        // Avatar Color Picker
        Text("لون المعرف (Avatar Color):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          avatarColors.forEach { colorValue ->
            val isSelected = selectedColor == colorValue
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(colorValue))
                .clickable { selectedColor = colorValue }
                .then(
                  if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                  else Modifier
                ),
              contentAlignment = Alignment.Center
            ) {
              if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (fullName.isBlank()) {
            errorMessage = "يرجى كتابة الاسم الكامل"
            return@Button
          }
          if (username.isBlank()) {
            errorMessage = "يرجى إدخال اسم المستخدم"
            return@Button
          }
          if (pinCode.isBlank()) {
            errorMessage = "يرجى إدخال كلمة المرور"
            return@Button
          }
          onSave(username, fullName, email, role, pinCode, selectedColor)
        }
      ) {
        Text("حفظ الحساب")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}
