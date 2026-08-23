package com.aqlanlab.app.util

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

object QrCodeGenerator {

  /**
   * Generates a QR Code Bitmap from the given string content
   */
  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return try {
      val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
        put(EncodeHintType.MARGIN, 1)
      }
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)

      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
        }
      }

      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }
}

@Composable
fun QrCodeView(
  content: String,
  modifier: Modifier = Modifier,
  size: Dp = 180.dp,
  backgroundColor: Color = Color.White
) {
  // FIX: QR generation is a 160,000-iteration pixel loop + bitmap allocation and
  // previously ran synchronously inside `remember` during composition on the main
  // thread. It now runs on Dispatchers.Default via produceState.
  val qrBitmap by produceState<Bitmap?>(initialValue = null, content) {
    value = withContext(Dispatchers.Default) {
      QrCodeGenerator.generateQrBitmap(content, sizePx = 400)
    }
  }

  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(12.dp))
      .background(backgroundColor)
      .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp)),
    contentAlignment = Alignment.Center
  ) {
    val bitmap = qrBitmap
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code لملصق الإرسالية: $content",
        modifier = Modifier.size(size - 16.dp)
      )
    }
  }
}
