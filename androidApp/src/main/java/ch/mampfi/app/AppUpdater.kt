package ch.mampfi.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class AvailableUpdate(val version: String, val apkUrl: String)

@Serializable
private data class UpdateManifest(val latest_version: String, val apk_url: String, val changelog: String = "")

class AppUpdater(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun checkForUpdate(metadataUrl: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(metadataUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val manifest = Json.decodeFromString<UpdateManifest>(response.body?.string() ?: return@use null)
                AvailableUpdate(manifest.latest_version, manifest.apk_url).takeIf { it.version != BuildConfig.VERSION_NAME }
            }
        }.getOrNull()
    }

    suspend fun downloadApk(apkUrl: String, apkName: String, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "$apkName.apk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadId = downloads.enqueue(request)
        while (true) {
            delay(400)
            downloads.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                check(cursor.moveToFirst()) { "Download nicht gefunden" }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) withContext(Dispatchers.Main) { onProgress(((downloaded * 100) / total).toInt()) }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        installApk(apkName)
                        return@withContext
                    }
                    DownloadManager.STATUS_FAILED -> error("Download fehlgeschlagen")
                }
            }
        }
    }

    private fun installApk(apkName: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$apkName.apk")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
