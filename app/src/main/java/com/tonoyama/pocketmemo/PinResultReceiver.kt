package com.tonoyama.pocketmemo

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Called by the launcher after a sticky note was placed from the editor. */
class PinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val memoId = intent.getStringExtra(EditorActivity.EXTRA_ID) ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        StickyWidget.bind(context, widgetId, memoId)
        Toast.makeText(context, "ホーム画面に付箋を貼りました", Toast.LENGTH_SHORT).show()
    }
}
