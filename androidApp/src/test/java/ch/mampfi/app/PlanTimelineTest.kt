package ch.mampfi.app

import ch.mampfi.app.data.AuswaertsEintrag
import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.MahlzeitEintrag
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlanTimelineTest {
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun `today marker precedes a scheduled entry for today`() {
        val meal = mealOn(today)

        val items = scheduledPlanItems(listOf(meal), emptyList(), today)

        assertEquals(
            listOf(PlanItem.Month(YearMonth.from(today)), PlanItem.TodayMarker(today), PlanItem.Day(today, listOf(meal), null)),
            items,
        )
    }

    @Test
    fun `today marker is ordered between past and future entries`() {
        val past = mealOn(today.minusDays(2), "Past")
        val future = mealOn(today.plusDays(2), "Future")

        val items = scheduledPlanItems(listOf(future, past), emptyList(), today)

        assertEquals(
            listOf(today.minusDays(2), today, today.plusDays(2)),
            items.filter { it is PlanItem.Day || it is PlanItem.TodayMarker }.map {
                when (it) {
                    is PlanItem.Day -> it.date
                    is PlanItem.TodayMarker -> it.date
                    is PlanItem.Month -> error("Months were filtered out")
                }
            },
        )
    }

    @Test
    fun `current month and marker are added when only other months have entries`() {
        val past = mealOn(LocalDate.of(2026, 8, 4))
        val futureAway = AuswaertsEintrag(datum = "2026-10-12")

        val items = scheduledPlanItems(listOf(past), listOf(futureAway), today)

        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            items.filterIsInstance<PlanItem.Month>().map { it.yearMonth },
        )
        assertEquals(today, items.single { it is PlanItem.TodayMarker }.let { assertIs<PlanItem.TodayMarker>(it).date })
    }

    @Test
    fun `empty plan still contains current month and today marker`() {
        val items = scheduledPlanItems(emptyList(), emptyList(), today)

        assertEquals(listOf(PlanItem.Month(YearMonth.from(today)), PlanItem.TodayMarker(today)), items)
    }

    private fun mealOn(date: LocalDate, name: String = "Meal") = Mahlzeit(
        name = name,
        eintraege = listOf(MahlzeitEintrag(datum = date.toString())),
    )
}
