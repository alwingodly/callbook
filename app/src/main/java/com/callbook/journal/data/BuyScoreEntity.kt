package com.callbook.journal.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.callbook.journal.domain.BuyOutcome
import com.callbook.journal.domain.BuyScore

@Entity(
    tableName = "buy_scores",
    indices = [Index(value = ["symbol", "epochDay"], unique = true)],
)
data class BuyScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val symbol: String,
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val outcome: String? = null,
)

fun BuyScoreEntity.toScore(): BuyScore = BuyScore(
    id = id,
    epochDay = epochDay,
    symbol = symbol,
    entryPrice = entryPrice,
    stopPrice = stopPrice,
    targetPrice = targetPrice,
    outcome = outcome?.let { runCatching { BuyOutcome.valueOf(it) }.getOrNull() },
)
