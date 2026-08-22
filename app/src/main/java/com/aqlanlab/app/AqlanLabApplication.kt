package com.aqlanlab.app

import android.app.Application
import android.util.Log
import com.aqlanlab.app.network.AppCheckManager
import com.aqlanlab.app.util.NotificationHelper
import com.google.firebase.FirebaseApp

/**
 * Custom Application class for Aqlan Dental Center.
 * Handles early startup initialization of Firebase and system resources safely.
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

    // 1. Initialize Firebase Core safely
    try {
      if (FirebaseApp.getApps(this).isEmpty()) {
        FirebaseApp.initializeApp(this)
        Log.i(TAG, "FirebaseApp initialized successfully")
      }
    } catch (t: Throwable) {
      Log.w(TAG, "FirebaseApp init handled: ${t.message}")
    }

    // 2. Initialize App Check attestation safely
    try {
      AppCheckManager.initialize(this)
    } catch (t: Throwable) {
      Log.w(TAG, "AppCheckManager init handled: ${t.message}")
    }

    // 3. Initialize Notification Channels safely
    try {
      NotificationHelper.init(this)
    } catch (t: Throwable) {
      Log.w(TAG, "NotificationHelper init handled: ${t.message}")
    }
  }
}
