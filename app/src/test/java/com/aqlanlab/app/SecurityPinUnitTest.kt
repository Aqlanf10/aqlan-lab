package com.aqlanlab.app

import com.aqlanlab.app.data.DatabaseSeedData
import com.aqlanlab.app.data.models.User
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.util.SecurityUtils
import org.junit.Assert.*
import org.junit.Test

class SecurityPinUnitTest {

  @Test
  fun testNoHardcodedPinsInSeedData() {
    for (user in DatabaseSeedData.defaultUsers) {
      // Seed users must NEVER contain plaintext hardcoded default PINs like 1111, 1234, 2222, 3333, 4444
      assertNotEquals("1111", user.pinCode)
      assertNotEquals("1234", user.pinCode)
      assertNotEquals("2222", user.pinCode)
      assertNotEquals("3333", user.pinCode)
      assertNotEquals("4444", user.pinCode)
      assertTrue("Default seed pinCode must be empty", user.pinCode.isEmpty())
    }
  }

  @Test
  fun testOldVulnerablePinsFailVerification() {
    val defaultUser = User(
      id = 1,
      username = "doctor",
      fullName = "د. عقلان الكامل",
      role = UserRole.SUPER_ADMIN,
      pinCode = "" // unconfigured/empty
    )

    // Bypass codes 1111, 1234, 0000, 2222, 3333, 4444 must all fail
    assertFalse(SecurityUtils.verifyPin("1111", defaultUser.pinCode))
    assertFalse(SecurityUtils.verifyPin("1234", defaultUser.pinCode))
    assertFalse(SecurityUtils.verifyPin("2222", defaultUser.pinCode))
    assertFalse(SecurityUtils.verifyPin("3333", defaultUser.pinCode))
    assertFalse(SecurityUtils.verifyPin("4444", defaultUser.pinCode))
    assertFalse(SecurityUtils.verifyPin("0000", defaultUser.pinCode))
    assertFalse(SecurityUtils.verifyPin("", defaultUser.pinCode))
  }

  @Test
  fun testSaltedHashPinGenerationAndVerification() {
    val rawPin = "9876"
    val hashed = SecurityUtils.hashPin(rawPin)

    // Hash must contain salt and hex digest separated by colon
    assertTrue(hashed.contains(":"))
    val parts = hashed.split(":")
    assertEquals(2, parts.size)

    // Verification with correct PIN must succeed
    assertTrue(SecurityUtils.verifyPin("9876", hashed))

    // Verification with incorrect or old bypass PINs must strictly fail
    assertFalse(SecurityUtils.verifyPin("1111", hashed))
    assertFalse(SecurityUtils.verifyPin("1234", hashed))
    assertFalse(SecurityUtils.verifyPin("9875", hashed))
    assertFalse(SecurityUtils.verifyPin("", hashed))
  }

  @Test
  fun testDistinctUsersHaveUniqueSaltsAndHashes() {
    val pin = "5566"
    val hash1 = SecurityUtils.hashPin(pin)
    val hash2 = SecurityUtils.hashPin(pin)

    // Because of random salts, identical PINs must produce distinct stored hashes
    assertNotEquals(hash1, hash2)

    // Both must still verify against the correct PIN
    assertTrue(SecurityUtils.verifyPin(pin, hash1))
    assertTrue(SecurityUtils.verifyPin(pin, hash2))
  }
}
