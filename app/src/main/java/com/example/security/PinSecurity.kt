package com.example.security

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * تجزئة رموز المرور (PIN) وتخزينها بشكل آمن.
 *
 * لا يتم تخزين رمز المرور نصاً صريحاً في قاعدة البيانات أبداً — يُخزَّن فقط ناتج
 * PBKDF2 مع ملح عشوائي لكل مستخدم، بالصيغة:
 *
 *   pbkdf2$<algorithm>$<iterations>$<saltBase64>$<hashBase64>
 *
 * المقارنة تتم بوقت ثابت (constant time) لمنع هجمات التوقيت.
 *
 * ملاحظتان بُنيت عليهما هذه النسخة:
 *
 * 1. **توفر الخوارزمية:** أندرويد لا يوفر `PBKDF2WithHmacSHA256` إلا من الإصدار
 *    8.0 (API 26) فأحدث، بينما التطبيق يدعم أندرويد 7.0 (API 24). على تلك
 *    الأجهزة كان `SecretKeyFactory.getInstance` يرمي `NoSuchAlgorithmException`
 *    فينهار التطبيق لحظة تأكيد رمز المرور. تُختار الخوارزمية الآن وقت التشغيل
 *    مع السقوط إلى `PBKDF2WithHmacSHA1` المتوفر على كل الإصدارات، ويُخزَّن
 *    اسمها داخل التجزئة نفسها ليبقى التحقق صحيحاً لو انتقلت البيانات بين
 *    جهازين مختلفي الإصدار.
 *
 * 2. **لا تُستدعى على الخيط الرئيسي:** الاشتقاق مكلف عمداً (١٢٠ ألف دورة).
 *    لذلك [hash] و [verify] دالتان `suspend` تنفّذان على [Dispatchers.Default]،
 *    حتى لا تتجمد الواجهة أو يُغلق النظام التطبيق بسبب عدم الاستجابة (ANR).
 */
object PinSecurity {
  private const val ALG_SHA256 = "PBKDF2WithHmacSHA256"
  private const val ALG_SHA1 = "PBKDF2WithHmacSHA1"
  private const val ITERATIONS = 120_000
  private const val KEY_LENGTH_BITS = 256
  private const val SALT_LENGTH_BYTES = 16
  private const val PREFIX = "pbkdf2"

  private val secureRandom = SecureRandom()

  /** أفضل خوارزمية متاحة فعلياً على هذا الجهاز، تُحسب مرة واحدة. */
  private val preferredAlgorithm: String by lazy {
    if (isAlgorithmAvailable(ALG_SHA256)) ALG_SHA256 else ALG_SHA1
  }

  /** الحد الأدنى المقبول لطول رمز المرور. */
  const val MIN_PIN_LENGTH = 4

  /** رموز مرور مرفوضة لأنها متوقعة تماماً. */
  private val BANNED_PINS = setOf(
    "0000", "1111", "1234", "2222", "3333", "4444", "5555",
    "6666", "7777", "8888", "9999", "4321", "1212", "123456", "000000"
  )

  /**
   * يتحقق من قوة رمز المرور. يعيد رسالة خطأ عربية أو null إذا كان الرمز مقبولاً.
   */
  fun validatePinStrength(pin: String): String? {
    val trimmed = pin.trim()
    return when {
      trimmed.length < MIN_PIN_LENGTH ->
        "يجب أن يتكون رمز المرور من $MIN_PIN_LENGTH أرقام على الأقل"
      !trimmed.all { it.isDigit() } ->
        "يجب أن يتكون رمز المرور من أرقام فقط"
      trimmed in BANNED_PINS ->
        "رمز المرور هذا ضعيف ومتوقع — يرجى اختيار رمز آخر"
      trimmed.toSet().size == 1 ->
        "لا يمكن أن تتكرر جميع أرقام رمز المرور"
      else -> null
    }
  }

