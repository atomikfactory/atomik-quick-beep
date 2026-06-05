package com.atomik.quickbeep.data.repository

import android.content.Context
import android.net.Uri
import com.atomik.quickbeep.data.database.CardDao
import com.atomik.quickbeep.data.model.LoyaltyCard
import com.atomik.quickbeep.domain.repository.CardRepository
import com.atomik.quickbeep.security.CryptographyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

class CardRepositoryImpl(
    private val cardDao: CardDao
) : CardRepository {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private fun encryptCard(card: LoyaltyCard): LoyaltyCard {
        return card.copy(
            rawValue = CryptographyManager.encryptString(card.rawValue)
        )
    }

    private fun decryptCard(card: LoyaltyCard): LoyaltyCard {
        return try {
            card.copy(
                rawValue = CryptographyManager.decryptString(card.rawValue)
            )
        } catch (e: Exception) {
            android.util.Log.e("CardRepositoryImpl", "Decryption failed for card ${card.id}: ${e.message}", e)
            if (e is android.security.keystore.UserNotAuthenticatedException || 
                e.cause is android.security.keystore.UserNotAuthenticatedException) {
                throw e
            }
            card.copy(
                rawValue = "[Decryption Error]"
            )
        }
    }

    override fun getAllCards(): Flow<List<LoyaltyCard>> = 
        cardDao.getAllCardsFlow().map { list ->
            list.map { decryptCard(it) }
        }

    override fun searchCards(query: String): Flow<List<LoyaltyCard>> {
        val searchQuery = "%$query%"
        return cardDao.searchCardsFlow(searchQuery).map { list ->
            list.map { decryptCard(it) }
        }
    }

    override fun getCardsByCategory(category: String): Flow<List<LoyaltyCard>> =
        cardDao.getCardsByCategoryFlow(category).map { list ->
            list.map { decryptCard(it) }
        }

    override suspend fun insertCard(card: LoyaltyCard): Long = withContext(Dispatchers.IO) {
        cardDao.insertCard(encryptCard(card))
    }

    override suspend fun updateCard(card: LoyaltyCard) = withContext(Dispatchers.IO) {
        cardDao.updateCard(encryptCard(card))
    }

    override suspend fun deleteCard(card: LoyaltyCard) = withContext(Dispatchers.IO) {
        cardDao.deleteCard(card)
    }

    override suspend fun exportBackup(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cards = cardDao.getAllCardsDirect()
            val backupList = cards.map { card ->
                val decrypted = decryptCard(card)
                
                val frontBase64 = card.frontPhotoPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val encryptedBytes = file.readBytes()
                        val decryptedBytes = CryptographyManager.decryptData(encryptedBytes)
                        android.util.Base64.encodeToString(decryptedBytes, android.util.Base64.NO_WRAP)
                    } else null
                }
                
                val backBase64 = card.backPhotoPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val encryptedBytes = file.readBytes()
                        val decryptedBytes = CryptographyManager.decryptData(encryptedBytes)
                        android.util.Base64.encodeToString(decryptedBytes, android.util.Base64.NO_WRAP)
                    } else null
                }

                val frontExt = card.frontPhotoPath?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                val backExt = card.backPhotoPath?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }

                LoyaltyCardBackup(
                    name = decrypted.name,
                    rawValue = decrypted.rawValue,
                    barcodeFormat = decrypted.barcodeFormat,
                    hexColor = decrypted.hexColor,
                    category = decrypted.category,
                    lastUsedTimestamp = decrypted.lastUsedTimestamp,
                    frontPhotoBase64 = frontBase64,
                    backPhotoBase64 = backBase64,
                    frontPhotoExtension = frontExt,
                    backPhotoExtension = backExt,
                    barcodeFormatInt = decrypted.barcodeFormatInt
                )
            }
            
            val jsonString = jsonConfig.encodeToString(backupList)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            } ?: throw java.io.IOException("Unable to open output stream for URI: $uri")
        }
    }

    override suspend fun importBackup(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonString = reader.readText()
                    val backupList = jsonConfig.decodeFromString<List<LoyaltyCardBackup>>(jsonString)
                    
                    val docDir = java.io.File(context.filesDir, "documents").apply {
                        if (!exists()) mkdirs()
                    }
                    
                    val cardsToInsert = backupList.mapIndexed { index, backup ->
                        val timestamp = System.currentTimeMillis()
                        
                        val frontPath = backup.frontPhotoBase64?.let { base64 ->
                            val decryptedBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                            val encryptedBytes = CryptographyManager.encryptData(decryptedBytes)
                            val ext = backup.frontPhotoExtension ?: "jpg"
                            val file = java.io.File(docDir, "doc_${timestamp}_${index}_front.$ext")
                            file.writeBytes(encryptedBytes)
                            file.absolutePath
                        }
                        
                        val backPath = backup.backPhotoBase64?.let { base64 ->
                            val decryptedBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                            val encryptedBytes = CryptographyManager.encryptData(decryptedBytes)
                            val ext = backup.backPhotoExtension ?: "jpg"
                            val file = java.io.File(docDir, "doc_${timestamp}_${index}_back.$ext")
                            file.writeBytes(encryptedBytes)
                            file.absolutePath
                        }
                        
                        val plainCard = LoyaltyCard(
                            name = backup.name,
                            rawValue = backup.rawValue,
                            barcodeFormat = backup.barcodeFormat,
                            hexColor = backup.hexColor,
                            category = backup.category,
                            lastUsedTimestamp = backup.lastUsedTimestamp,
                            frontPhotoPath = frontPath,
                            backPhotoPath = backPath,
                            barcodeFormatInt = backup.barcodeFormatInt
                        )
                        
                        encryptCard(plainCard)
                    }
                    
                    cardDao.insertCards(cardsToInsert)
                }
            } ?: throw java.io.IOException("Unable to open input stream for URI: $uri")
        }
    }
}

@Serializable
private data class LoyaltyCardBackup(
    val name: String,
    val rawValue: String,
    val barcodeFormat: String,
    val hexColor: String,
    val category: String,
    val lastUsedTimestamp: Long,
    val frontPhotoBase64: String? = null,
    val backPhotoBase64: String? = null,
    val frontPhotoExtension: String? = null,
    val backPhotoExtension: String? = null,
    val barcodeFormatInt: Int = -1
)
