package com.kumalatova.lmsadmin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Список подключённых сайтов в закрытом хранилище приложения. */
class SiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("lms_sites", Context.MODE_PRIVATE)

    fun load(): List<Site> {
        val raw = prefs.getString("sites", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Site(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    api = o.getString("api"),
                    key = o.getString("key"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(sites: List<Site>) {
        val arr = JSONArray()
        sites.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("url", s.url)
                    .put("api", s.api)
                    .put("key", s.key),
            )
        }
        prefs.edit().putString("sites", arr.toString()).apply()
    }
}
