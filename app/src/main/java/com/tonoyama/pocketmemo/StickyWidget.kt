package com.tonoyama.pocketmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
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
        private val ROOT: Int = android.R.id.background
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

            if (memo != null && !memo.isEmpty) {
                views.setInt(ROOT, "setBackgroundResource", MemoFormat.noteBackground(memo.color))

                val title = memo.title.trim()
                views.setViewVisibility(R.id.w_title, if (title.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.w_title, title)
                views.setViewVisibility(R.id.w_body, if (memo.body.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.w_body, memo.body)

                val photo = MemoImages.load(context, memo.bg)
                if (photo != null) {
                    views.setImageViewBitmap(R.id.w_bg, photo)
                    views.setViewVisibility(R.id.w_bg, View.VISIBLE)
                    views.setInt(R.id.w_content, "setBackgroundColor", MemoFormat.noteOverlay(context, memo.color))
                } else {
                    views.setViewVisibility(R.id.w_bg, View.GONE)
                    views.setInt(R.id.w_content, "setBackgroundColor", Color.TRANSPARENT)
                }

                val open = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(EditorActivity.intent(context, memo.id))
                    .getPendingIntent(widgetId, flags)
                views.setOnClickPendingIntent(ROOT, open)
            } else {
                views.setInt(ROOT, "setBackgroundResource", R.drawable.bg_note_plain)
                views.setViewVisibility(R.id.w_bg, View.GONE)
                views.setInt(R.id.w_content, "setBackgroundColor", Color.TRANSPARENT)
                views.setViewVisibility(R.id.w_title, View.VISIBLE)
                views.setViewVisibility(R.id.w_body, View.VISIBLE)
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
                    ROOT,
                    PendingIntent.getActivity(context, widgetId, pick, flags)
                )
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
