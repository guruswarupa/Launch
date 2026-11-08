package com.guruswarupa.launch.apps

import com.guruswarupa.launch.apps.clock.AlarmTimes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test fun onceLaterToday() {
        val n = AlarmTimes.next(at(2026, 11, 9, 6, 0), 7, 30, 0)
        assertEquals(at(2026, 11, 9, 7, 30), n)
    }

    @Test fun onceAlreadyPassedGoesTomorrow() {
        val n = AlarmTimes.next(at(2026, 11, 9, 8, 0), 7, 30, 0)
        assertEquals(at(2026, 11, 10, 7, 30), n)
    }

    @Test fun exactlyNowGoesTomorrow() {
        val n = AlarmTimes.next(at(2026, 11, 9, 7, 30), 7, 30, 0)
        assertEquals(at(2026, 11, 10, 7, 30), n)
    }

    @Test fun weekdaysSkipWeekend() {
        val weekdays = (1..5).fold(0) { a, i -> a or (1 shl (i - 1)) }
        val friday = at(2026, 11, 13, 9, 0)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val n = AlarmTimes.next(friday, 7, 0, weekdays)
        assertEquals(DayOfWeek.MONDAY, n.dayOfWeek)
        assertEquals(at(2026, 11, 16, 7, 0), n)
    }

    @Test fun singleDaySameWeekday() {
        val monday = 1
        val today = at(2026, 11, 9, 9, 0)
        assertEquals(DayOfWeek.MONDAY, today.dayOfWeek)
        assertEquals(at(2026, 11, 16, 7, 0), AlarmTimes.next(today, 7, 0, monday))
        assertEquals(at(2026, 11, 9, 10, 0), AlarmTimes.next(today, 10, 0, monday))
    }

    @Test fun dstSafeAcrossMonthEnd() {
        val n = AlarmTimes.next(at(2026, 12, 31, 23, 0), 6, 15, AlarmTimes.ALL_DAYS)
        assertEquals(at(2027, 1, 1, 6, 15), n)
    }
}
