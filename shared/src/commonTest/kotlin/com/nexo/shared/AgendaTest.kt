package com.nexo.shared

import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Instant

class AgendaTest {
    @Test fun passwordRequiresLengthDigitAndSymbol() {
        assertTrue(validPassword("Nexo2026!"))
        listOf("Nexo26!", "Nexolargo!", "Nexo2026", "Nexo2026 ").forEach { assertFalse(validPassword(it), it) }
    }
    @Test fun monthStartsOnMondayAndIncludesSelectedDay() {
        val date = LocalDate(2026, 11, 16)
        val cells = monthGrid(date)
        assertEquals(42, cells.size)
        assertEquals(DayOfWeek.MONDAY, cells.first().dayOfWeek)
        assertEquals(LocalDate(2026, 10, 26), cells.first())
        assertTrue(date in cells)
        assertEquals(7, weekDates(date).size)
    }
    @Test fun reminderFiresOnlyInWindowAndBeforeStart() {
        val event = Appointment(id = "1", title = "Reunión", start = "2026-11-16T09:00:00Z", end = "2026-11-16T10:00:00Z")
        assertTrue(reminderDue(event, Instant.parse("2026-11-16T08:55:00Z"), 5))
        assertFalse(reminderDue(event, Instant.parse("2026-11-16T08:54:59Z"), 5))
        assertFalse(reminderDue(event, Instant.parse("2026-11-16T09:00:00Z"), 5))
        assertNotEquals(reminderKey(event, 5), reminderKey(event.copy(start = "2026-11-16T11:00:00Z"), 5))
    }
    @Test fun eventEndingAtMidnightDoesNotAppearNextDay() {
        val zone = TimeZone.currentSystemDefault()
        val day = LocalDate(2026, 11, 16)
        val event = Appointment(title = "Tarde", start = day.atTime(23, 0).toInstant(zone).toString(), end = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toString())
        assertTrue(appointmentOnDay(event, day))
        assertFalse(appointmentOnDay(event, day.plus(1, DateTimeUnit.DAY)))
    }
}
