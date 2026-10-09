package com.tonoyama.pocketmemo

import android.content.Context
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

data class CheckItem(val id: String, val text: String, val done: Boolean) {
    companion object {
        fun create(text: String = "") = CheckItem(UUID.randomUUID().toString().replace("-", "").take(10), text, false)
    }
}

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
    /** "note" for free text, "check" for a checklist. */
    var type: String = "note",
    var items: List<CheckItem> = emptyList(),
    /** When the memo was moved to the trash, or 0 while it is in use. */
    var deletedAt: Long = 0L,
) {
    val isChecklist: Boolean
        get() = type == "check"

    val inTrash: Boolean
        get() = deletedAt > 0L

    /** A memo with neither title nor content is not kept. */
    val isEmpty: Boolean
        get() = title.isBlank() && text.isBlank() && items.none { it.text.isNotBlank() }

    private val filledItems: List<CheckItem>
        get() = items.filter { it.text.isNotBlank() }

    /** Content joined into one line for list previews. */
    val preview: String
        get() = if (isChecklist) {
            filledItems.joinToString("  ") { (if (it.done) "☑ " else "☐ ") + it.text.trim() }
        } else {
            text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        }

    /** Content with line breaks kept, for the sticky note and for sharing. */
    val body: String
        get() = if (isChecklist) {
            filledItems.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text.trim() }
        } else {
            text.trim()
        }

    /** Everything searchable, in one string. */
    val searchText: String
        get() = title + "\n" + text + "\n" + items.joinToString("\n") { it.text }

    val shareText: String
        get() = listOf(title.trim(), body).filter { it.isNotEmpty() }.joinToString("\n\n")

    companion object {
        fun create(): Memo {
            val now = System.currentTimeMillis()
            return Memo(
                id = "m" + UUID.randomUUID().toString().replace("-", "").take(12),
                title = "", text = "", color = "", pinned = false, bg = "",
                createdAt = now, updatedAt = now,
            )
        }

        fun fromJson(o: JSONObject): Memo {
            var title = o.optString("title", "")
            var text = o.optString("text", "")
            if (!o.has("title")) {
                // Version 1.1 kept the title as the first line of the text.
                val trimmed = text.trim()
                val cut = trimmed.indexOf('\n')
                title = if (cut < 0) trimmed else trimmed.substring(0, cut).trim()
                text = if (cut < 0) "" else trimmed.substring(cut + 1).trim()
            }
            val items = mutableListOf<CheckItem>()
            o.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val it = arr.getJSONObject(i)
                    items.add(CheckItem(it.optString("id", CheckItem.create().id), it.optString("text", ""), it.optBoolean("done", false)))
                }
            }
            return Memo(
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
                type = o.optString("type", "note"),
                items = items,
                deletedAt = o.optLong("deletedAt", 0L),
            )
        }
    }

    fun toJson(): JSONObject {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("text", text)
            .put("color", color)
            .put("pinned", pinned)
            .put("bg", bg)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("textSize", textSize)
            .put("textColor", textColor)
            .put("type", type)
            .put("items", arr)
            .put("deletedAt", deletedAt)
    }
}

/** Memos live in one small JSON file in the app's private storage. */
object MemoStore {
    private const val FILE = "memos.json"
    const val TRASH_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private var cache: MutableList<Memo>? = null

    /** Memos in use (not in the trash). */
    @Synchronized
    fun active(context: Context): List<Memo> = load(context).filter { !it.inTrash }.map { it.copy() }

