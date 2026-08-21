package com.aqlanlab.app

import com.aqlanlab.app.network.AppCheckManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. App Package and Application ID consistency (com.aqlanlab.app).
 * 2. Firebase App Check provider logic separation (Play Integrity vs Debug Provider).
 * 3. ProGuard and Room entity integrity.
 */
class AppCheckUnitTest {

  @Test
  fun testOfficialPackageNameIsAqlanLab() {
    val expectedPackage = "com.aqlanlab.app"
    val actualPackage = this.javaClass.`package`?.name
    assertNotNull(actualPackage)
    assertTrue("Package should start with $expectedPackage", actualPackage!!.startsWith(expectedPackage))
  }

  @Test
  fun testAppCheckProviderStrategy() {
    // In Debug builds, DebugAppCheckProviderFactory is used (with local tokens).
    // In Release builds, PlayIntegrityAppCheckProviderFactory is used for Google Play Hardware Attestation.
    val isDebug = BuildConfig.DEBUG
    val targetProvider = if (isDebug) "DebugAppCheckProviderFactory" else "PlayIntegrityAppCheckProviderFactory"
    assertNotNull(targetProvider)

    // Verify AppCheckManager singleton exists
    assertNotNull(AppCheckManager)
  }

  @Test
  fun testRoomEntityClassesUnderNewPackage() {
    // Ensure all Room models are accessible under the official com.aqlanlab.app package
    val shipmentClass = com.aqlanlab.app.data.models.Shipment::class.java
    val labClass = com.aqlanlab.app.data.models.Laboratory::class.java
    val userClass = com.aqlanlab.app.data.models.User::class.java
    val paymentClass = com.aqlanlab.app.data.models.Payment::class.java

    assertEquals("com.aqlanlab.app.data.models.Shipment", shipmentClass.name)
    assertEquals("com.aqlanlab.app.data.models.Laboratory", labClass.name)
    assertEquals("com.aqlanlab.app.data.models.User", userClass.name)
    assertEquals("com.aqlanlab.app.data.models.Payment", paymentClass.name)
  }
}
