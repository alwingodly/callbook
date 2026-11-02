package com.callbook.journal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.Direction
import com.callbook.journal.domain.GrowwMath
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.Segment
import com.callbook.journal.domain.TradeSide
import com.callbook.journal.domain.followSide
import com.callbook.journal.domain.parseAmount
import com.callbook.journal.domain.planWarning
import com.callbook.journal.domain.priceText
import com.callbook.journal.domain.rupees
import com.callbook.journal.ui.CallDraft
import com.callbook.journal.ui.DraftResult
import com.callbook.journal.ui.components.ChoiceRow
import com.callbook.journal.ui.epochDayToMillis
import com.callbook.journal.ui.formatDay
import com.callbook.journal.ui.millisToEpochDay
import com.callbook.journal.ui.theme.Amber
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Canvas
import com.callbook.journal.ui.theme.Faint
import com.callbook.journal.ui.theme.Fraunces
import com.callbook.journal.ui.theme.Ink
import com.callbook.journal.ui.theme.InkCard
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.pnlColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCallScreen(
    existing: Prediction?,
    loading: Boolean,
    missing: Boolean,
    initialDay: Long,
    available: Double,
    onAddFromBank: (Double) -> Unit,
    onBack: () -> Unit,
    onSave: (Prediction) -> Unit,
    holding: (String) -> Boolean = { false },
    prefill: CallDraft? = null,
) {
    var draft by remember {
        mutableStateOf(
            when {
                prefill != null -> prefill
                existing != null -> CallDraft.from(existing)
                !loading && !missing -> CallDraft.empty(initialDay)
                else -> null
            },
        )
    }
    LaunchedEffect(existing?.id) {
        if (draft == null && existing != null) draft = CallDraft.from(existing)
    }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = draft

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New stock" else "Edit stock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Canvas,
                    titleContentColor = Paper,
                    navigationIconContentColor = Paper,
                ),
            )
        },
        bottomBar = {
            if (current != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlanPreview(current)
                    error?.let { Text(it, color = Loss, fontSize = 13.sp) }
                    Button(
                        onClick = {
                            when (val result = current.toPrediction(existing)) {
                                is DraftResult.Invalid -> error = result.message
                                is DraftResult.Valid -> if (existing == null && holding(result.prediction.symbol)) {
                                    error = "${result.prediction.symbol} is already an open swing."
                                } else {
                                    onSave(result.prediction)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Canvas),
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { padding ->
        when {
            missing -> Text(
                "This call is no longer in the journal.",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = Mist,
            )
            current == null -> Text(
                "Opening this call...",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = Mist,
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DateField(
                    value = formatDay(current.epochDay),
                    onClick = { showDate = true },
                )
                Field(
                    label = "Stock",
                    value = current.symbol,
                    onValue = { draft = current.copy(symbol = it.uppercase()) },
                    placeholder = "RELIANCE",
                    keyboard = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                )
                ChoiceRow(
                    options = listOf("Gain", "Loss"),
                    selectedIndex = if (current.direction == Direction.GAIN) 0 else 1,
                    onSelect = { index ->
                        draft = current.copy(direction = if (index == 0) Direction.GAIN else Direction.LOSS)
                    },
                )
                ChoiceRow(
                    options = listOf("Delivery", "Intraday"),
                    selectedIndex = if (current.segment == Segment.DELIVERY) 0 else 1,
                    onSelect = { index ->
                        draft = current.copy(segment = if (index == 0) Segment.DELIVERY else Segment.INTRADAY)
                    },
                )
                Field(
                    label = "Price on Groww now",
                    value = current.entry,
                    onValue = { draft = current.copy(entry = it) },
                    placeholder = "2456.50",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                Field(
                    label = "Target",
                    value = current.target,
                    onValue = { draft = current.copy(target = it) },
                    placeholder = "2510",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                Field(
                    label = "Stop loss",
                    value = current.stop,
                    onValue = { draft = current.copy(stop = it) },
                    placeholder = "2420",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                Field(
                    label = "Amount",
                    value = current.capital,
                    onValue = { draft = current.copy(capital = it) },
                    placeholder = "10000",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                CapitalNote(
                    available = available,
                    wanted = parseAmount(current.capital),
                    onAddFromBank = onAddFromBank,
                )
                Field(
                    label = "Note",
                    value = current.note,
                    onValue = { draft = current.copy(note = it) },
                    placeholder = "Why this call",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    singleLine = false,
                )
            }
        }
    }

    if (showDate && current != null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDayToMillis(current.epochDay))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        draft = current.copy(epochDay = millisToEpochDay(millis))
                    }
                    showDate = false
                }) { Text("Set day", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancel", color = Mist) }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateField(value: String, onClick: () -> Unit) {
    val colors = fieldColors()
    Box(contentAlignment = Alignment.CenterStart) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Date") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = colors,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardOptions,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboard,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ink,
    unfocusedBorderColor = Line,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Mist,
    cursorColor = Ink,
    focusedTextColor = Paper,
    unfocusedTextColor = Paper,
    focusedContainerColor = InkCard,
    unfocusedContainerColor = InkCard,
    focusedPlaceholderColor = Faint,
    unfocusedPlaceholderColor = Faint,
)

@Composable
private fun CapitalNote(
    available: Double,
    wanted: Double?,
    onAddFromBank: (Double) -> Unit,
) {
    val short = if (wanted == null) 0.0 else wanted - available
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Cash left  ${rupees(available, signed = available < -0.005)}",
            color = if (available < -0.005) Loss else Mist,
            fontSize = 13.sp,
        )
        if (short > 0.5) {
            Text("Short ${rupees(short)} for this stock.", color = Amber, fontSize = 13.sp)
            TextButton(onClick = { onAddFromBank(short) }) {
                Text("Add ${rupees(short)} from bank", color = Accent)
            }
        }
    }
}

@Composable
private fun PlanPreview(draft: CallDraft) {
    val entry = parseAmount(draft.entry)
    val target = parseAmount(draft.target)
    val stop = parseAmount(draft.stop)
    val capital = parseAmount(draft.capital)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (entry == null || target == null || stop == null || capital == null) {
            return
        }
        val ghost = Prediction(
            epochDay = draft.epochDay,
            symbol = draft.symbol.ifBlank { "STOCK" },
            direction = draft.direction,
            segment = draft.segment,
            entryPrice = entry,
            targetPrice = target,
            stopPrice = stop,
            capital = capital,
            actualPrice = null,
            note = "",
            mark = null,
            createdAt = 0L,
        )
        val shares = GrowwMath.quantity(capital, entry)
        val buyTarget = GrowwMath.atPrice(ghost, target, TradeSide.LONG)
        val buyStop = GrowwMath.atPrice(ghost, stop, TradeSide.LONG)
        if (shares == 0) {
            Text("That amount does not cover one share at ${priceText(entry)}.", color = Amber, fontSize = 13.sp)
        } else {
            Text(
                "$shares shares  ·  ${rupees(buyTarget.deployed)} used  ·  ${rupees(buyTarget.idleCash)} left",
                color = Mist,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PreviewFigure("Target", buyTarget.netPnl)
                PreviewFigure("Stop", buyStop.netPnl)
            }
            Text(
                "Broker ${rupees(buyTarget.brokerCost)}",
                color = Faint,
                fontSize = 12.sp,
            )
        }
        planWarning(draft.direction, entry, target, stop)?.let { warning ->
            Text(warning, color = Amber, fontSize = 12.sp)
        }
        when (ghost.followSide()) {
            TradeSide.STAY_OUT -> Text("Stay out on delivery.", color = Faint, fontSize = 12.sp)
            TradeSide.SHORT -> {
                val shortTarget = GrowwMath.atPrice(ghost, target, TradeSide.SHORT)
                Text(
                    "Short ${rupees(shortTarget.netPnl, signed = true)}",
                    color = pnlColor(shortTarget.netPnl),
                    fontSize = 12.sp,
                )
            }
            TradeSide.LONG -> Unit
        }
    }
}

@Composable
private fun PreviewFigure(label: String, net: Double) {
    Column {
        Text(label, color = Faint, fontSize = 11.sp)
        Text(rupees(net, signed = true), color = pnlColor(net), fontFamily = Fraunces, fontSize = 18.sp)
    }
}
