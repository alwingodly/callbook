package com.callbook.journal.domain

data class Judgement(
    val kind: ResultKind,
    val hitTarget: Boolean,
    val hitStop: Boolean,
    val beyond: Boolean,
    val actualMove: Direction?,
)

private const val FLAT = 0.009

fun Prediction.judgement(): Judgement {
    val chosen = mark
    if (chosen != null) return fromMark(chosen)
    val price = actualPrice
    if (price != null) return fromPrice(price)
    return fromMark(null)
}

private fun Prediction.fromPrice(price: Double): Judgement {
    val gainCall = direction == Direction.GAIN
    val beyond = if (gainCall) price > targetPrice + FLAT else price < targetPrice - FLAT
    val hitTarget = if (gainCall) price + FLAT >= targetPrice else price - FLAT <= targetPrice
    val hitStop = if (gainCall) price - FLAT <= stopPrice else price + FLAT >= stopPrice
    val move = when {
        price > entryPrice + FLAT -> Direction.GAIN
        price < entryPrice - FLAT -> Direction.LOSS
        else -> null
    }
    val kind = when (move) {
        null -> ResultKind.SCRATCH
        direction -> ResultKind.SUCCESS
        else -> ResultKind.FAILED
    }
    return Judgement(
        kind = kind,
        hitTarget = hitTarget && kind != ResultKind.SCRATCH,
        hitStop = hitStop && kind != ResultKind.SCRATCH,
        beyond = beyond && kind == ResultKind.SUCCESS,
        actualMove = move,
    )
}

private fun fromMark(mark: Mark?): Judgement = when (mark) {
    null -> Judgement(ResultKind.OPEN, hitTarget = false, hitStop = false, beyond = false, actualMove = null)
    Mark.CORRECT -> Judgement(ResultKind.SUCCESS, hitTarget = false, hitStop = false, beyond = false, actualMove = null)
    Mark.OPPOSITE -> Judgement(ResultKind.FAILED, hitTarget = false, hitStop = false, beyond = false, actualMove = null)
    Mark.HIT_TARGET -> Judgement(ResultKind.SUCCESS, hitTarget = true, hitStop = false, beyond = false, actualMove = null)
    Mark.HIT_STOP -> Judgement(ResultKind.FAILED, hitTarget = false, hitStop = true, beyond = false, actualMove = null)
    Mark.BEYOND -> Judgement(ResultKind.SUCCESS, hitTarget = true, hitStop = false, beyond = true, actualMove = null)
    Mark.WITHDRAW_PROFIT -> Judgement(ResultKind.SUCCESS, hitTarget = true, hitStop = false, beyond = false, actualMove = null)
    Mark.WITHDRAW_LOSS -> Judgement(ResultKind.FAILED, hitTarget = false, hitStop = true, beyond = false, actualMove = null)
}

fun Prediction.verdictSentence(): String? {
    val judged = judgement()
    if (judged.kind == ResultKind.OPEN) return null
    val called = if (direction == Direction.GAIN) "a gain" else "a loss"
    if (mark != null) {
        val back = returnedCash()
        return when (mark) {
            Mark.WITHDRAW_PROFIT, Mark.CORRECT, Mark.HIT_TARGET, Mark.BEYOND ->
                "Withdrew with profit. ${rupees(back ?: capital)} is back in your account."
            Mark.WITHDRAW_LOSS, Mark.OPPOSITE, Mark.HIT_STOP ->
                "Withdrew with loss. ${rupees(back ?: capital)} is back in your account."
            null -> null
        }
    }
    return when (judged.kind) {
        ResultKind.SCRATCH -> "You called $called. The price is flat against your entry."
        ResultKind.SUCCESS -> when {
            judged.beyond && direction == Direction.GAIN -> "You called a gain, and it went beyond your target."
            judged.beyond && direction == Direction.LOSS -> "You called a loss, and it fell beyond your target."
            judged.hitTarget -> "You called $called, and the price reached your target."
            direction == Direction.GAIN -> "You called a gain, and it gained."
            else -> "You called a loss, and it fell."
        }
        ResultKind.FAILED -> when {
            judged.hitStop && direction == Direction.GAIN -> "You called a gain, and the price hit your stop."
            judged.hitStop && direction == Direction.LOSS -> "You called a loss, and the price hit your stop."
            direction == Direction.GAIN -> "You called a gain, but it went to a loss."
            else -> "You called a loss, but it went to a profit."
        }
        ResultKind.OPEN -> null
    }
}
