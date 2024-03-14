package com.shio.saikyo

import android.app.Activity
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.shio.saikyo.i18n.LangCode
import com.shio.saikyo.ocr.Sz
import com.shio.saikyo.ocr.doOcr
import com.shio.saikyo.notif.LIVE_DICT_NOTIF
import com.shio.saikyo.parser.GeminiParser
import com.shio.saikyo.parser.IParser
import com.shio.saikyo.ui.OverlayControls
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel


data class WindowMetricsCompat(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int
)

private fun Intent.getIntentExtra(name: String) = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(name, Intent::class.java)
    else -> getParcelableExtra(name)
}

private fun currentWindowMetricsCompat(
    ctx: Context, wm: WindowManager = ctx.getSystemService(WindowManager::class.java)
): WindowMetricsCompat {
    val metrics = wm.currentWindowMetrics
    return WindowMetricsCompat(
        widthPixels = metrics.bounds.width(),
        heightPixels = metrics.bounds.height(),
        densityDpi = ctx.resources.configuration.densityDpi
    )
}

private fun LayoutParams.animationsOn() = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        this.setCanPlayMoveAnimation(true)
    }

    else -> {
        val PRIVATE_NO_MOVE = 1 shl 6
        var currentFlags = this.javaClass.getField("privateFlags").getInt(this)
        currentFlags = currentFlags.and(PRIVATE_NO_MOVE.inv())

        // classic OoP non-sense
        this.javaClass.getField("privateFlags").set(this, currentFlags);
    }
}

private fun LayoutParams.animationsOff() = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        this.setCanPlayMoveAnimation(false)
    }

    else -> { // classic OoP non-sense
        val PRIVATE_NO_MOVE = 1 shl 6
        var currentFlags = this.javaClass.getField("privateFlags").getInt(this)
        currentFlags = currentFlags or PRIVATE_NO_MOVE
        this.javaClass.getField("privateFlags").set(this, currentFlags)
    }
}

private suspend fun takeScreenshot(vdisp: VirtualDisplay, metrics: WindowMetricsCompat): Bitmap {
    val frameAvailable = Channel<Unit>(0, BufferOverflow.DROP_LATEST)
    val imgReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2).apply {
        setOnImageAvailableListener({ frameAvailable.trySend(Unit) }, null)
    }
    vdisp.surface = imgReader.surface

    frameAvailable.receive()
    val img = imgReader.acquireLatestImage()

    val bmp = img.use {
        val rgbaData = it.planes[0]
        val pixelStride = rgbaData.pixelStride
        val rowStride = rgbaData.rowStride

        Bitmap.createBitmap(rowStride / pixelStride, it.height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(rgbaData.buffer)
        }
    }

    vdisp.surface = null
    imgReader.close()

    return bmp
}


abstract class ServiceX : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner {
    private val lifecycleReg = LifecycleRegistry(this)
    private val ssCtrl = SavedStateRegistryController.create(this)
    private val viewModels = ViewModelStore()
    private val backPresses = OnBackPressedDispatcher()

    override val lifecycle: Lifecycle
        get() = lifecycleReg

    override val savedStateRegistry: SavedStateRegistry
        get() = ssCtrl.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModels

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = backPresses

    override fun onCreate() {
        super.onCreate()
        ssCtrl.performAttach()
        ssCtrl.performRestore(null)
        lifecycleReg.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleReg.currentState = Lifecycle.State.STARTED
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModels.clear()
        lifecycleReg.currentState = Lifecycle.State.DESTROYED
    }

}

class LiveDictService : ServiceX() {
    private lateinit var mpToken: MediaProjection
    private lateinit var win: Context
    private lateinit var metrics: WindowMetricsCompat
    private lateinit var vdisp: VirtualDisplay
    private lateinit var ui: ComposeView
    private lateinit var lp: LayoutParams
    private lateinit var parserClient: IParser

