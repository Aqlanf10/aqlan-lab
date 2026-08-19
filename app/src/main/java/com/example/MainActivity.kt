package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.MainAppScaffold
import com.example.ui.theme.DentalLabTheme
import com.example.ui.viewmodel.DentalLabViewModel
import com.example.util.NotificationHelper

/**
 * تعتمد الآن [FragmentActivity] بدل ComponentActivity لأن [androidx.biometric.BiometricPrompt]
 * يتطلبها لعرض نافذة التحقق البيومتري الحقيقية من النظام.
 */
class MainActivity : FragmentActivity() {
  private val requestNotificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    // Notification permission response handled
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // يمنع التقاط صور الشاشة وظهور محتوى الشاشة (أسماء المرضى، الحسابات
    // المالية) في معاينة قائمة التطبيقات الأخيرة أو في تسجيلات الشاشة.
    window.setFlags(
      WindowManager.LayoutParams.FLAG_SECURE,
      WindowManager.LayoutParams.FLAG_SECURE
    )

    enableEdgeToEdge()

    NotificationHelper.init(this)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    setContent {
      val labViewModel: DentalLabViewModel = viewModel()

      // إعادة القفل التلقائي عند خروج التطبيق إلى الخلفية.
      //
      // سابقاً كان التطبيق يبقى مفتوحاً بصلاحيات كاملة إلى ما لا نهاية بعد أول
      // دخول: من يفتح الجهاز لاحقاً — أو يلتقطه في العيادة المزدحمة — يجد كل
      // بيانات المرضى والحسابات مفتوحة أمامه.
      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_STOP && labViewModel.appLockEnabled.value) {
            labViewModel.lockApp()
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

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
}
