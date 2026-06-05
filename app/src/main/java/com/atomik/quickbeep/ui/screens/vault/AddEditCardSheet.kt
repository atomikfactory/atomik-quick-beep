package com.atomik.quickbeep.ui.screens.vault

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomik.quickbeep.ui.components.BarcodeGenerator
import com.atomik.quickbeep.ui.components.BarcodeRenderer
import com.atomik.quickbeep.ui.components.DocumentImageRenderer
import com.atomik.quickbeep.ui.viewmodel.CardsViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

// Color palettes conforming to the "Purple Ban"
val PRESET_COLORS = listOf(
    "#374151", // Charcoal/Slate
    "#1E3A8A", // Navy Blue
    "#065F46", // Emerald Green
    "#0F766E", // Teal
    "#9A3412", // Amber/Dark Orange
    "#991B1B", // Crimson/Ruby Red
    "#475569"  // Slate Blue
)

// CATEGORIES loaded dynamically from viewmodel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AddEditCardSheet(
    rawValue: String,
    format: String,
    formatInt: Int,
    viewModel: CardsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("qa_settings", Context.MODE_PRIVATE)
    }
    val qaModeEnabled = remember {
        sharedPrefs.getBoolean("qa_mode_enabled", false)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editingCard by viewModel.editingCard.collectAsState()
    val capturedFrontPath by viewModel.capturedFrontPath.collectAsState()
    val capturedBackPath by viewModel.capturedBackPath.collectAsState()
    val tagsList by viewModel.tags.collectAsState()

    var cardName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS[0]) }
    var selectedCategory by remember { mutableStateOf("Others") }
    var nameError by remember { mutableStateOf(false) }

    LaunchedEffect(tagsList) {
        if (tagsList.isNotEmpty() && !tagsList.contains(selectedCategory) && editingCard == null) {
            selectedCategory = if (tagsList.contains("Others")) "Others" else tagsList.first()
        }
    }

    // Prefill form states on edit trigger or scan auto-suggestions
    LaunchedEffect(editingCard, rawValue, tagsList) {
        if (editingCard != null) {
            cardName = editingCard!!.name
            selectedColor = editingCard!!.hexColor
            selectedCategory = editingCard!!.category
        } else {
            // Auto-detect common patterns on scans (mock brand detector)
            val lowerValue = rawValue.lowercase()
            if (lowerValue.contains("starbucks")) {
                cardName = "Starbucks"
                selectedColor = "#065F46"
                selectedCategory = if (tagsList.contains("Others")) "Others" else (tagsList.firstOrNull() ?: "Others")
            } else if (lowerValue.contains("costco")) {
                cardName = "Costco"
                selectedColor = "#991B1B"
                selectedCategory = if (tagsList.contains("Shopping")) "Shopping" else (tagsList.firstOrNull() ?: "Others")
            } else {
                cardName = ""
                selectedColor = PRESET_COLORS[0]
                selectedCategory = if (tagsList.contains("Others")) "Others" else (tagsList.firstOrNull() ?: "Others")
            }
        }
    }

    var isScanningFront by remember { mutableStateOf(true) }

    val docScannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val docScanner = remember(context) {
        GmsDocumentScanning.getClient(docScannerOptions)
    }

    val docScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                viewModel.updatePhotoFromUri(context, uri, isFront = isScanningFront)
            }
        }
    }

    val triggerScan = { isFront: Boolean ->
        isScanningFront = isFront
        val act = context as? Activity
        if (act != null) {
            docScanner.getStartScanIntent(act)
                .addOnSuccessListener { intentSender ->
                    docScannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(context, "Scanner failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Could not resolve Activity context", Toast.LENGTH_SHORT).show()
        }
    }

    val isEditingMode = editingCard != null
    val isPdf = format == "PDF" || editingCard?.barcodeFormat == "PDF"
    val isDocumentCard = format == "DOCUMENT" || isPdf || capturedFrontPath != null || editingCard?.frontPhotoPath != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { Spacer(modifier = Modifier.height(16.dp)) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = if (isEditingMode) "Edit Vault Item" else "Save Vault Item",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Card Input Name
            OutlinedTextField(
                value = cardName,
                onValueChange = {
                    cardName = it
                    nameError = false
                },
                label = { Text("Item Name (e.g. Driver's License or Starbucks)") },
                isError = nameError,
                supportingText = {
                    if (nameError) {
                        Text("Name cannot be empty", color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selection Chips
            Text(
                text = "Category Tag",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagsList.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Color Picker
            Text(
                text = "Item Color Style",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PRESET_COLORS.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedColor == hex) 3.dp else 0.dp,
                                color = if (selectedColor == hex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor == hex) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected Color Indicator",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Item Preview Section
            Text(
                text = "Regenerated Preview",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isDocumentCard) 180.dp else 130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(android.graphics.Color.parseColor(selectedColor)))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = if (cardName.isEmpty()) "Item Preview" else cardName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDocumentCard) {
                        // Render document preview thumbnails (Front / Back side by side)
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPdf) {
                                // PDF layout: Only show PDF thumbnail (capturedBackPath)
                                capturedBackPath?.let { path ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PDF Document Preview", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        DocumentImageRenderer(
                                            filePath = path,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                }
                            } else {
                                // Standard document layout: Show Front and Back slots (placeholders or renderers)
                                
                                // Front Slot
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Front (Recto)", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (capturedFrontPath != null) {
                                        DocumentImageRenderer(
                                            filePath = capturedFrontPath!!,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { triggerScan(true) }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .clickable { triggerScan(true) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Scan front photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Scan Front",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Back Slot
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Back (Verso)", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (capturedBackPath != null) {
                                        DocumentImageRenderer(
                                            filePath = capturedBackPath!!,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { triggerScan(false) }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .clickable { triggerScan(false) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Scan back photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Scan Back",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Render vector barcode graphic preview
                        BarcodeRenderer(
                            rawValue = if (isEditingMode) editingCard!!.rawValue else rawValue,
                            formatName = if (isEditingMode) editingCard!!.barcodeFormat else format,
                            formatInt = if (isEditingMode) editingCard!!.barcodeFormatInt else formatInt,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }

            if (qaModeEnabled && !isDocumentCard) {
                Spacer(modifier = Modifier.height(12.dp))
                val displayRawValue = if (isEditingMode) editingCard!!.rawValue else rawValue
                val displayFormatStr = if (isEditingMode) editingCard!!.barcodeFormat else format
                val displayFormatInt = if (isEditingMode) editingCard!!.barcodeFormatInt else formatInt
                val clipboardManager = LocalClipboardManager.current
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "DEBUG CONSOLE",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Format: ${BarcodeGenerator.getMlKitFormatName(displayFormatInt, displayFormatStr)}",
                        color = Color(0xFF00FF66),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Raw: $displayRawValue",
                        color = Color(0xFF00FF66),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.combinedClickable(
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(displayRawValue))
                                Toast.makeText(context, "Copied raw value to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            onClick = {}
                        )
                    )
                }
            }

            val saveLogs by viewModel.saveLogs.collectAsState()
            if (qaModeEnabled && saveLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "MOBILE VERBOSE LOGS",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = saveLogs,
                            color = Color(0xFF00FF66),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // CTA Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.size(16.dp))
                Button(
                    onClick = {
                        if (cardName.trim().isEmpty()) {
                            nameError = true
                        } else {
                            viewModel.saveCard(
                                name = cardName.trim(),
                                rawValue = if (isEditingMode) editingCard!!.rawValue else rawValue,
                                format = if (isEditingMode) editingCard!!.barcodeFormat else format,
                                formatInt = if (isEditingMode) editingCard!!.barcodeFormatInt else formatInt,
                                hexColor = selectedColor,
                                category = selectedCategory,
                                frontPhoto = capturedFrontPath,
                                backPhoto = capturedBackPath
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isEditingMode) "Save Changes" else "Save Item",
                        color = Color.White
                    )
                }
            }
        }
    }
}
