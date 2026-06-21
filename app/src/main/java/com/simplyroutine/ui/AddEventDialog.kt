package com.simplyroutine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import com.simplyroutine.data.ALERT_OPTIONS_POSITIVE
import com.simplyroutine.data.Event
import com.simplyroutine.data.RepeatType
import com.simplyroutine.data.alertLabel
import com.simplyroutine.data.expandEventsForDate
import com.simplyroutine.data.parseAlertMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class ConflictEntry(
    val occurrence: Event,
    val parent: Event,
    val trimmed: Event?,
)

private val EVENT_COLORS = listOf(
    0xFF4CAF50.toInt(),
    0xFF2196F3.toInt(),
    0xFFF44336.toInt(),
    0xFFFF9800.toInt(),
    0xFF9C27B0.toInt(),
    0xFF00BCD4.toInt(),
)

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    event: Event? = null,
    existingEvents: List<Event> = emptyList(),
    defaultDate: LocalDate = LocalDate.now(),
    defaultStartMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    defaultAlertMinutes: Int = -1,
    is24Hour: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onResolveConflicts: (toUpdate: List<Event>, toDelete: List<Event>, toAdd: List<Event>) -> Unit = { _, _, _ -> },
    onDelete: ((Event) -> Unit)? = null,
    onRejoinSeries: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(event?.title ?: "") }
    var date by remember { mutableStateOf(event?.localDate ?: defaultDate) }
    var startMin by remember { mutableStateOf(event?.startMinutes ?: defaultStartMinutes) }
    var endMin by remember { mutableStateOf(event?.endMinutes ?: (defaultStartMinutes + 60)) }
    var color by remember { mutableStateOf(event?.color ?: EVENT_COLORS[0]) }
    var repeatType by remember { mutableStateOf(event?.recurrence ?: RepeatType.NONE) }
    var repeatInterval by remember { mutableStateOf(event?.repeatInterval ?: 1) }
    var repeatDays by remember { mutableStateOf(event?.repeatDays ?: 0) }
    var alerts by remember {
        mutableStateOf(
            parseAlertMinutes(event?.alertMinutes ?: "").ifEmpty {
                if (defaultAlertMinutes >= 0) listOf(defaultAlertMinutes) else emptyList()
            }
        )
    }
    var expandedAlertIndex by remember { mutableStateOf<Int?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customDialogIndex by remember { mutableStateOf(0) }
    var customInputText by remember { mutableStateOf("") }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRepeatDropdown by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false; customInputText = "" },
            title = { Text("Custom alert") },
            text = {
                OutlinedTextField(
                    value = customInputText,
                    onValueChange = { customInputText = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Minutes before event") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val mins = customInputText.toIntOrNull()
                    if (mins != null && mins >= 0) {
                        alerts = alerts.toMutableList().also { it[customDialogIndex] = mins }
                    }
                    showCustomDialog = false
                    customInputText = ""
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false; customInputText = "" }) { Text("Cancel") }
            },
        )
    }

    // Pending conflict resolution
    var pendingEvent by remember { mutableStateOf<Event?>(null) }
    var pendingConflicts by remember { mutableStateOf<List<ConflictEntry>>(emptyList()) }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinutes = startMin,
            is24Hour = is24Hour,
            onDismiss = { showStartPicker = false },
            onConfirm = { m ->
                startMin = m
                if (endMin <= startMin) endMin = startMin + 60
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinutes = endMin,
            is24Hour = is24Hour,
            onDismiss = { showEndPicker = false },
            onConfirm = { m -> endMin = m; showEndPicker = false },
        )
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pendingEvent != null) {
        val newEv = pendingEvent!!
        val isSingle = pendingConflicts.size == 1
        val singleEntry = if (isSingle) pendingConflicts[0] else null
        val occ = singleEntry?.occurrence

        val canSplit     = occ != null && occ.startMinutes < newEv.startMinutes && occ.endMinutes > newEv.endMinutes
        val canTrimEnd   = occ != null && occ.startMinutes < newEv.startMinutes
        val canTrimStart = occ != null && occ.endMinutes   > newEv.endMinutes
        val canDelete    = occ != null && occ.startMinutes >= newEv.startMinutes && occ.endMinutes <= newEv.endMinutes

        fun dismiss() { pendingEvent = null; pendingConflicts = emptyList() }

        // Resolves a single conflict entry into (toUpdate, toDelete, toAdd) lists.
        // For occurrences of a recurring event that aren't on the base date, we add an
        // exception to the parent and create standalone events for the trimmed/split pieces.
        fun resolveEntry(
            entry: ConflictEntry,
            splitLeft: Event? = null,
            splitRight: Event? = null,
            trimPiece: Event? = null,
            remove: Boolean = false,
        ): Triple<List<Event>, List<Event>, List<Event>> {
            val isRecurringOccurrence = entry.parent.localDate != entry.occurrence.localDate
            return if (isRecurringOccurrence) {
                val parentWithException = entry.parent.withException(entry.occurrence.localDate)
                val toAdd = buildList {
                    fun standaloneFrom(e: Event) = e.copy(
                        id = 0, repeatType = RepeatType.NONE.name,
                        repeatInterval = 1, repeatDays = 0, exceptDates = "", parentId = 0,
                    )
                    if (splitLeft != null)  add(standaloneFrom(splitLeft))
                    if (splitRight != null) add(standaloneFrom(splitRight))
                    if (trimPiece != null)  add(standaloneFrom(trimPiece))
                }
                Triple(listOf(parentWithException), emptyList(), toAdd)
            } else {
                val occ = entry.occurrence
                val toUpdate = buildList<Event> {
                    if (splitLeft  != null) add(splitLeft)
                    if (trimPiece  != null) add(trimPiece)
                }
                val toDelete  = if (remove || (splitLeft == null && trimPiece == null && splitRight == null)) listOf(occ) else emptyList()
                val toAdd     = if (splitRight != null) listOf(splitRight) else emptyList()
                Triple(toUpdate, toDelete, toAdd)
            }
        }

        Dialog(onDismissRequest = ::dismiss) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Overlapping events", style = MaterialTheme.typography.titleLarge)

                    // Conflict list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pendingConflicts.forEach { entry ->
                            val o = entry.occurrence
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(o.color), CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${o.title}  ${formatMin(o.startMinutes)} – ${formatMin(o.endMinutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Text("What would you like to do?", style = MaterialTheme.typography.bodyMedium)

                    // Action buttons — shown when applicable
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canSplit) {
                            OutlinedButton(
                                onClick = {
                                    val left  = occ!!.copy(endMinutes   = newEv.startMinutes)
                                    val right = occ.copy(startMinutes = newEv.endMinutes)
                                    val (toUpdate, toDelete, toAdd) = resolveEntry(singleEntry!!, splitLeft = left, splitRight = right)
                                    onSave(newEv)
                                    onResolveConflicts(toUpdate, toDelete, toAdd)
                                    dismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Split \"${occ!!.title}\" around this") }
                        }
                        if (canTrimEnd) {
                            OutlinedButton(
                                onClick = {
                                    val trimmed = occ!!.copy(endMinutes = newEv.startMinutes)
                                    val (toUpdate, toDelete, toAdd) = resolveEntry(singleEntry!!, trimPiece = trimmed)
                                    onSave(newEv)
                                    onResolveConflicts(toUpdate, toDelete, toAdd)
                                    dismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Keep \"${occ!!.title}\" before this") }
                        }
                        if (canTrimStart) {
                            OutlinedButton(
                                onClick = {
                                    val trimmed = occ!!.copy(startMinutes = newEv.endMinutes)
                                    val (toUpdate, toDelete, toAdd) = resolveEntry(singleEntry!!, trimPiece = trimmed)
                                    onSave(newEv)
                                    onResolveConflicts(toUpdate, toDelete, toAdd)
                                    dismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Keep \"${occ!!.title}\" after this") }
                        }
                        if (canDelete) {
                            OutlinedButton(
                                onClick = {
                                    val (toUpdate, toDelete, toAdd) = resolveEntry(singleEntry!!, remove = true)
                                    onSave(newEv)
                                    onResolveConflicts(toUpdate, toDelete, toAdd)
                                    dismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Remove \"${occ!!.title}\"") }
                        }
                        if (!isSingle) {
                            OutlinedButton(
                                onClick = {
                                    val toUpdate = mutableListOf<Event>()
                                    val toDelete = mutableListOf<Event>()
                                    val toAdd    = mutableListOf<Event>()
                                    pendingConflicts.forEach { entry ->
                                        val (u, d, a) = resolveEntry(entry, trimPiece = entry.trimmed, remove = entry.trimmed == null)
                                        toUpdate += u; toDelete += d; toAdd += a
                                    }
                                    onSave(newEv)
                                    onResolveConflicts(toUpdate, toDelete, toAdd)
                                    dismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Adjust all to make room") }
                        }
                        TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        val focusManager = LocalFocusManager.current

        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = color,
                onDismiss = { showColorPicker = false },
                onConfirm = { color = it; showColorPicker = false },
            )
        }

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (event == null) "New Event" else "Edit Event",
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedButton(
                    onClick = { focusManager.clearFocus(); showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeButton("Start", startMin, is24Hour, Modifier.weight(1f)) { focusManager.clearFocus(); showStartPicker = true }
                    TimeButton("End", endMin, is24Hour, Modifier.weight(1f)) { focusManager.clearFocus(); showEndPicker = true }
                }

                // ── Repeat / Rejoin ─────────────────────────────────────
                if ((event?.parentId ?: 0) != 0) {
                    if (onRejoinSeries != null) {
                        OutlinedButton(
                            onClick = { focusManager.clearFocus(); onRejoinSeries() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Rejoin recurring series") }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = showRepeatDropdown,
                        onExpandedChange = { if (it) focusManager.clearFocus(); showRepeatDropdown = it },
                    ) {
                        OutlinedTextField(
                            value = repeatLabel(repeatType, repeatInterval),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Repeat") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRepeatDropdown)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = showRepeatDropdown,
                            onDismissRequest = { showRepeatDropdown = false },
                        ) {
                            RepeatType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(staticRepeatLabel(type)) },
                                    onClick = {
                                        if (type == RepeatType.EVERY_N_WEEKS && repeatDays == 0) {
                                            repeatDays = 1 shl (date.dayOfWeek.value - 1)
                                        }
                                        repeatType = type
                                        showRepeatDropdown = false
                                    },
                                )
                            }
                        }
                    }

                    // Interval stepper — shown for EVERY_N_DAYS and EVERY_N_WEEKS
                    if (repeatType == RepeatType.EVERY_N_DAYS || repeatType == RepeatType.EVERY_N_WEEKS) {
                        val unitWord = if (repeatType == RepeatType.EVERY_N_DAYS)
                            if (repeatInterval == 1) "day" else "days"
                        else
                            if (repeatInterval == 1) "week" else "weeks"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Every", style = MaterialTheme.typography.bodyMedium)
                            FilledTonalIconButton(
                                onClick = { focusManager.clearFocus(); if (repeatInterval > 1) repeatInterval-- },
                                modifier = Modifier.size(36.dp),
                            ) { Text("−") }
                            Text(
                                "$repeatInterval",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.widthIn(min = 24.dp),
                            )
                            FilledTonalIconButton(
                                onClick = { focusManager.clearFocus(); if (repeatInterval < 30) repeatInterval++ },
                                modifier = Modifier.size(36.dp),
                            ) { Text("+") }
                            Text(unitWord, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Day-of-week chips — shown for EVERY_N_WEEKS
                    if (repeatType == RepeatType.EVERY_N_WEEKS) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("On", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                DAY_LABELS.forEachIndexed { i, label ->
                                    val bit = 1 shl i
                                    val selected = repeatDays and bit != 0
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                                CircleShape,
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                                CircleShape,
                                            )
                                            .clickable {
                                                focusManager.clearFocus()
                                                val next = if (selected) repeatDays and bit.inv()
                                                           else repeatDays or bit
                                                if (next != 0) repeatDays = next
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // ── End repeat ──────────────────────────────────────────

                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EVENT_COLORS.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(if (selected) 36.dp else 28.dp)
                                .background(Color(c), CircleShape)
                                .then(
                                    if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { focusManager.clearFocus(); color = c },
                        )
                    }

                    // Custom colour circle — rainbow when no custom colour picked, filled when one is active
                    val isCustomColor = color !in EVENT_COLORS
                    val rainbowBrush = remember {
                        Brush.sweepGradient(listOf(
                            Color(0xFFF44336), Color(0xFFFF9800), Color(0xFFFFEB3B),
                            Color(0xFF4CAF50), Color(0xFF00BCD4), Color(0xFF2196F3),
                            Color(0xFF9C27B0), Color(0xFFF44336),
                        ))
                    }
                    val customModifier = if (isCustomColor) {
                        Modifier
                            .size(36.dp)
                            .background(Color(color), CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                    } else {
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(rainbowBrush)
                    }
                    Box(
                        modifier = customModifier.clickable { focusManager.clearFocus(); showColorPicker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isCustomColor) {
                            Text("+", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // ── Alerts ──────────────────────────────────────────────
                if (alerts.isNotEmpty()) {
                    Text("Alerts", style = MaterialTheme.typography.labelMedium)
                }
                alerts.forEachIndexed { i, mins ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedAlertIndex == i,
                            onExpandedChange = { open ->
                                if (open) focusManager.clearFocus()
                                expandedAlertIndex = if (open) i else null
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = alertLabel(mins),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAlertIndex == i) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAlertIndex == i,
                                onDismissRequest = { expandedAlertIndex = null },
                            ) {
                                ALERT_OPTIONS_POSITIVE.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(alertLabel(option)) },
                                        onClick = {
                                            alerts = alerts.toMutableList().also { it[i] = option }
                                            expandedAlertIndex = null
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Custom…") },
                                    onClick = {
                                        customDialogIndex = i
                                        customInputText = if (mins !in ALERT_OPTIONS_POSITIVE) mins.toString() else ""
                                        showCustomDialog = true
                                        expandedAlertIndex = null
                                    },
                                )
                            }
                        }
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            alerts = alerts.toMutableList().also { it.removeAt(i) }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove alert")
                        }
                    }
                }
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        val next = ALERT_OPTIONS_POSITIVE.firstOrNull { it !in alerts } ?: 15
                        alerts = alerts + next
                    },
                    modifier = Modifier.align(Alignment.Start),
                ) { Text("+ Add alert") }
                // ── End alerts ──────────────────────────────────────────

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (event != null && onDelete != null) {
                        TextButton(onClick = { onDelete(event) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newEvent = Event(
                                id = event?.id ?: 0,
                                title = title.trim(),
                                date = date.toEpochDay(),
                                startMinutes = startMin,
                                endMinutes = endMin,
                                color = color,
                                repeatType = repeatType.name,
                                repeatInterval = repeatInterval,
                                repeatDays = repeatDays,
                                exceptDates = event?.exceptDates ?: "",
                                parentId = event?.parentId ?: 0,
                                alertMinutes = alerts.distinct().sorted().joinToString(","),
                            )
                            val candidates = existingEvents.filter { it.id != newEvent.id }
                            val conflicts = expandEventsForDate(candidates, newEvent.localDate)
                                .filter { it.startMinutes < newEvent.endMinutes && it.endMinutes > newEvent.startMinutes }
                                .map { occ ->
                                    val parent = candidates.first { it.id == occ.id }
                                    ConflictEntry(occ, parent, trimExistingEvent(occ, newEvent))
                                }
                            if (conflicts.isEmpty()) {
                                onSave(newEvent)
                            } else {
                                pendingEvent = newEvent
                                pendingConflicts = conflicts
                            }
                        },
                        enabled = title.isNotBlank() && endMin > startMin &&
                            (repeatType != RepeatType.EVERY_N_WEEKS || repeatDays != 0),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun repeatLabel(type: RepeatType, interval: Int): String = when (type) {
    RepeatType.NONE -> "Does not repeat"
    RepeatType.DAILY -> "Daily"
    RepeatType.WEEKDAYS -> "Weekdays (Mon – Fri)"
    RepeatType.WEEKENDS -> "Weekends (Sat & Sun)"
    RepeatType.EVERY_N_DAYS -> "Every $interval ${if (interval == 1) "day" else "days"}"
    RepeatType.EVERY_N_WEEKS -> "Every $interval ${if (interval == 1) "week" else "weeks"}"
}

private fun staticRepeatLabel(type: RepeatType): String = when (type) {
    RepeatType.NONE -> "Does not repeat"
    RepeatType.DAILY -> "Daily"
    RepeatType.WEEKDAYS -> "Weekdays (Mon – Fri)"
    RepeatType.WEEKENDS -> "Weekends (Sat & Sun)"
    RepeatType.EVERY_N_DAYS -> "Every N days…"
    RepeatType.EVERY_N_WEEKS -> "Every N weeks…"
}

private fun trimExistingEvent(existing: Event, newEvent: Event): Event? {
    if (existing.startMinutes >= newEvent.startMinutes && existing.endMinutes <= newEvent.endMinutes) {
        return null
    }
    if (existing.startMinutes < newEvent.startMinutes && existing.endMinutes > newEvent.endMinutes) {
        val leftDuration = newEvent.startMinutes - existing.startMinutes
        val rightDuration = existing.endMinutes - newEvent.endMinutes
        return if (leftDuration >= rightDuration) {
            existing.copy(endMinutes = newEvent.startMinutes)
        } else {
            existing.copy(startMinutes = newEvent.endMinutes)
        }
    }
    if (existing.startMinutes < newEvent.startMinutes) return existing.copy(endMinutes = newEvent.startMinutes)
    return existing.copy(startMinutes = newEvent.endMinutes)
}

private fun formatMin(minutes: Int): String =
    LocalTime.of(minutes / 60 % 24, minutes % 60)
        .format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
private fun TimeButton(label: String, minutes: Int, is24Hour: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatted = LocalTime.of(minutes / 60 % 24, minutes % 60)
        .format(DateTimeFormatter.ofPattern(pattern))
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinutes: Int,
    is24Hour: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60 % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val hsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue        by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1].coerceIn(0.1f, 1f)) }
    var brightness by remember { mutableStateOf(hsv[2].coerceIn(0.2f, 1f)) }
    val previewColor = remember(hue, saturation, brightness) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(previewColor))
                )
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                HueBar(hue) { hue = it }
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(previewColor),
                        activeTrackColor = Color(previewColor),
                    ),
                )
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(previewColor),
                        activeTrackColor = Color(previewColor),
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(previewColor) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    val hueColors = remember {
        (0..12).map { i ->
            Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)))
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange((offset.x / size.width * 360f).coerceIn(0f, 360f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val r  = size.height / 2f
        val cx = (hue / 360f * size.width).coerceIn(r, size.width - r)
        drawCircle(Color.White, r, Offset(cx, r))
        drawCircle(
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            r - 3.dp.toPx(),
            Offset(cx, r),
        )
    }
}
