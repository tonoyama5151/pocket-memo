package com.tonoyama.pocketmemo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: MemoAdapter
    private var query = ""

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@registerForActivityResult
        val app = applicationContext
        Thread {
            val result = runCatching { Backup.export(app, uri) }
            runOnUiThread {
                result.onSuccess { Toast.makeText(this, "${it}件のメモを書き出しました", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "書き出しに失敗しました", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val app = applicationContext
        Thread {
            val result = runCatching { Backup.importFrom(app, uri) }
            runOnUiThread {
                val count = result.getOrNull()
                when {
                    count == null -> Toast.makeText(this, "ポケットメモのバックアップファイルではないようです", Toast.LENGTH_LONG).show()
                    count == 0 -> Toast.makeText(this, "新しく読み込むメモはありませんでした", Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(this, "${count}件のメモを読み込みました", Toast.LENGTH_LONG).show()
                }
                refresh()
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = MemoAdapter(
            showSections = true,
            onLongClick = { memo, view -> showMemoMenu(memo, view) },
        ) { memo ->
            startActivity(EditorActivity.intent(this, memo.id))
        }
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<EditText>(R.id.search).doAfterTextChanged {
            query = it?.toString().orEmpty()
            refresh()
        }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(EditorActivity.intent(this, null))
        }
        val more = findViewById<ImageButton>(R.id.more)
        more.setOnClickListener { showMenu(more) }

        MemoStore.purgeOldTrash(this)
        // Redraw sticky notes so they pick up any change in how they look after an update.
        StickyWidget.updateAll(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "ゴミ箱")
        menu.menu.add(0, 2, 1, "バックアップを書き出す")
        menu.menu.add(0, 3, 2, "バックアップから読み込む")
        menu.menu.add(0, 4, 3, "表示テーマ")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, TrashActivity::class.java))
                2 -> {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.JAPAN).format(Date())
                    exportLauncher.launch("pocket-memo-$stamp.zip")
                }
                3 -> MaterialAlertDialogBuilder(this)
                    .setTitle("バックアップから読み込む")
                    .setMessage("書き出したファイルのメモをこのスマホに追加します。同じメモがある場合は、新しい方が残ります。")
                    .setNegativeButton("やめる", null)
                    .setPositiveButton("ファイルを選ぶ") { _, _ ->
                        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }
                    .show()
                4 -> chooseTheme()
            }
            true
        }
        menu.show()
    }

    private fun chooseTheme() {
        val current = Theme.OPTIONS.indexOfFirst { it.first == Theme.get(this) }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("表示テーマ")
            .setSingleChoiceItems(Theme.OPTIONS.map { it.second }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                Theme.set(this, Theme.OPTIONS[which].first)
            }
            .setNegativeButton("閉じる", null)
            .show()
    }

    /** Long-press menu on a memo in the list. */
    private fun showMemoMenu(memo: Memo, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "テキストをコピー")
        menu.menu.add(0, 2, 1, "複製を作る")
        menu.menu.add(0, 3, 2, "共有")
        menu.menu.add(0, 4, 3, "ゴミ箱に移す")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> MemoActions.copyText(this, memo)
                2 -> MemoActions.duplicate(this, memo)
                3 -> MemoActions.share(this, memo)
                4 -> {
                    MemoStore.moveToTrash(this, memo.id)
                    StickyWidget.updateAll(this)
                    refresh()
                    Toast.makeText(this, "ゴミ箱に移しました", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        menu.show()
    }

    private fun refresh() {
        val all = MemoStore.active(this)
            .filter { !it.isEmpty }
            .sortedByDescending { it.updatedAt }
        val q = query.trim().lowercase()
        val shown = if (q.isEmpty()) all else all.filter { it.searchText.lowercase().contains(q) }
        adapter.submit(shown)

        findViewById<View>(R.id.empty).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        val noHit = findViewById<TextView>(R.id.noHit)
        if (q.isNotEmpty() && all.isNotEmpty() && shown.isEmpty()) {
            noHit.text = "「${query.trim()}」に一致するメモはありません"
            noHit.visibility = View.VISIBLE
        } else {
            noHit.visibility = View.GONE
        }
        val count = findViewById<TextView>(R.id.count)
        count.text = when {
            all.isEmpty() -> ""
            q.isNotEmpty() -> "${shown.size} / ${all.size} 件"
            else -> "${all.size} 件のメモ"
        }
    }
}
