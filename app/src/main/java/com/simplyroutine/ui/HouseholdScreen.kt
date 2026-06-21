package com.simplyroutine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdDialog(
    householdId: String?,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: (String, (Boolean) -> Unit) -> Unit,
    onLeaveHousehold: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.apply {
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                )
                @Suppress("DEPRECATION")
                navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isNavigationBarContrastEnforced = false
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    TopAppBar(
                        title = { Text("Household sync") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                HouseholdContent(
                    householdId = householdId,
                    onCreateHousehold = onCreateHousehold,
                    onJoinHousehold = onJoinHousehold,
                    onLeaveHousehold = onLeaveHousehold,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun HouseholdContent(
    householdId: String?,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: (String, (Boolean) -> Unit) -> Unit,
    onLeaveHousehold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var joinCode by remember { mutableStateOf("") }
    var joinFailed by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (householdId != null) {
            Spacer(Modifier.height(32.dp))
            Text("Your household code", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    householdId,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Share this code with your housemates so they can join.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(householdId)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copy code")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onLeaveHousehold,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(MaterialTheme.colorScheme.error)),
            ) {
                Text("Leave household")
            }
        } else {
            Spacer(Modifier.height(32.dp))
            Text(
                "Sync tasks with your household",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Mark individual tasks as shared and they'll stay in sync with anyone in your household whenever either of you marks one done.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onCreateHousehold,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create a household")
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))
            Text(
                "Join an existing household",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase(); joinFailed = false },
                label = { Text("Household code") },
                placeholder = { Text("e.g. ROBIN-4821") },
                isError = joinFailed,
                supportingText = if (joinFailed) {
                    { Text("Code not found — check it and try again.") }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (joinCode.isNotBlank() && !joining) {
                        joining = true
                        onJoinHousehold(joinCode) { success ->
                            joining = false
                            if (!success) joinFailed = true
                        }
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    joining = true
                    onJoinHousehold(joinCode) { success ->
                        joining = false
                        if (!success) joinFailed = true
                    }
                },
                enabled = joinCode.isNotBlank() && !joining,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (joining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Join")
                }
            }
        }
    }
}
