package com.aqlanlab.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aqlanlab.app.ui.navigation.MainAppScaffold
import com.aqlanlab.app.ui.theme.DentalLabTheme
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import com.aqlanlab.app.util.NotificationHelper

class MainActivity : ComponentActivity() {
  private val labViewModel: DentalLabViewModel by viewModels()

  private val requestNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    // Notification permission response handled
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // NOTE: Firebase / AppCheck / NotificationHelper initialization happens once in
    // AqlanLabApplication.onCreate(); the duplicated (and try/catch-wrapped) copies
    // that used to live here were removed.

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
          requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
    } catch (e: Throwable) {
      android.util.Log.w("MainActivity", "Notification permission note: ${e.message}")
    }

    // FIX: notification taps now deep-link into the relevant screen. The extras were
    // always attached by NotificationHelper but never read, so every tap opened the
    // Dashboard.
    handleNotificationIntent(intent)

    setContent {
      DentalLabTheme {
        // Arabic Right-to-Left (RTL) Layout Direction
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .fillMaxHeight()
                  .widthIn(max = 480.dp)
                  .fillMaxWidth()
              ) {
                MainAppScaffold(viewModel = labViewModel)
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleNotificationIntent(intent)
  }

  private fun handleNotificationIntent(intent: Intent?) {
    try {
      val route = intent?.getStringExtra("NAV_ROUTE") ?: return
      if (route.isNotBlank()) {
        labViewModel.queueDeepLinkRoute(route)
      }
    } catch (e: Throwable) {
      android.util.Log.w("MainActivity", "Notification intent handling note: ${e.message}")
    }
  }

  override fun onStop() {
    super.onStop()
    // Makes the user-visible "قفل التطبيق عند الفتح" setting real: when enabled, the
    // app requires the PIN/biometric again after returning from the background.
    try {
      labViewModel.lockApp()
    } catch (e: Throwable) {
      android.util.Log.w("MainActivity", "lockApp note: ${e.message}")
    }
  }
}
