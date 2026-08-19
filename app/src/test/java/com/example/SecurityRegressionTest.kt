package com.example

import com.example.security.PinSecurity
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
  fun `pin is never stored in plaintext`() {
    val pin = "739154"
    val stored = PinSecurity.hash(pin)

    assertFalse("التجزئة يجب ألا تحتوي رمز المرور نفسه", stored.contains(pin))
    assertTrue("الصيغة يجب أن تكون PBKDF2", stored.startsWith("pbkdf2$"))
  }

  @Test
  fun `each hash uses a distinct random salt`() {
    val a = PinSecurity.hash("482913")
    val b = PinSecurity.hash("482913")
    assertNotEquals("نفس الرمز يجب أن ينتج تجزئتين مختلفتين (ملح عشوائي)", a, b)
    assertTrue(PinSecurity.verify("482913", a))
    assertTrue(PinSecurity.verify("482913", b))
  }

  @Test
  fun `verify accepts the correct pin and rejects others`() {
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
  fun `backdoor pin 1111 no longer unlocks any account`() {
    val stored = PinSecurity.hash("706432")
    assertFalse("رمز 1111 يجب ألا يطابق أي رمز آخر", PinSecurity.verify("1111", stored))
  }

  /**
   * الباب الخلفي "1234": كان مقبولاً في `verifyPin` وفي نافذة تبديل المستخدم،
   * فيسمح لأي موظف بالانتقال إلى حساب مدير النظام.
   */
  @Test
  fun `backdoor pin 1234 no longer unlocks any account`() {
    val stored = PinSecurity.hash("904275")
    assertFalse(PinSecurity.verify("1234", stored))
  }

  @Test
  fun `weak and predictable pins are rejected`() {
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
  fun `legacy plaintext pins still work once and are flagged for upgrade`() {
    val legacyPlaintext = "5821"
    assertTrue(PinSecurity.verify("5821", legacyPlaintext))
    assertFalse(PinSecurity.verify("5822", legacyPlaintext))
    assertTrue(PinSecurity.needsUpgrade(legacyPlaintext))
    assertFalse(PinSecurity.needsUpgrade(PinSecurity.hash("5821")))
  }

  @Test
  fun `malformed hashes are rejected without crashing`() {
    assertFalse(PinSecurity.verify("1234", "pbkdf2\$abc\$def"))
    assertFalse(PinSecurity.verify("1234", "pbkdf2\$120000\$!!!\$???"))
    assertFalse(PinSecurity.verify("1234", ""))
  }
}
