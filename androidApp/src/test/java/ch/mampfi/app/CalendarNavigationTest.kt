package ch.mampfi.app

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarNavigationTest {
    private val minimum = LocalDate.of(2024, 1, 1)
    private val maximum = LocalDate.of(2028, 12, 31)

    @Test
    fun `adjacent week moves in either direction`() {
        val current = LocalDate.of(2026, 10, 5)

        assertEquals(LocalDate.of(2026, 9, 28), adjacentWeekDate(current, -1, minimum, maximum))
        assertEquals(LocalDate.of(2026, 10, 12), adjacentWeekDate(current, 1, minimum, maximum))
    }

    @Test
    fun `adjacent week cannot leave calendar range`() {
        assertNull(adjacentWeekDate(minimum, -1, minimum, maximum))
        assertNull(adjacentWeekDate(maximum, 1, minimum, maximum))
    }
}