    private lateinit var ocrClient: TextRecognizer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // IMPORTANT: the foreground service MUST be started BEFORE getting the MediaProjection token
        ServiceCompat.startForeground(
            this,
            42,
            // TODO: make sure to request permission to display notifications from the main UI
            NotificationCompat.Builder(this, LIVE_DICT_NOTIF)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentTitle(resources.getString(R.string.app_name))
                .setContentText("Live dictionary is running.")
//                .addAction() TODO
//                .setContentIntent() TODO
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        // IMPORTANT: the MediaProjection token MUST be gotten BEFORE initializing the UI
        mpToken = run {
            val resultCode = intent!!.getIntExtra("resultCode", Activity.RESULT_OK)
            val resultIntent = intent.getIntentExtra("resultIntent")!!
            val mpMan = getSystemService(MediaProjectionManager::class.java)

            return@run mpMan.getMediaProjection(resultCode, resultIntent)
        }

        win = createDisplayContext(
            getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        ).createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null).apply {
            this.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    // reconfiguration means the display dimensions could have changed.
                    // need to reconfigure the virtual display if that's so.
                    metrics = currentWindowMetricsCompat(win)
                    vdisp.resize(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
                }

                override fun onLowMemory() {
                    /* unused */
                }

                override fun onTrimMemory(level: Int) {
                    /* unused */
                }
            })
        }

        val wm = win.getSystemService(WindowManager::class.java)
        val am = win.getSystemService(AudioManager::class.java)

        metrics = currentWindowMetricsCompat(win, wm)

        vdisp = let { self ->
            mpToken.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    // without a projection, this service cannot continue to function.
                    self.stopSelf()
                }
            }, null)

            mpToken.createVirtualDisplay(
                "OCR Screen capture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, // initially null because we will read the most current frame later
                null,
                null
            )
        }

        lp = LayoutParams().apply {
            this.type = LayoutParams.TYPE_APPLICATION_OVERLAY
            this.format = PixelFormat.TRANSLUCENT
            this.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    // TODO: when eventually typing into the window, this may need to be dynamically updated
                    LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    LayoutParams.FLAG_NOT_FOCUSABLE

            this.width = LayoutParams.WRAP_CONTENT
            this.height = LayoutParams.WRAP_CONTENT

            this.animationsOff()
        }

        ui = ComposeView(win).apply {
            setViewTreeLifecycleOwner(this@LiveDictService)
            setViewTreeSavedStateRegistryOwner(this@LiveDictService)
            setViewTreeViewModelStoreOwner(this@LiveDictService)
            setViewTreeOnBackPressedDispatcherOwner(this@LiveDictService)

            setContent {
                MaterialTheme {
                    val afReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()
                    OverlayControls(
                        onMinimize = {
                            lp.apply {
                                width = LayoutParams.WRAP_CONTENT; height = LayoutParams.WRAP_CONTENT
//                                this.animationsOff()
                            }
                            wm.updateViewLayout(this, lp)
                            am.abandonAudioFocusRequest(afReq)
                        },
                        onMaximize = {
                            lp.apply {
                                width = LayoutParams.MATCH_PARENT; height = LayoutParams.MATCH_PARENT
//                                this.animationsOn()
                            }
                            wm.updateViewLayout(this, lp)
                            am.requestAudioFocus(afReq)
                        },
                        { delta ->
                            lp.apply { x += delta.x.toInt(); y += delta.y.toInt() }
                            wm.updateViewLayout(this, lp)
                        },
                        {
                            takeScreenshot(vdisp, metrics)
                        },
                        { bmp: Bitmap, desired: LangCode, sz: Sz -> doOcr(ocrClient, bmp, desired, sz) },
                        { lang: LangCode, text: String -> parserClient.parse(lang, text) }
                    )
                }
            }
        }
        wm.addView(ui, lp)

        ocrClient = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

        parserClient = GeminiParser()

        // TODO: double-check the return value of the service for lifetime and process management
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // not a bindable service
    }

    override fun onDestroy() {
        super.onDestroy()

        vdisp.release()
    }

}
