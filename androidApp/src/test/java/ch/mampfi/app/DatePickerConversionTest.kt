package ch.mampfi.app

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DatePickerConversionTest {
    @Test
    fun `date picker conversion preserves dates at month and year boundaries`() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1),
        ).forEach { date ->
            assertEquals(date, datePickerMillisToLocalDate(date.toDatePickerMillis()))
        }
    }
}
