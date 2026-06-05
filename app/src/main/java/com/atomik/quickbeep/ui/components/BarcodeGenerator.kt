package com.atomik.quickbeep.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.EnumMap
import com.google.mlkit.vision.barcode.common.Barcode

object BarcodeGenerator {
    
    fun mapStringToFormat(formatName: String): BarcodeFormat {
        return try {
            BarcodeFormat.valueOf(formatName)
        } catch (e: Exception) {
            // Check common formats manually if name doesn't match exactly
            when (formatName.uppercase()) {
                "QR_CODE", "QR" -> BarcodeFormat.QR_CODE
                "EAN_13", "EAN13" -> BarcodeFormat.EAN_13
                "EAN_8", "EAN8" -> BarcodeFormat.EAN_8
                "UPC_A", "UPCA" -> BarcodeFormat.UPC_A
                "UPC_E", "UPCE" -> BarcodeFormat.UPC_E
                "CODE_128", "CODE128" -> BarcodeFormat.CODE_128
                "CODE_39", "CODE39" -> BarcodeFormat.CODE_39
                "ITF" -> BarcodeFormat.ITF
                "PDF_417", "PDF417" -> BarcodeFormat.PDF_417
                "CODABAR" -> BarcodeFormat.CODABAR
                "AZTEC" -> BarcodeFormat.AZTEC
                "DATA_MATRIX" -> BarcodeFormat.DATA_MATRIX
                else -> BarcodeFormat.QR_CODE
            }
        }
    }

    fun mapMlKitIntToZxingFormat(formatInt: Int, formatName: String): BarcodeFormat {
        return when (formatInt) {
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
            Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
            Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_ITF -> BarcodeFormat.ITF
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
            Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
            else -> mapStringToFormat(formatName) // Fallback for legacy database records
        }
    }

    fun getMlKitFormatName(formatInt: Int, fallback: String): String {
        return when (formatInt) {
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_PDF417 -> "PDF_417"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            else -> fallback
        }
    }

    fun is2DFormat(format: BarcodeFormat): Boolean {
        return format == BarcodeFormat.QR_CODE ||
               format == BarcodeFormat.DATA_MATRIX ||
               format == BarcodeFormat.AZTEC ||
               format == BarcodeFormat.PDF_417
    }

    fun generateMatrix(value: String, format: BarcodeFormat, width: Int = 500, height: Int = 500): BitMatrix? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 0 // Minimal margin to let Composable control padding
            
            // Adjust QR error correction level if needed
            if (format == BarcodeFormat.QR_CODE) {
                hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            }

            MultiFormatWriter().encode(value, format, width, height, hints)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createBarcodeBitmap(bitMatrix: BitMatrix, quietZonePercentage: Float = 0.1f): android.graphics.Bitmap {
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        
        // Calculate quiet zone padding size
        val paddingX = (matrixWidth * quietZonePercentage).toInt().coerceAtLeast(8)
        val paddingY = (matrixHeight * quietZonePercentage).toInt().coerceAtLeast(8)
        
        val width = matrixWidth + 2 * paddingX
        val height = matrixHeight + 2 * paddingY
        
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        
        val blackPixel = 0xFF000000.toInt()
        val whitePixel = 0xFFFFFFFF.toInt()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val matrixX = x - paddingX
                val matrixY = y - paddingY
                
                if (matrixX in 0 until matrixWidth && matrixY in 0 until matrixHeight) {
                    val isBlack = bitMatrix.get(matrixX, matrixY)
                    bitmap.setPixel(x, y, if (isBlack) blackPixel else whitePixel)
                } else {
                    bitmap.setPixel(x, y, whitePixel)
                }
            }
        }
        return bitmap
    }
}

@Composable
fun BarcodeRenderer(
    rawValue: String,
    formatName: String,
    formatInt: Int = -1,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val format = remember(formatName, formatInt) {
        if (formatInt != -1) {
            BarcodeGenerator.mapMlKitIntToZxingFormat(formatInt, formatName)
        } else {
            BarcodeGenerator.mapStringToFormat(formatName)
        }
    }
    
    val bitMatrix = remember(rawValue, format) {
        val width = if (BarcodeGenerator.is2DFormat(format)) 300 else 400
        val height = if (BarcodeGenerator.is2DFormat(format)) 300 else 100
        BarcodeGenerator.generateMatrix(rawValue, format, width, height)
    }

    if (bitMatrix == null) {
        Box(
            modifier = modifier.background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Rendering Error", color = Color.Red)
        }
        return
    }

    val imageBitmap = remember(bitMatrix) {
        BarcodeGenerator.createBarcodeBitmap(bitMatrix).asImageBitmap()
    }

    Box(
        modifier = modifier
            .background(Color.White)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Barcode Image",
            filterQuality = FilterQuality.None, // Exact pixel alignment with no blur
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
