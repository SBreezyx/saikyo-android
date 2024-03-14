package com.shio.saikyo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shio.saikyo.IntentLaunchers
import com.shio.saikyo.R


@Composable
fun RequestOverlayDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        icon = {
            Icon(imageVector = Icons.Default.Info, contentDescription = null)
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OPEN SETTINGS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        },
        text = {
            Text(
                text = "To use this app anywhere, we need the overlay permission. Please open settings and grant us this permission."
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Header(
    title: String,
    onExitClicked: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpanded: () -> Unit,
    onMenuDismiss: () -> Unit,
    menuItems: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.blue_heart),
                    contentDescription = "a humorous app icon",
                    modifier = Modifier.size(64.dp)

                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        actions = {
            IconButton(onClick = onExitClicked) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "exit to home screen and use live dict button"
                )
            }

            IconButton(onClick = onMenuExpanded) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "settings"
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onMenuDismiss,
                ) {
                    menuItems.forEach {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )
                            },
                            text = { Text(it.first) },
                            onClick = it.second
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier.shadow(4.dp)
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchField(
    searchTerm: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val noTextEntered = searchTerm.isEmpty()
    val fm = LocalFocusManager.current

    TextField(
        value = searchTerm,
        onValueChange = onValueChange,
        modifier = modifier.onInterceptKeyBeforeSoftKeyboard {
            if (it.key == Key.Back) {
                fm.clearFocus()
            }

            return@onInterceptKeyBeforeSoftKeyboard false
        },
        label = {
            Text("Search a word...")
        },
        trailingIcon = {
            if (!noTextEntered) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Button to erase all text from the search field"
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                fm.clearFocus()
                onSearch()
            }
        ),
        maxLines = 3
    )

}


@Composable
fun DefaultScreen(
    intentLaunchers: IntentLaunchers,
    navToSettings: () -> Unit,
) {
    val (requestOverlay, setRequestOverlay) = remember { mutableStateOf(false) }
    val (menuExpanded, setMenuExpanded) = remember { mutableStateOf(false) }
    val (searchTerm, setSearchTerm) = remember { mutableStateOf("") }

    val fm = LocalFocusManager.current

    Scaffold(
        topBar = {
            Header(
                title = "最強",
                onExitClicked = {
                    intentLaunchers.startLiveDict()
                },
                menuExpanded = menuExpanded,
                onMenuExpanded = { setMenuExpanded(true) },
                onMenuDismiss = { setMenuExpanded(false) },
                menuItems = listOf(
                    Pair("Settings", navToSettings)
                )
            )
        },
    ) { contentPadding ->
        if (requestOverlay) {
            RequestOverlayDialog(
                onDismiss = { setRequestOverlay(false) },
                onConfirm = intentLaunchers.reqOverlayLauncher
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // when searching with the search field, clicking off of it will release focus
                    fm.clearFocus()
                }
        ) {
            SearchField(
                searchTerm,
                onValueChange = { setSearchTerm(it) },
                onClear = { setSearchTerm("") },
                onSearch = {

                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PagePreview() {
//    SaikyoTheme(darkTheme = false) {
    DefaultScreen(IntentLaunchers({}, {}), {})
//    }
}

@Preview
@Composable
fun DialogPreview() {
    RequestOverlayDialog({}, {})
}