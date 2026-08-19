package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.User
import com.example.data.models.UserRole
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
  val activeUser by viewModel.activeUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var showAddDialog by remember { mutableStateOf(false) }
  var userToEdit by remember { mutableStateOf<User?>(null) }
  var userToDelete by remember { mutableStateOf<User?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "إدارة المستخدمين وكلمات المرور",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "صلاحيات الوصول وتعيين كلمات السر",
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
      if (activeUser.role == UserRole.ADMIN) {
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
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
      // Info Header Banner
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              Icons.Default.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp)
            )
            Column {
              Text(
                text = "حماية الحسابات وكلمات المرور",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "يمكنك إضافة موظفين وأطباء ومحاسبين مع تعيين كلمة سر خاصة لكل مستخدم لضبط الأذونات.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // Section Title
      item {
        Text(
          text = "المستخدمون الحاليون (${allUsers.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      // Users List
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
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
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
                        text = "الحساب الحالي",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }

                Text(
                  text = "اسم الدخول: @${user.username}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  RoleBadge(role = user.role)
                  Text(
                    // كان يعرض عدد نقاط مساوياً لطول رمز المرور الحقيقي — تسريب
                    // يقلّص مساحة التخمين. العدد ثابت الآن ولا يدل على شيء.
                    text = "• رمز المرور: ••••",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                  )
                }
              }
            }

            // Action Buttons
            if (activeUser.role == UserRole.ADMIN) {
              Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { userToEdit = user }) {
                  Icon(
                    Icons.Default.Edit,
                    contentDescription = "تعديل المستخدم",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }

                if (user.id != 1L) {
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
        }
      }
    }
  }

  // Add User Dialog
  if (showAddDialog) {
    UserFormDialog(
      user = null,
      onDismiss = { showAddDialog = false },
      onSave = { username, fullName, role, pin, color ->
        viewModel.addUser(username, fullName, role, pin, color) { success, message ->
          android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
          if (success) showAddDialog = false
        }
      }
    )
  }

  // Edit User Dialog
  if (userToEdit != null) {
    UserFormDialog(
      user = userToEdit,
      onDismiss = { userToEdit = null },
      onSave = { username, fullName, role, pin, color ->
        userToEdit?.let { existing ->
          viewModel.updateUserWithOptionalPin(
            existing.copy(
              username = username,
              fullName = fullName,
              role = role,
              avatarColor = color
            ),
            newPin = pin
          ) { success, message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            if (success) userToEdit = null
          }
        }
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
            userToDelete?.let { target ->
              viewModel.deleteUser(target) { _, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
              }
            }
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
}

@Composable
fun UserFormDialog(
  user: User?,
  onDismiss: () -> Unit,
  onSave: (username: String, fullName: String, role: UserRole, pin: String, color: Long) -> Unit
) {
  var username by remember(user) { mutableStateOf(user?.username ?: "") }
  var fullName by remember(user) { mutableStateOf(user?.fullName ?: "") }
  var role by remember(user) { mutableStateOf(user?.role ?: UserRole.STAFF) }
  // لا يُملأ رمز المرور الحالي مسبقاً: القيمة المخزنة تجزئة وليست رمزاً، وحتى
  // لو كانت رمزاً فإن عرضه في حقل قابل للإظهار تسريب. الحقل الفارغ عند التعديل
  // يعني «أبقِ الرمز الحالي كما هو».
  var pinCode by remember(user) { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }

  val avatarColors = listOf(
    0xFF00687A, // Teal
    0xFF1B5E20, // Green
    0xFFB71C1C, // Red
    0xFF4A148C, // Purple
    0xFFE65100, // Orange
    0xFF0D47A1, // Blue
    0xFF37474F  // Grey
  )
  var selectedColor by remember(user) { mutableStateOf(user?.avatarColor ?: avatarColors.first()) }

  var errorMessage by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (user == null) "إضافة مستخدم جديد" else "تعديل بيانات المستخدم",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
          label = { Text("الاسم الكامل (مثال: د. علي السعيد)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = username,
          onValueChange = { username = it; errorMessage = "" },
          label = { Text("اسم المستخدم للدخول (مثال: ali_dentist)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = pinCode,
          onValueChange = { pinCode = it; errorMessage = "" },
          label = {
            Text(if (user == null) "رمز المرور (٤ أرقام على الأقل)" else "رمز مرور جديد (اتركه فارغاً للإبقاء على الحالي)")
          },
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
        Text("الدور والصلاحيات:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          UserRole.values().forEach { r ->
            val isSelected = role == r
            FilterChip(
              selected = isSelected,
              onClick = { role = r },
              label = { Text(r.titleAr, fontSize = 12.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }

        // Avatar Color Picker
        Text("لون الحساب (Avatar Color):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          avatarColors.forEach { colorValue ->
            val isSelected = selectedColor == colorValue
            Box(
              modifier = Modifier
                .size(32.dp)
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
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
            errorMessage = "يرجى كتابة الاسم الكامل للمستخدم"
            return@Button
          }
          if (username.isBlank()) {
            errorMessage = "يرجى إدخال اسم المستخدم"
            return@Button
          }
          if (user == null && pinCode.isBlank()) {
            errorMessage = "يرجى إدخال رمز المرور"
            return@Button
          }
          onSave(username, fullName, role, pinCode, selectedColor)
        }
      ) {
        Text("حفظ المستخدم")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء")
      }
    }
  )
}
