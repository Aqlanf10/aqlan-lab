package com.aqlanlab.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Utility for hashing and verifying PINs.
 *
 * v2 format  : "v2:salt:hashHex"  -> PBKDF2WithHmacSHA256, 210,000 iterations (256-bit key)
 * v1 format  : "v1:salt:hashHex"  -> PBKDF2WithHmacSHA1,   210,000 iterations (256-bit key)
 *              (fallback for API 24/25 devices where HmacSHA256 is unavailable)
 * legacy v0  : "salt:hashHex"     -> 1,000 rounds of plain SHA-256 (kept READ-ONLY for
 *              backward compatibility so existing users can still sign in; hashes are
 *              transparently upgraded to v2 on the next successful login)
 *
 * SECURITY FIX: the previous implementation accepted a PLAINTEXT-stored PIN whenever the
 * stored value contained no ":" separator. That legacy bypass has been REMOVED — any
 * non-salted value is now rejected outright.
 */
object SecurityUtils {

  private const val ITERATIONS_V2 = 210_000
  private const val KEY_LENGTH_BITS = 256
  // Legacy scheme parameters (verification only — never used for new hashes)
  private const val LEGACY_ITERATIONS = 1000

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
   * Hashes the raw PIN with the provided salt using PBKDF2 (210k iterations).
   * Returns a secure hash string: "v2:salt:hashHex" (or "v1:..." on API 24/25).
   */
  fun hashPin(rawPin: String, salt: String = generateSalt()): String {
    if (rawPin.isBlank()) return ""
    return try {
      val (version, hashHex) = pbkdf2Hash(rawPin, salt)
      "$version:$salt:$hashHex"
    } catch (e: Exception) {
      ""
    }
  }

  /**
   * Verifies if a raw PIN matches the stored hash.
   * Supports v2/v1 PBKDF2 formats and the legacy "salt:hash" scheme.
   * REJECTS blank inputs and any value that is not a salted hash (plaintext removed).
   */
  fun verifyPin(rawPin: String, storedHash: String): Boolean {
    if (rawPin.isBlank() || storedHash.isBlank()) return false

    val parts = storedHash.split(":")
    return when {
      // v2 / v1 : "version:salt:hashHex"
      (parts.size == 3) && (parts[0] == "v2" || parts[0] == "v1") -> {
        val salt = parts[1]
        val expectedHash = parts[2]
        val computed = try {
          pbkdf2Hash(rawPin, salt, forceVersion = parts[0])
        } catch (e: Exception) {
          return false
        }
        constantTimeEquals(expectedHash, computed.second)
      }

      // Legacy v0 : "salt:hashHex" (1000x SHA-256) — verify only, upgrade on login
      parts.size == 2 -> {
        val salt = parts[0]
        val expectedHash = parts[1]
        constantTimeEquals(expectedHash, legacySha256Hash(rawPin, salt))
      }

      // SECURITY FIX: anything else (including plaintext values) is rejected.
      else -> false
    }
  }

  /**
   * True when the stored hash uses an outdated scheme and should be re-hashed
   * with hashPin() on the next successful login.
   */
  fun needsRehash(storedHash: String): Boolean {
    if (storedHash.isBlank()) return false
    return !storedHash.startsWith("v2:")
  }

  // ─── internals ─────────────────────────────────────────────

  /**
   * Derives a PBKDF2 hash. Prefers PBKDF2WithHmacSHA256 ("v2"); falls back to
   * PBKDF2WithHmacSHA1 ("v1") on devices where the SHA256 variant is unavailable
   * (API 24/25), or when verifying an existing v1 hash.
   * Returns (version, hashHex).
   */
  private fun pbkdf2Hash(
    rawPin: String,
    salt: String,
    forceVersion: String? = null
  ): Pair<String, String> {
    val spec = PBEKeySpec(
      rawPin.toCharArray(),
      salt.toByteArray(Charsets.UTF_8),
      ITERATIONS_V2,
      KEY_LENGTH_BITS
    )
    val factory: SecretKeyFactory = if (forceVersion == "v1") {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    } else {
      try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      } catch (e: Exception) {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
      }
    }
    val version = if (factory.algorithm == "PBKDF2WithHmacSHA256") "v2" else "v1"
    val hash = factory.generateSecret(spec).encoded
    return Pair(version, hash.joinToString("") { "%02x".format(it) })
  }

  /** Legacy scheme (verification only): 1,000 rounds of SHA-256 over "pin:salt". */
  private fun legacySha256Hash(rawPin: String, salt: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    var hash = (rawPin + ":" + salt).toByteArray(Charsets.UTF_8)
    for (i in 0 until LEGACY_ITERATIONS) {
      md.reset()
      hash = md.digest(hash)
    }
    return hash.joinToString("") { "%02x".format(it) }
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
