package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointSettingsTest {
    @Test
    fun `rating names have the expected defaults`() {
        val settings = EndpointSettings()

        assertEquals("Person 1", settings.firstRaterName)
        assertEquals("Person 2", settings.secondRaterName)
        assertEquals(ThemeMode.DARK, settings.themeMode)
    }

    @Test
    fun `rating names are trimmed and cannot be blank`() {
        assertEquals("First person", EndpointSettingsStore.normalizeRaterName("  First person  "))
        assertFailsWith<IllegalArgumentException> { EndpointSettingsStore.normalizeRaterName("   ") }
    }

    @Test
    fun `endpoint is normalized before connection testing or saving`() {
        assertEquals("http://192.168.1.50:8080/", EndpointSettingsStore.normalizeEndpoint(" http://192.168.1.50:8080 "))
        assertEquals("https://mampfi.example/", EndpointSettingsStore.normalizeEndpoint("https://mampfi.example/"))
    }

    @Test
    fun `endpoint rejects unsupported or incomplete addresses`() {
        assertFailsWith<IllegalArgumentException> { EndpointSettingsStore.normalizeEndpoint("mampfi.example") }
        assertFailsWith<IllegalArgumentException> { EndpointSettingsStore.normalizeEndpoint("ftp://mampfi.example") }
    }
}
