package com.tonoyama.pocketmemo

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Writes every memo (trash included) and its photo into one zip file, and reads it back. */
object Backup {
    private const val MEMOS = "memos.json"
    private const val MANIFEST = "manifest.json"
    private val SAFE_NAME = Regex("^[A-Za-z0-9._-]+$")

    /** Returns the number of memos written. */
    fun export(context: Context, uri: Uri): Int {
        val memos = MemoStore.all(context)
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(
                    JSONObject().put("app", "pocket-memo").put("version", 1)
                        .put("exportedAt", System.currentTimeMillis()).toString().toByteArray()
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(MEMOS))
                zip.write(MemoStore.toJsonArray(memos).toString().toByteArray())
                zip.closeEntry()

                memos.map { it.bg }.filter { it.isNotEmpty() && !Backgrounds.isPattern(it) }.distinct().forEach { name ->
                    val f = MemoImages.file(context, name)
                    if (f.exists()) {
                        zip.putNextEntry(ZipEntry("bg/$name"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: throw IllegalStateException("cannot open output")
        return memos.size
    }

    /** Returns the number of memos added or updated, or null when the file is not a Pocket Memo backup. */
    fun importFrom(context: Context, uri: Uri): Int? {
        var memos: List<Memo>? = null
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == MEMOS -> memos = MemoStore.parse(zip.readBytes().toString(Charsets.UTF_8))
                        name.startsWith("bg/") -> {
                            val file = name.removePrefix("bg/")
                            // Only plain file names are accepted, never paths.
                            if (SAFE_NAME.matches(file) && !MemoImages.file(context, file).exists()) {
                                MemoImages.file(context, file).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: return null
        val list = memos ?: return null
        val changed = MemoStore.merge(context, list)
        StickyWidget.updateAll(context)
        return changed
    }
}
