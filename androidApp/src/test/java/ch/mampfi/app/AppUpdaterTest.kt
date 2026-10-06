package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdaterTest {
    @Test
    fun `only newer numeric versions are updates`() {
        assertTrue(isNewerVersion("1.0.11", "1.0.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
        assertFalse(isNewerVersion("1.0.9", "1.0.9"))
        assertFalse(isNewerVersion("1.0.8", "1.0.9"))
    }

    @Test
    fun `version comparison tolerates prefixes and missing components`() {
        assertTrue(isNewerVersion("v1.1", "1.0.99"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
    }
}
