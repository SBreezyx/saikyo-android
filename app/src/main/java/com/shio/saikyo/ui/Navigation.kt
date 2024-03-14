package com.shio.saikyo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shio.saikyo.IntentLaunchers


@Composable
fun UINavigation(intentLaunchers: IntentLaunchers) {
    val navCtrl = rememberNavController()

    NavHost(navController = navCtrl, startDestination = "default") {
        composable("default") {
            DefaultScreen(intentLaunchers) {
                navCtrl.navigate("settings")
            }
        }

        composable("settings") {
            SettingsScreen()
        }
    }
}