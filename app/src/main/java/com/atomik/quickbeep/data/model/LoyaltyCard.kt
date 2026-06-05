package com.atomik.quickbeep.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "loyalty_cards")
data class LoyaltyCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val rawValue: String,
    val barcodeFormat: String,
    val hexColor: String,
    val category: String,
    val lastUsedTimestamp: Long,
    val frontPhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val barcodeFormatInt: Int = -1
)
