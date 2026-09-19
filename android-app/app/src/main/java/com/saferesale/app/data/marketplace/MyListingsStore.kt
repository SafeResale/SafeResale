package com.saferesale.app.data.marketplace

import android.content.Context

object MyListingsStore {
    private const val PREFS = "saferesale_my_listings"

    fun add(context: Context, listingId: String) {
        val ids = get(context).toMutableSet()
        ids.add(listingId)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("ids", ids)
            .apply()
    }

    fun get(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet())
            .orEmpty()
            .toList()
            .reversed()
}