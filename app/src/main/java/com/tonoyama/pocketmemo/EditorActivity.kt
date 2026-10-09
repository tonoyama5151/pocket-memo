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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
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
    private lateinit var titleInput: EditText
    private lateinit var textInput: EditText
    private lateinit var meta: TextView
    private lateinit var pinButton: ImageButton
    private lateinit var bgThumb: ImageView
    private lateinit var bgPick: MaterialButton
    private lateinit var bgRemove: MaterialButton
    private val dots = mutableListOf<Pair<String, View>>()
    private var deleted = false
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow(final = false) }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || !::memo.isInitialized) return@registerForActivityResult
        val appContext = applicationContext
        val memoId = memo.id
        bgPick.isEnabled = false
        Thread {
            val name = MemoImages.importImage(appContext, uri, memoId)
            runOnUiThread {
                bgPick.isEnabled = true
                if (name == null) {
                    Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show()
                } else {
                    val old = memo.bg
                    memo.bg = name
                    memo.updatedAt = System.currentTimeMillis()
                    saveNow(final = false)
                    MemoImages.delete(this, old)
                    updateBackgroundRow()
                    updateMeta()
                }
            }
        }.start()
    }

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

        titleInput = findViewById(R.id.titleInput)
        textInput = findViewById(R.id.text)
        meta = findViewById(R.id.meta)
        pinButton = findViewById(R.id.pin)
        bgThumb = findViewById(R.id.bgThumb)
        bgPick = findViewById(R.id.bgPick)
        bgRemove = findViewById(R.id.bgRemove)

        titleInput.setText(memo.title)
        textInput.setText(memo.text)
        if (existing == null) {
            titleInput.requestFocus()
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        titleInput.doAfterTextChanged {
            val value = it?.toString().orEmpty().replace("\n", " ")
            if (value == memo.title) return@doAfterTextChanged
            memo.title = value
            onEdited()
        }
        textInput.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            if (value == memo.text) return@doAfterTextChanged
            memo.text = value
            onEdited()
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
        bgPick.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        bgRemove.setOnClickListener {
            MemoImages.delete(this, memo.bg)
            memo.bg = ""
            memo.updatedAt = System.currentTimeMillis()
            saveNow(final = false)
            updateBackgroundRow()
            updateMeta()
        }

        buildColorDots()
        updateBackgroundRow()
        updateMeta()
    }

    private fun onEdited() {
        memo.updatedAt = System.currentTimeMillis()
        updateMeta()
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 600)
    }

    private fun buildColorDots() {
        val row = findViewById<LinearLayout>(R.id.colors)
        val label = TextView(this).apply {
            text = "色"
            setTextColor(ContextCompat.getColor(this@EditorActivity, R.color.ink_soft))
            textSize = 13f
            minWidth = dp(40)
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
            val lp = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(10)
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
            row.addView(dot, lp)
            dots.add(key to dot)
        }
    }

    private fun updateBackgroundRow() {
        val bitmap = MemoImages.load(this, memo.bg)
        if (bitmap != null) {
            bgThumb.setImageBitmap(bitmap)
            bgThumb.visibility = View.VISIBLE
            bgPick.text = "変更"
            bgRemove.visibility = View.VISIBLE
        } else {
            bgThumb.setImageDrawable(null)
            bgThumb.visibility = View.GONE
            bgPick.text = "画像を選ぶ"
            bgRemove.visibility = View.GONE
        }
    }

    private fun updateMeta() {
        val chars = MemoFormat.charCount(memo.title) + MemoFormat.charCount(memo.text)
        meta.text = "${MemoFormat.longDate(memo.updatedAt)} 更新 · ${chars}文字"
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

    /** Saves the memo. Empty memos are only removed when leaving the editor. */
    private fun saveNow(final: Boolean) {
        if (deleted) return
        if (memo.isEmpty) {
            if (final) {
                if (MemoStore.get(this, memo.id) != null) {
                    MemoStore.delete(this, memo.id)
                    StickyWidget.updateAll(this)
                } else {
                    MemoImages.delete(this, memo.bg)
                }
            }
            return
        }
        MemoStore.save(this, memo)
        StickyWidget.updateAll(this)
    }

    private fun pinToHomeScreen() {
        if (memo.isEmpty) {
            Toast.makeText(this, "先にタイトルか本文を書いてください", Toast.LENGTH_SHORT).show()
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
                if (MemoStore.get(this, memo.id) != null) MemoStore.delete(this, memo.id) else MemoImages.delete(this, memo.bg)
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
