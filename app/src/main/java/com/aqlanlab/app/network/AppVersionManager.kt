package com.aqlanlab.app.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aqlanlab.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * VersionInfo data model
 */
data class AppVersionConfig(
  val currentAppVersionCode: Int = BuildConfig.VERSION_CODE,
  val currentAppVersionName: String = BuildConfig.VERSION_NAME,
  val minimumSupportedVersionCode: Int = 1,
  val latestVersionCode: Int = BuildConfig.VERSION_CODE,
  val latestVersionName: String = BuildConfig.VERSION_NAME,
  val isMandatoryUpdate: Boolean = false,
  val updateTitleAr: String = "تحديث أمني إجباري مطلوب",
  val updateMessageAr: String = "يتوفر إصدار أمني جديد ومحدث من نظام مركز الدكتور عقلان الكامل. يُرجى التحديث للمتابعة وحماية البيانات السحابية.",
  val releaseNotesAr: String = "• تطبيق معايير حماية Play Integrity و App Check\n• فصل وحماية البيانات المالية server-side\n• ترقية قاعدة البيانات والأمان",
  val updateUrl: String = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
)

sealed class AppUpdateStatus {
  object Checking : AppUpdateStatus()
  object UpToDate : AppUpdateStatus()
  data class MandatoryUpdateRequired(val config: AppVersionConfig) : AppUpdateStatus()
  data class OptionalUpdateAvailable(val config: AppVersionConfig) : AppUpdateStatus()
  data class CheckFailed(val error: String) : AppUpdateStatus()
}

/**
 * AppVersionManager
 * Manages Semantic Versioning, version verification against Firebase Backend / Remote Config,
 * and enforces the Mandatory Update barrier when installed version < minimumSupportedVersionCode.
 */
class AppVersionManager(private val context: Context) {
  companion object {
    private const val TAG = "AppVersionManager"
    const val DEFAULT_MINIMUM_VERSION_CODE = 1
    const val VERSION_CONFIG_DOC = "version_config"
  }

