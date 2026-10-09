package com.tonoyama.pocketmemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.TaskStackBuilder

/** Receives text shared from other apps and turns it into a new memo. */
class ShareReceiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (intent.action == Intent.ACTION_SEND && (text.isNotEmpty() || subject.isNotEmpty())) {
            val memo = Memo.create().apply {
                title = subject.replace("\n", " ")
                this.text = text
            }
            MemoStore.save(this, memo)
            Toast.makeText(this, "メモを作成しました", Toast.LENGTH_SHORT).show()
            TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(EditorActivity.intent(this, memo.id))
                .startActivities()
        }
        finish()
    }
}
