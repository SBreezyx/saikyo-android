package com.shio.saikyo.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.UUID

fun <T : Iterable<String>> Context.checkAllGranted(perms: T) = perms.all {
    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

val perms = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
}

@Composable
fun <I, O> rememberLauncherForActivityResultImmediate(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ActivityResultLauncher<I> {
    // Keep track of the current contract and onResult listener
    val currentOnResult = rememberUpdatedState(onResult)

    // It doesn't really matter what the key is, just that it is unique
    // and consistent across configuration changes
    val key = rememberSaveable { UUID.randomUUID().toString() }

    val activityResultRegistry = checkNotNull(LocalActivityResultRegistryOwner.current) {
        "No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner"
    }.activityResultRegistry

    val realLauncher = remember {
        activityResultRegistry.register(key, contract) {
            currentOnResult.value(it)
        }
    }

    DisposableEffect(activityResultRegistry, key, contract) {

        // only dispose so we can use this launcher ASAP
        onDispose {
            realLauncher.unregister()
        }
    }
    return realLauncher
}

@Composable
fun EnsurePermissions(perms: List<String>, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val lc = LocalLifecycleOwner.current

    var allGranted by remember {
        mutableStateOf(ctx.checkAllGranted(perms))
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { mp ->
        allGranted = mp.values.all { it }
    }

    DisposableEffect(key1 = Unit) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    allGranted = ctx.checkAllGranted(perms)

                    if (!allGranted) {
                        permLauncher.launch(perms.toTypedArray())
                        if (!allGranted) {
                            throw Exception("deal with not being granted media permissions later")
                        }
                    }
                }

                else -> {

                }
            }
        }

        lc.lifecycle.addObserver(obs)
        onDispose {
            lc.lifecycle.removeObserver(obs)
        }
    }

    content.invoke()
}