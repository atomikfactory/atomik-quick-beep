package com.atomik.quickbeep.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atomik.quickbeep.data.model.LoyaltyCard
import com.atomik.quickbeep.domain.repository.CardRepository
import com.atomik.quickbeep.security.DocumentFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class CardsViewModel(
    private val repository: CardRepository,
    private val sharedPrefs: android.content.SharedPreferences
) : ViewModel() {

    // Category tags state
    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    // Debug logs for save operation
    private val _saveLogs = MutableStateFlow<String>("")
    val saveLogs: StateFlow<String> = _saveLogs.asStateFlow()

    init {
        loadTags()
    }

    private fun loadTags() {
        val defaultTags = setOf("Groceries", "Gym", "Transit", "Shopping", "Others")
        val savedTags = sharedPrefs.getStringSet("vault_tags", defaultTags) ?: defaultTags
        _tags.value = savedTags.toList().sorted()
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotEmpty() && !_tags.value.contains(trimmed)) {
            val updated = _tags.value.toMutableSet().apply { add(trimmed) }
            sharedPrefs.edit().putStringSet("vault_tags", updated).apply()
            _tags.value = updated.toList().sorted()
        }
    }

    fun removeTag(tag: String) {
        if (tag == "Others") return
        val updated = _tags.value.toMutableSet().apply { remove(tag) }
        sharedPrefs.edit().putStringSet("vault_tags", updated).apply()
        _tags.value = updated.toList().sorted()
    }

    // User Biometric Unlock State (cold start verification)
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun setUnlocked(unlocked: Boolean) {
        _isUnlocked.value = unlocked
    }

    // Search and category filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Backup operation statuses
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    // Scanned Card Confirmation Sheet States
    private val _scannedCardResult = MutableStateFlow<Triple<String, String, Int>?>(null) // Triple(rawValue, formatName, formatInt)
    val scannedCardResult: StateFlow<Triple<String, String, Int>?> = _scannedCardResult.asStateFlow()

    // Card currently being edited
    private val _editingCard = MutableStateFlow<LoyaltyCard?>(null)
    val editingCard: StateFlow<LoyaltyCard?> = _editingCard.asStateFlow()

    // Temp path states for capturing document images
    private val _capturedFrontPath = MutableStateFlow<String?>(null)
    val capturedFrontPath: StateFlow<String?> = _capturedFrontPath.asStateFlow()

    private val _capturedBackPath = MutableStateFlow<String?>(null)
    val capturedBackPath: StateFlow<String?> = _capturedBackPath.asStateFlow()

    private val _showAddSheet = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    fun onBarcodeScanned(rawValue: String, formatName: String, formatInt: Int) {
        _scannedCardResult.value = Triple(rawValue, formatName, formatInt)
        _editingCard.value = null
        _showAddSheet.value = true
    }

    fun editCard(card: LoyaltyCard) {
        _editingCard.value = card
        _scannedCardResult.value = null
        _capturedFrontPath.value = card.frontPhotoPath
        _capturedBackPath.value = card.backPhotoPath
        _showAddSheet.value = true
    }

    fun closeAddSheet() {
        _scannedCardResult.value = null
        _editingCard.value = null
        _capturedFrontPath.value = null
        _capturedBackPath.value = null
        _showAddSheet.value = false
    }

    fun clearCapturedPhotos() {
        _capturedFrontPath.value = null
        _capturedBackPath.value = null
    }

    fun setCapturedPhotoPaths(frontPath: String?, backPath: String?) {
        _capturedFrontPath.value = frontPath
        _capturedBackPath.value = backPath
    }

    // Set search query
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Set active category filter
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    // Clear backup status messages
    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    // Photo Capture helper logic
    fun onPhotoCaptured(tempFile: File, isFront: Boolean, context: Context) {
        viewModelScope.launch {
            val savedPath = DocumentFileManager.saveImageToSecureStorage(context, tempFile, isFront)
            if (isFront) {
                _capturedFrontPath.value = savedPath
            } else {
                _capturedBackPath.value = savedPath
            }
        }
    }

    fun updatePhotoFromUri(context: Context, uri: Uri, isFront: Boolean) {
        viewModelScope.launch {
            val savedPath = DocumentFileManager.saveUriToSecureStorage(context, uri, isFront)
            if (isFront) {
                _capturedFrontPath.value = savedPath
            } else {
                _capturedBackPath.value = savedPath
            }
        }
    }

    // Reactive cards list filtering based on search query and category selections
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<LoyaltyCard>> = combine(
        _isUnlocked,
        _searchQuery,
        _selectedCategory
    ) { unlocked, query, category ->
        Triple(unlocked, query, category)
    }.flatMapLatest { (unlocked, query, category) ->
        if (!unlocked) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            if (query.isNotEmpty()) {
                repository.searchCards(query)
            } else if (category != "All") {
                repository.getCardsByCategory(category)
            } else {
                repository.getAllCards()
            }
        }
    }.catch { e ->
        android.util.Log.e("CardsViewModel", "Error collecting cards flow", e)
        if (e is android.security.keystore.UserNotAuthenticatedException || 
            e.cause is android.security.keystore.UserNotAuthenticatedException) {
            _isUnlocked.value = false
        }
        emit(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // DB Operations
    fun saveCard(
        name: String,
        rawValue: String,
        format: String,
        formatInt: Int,
        hexColor: String,
        category: String,
        frontPhoto: String? = null,
        backPhoto: String? = null
    ) {
        _saveLogs.value = "" // Clear previous logs
        viewModelScope.launch {
            val log = java.lang.StringBuilder()
            fun logMsg(msg: String) {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
                log.append("[$timestamp] $msg\n")
                _saveLogs.value = log.toString()
                android.util.Log.d("CardsViewModel", msg)
            }

            logMsg("Starting saveCard: name='$name', rawValue.length=${rawValue.length}, format='$format', formatInt=$formatInt, category='$category'")
            try {
                val edit = _editingCard.value
                if (edit != null) {
                    logMsg("Saving card in EDIT mode for id: ${edit.id}")
                    if (edit.frontPhotoPath != null && edit.frontPhotoPath != frontPhoto) {
                        logMsg("Deleting old front photo: ${edit.frontPhotoPath}")
                        val deleted = DocumentFileManager.deleteFile(edit.frontPhotoPath)
                        logMsg("Front photo delete result: $deleted")
                    }
                    if (edit.backPhotoPath != null && edit.backPhotoPath != backPhoto) {
                        logMsg("Deleting old back photo: ${edit.backPhotoPath}")
                        val deleted = DocumentFileManager.deleteFile(edit.backPhotoPath)
                        logMsg("Back photo delete result: $deleted")
                    }

                    val updated = edit.copy(
                        name = name,
                        rawValue = if (rawValue.isNotEmpty()) rawValue else edit.rawValue,
                        barcodeFormat = if (format.isNotEmpty()) format else edit.barcodeFormat,
                        barcodeFormatInt = if (formatInt != -1) formatInt else edit.barcodeFormatInt,
                        hexColor = hexColor,
                        category = category,
                        frontPhotoPath = frontPhoto,
                        backPhotoPath = backPhoto,
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                    logMsg("Inserting/replacing updated card in Room database...")
                    repository.insertCard(updated)
                    logMsg("Database update successful.")
                } else {
                    logMsg("Saving card in INSERT mode")
                    val card = LoyaltyCard(
                        name = name,
                        rawValue = rawValue,
                        barcodeFormat = format,
                        barcodeFormatInt = formatInt,
                        hexColor = hexColor,
                        category = category,
                        frontPhotoPath = frontPhoto,
                        backPhotoPath = backPhoto,
                        lastUsedTimestamp = System.currentTimeMillis()
                    )
                    logMsg("Inserting new card in Room database...")
                    repository.insertCard(card)
                    logMsg("Database insertion successful.")
                }
                logMsg("Closing details sheet.")
                closeAddSheet()
            } catch (e: Exception) {
                logMsg("CRITICAL EXCEPTION: ${e.javaClass.name} - ${e.message}")
                logMsg("Stack Trace:\n${android.util.Log.getStackTraceString(e)}")
                if (e is android.security.keystore.UserNotAuthenticatedException || 
                    e.cause is android.security.keystore.UserNotAuthenticatedException) {
                    logMsg("Resetting unlock state to false.")
                    _isUnlocked.value = false
                }
            }
        }
    }

    fun updateCardUsage(card: LoyaltyCard) {
        viewModelScope.launch {
            try {
                val updated = card.copy(lastUsedTimestamp = System.currentTimeMillis())
                repository.updateCard(updated)
            } catch (e: Exception) {
                if (e is android.security.keystore.UserNotAuthenticatedException || 
                    e.cause is android.security.keystore.UserNotAuthenticatedException) {
                    _isUnlocked.value = false
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteCard(card: LoyaltyCard) {
        viewModelScope.launch {
            try {
                // Clean up files securely
                DocumentFileManager.deleteFile(card.frontPhotoPath)
                DocumentFileManager.deleteFile(card.backPhotoPath)
                repository.deleteCard(card)
            } catch (e: Exception) {
                if (e is android.security.keystore.UserNotAuthenticatedException || 
                    e.cause is android.security.keystore.UserNotAuthenticatedException) {
                    _isUnlocked.value = false
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    // JSON Import/Export Trigger operations
    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = repository.exportBackup(context, uri)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception is android.security.keystore.UserNotAuthenticatedException || 
                    exception?.cause is android.security.keystore.UserNotAuthenticatedException) {
                    _isUnlocked.value = false
                    return@launch
                }
            }
            _backupStatus.value = if (result.isSuccess) {
                "Backup exported successfully!"
            } else {
                "Export failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = repository.importBackup(context, uri)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception is android.security.keystore.UserNotAuthenticatedException || 
                    exception?.cause is android.security.keystore.UserNotAuthenticatedException) {
                    _isUnlocked.value = false
                    return@launch
                }
            }
            _backupStatus.value = if (result.isSuccess) {
                "Backup imported successfully!"
            } else {
                "Import failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // Factory to construct ViewModels with parameters
    class Factory(
        private val repository: CardRepository,
        private val sharedPrefs: android.content.SharedPreferences
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CardsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CardsViewModel(repository, sharedPrefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
