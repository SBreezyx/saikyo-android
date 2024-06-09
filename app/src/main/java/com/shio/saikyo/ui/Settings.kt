package com.shio.saikyo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shio.saikyo.R
import com.shio.saikyo.ui.primitives.DefaultAppBar
import com.shio.saikyo.ui.primitives.DefaultNavBar
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.primitives.PrimaryDestinations
import com.shio.saikyo.ui.primitives.ProgressNextButton
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.verticalScrollbar

@Composable
fun Setting(
    leadingIcon: @Composable () -> Unit,
    name: @Composable () -> Unit,
    desc: (@Composable () -> Unit)?,
    settingAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier
) {
    leadingIcon()
    Column(modifier = Modifier.weight(1f)) {
        name()
        if (desc != null) desc()
    }
    settingAction()
}

@Composable
fun SettingSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.labelMedium,
    modifier = modifier
)

@Composable
fun SettingsScaffold(
    appBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Scaffold(
    topBar = appBar,
    modifier = Modifier.fillMaxSize()
) { insets ->
    Column(
        modifier = modifier
            .padding(insets)
            .verticalScrollbar(rememberScrollState()),
        content = content
    )
}

@Composable
fun AllSettingsScreen(
    navToLocalFileAccess: () -> Unit,
    navToHome: () -> Unit,
    navToLanguageReactor: () -> Unit,
) {
    Scaffold(
        topBar = {
            DefaultAppBar(
                title = stringResource(R.string.settings_title),
            )
        },
        bottomBar = {
            DefaultNavBar(selectedIndex = 2) {
                add(PrimaryDestinations.home(onClick = navToHome))
                add(PrimaryDestinations.languageReactor(onClick = navToLanguageReactor))
                add(PrimaryDestinations.settings())
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScrollbar(rememberScrollState()),
        ) {
            val contentPadding = remember { 8.dp }

            SettingSectionHeading(
                text = "GENERAL",
                modifier = Modifier.padding(start = contentPadding, top = contentPadding)
            )
            Setting(
                leadingIcon = {
                },
                name = {
                    Text(
                        "Local File Access",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                desc = {
                    Text("Choose which local directories to add to your library.")
                },
                settingAction = {
                    ProgressNextButton(onClick = navToLocalFileAccess)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = navToLocalFileAccess)
                    .padding(contentPadding)
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() = SaikyoTheme {
    AllSettingsScreen(navToLocalFileAccess = {}, {}, {})
}