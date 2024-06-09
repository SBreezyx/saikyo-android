package com.shio.saikyo.ui.primitives

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shio.saikyo.R
import com.shio.saikyo.ui.theme.SaikyoTheme


data class MenuItemTuple(
    val icon: @Composable () -> Unit,
    val name: @Composable () -> Unit = NoName,
    val onClick: () -> Unit,
) {
    companion object {
        val NoName = @Composable {}
    }
}

object PrimaryDestinations {
    private fun genDestination(
        @DrawableRes iconId: Int,
        @StringRes labelId: Int? = null,
        onClick: () -> Unit = {}
    ) = if (labelId != null) {
        MenuItemTuple(
            { Icon(painterResource(iconId), stringResource(id = labelId)) },
            { Text(stringResource(id = labelId)) },
            onClick
        )
    } else {
        MenuItemTuple(
            { Icon(painterResource(iconId), null) },
            MenuItemTuple.NoName,
            onClick
        )
    }

    fun home(@StringRes labelId: Int? = null, onClick: () -> Unit = {}) = genDestination(
        R.drawable.home_24dp_fill, labelId, onClick
    )

    fun languageReactor(@StringRes labelId: Int? = null, onClick: () -> Unit = {}) = genDestination(
        R.drawable.movie_24dp_fill, labelId, onClick
    )

    fun settings(@StringRes labelId: Int? = null, onClick: () -> Unit = {}) = genDestination(
        R.drawable.blue_heart, labelId, onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppBar(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
    actionItems: @Composable RowScope.() -> Unit = {},
    menuItems: List<MenuItemTuple> = listOf(),
    navigation: @Composable () -> Unit = {}
) = TopAppBar(
    navigationIcon = navigation,
    title = {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.blue_heart),
                contentDescription = stringResource(R.string.app_icon_desc),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        }
    },
    actions = {
        actionItems()

        if (menuItems.isNotEmpty()) {
            var menuExpanded by remember { mutableStateOf(false) }

            IconButton(
                onClick = { menuExpanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.settings)
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    for ((icon, name, onClick) in menuItems) {
                        DropdownMenuItem(
                            leadingIcon = icon,
                            text = name,
                            onClick = onClick
                        )
                    }
                }
            }
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ),
    modifier = modifier.shadow(4.dp)
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterActionBar(
    center: @Composable (RowScope.() -> Unit),
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actionItems: (@Composable (RowScope.() -> Unit))? = null
) {
    val paddingLeft = if (navigation != null) 0.dp else 16.dp
    val paddingRight = if (actionItems != null) 0.dp else 16.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(64.dp)
            .padding(start = paddingLeft, end = paddingRight)
    ) {
        navigation?.invoke()
        center()
        actionItems?.invoke(this)
    }
}

@Composable
fun DefaultNavBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    selectedIndex: Int = 0,
    navItemsBuilder: MutableList<MenuItemTuple>.() -> Unit = { },
) {
//    val win = (LocalContext.current as Activity).window
//    win.navigationBarColor = MaterialTheme.colorScheme.surfaceColorAtElevation(tonalElevation).toArgb()

    NavigationBar(
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        modifier = modifier
    ) {
        val navItems = mutableListOf<MenuItemTuple>()
        navItems.navItemsBuilder()
        val maxSz = LocalViewConfiguration.current.minimumTouchTargetSize
        for ((ix, item) in navItems.withIndex()) {
            NavigationBarItem(
                selected = ix == selectedIndex,
                onClick = item.onClick,
                icon = item.icon,
                label = if (item.name != MenuItemTuple.NoName) item.name else null,
                modifier = Modifier.size(maxSz)
            )
        }
    }
}

@Preview
@Composable
private fun HeaderPreview() {
    SaikyoTheme {
        DefaultAppBar(
            menuItems = listOf(
                MenuItemTuple(
                    { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                    { Text("Settings") },
                    {}
                )
            )
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Nav() = SaikyoTheme {
    DefaultNavBar() {
        add(PrimaryDestinations.languageReactor())
        add(PrimaryDestinations.home())
    }
}