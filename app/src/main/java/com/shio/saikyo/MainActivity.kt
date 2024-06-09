package com.shio.saikyo

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.shio.saikyo.ui.AllSettingsScreen
import com.shio.saikyo.ui.DefinitionScreen
import com.shio.saikyo.ui.Dictionary2Screen
import com.shio.saikyo.ui.HomeScreen
import com.shio.saikyo.ui.LR2
import com.shio.saikyo.ui.LanguageReactor
import com.shio.saikyo.ui.Routes
import com.shio.saikyo.ui.media.MediaPlayerScreen
import com.shio.saikyo.ui.settings.LocalMediaSelectionScreen


class MainActivity : SaikyoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            UIRoot {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation(navCtrl: NavHostController = rememberNavController()) {
    NavHost(
        navController = navCtrl, startDestination = Routes.Home,
        // TODO: slide-in transitions
        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }
    ) {
        composable<Routes.Home> {
            HomeScreen(
                navToDictionary = { navCtrl.navigate(Routes.Dictionary) },
                navToLanguageReactor = { navCtrl.navigate(Routes.LanguageReactor) },
                navToSettings = { navCtrl.navigate(Routes.Settings) },
//                navToExit = { (navCtrl.context as Activity).finishAndRemoveTask() },
                navToYoutubeDL = { navCtrl.navigate(Routes.LR2) },
            )
        }

        composable<Routes.Dictionary> {
            Dictionary2Screen(
                navToDefinition = { lemmaId -> navCtrl.navigate(Routes.WordDefinition(lemmaId)) },
                navBack = { navCtrl.navigateUp() }
            )
        }

        composable<Routes.WordDefinition> {
            val lemmaId = it.toRoute<Routes.WordDefinition>().lemmaId
            DefinitionScreen(
                lemmaId,
                { navCtrl.navigate(Routes.Settings) },
                { navCtrl.navigateUp() }
            )
        }

        composable<Routes.LanguageReactor> {
            LanguageReactor(
                { navCtrl.navigate(Routes.Home) },
                { navCtrl.navigate(Routes.Settings) },
                { video, subtitle -> navCtrl.navigate(Routes.VideoPlayer(video, subtitle)) },
                { audio, subtitle -> navCtrl.navigate(Routes.AudioPlayer(audio, subtitle)) }
            )
        }

        composable<Routes.LR2> {
            LR2(
                navToDict = { navCtrl.navigate(Routes.Dictionary) },
                navToLr = { navCtrl.navigate(Routes.LanguageReactor) },
                navBack = { navCtrl.navigateUp() }
            )
        }

        composable<Routes.VideoPlayer> {
            val selectedVideo = it.toRoute<Routes.VideoPlayer>()
            MediaPlayerScreen(
                asVideo = true,
                selectedVideo.videoUri, selectedVideo.subtitleUri,
                navBack = { navCtrl.navigateUp() },
            )
        }

        composable<Routes.AudioPlayer> {
            val selectedAudio = it.toRoute<Routes.AudioPlayer>()
            MediaPlayerScreen(
                asVideo = false,
                selectedAudio.audioUri, selectedAudio.subtitleUri,
                navBack = { navCtrl.navigateUp() }
            )
        }

        navigation<Routes.Settings>(startDestination = Routes.Settings.AllSettings) {
            composable<Routes.Settings.AllSettings> {
                AllSettingsScreen(
                    navToLocalFileAccess = { navCtrl.navigate(Routes.Settings.LocalMediaSelection) },
                    navToHome = { navCtrl.navigate(Routes.Home) },
                    navToLanguageReactor = { navCtrl.navigate(Routes.LanguageReactor) }
                )
            }

            composable<Routes.Settings.LocalMediaSelection> {
                LocalMediaSelectionScreen(
                    navBack = { navCtrl.navigateUp() }
                )
            }
        }
    }
}