package com.tonoyama.pocketmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.TaskStackBuilder

/** Home screen sticky note showing one chosen memo. */
class StickyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    companion object {
        private const val PREFS = "sticky_widgets"
        private fun key(widgetId: Int) = "w_$widgetId"
        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun bind(context: Context, widgetId: Int, memoId: String) {
            prefs(context).edit().putString(key(widgetId), memoId).apply()
            update(context, AppWidgetManager.getInstance(context), widgetId)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StickyWidget::class.java))
            ids.forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val memoId = prefs(context).getString(key(widgetId), null)
            val memo = memoId?.let { MemoStore.get(context, it) }
            val views = RemoteViews(context.packageName, R.layout.widget_sticky)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (memo != null && memo.text.isNotBlank()) {
                views.setInt(R.id.widget_root, "setBackgroundResource", MemoFormat.noteBackground(memo.color))
                views.setTextViewText(R.id.w_title, memo.title.ifEmpty { "（タイトルなし）" })
                views.setTextViewText(R.id.w_body, memo.body)
                val open = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(EditorActivity.intent(context, memo.id))
                    .getPendingIntent(widgetId, flags)
                views.setOnClickPendingIntent(R.id.widget_root, open)
            } else {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_note_plain)
                if (memoId != null) {
                    views.setTextViewText(R.id.w_title, "このメモは削除されました")
                    views.setTextViewText(R.id.w_body, "タップして別のメモを選べます")
                } else {
                    views.setTextViewText(R.id.w_title, "付箋メモ")
                    views.setTextViewText(R.id.w_body, "タップして貼るメモを選んでください")
                }
                val pick = Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, widgetId, pick, flags)
                )
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
