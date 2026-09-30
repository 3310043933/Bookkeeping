package com.example.foodledger.data

data class LedgerEntry(
    val id: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val meal: String,
    val amount: Double,
    val category: String,
    val note: String,
    val imageUris: List<String> = emptyList()
)
