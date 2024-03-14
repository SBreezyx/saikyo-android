package com.shio.saikyo

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.shio.saikyo.notif.LIVE_DICT_NOTIF
import com.shio.saikyo.ui.UINavigation
import com.shio.saikyo.ui.theme.SaikyoTheme

class RequestOverlayPerm : ActivityResultContract<Unit, Boolean>() {
    private lateinit var ctx: Context

    override fun createIntent(context: Context, input: Unit): Intent {
        ctx = context
        return Intent().apply {
            this.action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            this.data = Uri.fromParts("package", context.packageName, "")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return Settings.canDrawOverlays(ctx)
    }

}

data class IntentLaunchers(
    val reqOverlayLauncher: () -> Unit,
    val startLiveDict: () -> Unit,
)

class UIActivity : ComponentActivity() {
    private lateinit var intentLaunchers: IntentLaunchers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = this.packageManager

        val foo = pm.queryIntentActivities(Intent().apply {
            this.action = Intent.ACTION_PROCESS_TEXT
            this.addCategory(Intent.CATEGORY_DEFAULT)
            this.type = "text/plain"
        }, PackageManager.MATCH_DEFAULT_ONLY)


        intentLaunchers = run {
            val mpMan = this.getSystemService(MediaProjectionManager::class.java)

//            val overlayLauncher = registerForActivityResult(RequestOverlayPerm()) {
//                // TODO: update settings with the result
//            }

            val mpLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    startForegroundService(Intent(this, LiveDictService::class.java).apply {
                        this.putExtra("resultCode", it.resultCode)
                        this.putExtra("resultIntent", it.data)
                    })

                    finish()
                } else {
                    // deal with this soon
                }
            }

            IntentLaunchers(
                reqOverlayLauncher = { /*overlayLauncher.launch(Unit)*/ },
                startLiveDict = { mpLauncher.launch(mpMan.createScreenCaptureIntent()) }
            )
        }

        // TODO: consider if this impacts start-up time and can be moved to a different thread
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(LIVE_DICT_NOTIF, "LiveDict", NotificationManager.IMPORTANCE_LOW).apply {
                // TODO: string resource the below
                description = "See if the live dictionary feature is running across your device"
            }
        ))

        enableEdgeToEdge()
        setContent {
            SaikyoTheme {
                UINavigation(intentLaunchers)
            }
        }
    }
}
