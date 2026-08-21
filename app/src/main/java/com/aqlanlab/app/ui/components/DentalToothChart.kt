package com.aqlanlab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// FDI Quadrants
val quadrant1UpperRight = listOf("18", "17", "16", "15", "14", "13", "12", "11")
val quadrant2UpperLeft = listOf("21", "22", "23", "24", "25", "26", "27", "28")
val quadrant3LowerLeft = listOf("31", "32", "33", "34", "35", "36", "37", "38")
val quadrant4LowerRight = listOf("48", "47", "46", "45", "44", "43", "42", "41")

@Composable
fun DentalToothChart(
  selectedTeeth: List<String>,
  onTeethSelectionChanged: (List<String>) -> Unit,
  modifier: Modifier = Modifier
) {
  val upperRight = quadrant1UpperRight
  val upperLeft = quadrant2UpperLeft
  val lowerRight = quadrant4LowerRight
  val lowerLeft = quadrant3LowerLeft

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
            imageVector = Icons.Default.HealthAndSafety,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "مخطط الأسنان التفاعلي (FDI Chart)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        if (selectedTeeth.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
          ) {
            Text(
              text = "${selectedTeeth.size} سن محدد",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Quick Selector Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val anterior = listOf("13", "12", "11", "21", "22", "23", "33", "32", "31", "41", "42", "43")
        val upperAll = upperRight + upperLeft
        val lowerAll = lowerRight + lowerLeft

        FilterChip(
          selected = false,
          onClick = {
            val updated = (selectedTeeth + anterior).distinct()
            onTeethSelectionChanged(updated)
          },
          label = { Text("الأمامية (Anterior)", fontSize = 11.sp) }
        )

        FilterChip(
          selected = false,
          onClick = {
            val updated = (selectedTeeth + upperAll).distinct()
            onTeethSelectionChanged(updated)
          },
          label = { Text("الفك العلوي (Upper)", fontSize = 11.sp) }
        )

        FilterChip(
          selected = false,
          onClick = {
            val updated = (selectedTeeth + lowerAll).distinct()
            onTeethSelectionChanged(updated)
          },
          label = { Text("الفك السفلي (Lower)", fontSize = 11.sp) }
        )

        if (selectedTeeth.isNotEmpty()) {
          FilledTonalButton(
            onClick = { onTeethSelectionChanged(emptyList()) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
          ) {
            Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("مسح", fontSize = 11.sp)
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Upper Arch Label
      Text(
        text = "الفك العلوي (Maxilla)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
      )

      // Upper Teeth Row (Right: 18..11 | Left: 21..28)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Upper Right (18 to 11)
        upperRight.forEach { tooth ->
          ToothItem(
            toothNumber = tooth,
            isSelected = selectedTeeth.contains(tooth),
            onClick = {
              val updated = if (selectedTeeth.contains(tooth)) {
                selectedTeeth - tooth
              } else {
                selectedTeeth + tooth
              }
              onTeethSelectionChanged(updated)
            }
          )
        }

        Box(
          modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(2.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )

        // Upper Left (21 to 28)
        upperLeft.forEach { tooth ->
          ToothItem(
            toothNumber = tooth,
            isSelected = selectedTeeth.contains(tooth),
            onClick = {
              val updated = if (selectedTeeth.contains(tooth)) {
                selectedTeeth - tooth
              } else {
                selectedTeeth + tooth
              }
              onTeethSelectionChanged(updated)
            }
          )
        }
      }

      // Midline horizontal separator
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      )

      // Lower Teeth Row (Right: 48..41 | Left: 31..38)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Lower Right (48 to 41)
        lowerRight.forEach { tooth ->
          ToothItem(
            toothNumber = tooth,
            isSelected = selectedTeeth.contains(tooth),
            onClick = {
              val updated = if (selectedTeeth.contains(tooth)) {
                selectedTeeth - tooth
              } else {
                selectedTeeth + tooth
              }
              onTeethSelectionChanged(updated)
            }
          )
        }

        Box(
          modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(2.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )

        // Lower Left (31 to 38)
        lowerLeft.forEach { tooth ->
          ToothItem(
            toothNumber = tooth,
            isSelected = selectedTeeth.contains(tooth),
            onClick = {
              val updated = if (selectedTeeth.contains(tooth)) {
                selectedTeeth - tooth
              } else {
                selectedTeeth + tooth
              }
              onTeethSelectionChanged(updated)
            }
          )
        }
      }

      // Lower Arch Label
      Text(
        text = "الفك السفلي (Mandible)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
      )

      // Selected list preview text
      if (selectedTeeth.isNotEmpty()) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "الأسنان المحددة:",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = selectedTeeth.sorted().joinToString(" ، "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ToothItem(
  toothNumber: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val isMolar = toothNumber.endsWith("6") || toothNumber.endsWith("7") || toothNumber.endsWith("8")
  val isPremolar = toothNumber.endsWith("4") || toothNumber.endsWith("5")
  val isAnterior = toothNumber.endsWith("1") || toothNumber.endsWith("2") || toothNumber.endsWith("3")

  val itemWidth = if (isMolar) 36.dp else if (isPremolar) 32.dp else 28.dp

  Box(
    modifier = Modifier
      .padding(horizontal = 2.dp, vertical = 2.dp)
      .width(itemWidth)
      .height(44.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
      )
      .border(
        width = if (isSelected) 1.5.dp else 1.dp,
        color = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp)
      )
      .clickable { onClick() }
      .testTag("tooth_$toothNumber"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = toothNumber,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
      )
      // Small visual indicator
      Box(
        modifier = Modifier
          .padding(top = 2.dp)
          .size(4.dp)
          .clip(CircleShape)
          .background(
            if (isSelected) MaterialTheme.colorScheme.onPrimary
            else if (isAnterior) Color(0xFF00ACC1)
            else if (isPremolar) Color(0xFF7E57C2)
            else Color(0xFF5C6BC0)
          )
      )
    }
  }
}
