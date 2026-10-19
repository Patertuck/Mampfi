package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.Request

class CloudflareAccessTest {
    private val settings = EndpointSettings(
        serverBaseUrl = "https://mampfi.example/",
        accessClientId = "phone-id",
        accessClientSecret = "phone-secret",
    )

    @Test
    fun `credentials are added to the configured origin`() {
        val request = Request.Builder().url("https://mampfi.example/api/mahlzeiten").build()
            .withCloudflareAccess(settings)

        assertEquals("phone-id", request.header(ACCESS_CLIENT_ID_HEADER))
        assertEquals("phone-secret", request.header(ACCESS_CLIENT_SECRET_HEADER))
    }

    @Test
    fun `credentials are removed from unrelated origins`() {
        val request = Request.Builder()
            .url("https://example.org/image.jpg")
            .header(ACCESS_CLIENT_ID_HEADER, "leaked-id")
            .header(ACCESS_CLIENT_SECRET_HEADER, "leaked-secret")
            .build()
            .withCloudflareAccess(settings)

        assertNull(request.header(ACCESS_CLIENT_ID_HEADER))
        assertNull(request.header(ACCESS_CLIENT_SECRET_HEADER))
    }

    @Test
    fun `credentials require exact scheme host and port`() {
        listOf(
            "http://mampfi.example/api/mahlzeiten",
            "https://other.mampfi.example/api/mahlzeiten",
            "https://mampfi.example:8443/api/mahlzeiten",
        ).forEach { url ->
            val request = Request.Builder().url(url).build().withCloudflareAccess(settings)
            assertNull(request.header(ACCESS_CLIENT_SECRET_HEADER), url)
        }
    }
}