  private val _updateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Checking)
  val updateStatus: StateFlow<AppUpdateStatus> = _updateStatus.asStateFlow()

  private val _versionConfig = MutableStateFlow(AppVersionConfig())
  val versionConfig: StateFlow<AppVersionConfig> = _versionConfig.asStateFlow()

  /**
   * Pure evaluation function for testability and runtime evaluation.
   */
  fun evaluateVersion(currentVersionCode: Int, remoteConfig: AppVersionConfig): AppUpdateStatus {
    return when {
      currentVersionCode < remoteConfig.minimumSupportedVersionCode -> {
        AppUpdateStatus.MandatoryUpdateRequired(remoteConfig.copy(isMandatoryUpdate = true))
      }
      currentVersionCode < remoteConfig.latestVersionCode -> {
        AppUpdateStatus.OptionalUpdateAvailable(remoteConfig.copy(isMandatoryUpdate = false))
      }
      else -> {
        AppUpdateStatus.UpToDate
      }
    }
  }

  /**
   * Checks app version against Firestore / Remote Backend.
   */
  suspend fun checkAppVersion(forceCheck: Boolean = false): AppUpdateStatus = withContext(Dispatchers.IO) {
    _updateStatus.value = AppUpdateStatus.Checking

    try {
      if (FirebaseApp.getApps(context).isEmpty()) {
        Log.w(TAG, "Firebase not initialized, using local fallback.")
        val fallbackConfig = AppVersionConfig(
          minimumSupportedVersionCode = DEFAULT_MINIMUM_VERSION_CODE,
          latestVersionCode = BuildConfig.VERSION_CODE,
          latestVersionName = BuildConfig.VERSION_NAME
        )
        _versionConfig.value = fallbackConfig
        val status = evaluateVersion(BuildConfig.VERSION_CODE, fallbackConfig)
        _updateStatus.value = status
        return@withContext status
      }

      val firestore = FirebaseFirestore.getInstance()
      val docRef = firestore.collection("app_config").document(VERSION_CONFIG_DOC)
      val snapshot = docRef.get().await()

      val config = if (snapshot.exists()) {
        val minVer = snapshot.getLong("minimumSupportedVersionCode")?.toInt() ?: DEFAULT_MINIMUM_VERSION_CODE
        val latestVer = snapshot.getLong("latestVersionCode")?.toInt() ?: BuildConfig.VERSION_CODE
        val latestName = snapshot.getString("latestVersionName") ?: BuildConfig.VERSION_NAME
        val title = snapshot.getString("updateTitleAr") ?: "تحديث أمني إجباري مطلوب"
        val message = snapshot.getString("updateMessageAr") ?: "يتوفر إصدار أمني جديد ومحدث. يُرجى التحديث للمتابعة وحماية البيانات السحابية."
        val notes = snapshot.getString("releaseNotesAr") ?: ""
        val url = snapshot.getString("updateUrl") ?: "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

        AppVersionConfig(
          minimumSupportedVersionCode = minVer,
          latestVersionCode = latestVer,
          latestVersionName = latestName,
          updateTitleAr = title,
          updateMessageAr = message,
          releaseNotesAr = notes,
          updateUrl = url
        )
      } else {
        // First run initialization on Firestore for admin transparency
        val initialConfig = AppVersionConfig(
          minimumSupportedVersionCode = DEFAULT_MINIMUM_VERSION_CODE,
          latestVersionCode = 2,
          latestVersionName = "1.1.0"
        )
        try {
          docRef.set(
            mapOf(
              "minimumSupportedVersionCode" to initialConfig.minimumSupportedVersionCode,
              "latestVersionCode" to initialConfig.latestVersionCode,
              "latestVersionName" to initialConfig.latestVersionName,
              "updateTitleAr" to initialConfig.updateTitleAr,
              "updateMessageAr" to initialConfig.updateMessageAr,
              "releaseNotesAr" to initialConfig.releaseNotesAr,
              "updateUrl" to initialConfig.updateUrl,
              "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
          ).await()
        } catch (e: Exception) {
          Log.w(TAG, "Failed to seed default version config: ${e.message}")
        }
        initialConfig
      }

      _versionConfig.value = config
      val status = evaluateVersion(BuildConfig.VERSION_CODE, config)
      _updateStatus.value = status
      Log.i(TAG, "Version check completed: $status (Installed: ${BuildConfig.VERSION_CODE}, Min: ${config.minimumSupportedVersionCode})")
      status
    } catch (e: Exception) {
      Log.e(TAG, "Error checking version from remote backend: ${e.message}", e)
      // On network failure or offline, if installed version is >= default fallback, allow offline access
      val fallbackConfig = AppVersionConfig(
        minimumSupportedVersionCode = DEFAULT_MINIMUM_VERSION_CODE
      )
      val status = evaluateVersion(BuildConfig.VERSION_CODE, fallbackConfig)
      _updateStatus.value = status
      status
    }
  }

  /**
   * Publishes / Updates the remote app version configuration in Firestore (Admin only).
   */
  suspend fun publishVersionConfig(newConfig: AppVersionConfig): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).isEmpty()) {
        return@withContext Result.failure(Exception("Firebase غير متصل"))
      }
      val firestore = FirebaseFirestore.getInstance()
      val docRef = firestore.collection("app_config").document(VERSION_CONFIG_DOC)
      docRef.set(
        mapOf(
          "minimumSupportedVersionCode" to newConfig.minimumSupportedVersionCode,
          "latestVersionCode" to newConfig.latestVersionCode,
          "latestVersionName" to newConfig.latestVersionName,
          "isMandatoryUpdate" to newConfig.isMandatoryUpdate,
          "updateTitleAr" to newConfig.updateTitleAr,
          "updateMessageAr" to newConfig.updateMessageAr,
          "releaseNotesAr" to newConfig.releaseNotesAr,
          "updateUrl" to newConfig.updateUrl,
          "updatedAt" to System.currentTimeMillis()
        ),
        SetOptions.merge()
      ).await()

      _versionConfig.value = newConfig
      _updateStatus.value = evaluateVersion(BuildConfig.VERSION_CODE, newConfig)
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to publish version config: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Opens Google Play Store or download link for the app update.
   */
  fun openUpdateUrl(updateUrl: String? = null) {
    val targetUrl = updateUrl ?: _versionConfig.value.updateUrl
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to open update URL: ${e.message}")
    }
  }
}
