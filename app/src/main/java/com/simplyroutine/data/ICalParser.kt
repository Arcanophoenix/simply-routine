package com.simplyroutine.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ICalEvent(
    val title: String,
    val date: LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
)

object ICalParser {
    fun parse(ical: String): ICalEvent? {
        // RFC 5545: unfold continuation lines (lines starting with space/tab)
        val lines = buildList<String> {
            for (raw in ical.lines()) {
                val line = raw.trimEnd('\r')
                if ((line.startsWith(' ') || line.startsWith('\t')) && isNotEmpty()) {
                    set(lastIndex, get(lastIndex) + line.trimStart())
                } else {
                    add(line)
                }
            }
        }

        // Collect the first value for each property key, stripping param sections
        // e.g. "DTSTART;TZID=Europe/London:..." → key "DTSTART"
        val props = mutableMapOf<String, String>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val key = line.substring(0, colon).split(';')[0].uppercase().trim()
            val value = line.substring(colon + 1)
            if (key !in props) props[key] = value
        }

        val title = props["SUMMARY"]?.unescapeIcal()?.trim()?.ifBlank { null } ?: return null
        val dtStartRaw = props["DTSTART"] ?: return null
        val dtEndRaw = props["DTEND"]

        val (startDate, startMin) = parseDateTime(dtStartRaw) ?: return null
        val endMin = dtEndRaw?.let { parseDateTime(it)?.second } ?: (startMin + 60)

        return ICalEvent(
            title = title,
            date = startDate,
            startMinutes = startMin.coerceIn(0, 1439),
            endMinutes = endMin.coerceIn(1, 1440).let { if (it <= startMin) startMin + 60 else it },
        )
    }

    private val dtFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

    private fun parseDateTime(value: String): Pair<LocalDate, Int>? = try {
        val v = value.trim()
        when {
            v.length == 8 -> {
                // All-day event: YYYYMMDD — default to 9 AM
                val date = LocalDate.parse(v, dateFormatter)
                Pair(date, 9 * 60)
            }
            v.endsWith('Z') -> {
                // UTC: convert to device local time
                val dt = LocalDateTime.parse(v.dropLast(1), dtFormatter)
                    .toInstant(ZoneOffset.UTC)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                Pair(dt.toLocalDate(), dt.hour * 60 + dt.minute)
            }
            else -> {
                // Floating or TZID-qualified: treat as local
                val dt = LocalDateTime.parse(v.take(15), dtFormatter)
                Pair(dt.toLocalDate(), dt.hour * 60 + dt.minute)
            }
        }
    } catch (_: Exception) { null }

    private fun String.unescapeIcal() =
        replace("\\n", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
