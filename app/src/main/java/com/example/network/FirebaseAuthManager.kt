package com.example.network

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.models.User
import com.example.data.models.UserRole
import com.example.ui.components.ClinicInfo
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthUiState {
  object Idle : AuthUiState()
  object Loading : AuthUiState()
  data class Success(val user: FirebaseUser?, val appUser: User, val isDoctorOwner: Boolean) : AuthUiState()
  data class Unauthorized(val email: String, val message: String) : AuthUiState()
  data class Error(val message: String) : AuthUiState()
}

class FirebaseAuthManager(
  private val context: Context,
  private val coroutineScope: CoroutineScope
) {
  private val TAG = "FirebaseAuthManager"

  // Primary Doctor Owner Email
  val MASTER_DOCTOR_EMAIL = ClinicInfo.EMAIL // "Aqlanf10@gmail.com"

  private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
  val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

  private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  private val _isAuthorized = MutableStateFlow(false)
  val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

  private var firebaseAuth: FirebaseAuth? = null
  private var credentialManager: CredentialManager = CredentialManager.create(context)

  // قائمة بيضاء صريحة بالبُرد المصرح لها. المطابقة على البريد الكامل فقط —
  // لا مطابقة جزئية ولا اشتقاق دور من نص البريد.
  private val defaultAuthorizedEmails = setOf(
    MASTER_DOCTOR_EMAIL.lowercase(),
    "aqlanf10@gmail.com",
    "aqlan.center@gmail.com",
    "marwa.reception@aqlan.com",
    "omar.accountant@aqlan.com"
  )

  init {
    initFirebaseAuth()
  }

  private fun initFirebaseAuth() {
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        firebaseAuth = FirebaseAuth.getInstance()
        val current = firebaseAuth?.currentUser
        _currentUser.value = current
        if (current != null && current.email != null) {
          checkAndSetAuthorizedUser(current)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firebase Auth not available locally: ${e.message}")
    }
  }

  /**
   * تحديد ما إذا كان حساب Firebase مصرحاً له.
   *
   * الثغرة التي أُصلحت هنا (تصعيد صلاحيات):
   * كان الشرط `email.contains("aqlan")` يمنح دور **مدير النظام** لأي بريد يحتوي
   * على كلمة "aqlan" في أي موضع — مثل `aqlan@gmail.com` أو
   * `attacker.aqlan@example.com` أو `xaqlanx@mail.ru`. وبما أن شاشة الدخول كانت
   * تسمح بالتسجيل الذاتي المفتوح، كان بإمكان أي شخص إنشاء بريد بهذا الشكل خلال
   * دقيقة والدخول كمالك المركز إلى كل بيانات المرضى والحسابات المالية.
   *
   * الآن: المطابقة على البريد الكامل تماماً مقابل قائمة بيضاء صريحة، ولا يُمنح
   * دور من نص البريد إطلاقاً — الدور الفعلي يُقرأ من حساب المستخدم المسجّل في
   * قاعدة بيانات المركز (انظر `DentalLabViewModel.bindFirebaseSessionToLocalUser`).
   */
  private fun checkAndSetAuthorizedUser(fbUser: FirebaseUser) {
    val email = fbUser.email?.trim()?.lowercase() ?: ""
    val isOwner = email == MASTER_DOCTOR_EMAIL.trim().lowercase()
    val isAllowed = defaultAuthorizedEmails.contains(email)

    if (isAllowed) {
      _isAuthorized.value = true
      val appUser = User(
        id = 0L,
        username = email.substringBefore("@"),
        fullName = fbUser.displayName ?: email.substringBefore("@"),
        // الدور الفعلي يأتي من قاعدة بيانات المركز، لا من البريد.
        role = UserRole.STAFF,
        pinHash = ""
      )
      _authState.value = AuthUiState.Success(fbUser, appUser, isOwner)
    } else {
      _isAuthorized.value = false
      firebaseAuth?.signOut()
      _authState.value = AuthUiState.Unauthorized(
        email = email,
        message = "هذا الحساب ($email) غير مصرح له بالوصول إلى نظام إدارة المعامل لمركز الدكتور عقلان الكامل. يرجى مراجعة إدارة المركز لإضافة صلاحيتك."
      )
    }
  }

  suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
    _authState.value = AuthUiState.Loading
    try {
      val auth = firebaseAuth ?: FirebaseAuth.getInstance()
      val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
      val user = authResult.user
      _currentUser.value = user

      if (user != null) {
        withContext(Dispatchers.Main) {
          checkAndSetAuthorizedUser(user)
        }
        Result.success(user)
      } else {
        _authState.value = AuthUiState.Error("فشل تسجيل الدخول: المستخدم غير موجود")
        Result.failure(Exception("User is null"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Email Sign-In error", e)
      val errorMsg = mapFirebaseError(e)
      _authState.value = AuthUiState.Error(errorMsg)
      Result.failure(Exception(errorMsg))
    }
  }

  suspend fun registerStaffWithEmail(
    email: String,
    pass: String,
    fullName: String,
    role: UserRole = UserRole.STAFF
  ): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
    _authState.value = AuthUiState.Loading
    try {
      val auth = firebaseAuth ?: FirebaseAuth.getInstance()
      val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
      val user = authResult.user

      if (user != null) {
        // Update display name
        val profileUpdates = UserProfileChangeRequest.Builder()
          .setDisplayName(fullName.trim())
          .build()
        user.updateProfile(profileUpdates).await()
        _currentUser.value = user

        // Save staff record to Firestore under clinic partition
        try {
          val db = FirebaseFirestore.getInstance()
          val staffDoc = mapOf(
            "email" to email.trim().lowercase(),
            "fullName" to fullName.trim(),
            "role" to role.name,
            "clinicId" to CloudSyncManager.DEFAULT_CLINIC_ID,
            "createdAt" to System.currentTimeMillis()
          )
          db.collection("clinics")
            .document(CloudSyncManager.DEFAULT_CLINIC_ID)
            .collection("staff")
            .document(user.uid)
            .set(staffDoc, SetOptions.merge())
        } catch (ignored: Exception) {
          Log.w(TAG, "Firestore staff sync ignored: ${ignored.message}")
        }

        withContext(Dispatchers.Main) {
          checkAndSetAuthorizedUser(user)
        }
        Result.success(user)
      } else {
        _authState.value = AuthUiState.Error("تعذر إنشاء حساب الموظف")
        Result.failure(Exception("Registration failed"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Staff registration error", e)
      val errorMsg = mapFirebaseError(e)
      _authState.value = AuthUiState.Error(errorMsg)
      Result.failure(Exception(errorMsg))
    }
  }

  suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
    _authState.value = AuthUiState.Loading
    try {
      // معرّف عميل Google كان قيمة وهمية ("...-placeholder.apps.googleusercontent.com")
      // مكتوبة داخل الكود، أي أن زر «تسجيل الدخول عبر Google» لم يكن ليعمل أبداً
      // على أي جهاز — يفشل بخطأ غامض. أصبح يُقرأ من موارد التطبيق، ويُرفض بوضوح
      // إذا لم يُضبط بعد.
      val webClientId = context.getString(com.example.R.string.google_web_client_id)
      if (webClientId.isBlank() || webClientId.contains("placeholder")) {
        val message = "تسجيل الدخول عبر Google غير مهيأ على هذا التطبيق. استخدم البريد وكلمة المرور أو رمز المرور."
        _authState.value = AuthUiState.Error(message)
        return@withContext Result.failure(IllegalStateException(message))
      }

      val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

      val result = credentialManager.getCredential(
        request = request,
        context = activityContext
      )

      val credential = result.credential
      if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)

        val auth = firebaseAuth ?: FirebaseAuth.getInstance()
        val authResult = auth.signInWithCredential(authCredential).await()
        val user = authResult.user
        _currentUser.value = user

        if (user != null) {
          withContext(Dispatchers.Main) {
            checkAndSetAuthorizedUser(user)
          }
          Result.success(user)
        } else {
          _authState.value = AuthUiState.Error("تعذر الحصول على بيانات المستخدم من Google")
          Result.failure(Exception("User is null"))
        }
      } else {
        _authState.value = AuthUiState.Error("نوع بيانات الاعتماد غير مدعوم")
        Result.failure(Exception("Unsupported credential type"))
      }
    } catch (e: GetCredentialCancellationException) {
      _authState.value = AuthUiState.Idle
      Result.failure(e)
    } catch (e: Exception) {
      Log.e(TAG, "Google Sign-In error", e)
      // If Google sign in fails on emulator without Play Services, provide informative message
      val message = if (e.message?.contains("Play Services", ignoreCase = true) == true ||
        e.message?.contains("16", ignoreCase = true) == true) {
        "خدمات Google Play غير مهيأة للمصادقة التلقائية على هذا الجهاز. يمكنك تسجيل الدخول باستخدام البريد وكلمة المرور أو رمز الطبيب."
      } else {
        mapFirebaseError(e)
      }
      _authState.value = AuthUiState.Error(message)
      Result.failure(Exception(message))
    }
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val auth = firebaseAuth ?: FirebaseAuth.getInstance()
      auth.sendPasswordResetEmail(email.trim()).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Password reset error", e)
      Result.failure(Exception(mapFirebaseError(e)))
    }
  }

  fun signOut() {
    try {
      firebaseAuth?.signOut()
      _currentUser.value = null
      _isAuthorized.value = false
      _authState.value = AuthUiState.Idle
    } catch (e: Exception) {
      Log.e(TAG, "Sign out error", e)
    }
  }

  fun resetAuthState() {
    _authState.value = AuthUiState.Idle
  }

  private fun mapFirebaseError(e: Exception): String {
    val msg = e.message ?: ""
    return when {
      msg.contains("user-not-found", ignoreCase = true) || msg.contains("invalid-credential", ignoreCase = true) ->
        "البريد الإلكتروني أو كلمة المرور غير صحيحة"
      msg.contains("wrong-password", ignoreCase = true) ->
        "كلمة المرور غير صحيحة"
      msg.contains("email-already-in-use", ignoreCase = true) ->
        "هذا البريد الإلكتروني مسجل مسبقاً في النظام"
      msg.contains("weak-password", ignoreCase = true) ->
        "كلمة المرور ضعيفة (يجب أن تتكون من 6 أحرف/أرقام على الأقل)"
      msg.contains("invalid-email", ignoreCase = true) ->
        "صيغة البريد الإلكتروني غير صحيحة"
      msg.contains("network", ignoreCase = true) ->
        "تعذر الاتصال بالشبكة! يرجى التحقق من اتصال الإنترنت"
      msg.contains("too-many-requests", ignoreCase = true) ->
        "تم حظر المحاولات مؤقتاً بسبب تكرار المحاولات الخاطئة. يرجى المحاولة لاحقاً"
      else -> msg.ifEmpty { "حدث خطأ أثناء المصادقة عبر Firebase" }
    }
  }
}
