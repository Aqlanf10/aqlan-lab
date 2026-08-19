package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.models.Shipment
import com.example.data.models.ShipmentStatus
import com.example.ui.components.ClinicInfo
import com.example.ui.components.DateUtils

object NotificationHelper {
  private const val TAG = "NotificationHelper"

  const val CHANNEL_ID_NEW_SHIPMENTS = "channel_aqlan_new_shipments"
  const val CHANNEL_ID_STATUS_CHANGES = "channel_aqlan_status_changes"
  const val CHANNEL_ID_URGENT_ALERTS = "channel_aqlan_urgent_alerts"

  private var isInitialized = false

  fun init(context: Context) {
    if (isInitialized) return
    createNotificationChannels(context)
    isInitialized = true
  }

  private fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      // 1. New Shipments Channel
      val newShipmentChannel = NotificationChannel(
        CHANNEL_ID_NEW_SHIPMENTS,
        "إرساليات جديدة 📦",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "إشعارات فورية عند إضافة إرسالية معملية جديدة لمركز د. عقلان الكامل"
        enableLights(true)
        lightColor = Color.BLUE
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 300, 200, 300)
      }

      // 2. Order Status Changes Channel
      val statusChangeChannel = NotificationChannel(
        CHANNEL_ID_STATUS_CHANGES,
        "تحديثات حالة الإرساليات 🔄",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "تنبيهات فورية عند تغيير حالة طلب (تم الإرسال، قيد التنفيذ، جاهز، تم الاستلام)"
        enableLights(true)
        lightColor = Color.GREEN
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 150, 250)
      }

      // 3. Urgent & Critical Alerts Channel
      val urgentChannel = NotificationChannel(
        CHANNEL_ID_URGENT_ALERTS,
        "تنبيهات الحالات المستعجلة 🚨",
        NotificationManager.IMPORTANCE_MAX
      ).apply {
        description = "تنبيهات الحالات الطارئة ومواعيد التسليم المتأخرة"
        enableLights(true)
        lightColor = Color.RED
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
      }

      notificationManager.createNotificationChannels(
        listOf(newShipmentChannel, statusChangeChannel, urgentChannel)
      )
    }
  }

  private fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  /**
   * Alerts staff when a new shipment is registered in the clinic
   */
  fun showNewShipmentNotification(
    context: Context,
    shipment: Shipment,
    createdByName: String? = null
  ) {
    init(context)
    if (!hasNotificationPermission(context)) {
      Log.w(TAG, "Notification permission not granted")
      return
    }

    try {
      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("NAV_ROUTE", "shipment_detail/${shipment.id}")
        putExtra("SHIPMENT_ID", shipment.id)
      }

      val pendingIntent = PendingIntent.getActivity(
        context,
        ("new_${shipment.id}").hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val creatorText = if (!createdByName.isNullOrBlank()) "بواسطة $createdByName" else ""
      val urgentTag = if (shipment.isUrgent) "🚨 [مستعجل جداً] " else ""
      val title = "${urgentTag}إرسالية جديدة: ${shipment.patientName}"
      val content = "المعمل: ${shipment.labName} • ${shipment.workTypeName} (${shipment.pieceCount} قطع/أسنان) $creatorText"

      val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val formattedDate = DateUtils.formatShortDate(shipment.expectedDeliveryDate)

      val notificationBuilder = NotificationCompat.Builder(context, if (shipment.isUrgent) CHANNEL_ID_URGENT_ALERTS else CHANNEL_ID_NEW_SHIPMENTS)
        .setSmallIcon(R.drawable.ic_aqlan_logo)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("$content\n🗓 تاريخ التسليم المتوقع: $formattedDate\n🦷 تدرج اللون: ${shipment.shade.ifEmpty { "غير محدد" }}\n🏥 ${ClinicInfo.CLINIC_SHORT_NAME}")
        )
        .setAutoCancel(true)
        .setSound(defaultSoundUri)
        .setPriority(if (shipment.isUrgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setColor(0xFF2563EB.toInt())

      with(NotificationManagerCompat.from(context)) {
        notify(shipment.id.toInt().coerceAtLeast(100), notificationBuilder.build())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error displaying new shipment notification", e)
    }
  }

  /**
   * Alerts staff when an existing order's status changes
   */
  fun showStatusChangeNotification(
    context: Context,
    shipment: Shipment,
    oldStatus: ShipmentStatus,
    newStatus: ShipmentStatus,
    updatedByName: String? = null
  ) {
    init(context)
    if (!hasNotificationPermission(context)) {
      Log.w(TAG, "Notification permission not granted")
      return
    }

    try {
      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("NAV_ROUTE", "shipment_detail/${shipment.id}")
        putExtra("SHIPMENT_ID", shipment.id)
      }

      val pendingIntent = PendingIntent.getActivity(
        context,
        ("status_${shipment.id}_${newStatus.name}").hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val statusIcon = when (newStatus) {
        ShipmentStatus.NEW -> "📦"
        ShipmentStatus.IN_PROGRESS -> "⚙️"
        ShipmentStatus.READY -> "✅"
        ShipmentStatus.RECEIVED -> "📥"
        ShipmentStatus.CANCELLED -> "❌"
      }

      val title = "$statusIcon تحديث حالة: ${shipment.patientName}"
      val userSuffix = if (!updatedByName.isNullOrBlank()) " • بواسطة $updatedByName" else ""
      val content = "تغيرت الحالة من [${oldStatus.titleAr}] إلى [${newStatus.titleAr}] • ${shipment.labName}$userSuffix"

      val channelId = if (newStatus == ShipmentStatus.READY || shipment.isUrgent) {
        CHANNEL_ID_URGENT_ALERTS
      } else {
        CHANNEL_ID_STATUS_CHANGES
      }

      val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

      val notificationBuilder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_aqlan_logo)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("$content\nنوع العمل: ${shipment.workTypeName}\nالمعمل: ${shipment.labName}\n🏥 ${ClinicInfo.CLINIC_SHORT_NAME}")
        )
        .setAutoCancel(true)
        .setSound(defaultSoundUri)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setColor(
          when (newStatus) {
            ShipmentStatus.READY -> 0xFF10B981.toInt()
            ShipmentStatus.IN_PROGRESS -> 0xFF3B82F6.toInt()
            ShipmentStatus.RECEIVED -> 0xFF6366F1.toInt()
            ShipmentStatus.CANCELLED -> 0xFFEF4444.toInt()
            ShipmentStatus.NEW -> 0xFFF59E0B.toInt()
          }
        )

      with(NotificationManagerCompat.from(context)) {
        notify((shipment.id + 5000).toInt(), notificationBuilder.build())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error displaying status change notification", e)
    }
  }
}
