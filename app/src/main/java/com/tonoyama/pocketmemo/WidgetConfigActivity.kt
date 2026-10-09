package com.tonoyama.pocketmemo

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/** Lets the user choose which memo a sticky note widget shows. */
class WidgetConfigActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, resultIntent())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContentView(R.layout.activity_widget_config)

        val memos = MemoStore.all(this)
            .filter { it.text.isNotBlank() }
            .sortedByDescending { it.updatedAt }
        val adapter = MemoAdapter(showSections = false) { memo -> choose(memo.id) }
        val list = findViewById<RecyclerView>(R.id.configList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        adapter.submit(memos)
        findViewById<View>(R.id.configEmpty).visibility = if (memos.isEmpty()) View.VISIBLE else View.GONE

        findViewById<MaterialButton>(R.id.configNew).setOnClickListener {
            val memo = Memo.create().apply { text = "新しい付箋\n" }
            MemoStore.save(this, memo)
            choose(memo.id)
            startActivity(EditorActivity.intent(this, memo.id))
        }
    }

    private fun choose(memoId: String) {
        StickyWidget.bind(this, widgetId, memoId)
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
