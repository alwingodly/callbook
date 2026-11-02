package com.callbook.journal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM predictions WHERE id = :id")
    suspend fun get(id: Long): PredictionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PredictionEntity): Long

    @Update
    suspend fun update(entity: PredictionEntity)

    @Query("DELETE FROM predictions WHERE id = :id")
    suspend fun delete(id: Long)
}
