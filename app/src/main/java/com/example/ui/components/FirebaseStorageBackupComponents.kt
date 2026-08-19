package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AutoBackupFrequency
import com.example.network.FirebaseStorageBackupInfo
import com.example.network.SyncState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enterprise Firebase Storage Backup Components & Cards
 */
@Composable
fun FirebaseStorageBackupSectionCard(
  isOnline: Boolean,
  backupState: SyncState,
  statusMessage: String,
  lastBackupTimestamp: Long?,
  isAutoBackupEnabled: Boolean,
  autoBackupFrequency: AutoBackupFrequency,
  onAutoBackupToggle: (Boolean) -> Unit,
  onAutoBackupFrequencyChange: (AutoBackupFrequency) -> Unit,
  onTriggerBackup: () -> Unit,
  availableBackups: List<FirebaseStorageBackupInfo>,
  onRestoreBackup: (FirebaseStorageBackupInfo) -> Unit,
  onRefreshBackups: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var showFrequencyMenu by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth().testTag("firebase_storage_backup_section"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF57C00).copy(alpha = 0.4f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header: Firebase Storage Branding
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFFFF3E0)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.CloudSync,
              contentDescription = null,
              tint = Color(0xFFE65100),
              modifier = Modifier.size(26.dp)
            )
          }
          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "النسخ السحابي التلقائي (Firebase Storage)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
              ) {
                Text(
                  text = if (isOnline) "🟢 متصل بالسحابة" else "🔴 غير متصل",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
            Text(
              text = if (lastBackupTimestamp != null)
                "آخر نسخة مرفوعة: ${SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date(lastBackupTimestamp))}"
              else
                "لم يتم الرفع مسبقاً - اضغط لرفع أول نسخة",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (backupState == SyncState.SYNCING) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFE65100), strokeWidth = 2.5.dp)
        }
      }

      // Status message box if present
      if (statusMessage.isNotEmpty()) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = when (backupState) {
            SyncState.ERROR -> MaterialTheme.colorScheme.errorContainer
            SyncState.SUCCESS -> Color(0xFFE8F5E9)
            SyncState.SYNCING -> Color(0xFFFFF3E0)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = when (backupState) {
                SyncState.ERROR -> Icons.Default.ErrorOutline
                SyncState.SUCCESS -> Icons.Default.CheckCircle
                else -> Icons.Default.Info
              },
              contentDescription = null,
              tint = when (backupState) {
                SyncState.ERROR -> MaterialTheme.colorScheme.error
                SyncState.SUCCESS -> Color(0xFF2E7D32)
                else -> Color(0xFFE65100)
              },
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = statusMessage,
              style = MaterialTheme.typography.bodySmall,
              color = when (backupState) {
                SyncState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                SyncState.SUCCESS -> Color(0xFF1B5E20)
                else -> Color(0xFFBF360C)
              }
            )
          }
        }
      }

      // Auto Backup Master Toggle
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "النسخ الاحتياطي التلقائي الذكي",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "حفظ فوري لمعلومات المرضى والإرساليات لمنع أي فقدان للبيانات",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = isAutoBackupEnabled,
              onCheckedChange = onAutoBackupToggle,
              modifier = Modifier.testTag("auto_backup_storage_switch")
            )
          }

          if (isAutoBackupEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "توقيت النسخ التلقائي:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
              )

              Box {
                FilterChip(
                  selected = true,
                  onClick = { showFrequencyMenu = true },
                  label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(autoBackupFrequency.titleAr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                  }
                )

                DropdownMenu(
                  expanded = showFrequencyMenu,
                  onDismissRequest = { showFrequencyMenu = false }
                ) {
                  AutoBackupFrequency.values().forEach { freq ->
                    DropdownMenuItem(
                      text = { Text(freq.titleAr, fontWeight = if (freq == autoBackupFrequency) FontWeight.Bold else FontWeight.Normal) },
                      onClick = {
                        onAutoBackupFrequencyChange(freq)
                        showFrequencyMenu = false
                      },
                      leadingIcon = {
                        if (freq == autoBackupFrequency) {
                          Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                      }
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Action Button: Instant Cloud Upload
      Button(
        onClick = onTriggerBackup,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("upload_to_firebase_storage_button"),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
        shape = RoundedCornerShape(12.dp),
        enabled = backupState != SyncState.SYNCING
      ) {
        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("إنشاء ورفع نسخة احتياطية فورية الآن إلى Storage", fontWeight = FontWeight.Bold)
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      // List Header of Available Storage Backups
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
          Text(
            text = "أرشيف النسخ السحابية على Storage (${availableBackups.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }
        TextButton(onClick = onRefreshBackups) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("تحديث الأرشيف", fontSize = 12.sp)
        }
      }

      if (availableBackups.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
            Text("لا توجد نسخ احتياطية مسجلة حتى الآن على Storage.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("اضغط على زر الرفع أعلاه لإنشاء وتأمين أول نسخة سحابية للمركز.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          availableBackups.forEach { backup ->
            FirebaseStorageBackupItem(
              backup = backup,
              onRestore = { onRestoreBackup(backup) },
              onCopyLink = {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = if (backup.downloadUrl.isNotEmpty()) backup.downloadUrl else backup.storagePath
                clip.setPrimaryClip(ClipData.newPlainText("Backup Path", text))
                Toast.makeText(context, "تم نسخ مسار النسخة السحابية", Toast.LENGTH_SHORT).show()
              }
            )
          }
        }
      }
    }
  }
}

@Composable
fun FirebaseStorageBackupItem(
  backup: FirebaseStorageBackupInfo,
  onRestore: () -> Unit,
  onCopyLink: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dateFormatted = remember(backup.timestamp) {
    SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date(backup.timestamp))
  }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(if (backup.isAutoBackup) Color(0xFFE3F2FD) else Color(0xFFFFF3E0)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (backup.isAutoBackup) Icons.Default.Autorenew else Icons.Default.Person,
              contentDescription = null,
              tint = if (backup.isAutoBackup) Color(0xFF1565C0) else Color(0xFFE65100),
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = if (backup.isAutoBackup) "نسخة تلقائية ذكية" else "نسخة يدوية (${backup.createdByName})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "$dateFormatted • ${backup.formattedSize}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xFFE8F5E9)
        ) {
          Text(
            text = "${backup.totalRecords} سجل",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // Breakdown tags (Shipments / Patients / Labs)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.surface
        ) {
          Text(
            text = "📋 ${backup.shipmentsCount} إرسالية/مريض",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.surface
        ) {
          Text(
            text = "🏢 ${backup.labsCount} معمل",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.surface
        ) {
          Text(
            text = "💳 ${backup.paymentsCount} دفعة",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // Action row: Restore & Copy
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onCopyLink,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("نسخ المسار", fontSize = 11.sp)
        }

        Spacer(Modifier.width(6.dp))

        Button(
          onClick = onRestore,
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("استعادة هذه النسخة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
