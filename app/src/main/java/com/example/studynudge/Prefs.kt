package com.example.studynudge

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("study_nudge", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) { sp.edit().putString("api_key", v).apply() }

    var model: String
        get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) { sp.edit().putString("model", v).apply() }

    var intervalMin: Int
        get() = sp.getInt("interval_min", 30)
        set(v) { sp.edit().putInt("interval_min", v).apply() }

    var bedtimeHour: Int
        get() = sp.getInt("bedtime_hour", 23)
        set(v) { sp.edit().putInt("bedtime_hour", v).apply() }

    var earlyHour: Int
        get() = sp.getInt("early_hour", 5)
        set(v) { sp.edit().putInt("early_hour", v).apply() }

    var lastError: String
        get() = sp.getString("last_error", "") ?: ""
        set(v) { sp.edit().putString("last_error", v).apply() }

    fun getPlace(key: String): Place? {
        val lat = sp.getString("${key}_lat", null)?.toDoubleOrNull() ?: return null
        val lng = sp.getString("${key}_lng", null)?.toDoubleOrNull() ?: return null
        val label = PlaceKeys.ALL.firstOrNull { it.first == key }?.second ?: key
        return Place(key, label, lat, lng)
    }

    fun setPlace(key: String, lat: Double, lng: Double) {
        sp.edit().putString("${key}_lat", lat.toString()).putString("${key}_lng", lng.toString()).apply()
    }

    fun clearPlace(key: String) {
        sp.edit().remove("${key}_lat").remove("${key}_lng").apply()
    }
}
