package com.tonoyama.pocketmemo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast

/** Actions offered both from the memo list and from the editor. */
object MemoActions {
    fun copyText(activity: Activity, memo: Memo) {
        if (memo.isEmpty) {
            Toast.makeText(activity, "コピーする内容がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("メモ", memo.shareText))
        // Android 13 and later show their own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(activity, "テキストをコピーしました", Toast.LENGTH_SHORT).show()
        }
    }

    /** Duplicates the memo and opens the copy. */
    fun duplicate(activity: Activity, memo: Memo) {
        if (memo.isEmpty) {
            Toast.makeText(activity, "複製する内容がありません", Toast.LENGTH_SHORT).show()
            return
        }
        MemoStore.save(activity, memo)
        val copy = MemoStore.duplicate(activity, memo.id) ?: return
        Toast.makeText(activity, "複製しました", Toast.LENGTH_SHORT).show()
        activity.startActivity(EditorActivity.intent(activity, copy.id))
    }

    fun share(activity: Activity, memo: Memo) {
        if (memo.isEmpty) {
            Toast.makeText(activity, "共有する内容がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, memo.shareText)
            if (memo.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, memo.title.trim())
        }
        activity.startActivity(Intent.createChooser(send, "メモを共有"))
    }
}
