package fi.kansalaisuuskoe.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the daily reminder says, if anything. A learner who has already practised today is left alone. */
sealed interface ReminderMessage {
    data class Reviews(val count: Int) : ReminderMessage
    data object NewLesson : ReminderMessage
}

object StudyReminder {
    const val DEFAULT_MINUTE_OF_DAY = 18 * 60

    /** Practice in either study language counts; the due count is for the selected one. */
    fun message(all: LearningSnapshot, study: StudyLanguage, now: Long, zone: ZoneId = ZoneId.systemDefault()): ReminderMessage? {
        fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = day(now)
        if (all.attempts.any { day(it.answeredAt) == today }) return null
        val plan = DailyLearningPlanner.plan(all.forLanguage(study), now)
        return when {
            plan.totalDue > 0 -> ReminderMessage.Reviews(plan.totalDue)
            plan.items.isNotEmpty() -> ReminderMessage.NewLesson
            else -> null
        }
    }

    /** The next wall-clock occurrence of [minuteOfDay] strictly after [now]; a DST gap moves it forward. */
    fun nextAt(minuteOfDay: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var next = ZonedDateTime.of(current.toLocalDate(), time, zone)
        if (!next.isAfter(current)) next = ZonedDateTime.of(current.toLocalDate().plusDays(1), time, zone)
        return next.toInstant().toEpochMilli()
    }
}
