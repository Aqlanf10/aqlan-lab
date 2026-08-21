package com.aqlanlab.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.aqlanlab.app.data.models.ShipmentStatus
import com.aqlanlab.app.data.models.UserRole
import com.aqlanlab.app.ui.components.PriceDisplay
import com.aqlanlab.app.ui.components.RoleBadge
import com.aqlanlab.app.ui.components.StatCard
import com.aqlanlab.app.ui.components.StatusBadge
import com.aqlanlab.app.ui.theme.DentalLabTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun dentalLabComponents_screenshot() {
    composeTestRule.setContent {
      DentalLabTheme {
        StatCard(
          title = "إرساليات اليوم",
          value = "18",
          subtitle = "+4 إرساليات جديدة",
          icon = Icons.AutoMirrored.Filled.Assignment,
          modifier = Modifier.padding(16.dp)
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
