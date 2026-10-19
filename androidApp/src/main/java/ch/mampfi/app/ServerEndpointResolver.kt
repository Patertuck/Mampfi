package ch.mampfi.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class ConnectionCheckResult { CONNECTED, ACCESS_DENIED, UNAVAILABLE }

object ServerEndpointResolver {
    suspend fun check(settings: EndpointSettings): ConnectionCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            authenticatedHttpClient({ settings }, callTimeoutMillis = 3_000, followRedirects = false)
                .newCall(Request.Builder().url("${settings.serverBaseUrl}api/mahlzeiten").build())
                .execute()
                .use { response ->
                    when {
                        response.isSuccessful -> ConnectionCheckResult.CONNECTED
                        response.code == 401 || response.code == 403 || response.isRedirect -> ConnectionCheckResult.ACCESS_DENIED
                        else -> ConnectionCheckResult.UNAVAILABLE
                    }
                }
        }.getOrDefault(ConnectionCheckResult.UNAVAILABLE)
    }
}
