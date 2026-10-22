package com.fitflow.timer.core

/**
 * Eventos que el motor emite para el asistente de voz.
 * Hoy solo se registran en pantalla (panel de avisos); conectar un motor
 * de texto a voz (TTS) es tan simple como implementar [Announcer].
 */
sealed interface VoiceEvent {
    data class SessionStarting(val totalSeconds: Int) : VoiceEvent
    data class WorkStarted(val round: Int, val cycle: Int) : VoiceEvent
    data class HalfTime(val round: Int, val cycle: Int) : VoiceEvent
    data class WorkFinished(val round: Int) : VoiceEvent
    data class RestStarted(val seconds: Int) : VoiceEvent
    data class CycleFinished(val cycle: Int) : VoiceEvent
    data class SessionFinished(val totalSeconds: Int) : VoiceEvent
}

/**
 * Punto de extension del motor de avisos.
 * Implementaciones: registro en pantalla (hoy) y texto a voz (proximamente).
 */
fun interface Announcer {
    fun announce(event: VoiceEvent)
}