package com.example.foodledger.data

data class LedgerEntry(
    val id: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val transactionDate: Long = createdAt,
    val meal: String,
    val amount: Double,
    val category: String,
    val primaryCategory: String = category,
    val secondaryCategory: String = category,
    val transactionType: String = "EXPENSE",
    val note: String,
    val imageUris: List<String> = emptyList()
)
