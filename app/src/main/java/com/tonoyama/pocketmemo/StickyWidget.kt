package com.tonoyama.pocketmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.TaskStackBuilder
import kotlin.math.min
import kotlin.math.sqrt

/** Home screen sticky note showing one chosen memo. */
class StickyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Resized: redraw the background at the new size.
        update(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    companion object {
        private const val PREFS = "sticky_widgets"
        private val ROOT: Int = android.R.id.background

        /** Widget bitmaps travel between apps, so they are kept under about 512×512 pixels. */
        private const val MAX_PIXELS = 512f * 512f

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

                val textColor = Backgrounds.textColor(memo.textColor)
                val (titleSp, bodySp) = MemoFormat.textSizes(memo.textSize)
                val title = memo.title.trim()
                views.setViewVisibility(R.id.w_title, if (title.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.w_title, title)
                views.setTextColor(R.id.w_title, textColor)
                views.setTextViewTextSize(R.id.w_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
                views.setViewVisibility(R.id.w_body, if (memo.body.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.w_body, memo.body)
                views.setTextColor(R.id.w_body, textColor)
                views.setTextViewTextSize(R.id.w_body, TypedValue.COMPLEX_UNIT_SP, bodySp)

                val picture = renderBackground(context, manager, widgetId, memo)
                if (picture != null) {
                    views.setImageViewBitmap(R.id.w_bg, picture)
                    views.setViewVisibility(R.id.w_bg, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.w_bg, View.GONE)
                }

                val open = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(EditorActivity.intent(context, memo.id))
                    .getPendingIntent(widgetId, flags)
                views.setOnClickPendingIntent(ROOT, open)
            } else {
                views.setInt(ROOT, "setBackgroundResource", R.drawable.bg_note_plain)
                views.setViewVisibility(R.id.w_bg, View.GONE)
                views.setViewVisibility(R.id.w_title, View.VISIBLE)
                views.setViewVisibility(R.id.w_body, View.VISIBLE)
                val ink = Backgrounds.textColor("dark")
                views.setTextColor(R.id.w_title, ink)
                views.setTextColor(R.id.w_body, ink)
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
                views.setOnClickPendingIntent(ROOT, PendingIntent.getActivity(context, widgetId, pick, flags))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun renderBackground(context: Context, manager: AppWidgetManager, widgetId: Int, memo: Memo) =
            if (memo.bg.isEmpty()) {
                null
            } else {
                val options = manager.getAppWidgetOptions(widgetId)
                var wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                var hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                if (wDp <= 0) wDp = 180
                if (hDp <= 0) hDp = 180
                val density = context.resources.displayMetrics.density
                val wPx = wDp * density
                val hPx = hDp * density
                val scale = min(1f, sqrt(MAX_PIXELS / (wPx * hPx)))
                Backgrounds.render(
                    context, memo.bg, memo.color, memo.textColor,
                    (wPx * scale).toInt(), (hPx * scale).toInt(), density * scale,
                )
            }
    }
}
