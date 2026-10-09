package com.tonoyama.pocketmemo

import android.content.Context
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

data class Memo(
    val id: String,
    var title: String,
    var text: String,
    var color: String,
    var pinned: Boolean,
    /** Photo file name in app storage, "pattern:<key>" for a built-in pattern, or "" for none. */
    var bg: String,
    val createdAt: Long,
    var updatedAt: Long,
    /** Sticky note text size: "s", "m" or "l". */
    var textSize: String = "m",
    /** Sticky note text color: "dark" or "light". */
    var textColor: String = "dark",
) {
    /** A memo with neither title nor body is not kept. */
    val isEmpty: Boolean
        get() = title.isBlank() && text.isBlank()

    /** Body joined into one line for list previews. */
    val preview: String
        get() = text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    /** Body with line breaks kept, for the sticky note widget. */
    val body: String
        get() = text.trim()

    companion object {
        fun create(): Memo {
            val now = System.currentTimeMillis()
            return Memo(
                id = "m" + UUID.randomUUID().toString().replace("-", "").take(12),
                title = "", text = "", color = "", pinned = false, bg = "",
                createdAt = now, updatedAt = now,
            )
        }
    }
}

/** Memos live in one small JSON file in the app's private storage. */
object MemoStore {
    private const val FILE = "memos.json"
    private var cache: MutableList<Memo>? = null

    @Synchronized
    fun all(context: Context): List<Memo> = load(context).map { it.copy() }

    @Synchronized
    fun get(context: Context, id: String): Memo? = load(context).firstOrNull { it.id == id }?.copy()

    @Synchronized
    fun save(context: Context, memo: Memo) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == memo.id }
        if (i >= 0) list[i] = memo.copy() else list.add(memo.copy())
        write(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val list = load(context)
        val memo = list.firstOrNull { it.id == id } ?: return
        MemoImages.delete(context, memo.bg)
        list.remove(memo)
        write(context, list)
    }

    private fun load(context: Context): MutableList<Memo> {
        cache?.let { return it }
        val file = File(context.filesDir, FILE)
        val list = mutableListOf<Memo>()
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    var title = o.optString("title", "")
                    var text = o.optString("text", "")
                    if (!o.has("title")) {
                        // Version 1.1 kept the title as the first line of the text.
                        val trimmed = text.trim()
                        val cut = trimmed.indexOf('\n')
                        title = if (cut < 0) trimmed else trimmed.substring(0, cut).trim()
                        text = if (cut < 0) "" else trimmed.substring(cut + 1).trim()
                    }
                    list.add(
                        Memo(
                            id = o.getString("id"),
                            title = title,
                            text = text,
                            color = o.optString("color", ""),
                            pinned = o.optBoolean("pinned", false),
                            bg = o.optString("bg", ""),
                            createdAt = o.optLong("createdAt", 0L),
                            updatedAt = o.optLong("updatedAt", 0L),
                            textSize = o.optString("textSize", "m"),
                            textColor = o.optString("textColor", "dark"),
                        )
                    )
                }
            } catch (e: Exception) {
                // A damaged file is kept aside instead of being overwritten.
                file.renameTo(File(context.filesDir, "memos-damaged-${System.currentTimeMillis()}.json"))
            }
        }
        cache = list
        return list
    }

    private fun write(context: Context, list: List<Memo>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("title", m.title)
                    .put("text", m.text)
                    .put("color", m.color)
                    .put("pinned", m.pinned)
                    .put("bg", m.bg)
                    .put("createdAt", m.createdAt)
                    .put("updatedAt", m.updatedAt)
                    .put("textSize", m.textSize)
                    .put("textColor", m.textColor)
            )
        }
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(context.filesDir, FILE))
    }
}

object MemoFormat {
    val COLORS = listOf("", "clear", "red", "yellow", "green", "blue")
    private val COLOR_NAMES = mapOf(
        "" to "色なし", "clear" to "透明", "red" to "赤", "yellow" to "黄", "green" to "緑", "blue" to "青"
    )

    /** Title and body sizes in sp for the sticky note. */
    fun textSizes(key: String): Pair<Float, Float> = when (key) {
        "s" -> 15f to 13f
        "l" -> 22f to 20f
        else -> 18f to 16f
    }
    private val WEEKDAYS = arrayOf("日", "月", "火", "水", "木", "金", "土")

    fun colorName(key: String) = COLOR_NAMES[key] ?: "色なし"

    fun tagColor(context: Context, key: String): Int? = when (key) {
        "red" -> ContextCompat.getColor(context, R.color.tag_red)
        "yellow" -> ContextCompat.getColor(context, R.color.tag_yellow)
        "green" -> ContextCompat.getColor(context, R.color.tag_green)
        "blue" -> ContextCompat.getColor(context, R.color.tag_blue)
        else -> null
    }

    fun noteBackground(key: String): Int = when (key) {
        "red" -> R.drawable.bg_note_red
        "yellow" -> R.drawable.bg_note_yellow
        "green" -> R.drawable.bg_note_green
        "blue" -> R.drawable.bg_note_blue
        "clear" -> R.drawable.bg_note_clear
        else -> R.drawable.bg_note_plain
    }

    fun shortDate(ts: Long): String {
        val d = Calendar.getInstance().apply { timeInMillis = ts }
        val n = Calendar.getInstance()
        val sameYear = d.get(Calendar.YEAR) == n.get(Calendar.YEAR)
        return when {
            sameYear && d.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR) ->
                "%d:%02d".format(d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE))
            sameYear -> "${d.get(Calendar.MONTH) + 1}月${d.get(Calendar.DAY_OF_MONTH)}日"
            else -> "${d.get(Calendar.YEAR)}/${d.get(Calendar.MONTH) + 1}/${d.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    fun longDate(ts: Long): String {
        val d = Calendar.getInstance().apply { timeInMillis = ts }
        return "%d年%d月%d日(%s) %d:%02d".format(
            d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH),
            WEEKDAYS[d.get(Calendar.DAY_OF_WEEK) - 1], d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE)
        )
    }

    fun charCount(text: String): Int = text.codePointCount(0, text.length) - text.count { it == '\n' }
}
