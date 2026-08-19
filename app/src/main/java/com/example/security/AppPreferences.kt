package com.example.security

import android.content.Context
import android.content.SharedPreferences

/**
 * تخزين دائم لإعدادات التطبيق التشغيلية.
 *
 * قبل هذا الملف كانت كل الإعدادات (تفعيل القفل، الإشعارات، معرّف المركز، النسخ
 * الاحتياطي التلقائي...) محفوظة في الذاكرة فقط وتُفقد عند إغلاق التطبيق — أي أن
 * إيقاف القفل أو تعطيل النسخ التلقائي كان يعود لحالته الافتراضية عند كل تشغيل.
 *
 * ملاحظة: لا تُخزَّن هنا أي أسرار (رموز المرور تُخزَّن مجزّأة في قاعدة البيانات،
 * ولا يوجد مفتاح API خارجي في التطبيق).
 */
class AppPreferences(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

  var appLockEnabled: Boolean
    get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, true)
    set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

  var notificationsEnabled: Boolean
    get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

  var biometricUnlockEnabled: Boolean
    get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

  /** معرّف المستخدم الذي وافق على فتح التطبيق بالبصمة. */
  var biometricUserId: Long
    get() = prefs.getLong(KEY_BIOMETRIC_USER_ID, 0L)
    set(value) = prefs.edit().putLong(KEY_BIOMETRIC_USER_ID, value).apply()

  var clinicId: String
    get() = prefs.getString(KEY_CLINIC_ID, "") ?: ""
    set(value) = prefs.edit().putString(KEY_CLINIC_ID, value).apply()

  var clinicName: String
    get() = prefs.getString(KEY_CLINIC_NAME, "") ?: ""
    set(value) = prefs.edit().putString(KEY_CLINIC_NAME, value).apply()

  var autoCloudBackupEnabled: Boolean
    get() = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, value).apply()

  var autoBackupFrequency: String
    get() = prefs.getString(KEY_AUTO_BACKUP_FREQ, "") ?: ""
    set(value) = prefs.edit().putString(KEY_AUTO_BACKUP_FREQ, value).apply()

  var baseCurrency: String
    get() = prefs.getString(KEY_BASE_CURRENCY, "SAR") ?: "SAR"
    set(value) = prefs.edit().putString(KEY_BASE_CURRENCY, value).apply()

  /** هل تم بذر البيانات التجريبية مرة واحدة من قبل؟ يمنع عودتها بعد التصفير. */
  var demoDataSeeded: Boolean
    get() = prefs.getBoolean(KEY_DEMO_SEEDED, false)
    set(value) = prefs.edit().putBoolean(KEY_DEMO_SEEDED, value).apply()

  // --- الحماية من التخمين: عدّاد المحاولات الفاشلة وزمن انتهاء الحظر ---

  var failedUnlockAttempts: Int
    get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    set(value) = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()

  var lockoutUntilMillis: Long
    get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
    set(value) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply()

  companion object {
    private const val FILE_NAME = "aqlan_lab_prefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_BIOMETRIC_USER_ID = "biometric_user_id"
    private const val KEY_CLINIC_ID = "clinic_id"
    private const val KEY_CLINIC_NAME = "clinic_name"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    private const val KEY_AUTO_BACKUP_FREQ = "auto_backup_frequency"
    private const val KEY_BASE_CURRENCY = "base_currency"
    private const val KEY_DEMO_SEEDED = "demo_data_seeded"
    private const val KEY_FAILED_ATTEMPTS = "failed_unlock_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until_millis"
  }
}
