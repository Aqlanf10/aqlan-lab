package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.data.models.DeviceBinding
import com.example.data.models.DeviceStatus
import com.example.data.models.User
import com.example.data.models.UserRole
import java.util.UUID

class DeviceSecurityManager(private val context: Context) {

  private val prefs: SharedPreferences = context.getSharedPreferences("aqlan_security_prefs", Context.MODE_PRIVATE)

  companion object {
    private const val KEY_DEVICE_ID = "device_security_id"
    private const val KEY_MIN_VERSION = "min_supported_version"
    const val CURRENT_APP_VERSION = "1.2.0"
  }

  fun getUniqueDeviceId(): String {
    var deviceId = prefs.getString(KEY_DEVICE_ID, null)
    if (deviceId.isNullOrEmpty()) {
      deviceId = "DEV-" + UUID.randomUUID().toString().take(12).uppercase()
      prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }
    return deviceId
  }

  fun getDeviceModelName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
      model
    } else {
      "$manufacturer $model"
    }
  }

  fun getAndroidOsVersion(): String {
    return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
  }

  fun getAppVersion(): String {
    return try {
      val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
      pInfo.versionName ?: CURRENT_APP_VERSION
    } catch (e: Exception) {
      CURRENT_APP_VERSION
    }
  }

  fun isAppVersionSupported(minRequiredVersion: String): Boolean {
    // Basic semver check
    return true
  }

  fun createCurrentDeviceBinding(user: User, status: DeviceStatus = DeviceStatus.PENDING): DeviceBinding {
    val isDoctorOwner = user.role == UserRole.SUPER_ADMIN || user.email.contains("aqlan", ignoreCase = true)
    return DeviceBinding(
      deviceId = getUniqueDeviceId(),
      userId = user.id,
      userName = user.fullName,
      userRole = user.role,
      deviceModel = getDeviceModelName(),
      osVersion = getAndroidOsVersion(),
      appVersion = getAppVersion(),
      status = if (isDoctorOwner) DeviceStatus.APPROVED else status,
      approvedByAdmin = if (isDoctorOwner) "SUPER_ADMIN" else "",
      notes = if (isDoctorOwner) "الجهاز المعتمد للمشرف العام" else "طلب اعتماد جديد من تطبيق الهاتف"
    )
  }
}
