package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Natural tooth enamel hue approximations for visual swatches
data class ToothShadeInfo(
  val code: String,
  val nameAr: String,
  val group: String,
  val tintColor: Color
)

val standardShades = listOf(
  // VITA Classical A (Reddish-brownish)
  ToothShadeInfo("A1", "A1 (فاتح مائل للحمرة)", "VITA A", Color(0xFFF9F7F1)),
  ToothShadeInfo("A2", "A2 (طبيعي متوسط الأكثر طلباً)", "VITA A", Color(0xFFF4EFE6)),
  ToothShadeInfo("A3", "A3 (طبيعي دافئ)", "VITA A", Color(0xFFEEE5D5)),
  ToothShadeInfo("A3.5", "A3.5 (داكن محمر)", "VITA A", Color(0xFFE5D8C1)),
  ToothShadeInfo("A4", "A4 (بني محمر غامق)", "VITA A", Color(0xFFDCCBB1)),

  // VITA Classical B (Reddish-yellowish)
  ToothShadeInfo("B1", "B1 (أفتح درجة طبيعية مائلة للصفرة)", "VITA B", Color(0xFFFAF9F4)),
  ToothShadeInfo("B2", "B2 (أصفر ناصع)", "VITA B", Color(0xFFF6F2E7)),
  ToothShadeInfo("B3", "B3 (أصفر دافئ)", "VITA B", Color(0xFFEFE7D5)),
  ToothShadeInfo("B4", "B4 (أصفر داكن)", "VITA B", Color(0xFFE6DBC3)),

  // VITA Classical C (Greyish)
  ToothShadeInfo("C1", "C1 (رمادي فاتح)", "VITA C", Color(0xFFF2F2EE)),
  ToothShadeInfo("C2", "C2 (رمادي متوسط)", "VITA C", Color(0xFFEBEBE5)),
  ToothShadeInfo("C3", "C3 (رمادي داكن)", "VITA C", Color(0xFFDFDFD7)),
  ToothShadeInfo("C4", "C4 (رمادي غامق)", "VITA C", Color(0xFFD4D4CA)),

  // VITA Classical D (Reddish-grey)
  ToothShadeInfo("D2", "D2 (رمادي محمر فاتح)", "VITA D", Color(0xFFF5EFE9)),
  ToothShadeInfo("D3", "D3 (رمادي محمر متوسط)", "VITA D", Color(0xFFEDE3D9)),
  ToothShadeInfo("D4", "D4 (رمادي محمر داكن)", "VITA D", Color(0xFFE2D6CA)),

  // Bleach Shades (Hollywood White)
  ToothShadeInfo("BL1", "BL1 (هوليوود سوبر وايت)", "Bleach", Color(0xFFFFFFFF)),
  ToothShadeInfo("BL2", "BL2 (تبييض ناصع عالي)", "Bleach", Color(0xFFFDFAF6)),
  ToothShadeInfo("BL3", "BL3 (تبييض طبيعي)", "Bleach", Color(0xFFFAF7F0)),
  ToothShadeInfo("BL4", "BL4 (تبييض هادئ)", "Bleach", Color(0xFFF7F3E9))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadeSelector(
  selectedShade: String,
  shadeNotes: String,
  onShadeSelected: (String) -> Unit,
  onShadeNotesChanged: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedGroup by remember { mutableStateOf("الكل") }
  var isCustomShadeMode by remember { mutableStateOf(false) }
  var customShadeInput by remember { mutableStateOf("") }

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
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
            imageVector = Icons.Default.ColorLens,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "لون وتدرج الأسنان (Tooth Shade)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        // Active Shade Badge with Color Swatch
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val matchingShade = standardShades.find { it.code.equals(selectedShade, ignoreCase = true) }
            Box(
              modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(matchingShade?.tintColor ?: Color(0xFFF4EFE6))
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
            )
            Text(
              text = if (selectedShade.isNotEmpty()) "اللون: $selectedShade" else "اختر اللون",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Group Filter Tabs
      val groups = listOf("الكل", "VITA A", "VITA B", "VITA C", "VITA D", "Bleach", "مخصص")
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        groups.forEach { group ->
          FilterChip(
            selected = selectedGroup == group,
            onClick = {
              selectedGroup = group
              if (group == "مخصص") {
                isCustomShadeMode = true
              } else {
                isCustomShadeMode = false
              }
            },
            label = { Text(group, fontSize = 12.sp) }
          )
        }
      }

      // Shade Chips Grid / Row
      if (!isCustomShadeMode) {
        val filteredShades = if (selectedGroup == "الكل") standardShades
        else standardShades.filter { it.group == selectedGroup }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          filteredShades.forEach { shade ->
            val isSelected = selectedShade.equals(shade.code, ignoreCase = true)
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
              border = androidx.compose.foundation.BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
              ),
              modifier = Modifier
                .clickable { onShadeSelected(shade.code) }
                .testTag("shade_${shade.code}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Swatch preview circle
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(shade.tintColor)
                    .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                )
                Text(
                  text = shade.code,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      } else {
        // Custom Shade input
        OutlinedTextField(
          value = customShadeInput,
          onValueChange = {
            customShadeInput = it
            onShadeSelected(it)
          },
          label = { Text("أدخل لون أو كود Shade مخصص (مثال: 2M2, 3R1.5, Gingival Pink)") },
          leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_shade_input"),
          singleLine = true
        )
      }

      // Shade Details & Notes
      OutlinedTextField(
        value = shadeNotes,
        onValueChange = onShadeNotesChanged,
        label = { Text("ملاحظات تفصيلية للون والشفافية (اختياري)") },
        placeholder = { Text("مثال: شفافية في الحافة القاطعة، عنق السن أغمق بنصف درجة A3.5...") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("shade_notes_input"),
        maxLines = 2
      )
    }
  }
}
