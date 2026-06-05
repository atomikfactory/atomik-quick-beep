package com.atomik.quickbeep.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atomik.quickbeep.security.CryptographyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewerDialog(
    pdfPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tempFile by remember { mutableStateOf<File?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    // Decrypt PDF to a temporary file in background for rendering
    LaunchedEffect(pdfPath) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedFile = File(pdfPath)
                if (encryptedFile.exists()) {
                    val encryptedBytes = encryptedFile.readBytes()
                    val decryptedBytes = CryptographyManager.decryptData(encryptedBytes)
                    
                    val temp = File.createTempFile("temp_pdf_", ".pdf", context.cacheDir)
                    temp.writeBytes(decryptedBytes)
                    tempFile = temp

                    val input = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(input)
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
            }
        }
    }

    // Clean up temporary file and renderer on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                tempFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (pdfRenderer != null) {
                PdfPagesContainer(
                    renderer = pdfRenderer!!,
                    pageCount = pageCount
                )
            } else {
                Text(
                    text = "Failed to load PDF",
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close PDF Viewer",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun PdfPagesContainer(
    renderer: PdfRenderer,
    pageCount: Int
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isScrollEnabled by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        // Gestures box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scale) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val activePointers = event.changes.filter { it.pressed }.size
                            isScrollEnabled = scale == 1f && activePointers <= 1
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        
                        if (newScale > 1f) {
                            val maxX = (widthPx * (newScale - 1) / 2f).coerceAtLeast(0f)
                            val maxY = (heightPx * (newScale - 1) / 2f).coerceAtLeast(0f)
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = isScrollEnabled
            ) {
                items(pageCount) { pageIndex ->
                    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                    LaunchedEffect(pageIndex) {
                        withContext(Dispatchers.IO) {
                            try {
                                val page = renderer.openPage(pageIndex)
                                val scaleFactor = 2f
                                val width = (page.width * scaleFactor).toInt()
                                val height = (page.height * scaleFactor).toInt()
                                
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageBitmap = bitmap.asImageBitmap()
                                page.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (pageBitmap != null) {
                        val aspectRatio = pageBitmap!!.width.toFloat() / pageBitmap!!.height.toFloat()
                        Image(
                            bitmap = pageBitmap!!,
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(Color.White)
                                .aspectRatio(aspectRatio)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
