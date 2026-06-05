package com.atomik.quickbeep.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.atomik.quickbeep.data.model.LoyaltyCard
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM loyalty_cards ORDER BY lastUsedTimestamp DESC")
    fun getAllCardsFlow(): Flow<List<LoyaltyCard>>

    @Query("SELECT * FROM loyalty_cards WHERE name LIKE :searchQuery OR rawValue LIKE :searchQuery ORDER BY lastUsedTimestamp DESC")
    fun searchCardsFlow(searchQuery: String): Flow<List<LoyaltyCard>>

    @Query("SELECT * FROM loyalty_cards WHERE category = :category ORDER BY lastUsedTimestamp DESC")
    fun getCardsByCategoryFlow(category: String): Flow<List<LoyaltyCard>>

    @Query("SELECT * FROM loyalty_cards")
    suspend fun getAllCardsDirect(): List<LoyaltyCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: LoyaltyCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<LoyaltyCard>)

    @Update
    suspend fun updateCard(card: LoyaltyCard)

    @Delete
    suspend fun deleteCard(card: LoyaltyCard)
}
