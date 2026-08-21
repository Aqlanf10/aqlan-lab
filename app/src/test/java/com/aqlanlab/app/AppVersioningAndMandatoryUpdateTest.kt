package com.aqlanlab.app

import com.aqlanlab.app.network.AppUpdateStatus
import com.aqlanlab.app.network.AppVersionConfig
import com.aqlanlab.app.network.AppVersionManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppVersioningAndMandatoryUpdateTest
 *
 * Tests:
 * 1. Semantic Versioning format compliance (e.g. 1.1.0, 1.2.0, etc.).
 * 2. Version Code correctness (versionCode = 2 corresponds to versionName = "1.1.0").
 * 3. Mandatory Update Trigger when currentVersionCode < minimumSupportedVersionCode.
 * 4. Supported Version Behavior when currentVersionCode >= minimumSupportedVersionCode.
 * 5. Optional Update Trigger when currentVersionCode < latestVersionCode but >= minimumSupportedVersionCode.
 * 6. Up-To-Date Behavior when currentVersionCode == latestVersionCode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppVersioningAndMandatoryUpdateTest {

  private val appVersionManager = AppVersionManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())

  @Test
  fun testSemanticVersioningCompliance() {
    val versionName = BuildConfig.VERSION_NAME
    val semVerRegex = Regex("""^\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$""")
    assertTrue(
      "versionName '$versionName' must strictly follow Semantic Versioning (X.Y.Z)",
      semVerRegex.matches(versionName)
    )
    assertEquals("1.1.0", versionName)
    assertEquals(2, BuildConfig.VERSION_CODE)
  }

  @Test
  fun testMandatoryUpdateTriggeredWhenVersionIsUnsupported() {
    val remoteConfig = AppVersionConfig(
      minimumSupportedVersionCode = 2,
      latestVersionCode = 3,
      latestVersionName = "1.2.0",
      updateTitleAr = "تحديث أمني إجباري",
      updateMessageAr = "يرجى التحديث لحماية البيانات"
    )

    // Case 1: Outdated client with old versionCode = 1 (e.g. v1.0)
    val oldInstalledVersionCode = 1
    val status = appVersionManager.evaluateVersion(oldInstalledVersionCode, remoteConfig)

    assertTrue("Status must be MandatoryUpdateRequired", status is AppUpdateStatus.MandatoryUpdateRequired)
    val mandatory = status as AppUpdateStatus.MandatoryUpdateRequired
    assertTrue("isMandatoryUpdate must be true", mandatory.config.isMandatoryUpdate)
    assertEquals(2, mandatory.config.minimumSupportedVersionCode)
    assertEquals("1.2.0", mandatory.config.latestVersionName)
  }

  @Test
  fun testSupportedVersionAllowsAppAccess() {
    val remoteConfig = AppVersionConfig(
      minimumSupportedVersionCode = 2,
      latestVersionCode = 2,
      latestVersionName = "1.1.0"
    )

    // Case 2: Client running current supported version (versionCode = 2, v1.1.0)
    val installedVersionCode = 2
    val status = appVersionManager.evaluateVersion(installedVersionCode, remoteConfig)

    assertTrue("Status must be UpToDate for current supported release", status is AppUpdateStatus.UpToDate)
  }

  @Test
  fun testOptionalUpdateTriggeredWhenAboveMinimumButBelowLatest() {
    val remoteConfig = AppVersionConfig(
      minimumSupportedVersionCode = 2, // 1.1.0 is still supported
      latestVersionCode = 4,           // 1.3.0 is out
      latestVersionName = "1.3.0"
    )

    // Case 3: Client running versionCode = 2 (Supported, but newer version exists)
    val installedVersionCode = 2
    val status = appVersionManager.evaluateVersion(installedVersionCode, remoteConfig)

    assertTrue("Status must be OptionalUpdateAvailable", status is AppUpdateStatus.OptionalUpdateAvailable)
    val optional = status as AppUpdateStatus.OptionalUpdateAvailable
    assertFalse("isMandatoryUpdate should be false", optional.config.isMandatoryUpdate)
    assertEquals(4, optional.config.latestVersionCode)
  }

  @Test
  fun testAppConfigSecurityAndDocPath() {
    assertEquals("version_config", AppVersionManager.VERSION_CONFIG_DOC)
    assertEquals(2, AppVersionManager.DEFAULT_MINIMUM_VERSION_CODE)
  }
}
