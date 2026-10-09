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
    var text: String,
    var color: String,
    var pinned: Boolean,
    val createdAt: Long,
    var updatedAt: Long,
) {
    val title: String
        get() = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()

    /** Everything after the first line, joined into one line for list previews. */
    val preview: String
        get() = text.trim().lines().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    /** Everything after the first line with line breaks kept, for the sticky note widget. */
    val body: String
        get() = text.trim().lines().drop(1).joinToString("\n").trim()

    companion object {
        fun create(): Memo {
            val now = System.currentTimeMillis()
            return Memo("m" + UUID.randomUUID().toString().replace("-", "").take(12), "", "", false, now, now)
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
        if (list.removeAll { it.id == id }) write(context, list)
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
                    list.add(
                        Memo(
                            id = o.getString("id"),
                            text = o.optString("text", ""),
                            color = o.optString("color", ""),
                            pinned = o.optBoolean("pinned", false),
                            createdAt = o.optLong("createdAt", 0L),
                            updatedAt = o.optLong("updatedAt", 0L),
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
                    .put("text", m.text)
                    .put("color", m.color)
                    .put("pinned", m.pinned)
                    .put("createdAt", m.createdAt)
                    .put("updatedAt", m.updatedAt)
            )
        }
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(context.filesDir, FILE))
    }
}

object MemoFormat {
    val COLORS = listOf("", "red", "yellow", "green", "blue")
    private val COLOR_NAMES = mapOf("" to "色なし", "red" to "赤", "yellow" to "黄", "green" to "緑", "blue" to "青")
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
