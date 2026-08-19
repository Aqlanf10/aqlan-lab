package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * تجزئة رموز المرور (PIN) وتخزينها بشكل آمن.
 *
 * لا يتم تخزين رمز المرور نصاً صريحاً في قاعدة البيانات أبداً — يُخزَّن فقط ناتج
 * PBKDF2-HMAC-SHA256 مع ملح عشوائي لكل مستخدم، بالصيغة:
 *
 *   pbkdf2$<iterations>$<saltBase64>$<hashBase64>
 *
 * المقارنة تتم بوقت ثابت (constant time) لمنع هجمات التوقيت.
 */
object PinSecurity {
  private const val ALGORITHM = "PBKDF2WithHmacSHA256"
  private const val ITERATIONS = 120_000
  private const val KEY_LENGTH_BITS = 256
  private const val SALT_LENGTH_BYTES = 16
  private const val PREFIX = "pbkdf2"

  private val secureRandom = SecureRandom()

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
  fun hash(pin: String): String {
    val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
    val hash = derive(pin.trim(), salt, ITERATIONS)
    return listOf(
      PREFIX,
      ITERATIONS.toString(),
      Base64.encodeToString(salt, Base64.NO_WRAP),
      Base64.encodeToString(hash, Base64.NO_WRAP)
    ).joinToString("$")
  }

  /**
   * يتحقق من مطابقة رمز المرور للتجزئة المخزنة.
   *
   * يقبل أيضاً القيم القديمة المخزنة نصاً صريحاً (من إصدارات سابقة من التطبيق)
   * حتى لا يُقفل المستخدم خارج التطبيق بعد التحديث — ويجب ترقيتها فوراً عبر
   * [needsUpgrade] و [hash].
   */
  fun verify(pin: String, stored: String): Boolean = runCatching {
    if (stored.isBlank()) return@runCatching false
    val candidate = pin.trim()

    if (!stored.startsWith("$PREFIX$")) {
      // قيمة قديمة مخزنة نصاً صريحاً — مقارنة بوقت ثابت ثم ترقية من طرف المُستدعي.
      return@runCatching constantTimeEquals(candidate.toByteArray(), stored.trim().toByteArray())
    }

    val parts = stored.split("$")
    if (parts.size != 4) return@runCatching false
    val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..1_000_000 } ?: return@runCatching false
    val salt = Base64.decode(parts[2], Base64.NO_WRAP)
    val expected = Base64.decode(parts[3], Base64.NO_WRAP)
    if (salt.isEmpty() || expected.isEmpty()) return@runCatching false

    constantTimeEquals(derive(candidate, salt, iterations), expected)
  }.getOrDefault(false)

  /** هل تحتاج القيمة المخزنة إلى إعادة تجزئة (لأنها نص صريح قديم أو عدد دورات أقل)؟ */
  fun needsUpgrade(stored: String): Boolean {
    if (!stored.startsWith("$PREFIX$")) return true
    val iterations = stored.split("$").getOrNull(1)?.toIntOrNull() ?: return true
    return iterations < ITERATIONS
  }

  private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
    return try {
      SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
    MessageDigest.isEqual(a, b)
}
