package com.aqlanlab.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.data.models.ShipmentStatus
import com.aqlanlab.app.network.SmsGatewayConfig
import com.aqlanlab.app.network.SmsProvider
import com.aqlanlab.app.network.WhatsAppGatewayConfig
import com.aqlanlab.app.network.WhatsAppGatewayMode
import com.aqlanlab.app.ui.components.ClinicInfo
import com.aqlanlab.app.ui.components.WhatsAppNotificationDialog
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import com.aqlanlab.app.util.WhatsAppMessagingManager
import com.aqlanlab.app.util.WhatsAppTemplateType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppNotificationsScreen(
  viewModel: DentalLabViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToShipmentDetail: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val allShipments by viewModel.allShipments.collectAsState()
  val allLabs by viewModel.allLabs.collectAsState()
  val activeUser = viewModel.activeUser.collectAsState().value ?: viewModel.getActiveUserSafe()

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Ready Cases, 1: Send Invoice/Receipt, 2: Templates, 3: Care Guides, 4: Gateways & SMS Settings

  // WhatsApp Dialog State
  var showWhatsAppDialog by remember { mutableStateOf(false) }
  var dialogShipment by remember { mutableStateOf<Shipment?>(null) }
  var dialogTemplateType by remember { mutableStateOf(WhatsAppTemplateType.CASE_READY_ALERT) }
  var dialogPhoneNumber by remember { mutableStateOf("") }
  var dialogPatientName by remember { mutableStateOf("") }

  val readyShipments = remember(allShipments) {
    allShipments.filter { it.status == ShipmentStatus.READY }
  }

  val inProgressShipments = remember(allShipments) {
    allShipments.filter { it.status == ShipmentStatus.IN_PROGRESS }
  }

  val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }

  if (showWhatsAppDialog) {
    WhatsAppNotificationDialog(
      shipment = dialogShipment,
      initialTemplateType = dialogTemplateType,
      initialPhoneNumber = dialogPhoneNumber,
      initialPatientName = dialogPatientName,
      onDismiss = { showWhatsAppDialog = false }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "مركز إرسال الرسائل وبوابات SMS والواتساب",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "يمن موبايل • Sender ID باسم المركز • واتساب سحابي",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("whatsapp_screen_back_btn")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
          }
        },
        actions = {
          IconButton(
            onClick = {
              dialogShipment = null
              dialogTemplateType = WhatsAppTemplateType.CUSTOM
              dialogPhoneNumber = ""
              dialogPatientName = ""
              showWhatsAppDialog = true
            },
            modifier = Modifier.testTag("whatsapp_new_custom_msg_btn")
          ) {
            Icon(
              imageVector = Icons.Default.AddComment,
              contentDescription = "رسالة جديدة",
              tint = Color(0xFF15803D)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    modifier = modifier.testTag("whatsapp_notifications_screen")
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // 1. WhatsApp Hero Banner
      Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  Color(0xFF064E3B),
                  Color(0xFF065F46),
                  Color(0xFF047857)
                )
              )
            )
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Surface(
                  shape = CircleShape,
                  color = Color(0xFF25D366),
                  modifier = Modifier.size(38.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Send,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }

                Column {
                  Text(
                    text = ClinicInfo.CLINIC_SHORT_NAME,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                  Text(
                    text = "منظومة التواصل والتنبيه المباشر مع المرضى والمعامل",
                    color = Color(0xFFA7F3D0),
                    fontSize = 11.sp
                  )
                }
              }

              // Ready Badge
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.2f)
              ) {
                Text(
                  text = "${readyShipments.size} حالات جاهزة",
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }

            // Quick Stats Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.2f),
                modifier = Modifier.weight(1f)
              ) {
                Row(
                  modifier = Modifier.padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("📞", fontSize = 14.sp)
                  Text(ClinicInfo.PHONE_PRIMARY, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.2f),
                modifier = Modifier.weight(1f)
              ) {
                Row(
                  modifier = Modifier.padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("📍", fontSize = 14.sp)
                  Text("شارع التحرير الأعلى", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                }
              }
            }
          }
        }
      }

      // 2. Navigation Tabs (Scrollable for all 5 tabs)
      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = Color(0xFF15803D),
        edgePadding = 8.dp
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = {
            BadgeBox(
              badgeCount = readyShipments.size,
              title = "حالات جاهزة"
            )
          }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("إرسال فاتورة", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("قوالب الرسائل", fontSize = 13.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
        )
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("إرشادات العناية", fontSize = 13.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
        )
        Tab(
          selected = selectedTab == 4,
          onClick = { selectedTab = 4 },
          text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Default.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selectedTab == 4) Color(0xFF0284C7) else Color.Gray)
              Text("⚙️ بوابات SMS والواتساب", fontSize = 13.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal)
            }
          }
        )
      }

      // 3. Tab Content
      Box(modifier = Modifier.weight(1f)) {
        when (selectedTab) {
          0 -> ReadyShipmentsTab(
            readyShipments = readyShipments,
            dateFormat = dateFormat,
            onSendWhatsApp = { shipment ->
              dialogShipment = shipment
              dialogTemplateType = WhatsAppTemplateType.CASE_READY_ALERT
              dialogPhoneNumber = shipment.patientPhone
              dialogPatientName = shipment.patientName
              showWhatsAppDialog = true
            },
            onNavigateToDetail = onNavigateToShipmentDetail
          )

          1 -> SendInvoiceTab(
            allShipments = allShipments,
            dateFormat = dateFormat,
            onSelectShipmentForInvoice = { shipment, isInvoice ->
              dialogShipment = shipment
              dialogTemplateType = if (isInvoice) WhatsAppTemplateType.PATIENT_INVOICE else WhatsAppTemplateType.PAYMENT_RECEIPT
              dialogPhoneNumber = shipment.patientPhone
              dialogPatientName = shipment.patientName
              showWhatsAppDialog = true
            }
          )

          2 -> MessageTemplatesTab(
            onOpenTemplate = { template ->
              dialogShipment = allShipments.firstOrNull()
              dialogTemplateType = template
              dialogPhoneNumber = ""
              dialogPatientName = ""
              showWhatsAppDialog = true
            }
          )

          3 -> PostOpCareGuidesTab(
            onSendCareGuide = { guideNotes, title ->
              dialogShipment = null
              dialogTemplateType = WhatsAppTemplateType.POST_FITTING_CARE
              dialogPhoneNumber = ""
              dialogPatientName = ""
              showWhatsAppDialog = true
            }
          )

          4 -> MessagingGatewaysSettingsTab(
            viewModel = viewModel
          )
        }
      }
    }
  }
}

