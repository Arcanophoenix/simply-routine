package com.simplyroutine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplyroutine.data.ALERT_OPTIONS
import com.simplyroutine.data.Settings
import com.simplyroutine.data.alertLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    is24Hour: Boolean = false,
    onUpdateDayStart: (Int) -> Unit,
    onUpdateDayEnd: (Int) -> Unit,
    onUpdateTapToAdd: (Boolean) -> Unit,
    onUpdateShowNextDayEvent: (Boolean) -> Unit,
    onUpdateTimeFormat: (String) -> Unit,
    onUpdateResetScrollOnWeekChange: (Boolean) -> Unit,
    onUpdateDefaultAlertMinutes: (Int) -> Unit,
    onStartTour: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showAlertDropdown by remember { mutableStateOf(false) }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinutes = settings.dayStartMinutes,
            is24Hour = is24Hour,
            onDismiss = { showStartPicker = false },
            onConfirm = { onUpdateDayStart(it); showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinutes = settings.dayEndMinutes,
            is24Hour = is24Hour,
            onDismiss = { showEndPicker = false },
            onConfirm = { onUpdateDayEnd(it); showEndPicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Day range",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Day starts at") },
                supportingContent = {
                    Text(
                        LocalTime.of(settings.dayStartMinutes / 60 % 24, settings.dayStartMinutes % 60)
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                    )
                },
                modifier = Modifier.clickable { showStartPicker = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Day ends at") },
                supportingContent = {
                    Text(
                        LocalTime.of(settings.dayEndMinutes / 60 % 24, settings.dayEndMinutes % 60)
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                    )
                },
                modifier = Modifier.clickable { showEndPicker = true },
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "Interaction",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Tap empty space to add event") },
                supportingContent = { Text("Tapping a blank area in the timetable opens the new-event dialog") },
                trailingContent = {
                    Switch(
                        checked = settings.tapToAdd,
                        onCheckedChange = onUpdateTapToAdd,
                    )
                },
                modifier = Modifier.clickable { onUpdateTapToAdd(!settings.tapToAdd) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Show next day's first event") },
                supportingContent = {
                    Column {
                        Text("When no events remain today, show the first event of the following day in the widget and notification")
                        Text(
                            "Widget may take up to 1 minute to reflect this change",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = settings.showNextDayEvent,
                        onCheckedChange = onUpdateShowNextDayEvent,
                    )
                },
                modifier = Modifier.clickable { onUpdateShowNextDayEvent(!settings.showNextDayEvent) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Reset scroll when changing weeks") },
                supportingContent = {
                    Column {
                        Text("When off, your scroll position is kept as you swipe between weeks")
                        Text(
                            "Restart the app for this change to take effect",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = settings.resetScrollOnWeekChange,
                        onCheckedChange = onUpdateResetScrollOnWeekChange,
                    )
                },
                modifier = Modifier.clickable { onUpdateResetScrollOnWeekChange(!settings.resetScrollOnWeekChange) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Time format") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                        listOf("12h" to "12h", "24h" to "24h")
                            .forEachIndexed { i, (value, label) ->
                                SegmentedButton(
                                    selected = if (value == "24h") is24Hour else !is24Hour,
                                    onClick = { onUpdateTimeFormat(value) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    label = { Text(label) },
                                )
                            }
                    }
                },
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Default alert for new events") },
                supportingContent = {
                    Column {
                        Text("Pre-fills the alert when creating a new event. Each event's alert can still be changed individually.")
                        ExposedDropdownMenuBox(
                            expanded = showAlertDropdown,
                            onExpandedChange = { showAlertDropdown = it },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = alertLabel(settings.defaultAlertMinutes),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAlertDropdown) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = showAlertDropdown,
                                onDismissRequest = { showAlertDropdown = false },
                            ) {
                                ALERT_OPTIONS.forEach { mins ->
                                    DropdownMenuItem(
                                        text = { Text(alertLabel(mins)) },
                                        onClick = { onUpdateDefaultAlertMinutes(mins); showAlertDropdown = false },
                                    )
                                }
                            }
                        }
                    }
                },
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "Help",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("App tour") },
                supportingContent = { Text("Replay the walkthrough that highlights each feature") },
                modifier = Modifier.clickable { onNavigateBack(); onStartTour() },
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "About",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val context = LocalContext.current
            val versionName = remember {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
            ListItem(
                headlineContent = { Text("Simply Routine") },
                supportingContent = { Text("Version $versionName") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Typography") },
                supportingContent = { Text("Lexend, designed by Thomas Jockin. Licensed under the SIL Open Font License 1.1.") },
            )
            HorizontalDivider()

            Spacer(Modifier.height(16.dp))
        }
    }
}
