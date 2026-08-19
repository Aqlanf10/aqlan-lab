package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // كان هذا الاختبار فاشلاً في الكود الأصلي: يتوقع "Dental Lab Manager" بينما
    // اسم التطبيق الفعلي في strings.xml عربي. أي أن مجموعة الاختبارات كانت حمراء
    // قبل أي تعديل.
    val appName = context.getString(R.string.app_name)
    assertEquals("د. عقلان الكامل", appName)
  }

  @Test
  fun `application context is available`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(context.packageName.isNotEmpty())
  }
}
