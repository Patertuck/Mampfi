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
    }

    @Test
    fun `rating names are trimmed and cannot be blank`() {
        assertEquals("First person", EndpointSettingsStore.normalizeRaterName("  First person  "))
        assertFailsWith<IllegalArgumentException> { EndpointSettingsStore.normalizeRaterName("   ") }
    }
}
