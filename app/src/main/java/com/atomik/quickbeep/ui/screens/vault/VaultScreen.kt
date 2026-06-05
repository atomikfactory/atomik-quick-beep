package com.atomik.quickbeep.ui.screens.vault

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.atomik.quickbeep.ui.components.BarcodeGenerator
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.atomik.quickbeep.data.model.LoyaltyCard
import com.atomik.quickbeep.security.DocumentFileManager
import com.atomik.quickbeep.ui.components.BarcodeRenderer
import com.atomik.quickbeep.ui.components.DocumentImageRenderer
import com.atomik.quickbeep.ui.components.PdfViewerDialog
import com.atomik.quickbeep.ui.components.DocumentViewerDialog
import com.atomik.quickbeep.ui.screens.scanner.ScannerScreen
import com.atomik.quickbeep.ui.viewmodel.CardsViewModel
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(viewModel: CardsViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    val sharedPrefs = remember(context) {
        context.getSharedPreferences("qa_settings", Context.MODE_PRIVATE)
    }
    var qaModeEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("qa_mode_enabled", false))
    }

    val cards by viewModel.cards.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    
    // View States
    var isScannerOpen by remember { mutableStateOf(false) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<LoyaltyCard?>(null) }
    var activePdfPathToView by remember { mutableStateOf<String?>(null) }
    var activeDocToView by remember { mutableStateOf<LoyaltyCard?>(null) }
    var selectedCarouselCard by remember { mutableStateOf<LoyaltyCard?>(null) }
    
    // 3D Card Flipped State Map
    val flippedPages = remember { mutableStateMapOf<Int, Boolean>() }

    // Bottom Sheet Confirmation
    val showAddSheet by viewModel.showAddSheet.collectAsState()
    val scannedResult by viewModel.scannedCardResult.collectAsState()
    val editingCard by viewModel.editingCard.collectAsState()

    // ML Kit Document Scanner Integration
    val docScannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(2)
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
            scanningResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val frontUri = pages[0].imageUri
                    val backUri = if (pages.size > 1) pages[1].imageUri else null
                    
                    val frontPath = DocumentFileManager.saveUriToSecureStorage(context, frontUri, isFront = true)
                    val backPath = backUri?.let {
                        DocumentFileManager.saveUriToSecureStorage(context, it, isFront = false)
                    }
                    
                    viewModel.setCapturedPhotoPaths(frontPath, backPath)
                    viewModel.onBarcodeScanned("", "DOCUMENT", -1)
                }
            }
        }
    }

    // PDF Import File Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val result = DocumentFileManager.savePdfFileSecurely(context, it)
            if (result != null) {
                val pdfPath = result.first
                val thumbPath = result.second
                viewModel.setCapturedPhotoPaths(pdfPath, thumbPath)
                viewModel.onBarcodeScanned("", "PDF", -1)
            } else {
                Toast.makeText(context, "Failed to save PDF file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Backup Intent Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackup(context, it) }
    }

    // Monitor Backup Notifications
    val backupStatus by viewModel.backupStatus.collectAsState()
    LaunchedEffect(backupStatus) {
        backupStatus?.let { status ->
            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            viewModel.clearBackupStatus()
        }
    }

    // Main Scaffold UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Atomik Quick Beep",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            qaModeEnabled = !qaModeEnabled
                            sharedPrefs.edit().putBoolean("qa_mode_enabled", qaModeEnabled).apply()
                            val msg = if (qaModeEnabled) "QA Mode Enabled" else "QA Mode Disabled"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Toggle QA Mode",
                            tint = if (qaModeEnabled) Color(0xFF00FF66) else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { exportLauncher.launch("quick_beep_backup.json") }) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Export Backup",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Import Backup",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddOptionsDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Open Scan Options",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search vault items...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Category filter chips
            val tagsList by viewModel.tags.collectAsState()
            val categoriesList = remember(tagsList) { listOf("All") + tagsList }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(categoriesList) { category ->
                    val isSelected = selectedCategory == category
                    InputChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category) },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                item {
                    var showManageTagsDialog by remember { mutableStateOf(false) }
                    
                    InputChip(
                        selected = false,
                        onClick = { showManageTagsDialog = true },
                        label = { Text("+ Manage") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Manage Tags",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    if (showManageTagsDialog) {
                        ManageTagsDialog(
                            viewModel = viewModel,
                            onDismiss = { showManageTagsDialog = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "No Items Saved Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the '+' button below to scan a barcode or capture secure document photos.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Grid / List View Layout
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(cards) { card ->
                        val cardColor = remember(card.hexColor) {
                            Color(android.graphics.Color.parseColor(card.hexColor))
                        }
                        val isDoc = card.frontPhotoPath != null
                        val isPdf = card.barcodeFormat == "PDF"
                        val photoPathToRender = if (isPdf) card.backPhotoPath else card.frontPhotoPath
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clickable {
                                    if (isPdf) {
                                        activePdfPathToView = card.frontPhotoPath
                                    } else if (isDoc) {
                                        activeDocToView = card
                                    } else {
                                        selectedCarouselCard = card
                                    }
                                    viewModel.updateCardUsage(card)
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = card.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = { viewModel.editCard(card) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Item",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { cardToDelete = card },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (isDoc) {
                                        DocumentImageRenderer(
                                            filePath = photoPathToRender ?: "",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        BarcodeRenderer(
                                            rawValue = card.rawValue,
                                            formatName = card.barcodeFormat,
                                            formatInt = card.barcodeFormatInt,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                }
                                Text(
                                    text = card.category,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Options Selector Dialog
    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = { Text("Add Vault Item") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = "Select how you would like to import your loyalty card or secure document:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Option 1: Scan Barcode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddOptionsDialog = false
                                isScannerOpen = true
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Scan Barcode", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Import using real-time camera scanner", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 2: Scan / Import Document
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddOptionsDialog = false
                                viewModel.clearCapturedPhotos()
                                val act = activity ?: (context as? Activity)
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
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Scan / Import Document", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Auto-cropped camera scan or gallery photos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 3: Import PDF
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddOptionsDialog = false
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Import PDF File", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Securely store and view the full PDF document", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Camera viewfinder layout overlay
    if (isScannerOpen) {
        ScannerScreen(
            viewModel = viewModel,
            onCloseScanner = { isScannerOpen = false }
        )
    }

    // Bottom sheet details confirm/edit dialog
    if (showAddSheet) {
        val resultRaw = scannedResult?.first ?: ""
        val resultFormat = scannedResult?.second ?: editingCard?.barcodeFormat ?: ""
        val resultFormatInt = scannedResult?.third ?: editingCard?.barcodeFormatInt ?: -1
        
        AddEditCardSheet(
            rawValue = resultRaw,
            format = resultFormat,
            formatInt = resultFormatInt,
            viewModel = viewModel,
            onDismiss = { viewModel.closeAddSheet() }
        )
    }

    // Confirm Delete Dialog
    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("Delete Card?") },
            text = { Text("Are you sure you want to permanently delete \"${card.name}\" from your vault?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCard(card)
                        cardToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // PDF Viewer Dialog Overlay
    activePdfPathToView?.let { pdfPath ->
        PdfViewerDialog(
            pdfPath = pdfPath,
            onDismiss = { activePdfPathToView = null }
        )
    }

    // Document Viewer Dialog Overlay
    activeDocToView?.let { docCard ->
        DocumentViewerDialog(
            card = docCard,
            onDismiss = { activeDocToView = null }
        )
    }

    // Detail Carousel Overlay
    selectedCarouselCard?.let { initialCard ->
        val initialCardType = if (initialCard.frontPhotoPath != null) "DOCUMENT" else "BARCODE"
        val filteredCards = remember(cards, initialCardType) {
            cards.filter { card ->
                when (initialCardType) {
                    "DOCUMENT" -> card.frontPhotoPath != null && card.barcodeFormat != "PDF"
                    else -> card.frontPhotoPath == null && card.barcodeFormat != "PDF"
                }
            }
        }
        val startIndex = remember(filteredCards, initialCard) {
            val idx = filteredCards.indexOf(initialCard)
            if (idx != -1) idx else 0
        }
        
        androidx.compose.runtime.key(initialCard.id) {
            CarouselOverlay(
                initialCard = initialCard,
                filteredCards = filteredCards,
                startIndex = startIndex,
                viewModel = viewModel,
                qaModeEnabled = qaModeEnabled,
                flippedPages = flippedPages,
                onDeleteClick = { cardToDelete = it },
                onDismiss = { selectedCarouselCard = null }
            )
        }
    }
}

@Composable
fun ManageTagsDialog(
    viewModel: CardsViewModel,
    onDismiss: () -> Unit
) {
    val tagsList by viewModel.tags.collectAsState()
    var newTagText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Category Tags") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Add or remove custom tags to organize your loyalty cards and documents.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Add tag input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = {
                            newTagText = it
                            inputError = false
                        },
                        placeholder = { Text("e.g. Work, Keys...") },
                        singleLine = true,
                        isError = inputError,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = newTagText.trim()
                            if (trimmed.isEmpty() || tagsList.contains(trimmed)) {
                                inputError = true
                            } else {
                                viewModel.addTag(trimmed)
                                newTagText = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add")
                    }
                }
                
                if (inputError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (newTagText.trim().isEmpty()) "Tag cannot be empty" else "Tag already exists",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Current Tags",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // List of existing tags
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tagsList) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            if (tag != "Others") {
                                IconButton(
                                    onClick = { viewModel.removeTag(tag) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Tag",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "System",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CarouselOverlay(
    initialCard: LoyaltyCard,
    filteredCards: List<LoyaltyCard>,
    startIndex: Int,
    viewModel: CardsViewModel,
    qaModeEnabled: Boolean,
    flippedPages: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Boolean>,
    onDeleteClick: (LoyaltyCard) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { filteredCards.size })
    
    // Boost brightness only if active card is a barcode card
    LaunchedEffect(pagerState.currentPage, filteredCards) {
        val activity = context as? Activity
        activity?.let { act ->
            if (filteredCards.isNotEmpty() && pagerState.currentPage < filteredCards.size) {
                val activeCard = filteredCards[pagerState.currentPage]
                
                val layoutParams = act.window.attributes
                val isBarcode = activeCard.frontPhotoPath == null && activeCard.barcodeFormat != "PDF"
                layoutParams.screenBrightness = if (isBarcode) 1.0f else -1.0f
                act.window.attributes = layoutParams
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.let { act ->
                val layoutParams = act.window.attributes
                layoutParams.screenBrightness = -1.0f
                act.window.attributes = layoutParams
            }
        }
    }
    
    val initialCardType = if (initialCard.frontPhotoPath != null) "Document" else "Barcode"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "$initialCardType Details",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to List",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val card = filteredCards[page]
                    val isDocument = card.frontPhotoPath != null && card.barcodeFormat != "PDF"

                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .graphicsLayer {
                                val scale = lerp(0.85f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                                scaleX = scale
                                scaleY = scale
                                alpha = lerp(0.5f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                                shadowElevation = (1f - pageOffset.coerceIn(0f, 1f)) * 10f * density
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDocument) {
                            DocumentCard(
                                card = card,
                                viewModel = viewModel,
                                flippedPages = flippedPages,
                                page = page,
                                onDeleteClick = onDeleteClick
                            )
                        } else {
                            BarcodeCard(
                                card = card,
                                viewModel = viewModel,
                                qaModeEnabled = qaModeEnabled,
                                onDeleteClick = onDeleteClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BarcodeCard(
    card: LoyaltyCard,
    viewModel: CardsViewModel,
    qaModeEnabled: Boolean,
    onDeleteClick: (LoyaltyCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = remember(card.hexColor) {
        Color(android.graphics.Color.parseColor(card.hexColor))
    }
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.category,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row {
                    IconButton(onClick = { viewModel.editCard(card) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Card",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = { onDeleteClick(card) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Card",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Barcode Renderer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (qaModeEnabled) 130.dp else 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                BarcodeRenderer(
                    rawValue = card.rawValue,
                    formatName = card.barcodeFormat,
                    formatInt = card.barcodeFormatInt,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (qaModeEnabled) {
                val clipboardManager = LocalClipboardManager.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "DEBUG CONSOLE",
                        color = Color(0xFF64748B),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                          text = "Format: ${BarcodeGenerator.getMlKitFormatName(card.barcodeFormatInt, card.barcodeFormat)}",
                          color = Color(0xFF00FF66),
                          fontSize = 11.sp,
                          fontFamily = FontFamily.Monospace
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                          text = "Raw: ${card.rawValue}",
                          color = Color(0xFF00FF66),
                          fontSize = 11.sp,
                          fontFamily = FontFamily.Monospace,
                          modifier = Modifier.combinedClickable(
                              onLongClick = {
                                  clipboardManager.setText(AnnotatedString(card.rawValue))
                                  Toast.makeText(context, "Copied raw value to clipboard!", Toast.LENGTH_SHORT).show()
                              },
                              onClick = {}
                          )
                      )
                  }
              }

              // Footer info
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                      text = card.rawValue,
                      color = Color.White,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "Keep screen visible to the cashier",
                      color = Color.White.copy(alpha = 0.6f),
                      fontSize = 11.sp,
                      textAlign = TextAlign.Center
                  )
              }
          }
      }
  }

@Composable
fun DocumentCard(
    card: LoyaltyCard,
    viewModel: CardsViewModel,
    flippedPages: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Boolean>,
    page: Int,
    onDeleteClick: (LoyaltyCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = remember(card.hexColor) {
        Color(android.graphics.Color.parseColor(card.hexColor))
    }
    
    val isFlipped = flippedPages[page] ?: false
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "CardFlipAnimation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clickable {
                flippedPages[page] = !isFlipped
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (rotation <= 90f) {
                // Front Card Side Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = card.category,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row {
                            IconButton(onClick = { viewModel.editCard(card) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Card",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(onClick = { onDeleteClick(card) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Card",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Secure Front Photo display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DocumentImageRenderer(
                            filePath = card.frontPhotoPath ?: "",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = if (card.backPhotoPath != null) "Tap card to flip to back" else "Secure Document Card",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Flipped Back Side Content (rotate 180 degrees visually)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Document Reverse",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { onDeleteClick(card) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Card",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (card.backPhotoPath != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                DocumentImageRenderer(
                                    filePath = card.backPhotoPath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No Back Capture Saved",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Text(
                            text = "Tap card to flip to front",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

