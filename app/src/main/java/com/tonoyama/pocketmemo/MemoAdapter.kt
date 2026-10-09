package com.tonoyama.pocketmemo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MemoAdapter(
    private val showSections: Boolean,
    private val onClick: (Memo) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Header(val label: String) : Item()
        data class Row(val memo: Memo) : Item()
    }

    private var items: List<Item> = emptyList()

    fun submit(memos: List<Memo>) {
        val pinned = memos.filter { it.pinned }
        val rest = memos.filter { !it.pinned }
        val out = mutableListOf<Item>()
        if (showSections && pinned.isNotEmpty()) {
            out.add(Item.Header("ピン留め"))
            pinned.forEach { out.add(Item.Row(it)) }
            if (rest.isNotEmpty()) out.add(Item.Header("すべてのメモ"))
            rest.forEach { out.add(Item.Row(it)) }
        } else {
            (pinned + rest).forEach { out.add(Item.Row(it)) }
        }
        items = out
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (items[position] is Item.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.item_memo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderHolder).label.text = item.label
            is Item.Row -> (holder as RowHolder).bind(item.memo)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.header)
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tag: View = view.findViewById(R.id.tag)
        private val title: TextView = view.findViewById(R.id.title)
        private val preview: TextView = view.findViewById(R.id.preview)
        private val date: TextView = view.findViewById(R.id.date)
        private val pinMark: ImageView = view.findViewById(R.id.pinMark)

        fun bind(memo: Memo) {
            val ctx = itemView.context
            tag.setBackgroundColor(MemoFormat.tagColor(ctx, memo.color) ?: Color.TRANSPARENT)
            val heading = memo.title.trim()
            title.text = heading
            title.visibility = if (heading.isEmpty()) View.GONE else View.VISIBLE
            preview.text = memo.preview
            preview.maxLines = if (heading.isEmpty()) 3 else 2
            preview.visibility = if (memo.preview.isEmpty()) View.GONE else View.VISIBLE
            date.text = MemoFormat.shortDate(memo.updatedAt)
            pinMark.visibility = if (memo.pinned) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(memo) }
        }
    }
}
