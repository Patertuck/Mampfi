package ch.mampfi.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class AvailableUpdate(val version: String, val apkUrl: String, val changelog: String = "")

@Serializable
private data class UpdateManifest(val latest_version: String, val apk_url: String, val changelog: String = "")

internal data class DownloadSnapshot(val status: Int, val progress: Int?)

internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parts(value: String) = value.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val candidateParts = parts(candidate)
    val installedParts = parts(installed)
    for (index in 0 until maxOf(candidateParts.size, installedParts.size)) {
        val comparison = candidateParts.getOrElse(index) { 0 }.compareTo(installedParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

class AppUpdater(private val context: Context) {
    private val client = OkHttpClient()
    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun checkForUpdate(metadataUrl: String): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(metadataUrl).build()).execute().use { response ->
                check(response.isSuccessful) { "Update-Informationen nicht erreichbar" }
                val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<UpdateManifest>(response.body?.string().orEmpty())
                AvailableUpdate(manifest.latest_version, manifest.apk_url, manifest.changelog)
                    .takeIf { isNewerVersion(it.version, BuildConfig.VERSION_NAME) }
            }
        }
    }

    fun enqueue(update: AvailableUpdate, filename: String): Long {
        apkFile(filename).delete()
        return downloads.enqueue(
            DownloadManager.Request(Uri.parse(update.apkUrl))
                .setTitle("Mampfi ${update.version}")
                .setDescription("Update wird heruntergeladen")
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "$filename.apk")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED),
        )
    }

    internal fun query(downloadId: Long): DownloadSnapshot? = downloads.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
        if (!cursor.moveToFirst()) return null
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        DownloadSnapshot(status, if (total > 0) ((downloaded * 100) / total).toInt() else null)
    }

    fun apkExists(filename: String) = apkFile(filename).isFile

    fun canInstallPackages() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun install(filename: String): Boolean {
        if (!canInstallPackages()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val file = apkFile(filename)
        check(file.isFile) { "Die heruntergeladene APK wurde nicht gefunden" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    private fun apkFile(filename: String) = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$filename.apk")
}
