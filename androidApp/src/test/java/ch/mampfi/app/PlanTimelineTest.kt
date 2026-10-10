package ch.mampfi.app

import ch.mampfi.app.data.AuswaertsEintrag
import ch.mampfi.app.data.Mahlzeit
import ch.mampfi.app.data.MahlzeitBewertung
import ch.mampfi.app.data.MahlzeitBild
import ch.mampfi.app.data.MahlzeitEintrag
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlanTimelineTest {
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun `today marker precedes a scheduled entry for today`() {
        val meal = mealOn(today)

        val items = scheduledPlanItems(listOf(meal), emptyList(), today)

        val markerIndex = items.indexOf(PlanItem.TodayMarker(today))
        assertEquals(PlanItem.Day(today, listOf(meal), null), items[markerIndex + 1])
        assertEquals((0L..7L).map(today::plusDays), items.filterIsInstance<PlanItem.Day>().map { it.date })
    }

    @Test
    fun `today marker is ordered between past and future entries`() {
        val past = mealOn(today.minusDays(2), "Past")
        val future = mealOn(today.plusDays(2), "Future")

        val items = scheduledPlanItems(listOf(future, past), emptyList(), today)

        val pastIndex = items.indexOfFirst { it is PlanItem.Day && it.date == today.minusDays(2) }
        val markerIndex = items.indexOf(PlanItem.TodayMarker(today))
        val futureIndex = items.indexOfFirst { it is PlanItem.Day && it.date == today.plusDays(2) }
        assertEquals(true, pastIndex < markerIndex && markerIndex < futureIndex)
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
    fun `empty plan contains today and the following seven dates across month boundary`() {
        val items = scheduledPlanItems(emptyList(), emptyList(), today)

        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), items.filterIsInstance<PlanItem.Month>().map { it.yearMonth })
        assertEquals((0L..7L).map(today::plusDays), items.filterIsInstance<PlanItem.Day>().map { it.date })
        assertEquals(today, items.single { it is PlanItem.TodayMarker }.let { assertIs<PlanItem.TodayMarker>(it).date })
    }

    @Test
    fun `occupied dates outside the visible window are retained`() {
        val past = today.minusMonths(2)
        val future = today.plusMonths(2)

        val dates = scheduledPlanItems(listOf(mealOn(past), mealOn(future)), emptyList(), today)
            .filterIsInstance<PlanItem.Day>().map { it.date }

        assertEquals(true, past in dates)
        assertEquals(true, future in dates)
    }

    @Test
    fun `drop validation allows coexistence and rejects same type duplicates`() {
        val sourceEntry = MahlzeitEintrag(id = "source", datum = today.toString())
        val targetEntry = MahlzeitEintrag(id = "target", datum = today.plusDays(1).toString())
        val meal = Mahlzeit(id = "meal", name = "Meal", eintraege = listOf(sourceEntry, targetEntry))
        val away = AuswaertsEintrag(id = "away", datum = today.plusDays(2).toString())

        assertNotNull(planDropError(PlanDragItem.Meal(meal, sourceEntry), today.plusDays(1), listOf(meal), listOf(away)))
        assertNull(planDropError(PlanDragItem.Meal(meal, sourceEntry), today.plusDays(2), listOf(meal), listOf(away)))
        assertNotNull(planDropError(PlanDragItem.Away(away), today.plusDays(3), listOf(meal), listOf(away, AuswaertsEintrag(datum = today.plusDays(3).toString()))))
        assertNull(planDropError(PlanDragItem.Away(away), today.plusDays(1), listOf(meal), listOf(away)))
    }

    @Test
    fun `optimistic meal move preserves occurrence data and changes only its date`() {
        val target = today.plusDays(3)
        val entry = MahlzeitEintrag(
            id = "entry",
            datum = today.toString(),
            bilder = listOf(MahlzeitBild(id = "image", url = "/uploads/image.jpg")),
            bewertung = MahlzeitBewertung(listOf(8.0, 9.0)),
        )
        val meal = Mahlzeit(id = "meal", name = "Curry", eintraege = listOf(entry), notiz = "Extra scharf")
        val move = PendingPlanMove.Meal(meal.id, entry.id, target)

        val result = applyPendingPlanMoves(listOf(meal), emptyList(), listOf(move))

        assertEquals(entry.copy(datum = target.toString()), result.meals.single().eintraege.single())
        assertEquals(meal.notiz, result.meals.single().notiz)
        assertEquals(false, pendingMoveReflected(move, listOf(meal), emptyList()))
        assertEquals(true, pendingMoveReflected(move, result.meals, emptyList()))
    }

    @Test
    fun `optimistic away move preserves id and note until backing data catches up`() {
        val target = today.plusDays(4)
        val away = AuswaertsEintrag(id = "away", datum = today.toString(), notiz = "Bei Anna")
        val move = PendingPlanMove.Away(away.id, target)

        val result = applyPendingPlanMoves(emptyList(), listOf(away), listOf(move))

        assertEquals(away.copy(datum = target.toString()), result.awayEntries.single())
        assertEquals(false, pendingMoveReflected(move, emptyList(), listOf(away)))
        assertEquals(true, pendingMoveReflected(move, emptyList(), result.awayEntries))
    }

    private fun mealOn(date: LocalDate, name: String = "Meal") = Mahlzeit(
        name = name,
        eintraege = listOf(MahlzeitEintrag(datum = date.toString())),
    )
}
