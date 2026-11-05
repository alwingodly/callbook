package com.callbook.journal.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "predictions",
    indices = [Index("epochDay")],
)
data class PredictionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val symbol: String,
    val direction: String,
    val segment: String,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopPrice: Double,
    val capital: Double,
    val actualPrice: Double?,
    val note: String,
    val mark: String?,
    val createdAt: Long,
    val manualPnl: Double? = null,
    val notStarted: Boolean = false,
)
