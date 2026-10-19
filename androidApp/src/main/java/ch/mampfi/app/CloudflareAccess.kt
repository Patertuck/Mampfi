package ch.mampfi.app

import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal const val ACCESS_CLIENT_ID_HEADER = "CF-Access-Client-Id"
internal const val ACCESS_CLIENT_SECRET_HEADER = "CF-Access-Client-Secret"

internal class CloudflareAccessInterceptor(
    private val settings: () -> EndpointSettings?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configured = settings()
        val authenticatedRequest = request.withCloudflareAccess(configured)
        return chain.proceed(authenticatedRequest)
    }
}

internal fun Request.withCloudflareAccess(settings: EndpointSettings?): Request {
    val builder = newBuilder()
        .removeHeader(ACCESS_CLIENT_ID_HEADER)
        .removeHeader(ACCESS_CLIENT_SECRET_HEADER)
    if (settings != null && url.matchesOrigin(settings.serverBaseUrl)) {
        builder.header(ACCESS_CLIENT_ID_HEADER, settings.accessClientId)
        builder.header(ACCESS_CLIENT_SECRET_HEADER, settings.accessClientSecret)
    }
    return builder.build()
}

internal fun authenticatedHttpClient(
    settings: () -> EndpointSettings?,
    callTimeoutMillis: Long? = null,
    followRedirects: Boolean = true,
): OkHttpClient = OkHttpClient.Builder()
    .apply { if (callTimeoutMillis != null) callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS) }
    .followRedirects(followRedirects)
    .followSslRedirects(followRedirects)
    .addNetworkInterceptor(CloudflareAccessInterceptor(settings))
    .build()

private fun okhttp3.HttpUrl.matchesOrigin(baseUrl: String): Boolean {
    val configured = runCatching { URI(baseUrl) }.getOrNull() ?: return false
    val configuredPort = if (configured.port >= 0) configured.port else 443
    return scheme == "https" &&
        configured.scheme.equals("https", ignoreCase = true) &&
        host.equals(configured.host, ignoreCase = true) &&
        port == configuredPort
}
