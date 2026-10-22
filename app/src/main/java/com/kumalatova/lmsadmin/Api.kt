package com.kumalatova.lmsadmin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ошибка с текстом, который можно показать пользователю. */
class ApiException(val userMessage: String) : Exception(userMessage)

/** Ответ summary: название сайта и число непрочитанных. */
data class SummaryResult(val siteName: String, val counts: Counts)

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun url(api: String, path: String, params: Map<String, String> = emptyMap()): HttpUrl {
        val base = (api + path).toHttpUrlOrNull() ?: throw ApiException("Неверный адрес сайта")
        if (base.scheme != "https") throw ApiException("Нужен адрес с https")
        val builder = base.newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    /** Текст ошибки WordPress из ответа вида {"code":"...","message":"..."}. */
    private fun serverMessage(body: String): String {
        return try {
            JSONObject(body).optString("message")
        } catch (e: Exception) {
            ""
        }
    }

    private fun call(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) return body
                val base = when (response.code) {
                    401 -> "Ключ не подходит или отозван"
                    403 -> "Нет доступа"
                    404 -> "Плагин на сайте не найден"
                    429 -> "Слишком много попыток, подождите"
                    else -> "Ошибка сайта"
                }
                val detail = serverMessage(body)
                val tail = if (detail.isNotEmpty()) ": $detail" else ""
                throw ApiException("$base (код ${response.code})$tail")
            }
        } catch (e: IOException) {
            throw ApiException("Нет связи с сайтом (${e.javaClass.simpleName})")
        }
    }

    private fun get(site: Site, path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val request = Request.Builder()
            .url(url(site.api, path, params))
            .header("X-LMS-Key", site.key)
            .get()
            .build()
        return parse(call(request))
    }

    private fun post(site: Site, path: String, fields: Map<String, String>): JSONObject {
        val form = FormBody.Builder()
        fields.forEach { (k, v) -> form.add(k, v) }
        val request = Request.Builder()
            .url(url(site.api, path))
            .header("X-LMS-Key", site.key)
            .post(form.build())
            .build()
        return parse(call(request))
    }

    private fun parse(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            val start = body.trim().replace(Regex("\\s+"), " ").take(60)
            throw ApiException("Сайт ответил не так, как ждёт приложение. Начало ответа: $start")
        }
    }

    private fun counts(o: JSONObject): Counts {
        val c = o.getJSONObject("counts")
        return Counts(c.optInt("tickets"), c.optInt("teachers"), c.optInt("shop"))
    }

    suspend fun summary(site: Site): SummaryResult = withContext(Dispatchers.IO) {
        val o = get(site, "summary")
        SummaryResult(o.getJSONObject("site").optString("name"), counts(o))
    }

    suspend fun list(site: Site, group: Group): List<ListItem> = withContext(Dispatchers.IO) {
        val o = get(site, "list", mapOf("group" to group.code))
        val arr = o.getJSONArray("items")
        (0 until arr.length()).map { i ->
            val row = arr.getJSONObject(i)
            ListItem(
                id = row.getLong("id"),
                group = group,
                person = row.optString("person"),
                topic = row.optString("topic"),
                to = row.optString("to"),
                unread = row.optInt("unread"),
                time = row.optString("time"),
            )
        }
    }

    suspend fun thread(site: Site, group: Group, id: Long): ThreadData = withContext(Dispatchers.IO) {
        val o = get(site, "thread", mapOf("group" to group.code, "id" to id.toString()))
        val arr = o.getJSONArray("messages")
        val messages = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            Message(
                id = m.getLong("id"),
                mine = m.optBoolean("mine"),
                sender = m.optString("sender"),
                text = m.optString("text"),
                attachment = m.optString("attachment"),
                time = m.optString("time"),
            )
        }
        ThreadData(
            id = o.getLong("id"),
            group = group,
            topic = o.optString("topic"),
            person = o.optString("person"),
            to = o.optString("to"),
            messages = messages,
        )
    }

    suspend fun markRead(site: Site, group: Group, id: Long) {
        withContext(Dispatchers.IO) {
            post(site, "read", mapOf("group" to group.code, "id" to id.toString()))
        }
    }
}
