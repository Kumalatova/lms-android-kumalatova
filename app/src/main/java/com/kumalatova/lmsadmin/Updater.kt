package com.kumalatova.lmsadmin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Новая версия приложения: номер сборки и название. */
data class UpdateInfo(val code: Int, val name: String)

/** Состояние обновления на главном экране. */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

/**
 * Обновление из релиза latest репозитория приложения: version.json с номером
 * сборки и сам lms-admin.apk. Репозиторий должен быть открытым.
 */
object Updater {
    private const val BASE = "https://github.com/Kumalatova/lms-android-kumalatova/releases/download/latest/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun currentCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun currentName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()

    /** Возвращает данные новой версии или null, если новой нет или проверить не удалось. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(BASE + "version.json").header("Cache-Control", "no-cache").build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val o = JSONObject(r.body?.string().orEmpty())
                val code = o.getInt("code")
                if (code > currentCode(ctx)) UpdateInfo(code, o.optString("name")) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "lms-admin.apk")
        val request = Request.Builder().url(BASE + "lms-admin.apk").build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("код ${r.code}")
            val body = r.body ?: throw ApiException("пустой ответ")
            body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        if (file.length() < 1_000_000) throw ApiException("файл скачался не полностью")
        file
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Экран системы, где один раз разрешается установка из этого приложения. */
    fun openInstallPermission(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // Нет такого экрана: пользователь разрешит вручную в настройках.
        }
    }

    /** Открывает системное окно установки скачанного файла. */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
