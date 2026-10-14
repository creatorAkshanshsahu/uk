package com.akshansh.aktv

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("aktv", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = HashSet(sp.getStringSet("fav", emptySet()) ?: emptySet())

    fun toggleFavorite(id: String) {
        val s = favorites().toMutableSet()
        if (!s.remove(id)) s.add(id)
        sp.edit().putStringSet("fav", s).apply()
    }

    fun recents(): List<String> =
        (sp.getString("recent", "") ?: "").split(',').filter { it.isNotBlank() }

    fun pushRecent(id: String) {
        val l = recents().toMutableList()
        l.remove(id)
        l.add(0, id)
        sp.edit().putString("recent", l.take(20).joinToString(",")).apply()
    }
}
