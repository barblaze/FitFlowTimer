package com.fitflow.timer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.fitflow.timer.R
import com.fitflow.timer.audio.BeepPlayer
import com.fitflow.timer.core.Announcer
import com.fitflow.timer.core.Clock
import com.fitflow.timer.core.EngineEvent
import com.fitflow.timer.core.PhaseSegment
import com.fitflow.timer.core.PhaseType
import com.fitflow.timer.core.TimerEngine
import com.fitflow.timer.core.VoiceEvent
import com.fitflow.timer.core.WorkoutConfig
import com.fitflow.timer.data.AppSettings
import com.fitflow.timer.data.ConfigStore
import com.fitflow.timer.ui.WorkoutActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale

/**
 * Servicio en primer plano que mantiene el temporizador vivo con la pantalla
 * apagada o la app en segundo plano. Publica el estado en [SessionBus].
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logLines: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val handler = Handler(Looper.getMainLooper())

    private var job: Job? = null
    private var engine: TimerEngine? = null
    private var beeps: BeepPlayer? = null
    private var settings: AppSettings = AppSettings()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                engine?.pause()
                refreshNotification()
            }
            ACTION_RESUME -> {
                engine?.resume()
                refreshNotification()
            }
            ACTION_TOGGLE -> {
                if (engine?.isRunning == true) engine?.pause() else engine?.resume()
                refreshNotification()
            }
            ACTION_SKIP -> {
                engine?.skip()
                refreshNotification()
            }
            ACTION_STOP -> stopEverything()
            else -> {
                val config = intent?.let {
                    IntentCompat.getSerializableExtra(it, EXTRA_CONFIG, WorkoutConfig::class.java)
                }
                if (config != null) startSession(config) else stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        job?.cancel()
        job = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startSession(config: WorkoutConfig) {
        job?.cancel()
        job = null
        engine?.stop()
        releaseWakeLock()
        logLines.clear()

        settings = ConfigStore(this).loadSettings()
        val player = BeepPlayer(settings.soundOutput)
        player.volume = settings.volume
        beeps = player

        createChannel()
        startForegroundCompat(buildNotification(getString(R.string.notification_preparing)))

        val announcer = Announcer { event -> onVoiceEvent(event) }
        val timer = TimerEngine(config, Clock { SystemClock.elapsedRealtime() }, announcer)
        engine = timer
        acquireWakeLock()
        timer.start()

        job = scope.launch {
            while (isActive) {
                val events = timer.tick()
                for (event in events) handleEngineEvent(event, timer)
                publishSnapshot(timer)
                if (timer.isFinished) break
                delay(TICK_MS)
            }
        }
    }

    private fun handleEngineEvent(event: EngineEvent, timer: TimerEngine) {
        when (event) {
            is EngineEvent.Countdown -> {
                if (settings.soundEnabled && settings.countdownBeeps) beeps?.play(TONE_COUNTDOWN, 90)
            }
            is EngineEvent.HalfTime -> {
                if (settings.soundEnabled) beeps?.play(TONE_HALF, 140)
                vibrate()
            }
            is EngineEvent.SegmentChanged -> {
                when (event.segment.type) {
                    PhaseType.WORK -> {
                        if (settings.soundEnabled && settings.startTone) beeps?.play(TONE_GO, 240)
                        vibrate()
                    }
                    PhaseType.REST, PhaseType.REST_BETWEEN_CYCLES -> {
                        if (settings.soundEnabled) beeps?.play(TONE_CHANGE, 150)
                        vibrate()
                    }
                    PhaseType.PREPARATION -> Unit
                }
            }
            EngineEvent.Finished -> {
                if (settings.soundEnabled) {
                    beeps?.playSequence(listOf(TONE_END to 200, TONE_END to 200, TONE_END to 200), 150L)
                }
                vibrate()
                releaseWakeLock()
                refreshNotification()
                scheduleStopAfterFinish()
            }
        }
    }

    private fun onVoiceEvent(event: VoiceEvent) {
        val line = when (event) {
            is VoiceEvent.SessionStarting ->
                getString(R.string.voice_session_starting, formatSeconds(event.totalSeconds))
            is VoiceEvent.WorkStarted ->
                getString(R.string.voice_work_started, event.round, event.cycle)
            is VoiceEvent.HalfTime ->
                getString(R.string.voice_half_time, event.round)
            is VoiceEvent.WorkFinished ->
                getString(R.string.voice_work_finished, event.round)
            is VoiceEvent.RestStarted ->
                getString(R.string.voice_rest_started, event.seconds)
            is VoiceEvent.CycleFinished ->
                getString(R.string.voice_cycle_finished, event.cycle)
            is VoiceEvent.SessionFinished ->
                getString(R.string.voice_session_finished)
        }
        logLines.add(line)
        while (logLines.size > 8) {
            logLines.removeAt(0)
        }
    }

    private fun publishSnapshot(timer: TimerEngine) {
        val segment = timer.currentSegment
        val next = timer.segments.getOrNull(timer.index + 1)
        SessionBus.publish(
            SessionSnapshot(
                active = true,
                phaseType = segment?.type ?: PhaseType.PREPARATION,
                remainingMs = timer.remainingMs(),
                phaseProgress = timer.progress(),
                cycle = segment?.cycle ?: 0,
                totalCycles = segment?.totalCycles ?: 0,
                round = segment?.round ?: 0,
                totalRounds = segment?.totalRounds ?: 0,
                isRunning = timer.isRunning,
                isFinished = timer.isFinished,
                elapsedMs = timer.elapsedMs(),
                totalMs = timer.totalMs,
                nextLabel = next?.let { getString(R.string.next_label, describePhase(it)) } ?: "",
                log = synchronized(logLines) { logLines.toList() },
            )
        )
    }

    private fun describePhase(segment: PhaseSegment): String {
        val name = getString(
            when (segment.type) {
                PhaseType.PREPARATION -> R.string.phase_preparation
                PhaseType.WORK -> R.string.phase_work
                PhaseType.REST -> R.string.phase_rest
                PhaseType.REST_BETWEEN_CYCLES -> R.string.phase_cycle_rest
            }
        )
        return "$name (${formatSeconds(segment.durationSeconds)})"
    }

    private fun currentNotificationText(): String {
        val timer = engine ?: return getString(R.string.notification_preparing)
        if (timer.isFinished) return getString(R.string.notification_done)
        val segment = timer.currentSegment ?: return getString(R.string.notification_preparing)
        val name = getString(
            when (segment.type) {
                PhaseType.PREPARATION -> R.string.phase_preparation
                PhaseType.WORK -> R.string.phase_work
                PhaseType.REST -> R.string.phase_rest
                PhaseType.REST_BETWEEN_CYCLES -> R.string.phase_cycle_rest
            }
        )
        val progress = if (segment.round > 0) {
            getString(
                R.string.round_counter,
                segment.cycle,
                segment.totalCycles,
                segment.round,
                segment.totalRounds,
            )
        } else {
            ""
        }
        return if (progress.isEmpty()) name else "$name · $progress"
    }

    private fun buildNotification(text: String): Notification {
        if (engine?.isFinished == true) return buildFinalNotification()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WorkoutActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() - (engine?.elapsedMs() ?: 0L))

        val running = engine?.isRunning ?: true
        builder.addAction(
            R.drawable.ic_timer,
            getString(if (running) R.string.action_pause else R.string.action_resume),
            serviceIntent(if (running) ACTION_PAUSE else ACTION_RESUME, 1),
        )
        builder.addAction(R.drawable.ic_timer, getString(R.string.action_skip), serviceIntent(ACTION_SKIP, 2))
        builder.addAction(R.drawable.ic_timer, getString(R.string.action_stop), serviceIntent(ACTION_STOP, 3))
        return builder.build()
    }

    private fun buildFinalNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WorkoutActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_done))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    /**
     * Tras terminar la sesion el servicio deja de estar en primer plano, pero la
     * notificacion final permanece en la barra para poder abrir la app.
     */
    private fun scheduleStopAfterFinish() {
        handler.postDelayed({
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }, FINISH_STOP_DELAY_MS)
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getForegroundService(
        this,
        requestCode,
        Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun refreshNotification() {
        startForegroundCompat(buildNotification(currentNotificationText()))
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fitflow:timer").apply {
            setReferenceCounted(false)
            acquire(MAX_SESSION_MS)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            // el wake lock ya estaba liberado
        }
        wakeLock = null
    }

    private fun vibrate() {
        if (!settings.vibration) return
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun stopEverything() {
        job?.cancel()
        job = null
        engine?.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        SessionBus.reset()
        stopSelf()
    }

    private fun formatSeconds(seconds: Int): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    companion object {
        const val ACTION_START = "com.fitflow.timer.action.START"
        const val ACTION_PAUSE = "com.fitflow.timer.action.PAUSE"
        const val ACTION_RESUME = "com.fitflow.timer.action.RESUME"
        const val ACTION_TOGGLE = "com.fitflow.timer.action.TOGGLE"
        const val ACTION_SKIP = "com.fitflow.timer.action.SKIP"
        const val ACTION_STOP = "com.fitflow.timer.action.STOP"
        const val EXTRA_CONFIG = "com.fitflow.timer.extra.CONFIG"

        private const val CHANNEL_ID = "fitflow_timer"
        private const val NOTIFICATION_ID = 4711
        private const val TICK_MS = 100L
        private const val MAX_SESSION_MS = 4L * 60L * 60L * 1000L
        private const val FINISH_STOP_DELAY_MS = 8_000L

        private const val TONE_COUNTDOWN = 880.0
        private const val TONE_GO = 1318.0
        private const val TONE_CHANGE = 660.0
        private const val TONE_HALF = 1046.0
        private const val TONE_END = 523.0

        fun start(context: Context, config: WorkoutConfig) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONFIG, config)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, TimerService::class.java).setAction(action)
            context.startService(intent)
        }
    }
}