package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * تحقق حقيقي من بصمة/وجه المستخدم عبر [BiometricPrompt] من نظام أندرويد.
 *
 * سابقاً كان زر البصمة في شاشة الدخول يفتح التطبيق بصلاحيات مدير النظام دون أي
 * تحقق على الإطلاق (دالة كانت تُرجع true دائماً) — أي أن أي شخص يمسك الجهاز كان
 * يدخل بضغطة واحدة. هذا الملف يستبدل ذلك بتحقق فعلي من عتاد الجهاز.
 */
object BiometricAuthenticator {

  /** حالات توفر البصمة على الجهاز. */
  enum class Availability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    UNKNOWN
  }

  private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.DEVICE_CREDENTIAL

  fun availability(context: Context): Availability =
    when (BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)) {
      BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.HARDWARE_UNAVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
      else -> Availability.UNKNOWN
    }

  fun isAvailable(context: Context): Boolean = availability(context) == Availability.AVAILABLE

  fun unavailableMessageAr(availability: Availability): String = when (availability) {
    Availability.NO_HARDWARE -> "هذا الجهاز لا يدعم البصمة"
    Availability.HARDWARE_UNAVAILABLE -> "قارئ البصمة غير متاح حالياً"
    Availability.NONE_ENROLLED -> "لم يتم تسجيل أي بصمة على الجهاز — سجّل بصمتك من إعدادات النظام أولاً"
    Availability.UNKNOWN -> "تعذر استخدام البصمة على هذا الجهاز"
    Availability.AVAILABLE -> ""
  }

  /**
   * يعرض نافذة التحقق البيومتري.
   *
   * @param onSuccess يُستدعى فقط بعد نجاح تحقق النظام من هوية المستخدم.
   * @param onError يُستدعى مع رسالة عربية عند الفشل أو الإلغاء.
   */
  fun authenticate(
    activity: FragmentActivity,
    title: String = "فتح تطبيق إدارة المعامل",
    subtitle: String = "استخدم بصمتك للتحقق من هويتك",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val availability = availability(activity)
    if (availability != Availability.AVAILABLE) {
      onError(unavailableMessageAr(availability))
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          val message = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED -> "تم إلغاء التحقق بالبصمة"
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
              "تم تعطيل البصمة مؤقتاً بسبب تكرار المحاولات — استخدم رمز المرور"
            else -> "تعذر التحقق بالبصمة: $errString"
          }
          onError(message)
        }

        override fun onAuthenticationFailed() {
          // بصمة غير مطابقة — النظام يسمح بمحاولة أخرى، لا نغلق النافذة هنا.
        }
      }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
      .build()

    prompt.authenticate(promptInfo)
  }
}
