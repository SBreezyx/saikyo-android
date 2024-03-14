package com.shio.saikyo

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.hypot

class DraggableTouchListener(
    private val touchSlop: Int,
    private val longClickInterval: Int,
    private val onClick: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
) : View.OnTouchListener {
    private var pointerStartX = 0
    private var pointerStartY = 0

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                pointerStartX = motionEvent.rawX.toInt()
                pointerStartY = motionEvent.rawY.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = motionEvent.x - pointerStartX
                val deltaY = motionEvent.y - pointerStartY
                if (hypot(deltaX, deltaY) > touchSlop) {
                    onDrag(deltaX, deltaY)
                }
            }

            MotionEvent.ACTION_UP -> {
                onClick()
            }

        }

        return true
    }
}

abstract class AccessibleLifecycleService :
    AccessibilityService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleReg = LifecycleRegistry(this)
    private val ssCtrl = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleReg

    override val savedStateRegistry: SavedStateRegistry
        get() = ssCtrl.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        ssCtrl.performAttach()
        ssCtrl.performRestore(null)
        lifecycleReg.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleReg.currentState = Lifecycle.State.STARTED
    }

    override fun onDestroy() {
        super.onDestroy()

        lifecycleReg.currentState = Lifecycle.State.DESTROYED
    }
}

class AnywhereService : AccessibleLifecycleService() {
    override fun onServiceConnected() {
        super.onServiceConnected()

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lp = LayoutParams().apply {
            type = LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT

            // TODO: this may need to change depending on the IME
            flags = flags.or(LayoutParams.FLAG_NOT_FOCUSABLE)

            width = LayoutParams.WRAP_CONTENT
            height = LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP
        }
        val view = ComposeView(this).apply {
            val lifecycleOwner = this@AnywhereService

            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(
                    lifecycleOwner
                )
            )
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                Text(text = "HIIIII", modifier = Modifier.size(48.dp))
            }
        }

        wm.addView(view, lp)
    }

    override fun onAccessibilityEvent(ev: AccessibilityEvent?) {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        println(ev)
    }

    override fun onInterrupt() {
        TODO("Not yet implemented")
    }

}