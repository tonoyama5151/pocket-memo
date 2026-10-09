package com.tonoyama.pocketmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
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
                    setBackground(name)
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
        if (existing != null && existing.inTrash) {
            Toast.makeText(this, "このメモはゴミ箱にあります", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, TrashActivity::class.java))
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
        findViewById<ImageButton>(R.id.share).setOnClickListener { share() }
        findViewById<ImageButton>(R.id.mode).setOnClickListener { toggleMode() }
        findViewById<MaterialButton>(R.id.addItem).setOnClickListener {
            memo.items = memo.items + CheckItem.create()
            onEdited()
            rebuildChecklist(focusIndex = memo.items.size - 1)
        }
        findViewById<ImageButton>(R.id.delete).setOnClickListener { confirmDelete() }
        bgPick.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<MaterialButton>(R.id.bgPattern).setOnClickListener { showPatternPicker() }
        bgRemove.setOnClickListener {
            MemoImages.delete(this, memo.bg)
            memo.bg = ""
            memo.updatedAt = System.currentTimeMillis()
            saveNow(final = false)
            updateBackgroundRow()
            updateMeta()
        }

        val sizeGroup = findViewById<MaterialButtonToggleGroup>(R.id.sizeGroup)
        sizeGroup.check(
            when (memo.textSize) {
                "s" -> R.id.sizeS
                "l" -> R.id.sizeL
                else -> R.id.sizeM
            }
        )
        sizeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = when (checkedId) {
                R.id.sizeS -> "s"
                R.id.sizeL -> "l"
                else -> "m"
            }
            if (value != memo.textSize) {
                memo.textSize = value
                memo.updatedAt = System.currentTimeMillis()
                updateMeta()
                saveNow(final = false)
            }
        }
        val inkGroup = findViewById<MaterialButtonToggleGroup>(R.id.inkGroup)
        inkGroup.check(if (memo.textColor == "light") R.id.inkLight else R.id.inkDark)
        inkGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = if (checkedId == R.id.inkLight) "light" else "dark"
            if (value != memo.textColor) {
                memo.textColor = value
                memo.updatedAt = System.currentTimeMillis()
                updateMeta()
                updateBackgroundRow()
                saveNow(final = false)
            }
        }

        buildColorDots()
        updateBackgroundRow()
        showBodyMode()
        updateMeta()
    }

    private fun showBodyMode() {
        val check = memo.isChecklist
        textInput.visibility = if (check) View.GONE else View.VISIBLE
        findViewById<View>(R.id.checklist).visibility = if (check) View.VISIBLE else View.GONE
        findViewById<View>(R.id.addItem).visibility = if (check) View.VISIBLE else View.GONE
        val mode = findViewById<ImageButton>(R.id.mode)
        mode.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (check) R.color.ai else R.color.ink_soft)
        )
        mode.contentDescription = if (check) "普通のメモに戻す" else "チェックリストにする"
        if (check) rebuildChecklist(focusIndex = -1)
    }

    private fun toggleMode() {
        if (memo.isChecklist) {
            memo.text = memo.items.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            memo.items = emptyList()
            memo.type = "note"
            textInput.setText(memo.text)
            Toast.makeText(this, "普通のメモにしました", Toast.LENGTH_SHORT).show()
        } else {
            val lines = memo.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            memo.items = if (lines.isEmpty()) listOf(CheckItem.create()) else lines.map { CheckItem.create(it) }
            memo.text = ""
            memo.type = "check"
            Toast.makeText(this, "チェックリストにしました", Toast.LENGTH_SHORT).show()
        }
        memo.updatedAt = System.currentTimeMillis()
        showBodyMode()
        updateMeta()
        saveNow(final = false)
    }

    private fun rebuildChecklist(focusIndex: Int) {
        val box = findViewById<LinearLayout>(R.id.checklist)
        box.removeAllViews()
        val ink = ContextCompat.getColor(this, R.color.ink)
        val soft = ContextCompat.getColor(this, R.color.ink_soft)
        memo.items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(4), 0)
            }
            val check = CheckBox(this).apply { isChecked = item.done }
            val field = EditText(this).apply {
                setText(item.text)
                background = null
                textSize = 17f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_NEXT
                hint = "項目"
                setHintTextColor(soft)
                styleDone(this, item.done, ink, soft)
            }
            val remove = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                background = null
                contentDescription = "項目を削除"
                imageTintList = ColorStateList.valueOf(soft)
            }
            row.addView(check, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(remove, LinearLayout.LayoutParams(dp(40), dp(40)))
            box.addView(row)

            check.setOnCheckedChangeListener { _, isChecked ->
                memo.items = memo.items.map { if (it.id == item.id) it.copy(done = isChecked) else it }
                styleDone(field, isChecked, ink, soft)
                memo.updatedAt = System.currentTimeMillis()
                updateMeta()
                saveNow(final = false)
            }
            field.doAfterTextChanged { e ->
                val value = e?.toString().orEmpty()
                val current = memo.items.firstOrNull { it.id == item.id } ?: return@doAfterTextChanged
                if (current.text == value) return@doAfterTextChanged
                memo.items = memo.items.map { if (it.id == item.id) it.copy(text = value) else it }
                onEdited()
            }
            field.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    val at = memo.items.indexOfFirst { it.id == item.id } + 1
                    memo.items = memo.items.toMutableList().apply { add(at, CheckItem.create()) }
                    onEdited()
                    rebuildChecklist(focusIndex = at)
                    true
                } else {
                    false
                }
            }
            remove.setOnClickListener {
                memo.items = memo.items.filter { it.id != item.id }
                onEdited()
                rebuildChecklist(focusIndex = -1)
            }
            if (index == focusIndex) {
                field.post {
                    field.requestFocus()
                    getSystemService(InputMethodManager::class.java)?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private fun styleDone(field: EditText, done: Boolean, ink: Int, soft: Int) {
        field.setTextColor(if (done) soft else ink)
        field.paintFlags = if (done) {
            field.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            field.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun share() {
        if (memo.isEmpty) {
            Toast.makeText(this, "共有する内容がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, memo.shareText)
            if (memo.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, memo.title.trim())
        }
        startActivity(Intent.createChooser(send, "メモを共有"))
    }

    /** Applies a new photo or pattern. A new background starts with the clear label. */
    private fun setBackground(value: String) {
        val old = memo.bg
        memo.bg = value
        memo.color = "clear"
        memo.updatedAt = System.currentTimeMillis()
        saveNow(final = false)
        if (old != value) MemoImages.delete(this, old)
        updateBackgroundRow()
        updateMeta()
    }

    private fun showPatternPicker() {
        val density = resources.displayMetrics.density
        val w = (110 * density).toInt()
        val h = (72 * density).toInt()
        val previewColor = if (Backgrounds.isPattern(memo.bg)) memo.color else "clear"
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@EditorActivity, 3)
            setPadding(dp(12), dp(8), dp(12), 0)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = Backgrounds.PATTERNS.size
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                    object : RecyclerView.ViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.item_pattern, parent, false)
                    ) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val info = Backgrounds.PATTERNS[position]
                    val value = Backgrounds.PATTERN_PREFIX + info.key
                    holder.itemView.findViewById<ImageView>(R.id.patternImage).setImageBitmap(
                        Backgrounds.render(this@EditorActivity, value, previewColor, memo.textColor, w, h, density)
                    )
                    holder.itemView.findViewById<TextView>(R.id.patternName).text = info.name
                    holder.itemView.setOnClickListener {
                        setBackground(value)
                        dialog?.dismiss()
                    }
                }
            }
        }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("柄を選ぶ")
            .setView(grid)
            .setNegativeButton("閉じる", null)
            .show()
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
                    updateBackgroundRow()
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
        val size = dp(44)
        val bitmap = Backgrounds.render(
            this, memo.bg, memo.color, memo.textColor, size, size, resources.displayMetrics.density
        )
        if (bitmap != null) {
            bgThumb.setImageBitmap(bitmap)
            bgThumb.visibility = View.VISIBLE
            bgRemove.visibility = View.VISIBLE
        } else {
            bgThumb.setImageDrawable(null)
            bgThumb.visibility = View.GONE
            bgRemove.visibility = View.GONE
        }
    }

    private fun updateMeta() {
        val chars = MemoFormat.charCount(memo.title) + MemoFormat.charCount(memo.text) +
            memo.items.sumOf { MemoFormat.charCount(it.text) }
        meta.text = "${MemoFormat.longDate(memo.updatedAt)} 更新 · ${chars}文字"
        val accent = ContextCompat.getColor(this, R.color.ai)
        val soft = ContextCompat.getColor(this, R.color.ink_soft)
        pinButton.imageTintList = ColorStateList.valueOf(if (memo.pinned) accent else soft)
        pinButton.contentDescription = if (memo.pinned) "ピン留めを外す" else "ピン留め"

        val ink = ContextCompat.getColor(this, R.color.ink)
        val rule = ContextCompat.getColor(this, R.color.rule)
        val sheet = ContextCompat.getColor(this, R.color.sheet)
        val note = ContextCompat.getColor(this, R.color.note_plain)
        dots.forEach { (key, view) ->
            val selected = key == memo.color
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                when (key) {
                    "clear" -> {
                        // Transparent: hollow dot with a dashed outline.
                        setColor(Color.TRANSPARENT)
                        if (selected) setStroke(dp(3), ink) else setStroke(dp(2), ink_soft(), dp(3).toFloat(), dp(2).toFloat())
                    }
                    else -> {
                        setColor(MemoFormat.tagColor(this@EditorActivity, key) ?: note)
                        setStroke(if (selected) dp(3) else dp(1), if (selected) ink else rule)
                    }
                }
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
            .setMessage("このメモをゴミ箱に移しますか？30日間は元に戻せます。")
            .setNegativeButton("やめる", null)
            .setPositiveButton("ゴミ箱に移す") { _, _ ->
                handler.removeCallbacks(saveRunnable)
                deleted = true
                if (memo.isEmpty) {
                    if (MemoStore.get(this, memo.id) != null) MemoStore.delete(this, memo.id) else MemoImages.delete(this, memo.bg)
                } else {
                    MemoStore.save(this, memo)
                    MemoStore.moveToTrash(this, memo.id)
                }
                StickyWidget.updateAll(this)
                Toast.makeText(this, "ゴミ箱に移しました", Toast.LENGTH_SHORT).show()
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

    private fun ink_soft(): Int = ContextCompat.getColor(this, R.color.ink_soft)
}
