package com.fitflow.timer.service

import com.fitflow.timer.core.PhaseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de la sesion que la UI observa. El servicio publica aqui y la
 * pantalla de entrenamiento se redibuja con el ultimo valor, de forma que
 * rotar el movil o recrear la actividad no pierde nada.
 */
data class SessionSnapshot(
    val active: Boolean = false,
    val phaseType: PhaseType = PhaseType.PREPARATION,
    val remainingMs: Long = 0L,
    val phaseProgress: Float = 0f,
    val cycle: Int = 0,
    val totalCycles: Int = 0,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val elapsedMs: Long = 0L,
    val totalMs: Long = 0L,
    val nextLabel: String = "",
    val log: List<String> = emptyList(),
)

object SessionBus {

    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot

    fun publish(value: SessionSnapshot) {
        _snapshot.value = value
    }

    fun reset() {
        _snapshot.value = SessionSnapshot()
    }
}