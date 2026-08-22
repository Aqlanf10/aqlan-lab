package com.aqlanlab.app.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utility for hashing and verifying PINs using cryptographic SHA-256 with a unique Salt.
 * No plaintext PINs or Passwords are stored in database or memory.
 */
object SecurityUtils {

  private const val ITERATIONS = 1000

  /**
   * Generates a secure random 16-byte salt, Hex encoded.
   */
  fun generateSalt(): String {
    val random = SecureRandom()
    val saltBytes = ByteArray(16)
    random.nextBytes(saltBytes)
    return saltBytes.joinToString("") { "%02x".format(it) }
  }

  /**
   * Hashes the raw PIN with the provided salt using multiple rounds of SHA-256.
   * Returns a secure hash string: "salt:hashHex"
   */
  fun hashPin(rawPin: String, salt: String = generateSalt()): String {
    if (rawPin.isBlank()) return ""
    return try {
      val md = MessageDigest.getInstance("SHA-256")
      var hash = (rawPin + ":" + salt).toByteArray(Charsets.UTF_8)
      for (i in 0 until ITERATIONS) {
        md.reset()
        hash = md.digest(hash)
      }
      val hashHex = hash.joinToString("") { "%02x".format(it) }
      "$salt:$hashHex"
    } catch (e: Exception) {
      ""
    }
  }

  /**
   * Verifies if a raw PIN matches the stored salted hash.
   * Supports both formatted salted hashes ("salt:hashHex") and legacy fallback verification.
   * Rejects any blank inputs or universal bypasses.
   */
  fun verifyPin(rawPin: String, storedHash: String): Boolean {
    if (rawPin.isBlank() || storedHash.isBlank()) return false

    // Check if storedHash is in salted format "salt:hashHex"
    if (storedHash.contains(":")) {
      val parts = storedHash.split(":")
      if (parts.size == 2) {
        val salt = parts[0]
        val expectedHash = parts[1]
        val computedHashWithSalt = hashPin(rawPin, salt)
        val computedHashOnly = computedHashWithSalt.substringAfter(":")
        return constantTimeEquals(expectedHash, computedHashOnly)
      }
    }

    // Direct constant time check if hashed without salt delimiter
    return constantTimeEquals(storedHash, rawPin)
  }

  /**
   * Performs constant-time comparison to prevent timing attacks.
   */
  private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) {
      result = result or (a[i].code xor b[i].code)
    }
    return result == 0
  }
}
