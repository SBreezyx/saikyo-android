package com.shio.saikyo.ui

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext


@Composable
fun SettingsSection(
    heading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        heading()
        content()
    }
}

@Composable
fun Setting(
    leadingIcon: @Composable () -> Unit,
    name: @Composable () -> Unit,
    desc: (@Composable () -> Unit)?,
    settingState: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) {
        modifier.clickable(
            enabled = true,
            onClick = onClick
        )
    } else {
        modifier
    }

    Row(modifier = mod) {
        leadingIcon()
        Column(modifier = Modifier.weight(1f)) {
            name()
            if (desc != null) desc()
        }
        settingState()
    }
}

@Composable
fun SettingsScreen() {
    // TODO: move this to a view model
    val canDrawOverlays = Settings.canDrawOverlays(LocalContext.current)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.padding(it)) {
            SettingsSection(heading = {
                Text(
                    text = "General",
                    color = MaterialTheme.colorScheme.primary
                )
            }) {
                Setting(
                    leadingIcon = {},
                    name = { Text("Live Dictionary") },
                    desc = { Text("Lets you use this app over other apps") },
                    settingState = {
                        Checkbox(
                            checked = canDrawOverlays,
                            null
                        )
                    },
                )
            }
        }
    }
}