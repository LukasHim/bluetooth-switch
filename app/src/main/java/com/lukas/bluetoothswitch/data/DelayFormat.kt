package com.lukas.bluetoothswitch.data

/**
 * Converts delay durations between seconds and the compact `mm:ss` / `hh:mm:ss`
 * notation used by the control screen.
 */
object DelayFormat {
    private const val MAX_INPUT_LENGTH = 8

    fun format(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
        } else {
            "$minutes:${seconds.twoDigits()}"
        }
    }

    fun parse(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(':')
        if (parts.size > 3) return null

        val values = parts.map { part ->
            if (part.isEmpty() || part.length > 5 || !part.all(Char::isDigit)) return null
            part.toIntOrNull() ?: return null
        }

        if (values.size == 1) {
            return values[0].takeIf { seconds -> seconds in 0..Protocol.MAX_DELAY_SECONDS }
        }

        val (hours, minutes, seconds) = when (values.size) {
            2 -> Triple(0, values[0], values[1])
            else -> Triple(values[0], values[1], values[2])
        }
        if (minutes !in 0..59 || seconds !in 0..59) return null

        val total = hours.toLong() * 3600L + minutes * 60L + seconds
        if (total > Protocol.MAX_DELAY_SECONDS) return null
        return total.toInt()
    }

    fun sanitizeInput(value: String): String =
        value.replace('：', ':')
            .filter { char -> char.isDigit() || char == ':' }
            .take(MAX_INPUT_LENGTH)

    private fun Int.twoDigits(): String = toString().padStart(2, '0')
}
