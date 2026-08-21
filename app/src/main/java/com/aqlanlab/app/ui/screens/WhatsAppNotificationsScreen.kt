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

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Ready Cases, 1: Send Invoice/Receipt, 2: Templates, 3: Care Guides

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
              text = "نظام إشعارات وفواتير الواتساب",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "إرسال فواتير • تنبيهات جاهزية • تذكير مواعيد",
              fontSize = 12.sp,
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

      // 2. Navigation Tabs
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = Color(0xFF15803D)
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
