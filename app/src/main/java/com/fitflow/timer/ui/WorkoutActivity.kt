package com.fitflow.timer.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fitflow.timer.R
import com.fitflow.timer.core.PhaseType
import com.fitflow.timer.data.AppSettings
import com.fitflow.timer.data.ConfigStore
import com.fitflow.timer.databinding.ActivityWorkoutBinding
import com.fitflow.timer.service.SessionBus
import com.fitflow.timer.service.SessionSnapshot
import com.fitflow.timer.service.TimerService
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class WorkoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutBinding
    private lateinit var settings: AppSettings
    private var lastPhaseKey: String? = null
    private var lastRunning: Boolean? = null
    private var lastSecond: Long = -1L
    private var finishedAnimated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBars()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        settings = ConfigStore(this).loadSettings()
        binding.root.keepScreenOn = settings.keepScreenOn

        val initialColor = ContextCompat.getColor(this, R.color.phase_preparation)
        binding.ringProgress.setPhaseColor(initialColor, animate = false)
        binding.backdrop.setPhaseColor(initialColor, animate = false)

        binding.pauseButton.setOnClickListener {
            TimerService.send(this, TimerService.ACTION_TOGGLE)
        }
        binding.skipButton.setOnClickListener {
            TimerService.send(this, TimerService.ACTION_SKIP)
        }
        binding.stopButton.setOnClickListener {
            TimerService.send(this, TimerService.ACTION_STOP)
            finish()
        }
        binding.rotateButton.setOnClickListener { toggleOrientation() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                TimerService.send(this@WorkoutActivity, TimerService.ACTION_STOP)
                finish()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionBus.snapshot.collect { render(it) }
            }
        }
    }

    private fun applySystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun render(snapshot: SessionSnapshot) {
        if (!snapshot.active) return

        val finished = snapshot.isFinished
        val phaseKey = if (finished) "finished" else snapshot.phaseType.name
        val color = ContextCompat.getColor(
            this,
            if (finished) R.color.brand else phaseColorRes(snapshot.phaseType),
        )

        if (phaseKey != lastPhaseKey) {
            lastPhaseKey = phaseKey
            applyPhase(snapshot, color, finished)
        }

        binding.phaseLabel.setTextColor(color)
        binding.timeText.setTextColor(color)
        binding.counterLabel.text = if (finished) {
            getString(R.string.workout_finished_sub)
        } else {
            counterText(snapshot)
        }
        binding.timeText.text = formatTime(snapshot.remainingMs)
        binding.ringProgress.progress = snapshot.phaseProgress

        val showNext = !finished && snapshot.nextLabel.isNotBlank()
        binding.nextLabel.isVisible = showNext
        binding.nextLabel.text = snapshot.nextLabel

        val lastLog = snapshot.log.lastOrNull().orEmpty()
        binding.logText.isVisible = lastLog.isNotBlank()
        binding.logText.text = lastLog

        if (lastRunning != snapshot.isRunning) {
            lastRunning = snapshot.isRunning
            binding.pauseButton.setText(
                if (snapshot.isRunning) R.string.action_pause else R.string.action_resume
            )
            binding.pauseButton.setIconResource(
                if (snapshot.isRunning) R.drawable.ic_pause else R.drawable.ic_play
            )
            binding.ringContainer.animate()
                .alpha(if (snapshot.isRunning || finished) 1f else 0.55f)
                .setDuration(220L)
                .start()
        }

        val seconds = (snapshot.remainingMs / 1000).coerceAtLeast(0)
        if (seconds != lastSecond) {
            lastSecond = seconds
            if (!finished && seconds in 1..5 && snapshot.phaseType != PhaseType.WORK) {
                pulseTime()
            }
        }
    }

    private fun applyPhase(snapshot: SessionSnapshot, color: Int, finished: Boolean) {
        binding.phaseLabel.text = if (finished) {
            getString(R.string.phase_finished)
        } else {
            getString(phaseNameRes(snapshot.phaseType))
        }
        binding.phaseLabel.setTextColor(color)
        binding.timeText.setTextColor(color)
        binding.ringProgress.setPhaseColor(color, animate = true)
        binding.backdrop.setPhaseColor(color, animate = true)

        binding.phaseLabel.animate().cancel()
        binding.phaseLabel.alpha = 0f
        binding.phaseLabel.translationY = dp(8).toFloat()
        binding.phaseLabel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        pulseRing()

        if (finished) {
            binding.nextLabel.isVisible = false
            binding.ringProgress.progress = 1f
            binding.pauseButton.isEnabled = false
            binding.skipButton.isEnabled = false
            if (!finishedAnimated) {
                finishedAnimated = true
                playFinishAnimation()
            }
        } else {
            binding.pauseButton.isEnabled = true
            binding.skipButton.isEnabled = true
        }
    }

    private fun pulseRing() {
        binding.ringContainer.animate().cancel()
        binding.ringContainer.scaleX = 1f
        binding.ringContainer.scaleY = 1f
        binding.ringContainer.animate()
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(120L)
            .withEndAction {
                binding.ringContainer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun pulseTime() {
        binding.timeText.animate().cancel()
        binding.timeText.scaleX = 1f
        binding.timeText.scaleY = 1f
        binding.timeText.animate()
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(80L)
            .withEndAction {
                binding.timeText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140L)
                    .start()
            }
            .start()
    }

    private fun playFinishAnimation() {
        binding.timeText.animate().cancel()
        binding.timeText.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(160L)
            .withEndAction {
                binding.timeText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun counterText(snapshot: SessionSnapshot): String {
        if (snapshot.totalRounds <= 0) return ""
        if (snapshot.cycle <= 0) return getString(R.string.phase_preparation)
        return getString(
            R.string.round_counter,
            snapshot.cycle,
            snapshot.totalCycles,
            snapshot.round,
            snapshot.totalRounds,
        )
    }

    private fun phaseNameRes(type: PhaseType): Int = when (type) {
        PhaseType.PREPARATION -> R.string.phase_preparation
        PhaseType.WORK -> R.string.phase_work
        PhaseType.REST -> R.string.phase_rest
        PhaseType.REST_BETWEEN_CYCLES -> R.string.phase_cycle_rest
    }

    private fun phaseColorRes(type: PhaseType): Int = when (type) {
        PhaseType.PREPARATION -> R.color.phase_preparation
        PhaseType.WORK -> R.color.phase_work
        PhaseType.REST -> R.color.phase_rest
        PhaseType.REST_BETWEEN_CYCLES -> R.color.phase_cycle_rest
    }

    private fun toggleOrientation() {
        requestedOrientation =
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun formatTime(milliseconds: Long): String {
        val total = (milliseconds / 1000).coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
