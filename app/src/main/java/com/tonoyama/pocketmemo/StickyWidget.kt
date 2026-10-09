package com.tonoyama.pocketmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.TaskStackBuilder
import androidx.core.graphics.ColorUtils
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
        appWidgetIds.forEach {
            edit.remove(key(it))
            edit.remove(sizeKey(it))
        }
        edit.apply()
    }

    companion object {
        private const val PREFS = "sticky_widgets"
        private val ROOT: Int = android.R.id.background

        /** Widget bitmaps travel between apps, so they are kept under about 512×512 pixels. */
        private const val MAX_PIXELS = 512f * 512f

        private fun key(widgetId: Int) = "w_$widgetId"
        private fun sizeKey(widgetId: Int) = "s_$widgetId"
        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun bind(context: Context, widgetId: Int, memoId: String, size: String? = null) {
            val p = prefs(context)
            val edit = p.edit().putString(key(widgetId), memoId)
            if (size != null) edit.putString(sizeKey(widgetId), size)
            else if (!p.contains(sizeKey(widgetId))) edit.putString(sizeKey(widgetId), "m")
            edit.apply()
            update(context, AppWidgetManager.getInstance(context), widgetId)
        }

        /** Sticky notes currently showing this memo, in the order they were placed. */
        fun widgetsFor(context: Context, memoId: String): List<Int> {
            val manager = AppWidgetManager.getInstance(context)
            val p = prefs(context)
            return manager.getAppWidgetIds(ComponentName(context, StickyWidget::class.java))
                .filter { p.getString(key(it), null) == memoId }
                .sorted()
        }

        /** Text size of one sticky note: "s", "m" or "l". */
        fun sizeOf(context: Context, widgetId: Int, fallback: String = "m"): String =
            prefs(context).getString(sizeKey(widgetId), null) ?: fallback

        fun setSize(context: Context, widgetId: Int, size: String) {
            prefs(context).edit().putString(sizeKey(widgetId), size).apply()
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

            views.setViewVisibility(R.id.w_list, View.GONE)
            views.setViewVisibility(R.id.w_open, View.GONE)
            views.setViewVisibility(R.id.w_header, View.VISIBLE)
            if (memo != null && memo.inTrash) {
                views.setInt(ROOT, "setBackgroundResource", R.drawable.bg_note_plain)
                views.setViewVisibility(R.id.w_bg, View.GONE)
                views.setViewVisibility(R.id.w_title, View.VISIBLE)
                views.setViewVisibility(R.id.w_body, View.VISIBLE)
                val ink = Backgrounds.textColor("dark")
                views.setTextColor(R.id.w_title, ink)
                views.setTextColor(R.id.w_body, ink)
                views.setTextViewText(R.id.w_title, "このメモはゴミ箱にあります")
                views.setTextViewText(R.id.w_body, "タップしてゴミ箱を開くと、元に戻せます")
                val openTrash = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(Intent(context, TrashActivity::class.java))
                    .getPendingIntent(widgetId, flags)
                views.setOnClickPendingIntent(ROOT, openTrash)
            } else if (memo != null && !memo.isEmpty) {
                views.setInt(ROOT, "setBackgroundResource", MemoFormat.noteBackground(memo.color))

                val textColor = Backgrounds.textColor(memo.textColor)
                // Older notes had no size of their own; they keep the memo's earlier setting.
                val (titleSp, bodySp) = MemoFormat.textSizes(sizeOf(context, widgetId, memo.textSize))
                val title = memo.title.trim()
                views.setViewVisibility(R.id.w_title, if (title.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.w_title, title)
                views.setTextColor(R.id.w_title, textColor)
                views.setTextViewTextSize(R.id.w_title, TypedValue.COMPLEX_UNIT_SP, titleSp)
                views.setViewVisibility(R.id.w_body, if (memo.body.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.w_body, memo.body)
                views.setTextColor(R.id.w_body, textColor)
                views.setTextViewTextSize(R.id.w_body, TypedValue.COMPLEX_UNIT_SP, bodySp)
                if (memo.isChecklist && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Tickable rows on Android 12+; older versions show the text list above.
                    setChecklist(context, views, widgetId, memo, textColor, bodySp)
                }

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
                views.setOnClickPendingIntent(R.id.w_title, open)
                // A checklist fills the note with tickable rows, so it gets its own "open" button.
                val listShown = memo.isChecklist && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    memo.items.any { it.text.isNotBlank() }
                if (listShown) {
                    views.setViewVisibility(R.id.w_open, View.VISIBLE)
                    views.setInt(R.id.w_open, "setColorFilter", textColor)
                    views.setOnClickPendingIntent(R.id.w_open, open)
                }
                views.setViewVisibility(R.id.w_header, if (title.isEmpty() && !listShown) View.GONE else View.VISIBLE)
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

        @RequiresApi(Build.VERSION_CODES.S)
        private fun setChecklist(context: Context, views: RemoteViews, widgetId: Int, memo: Memo, textColor: Int, bodySp: Float) {
            val filled = memo.items.filter { it.text.isNotBlank() }
            if (filled.isEmpty()) return
            val faded = ColorUtils.setAlphaComponent(textColor, 0x8C)
            val builder = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            filled.forEach { item ->
                val row = RemoteViews(context.packageName, R.layout.widget_check_item)
                row.setTextViewText(R.id.w_check, item.text.trim())
                row.setTextColor(R.id.w_check, if (item.done) faded else textColor)
                row.setTextViewTextSize(R.id.w_check, TypedValue.COMPLEX_UNIT_SP, bodySp)
                row.setCompoundButtonChecked(R.id.w_check, item.done)
                val fillIn = Intent()
                    .putExtra(CheckToggleReceiver.EXTRA_MEMO, memo.id)
                    .putExtra(CheckToggleReceiver.EXTRA_ITEM, item.id)
                row.setOnCheckedChangeResponse(R.id.w_check, RemoteViews.RemoteResponse.fromFillInIntent(fillIn))
                builder.addItem(item.id.hashCode().toLong(), row)
            }
            views.setRemoteAdapter(R.id.w_list, builder.build())
            val template = PendingIntent.getBroadcast(
                context, widgetId,
                Intent(context, CheckToggleReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.w_list, template)
            views.setViewVisibility(R.id.w_body, View.GONE)
            views.setViewVisibility(R.id.w_list, View.VISIBLE)
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
