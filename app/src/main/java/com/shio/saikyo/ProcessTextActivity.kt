package com.shio.saikyo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.shio.saikyo.ui.DefinitionScreen
import com.shio.saikyo.ui.DictionaryLiteScreen
import com.shio.saikyo.ui.Routes

class ProcessTextActivity : SaikyoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            UIRoot {
                ProcessTextNavigation(intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)!!)
            }
        }
    }
}

@Composable
fun ProcessTextNavigation(prompt: String) {
    val navCtrl = rememberNavController()

    NavHost(
        navController = navCtrl, startDestination = Routes.Dictionary,
        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }
    ) {
        composable<Routes.Dictionary> {
            DictionaryLiteScreen(
                prompt = prompt,
                navToDefinition = { lemmaId -> navCtrl.navigate(Routes.WordDefinition(lemmaId)) },
                navBack = { (navCtrl.context as Activity).finish() }
            )
        }

        composable<Routes.WordDefinition> {
            DefinitionScreen(
                it.toRoute<Routes.WordDefinition>().lemmaId,
                { navCtrl.navigate(Routes.Settings) },
                { navCtrl.navigateUp() }
            )
        }
    }
}