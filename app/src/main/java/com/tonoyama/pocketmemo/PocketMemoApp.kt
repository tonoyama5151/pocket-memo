package com.tonoyama.pocketmemo

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class PocketMemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Theme.apply(Theme.get(this))
    }
}

/** The app's light/dark setting: follow the phone, or always light or dark. */
object Theme {
    private const val PREFS = "settings"
    private const val KEY = "theme"
    val OPTIONS = listOf("system" to "端末の設定に合わせる", "light" to "ライト", "dark" to "ダーク")

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
        apply(value)
    }

    fun apply(value: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (value) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
