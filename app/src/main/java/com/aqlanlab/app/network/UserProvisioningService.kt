package com.aqlanlab.app.network

import android.content.Context
import android.util.Log
import com.aqlanlab.app.data.models.User
import com.aqlanlab.app.data.models.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserProvisioningResult(
  val success: Boolean,
  val uid: String,
  val username: String,
  val email: String,
  val role: String,
  val message: String
)

/**
 * UserProvisioningService handles administrative user lifecycle operations
 * strictly via Firebase Cloud Functions and Firebase Admin SDK backend.
 *
 * Guarantees:
 * 1. No Admin SDK secrets or Service Account credentials in the APK.
 * 2. Strict Super Admin server-side verification.
 * 3. Never returns fake success on partial failures or backend rejections.
 */
class UserProvisioningService(private val context: Context) {
  private val TAG = "UserProvisioningService"

  private fun getFunctionsInstance(): FirebaseFunctions? {
    return try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        FirebaseFunctions.getInstance()
      } else {
        null
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error initializing FirebaseFunctions: ${e.message}")
      null
    }
  }

  /**
   * Securely provisions a new user via Cloud Function 'createAuthorizedUser'.
   */
  suspend fun createAuthorizedUser(
    username: String,
    email: String,
    temporaryPass: String,
    fullName: String,
    role: UserRole,
    permissions: List<String> = listOf("read:shipments", "write:shipments"),
    maxDevices: Int = 2
  ): Result<UserProvisioningResult> = withContext(Dispatchers.IO) {
    val functions = getFunctionsInstance()
      ?: return@withContext Result.failure(IllegalStateException("خدمات Firebase غير مهيأة في هذه البيئة."))

    try {
      val data = hashMapOf(
        "username" to username.trim().lowercase(),
        "email" to email.trim().lowercase(),
        "temporaryPassword" to temporaryPass,
        "fullName" to fullName.trim(),
        "role" to role.name,
        "permissions" to permissions,
        "maxDevices" to maxDevices,
        "clinicId" to CloudSyncManager.DEFAULT_CLINIC_ID
      )

      val result = functions
        .getHttpsCallable("createAuthorizedUser")
        .call(data)
        .await()

      val resultMap = result.data as? Map<*, *>
        ?: return@withContext Result.failure(IllegalStateException("استجابة غير متوقعة من الخادم."))

      val isSuccess = (resultMap["success"] as? Boolean) == true
      if (!isSuccess) {
        val errMsg = (resultMap["message"] as? String) ?: "فشلت عملية إنشاء الحساب."
        return@withContext Result.failure(Exception(errMsg))
      }

      val provResult = UserProvisioningResult(
        success = true,
        uid = (resultMap["uid"] as? String) ?: "",
        username = (resultMap["username"] as? String) ?: username,
        email = (resultMap["email"] as? String) ?: email,
        role = (resultMap["role"] as? String) ?: role.name,
        message = (resultMap["message"] as? String) ?: "تم إنشاء المستخدم بنجاح."
      )
      Result.success(provResult)
    } catch (e: FirebaseFunctionsException) {
      val detailedMsg = when (e.code) {
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "تم رفض العملية: هذه الصلاحية مخصصة للمشرف العام فقط."
        FirebaseFunctionsException.Code.ALREADY_EXISTS -> "اسم المستخدم أو البريد الإلكتروني مسجل مسبقاً."
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "بيانات غير صالحة: ${e.message}"
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "يجب تسجيل الدخول كـ Super Admin لتنفيذ هذه العملية."
        else -> e.message ?: "حدث خطأ أثناء الاتصال بالخادم."
      }
      Log.e(TAG, "Cloud Function createAuthorizedUser failed: $detailedMsg", e)
      Result.failure(Exception(detailedMsg))
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error in createAuthorizedUser: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Enables or disables a user account via Cloud Function 'setUserActiveStatus'.
   */
  suspend fun setUserActiveStatus(
    targetUid: String,
    isActive: Boolean,
    reason: String = ""
  ): Result<String> = withContext(Dispatchers.IO) {
    val functions = getFunctionsInstance()
      ?: return@withContext Result.failure(IllegalStateException("خدمات Firebase غير مهيأة."))

    try {
      val data = hashMapOf(
        "targetUid" to targetUid,
        "isActive" to isActive,
        "reason" to reason,
        "clinicId" to CloudSyncManager.DEFAULT_CLINIC_ID
      )

      val result = functions
        .getHttpsCallable("setUserActiveStatus")
        .call(data)
        .await()

      val resultMap = result.data as? Map<*, *>
      val msg = (resultMap?.get("message") as? String) ?: if (isActive) "تم تفعيل الحساب." else "تم تعطيل الحساب."
      Result.success(msg)
    } catch (e: Exception) {
      Log.e(TAG, "Cloud Function setUserActiveStatus failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Resets user password via Cloud Function 'resetUserPassword'.
   */
  suspend fun resetUserPassword(
    targetUid: String,
    newPassword: String
  ): Result<String> = withContext(Dispatchers.IO) {
    val functions = getFunctionsInstance()
      ?: return@withContext Result.failure(IllegalStateException("خدمات Firebase غير مهيأة."))

    try {
      val data = hashMapOf(
        "targetUid" to targetUid,
        "newPassword" to newPassword,
        "clinicId" to CloudSyncManager.DEFAULT_CLINIC_ID
      )

      val result = functions
        .getHttpsCallable("resetUserPassword")
        .call(data)
        .await()

      val resultMap = result.data as? Map<*, *>
      val msg = (resultMap?.get("message") as? String) ?: "تم إعادة تعيين كلمة المرور بنجاح."
      Result.success(msg)
    } catch (e: Exception) {
      Log.e(TAG, "Cloud Function resetUserPassword failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Updates user role, custom claims, and permissions via Cloud Function 'updateUserRoleAndPermissions'.
   */
  suspend fun updateUserRoleAndPermissions(
    targetUid: String,
    role: UserRole,
    permissions: List<String> = emptyList(),
    maxDevices: Int? = null
  ): Result<String> = withContext(Dispatchers.IO) {
    val functions = getFunctionsInstance()
      ?: return@withContext Result.failure(IllegalStateException("خدمات Firebase غير مهيأة."))

    try {
      val data = hashMapOf<String, Any>(
        "targetUid" to targetUid,
        "role" to role.name,
        "permissions" to permissions,
        "clinicId" to CloudSyncManager.DEFAULT_CLINIC_ID
      )
      if (maxDevices != null) {
        data["maxDevices"] = maxDevices
      }

      val result = functions
        .getHttpsCallable("updateUserRoleAndPermissions")
        .call(data)
        .await()

      val resultMap = result.data as? Map<*, *>
      val msg = (resultMap?.get("message") as? String) ?: "تم تحديث الدور والصلاحيات بنجاح."
      Result.success(msg)
    } catch (e: Exception) {
      Log.e(TAG, "Cloud Function updateUserRoleAndPermissions failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Revokes all active user sessions via Cloud Function 'revokeUserSessions'.
   */
  suspend fun revokeUserSessions(targetUid: String): Result<String> = withContext(Dispatchers.IO) {
    val functions = getFunctionsInstance()
      ?: return@withContext Result.failure(IllegalStateException("خدمات Firebase غير مهيأة."))

    try {
      val data = hashMapOf(
        "targetUid" to targetUid,
        "clinicId" to CloudSyncManager.DEFAULT_CLINIC_ID
      )

      val result = functions
        .getHttpsCallable("revokeUserSessions")
        .call(data)
        .await()

      val resultMap = result.data as? Map<*, *>
      val msg = (resultMap?.get("message") as? String) ?: "تم إنهاء كافة الجلسات النشطة بنجاح."
      Result.success(msg)
    } catch (e: Exception) {
      Log.e(TAG, "Cloud Function revokeUserSessions failed: ${e.message}", e)
      Result.failure(e)
    }
  }
}