@Composable
private fun BadgeBox(
  badgeCount: Int,
  title: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    if (badgeCount > 0) {
      Surface(
        shape = CircleShape,
        color = Color(0xFFEF4444),
        modifier = Modifier.size(18.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "$badgeCount",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun ReadyShipmentsTab(
  readyShipments: List<Shipment>,
  dateFormat: SimpleDateFormat,
  onSendWhatsApp: (Shipment) -> Unit,
  onNavigateToDetail: (Long) -> Unit
) {
  if (readyShipments.isEmpty()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xFFDCFCE7),
        modifier = Modifier.size(72.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF15803D),
            modifier = Modifier.size(36.dp)
          )
        }
      }
      Spacer(Modifier.height(16.dp))
      Text(
        text = "لا توجد إرساليات جاهزة تنتظر الإشعار حالياً",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = "عندما تصبح أي إرسالية بحالة (جاهزة)، ستظهر هنا فوراً لإشعار المريض بضغطة زر.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
    }
  } else {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFF0FDF4),
          border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Campaign,
              contentDescription = null,
              tint = Color(0xFF15803D),
              modifier = Modifier.size(24.dp)
            )
            Column {
              Text(
                text = "يوجد ${readyShipments.size} إرساليات وصلت من المعمل وجاهزة للتركيب",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF14532D)
              )
              Text(
                text = "اضغط على زر (إشعار واتساب) لإرسال رسالة جاهزية وتحديد موعد للمريض فوراً.",
                fontSize = 11.sp,
                color = Color(0xFF166534)
              )
            }
          }
        }
      }

      items(readyShipments, key = { it.id }) { shipment ->
        ReadyShipmentCard(
          shipment = shipment,
          dateFormat = dateFormat,
          onSendWhatsApp = { onSendWhatsApp(shipment) },
          onClick = { onNavigateToDetail(shipment.id) }
        )
      }
    }
  }
}

