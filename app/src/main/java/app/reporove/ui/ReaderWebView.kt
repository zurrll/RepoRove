package app.reporove.ui

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import org.json.JSONArray

/** Preserve WebView's selection/copy/handles and Callback2's floating-menu positioning. */
class ReaderWebView(context: Context) : WebView(context) {
    var translateSelection: (String) -> Unit = {}
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = super.startActionMode(wrap(callback))
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = super.startActionMode(wrap(callback), type)
    private fun wrap(original: ActionMode.Callback): ActionMode.Callback2 = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = original.onCreateActionMode(mode, menu).also { if (it) add(menu) }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { val result = original.onPrepareActionMode(mode, menu); add(menu); return result }
        private fun add(menu: Menu) { if (menu.findItem(TRANSLATE) == null) menu.add(Menu.NONE, TRANSLATE, Menu.NONE, "AI 翻译").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != TRANSLATE) return original.onActionItemClicked(mode, item)
            // Capture before finishing ActionMode or moving focus to a Compose sheet.
            evaluateJavascript("window.getSelection().toString()") { value ->
                val selected = runCatching { JSONArray("[$value]").getString(0) }.getOrDefault("")
                mode.finish(); if (selected.isNotBlank()) translateSelection(selected)
            }
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) = original.onDestroyActionMode(mode)
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) { if (original is ActionMode.Callback2) original.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect) }
    }
    companion object { private const val TRANSLATE = 0x525654 }
}
