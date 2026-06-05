package com.atomik.quickbeep.domain.repository

import android.content.Context
import android.net.Uri
import com.atomik.quickbeep.data.model.LoyaltyCard
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun getAllCards(): Flow<List<LoyaltyCard>>
    fun searchCards(query: String): Flow<List<LoyaltyCard>>
    fun getCardsByCategory(category: String): Flow<List<LoyaltyCard>>
    
    suspend fun insertCard(card: LoyaltyCard): Long
    suspend fun updateCard(card: LoyaltyCard)
    suspend fun deleteCard(card: LoyaltyCard)
    
    suspend fun exportBackup(context: Context, uri: Uri): Result<Unit>
    suspend fun importBackup(context: Context, uri: Uri): Result<Unit>
}
