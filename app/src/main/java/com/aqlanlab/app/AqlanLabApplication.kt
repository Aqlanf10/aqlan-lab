package com.aqlanlab.app

import android.app.Application
import android.util.Log
import com.aqlanlab.app.network.AppCheckManager
import com.aqlanlab.app.util.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Custom Application class for Aqlan Dental Center.
 * Handles early startup initialization of Firebase (Auth, Firestore, AppCheck)
 * and critical system resources before Activity UI / ViewModel composition occurs.
 */
class AqlanLabApplication : Application() {
  companion object {
    private const val TAG = "AqlanLabApplication"
    lateinit var instance: AqlanLabApplication
      private set
  }

  override fun onCreate() {
    super.onCreate()
    instance = this

    // 1. Initialize Firebase Core & Services safely before any UI or ViewModel loads
    initializeFirebaseServices()

    // 2. Initialize App Check attestation provider safely
    try {
      AppCheckManager.initialize(this)
    } catch (t: Throwable) {
      Log.w(TAG, "AppCheckManager init note: ${t.message}")
    }

    // 3. Initialize Notification Channels
    try {
      NotificationHelper.init(this)
    } catch (t: Throwable) {
      Log.w(TAG, "NotificationHelper init note: ${t.message}")
    }
  }

  private fun initializeFirebaseServices() {
    try {
      // Initialize FirebaseApp if not already initialized by Google Services ContentProvider
      if (FirebaseApp.getApps(this).isEmpty()) {
        FirebaseApp.initializeApp(this)
        Log.i(TAG, "FirebaseApp initialized explicitly in Application.onCreate")
      } else {
        Log.i(TAG, "FirebaseApp was auto-initialized by Google Services Provider")
      }

      // Configure Firestore with robust offline persistence & cache settings
      try {
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
          .setLocalCacheSettings(
            PersistentCacheSettings.newBuilder()
              .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
              .build()
          )
          .build()
        firestore.firestoreSettings = settings
        Log.i(TAG, "Firestore initialized with unlimited persistent local cache")
      } catch (t: Throwable) {
        Log.w(TAG, "Firestore settings configuration note: ${t.message}")
      }

      // Warm up FirebaseAuth instance safely
      try {
        val auth = FirebaseAuth.getInstance()
        Log.i(TAG, "FirebaseAuth initialized. Current user: ${auth.currentUser?.email ?: "None"}")
      } catch (t: Throwable) {
        Log.w(TAG, "FirebaseAuth warmup note: ${t.message}")
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Firebase initialization error: ${t.message}", t)
    }
  }
}
