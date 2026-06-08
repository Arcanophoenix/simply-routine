package com.simplyroutine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskTrackerDialog(
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onAdd: (Task) -> Unit,
    onUpdate: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val sorted = remember(tasks) {
        tasks.sortedByDescending { it.urgencyScore(today) }
    }

    var showAddEdit by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }

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
                        items(sorted, key = { it.id }) { task ->
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
            onDismiss = { showAddEdit = false; editingTask = null },
            onSave = { saved ->
                if (saved.id == 0) onAdd(saved) else onUpdate(saved)
                showAddEdit = false
                editingTask = null
            },
            onDelete = editingTask?.let { t -> { onDelete(t); showAddEdit = false; editingTask = null } },
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

    val subtitle = when {
        lastDone == null -> "Never done · every ${frequencyLabel(task.frequencyDays)}"
        doneToday        -> "Done today · every ${frequencyLabel(task.frequencyDays)}"
        else             -> {
            val days = ChronoUnit.DAYS.between(lastDone, today)
            "${days}d ago · every ${frequencyLabel(task.frequencyDays)}"
        }
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
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
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
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isEdit = task != null
    var title by remember { mutableStateOf(task?.title ?: "") }
    var freqText by remember { mutableStateOf(task?.frequencyDays?.toString() ?: "1") }
    var lastCompleted by remember { mutableStateOf(task?.lastCompletedDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val valid = title.isNotBlank() && freqText.toIntOrNull()?.let { it >= 1 } == true
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
                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it.filter { c -> c.isDigit() } },
                    label = { Text("Every N days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    val freq = freqText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onSave(Task(
                        id = task?.id ?: 0,
                        title = title.trim(),
                        frequencyDays = freq,
                        lastCompleted = lastCompleted?.toEpochDay(),
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
    score == Float.MAX_VALUE || score >= 1.5f -> Color(0xFF9575CD)  // soft purple — significantly overdue
    score >= 1.0f                             -> Color(0xFFFF8A65)  // soft coral — due now
    score >= 0.7f                             -> Color(0xFFFFCC80)  // soft amber — coming up soon
    else                                      -> Color(0xFF81C784)  // soft green — well ahead
}

private fun frequencyLabel(days: Int): String = when (days) {
    1    -> "day"
    7    -> "week"
    14   -> "2 weeks"
    30   -> "month"
    else -> "${days}d"
}
