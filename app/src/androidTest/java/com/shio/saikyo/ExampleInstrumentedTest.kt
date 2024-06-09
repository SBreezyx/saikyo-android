package com.shio.saikyo

import android.Manifest
import android.app.UiAutomation
import android.content.Intent
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun playground()  {
        // Context of the app under test.
        val smth = InstrumentationRegistry.getInstrumentation()

        val ctx = smth.targetContext
//        smth.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_MEDIA_VIDEO)
//        smth.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_MEDIA_AUDIO)
//        smth.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_MEDIA_IMAGES)
//        smth.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
//        smth.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.MANAGE_EXTERNAL_STORAGE)


//        smth.startActivitySync(Intent().apply {
//            action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
//        })
        val res = ctx.contentResolver

        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        res.query(
            uri,
            arrayOf(
                MediaStore.Files.FileColumns.VOLUME_NAME,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            ),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor!!.moveToNext()) {
                Log.i("TEST", (0 until cursor.columnCount).map {
                    "${cursor.getColumnName(it)} ${cursor.getString(it)}"
                }.joinToString(separator = "\n"))
            }
        }


//        print(ret)


    }
}