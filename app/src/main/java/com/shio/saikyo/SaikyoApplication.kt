package com.shio.saikyo

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.shio.saikyo.ai.connectToGemini
import com.shio.saikyo.db.MediaDb
import com.shio.saikyo.db.DictionaryDb
import com.shio.saikyo.ocr.MLKit
import com.shio.saikyo.ui.ContextMenuItem


class SaikyoApp : Application() {
    val db by lazy { DictionaryDb.getDatabase(this) }
    val mediaDb by lazy { MediaDb.getDatabase(this) }
    val ai by lazy { connectToGemini() }

    // TODO: replace this with Cloud Vision API
    val ocr by lazy { MLKit() }

    val textProcessors by lazy {
        val apps = mutableListOf<ContextMenuItem>()

        val resolved = packageManager.queryIntentActivities(Intent().apply {
            this.action = Intent.ACTION_PROCESS_TEXT
            this.addCategory(Intent.CATEGORY_DEFAULT)
            this.type = "text/plain"
        }, PackageManager.MATCH_DEFAULT_ONLY)

        val selfPkg = packageName
        for (res in resolved) {
            val actInfo = res.activityInfo
            if (actInfo.packageName != selfPkg) {
                val label = actInfo.loadLabel(packageManager).toString()
                val item = ContextMenuItem(title = label) { selectedTxt ->
                    startActivity(Intent().apply {
                        action = Intent.ACTION_PROCESS_TEXT
                        component = ComponentName(packageName, actInfo.name)
                        putExtra(Intent.EXTRA_PROCESS_TEXT, selectedTxt)
                    })
                }

                apps.add(item)
            } else {
                // nothing; filtering ourselves out
            }
        }

        return@lazy apps
    }

}