    @Synchronized
    fun trashed(context: Context): List<Memo> = load(context).filter { it.inTrash }.map { it.copy() }

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
    fun moveToTrash(context: Context, id: String) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(deletedAt = System.currentTimeMillis())
        write(context, list)
    }

    @Synchronized
    fun restore(context: Context, id: String) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(deletedAt = 0L)
        write(context, list)
    }

    /** Makes an independent copy of a memo (its photo included) and returns it. */
    @Synchronized
    fun duplicate(context: Context, id: String): Memo? {
        val src = load(context).firstOrNull { it.id == id } ?: return null
        val fresh = Memo.create()
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = fresh.id,
            title = if (src.title.isBlank()) "" else src.title.trim() + "（コピー）",
            pinned = false,
            bg = MemoImages.copy(context, src.bg, fresh.id),
            createdAt = now,
            updatedAt = now,
            items = src.items.map { CheckItem.create(it.text).copy(done = it.done) },
            deletedAt = 0L,
        )
        val list = load(context)
        list.add(copy)
        write(context, list)
        return copy.copy()
    }

    /** Removes a memo and its photo for good. */
    @Synchronized
    fun delete(context: Context, id: String) {
        val list = load(context)
        val memo = list.firstOrNull { it.id == id } ?: return
        MemoImages.delete(context, memo.bg)
        list.remove(memo)
        write(context, list)
    }

    @Synchronized
    fun emptyTrash(context: Context) {
        load(context).filter { it.inTrash }.forEach { delete(context, it.id) }
    }

    /** Deletes memos that have been in the trash longer than [TRASH_DAYS]. */
    @Synchronized
    fun purgeOldTrash(context: Context) {
        val limit = System.currentTimeMillis() - TRASH_DAYS * DAY_MS
        load(context).filter { it.inTrash && it.deletedAt < limit }.forEach { delete(context, it.id) }
    }

    fun daysLeft(memo: Memo): Int {
        val left = memo.deletedAt + TRASH_DAYS * DAY_MS - System.currentTimeMillis()
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(0)
    }

    /** Adds memos from a backup. A memo already here is replaced only by a newer copy. Returns how many changed. */
    @Synchronized
    fun merge(context: Context, incoming: List<Memo>): Int {
        val list = load(context)
        var changed = 0
        incoming.forEach { m ->
            val i = list.indexOfFirst { it.id == m.id }
            if (i < 0) {
                list.add(m); changed++
            } else if (list[i].updatedAt < m.updatedAt) {
                list[i] = m; changed++
            }
        }
        if (changed > 0) write(context, list)
        return changed
    }

    fun toJsonArray(list: List<Memo>): JSONArray = JSONArray().apply { list.forEach { put(it.toJson()) } }

    fun parse(json: String): List<Memo> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { Memo.fromJson(arr.getJSONObject(it)) }
    }

    private fun load(context: Context): MutableList<Memo> {
        cache?.let { return it }
        val file = File(context.filesDir, FILE)
        val list = mutableListOf<Memo>()
        if (file.exists()) {
            try {
                list.addAll(parse(file.readText()))
            } catch (e: Exception) {
                // A damaged file is kept aside instead of being overwritten.
                file.renameTo(File(context.filesDir, "memos-damaged-${System.currentTimeMillis()}.json"))
            }
        }
        cache = list
        return list
    }

    private fun write(context: Context, list: List<Memo>) {
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(toJsonArray(list).toString())
        tmp.renameTo(File(context.filesDir, FILE))
    }
}

object MemoFormat {
    /** "" is white, the default. */
    val COLORS = listOf("", "red", "yellow", "green", "blue", "clear")
    private val COLOR_NAMES = mapOf(
        "" to "白", "red" to "赤", "yellow" to "黄", "green" to "緑", "blue" to "青", "clear" to "透明"
    )

    /** Text size levels 1–7; the old small/medium/large are levels 3, 4 and 5. */
    val LEVEL_NAMES = arrayOf("極小", "より小", "小", "中", "大", "より大", "特大")
    private val EDITOR_BODY = floatArrayOf(12f, 13.5f, 15f, 17f, 21f, 24f, 28f)
    private val NOTE_BODY = floatArrayOf(10f, 11.5f, 13f, 16f, 20f, 23f, 27f)

    fun level(key: String): Int = when (key) {
        "s" -> 3
        "m" -> 4
        "l" -> 5
        else -> key.toIntOrNull()?.coerceIn(1, 7) ?: 4
    }

    fun levelName(key: String): String = LEVEL_NAMES[level(key) - 1]

    /** Title and body sizes in sp for the memo editor. */
    fun editorSizes(key: String): Pair<Float, Float> = EDITOR_BODY[level(key) - 1].let { it + 3f to it }

    /** Title and body sizes in sp for the sticky note. */
    fun textSizes(key: String): Pair<Float, Float> = NOTE_BODY[level(key) - 1].let { it + 2f to it }

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
