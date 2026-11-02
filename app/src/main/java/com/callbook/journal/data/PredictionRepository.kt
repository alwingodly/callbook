package com.callbook.journal.data

import com.callbook.journal.domain.Prediction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PredictionRepository(private val dao: PredictionDao) {
    fun observeAll(): Flow<List<Prediction>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: Long): Prediction? = dao.get(id)?.toModel()

    suspend fun listAll(): List<Prediction> = dao.listAll().map { it.toModel() }

    suspend fun save(prediction: Prediction): Long {
        val entity = prediction.toEntity()
        return if (prediction.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            prediction.id
        }
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
