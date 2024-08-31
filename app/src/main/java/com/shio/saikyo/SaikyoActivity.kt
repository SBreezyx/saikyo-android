package com.shio.saikyo

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.shio.saikyo.notif.LIVE_DICT_NOTIF
import com.shio.saikyo.ui.ComposeContextMenu
import com.shio.saikyo.ui.ContextMenuItem
import com.shio.saikyo.ui.LocalTextProcessors
import com.shio.saikyo.ui.Routes
import com.shio.saikyo.ui.theme.SaikyoTheme

fun Context.enableEdgeToEdge(
    statusBars: Color = Color.Transparent,
    navigationBarStyle: Color = Color.Transparent,
) = (this as? Activity)?.let {
    it.window.statusBarColor = statusBars.toArgb()
    it.window.navigationBarColor= navigationBarStyle.toArgb()
}

@Composable
fun UIRoot(
    ctx: Context = LocalContext.current,
    app: SaikyoApp = ctx.applicationContext as SaikyoApp,
    content: @Composable () -> Unit
) = SaikyoTheme {
    CompositionLocalProvider(
        LocalTextProcessors provides app.textProcessors.toTypedArray(),
        LocalTextToolbar provides ComposeContextMenu(LocalView.current, listOf(
            ContextMenuItem.Copy, ContextMenuItem.Paste, ContextMenuItem.Cut, ContextMenuItem.SelectAll
        ))
    ) {
        content()
    }
}

@MainThread
fun<T : Any> NavHostController.navigateLateral(route: T) = navigate(route) {
    // Pop up to the start destination of the graph to
    // avoid building up a large stack of destinations
    // on the back stack as users select items

    popUpTo(graph.findStartDestination().id) {
        saveState = true
    }
    // Avoid multiple copies of the same destination when
    // reselecting the same item
    launchSingleTop = true

    // Restore state when reselecting a previously selected item
    restoreState = true
}

open class SaikyoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: consider if this impacts start-up time and can be moved to a different thread
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(LIVE_DICT_NOTIF, "LiveDict", NotificationManager.IMPORTANCE_LOW).apply {
                // TODO: string resource the below
                description = "See if the live dictionary feature is running across your device"
            }
        ))
    }
}