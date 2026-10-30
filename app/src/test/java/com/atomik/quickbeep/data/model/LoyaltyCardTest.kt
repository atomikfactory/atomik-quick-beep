package com.atomik.quickbeep.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoyaltyCardTest {

    private val sample = LoyaltyCard(
        id = 7,
        name = "Corner Cafe",
        rawValue = "1234567890128",
        barcodeFormat = "EAN_13",
        hexColor = "#FF5722",
        category = "Food",
        lastUsedTimestamp = 1_700_000_000_000L,
        frontPhotoPath = "front.enc",
        backPhotoPath = null,
        barcodeFormatInt = 32
    )

    @Test
    fun defaults_areApplied() {
        val card = LoyaltyCard(
            name = "Shop",
            rawValue = "abc",
            barcodeFormat = "QR_CODE",
            hexColor = "#000000",
            category = "Misc",
            lastUsedTimestamp = 0L
        )
        assertEquals(0, card.id)
        assertNull(card.frontPhotoPath)
        assertNull(card.backPhotoPath)
        assertEquals(-1, card.barcodeFormatInt)
    }

    @Test
    fun json_roundTrip_preservesAllFields() {
        val json = Json.encodeToString(sample)
        assertEquals(sample, Json.decodeFromString<LoyaltyCard>(json))
    }

    @Test
    fun json_decode_usesDefaultsForMissingOptionalFields() {
        val json = """{"name":"A","rawValue":"1","barcodeFormat":"CODE_128",""" +
            """"hexColor":"#112233","category":"Gym","lastUsedTimestamp":5}"""
        val card = Json.decodeFromString<LoyaltyCard>(json)
        assertEquals(0, card.id)
        assertEquals(-1, card.barcodeFormatInt)
        assertNull(card.frontPhotoPath)
    }

    @Test
    fun copy_keepsOtherFields() {
        val updated = sample.copy(lastUsedTimestamp = 42L)
        assertEquals(42L, updated.lastUsedTimestamp)
        assertEquals(sample.name, updated.name)
        assertEquals(sample.rawValue, updated.rawValue)
    }
}
