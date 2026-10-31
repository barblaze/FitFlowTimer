package com.fitflow.timer.core

import java.util.Locale

/** Utilidades para mostrar y editar duraciones como minutos:segundos. */
object DurationFormat {

    fun formatMinSec(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    fun split(totalSeconds: Int): Pair<Int, Int> {
        val safe = totalSeconds.coerceAtLeast(0)
        return (safe / 60) to (safe % 60)
    }

    fun toSeconds(minutes: Int, seconds: Int): Int =
        minutes.coerceAtLeast(0) * 60 + seconds.coerceAtLeast(0)

    fun parseMinSec(value: String): Int? {
        val parts = value.trim().split(':')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.coerceAtLeast(0)
            2 -> {
                val minutes = parts[0].toIntOrNull() ?: return null
                val seconds = parts[1].toIntOrNull() ?: return null
                if (minutes < 0 || seconds < 0) null else toSeconds(minutes, seconds)
            }
            else -> null
        }
    }
}
