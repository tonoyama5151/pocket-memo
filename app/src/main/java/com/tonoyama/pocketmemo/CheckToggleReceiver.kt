package com.tonoyama.pocketmemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Ticks or unticks a checklist item straight from the sticky note (Android 12 and later). */
class CheckToggleReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_MEMO = "toggle_memo"
        const val EXTRA_ITEM = "toggle_item"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getStringExtra(EXTRA_MEMO) ?: return
        val itemId = intent.getStringExtra(EXTRA_ITEM) ?: return
        val memo = MemoStore.get(context, memoId) ?: return
        val current = memo.items.firstOrNull { it.id == itemId } ?: return
        val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, !current.done)
        memo.items = memo.items.map { if (it.id == itemId) it.copy(done = checked) else it }
        memo.updatedAt = System.currentTimeMillis()
        MemoStore.save(context, memo)
        StickyWidget.updateAll(context)
    }
}
