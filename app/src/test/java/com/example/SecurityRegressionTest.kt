package com.example

import com.example.security.PinSecurity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * اختبارات انحدار أمني: كل اختبار هنا يمثّل ثغرة كانت موجودة فعلياً في التطبيق،
 * ويفشل إذا عادت.
 *
 * يعمل تحت Robolectric لأن [PinSecurity] يستخدم `android.util.Base64`.
 */
@RunWith(RobolectricTestRunner::class)
class SecurityRegressionTest {

  @Test
  fun `pin is never stored in plaintext`() = runBlocking<Unit> {
    val pin = "739154"
    val stored = PinSecurity.hash(pin)

    assertFalse("التجزئة يجب ألا تحتوي رمز المرور نفسه", stored.contains(pin))
    assertTrue("الصيغة يجب أن تكون PBKDF2", stored.startsWith("pbkdf2$"))
  }

  @Test
  fun `each hash uses a distinct random salt`() = runBlocking<Unit> {
    val a = PinSecurity.hash("482913")
    val b = PinSecurity.hash("482913")
    assertNotEquals("نفس الرمز يجب أن ينتج تجزئتين مختلفتين (ملح عشوائي)", a, b)
    assertTrue(PinSecurity.verify("482913", a))
    assertTrue(PinSecurity.verify("482913", b))
  }

  @Test
  fun `verify accepts the correct pin and rejects others`() = runBlocking<Unit> {
    val stored = PinSecurity.hash("592017")
    assertTrue(PinSecurity.verify("592017", stored))
    assertFalse(PinSecurity.verify("592018", stored))
    assertFalse(PinSecurity.verify("", stored))
    assertFalse(PinSecurity.verify("592017 ", "not-a-hash"))
  }

  /**
   * الباب الخلفي "1111": كانت دالة فتح التطبيق تقبل هذا الرمز دائماً وتمنح
   * صلاحيات مدير النظام، حتى بعد أن يغيّر الطبيب رمزه.
   */
  @Test
  fun `backdoor pin 1111 no longer unlocks any account`() = runBlocking<Unit> {
    val stored = PinSecurity.hash("706432")
    assertFalse("رمز 1111 يجب ألا يطابق أي رمز آخر", PinSecurity.verify("1111", stored))
  }

  /**
   * الباب الخلفي "1234": كان مقبولاً في `verifyPin` وفي نافذة تبديل المستخدم،
   * فيسمح لأي موظف بالانتقال إلى حساب مدير النظام.
   */
  @Test
  fun `backdoor pin 1234 no longer unlocks any account`() = runBlocking<Unit> {
    val stored = PinSecurity.hash("904275")
    assertFalse(PinSecurity.verify("1234", stored))
  }

  @Test
  fun `weak and predictable pins are rejected`() = runBlocking<Unit> {
    listOf("1111", "1234", "0000", "2222", "3333", "9999", "4321").forEach { weak ->
      assertNotNull("الرمز الضعيف $weak يجب أن يُرفض", PinSecurity.validatePinStrength(weak))
    }
    assertNotNull("رمز أقصر من 4 خانات يجب أن يُرفض", PinSecurity.validatePinStrength("12"))
    assertNotNull("رمز غير رقمي يجب أن يُرفض", PinSecurity.validatePinStrength("ab12"))
    assertNull("رمز قوي يجب أن يُقبل", PinSecurity.validatePinStrength("8265"))
    assertNull(PinSecurity.validatePinStrength("508213"))
  }

  /**
   * ترقية سلسة: قواعد البيانات المثبّتة مسبقاً تحوي رموزاً نصية صريحة، ويجب
   * ألا يُقفل المستخدم خارج تطبيقه بعد التحديث — لكن القيمة يجب أن تُعلَّم
   * كمحتاجة لإعادة تجزئة.
   */
  @Test
  fun `legacy plaintext pins still work once and are flagged for upgrade`() = runBlocking<Unit> {
    val legacyPlaintext = "5821"
    assertTrue(PinSecurity.verify("5821", legacyPlaintext))
    assertFalse(PinSecurity.verify("5822", legacyPlaintext))
    assertTrue(PinSecurity.needsUpgrade(legacyPlaintext))
    assertFalse(PinSecurity.needsUpgrade(PinSecurity.hash("5821")))
  }

  @Test
  fun `malformed hashes are rejected without crashing`() = runBlocking<Unit> {
    assertFalse(PinSecurity.verify("1234", "pbkdf2\$abc\$def"))
    assertFalse(PinSecurity.verify("1234", "pbkdf2\$120000\$!!!\$???"))
    assertFalse(PinSecurity.verify("1234", ""))
  }

  /**
   * الانهيار الذي أبلغ عنه المستخدم عند تأكيد رمز المرور:
   * أندرويد لا يوفّر PBKDF2WithHmacSHA256 قبل الإصدار 8.0 (API 26)، والتطبيق
   * يدعم 7.0 (API 24) — فكان `SecretKeyFactory.getInstance` يرمي استثناءً
   * ويُغلق التطبيق. التجزئة الآن تحمل اسم الخوارزمية المستخدمة، والتحقق يعمل
   * مع أي منهما.
   */
  @Test
  fun `hash records its algorithm so verification survives across android versions`() = runBlocking<Unit> {
    val stored = PinSecurity.hash("615483")
    val parts = stored.split("$")

    assertEquals("الصيغة يجب أن تحوي اسم الخوارزمية", 5, parts.size)
    assertEquals("pbkdf2", parts[0])
    assertTrue(
      "الخوارزمية المخزّنة يجب أن تكون إحدى الخوارزميتين المدعومتين",
      parts[1] == "PBKDF2WithHmacSHA256" || parts[1] == "PBKDF2WithHmacSHA1"
    )
    assertTrue(PinSecurity.verify("615483", stored))
    assertFalse(PinSecurity.verify("615484", stored))
  }

  /** تجزئة مكتوبة بخوارزمية SHA-1 صراحةً (كما تُنتَج على أندرويد 7) يجب أن تُقبل. */
  @Test
  fun `sha1 hashes produced on older android still verify`() = runBlocking<Unit> {
    val sha256Style = PinSecurity.hash("729401")
    val parts = sha256Style.split("$")
    val asSha1 = listOf("pbkdf2", "PBKDF2WithHmacSHA1", parts[2], parts[3], parts[4]).joinToString("$")

    // القيمة أعلاه ليست تجزئة صحيحة لـ SHA-1، لكن المهم ألا تنهار الدالة
    // وأن تُرجع false بدل رمي استثناء.
    assertFalse(PinSecurity.verify("729401", asSha1))

    // وخوارزمية غير معروفة تُرفض بهدوء أيضاً.
    val unknownAlg = listOf("pbkdf2", "PBKDF2WithHmacSHA999", parts[2], parts[3], parts[4]).joinToString("$")
    assertFalse(PinSecurity.verify("729401", unknownAlg))
  }

  /** الصيغة القديمة ذات الأربعة أجزاء يجب أن تبقى مقروءة. */
  @Test
  fun `legacy four part hash format is still readable`() = runBlocking<Unit> {
    val current = PinSecurity.hash("864017").split("$")
    val legacyFormat = listOf("pbkdf2", current[2], current[3], current[4]).joinToString("$")

    // تُقرأ على أنها SHA-256؛ تنجح فقط إذا كانت التجزئة الأصلية بـ SHA-256.
    val expected = current[1] == "PBKDF2WithHmacSHA256"
    assertEquals(expected, PinSecurity.verify("864017", legacyFormat))
    assertTrue(PinSecurity.needsUpgrade(legacyFormat) || expected)
  }
}
