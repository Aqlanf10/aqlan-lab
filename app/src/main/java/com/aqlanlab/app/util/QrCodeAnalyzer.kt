package com.aqlanlab.app.util

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrCodeAnalyzer(
  private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

  private val options = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(
      Barcode.FORMAT_QR_CODE,
      Barcode.FORMAT_CODE_128,
      Barcode.FORMAT_CODE_39,
      Barcode.FORMAT_EAN_13,
      Barcode.FORMAT_EAN_8,
      Barcode.FORMAT_DATA_MATRIX,
      Barcode.FORMAT_PDF417
    )
    .build()

  private val scanner = BarcodeScanning.getClient(options)
  private var lastScannedTimestamp = 0L

  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
      val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
      scanner.process(image)
        .addOnSuccessListener { barcodes ->
          for (barcode in barcodes) {
            val rawValue = barcode.rawValue
            if (!rawValue.isNullOrBlank()) {
              val currentTimestamp = System.currentTimeMillis()
              // Throttle to prevent spamming multiple identical triggers
              if (currentTimestamp - lastScannedTimestamp > 1200L) {
                lastScannedTimestamp = currentTimestamp
                onQrCodeScanned(rawValue.trim())
              }
              break
            }
          }
        }
        .addOnFailureListener {
          // Ignored for next frame
        }
        .addOnCompleteListener {
          imageProxy.close()
        }
    } else {
      imageProxy.close()
    }
  }
}
