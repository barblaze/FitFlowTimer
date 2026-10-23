package com.fitflow.timer.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

class WorkoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBars()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        settings = ConfigStore(this).loadSettings()
        binding.root.keepScreenOn = settings.keepScreenOn

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

        binding.phaseLabel.text = if (snapshot.isFinished) {
            getString(R.string.phase_finished)
        } else {
            getString(phaseNameRes(snapshot.phaseType))
        }
        binding.phaseLabel.setTextColor(ContextCompat.getColor(this, phaseColorRes(snapshot.phaseType)))
        binding.counterLabel.text = counterText(snapshot)
        binding.timeText.text = formatTime(snapshot.remainingMs)
        binding.phaseProgress.setProgressCompat((snapshot.phaseProgress * 100).toInt(), true)
        binding.nextLabel.text = snapshot.nextLabel
        binding.logText.text = snapshot.log.joinToString("\n")
        binding.pauseButton.text = getString(
            if (snapshot.isRunning) R.string.action_pause else R.string.action_resume
        )
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
        requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

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