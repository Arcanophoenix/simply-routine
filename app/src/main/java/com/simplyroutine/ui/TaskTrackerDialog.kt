package com.simplyroutine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.simplyroutine.data.Task
import com.simplyroutine.data.urgencyScore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskTrackerDialog(
    tasks: List<Task>,
    householdId: String?,
    onDismiss: () -> Unit,
    onAdd: (Task) -> Unit,
    onUpdate: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: (String, (Boolean) -> Unit) -> Unit,
    onLeaveHousehold: () -> Unit,
    onPinTaskListWidget: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val sorted = remember(tasks) {
        tasks.sortedByDescending { it.urgencyScore(today) }
    }

    var showAddEdit by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var showHousehold by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onPinTaskListWidget) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = "Add tasks widget",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showHousehold = true }) {
                        Icon(
                            Icons.Outlined.Home,
                            contentDescription = "Household sync",
                            tint = if (householdId != null) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editingTask = null; showAddEdit = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add task")
                    }
                }

                HorizontalDivider()

                if (sorted.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No tasks yet.\nTap + to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(sorted) { task ->
                            TaskRow(
                                task = task,
                                today = today,
                                onDone = { onUpdate(task.copy(lastCompleted = today.toEpochDay())) },
                                onLongPress = { editingTask = task; showAddEdit = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEdit) {
        TaskEditDialog(
            task = editingTask,
            householdId = householdId,
            onDismiss = { showAddEdit = false; editingTask = null },
            onSave = { saved ->
                if (saved.id == 0) onAdd(saved) else onUpdate(saved)
                showAddEdit = false
                editingTask = null
            },
            onDelete = editingTask?.let { t -> { onDelete(t); showAddEdit = false; editingTask = null } },
        )
    }

    if (showHousehold) {
        HouseholdDialog(
            householdId = householdId,
            onCreateHousehold = onCreateHousehold,
            onJoinHousehold = onJoinHousehold,
            onLeaveHousehold = onLeaveHousehold,
            onDismiss = { showHousehold = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    today: LocalDate,
    onDone: () -> Unit,
    onLongPress: () -> Unit,
) {
    val urgency = task.urgencyScore(today)
    val stripColor = urgencyColor(urgency)
    val lastDone = task.lastCompletedDate
    val doneToday = lastDone == today

    val scheduleStr = when (task.frequencyUnit) {
        "none"   -> "no schedule"
        "months" -> "every ${task.frequencyDays} ${if (task.frequencyDays == 1) "month" else "months"}"
        else     -> "every ${frequencyLabel(task.frequencyDays)}"
    }
    val subtitle = when {
        lastDone == null -> "Never done · $scheduleStr"
        doneToday        -> "Done today · $scheduleStr"
        else             -> "${ChronoUnit.DAYS.between(lastDone, today)}d ago · $scheduleStr"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(stripColor, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                if (task.shared) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Home,
                        contentDescription = "Shared",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onDone,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (doneToday) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "Mark done today")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditDialog(
    task: Task?,
    householdId: String?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isEdit = task != null
    var title by remember { mutableStateOf(task?.title ?: "") }
    var freqUnit by remember { mutableStateOf(task?.frequencyUnit ?: "days") }
    var freqInterval by remember { mutableStateOf(if (task?.frequencyUnit == "none") 1 else task?.frequencyDays ?: 1) }
    val namedPresets = remember {
        setOf(Pair("days", 1), Pair("days", 7), Pair("days", 14), Pair("months", 1), Pair("months", 3), Pair("months", 12))
    }
    var customFreq by remember {
        mutableStateOf(
            task != null && task.frequencyUnit != "none" &&
                Pair(task.frequencyUnit, task.frequencyDays) !in namedPresets
        )
    }
    var showFreqDropdown by remember { mutableStateOf(false) }
    var lastCompleted by remember { mutableStateOf(task?.lastCompletedDate) }
    var shared by remember { mutableStateOf(task?.shared ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val valid = title.isNotBlank() && (freqUnit == "none" || freqInterval >= 1)
    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit task" else "New task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = showFreqDropdown,
                    onExpandedChange = { showFreqDropdown = it },
                ) {
                    OutlinedTextField(
                        value = freqLabel(freqUnit, freqInterval),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Schedule") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFreqDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = showFreqDropdown,
                        onDismissRequest = { showFreqDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("No schedule") },
                            onClick = { freqUnit = "none"; customFreq = false; showFreqDropdown = false },
                        )
                        listOf(
                            "Daily"       to Pair("days",   1),
                            "Weekly"      to Pair("days",   7),
                            "Fortnightly" to Pair("days",  14),
                            "Monthly"     to Pair("months", 1),
                            "Quarterly"   to Pair("months", 3),
                            "Yearly"      to Pair("months", 12),
                        ).forEach { (label, unitInterval) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    freqUnit = unitInterval.first
                                    freqInterval = unitInterval.second
                                    customFreq = false
                                    showFreqDropdown = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Every N days…") },
                            onClick = {
                                if (freqUnit != "days") { freqUnit = "days"; freqInterval = 1 }
                                customFreq = true
                                showFreqDropdown = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Every N months…") },
                            onClick = {
                                if (freqUnit != "months") { freqUnit = "months"; freqInterval = 1 }
                                customFreq = true
                                showFreqDropdown = false
                            },
                        )
                    }
                }
                if (freqUnit != "none" && customFreq) {
                    val unitWord = if (freqUnit == "months") {
                        if (freqInterval == 1) "month" else "months"
                    } else {
                        if (freqInterval == 1) "day" else "days"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Every", style = MaterialTheme.typography.bodyMedium)
                        FilledTonalIconButton(
                            onClick = { if (freqInterval > 1) freqInterval-- },
                            modifier = Modifier.size(36.dp),
                        ) { Text("−") }
                        Text(
                            "$freqInterval",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.widthIn(min = 24.dp),
                        )
                        FilledTonalIconButton(
                            onClick = { if (freqInterval < 99) freqInterval++ },
                            modifier = Modifier.size(36.dp),
                        ) { Text("+") }
                        Text(unitWord, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "Last completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            lastCompleted?.format(dateFmt) ?: "Never",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = { showDatePicker = true }) { Text("Change") }
                }
                if (householdId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sync with household",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                householdId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = shared, onCheckedChange = { shared = it })
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete task")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(Task(
                        id = task?.id ?: 0,
                        title = title.trim(),
                        frequencyDays = if (freqUnit == "none") 1 else freqInterval,
                        frequencyUnit = freqUnit,
                        lastCompleted = lastCompleted?.toEpochDay(),
                        syncId = task?.syncId ?: UUID.randomUUID().toString(),
                        shared = shared,
                    ))
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val initialMs = lastCompleted
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    lastCompleted = datePickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun urgencyColor(score: Float): Color = when {
    score < 0f                                -> Color(0xFF64B5F6)  // blue — no schedule
    score == Float.MAX_VALUE || score >= 1.5f -> Color(0xFF9575CD)  // purple — significantly overdue
    score >= 1.0f                             -> Color(0xFFFF8A65)  // coral — due now
    score >= 0.7f                             -> Color(0xFFFFCC80)  // amber — coming up soon
    else                                      -> Color(0xFF81C784)  // green — well ahead
}

private fun freqLabel(unit: String, interval: Int): String = when {
    unit == "none"                     -> "No schedule"
    unit == "months" && interval == 1  -> "Monthly"
    unit == "months" && interval == 3  -> "Quarterly"
    unit == "months" && interval == 12 -> "Yearly"
    unit == "days"   && interval == 1  -> "Daily"
    unit == "days"   && interval == 7  -> "Weekly"
    unit == "days"   && interval == 14 -> "Fortnightly"
    unit == "months"                   -> "Every $interval months"
    else                               -> "Every $interval days"
}

private fun frequencyLabel(days: Int): String = when (days) {
    1    -> "day"
    7    -> "week"
    14   -> "2 weeks"
    30   -> "month"
    else -> "${days}d"
}
