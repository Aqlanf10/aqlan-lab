package com.aqlanlab.app.network

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * AppCloudError
 * Strongly-typed domain error model categorizing cloud and network failures.
 */
sealed class AppCloudError(
  val userFriendlyMessageAr: String,
  val technicalMessage: String,
  cause: Throwable? = null
) : Exception(userFriendlyMessageAr, cause) {

  class NetworkUnavailable(
    userFriendlyMessageAr: String = "تعذر الاتصال بالشبكة! يرجى التحقق من اتصال الإنترنت وحالة الاتصال.",
    technicalMessage: String = "Network is offline or host is unreachable.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  class PermissionDenied(
    userFriendlyMessageAr: String = "تم رفض الوصول: ليس لديك الصلاحيات الكافية لتنفيذ هذه العملية السحابية.",
    technicalMessage: String = "Permission Denied / Unauthorized access.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  class AuthenticationFailure(
    userFriendlyMessageAr: String = "فشلت المصادقة: انتهت صلاحية الجلسة أو بيانات الدخول غير صحيحة.",
    technicalMessage: String = "Authentication failed or token is expired.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  class ServerError(
    userFriendlyMessageAr: String = "حدث خطأ في الخادم السحابي. يرجى المحاولة مرة أخرى لاحقاً.",
    technicalMessage: String = "Internal server or cloud function error.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  class ValidationError(
    userFriendlyMessageAr: String = "البيانات المدخلة غير صحيحة أو غير مكتملة.",
    technicalMessage: String = "Validation error.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  class SecurityAttestationFailed(
    userFriendlyMessageAr: String = "فشل التحقق من أمان الجهاز وسلامة التطبيق (Play Integrity / App Check).",
    technicalMessage: String = "Security attestation / App Check token failed.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  class Unknown(
    userFriendlyMessageAr: String = "حدث خطأ غير متوقع أثناء المعالجة السحابية.",
    technicalMessage: String = "Unknown error occurred.",
    cause: Throwable? = null
  ) : AppCloudError(userFriendlyMessageAr, technicalMessage, cause)

  companion object {
    fun fromThrowable(t: Throwable): AppCloudError {
      if (t is AppCloudError) return t

      val msg = t.message?.lowercase() ?: ""

      return when {
        // Network Errors
        t is FirebaseNetworkException || t is UnknownHostException || t is SocketTimeoutException ||
        t is IOException || msg.contains("network") || msg.contains("unable to resolve host") ||
        msg.contains("timeout") -> {
          NetworkUnavailable(
            userFriendlyMessageAr = "تعذر الاتصال بالإنترنت! يرجى التحقق من اتصالك بالشبكة.",
            technicalMessage = t.message ?: "Network error",
            cause = t
          )
        }

        // Permission & Authorization Denials
        (t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
        (t is FirebaseFunctionsException && t.code == FirebaseFunctionsException.Code.PERMISSION_DENIED) ||
        msg.contains("permission_denied") || msg.contains("missing or insufficient permissions") ||
        msg.contains("permission denied") || msg.contains("unauthorized") -> {
          PermissionDenied(
            userFriendlyMessageAr = "تم رفض الإذن السحابي: حسابك غير مخول لتعديل أو قراءة هذه البيانات.",
            technicalMessage = t.message ?: "Permission denied",
            cause = t
          )
        }

        // Authentication Failures
        (t is FirebaseFunctionsException && t.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) ||
        (t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.UNAUTHENTICATED) ||
        t is FirebaseAuthException || msg.contains("unauthenticated") || msg.contains("user-not-found") ||
        msg.contains("invalid-credential") || msg.contains("wrong-password") -> {
          AuthenticationFailure(
            userFriendlyMessageAr = "فشلت المصادقة أو انتهت صلاحية الجلسة. يرجى إعادة تسجيل الدخول.",
            technicalMessage = t.message ?: "Unauthenticated",
            cause = t
          )
        }

        // App Check / Security Failures
        (t is FirebaseFunctionsException && t.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && msg.contains("app check")) ||
        msg.contains("appcheck") || msg.contains("play integrity") || msg.contains("app check token") -> {
          SecurityAttestationFailed(
            userFriendlyMessageAr = "فشل التحقق من أمان الجهاز وسلامة التطبيق (Play Integrity / App Check).",
            technicalMessage = t.message ?: "App Check attestation failed",
            cause = t
          )
        }

        // Validation / Invalid Argument Failures
        (t is FirebaseFunctionsException && t.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT) ||
        (t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.INVALID_ARGUMENT) ||
        msg.contains("invalid_argument") || msg.contains("already_exists") || msg.contains("validation") -> {
          ValidationError(
            userFriendlyMessageAr = if (msg.contains("already_exists")) "اسم المستخدم أو السجل مسجل مسبقاً." else "البيانات المدخلة غير صالحة.",
            technicalMessage = t.message ?: "Invalid argument",
            cause = t
          )
        }

        // Server Errors
        (t is FirebaseFunctionsException && (t.code == FirebaseFunctionsException.Code.INTERNAL || t.code == FirebaseFunctionsException.Code.UNAVAILABLE)) ||
        (t is FirebaseFirestoreException && (t.code == FirebaseFirestoreException.Code.INTERNAL || t.code == FirebaseFirestoreException.Code.UNAVAILABLE)) ||
        msg.contains("internal") || msg.contains("server error") -> {
          ServerError(
            userFriendlyMessageAr = "حدث خطأ في الخادم السحابي، جاري محاولة إعادة الطلب لاحقاً.",
            technicalMessage = t.message ?: "Internal error",
            cause = t
          )
        }

        else -> {
          Unknown(
            userFriendlyMessageAr = t.localizedMessage?.ifBlank { "حدث خطأ غير متوقع في الاتصال السحابي." } ?: "حدث خطأ غير متوقع.",
            technicalMessage = t.message ?: "Unknown exception",
            cause = t
          )
        }
      }
    }
  }
}
