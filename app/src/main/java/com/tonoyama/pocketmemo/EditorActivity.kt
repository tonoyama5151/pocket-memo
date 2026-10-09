package com.tonoyama.pocketmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "memo_id"

        fun intent(context: Context, id: String?): Intent =
            Intent(context, EditorActivity::class.java).apply {
                if (id != null) putExtra(EXTRA_ID, id)
            }
    }

    private lateinit var memo: Memo
    private lateinit var textView: EditText
    private lateinit var meta: TextView
    private lateinit var pinButton: ImageButton
    private val dots = mutableListOf<Pair<String, View>>()
    private var deleted = false
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow(final = false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        val existing = id?.let { MemoStore.get(this, it) }
        if (id != null && existing == null) {
            Toast.makeText(this, "このメモは削除されています", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        memo = existing ?: Memo.create()
        setContentView(R.layout.activity_editor)

        textView = findViewById(R.id.text)
        meta = findViewById(R.id.meta)
        pinButton = findViewById(R.id.pin)

        textView.setText(memo.text)
        if (existing == null) {
            textView.requestFocus()
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        textView.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            if (value == memo.text) return@doAfterTextChanged
            memo.text = value
            memo.updatedAt = System.currentTimeMillis()
            updateMeta()
            handler.removeCallbacks(saveRunnable)
            handler.postDelayed(saveRunnable, 600)
        }

        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
        pinButton.setOnClickListener {
            memo.pinned = !memo.pinned
            memo.updatedAt = System.currentTimeMillis()
            updateMeta()
            saveNow(final = false)
            Toast.makeText(this, if (memo.pinned) "ピン留めしました" else "ピン留めを外しました", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.sticky).setOnClickListener { pinToHomeScreen() }
        findViewById<ImageButton>(R.id.delete).setOnClickListener { confirmDelete() }

        buildColorDots()
        updateMeta()
    }

    private fun buildColorDots() {
        val row = findViewById<LinearLayout>(R.id.colors)
        val label = TextView(this).apply {
            text = "色"
            setTextColor(ContextCompat.getColor(this@EditorActivity, R.color.ink_soft))
            textSize = 13f
            setPadding(0, 0, dp(10), 0)
        }
        row.addView(label)
        MemoFormat.COLORS.forEach { key ->
            val dot = View(this).apply {
                contentDescription = MemoFormat.colorName(key)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    memo.color = key
                    memo.updatedAt = System.currentTimeMillis()
                    updateMeta()
                    saveNow(final = false)
                }
            }
            val lp = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
            row.addView(dot, lp)
            dots.add(key to dot)
        }
    }

    private fun updateMeta() {
        meta.text = "${MemoFormat.longDate(memo.updatedAt)} 更新 · ${MemoFormat.charCount(memo.text)}文字"
        val accent = ContextCompat.getColor(this, R.color.ai)
        val soft = ContextCompat.getColor(this, R.color.ink_soft)
        pinButton.imageTintList = ColorStateList.valueOf(if (memo.pinned) accent else soft)
        pinButton.contentDescription = if (memo.pinned) "ピン留めを外す" else "ピン留め"

        val ink = ContextCompat.getColor(this, R.color.ink)
        val rule = ContextCompat.getColor(this, R.color.rule)
        val sheet = ContextCompat.getColor(this, R.color.sheet)
        dots.forEach { (key, view) ->
            val fill = MemoFormat.tagColor(this, key) ?: sheet
            val selected = key == memo.color
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                setStroke(if (selected) dp(3) else dp(1), if (selected) ink else rule)
            }
            view.isSelected = selected
        }
    }

    /** Saves the memo. Blank memos are only removed when leaving the editor. */
    private fun saveNow(final: Boolean) {
        if (deleted) return
        if (memo.text.isBlank()) {
            if (final && MemoStore.get(this, memo.id) != null) {
                MemoStore.delete(this, memo.id)
                StickyWidget.updateAll(this)
            }
            return
        }
        MemoStore.save(this, memo)
        StickyWidget.updateAll(this)
    }

    private fun pinToHomeScreen() {
        if (memo.text.isBlank()) {
            Toast.makeText(this, "先にメモを書いてください", Toast.LENGTH_SHORT).show()
            return
        }
        saveNow(final = false)
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "ホーム画面を長押しして「ウィジェット」から「付箋メモ」を追加してください", Toast.LENGTH_LONG).show()
            return
        }
        val callback = Intent(this, PinResultReceiver::class.java).putExtra(EXTRA_ID, memo.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(this, memo.id.hashCode(), callback, flags)
        manager.requestPinAppWidget(ComponentName(this, StickyWidget::class.java), null, pending)
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setMessage("このメモを削除しますか？")
            .setNegativeButton("やめる", null)
            .setPositiveButton("削除する") { _, _ ->
                handler.removeCallbacks(saveRunnable)
                deleted = true
                MemoStore.delete(this, memo.id)
                StickyWidget.updateAll(this)
                Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (!::memo.isInitialized) return
        handler.removeCallbacks(saveRunnable)
        saveNow(final = isFinishing)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
