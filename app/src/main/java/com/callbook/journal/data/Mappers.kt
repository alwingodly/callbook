package com.callbook.journal.data

import com.callbook.journal.domain.Direction
import com.callbook.journal.domain.Mark
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.Segment

fun PredictionEntity.toModel(): Prediction = Prediction(
    id = id,
    epochDay = epochDay,
    symbol = symbol,
    direction = Direction.valueOf(direction),
    segment = Segment.valueOf(segment),
    entryPrice = entryPrice,
    targetPrice = targetPrice,
    stopPrice = stopPrice,
    capital = capital,
    actualPrice = actualPrice,
    note = note,
    mark = mark?.let(Mark::valueOf),
    createdAt = createdAt,
)

fun Prediction.toEntity(): PredictionEntity = PredictionEntity(
    id = id,
    epochDay = epochDay,
    symbol = symbol,
    direction = direction.name,
    segment = segment.name,
    entryPrice = entryPrice,
    targetPrice = targetPrice,
    stopPrice = stopPrice,
    capital = capital,
    actualPrice = actualPrice,
    note = note,
    mark = mark?.name,
    createdAt = createdAt,
)
