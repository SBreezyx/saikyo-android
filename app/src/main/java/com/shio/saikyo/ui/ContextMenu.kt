package com.shio.saikyo.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import com.shio.saikyo.MainActivity
import com.shio.saikyo.ProcessTextActivity
import com.shio.saikyo.R
import kotlin.math.min

val LocalTextProcessors = staticCompositionLocalOf { arrayOf<ContextMenuItem>() }

data class ContextMenuItem(
    val title: String,
    val id: Int = title.hashCode(), // TODO: proper ID generation
    val fn: ((String) -> Unit)? = null
) {
    companion object {
        val Copy = ContextMenuItem("", android.R.id.copy)
        val Paste = ContextMenuItem("", android.R.id.paste)
        val Cut = ContextMenuItem("", android.R.id.cut)
        val SelectAll = ContextMenuItem("", android.R.id.selectAll)

        fun Search(ctx: Context, title: String = ctx.resources.getString(R.string.search)) =
            ContextMenuItem(title) { selectedTxt ->
                ctx.startActivity(Intent().apply {
                    action = Intent.ACTION_PROCESS_TEXT

                    putExtra(Intent.EXTRA_PROCESS_TEXT, selectedTxt)
                    setClass(ctx, ProcessTextActivity::class.java)
                })
            }
    }
}

val DefaultId2TitleId = mapOf(
    android.R.id.copy to android.R.string.copy,
    android.R.id.paste to android.R.string.paste,
    android.R.id.cut to android.R.string.cut,
    android.R.id.selectAll to android.R.string.selectAll,
)


class ComposeContextMenu(
    /*
    * TODO: Optimise this class by consolidating the data structures.
    *
    **/
    private val vw: View,
    items: List<ContextMenuItem> = listOf(),
    maxNumVisible: Int = items.size
) : ActionMode.Callback2(), TextToolbar {
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    var text: String? = null
    private var contentRect: androidx.compose.ui.geometry.Rect? = null
    private var actionMode: ActionMode? = null

    private var itemList = mutableListOf<Pair<Int, String>>()
    private var fnMap = mutableMapOf<Int, ((String) -> Unit)?>()
    private val defaultFnMap = mutableMapOf<Int, (() -> Unit)?>(
        ContextMenuItem.Copy.id to null,
        ContextMenuItem.Paste.id to null,
        ContextMenuItem.Cut.id to null,
        ContextMenuItem.SelectAll.id to null,
    )
    private var maxNumVisible: Int = 0


    init {
        updateItems(items, maxNumVisible)
    }

    /*
    * Compose uses this function BOTH for:
    * - starting a new menu; AND
    * - updating the menu with the default actions.
    * Therefore, to know if the menu is simply being updated OR being recreated
    * requires looking at the value of @actionMode.
    * If it is null, then we need to create the menu.
    * Otherwise, this is an update.
    * */
    override fun showMenu(
        rect: androidx.compose.ui.geometry.Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        contentRect = rect

        defaultFnMap[ContextMenuItem.Copy.id] = onCopyRequested
        defaultFnMap[ContextMenuItem.Paste.id] = onPasteRequested
        defaultFnMap[ContextMenuItem.Cut.id] = onCutRequested
        defaultFnMap[ContextMenuItem.SelectAll.id] = onSelectAllRequested

        if (actionMode == null) {
            actionMode = vw.startActionMode(this, ActionMode.TYPE_FLOATING)
            status = TextToolbarStatus.Shown
        } else {
            actionMode!!.invalidate()
        }

    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        // TODO: remove code duplication in this function

        menu.clear()

        val numVisible = min(itemList.size, maxNumVisible)

        for (i in 0 until numVisible) {
            val (id, title) = itemList[i]

            if (!(fnMap[id] == null && defaultFnMap[id] == null)) {
                val titleRes = DefaultId2TitleId[id]

                if (titleRes == null) {
                    menu.add(Menu.NONE, id, i, title)
                } else {
                    menu.add(Menu.NONE, id, i, titleRes).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
            } else {
                // don't display this item for this menu -- we may display it at a later time
                // (note: this is a result of Compose's selection API being under-developed.
            }
        }

        if (itemList.size > numVisible) {
            // TODO: make this a resource
            val overflow = menu.addSubMenu("More")
            for (i in maxNumVisible until itemList.size) {
                val (id, title) = itemList[i]
                val titleRes = DefaultId2TitleId[id]

                if (!(fnMap[id] == null && defaultFnMap[id] == null)) {

                    if (titleRes == null) {
                        overflow.add(Menu.NONE, id, i, title)
                    } else {
                        overflow.add(Menu.NONE, id, i, titleRes).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                    }
                } else {
                    // don't display this item for this menu -- we may display it at a later time
                    // (note: this is a result of Compose's selection API being under-developed.
                }
            }
        }

        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val itemId = item.itemId
        val fn = fnMap[itemId]
        var succeeded = true

        if (fn != null) {
            fn(text!!)
            mode.finish()
        } else {
            // try a default action
            val defaultFn = defaultFnMap[itemId]
            if (defaultFn != null) {
                defaultFn()
                mode.finish()
            } else {
                succeeded = false
            }
        }

        return succeeded
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        outRect.set(contentRect!!.toAndroidRect())
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        text = null
        contentRect = null

        // it possible for the actionMode to be destroyed without Compose realising
        // e.g. if navigating starting a new activity
        actionMode?.finish()
        actionMode = null
//        updateItems()
    }

    fun updateItems(items: List<ContextMenuItem>, maxVisible: Int = items.size) {
        itemList.clear()
        fnMap.clear()

        maxNumVisible = maxVisible
        for ((title, id, fn) in items) {
            itemList.add(id to title)
            fnMap[id] = fn
        }
    }

    fun updateItems(vararg items: ContextMenuItem, maxVisible: Int = items.size) = updateItems(
        items.toList(),
        maxVisible
    )
}

data class ContextMenu(
    val items: List<ContextMenuItem>,
    val maxNumVisible: Int = items.size,
) {
    companion object {
        fun from(vararg items: ContextMenuItem, maxVisible: Int = items.size) = ContextMenu(
            items.toList(), maxVisible
        )
    }
}
