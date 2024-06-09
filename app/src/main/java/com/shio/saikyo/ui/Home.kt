package com.shio.saikyo.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.session.MediaBrowser
import com.shio.saikyo.LiveDictService
import com.shio.saikyo.R
import com.shio.saikyo.ui.primitives.DefaultAppBar
import com.shio.saikyo.ui.primitives.DefaultNavBar
import com.shio.saikyo.ui.primitives.PrimaryDestinations
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.forEachIndexed
import com.shio.saikyo.youtubedl.YoutubeDL

@Composable
fun rememberMediaProjectionLauncher(onSuccess: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val mMediaProjection = ctx.getSystemService(MediaProjectionManager::class.java)
    val startLiveDict = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val fgsIntent = Intent(ctx, LiveDictService::class.java).apply {
                putExtra("resultCode", it.resultCode)
                putExtra("resultIntent", it.data)
            }
            ctx.startForegroundService(fgsIntent)

            onSuccess()
        } else {
            // do nothing; the user denied our request for some reason
        }
    }

    return { startLiveDict.launch(mMediaProjection.createScreenCaptureIntent()) }
}

@Composable
fun LiveDictButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(onClick = onClick, modifier = modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
        contentDescription = stringResource(R.string.live_dict_btn_desc)
    )
}

@Composable
fun WordSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(onClick = onClick, modifier = modifier) {
    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.word_search_button_desc))
}

@Composable
fun YoutubeDLButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(onClick = onClick, modifier = modifier) {
    Icon(painterResource(R.drawable.youtube_activity_24dp_fill), contentDescription = null)
}

@Composable
fun HomeScreen(
    navToDictionary: () -> Unit,
    navToLanguageReactor: () -> Unit,
    navToSettings: () -> Unit,
//    liveDictLauncher: () -> Unit,
    navToYoutubeDL: () -> Unit,
) {
    Scaffold(
        topBar = {
            DefaultAppBar(
                actionItems = {
                    YoutubeDLButton(onClick = navToYoutubeDL)
                    WordSearchButton(onClick = navToDictionary)
                }
            )
        },
        bottomBar = {
            DefaultNavBar(selectedIndex = 0) {
                add(PrimaryDestinations.home())
                add(PrimaryDestinations.languageReactor(onClick = navToLanguageReactor))
                add(PrimaryDestinations.settings(onClick = navToSettings))
            }
        }
    ) { insets ->
        var selectedFilter by remember { mutableIntStateOf(0) }
        Column(modifier = Modifier.padding(insets)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf("All", "Videos").forEachIndexed { ix, filt ->
                    FilterChip(
                        selected = ix == selectedFilter,
                        onClick = { selectedFilter = ix },
                        label = { Text(filt) }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    navToDictionary: () -> Unit,
    navToLanguageReactor: () -> Unit,
    navToSettings: () -> Unit,
//    navToExit: () -> Unit,
    navToYoutubeDL: () -> Unit,
    vm: HomeVM = viewModel(factory = HomeVM.factory())
) {

    HomeScreen(
        navToDictionary = navToDictionary,
        navToLanguageReactor = navToLanguageReactor,
        navToSettings = navToSettings,
//        liveDictLauncher = rememberMediaProjectionLauncher(onSuccess = navToExit),
        navToYoutubeDL = navToYoutubeDL
    )
}

class HomeVM : ViewModel() {
    // TODO: job to check for shit

    companion object {


        fun factory() = viewModelFactory {
            initializer {
                HomeVM()
            }
        }
    }
}

@Preview
@Composable
fun HomePreview() = SaikyoTheme {
    HomeScreen({}, {}, {}, {})
}