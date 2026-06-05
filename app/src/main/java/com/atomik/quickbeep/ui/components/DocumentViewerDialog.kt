package com.atomik.quickbeep.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atomik.quickbeep.data.model.LoyaltyCard
import com.atomik.quickbeep.security.CryptographyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentViewerDialog(
    card: LoyaltyCard,
    onDismiss: () -> Unit
) {
    var frontBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var backBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Decrypt images in-memory to prevent temp file leak
    LaunchedEffect(card) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                card.frontPhotoPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val encryptedBytes = file.readBytes()
                        val decryptedBytes = CryptographyManager.decryptData(encryptedBytes)
                        val bmp = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                        frontBitmap = bmp?.asImageBitmap()
                    }
                }
                card.backPhotoPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val encryptedBytes = file.readBytes()
                        val decryptedBytes = CryptographyManager.decryptData(encryptedBytes)
                        val bmp = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                        backBitmap = bmp?.asImageBitmap()
                    }
                }
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
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
            } else if (frontBitmap != null || backBitmap != null) {
                DocumentImagesContainer(
                    frontBitmap = frontBitmap,
                    backBitmap = backBitmap,
                    cardName = card.name
                )
            } else {
                Text(
                    text = "Failed to load document images",
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
                    contentDescription = "Close Document Viewer",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun DocumentImagesContainer(
    frontBitmap: ImageBitmap?,
    backBitmap: ImageBitmap?,
    cardName: String
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
                userScrollEnabled = isScrollEnabled,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = cardName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (frontBitmap != null) {
                    item {
                        Text(
                            text = "Document Front",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        val frontAspect = frontBitmap.width.toFloat() / frontBitmap.height.toFloat()
                        Image(
                            bitmap = frontBitmap,
                            contentDescription = "Document Front Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .background(Color.White)
                                .aspectRatio(frontAspect)
                        )
                    }
                }

                if (backBitmap != null) {
                    item {
                        Text(
                            text = "Document Back",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        val backAspect = backBitmap.width.toFloat() / backBitmap.height.toFloat()
                        Image(
                            bitmap = backBitmap,
                            contentDescription = "Document Back Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .background(Color.White)
                                .aspectRatio(backAspect)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}
