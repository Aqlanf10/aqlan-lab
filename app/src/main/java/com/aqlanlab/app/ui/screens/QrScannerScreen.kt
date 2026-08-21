package com.aqlanlab.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aqlanlab.app.data.models.Shipment
import com.aqlanlab.app.ui.components.DateUtils
import com.aqlanlab.app.ui.components.StatusBadge
import com.aqlanlab.app.ui.viewmodel.DentalLabViewModel
import com.aqlanlab.app.util.QrCodeAnalyzer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
  viewModel: DentalLabViewModel,
  onNavigateToShipment: (Long) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val focusManager = LocalFocusManager.current
  val allShipments by viewModel.allShipments.collectAsState()

  // Permission State
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
    if (!isGranted) {
      Toast.makeText(context, "إذن الكاميرا مطلوب لمسح ملصقات الباركود وQR", Toast.LENGTH_SHORT).show()
    }
  }

  // Camera & Torch Controls
  var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
  var isTorchOn by remember { mutableStateOf(false) }
  var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

  // Scanning Result State
  var lastScannedCode by remember { mutableStateOf<String?>(null) }
  var matchedShipment by remember { mutableStateOf<Shipment?>(null) }
  var isManualSearchOpen by remember { mutableStateOf(false) }
  var manualCodeInput by remember { mutableStateOf("") }

  // Trigger vibration helper
  fun triggerScanVibration() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(100)
      }
    } catch (e: Exception) {
      Log.w("QrScanner", "Vibration failed: ${e.message}")
    }
  }

  // Resolver function to find shipment by scanned string
  fun resolveShipmentFromCode(code: String) {
    val clean = code.trim().removePrefix("shipment_").removePrefix("AQL-").removePrefix("#")
    val found = allShipments.find { shipment ->
      shipment.shipmentNumber.equals(code, ignoreCase = true) ||
      shipment.shipmentNumber.removePrefix("#").equals(clean, ignoreCase = true) ||
      shipment.id.toString() == clean ||
      shipment.shipmentNumber.contains(clean, ignoreCase = true) ||
      (code.length > 2 && shipment.patientName.contains(code, ignoreCase = true))
    }

    lastScannedCode = code
    matchedShipment = found
    triggerScanVibration()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "قارئ الباركود والـ QR",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "مسح ملصقات طرود وإرساليات العيادة",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = Color(0xFF94A3B8)
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
          }
        },
        actions = {
          // Torch Button
          IconButton(
            onClick = {
              val newTorchState = !isTorchOn
              cameraControl?.enableTorch(newTorchState)
              isTorchOn = newTorchState
            }
          ) {
            Icon(
              imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
              contentDescription = "كشاف الإضاءة",
              tint = if (isTorchOn) Color(0xFFFBBF24) else Color.White
            )
          }

          // Lens Switcher
          IconButton(
            onClick = {
              lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
              } else {
                CameraSelector.LENS_FACING_BACK
              }
            }
          ) {
            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "تبديل الكاميرا", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF0F172A)
        )
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(Color.Black)
    ) {
      if (!hasCameraPermission) {
        // Permission Request View
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
            modifier = Modifier.size(80.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(42.dp)
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          Text(
            text = "إذن الكاميرا مطلوب",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Spacer(Modifier.height(8.dp))

          Text(
            text = "يتطلب نظام إدارة المعامل لمركز د. عقلان الكامل الوصول إلى الكاميرا لمسح ملصقات الباركود وQR الملصقة على طرود الأسنان والتركيبات لفتح تفاصيل الإرسالية فوراً.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
          )

          Spacer(Modifier.height(24.dp))

          Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(48.dp)
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("منح الإذن وتشغيل الماسح", fontWeight = FontWeight.Bold)
          }
        }
      } else {
        // CameraX Live Viewfinder
        AndroidView(
          factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              val cameraProvider = cameraProviderFuture.get()

              val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
              }

              val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                  it.setAnalyzer(
                    Executors.newSingleThreadExecutor(),
                    QrCodeAnalyzer { rawCode ->
                      resolveShipmentFromCode(rawCode)
                    }
                  )
                }

              val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

              try {
                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  cameraSelector,
                  preview,
                  imageAnalysis
                )
                cameraControl = cam.cameraControl
              } catch (exc: Exception) {
                Log.e("QrScanner", "Use case binding failed", exc)
              }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
          },
          modifier = Modifier.fillMaxSize()
        )

        // Viewfinder Scanner Reticle & Laser Overlay
        ScannerReticleOverlay(
          isMatched = matchedShipment != null,
          modifier = Modifier.fillMaxSize()
        )

        // Instructions Hint Banner (Top)
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF0F172A).copy(alpha = 0.8f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 20.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CenterFocusStrong,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "وجّه الكاميرا نحو كود QR أو باركود ملصق الإرسالية",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFFE2E8F0)
            )
          }
        }

        // Bottom Controls: Manual Search & Scanned Result Card
        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Scanned Result Card
          AnimatedVisibility(
            visible = lastScannedCode != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
          ) {
            if (matchedShipment != null) {
              val shipment = matchedShipment!!
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
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
                        shape = CircleShape,
                        color = Color(0xFF059669),
                        modifier = Modifier.size(28.dp)
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                      }
                      Column {
                        Text(
                          text = shipment.patientName,
                          fontWeight = FontWeight.Bold,
                          fontSize = 15.sp,
                          color = Color.White,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Text(
                          text = "${shipment.shipmentNumber} • ${shipment.labName}",
                          fontSize = 12.sp,
                          color = Color(0xFF94A3B8)
                        )
                      }
                    }

                    StatusBadge(status = shipment.status)
                  }

                  HorizontalDivider(color = Color(0xFF334155))

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(
                        text = "نوع العمل: ${shipment.workTypeName} (${shipment.pieceCount} قطع)",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                      )
                      Text(
                        text = "التسليم: ${DateUtils.formatShortDate(shipment.expectedDeliveryDate)}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                      )
                    }

                    Button(
                      onClick = { onNavigateToShipment(shipment.id) },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                      Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("فتح الإرسالية", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                  }
                }
              }
            } else {
              // Not Found Feedback Card
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFFEF4444))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "لم يتم العثور على إرسالية مطابقة",
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      fontSize = 13.sp
                    )
                    Text(
                      text = "الكود المقروء: $lastScannedCode",
                      fontSize = 11.sp,
                      color = Color(0xFF94A3B8)
                    )
                  }
                  TextButton(
                    onClick = {
                      lastScannedCode = null
                      matchedShipment = null
                    }
                  ) {
                    Text("إعادة المحاولة", color = Color(0xFF38BDF8), fontSize = 11.sp)
                  }
                }
              }
            }
          }

          // Manual Entry Toggle / Search Bar
          if (isManualSearchOpen) {
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = manualCodeInput,
                  onValueChange = { manualCodeInput = it },
                  placeholder = { Text("أدخل رقم الإرسالية أو اسم المريض...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                  keyboardActions = KeyboardActions(
                    onSearch = {
                      focusManager.clearFocus()
                      if (manualCodeInput.isNotBlank()) {
                        resolveShipmentFromCode(manualCodeInput)
                      }
                    }
                  ),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                  ),
                  modifier = Modifier.weight(1f).testTag("manual_qr_input")
                )

                IconButton(
                  onClick = {
                    focusManager.clearFocus()
                    if (manualCodeInput.isNotBlank()) {
                      resolveShipmentFromCode(manualCodeInput)
                    }
                  },
                  colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2563EB))
                ) {
                  Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color.White)
                }

                IconButton(onClick = { isManualSearchOpen = false }) {
                  Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                }
              }
            }
          } else {
            // Manual code button
            OutlinedButton(
              onClick = { isManualSearchOpen = true },
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF0F172A).copy(alpha = 0.8f),
                contentColor = Color(0xFFE2E8F0)
              ),
              modifier = Modifier.fillMaxWidth().height(44.dp).testTag("manual_qr_entry_btn")
            ) {
              Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF38BDF8))
              Spacer(Modifier.width(8.dp))
              Text("إدخال رقم الإرسالية يدوياً", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

/**
 * Animated Viewfinder with corner brackets and scanning laser line
 */
@Composable
private fun ScannerReticleOverlay(
  isMatched: Boolean,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
  val laserProgress by infiniteTransition.animateFloat(
    initialValue = 0.05f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "laser_progress"
  )

  val bracketColor = if (isMatched) Color(0xFF10B981) else Color(0xFF38BDF8)
  val laserColor = if (isMatched) Color(0xFF10B981) else Color(0xFF60A5FA)

  Canvas(modifier = modifier) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    val boxSize = (canvasWidth * 0.72f).coerceAtMost(320.dp.toPx())
    val left = (canvasWidth - boxSize) / 2f
    val top = (canvasHeight - boxSize) / 2f - 40.dp.toPx()
    val right = left + boxSize
    val bottom = top + boxSize

    val cornerLength = 36.dp.toPx()
    val strokeWidth = 4.dp.toPx()
    val cornerRadius = 16.dp.toPx()

    // 1. Draw darkened transparent scrim outside scan frame
    val scrimPath = Path().apply {
      addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
      addRoundRect(
        RoundRect(
          left = left,
          top = top,
          right = right,
          bottom = bottom,
          cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
      )
      fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(scrimPath, color = Color.Black.copy(alpha = 0.65f))

    // 2. Draw 4 Corner Brackets
    // Top-Left
    drawLine(bracketColor, Offset(left, top + cornerLength), Offset(left, top + cornerRadius), strokeWidth)
    drawLine(bracketColor, Offset(left + cornerRadius, top), Offset(left + cornerLength, top), strokeWidth)
    drawArc(
      color = bracketColor,
      startAngle = 180f,
      sweepAngle = 90f,
      useCenter = false,
      topLeft = Offset(left, top),
      size = Size(cornerRadius * 2, cornerRadius * 2),
      style = Stroke(strokeWidth)
    )

    // Top-Right
    drawLine(bracketColor, Offset(right - cornerLength, top), Offset(right - cornerRadius, top), strokeWidth)
    drawLine(bracketColor, Offset(right, top + cornerRadius), Offset(right, top + cornerLength), strokeWidth)
    drawArc(
      color = bracketColor,
      startAngle = 270f,
      sweepAngle = 90f,
      useCenter = false,
      topLeft = Offset(right - cornerRadius * 2, top),
      size = Size(cornerRadius * 2, cornerRadius * 2),
      style = Stroke(strokeWidth)
    )

    // Bottom-Left
    drawLine(bracketColor, Offset(left, bottom - cornerLength), Offset(left, bottom - cornerRadius), strokeWidth)
    drawLine(bracketColor, Offset(left + cornerRadius, bottom), Offset(left + cornerLength, bottom), strokeWidth)
    drawArc(
      color = bracketColor,
      startAngle = 90f,
      sweepAngle = 90f,
      useCenter = false,
      topLeft = Offset(left, bottom - cornerRadius * 2),
      size = Size(cornerRadius * 2, cornerRadius * 2),
      style = Stroke(strokeWidth)
    )

    // Bottom-Right
    drawLine(bracketColor, Offset(right - cornerLength, bottom), Offset(right - cornerRadius, bottom), strokeWidth)
    drawLine(bracketColor, Offset(right, bottom - cornerRadius), Offset(right, bottom - cornerLength), strokeWidth)
    drawArc(
      color = bracketColor,
      startAngle = 0f,
      sweepAngle = 90f,
      useCenter = false,
      topLeft = Offset(right - cornerRadius * 2, bottom - cornerRadius * 2),
      size = Size(cornerRadius * 2, cornerRadius * 2),
      style = Stroke(strokeWidth)
    )

    // 3. Draw Laser Scan Line
    val laserY = top + (boxSize * laserProgress)
    drawLine(
      brush = Brush.horizontalGradient(
        colors = listOf(
          Color.Transparent,
          laserColor.copy(alpha = 0.8f),
          laserColor,
          laserColor.copy(alpha = 0.8f),
          Color.Transparent
        ),
        startX = left + 10.dp.toPx(),
        endX = right - 10.dp.toPx()
      ),
      start = Offset(left + 10.dp.toPx(), laserY),
      end = Offset(right - 10.dp.toPx(), laserY),
      strokeWidth = 3.dp.toPx()
    )
  }
}
