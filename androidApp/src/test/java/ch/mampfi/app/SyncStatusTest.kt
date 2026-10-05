package ch.mampfi.app

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncStatusTest {
    @Test
    fun `successful refresh marks app online and stores time`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        val result = SyncStatus().afterRefresh(success = true, completedAt = now)

        assertFalse(result.isRefreshing)
        assertFalse(result.isOffline)
        assertEquals(now, result.lastSuccessfulRefresh)
    }

    @Test
    fun `failed refresh keeps the last successful time`() {
        val lastSuccess = Instant.parse("2026-10-05T11:00:00Z")
        val result = SyncStatus(lastSuccessfulRefresh = lastSuccess).afterRefresh(
            success = false,
            completedAt = Instant.parse("2026-10-05T12:00:00Z"),
        )

        assertFalse(result.isRefreshing)
        assertTrue(result.isOffline)
        assertEquals(lastSuccess, result.lastSuccessfulRefresh)
    }
}
