package com.atomik.quickbeep.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DocumentImageRenderer(
    filePath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    // Read and decrypt bytes on-the-fly
                    val encryptedBytes = file.readBytes()
                    val decryptedBytes = com.atomik.quickbeep.security.CryptographyManager.decryptData(encryptedBytes)
                    
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
                    
                    // Request safe maximum size to avoid OOM
                    options.inSampleSize = calculateInSampleSize(options, 800, 800)
                    options.inJustDecodeBounds = false
                    
                    val decoded = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
                    if (decoded != null) {
                        bitmap = decoded.asImageBitmap()
                        hasError = false
                    } else {
                        hasError = true
                    }
                } else {
                    hasError = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                hasError = true
            }
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        hasError -> {
            Box(
                modifier = modifier.background(Color.Red.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                // Keep error layout blank or background tint
            }
        }
        else -> {
            Box(
                modifier = modifier.background(Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
