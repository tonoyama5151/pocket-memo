package com.tonoyama.pocketmemo

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: MemoAdapter
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = MemoAdapter(showSections = true) { memo ->
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
        // Redraw sticky notes so they pick up any change in how they look after an update.
        StickyWidget.updateAll(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = MemoStore.all(this)
            .filter { !it.isEmpty }
            .sortedByDescending { it.updatedAt }
        val q = query.trim().lowercase()
        val shown = if (q.isEmpty()) all else all.filter { "${it.title}\n${it.text}".lowercase().contains(q) }
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
