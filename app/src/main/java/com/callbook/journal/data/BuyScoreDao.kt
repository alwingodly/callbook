package com.callbook.journal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuyScoreDao {
    @Query("SELECT * FROM buy_scores ORDER BY epochDay DESC, symbol ASC")
    fun observeAll(): Flow<List<BuyScoreEntity>>

    @Query("SELECT * FROM buy_scores WHERE outcome IS NULL")
    suspend fun unscored(): List<BuyScoreEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BuyScoreEntity): Long

    @Query("UPDATE buy_scores SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: String)
}
