package com.leejolt.reelmock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the last received URL (+ short history) in SharedPreferences. */
class ReelStore(context: Context) {

    private val prefs = context.getSharedPreferences("reelmock", Context.MODE_PRIVATE)

    /** [source] = LinkIntake.SOURCE_SHARE or SOURCE_VIEW. */
    fun save(v: ReelParser.Result.Valid, source: String = LinkIntake.SOURCE_SHARE) {
        val now = System.currentTimeMillis()
        val old = loadHistory()
        val arr = JSONArray()
        arr.put(JSONObject().put("url", v.originalUrl).put("id", v.reelId).put("ts", now).put("src", source))
        for (i in 0 until minOf(old.length(), MAX_HISTORY - 1)) arr.put(old.getJSONObject(i))

        prefs.edit()
            .putString("last_url", v.originalUrl)
            .putString("last_id", v.reelId)
            .putLong("last_ts", now)
            .putString("last_src", source)
            .putInt("count", prefs.getInt("count", 0) + 1)
            .putString("history", arr.toString())
            .apply()
    }

    fun lastUrl(): String? = prefs.getString("last_url", null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** status: "idle" | "valid" | "invalid"; source: how this event arrived ("share" | "view"). */
    fun buildState(
        status: String,
        reason: String? = null,
        raw: String? = null,
        source: String? = null
    ): JSONObject {
        val s = JSONObject()
        s.put("status", status)
        s.put("source", source ?: JSONObject.NULL)
        s.put("reason", reason ?: JSONObject.NULL)
        s.put("raw", raw?.take(500) ?: JSONObject.NULL)
        s.put("shareCount", prefs.getInt("count", 0))

        val url = prefs.getString("last_url", null)
        val id = prefs.getString("last_id", null)
        if (url != null && id != null) {
            s.put(
                "current",
                JSONObject()
                    .put("url", url)
                    .put("id", id)
                    .put("canonical", "https://www.instagram.com/reel/$id/")
                    .put("ts", prefs.getLong("last_ts", 0L))
                    .put("source", prefs.getString("last_src", null) ?: JSONObject.NULL)
            )
        } else {
            s.put("current", JSONObject.NULL)
        }
        s.put("history", loadHistory())
        return s
    }

    private fun loadHistory(): JSONArray =
        try { JSONArray(prefs.getString("history", "[]")) } catch (e: Exception) { JSONArray() }

    private companion object {
        const val MAX_HISTORY = 10
    }
}
