package com.callbook.journal.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

private val shortDayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormat)

fun formatShortDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDayFormat)

fun epochDayToMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun millisToEpochDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
