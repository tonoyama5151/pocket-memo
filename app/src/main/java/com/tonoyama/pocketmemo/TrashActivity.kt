package com.tonoyama.pocketmemo

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Memos deleted in the last 30 days, which can be restored or removed for good. */
class TrashActivity : AppCompatActivity() {
    private lateinit var adapter: MemoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        adapter = MemoAdapter(
            showSections = false,
            dateText = { "あと${MemoStore.daysLeft(it)}日" },
        ) { memo -> askWhatToDo(memo) }
        val list = findViewById<RecyclerView>(R.id.trashList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.trashBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.trashEmpty).setOnClickListener {
            if (MemoStore.trashed(this).isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setMessage("ゴミ箱のメモをすべて完全に削除しますか？元に戻せなくなります。")
                .setNegativeButton("やめる", null)
                .setPositiveButton("すべて削除") { _, _ ->
                    MemoStore.emptyTrash(this)
                    StickyWidget.updateAll(this)
                    refresh()
                    Toast.makeText(this, "ゴミ箱を空にしました", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        MemoStore.purgeOldTrash(this)
        refresh()
    }

    private fun askWhatToDo(memo: Memo) {
        val name = memo.title.trim().ifEmpty { memo.preview.take(30) }
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setMessage("このメモをどうしますか？")
            .setNeutralButton("やめる", null)
            .setNegativeButton("完全に削除") { _, _ ->
                MemoStore.delete(this, memo.id)
                StickyWidget.updateAll(this)
                refresh()
                Toast.makeText(this, "完全に削除しました", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("元に戻す") { _, _ ->
                MemoStore.restore(this, memo.id)
                StickyWidget.updateAll(this)
                refresh()
                Toast.makeText(this, "元に戻しました", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun refresh() {
        val trashed = MemoStore.trashed(this).sortedByDescending { it.deletedAt }
        adapter.submit(trashed)
        findViewById<View>(R.id.trashNone).visibility = if (trashed.isEmpty()) View.VISIBLE else View.GONE
    }
}
