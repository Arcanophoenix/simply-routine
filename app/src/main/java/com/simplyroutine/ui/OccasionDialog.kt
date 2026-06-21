package com.simplyroutine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.simplyroutine.data.Occasion
import com.simplyroutine.data.RepeatType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OccasionDialog(
    date: LocalDate,
    occasions: List<Occasion>,
    onDismiss: () -> Unit,
    onAdd: (Occasion) -> Unit,
    onUpdate: (Occasion) -> Unit,
    onDelete: (Occasion) -> Unit,
) {
    var showAddEdit by remember { mutableStateOf(false) }
    var editingOccasion by remember { mutableStateOf<Occasion?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("MMMM d")),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { editingOccasion = null; showAddEdit = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add occasion")
                    }
                }

                HorizontalDivider()

                if (occasions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No occasions.\nTap + to add one.",
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
                        items(occasions) { occasion ->
                            OccasionRow(
                                occasion = occasion,
                                onLongPress = { editingOccasion = occasion; showAddEdit = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEdit) {
        OccasionEditDialog(
            occasion = editingOccasion,
            defaultDate = date,
            onDismiss = { showAddEdit = false; editingOccasion = null },
            onSave = { saved ->
                if (saved.id == 0) onAdd(saved) else onUpdate(saved)
                showAddEdit = false
                editingOccasion = null
            },
            onDelete = editingOccasion?.let { o -> { onDelete(o); showAddEdit = false; editingOccasion = null } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OccasionRow(occasion: Occasion, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(occasion.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                occasionRepeatLabel(occasion.recurrence, occasion.repeatInterval),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccasionEditDialog(
    occasion: Occasion?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Occasion) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isEdit = occasion != null
    var title by remember { mutableStateOf(occasion?.title ?: "") }
    var repeatType by remember { mutableStateOf(occasion?.recurrence ?: RepeatType.YEARLY) }
    var repeatInterval by remember { mutableStateOf(occasion?.repeatInterval ?: 1) }
    var repeatDays by remember {
        mutableStateOf(occasion?.repeatDays?.takeIf { it != 0 } ?: (1 shl (defaultDate.dayOfWeek.value - 1)))
    }
    var showRepeatDropdown by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit occasion" else "New occasion") },
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
                    expanded = showRepeatDropdown,
                    onExpandedChange = { if (it) focusManager.clearFocus(); showRepeatDropdown = it },
                ) {
                    OutlinedTextField(
                        value = occasionRepeatLabel(repeatType, repeatInterval),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRepeatDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = showRepeatDropdown,
                        onDismissRequest = { showRepeatDropdown = false },
                    ) {
                        RepeatType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(staticOccasionRepeatLabel(type)) },
                                onClick = {
                                    if (type == RepeatType.EVERY_N_WEEKS && repeatDays == 0)
                                        repeatDays = 1 shl (defaultDate.dayOfWeek.value - 1)
                                    repeatType = type
                                    showRepeatDropdown = false
                                },
                            )
                        }
                    }
                }

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
                                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                            val next = if (selected) repeatDays and bit.inv() else repeatDays or bit
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

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete occasion") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Occasion(
                            id = occasion?.id ?: 0,
                            title = title.trim(),
                            date = occasion?.date ?: defaultDate.toEpochDay(),
                            repeatType = repeatType.name,
                            repeatInterval = repeatInterval,
                            repeatDays = if (repeatType == RepeatType.EVERY_N_WEEKS) repeatDays else 0,
                        )
                    )
                },
                enabled = title.isNotBlank() && (repeatType != RepeatType.EVERY_N_WEEKS || repeatDays != 0),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun occasionRepeatLabel(type: RepeatType, interval: Int): String = when (type) {
    RepeatType.NONE -> "One-off"
    RepeatType.DAILY -> "Daily"
    RepeatType.WEEKDAYS -> "Weekdays"
    RepeatType.WEEKENDS -> "Weekends"
    RepeatType.EVERY_N_DAYS -> "Every $interval ${if (interval == 1) "day" else "days"}"
    RepeatType.EVERY_N_WEEKS -> "Every $interval ${if (interval == 1) "week" else "weeks"}"
    RepeatType.MONTHLY -> "Monthly"
    RepeatType.YEARLY -> "Yearly"
}

private fun staticOccasionRepeatLabel(type: RepeatType): String = when (type) {
    RepeatType.NONE -> "One-off"
    RepeatType.DAILY -> "Daily"
    RepeatType.WEEKDAYS -> "Weekdays"
    RepeatType.WEEKENDS -> "Weekends"
    RepeatType.EVERY_N_DAYS -> "Every N days…"
    RepeatType.EVERY_N_WEEKS -> "Every N weeks…"
    RepeatType.MONTHLY -> "Monthly"
    RepeatType.YEARLY -> "Yearly"
}
