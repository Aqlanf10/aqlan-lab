package com.aqlanlab.app.network

import android.content.Context
import android.util.Log
import com.aqlanlab.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * AppCheckManager
 * Manages Firebase App Check initialization and verification using:
 * - Play Integrity Provider in Production / Release builds.
 * - Debug Provider exclusively in Debug builds (BuildConfig.DEBUG).
 *
 * Enforces hardware and app attestation on Firestore, Cloud Storage, and Cloud Functions.
 */
object AppCheckManager {
  private const val TAG = "AppCheckManager"

  @Volatile
  private var isInitialized = false

  /**
   * Initializes Firebase App Check with the appropriate provider factory.
   */
  fun initialize(context: Context) {
    if (isInitialized) return

    try {
      if (FirebaseApp.getApps(context).isEmpty()) {
        Log.w(TAG, "FirebaseApp not initialized, skipping App Check initialization.")
        return
      }

      val firebaseAppCheck = FirebaseAppCheck.getInstance()

      try {
        if (BuildConfig.DEBUG) {
          // Debug builds use the DebugAppCheckProviderFactory
          Log.i(TAG, "Initializing Firebase App Check with DebugAppCheckProviderFactory")
          firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
          )
        } else {
          // Production / Release builds use Google Play Integrity API
          Log.i(TAG, "Initializing Firebase App Check with PlayIntegrityAppCheckProviderFactory")
          firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
          )
        }
      } catch (e: Throwable) {
        Log.w(TAG, "AppCheck provider installation note: ${e.message}")
      }

      // Automatically refresh App Check tokens before expiration
      try {
        firebaseAppCheck.setTokenAutoRefreshEnabled(true)
      } catch (e: Throwable) {
        Log.w(TAG, "AppCheck token auto refresh note: ${e.message}")
      }

      // Add listener to monitor App Check token events and attestation status
      try {
        firebaseAppCheck.addAppCheckListener { token: AppCheckToken ->
          Log.d(TAG, "App Check token refreshed successfully. Expire Time: ${token.expireTimeMillis}")
        }
      } catch (e: Throwable) {
        Log.w(TAG, "AppCheck listener note: ${e.message}")
      }

      isInitialized = true
      Log.i(TAG, "Firebase App Check initialized successfully.")
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to initialize Firebase App Check: ${e.message}")
    }
  }

  /**
   * Proactively retrieves or forces a refresh of the current App Check token.
   * Useful for testing and verifying attestation before high-security operations.
   */
  suspend fun getAppCheckToken(forceRefresh: Boolean = false): Result<AppCheckToken> = withContext(Dispatchers.IO) {
    try {
      val firebaseAppCheck = FirebaseAppCheck.getInstance()
      val token = firebaseAppCheck.getAppCheckToken(forceRefresh).await()
      if (token != null) {
        Result.success(token)
      } else {
        Result.failure(IllegalStateException("App Check token is null or attestation failed."))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching App Check token: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Returns whether App Check has been initialized.
   */
  fun isAppCheckReady(): Boolean = isInitialized
}
