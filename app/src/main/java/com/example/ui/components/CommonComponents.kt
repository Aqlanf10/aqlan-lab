package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Shipment
import com.example.data.models.ShipmentStatus
import com.example.data.models.UserRole
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatusBadge(
  status: ShipmentStatus,
  isLate: Boolean = false,
  modifier: Modifier = Modifier
) {
  val (bgColor, textColor, label) = when {
    isLate -> Triple(StatusLateContainer, StatusLate, "متأخرة ⚠️")
    status == ShipmentStatus.NEW -> Triple(StatusNewContainer, StatusNew, status.titleAr)
    status == ShipmentStatus.IN_PROGRESS -> Triple(StatusInProgressContainer, StatusInProgress, status.titleAr)
    status == ShipmentStatus.READY -> Triple(StatusReadyContainer, StatusReady, status.titleAr)
    status == ShipmentStatus.RECEIVED -> Triple(StatusReceivedContainer, StatusReceived, status.titleAr)
    status == ShipmentStatus.CANCELLED -> Triple(StatusCancelledContainer, StatusCancelled, status.titleAr)
    else -> Triple(Color.LightGray.copy(alpha = 0.2f), Color.DarkGray, status.titleAr)
  }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = bgColor,
    border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.25f)),
    modifier = modifier
  ) {
    Text(
      text = label,
      color = textColor,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

@Composable
fun RoleBadge(
  role: UserRole,
  modifier: Modifier = Modifier
) {
  val (color, label) = when (role) {
    UserRole.ADMIN -> Pair(RoleAdminColor, role.titleAr)
    UserRole.STAFF -> Pair(RoleStaffColor, role.titleAr)
    UserRole.ACCOUNTANT -> Pair(RoleAccountantColor, role.titleAr)
  }

  Surface(
    shape = RoundedCornerShape(6.dp),
    color = color.copy(alpha = 0.12f),
    border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.3f)),
    modifier = modifier
  ) {
    Text(
      text = label,
      color = color,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      fontSize = 10.sp,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
  }
}

@Composable
fun PriceDisplay(
  amount: Double,
  userRole: UserRole,
  currencyCode: String = "SAR",
  style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
  color: Color = MaterialTheme.colorScheme.onSurface,
  modifier: Modifier = Modifier
) {
  if (userRole == UserRole.STAFF) {
    Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = "محمي للإدارة",
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = "محمي",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        fontWeight = FontWeight.Medium
      )
    }
  } else {
    val currency = com.example.data.models.AppCurrency.fromCode(currencyCode)
    val formattedText = currency.formatAmount(amount)

    Text(
      text = formattedText,
      style = style,
      color = color,
      fontWeight = FontWeight.Bold,
      modifier = modifier
    )
  }
}

@Composable
fun CurrencyBadge(
  currency: com.example.data.models.AppCurrency,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false
) {
  val (bgColor, textColor) = when (currency) {
    com.example.data.models.AppCurrency.YER -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32)) // Greenish for YER
    com.example.data.models.AppCurrency.SAR -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0)) // Blue for SAR
    com.example.data.models.AppCurrency.USD -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100)) // Amber/Gold for USD
  }

  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (isSelected) textColor else bgColor,
    border = androidx.compose.foundation.BorderStroke(0.8.dp, textColor.copy(alpha = 0.5f)),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = currency.flag,
        fontSize = 11.sp
      )
      Text(
        text = currency.symbolAr,
        color = if (isSelected) Color.White else textColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
      )
    }
  }
}

@Composable
fun CurrencySelector(
  selectedCurrency: com.example.data.models.AppCurrency,
  onCurrencySelected: (com.example.data.models.AppCurrency) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (label != null) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      com.example.data.models.AppCurrency.ALL.forEach { curr ->
        val isSelected = curr == selectedCurrency
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCurrencySelected(curr) },
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Text(text = curr.flag, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = curr.symbolAr,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}

@Composable
fun StatCard(
  title: String,
  value: String,
  subtitle: String? = null,
  icon: ImageVector? = null,
  accentColor: Color = PolishPrimary,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Medium
        )
        if (icon != null) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = accentColor
      )

      if (!subtitle.isNullOrEmpty()) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = accentColor.copy(alpha = 0.08f)
        ) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }
  }
}

@Composable
fun EmptyStateView(
  title: String,
  description: String,
  icon: ImageVector? = null,
  actionButton: (@Composable () -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 32.dp, horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    if (icon != null) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      }
      Spacer(Modifier.height(16.dp))
    }

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(6.dp))

    Text(
      text = description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    if (actionButton != null) {
      Spacer(Modifier.height(16.dp))
      actionButton()
    }
  }
}

object DateUtils {
  private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
  private val shortDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

  fun formatDateTime(timestamp: Long): String {
    return dateTimeFormat.format(Date(timestamp))
  }

  fun formatShortDate(timestamp: Long): String {
    return shortDateFormat.format(Date(timestamp))
  }

  fun isLate(expectedDeliveryTimestamp: Long, status: ShipmentStatus): Boolean {
    if (status == ShipmentStatus.RECEIVED || status == ShipmentStatus.CANCELLED) {
      return false
    }
    return System.currentTimeMillis() > expectedDeliveryTimestamp
  }
}

@Composable
fun ShipmentCardItem(
  shipment: Shipment,
  userRole: UserRole,
  currency: String = "USD",
  currencyCode: String = currency,
  onClick: () -> Unit,
  onQuickStatusChange: ((ShipmentStatus) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val isLate = DateUtils.isLate(shipment.expectedDeliveryDate, shipment.status)

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = if (isLate) {
      androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    } else {
      androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    },
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header: Number + Urgent Tag + Status Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
          ) {
            Text(
              text = shipment.shipmentNumber,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }

          if (shipment.isUrgent) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.errorContainer
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Icon(
                  Icons.Default.Bolt,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(12.dp)
                )
                Text(
                  text = "عاجل",
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp
                )
              }
            }
          }
        }

        StatusBadge(status = shipment.status, isLate = isLate)
      }

      // Title & Piece Count
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = shipment.workTypeName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant
        ) {
          Text(
            text = "${shipment.pieceCount} قطعة",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      // Details: Lab & Doctor
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            Icons.Default.Apartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = "المعمل: ${shipment.labName}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = "د. ${shipment.clinicOrDoctorName}${if (shipment.patientName.isNotEmpty()) " | المريض: ${shipment.patientName}" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Dental Tooth numbers + Shade pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (shipment.toothNumbers.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "الأسنان (FDI):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = shipment.toothNumbers,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // Shade Badge
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5EEDC))
                .border(0.5.dp, Color.Gray, CircleShape)
            )
            Text(
              text = "لون VITA: ${shipment.shade}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Footer: Delivery Date & Financial total
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            Icons.Default.Event,
            contentDescription = null,
            tint = if (isLate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = if (isLate) "متأخرة! التسليم كان: ${DateUtils.formatShortDate(shipment.expectedDeliveryDate)}"
            else "التسليم: ${DateUtils.formatShortDate(shipment.expectedDeliveryDate)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isLate) FontWeight.Bold else FontWeight.Normal
          )
        }

        PriceDisplay(
          amount = shipment.totalPrice,
          userRole = userRole,
          currencyCode = currencyCode,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

