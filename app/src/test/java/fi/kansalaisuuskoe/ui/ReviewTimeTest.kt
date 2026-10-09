package fi.kansalaisuuskoe.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReviewTimeTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun `review days count calendar days rather than elapsed hours`() {
        val evening = at(9, 22)
        assertNull(reviewDays(at(9, 21), evening, zone))
        assertNull(reviewDays(evening, evening, zone))
        assertEquals(0, reviewDays(at(9, 23), evening, zone))
        assertEquals(1, reviewDays(at(10, 1), evening, zone)) // three hours later, but tomorrow
        assertEquals(3, reviewDays(at(12, 9), evening, zone))
    }
}
