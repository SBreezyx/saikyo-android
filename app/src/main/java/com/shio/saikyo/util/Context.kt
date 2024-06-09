package com.shio.saikyo.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import com.shio.saikyo.OverlayActivity

fun Context.startOverlayActivity(
    intent: Intent = Intent().apply {
        setClass(this@startOverlayActivity, OverlayActivity::class.java)
        flags = flags or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
    }
) = startActivity(intent)

fun Context.getWindowManager() = getSystemService(WindowManager::class.java)

fun Context.getAudioManager() = getSystemService(AudioManager::class.java)

@SuppressLint("ServiceCast")
fun Context.getVibratorManager(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    getSystemService(VibratorManager::class.java).defaultVibrator
} else {
    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as Vibrator
}