@Composable
private fun ReadyShipmentCard(
  shipment: Shipment,
  dateFormat: SimpleDateFormat,
  onSendWhatsApp: () -> Unit,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("ready_shipment_card_${shipment.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
            color = Color(0xFFDCFCE7)
          ) {
            Text(
              text = "جاهزة للاستلام",
              color = Color(0xFF15803D),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }

          Text(
            text = shipment.shipmentNumber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Text(
          text = dateFormat.format(Date(shipment.orderDate)),
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Patient Name & Work Type
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = shipment.patientName.ifBlank { "مريض بدون اسم" },
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "${shipment.workTypeName} (${shipment.pieceCount} قطع) • ${shipment.labName}",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (shipment.toothNumbers.isNotBlank()) {
          Text(
            text = "🦷 الأسنان: ${shipment.toothNumbers} • اللون: ${shipment.shade}",
            fontSize = 11.sp,
            color = Color(0xFF0369A1),
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Bottom Action Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (shipment.patientPhone.isNotBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
            Text(shipment.patientPhone, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
          }
        } else {
          Text("لم يسجل رقم هاتف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
          onClick = onSendWhatsApp,
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF25D366),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.testTag("notify_patient_whatsapp_btn_${shipment.id}")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text("إشعار واتساب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun SendInvoiceTab(
  allShipments: List<Shipment>,
  dateFormat: SimpleDateFormat,
  onSelectShipmentForInvoice: (Shipment, Boolean) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }

  val filteredShipments = remember(allShipments, searchQuery) {
    if (searchQuery.isBlank()) allShipments
    else {
      allShipments.filter {
        it.patientName.contains(searchQuery, ignoreCase = true) ||
        it.shipmentNumber.contains(searchQuery, ignoreCase = true) ||
        it.workTypeName.contains(searchQuery, ignoreCase = true)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      label = { Text("بحث عن مريض أو رقم الإرسالية لإصدار فاتورة") },
      placeholder = { Text("اكتب اسم المريض أو رقم الحالة...") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotBlank()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Clear, contentDescription = "مسح")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(filteredShipments, key = { it.id }) { shipment ->
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = shipment.patientName.ifBlank { "مريض بدون اسم" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Text(
                text = "${shipment.shipmentNumber} • ${shipment.workTypeName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "💰 التكلفة: ${shipment.totalPrice} ${shipment.currency} • ${dateFormat.format(Date(shipment.orderDate))}",
                fontSize = 11.sp,
                color = Color(0xFF0369A1)
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(
                onClick = { onSelectShipmentForInvoice(shipment, true) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text("فاتورة 🧾", fontSize = 11.sp)
              }

              Button(
                onClick = { onSelectShipmentForInvoice(shipment, false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text("سند قبض 💳", fontSize = 11.sp, color = Color.White)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MessageTemplatesTab(
  onOpenTemplate: (WhatsAppTemplateType) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    items(WhatsAppTemplateType.values()) { template ->
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpenTemplate(template) }
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Surface(
              shape = CircleShape,
              color = Color(0xFFDCFCE7),
              modifier = Modifier.size(44.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(template.iconEmoji, fontSize = 20.sp)
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = template.titleAr,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Text(
                text = template.descriptionAr,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color(0xFF15803D),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun PostOpCareGuidesTab(
  onSendCareGuide: (String, String) -> Unit
) {
  val guides = listOf(
    Pair("تعليمات تيجان وجسور الزركونيا والـ E-max", "العناية بنظافة التيجان والجسور واستخدام خيط الأسنان المخصص وتجنب قضم الأشياء الصلبة."),
    Pair("إرشادات الفينير والابتسامة التجميلية", "تجنب المشروبات الصبغية الشديدة أول 48 ساعة واستخدام فرشاة أسنان ناعمة للحفاظ على لمعان الفينير."),
    Pair("تعليمات ما بعد زراعة الأسنان والتركيبات", "الالتزام بالمضمضة الطبية الموصوفة وتجنب الضغط المباشر على الغرسة في مراحل الالتئام الأولى."),
    Pair("إرشادات العناية بأجهزة التقويم الشفاف والثابت", "تنظيف الحاصرات والأجهزة وتجنب المأكولات الصلبة واللزجة لحماية الأسلاك والتقويم.")
  )

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    items(guides) { guide ->
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text("✨", fontSize = 18.sp)
            Text(
              text = guide.first,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = Color(0xFF0369A1)
            )
          }

          Text(
            text = guide.second,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = { onSendCareGuide(guide.second, guide.first) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.align(Alignment.End)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("إرسال الإرشادات للمريض", fontSize = 11.sp, color = Color.White)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagingGatewaysSettingsTab(
  viewModel: DentalLabViewModel
) {
  val context = LocalContext.current
  val currentSmsConfig by viewModel.smsConfig.collectAsState()
  val currentWaConfig by viewModel.whatsAppConfig.collectAsState()

  // Local form state for SMS
  var smsEnabled by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.isEnabled) }
  var smsProvider by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.provider) }
  var smsSenderId by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.senderId) }
  var smsApiUrl by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.apiUrl) }
  var smsUsername by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.apiUsername) }
  var smsApiKey by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.apiKeyOrPassword) }
  var smsTestPhone by remember(currentSmsConfig) { mutableStateOf(currentSmsConfig.defaultAdminPhone) }
  var isSmsKeyVisible by remember { mutableStateOf(false) }
  var isTestingSms by remember { mutableStateOf(false) }
  var smsTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

  // Local form state for WhatsApp
  var waMode by remember(currentWaConfig) { mutableStateOf(currentWaConfig.mode) }
  var waInstanceId by remember(currentWaConfig) { mutableStateOf(currentWaConfig.instanceId) }
  var waApiToken by remember(currentWaConfig) { mutableStateOf(currentWaConfig.apiToken) }
  var waPhoneId by remember(currentWaConfig) { mutableStateOf(currentWaConfig.phoneNumberId) }
  var waAutoReady by remember(currentWaConfig) { mutableStateOf(currentWaConfig.autoNotifyReadyCase) }
  var isWaTokenVisible by remember { mutableStateOf(false) }
  var isTestingWa by remember { mutableStateOf(false) }
  var waTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Overview Banner
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              shape = CircleShape,
              color = Color(0xFF0284C7),
              modifier = Modifier.size(36.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CellTower, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
              }
            }
            Column {
              Text("بوابات الرسائل القصيرة والواتساب السحابي", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
              Text("ربط الـ API وإظهار اسم المركز (Sender ID) للمستلم", fontSize = 12.sp, color = Color(0xFFBAE6FD))
            }
          }

          Text(
            text = "يتيح هذا القسم ربط التطبيق ببوابة يمن موبايل للرسائل القصيرة (Bulk SMS Gateway) لتصل الرسائل للمرضى باسم المركز (مثال: AqlanDental) بدلاً من ظهور رقم هاتف عادي، بالإضافة للربط السحابي للواتساب.",
            fontSize = 11.sp,
            color = Color(0xFFE2E8F0),
            lineHeight = 16.sp
          )
        }
      }
    }

    // 2. Sender ID Live Preview
    item {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("📱 معاينة شكل الرسالة في جوال المريض:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155))
          
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                  Text(
                    text = smsSenderId.ifBlank { "AqlanDental" },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                  )
                }
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color(0xFFDCFCE7)
                ) {
                  Text("اسم المرسل المعتمد", fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
              }

              HorizontalDivider(color = Color(0xFFF1F5F9))

              Text(
                text = "عزيزنا المريض، تركيبتك السنية أصبحت جاهزة للتسليم في مركز د. عقلان لطب وتجميل الأسنان. يرجى مراجعتنا لتحديد موعد التركيب 🦷✨",
                fontSize = 11.sp,
                color = Color(0xFF475569),
                lineHeight = 16.sp
              )
            }
          }
        }
      }
    }

    // 3. SMS Gateway Configuration Form (يمن موبايل / المزودين)
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Sms, contentDescription = null, tint = Color(0xFF0284C7))
              Text("إعدادات بوابة رسائل SMS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(if (smsEnabled) "مفعلة" else "معطلة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (smsEnabled) Color(0xFF15803D) else Color.Gray)
              Switch(
                checked = smsEnabled,
                onCheckedChange = { smsEnabled = it },
                modifier = Modifier.testTag("sms_gateway_switch")
              )
            }
          }

          // Provider Selection
          Text("مزود خدمة الرسائل (SMS Provider):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = smsProvider == SmsProvider.YEMEN_MOBILE,
              onClick = {
                smsProvider = SmsProvider.YEMEN_MOBILE
                smsApiUrl = SmsProvider.YEMEN_MOBILE.defaultEndpoint
              },
              label = { Text("يمن موبايل", fontSize = 11.sp) },
              modifier = Modifier.weight(1f)
            )
            FilterChip(
              selected = smsProvider == SmsProvider.UNIFONIC,
              onClick = {
                smsProvider = SmsProvider.UNIFONIC
                smsApiUrl = SmsProvider.UNIFONIC.defaultEndpoint
              },
              label = { Text("Unifonic", fontSize = 11.sp) },
              modifier = Modifier.weight(1f)
            )
            FilterChip(
              selected = smsProvider == SmsProvider.CUSTOM_HTTP,
              onClick = { smsProvider = SmsProvider.CUSTOM_HTTP },
              label = { Text("بوابة مخصصة", fontSize = 11.sp) },
              modifier = Modifier.weight(1f)
            )
          }

          // Sender ID field
          OutlinedTextField(
            value = smsSenderId,
            onValueChange = { smsSenderId = it },
            label = { Text("اسم المرسل (Sender ID)") },
            placeholder = { Text("مثال: AqlanDental أو د. عقلان") },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF0284C7)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("sms_sender_id_input")
          )
          Text("💡 هذا الاسم المسجل رسمياً لدى شركة الاتصالات ليظهر للمستلم في خانة المرسل.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

          // API Endpoint URL
          OutlinedTextField(
            value = smsApiUrl,
            onValueChange = { smsApiUrl = it },
            label = { Text("رابط خادم البوابة (API Endpoint URL)") },
            placeholder = { Text("https://api.yemenmobile.com.ye/...") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingIcon = {
              IconButton(onClick = { smsApiUrl = smsProvider.defaultEndpoint }) {
                Icon(Icons.Default.Restore, contentDescription = "استعادة الرابط الافتراضي", modifier = Modifier.size(18.dp))
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("sms_api_url_input")
          )

          // Username & Password / Key
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = smsUsername,
              onValueChange = { smsUsername = it },
              label = { Text("اسم المستخدم (Username)") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("sms_username_input")
            )

            OutlinedTextField(
              value = smsApiKey,
              onValueChange = { smsApiKey = it },
              label = { Text("كلمة المرور / API Key") },
              singleLine = true,
              visualTransformation = if (isSmsKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { isSmsKeyVisible = !isSmsKeyVisible }) {
                  Icon(if (isSmsKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                }
              },
              modifier = Modifier.weight(1f).testTag("sms_api_key_input")
            )
          }

          // Test SMS Section
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF0F9FF),
            border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("🧪 تجربة إرسال رسالة SMS عبر البوابة الآن:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0369A1))
              
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = smsTestPhone,
                  onValueChange = { smsTestPhone = it },
                  label = { Text("رقم هاتف التجربة") },
                  placeholder = { Text("+967770245745") },
                  singleLine = true,
                  modifier = Modifier.weight(1.3f).testTag("sms_test_phone_input")
                )

                Button(
                  onClick = {
                    isTestingSms = true
                    smsTestResult = null
                    // Save first then test
                    viewModel.updateSmsConfig(
                      SmsGatewayConfig(
                        isEnabled = smsEnabled,
                        provider = smsProvider,
                        senderId = smsSenderId,
                        apiUrl = smsApiUrl,
                        apiUsername = smsUsername,
                        apiKeyOrPassword = smsApiKey,
                        defaultAdminPhone = smsTestPhone
                      )
                    )
                    viewModel.testSmsGateway(smsTestPhone) { success, msg ->
                      isTestingSms = false
                      smsTestResult = Pair(success, msg)
                      android.widget.Toast.makeText(context, msg, if (success) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
                    }
                  },
                  enabled = !isTestingSms,
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.weight(1f).testTag("test_sms_btn")
                ) {
                  if (isTestingSms) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                  } else {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إرسال تجربة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }

              smsTestResult?.let { result ->
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (result.first) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (result.first) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (result.first) Color(0xFF166534) else Color(0xFF991B1B), modifier = Modifier.size(16.dp))
                    Text(result.second, fontSize = 11.sp, color = if (result.first) Color(0xFF166534) else Color(0xFF991B1B))
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. WhatsApp Cloud API / UltraMsg Gateway Section
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF15803D))
            Text("إعدادات بوابة الواتساب السحابية والتلقائية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          }

          Text("نمط إرسال الواتساب:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = waMode == WhatsAppGatewayMode.DIRECT_APP,
              onClick = { waMode = WhatsAppGatewayMode.DIRECT_APP },
              label = { Text("واتساب مباشر عبر تطبيق الهاتف (مجاني وبدون اشتراك)", fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth()
            )
            FilterChip(
              selected = waMode == WhatsAppGatewayMode.ULTRAMSG,
              onClick = { waMode = WhatsAppGatewayMode.ULTRAMSG },
              label = { Text("بوابة ألترا مسج السحابية (UltraMsg API)", fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth()
            )
            FilterChip(
              selected = waMode == WhatsAppGatewayMode.META_CLOUD_API,
              onClick = { waMode = WhatsAppGatewayMode.META_CLOUD_API },
              label = { Text("بوابة ميتا الرسمية (Meta WhatsApp Cloud API)", fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth()
            )
          }

          if (waMode != WhatsAppGatewayMode.DIRECT_APP) {
            OutlinedTextField(
              value = waInstanceId,
              onValueChange = { waInstanceId = it },
              label = { Text(if (waMode == WhatsAppGatewayMode.ULTRAMSG) "رقم الحساب (Instance ID)" else "معرف رقم الهاتف (Phone Number ID)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = waApiToken,
              onValueChange = { waApiToken = it },
              label = { Text("رمز الوصول السحابي (API Token)") },
              singleLine = true,
              visualTransformation = if (isWaTokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { isWaTokenVisible = !isWaTokenVisible }) {
                  Icon(if (isWaTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                }
              },
              modifier = Modifier.fillMaxWidth()
            )

            // Test WhatsApp Button
            Button(
              onClick = {
                isTestingWa = true
                waTestResult = null
                viewModel.updateWhatsAppConfig(
                  WhatsAppGatewayConfig(
                    mode = waMode,
                    instanceId = waInstanceId,
                    apiToken = waApiToken,
                    phoneNumberId = waPhoneId,
                    autoNotifyReadyCase = waAutoReady
                  )
                )
                viewModel.testWhatsAppGateway(smsTestPhone) { success, msg ->
                  isTestingWa = false
                  waTestResult = Pair(success, msg)
                  android.widget.Toast.makeText(context, msg, if (success) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
                }
              },
              enabled = !isTestingWa,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().testTag("test_whatsapp_gateway_btn")
            ) {
              if (isTestingWa) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("تجربة إرسال واتساب سحابي للمشرف", fontWeight = FontWeight.Bold)
              }
            }

            waTestResult?.let { result ->
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (result.first) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(if (result.first) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (result.first) Color(0xFF166534) else Color(0xFF991B1B), modifier = Modifier.size(16.dp))
                  Text(result.second, fontSize = 11.sp, color = if (result.first) Color(0xFF166534) else Color(0xFF991B1B))
                }
              }
            }
          }
        }
      }
    }

    // 5. Save All Gateway Settings Button
    item {
      Button(
        onClick = {
          viewModel.updateSmsConfig(
            SmsGatewayConfig(
              isEnabled = smsEnabled,
              provider = smsProvider,
              senderId = smsSenderId,
              apiUrl = smsApiUrl,
              apiUsername = smsUsername,
              apiKeyOrPassword = smsApiKey,
              defaultAdminPhone = smsTestPhone
            )
          )
          viewModel.updateWhatsAppConfig(
            WhatsAppGatewayConfig(
              mode = waMode,
              instanceId = waInstanceId,
              apiToken = waApiToken,
              phoneNumberId = waPhoneId,
              autoNotifyReadyCase = waAutoReady
            )
          )
          android.widget.Toast.makeText(context, "تم حفظ كافة إعدادات بوابات SMS والواتساب بنجاح 💾✨", android.widget.Toast.LENGTH_LONG).show()
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_gateway_settings_btn")
      ) {
        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("حفظ كافة إعدادات البوابات (Save Gateways)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
      }
    }

    // 6. Informational Steps for Yemen Mobile Sender ID
    item {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text("ℹ️ خطوات تفعيل اسم المرسل (Sender ID) لدى يمن موبايل:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
          Text("1. التواصل مع قسم خدمات الشركات والرسائل الجماعية في شركة يمن موبايل.", fontSize = 11.sp, color = Color(0xFF78350F))
          Text("2. طلب حجز اسم مرسل رسمي للمركز (مثال: AqlanDental أو د. عقلان).", fontSize = 11.sp, color = Color(0xFF78350F))
          Text("3. استلام بيانات الربط البرمجي (API Username & Password) وإدخالها في الحقول أعلاه.", fontSize = 11.sp, color = Color(0xFF78350F))
          Text("4. سيعمل الإرسال التلقائي في الخلفية فوراً وتصل الرسائل باسم مركزك للمرضى والمعامل.", fontSize = 11.sp, color = Color(0xFF78350F))
        }
      }
      Spacer(Modifier.height(20.dp))
    }
  }
}