  /** ينشئ تجزئة جديدة لرمز المرور مع ملح عشوائي. */
  suspend fun hash(pin: String): String = withContext(Dispatchers.Default) {
    val algorithm = preferredAlgorithm
    val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
    val derived = derive(pin.trim(), salt, ITERATIONS, algorithm)
    listOf(
      PREFIX,
      algorithm,
      ITERATIONS.toString(),
      Base64.encodeToString(salt, Base64.NO_WRAP),
      Base64.encodeToString(derived, Base64.NO_WRAP)
    ).joinToString("$")
  }

  /**
   * يتحقق من مطابقة رمز المرور للتجزئة المخزنة.
   *
   * يقبل أيضاً القيم القديمة المخزنة نصاً صريحاً (من إصدارات سابقة من التطبيق)
   * حتى لا يُقفل المستخدم خارج التطبيق بعد التحديث — ويجب ترقيتها فوراً عبر
   * [needsUpgrade] و [hash].
   */
  suspend fun verify(pin: String, stored: String): Boolean = withContext(Dispatchers.Default) {
    runCatching {
      if (stored.isBlank()) return@runCatching false
      val candidate = pin.trim()

      if (!stored.startsWith("$PREFIX$")) {
        // قيمة قديمة مخزنة نصاً صريحاً — مقارنة بوقت ثابت ثم ترقية من طرف المُستدعي.
        return@runCatching constantTimeEquals(candidate.toByteArray(), stored.trim().toByteArray())
      }

      val parts = stored.split("$")
      // الصيغة الحالية خمسة أجزاء (مع اسم الخوارزمية)؛ الصيغة الأولى كانت أربعة
      // أجزاء وتعني SHA-256 ضمناً.
      val algorithm: String
      val iterationsRaw: String
      val saltRaw: String
      val hashRaw: String
      when (parts.size) {
        5 -> {
          algorithm = parts[1]; iterationsRaw = parts[2]; saltRaw = parts[3]; hashRaw = parts[4]
        }
        4 -> {
          algorithm = ALG_SHA256; iterationsRaw = parts[1]; saltRaw = parts[2]; hashRaw = parts[3]
        }
        else -> return@runCatching false
      }

      if (algorithm != ALG_SHA256 && algorithm != ALG_SHA1) return@runCatching false
      if (!isAlgorithmAvailable(algorithm)) return@runCatching false

      val iterations = iterationsRaw.toIntOrNull()?.takeIf { it in 1..1_000_000 }
        ?: return@runCatching false
      val salt = Base64.decode(saltRaw, Base64.NO_WRAP)
      val expected = Base64.decode(hashRaw, Base64.NO_WRAP)
      if (salt.isEmpty() || expected.isEmpty()) return@runCatching false

      constantTimeEquals(derive(candidate, salt, iterations, algorithm), expected)
    }.getOrDefault(false)
  }

  /**
   * هل تحتاج القيمة المخزنة إلى إعادة تجزئة؟
   * (نص صريح قديم، أو عدد دورات أقل، أو خوارزمية أضعف مما يدعمه هذا الجهاز)
   */
  fun needsUpgrade(stored: String): Boolean {
    if (!stored.startsWith("$PREFIX$")) return true
    val parts = stored.split("$")
    val algorithm: String
    val iterationsRaw: String
    when (parts.size) {
      5 -> { algorithm = parts[1]; iterationsRaw = parts[2] }
      4 -> { algorithm = ALG_SHA256; iterationsRaw = parts[1] }
      else -> return true
    }
    val iterations = iterationsRaw.toIntOrNull() ?: return true
    return iterations < ITERATIONS || algorithm != preferredAlgorithm
  }

  private fun isAlgorithmAvailable(algorithm: String): Boolean =
    runCatching { SecretKeyFactory.getInstance(algorithm) }.isSuccess

  private fun derive(pin: String, salt: ByteArray, iterations: Int, algorithm: String): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
    return try {
      SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
    MessageDigest.isEqual(a, b)
}
