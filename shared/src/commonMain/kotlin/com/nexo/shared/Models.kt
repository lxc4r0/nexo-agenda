package com.nexo.shared

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable data class Preferences(
    val dark: Boolean = false,
    val color: String = "Verde",
    val alerts: Boolean = true,
    val sound: Boolean = true,
    val minutes: Int = 5,
)
@Serializable data class Employee(val id: String, val name: String, val username: String, val preferences: Preferences = Preferences(), val password: String = "Nexo2026!")
@Serializable data class Appointment(
    val id: String = "", val ownerId: String = "", val title: String,
    val start: String, val end: String, val place: String = "", val notes: String = "",
)
@Serializable data class Room(val id: String, val name: String, val creatorId: String, val members: List<Member>)
@Serializable data class Member(val userId: String, val accepted: Boolean)
@Serializable data class NewRoom(val name: String, val invitees: List<String>)
@Serializable data class Message(val id: String, val roomId: String, val authorId: String, val text: String, val sentAt: String)

fun validPassword(password: String): Boolean = password.length >= 8 && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
fun localStart(appointment: Appointment): LocalDateTime = Instant.parse(appointment.start).toLocalDateTime(TimeZone.currentSystemDefault())
fun localEnd(appointment: Appointment): LocalDateTime = Instant.parse(appointment.end).toLocalDateTime(TimeZone.currentSystemDefault())
fun dateLabel(date: LocalDate): String = "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}/${date.year}"
fun timeLabel(time: LocalTime): String = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
fun monthGrid(date: LocalDate): List<LocalDate> {
    val first = LocalDate(date.year, date.month, 1)
    val start = first.minus(first.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    return List(42) { start.plus(it, DateTimeUnit.DAY) }
}
fun weekDates(date: LocalDate): List<LocalDate> {
    val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    return List(7) { monday.plus(it, DateTimeUnit.DAY) }
}
fun appointmentOnDay(appointment: Appointment, date: LocalDate): Boolean {
    val zone = TimeZone.currentSystemDefault()
    val startOfDay = date.atStartOfDayIn(zone)
    val nextDay = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return Instant.parse(appointment.start) < nextDay && Instant.parse(appointment.end) > startOfDay
}
fun reminderKey(appointment: Appointment, minutes: Int) = "${appointment.id}:${appointment.start}:$minutes"
fun reminderDue(appointment: Appointment, now: Instant, minutes: Int): Boolean {
    val remaining = Instant.parse(appointment.start).toEpochMilliseconds() - now.toEpochMilliseconds()
    return remaining > 0 && remaining <= minutes * 60_000L
}
