package com.paul.urdukb.ime

import android.content.Context
import android.content.SharedPreferences

/**
 * The keyboard's small preferences file, shared by the keyboard and the app. The settings in it are read through
 * [KbSettings]; the other things kept in it (recent emoji, learned words, clips) go through their own classes.
 */
object Kb {
    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
}
