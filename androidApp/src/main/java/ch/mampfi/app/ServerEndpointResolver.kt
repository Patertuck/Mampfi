package ch.mampfi.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ServerEndpointResolver {
    private val client = OkHttpClient.Builder().callTimeout(1500, TimeUnit.MILLISECONDS).build()

    suspend fun resolve(): String = withContext(Dispatchers.IO) {
        ApiConfig.candidates.firstOrNull(::isReachable) ?: ApiConfig.lanBaseUrl
    }

    private fun isReachable(baseUrl: String): Boolean = runCatching {
        client.newCall(Request.Builder().url("${baseUrl}api/mahlzeiten").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
