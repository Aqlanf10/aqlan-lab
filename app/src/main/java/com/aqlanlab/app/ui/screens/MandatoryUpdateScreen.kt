package com.aqlanlab.app.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.network.AppVersionConfig

/**
 * MandatoryUpdateScreen
 * A non-dismissible, blocking barrier screen displayed when the installed app version
 * is strictly less than minimumSupportedVersionCode.
 *
 * No back button or navigation affordance allows the user to bypass this screen.
 */
@Composable
fun MandatoryUpdateScreen(
  versionConfig: AppVersionConfig,
  onUpdateClick: () -> Unit,
  onRetryCheck: () -> Unit,
  isChecking: Boolean = false,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          )
        )
      )
      .padding(24.dp)
      .testTag("mandatory_update_screen"),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .testTag("mandatory_update_card"),
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
          .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Icon Badge
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.SecurityUpdateWarning,
            contentDescription = "تحديث أمني إجباري",
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.error
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
          text = versionConfig.updateTitleAr.ifEmpty { "تحديث أمني إجباري مطلوب" },
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Version Comparison Badge
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "إصدارك الحالي: v${versionConfig.currentAppVersionName} (${versionConfig.currentAppVersionCode})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
              text = "➜",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "المطلوب: v${versionConfig.latestVersionName} (${versionConfig.minimumSupportedVersionCode}+)",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Explanation Message
        Text(
          text = versionConfig.updateMessageAr.ifEmpty {
            "تم إطلاق ترقية أمنية هامة لنظام مركز الدكتور عقلان الكامل. لا يمكن المتابعة بالإصدار الحالي لضمان تشفير وسلامة البيانات."
          },
          style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        // Release Notes
        if (versionConfig.releaseNotesAr.isNotBlank()) {
          Spacer(modifier = Modifier.height(16.dp))
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
          ) {
            Column(
              modifier = Modifier.padding(16.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.NewReleases,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "أبرز التحديثات:",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = versionConfig.releaseNotesAr,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Action: Update Now
        Button(
          onClick = onUpdateClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("update_now_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "تحديث التطبيق الآن",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Action: Re-check / Retry
        OutlinedButton(
          onClick = onRetryCheck,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("retry_check_button"),
          shape = RoundedCornerShape(14.dp),
          enabled = !isChecking
        ) {
          if (isChecking) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("جاري فحص الإصدار...")
          } else {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("التحقق مرة أخرى بعد التحديث")
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Exit App Button
        TextButton(
          onClick = {
            (context as? Activity)?.finishAffinity()
          },
          modifier = Modifier.testTag("exit_app_button")
        ) {
          Text(
            text = "إغلاق التطبيق",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium
          )
        }
      }
    }
  }
}
