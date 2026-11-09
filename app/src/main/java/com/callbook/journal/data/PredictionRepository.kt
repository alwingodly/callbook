package com.callbook.journal.data

import com.callbook.journal.domain.BuyOutcome
import com.callbook.journal.domain.BuyScore
import com.callbook.journal.domain.Prediction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PredictionRepository(
    private val dao: PredictionDao,
    private val scores: BuyScoreDao,
) {
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

    fun observeScores(): Flow<List<BuyScore>> = scores.observeAll().map { rows -> rows.map { it.toScore() } }

    /** The first buy of the day is kept. A later refresh does not move the stop or the target. */
    suspend fun rememberBuy(symbol: String, day: Long, entry: Double, stop: Double, target: Double) {
        val name = symbol.trim().uppercase()
        if (name.isEmpty() || entry <= 0.0 || stop <= 0.0 || target <= 0.0) return
        scores.insert(
            BuyScoreEntity(
                epochDay = day,
                symbol = name,
                entryPrice = entry,
                stopPrice = stop,
                targetPrice = target,
            ),
        )
    }

    suspend fun unscoredBuys(): List<BuyScore> = scores.unscored().map { it.toScore() }

    suspend fun setOutcome(id: Long, outcome: BuyOutcome) {
        scores.setOutcome(id, outcome.name)
    }
}
