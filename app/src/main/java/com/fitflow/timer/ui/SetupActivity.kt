package com.fitflow.timer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.fitflow.timer.R
import com.fitflow.timer.audio.BeepPlayer
import com.fitflow.timer.core.Mode
import com.fitflow.timer.core.WorkoutConfig
import com.fitflow.timer.data.AppSettings
import com.fitflow.timer.data.ConfigStore
import com.fitflow.timer.data.OrientationMode
import com.fitflow.timer.data.SoundOutput
import com.fitflow.timer.databinding.ActivitySetupBinding
import com.fitflow.timer.databinding.RowParamBinding
import com.fitflow.timer.databinding.RowSwitchBinding
import com.fitflow.timer.service.TimerService
import com.google.android.material.tabs.TabLayout
import java.util.Locale
import kotlin.math.roundToInt

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var store: ConfigStore
    private var config: WorkoutConfig = WorkoutConfig()
    private var settings: AppSettings = AppSettings()
    private var bindingValues = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBars()
        applyOrientation()

        store = ConfigStore(this)
        config = store.loadConfig()
        settings = store.loadSettings()

        setupTabs()
        setupModes()
        setupParameters()
        setupSettings()
        refreshSummary()

        onBackPressedDispatcher.addCallback(this) {
            moveTaskToBack(true)
        }
    }

    private fun applySystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }

    private fun applyOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.trainingScroll.isVisible = tab.position == 0
                binding.settingsScroll.isVisible = tab.position == 1
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
    }

    private fun setupModes() {
        bindingValues = true
        binding.modeGroup.check(buttonForMode(config.mode))
        bindingValues = false

        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || bindingValues) return@addOnButtonCheckedListener
            applyMode(modeForButton(checkedId))
        }
    }

    private fun setupParameters() {
        bindRow(binding.rowPrep, getString(R.string.param_prep), 0, 600, 5, config.prepSeconds) {
            config = config.copy(prepSeconds = it)
        }
        bindRow(binding.rowWork, getString(R.string.param_work), 1, 3600, 5, config.workSeconds) {
            config = config.copy(workSeconds = it)
        }
        bindRow(binding.rowRest, getString(R.string.param_rest), 0, 600, 5, config.restSeconds) {
            config = config.copy(restSeconds = it)
        }
        bindRow(binding.rowRounds, getString(R.string.param_rounds), 1, 99, 1, config.rounds) {
            config = config.copy(rounds = it)
        }
        bindRow(binding.rowCycles, getString(R.string.param_cycles), 1, 99, 1, config.cycles) {
            config = config.copy(cycles = it)
        }
        bindRow(binding.rowRestCycles, getString(R.string.param_rest_cycles), 0, 600, 10, config.restBetweenCyclesSeconds) {
            config = config.copy(restBetweenCyclesSeconds = it)
        }
    }

    private fun setupSettings() {
        bindingValues = true
        bindSwitch(binding.rowSound, getString(R.string.setting_sound), settings.soundEnabled) {
            settings = settings.copy(soundEnabled = it)
        }
        bindSwitch(binding.rowCountdownBeep, getString(R.string.setting_countdown_beeps), settings.countdownBeeps) {
            settings = settings.copy(countdownBeeps = it)
        }
        bindSwitch(binding.rowStartTone, getString(R.string.setting_start_tone), settings.startTone) {
            settings = settings.copy(startTone = it)
        }
        bindSwitch(binding.rowKeepScreenOn, getString(R.string.setting_keep_screen_on), settings.keepScreenOn) {
            settings = settings.copy(keepScreenOn = it)
        }
        bindSwitch(binding.rowVibration, getString(R.string.setting_vibration), settings.vibration) {
            settings = settings.copy(vibration = it)
        }
        bindSwitch(binding.rowVoice, getString(R.string.setting_voice), settings.voiceEnabled) {
            settings = settings.copy(voiceEnabled = it)
        }
        binding.voiceHint.isVisible = true

        binding.outputGroup.check(
            if (settings.soundOutput == SoundOutput.MEDIA) R.id.outputMedia else R.id.outputSystem
        )
        binding.outputGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || bindingValues) return@addOnButtonCheckedListener
            settings = settings.copy(
                soundOutput = if (checkedId == R.id.outputSystem) SoundOutput.SYSTEM else SoundOutput.MEDIA
            )
        }

        val sliderValue = ((settings.volume * 100f).roundToInt() / 5 * 5).coerceIn(0, 100)
        binding.volumeSlider.value = sliderValue.toFloat()
        binding.volumeSlider.addOnChangeListener { _, value, _ ->
            if (!bindingValues) {
                settings = settings.copy(volume = value / 100f)
            }
        }

        binding.testSoundButton.setOnClickListener { testSound() }
        binding.startButton.setOnClickListener { startWorkout() }
        bindingValues = false
    }

    private fun bindRow(
        row: RowParamBinding,
        label: String,
        min: Int,
        max: Int,
        step: Int,
        current: Int,
        onChange: (Int) -> Unit,
    ) {
        row.paramLabel.text = label
        row.paramValue.setText(current.toString())
        row.paramValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!bindingValues) commit(row, min, max, onChange)
            }
        })
        row.paramMinus.setOnClickListener {
            if (bindingValues) return@setOnClickListener
            row.paramValue.setText((readValue(row) - step).coerceAtLeast(min).toString())
        }
        row.paramPlus.setOnClickListener {
            if (bindingValues) return@setOnClickListener
            row.paramValue.setText((readValue(row) + step).coerceAtMost(max).toString())
        }
    }

    private fun bindSwitch(
        row: RowSwitchBinding,
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        row.switchLabel.text = label
        row.switchValue.isChecked = checked
        row.switchValue.setOnCheckedChangeListener { _, value ->
            if (!bindingValues) onChange(value)
        }
    }

    private fun commit(row: RowParamBinding, min: Int, max: Int, onChange: (Int) -> Unit) {
        val raw = readValue(row)
        val value = raw.coerceIn(min, max)
        if (value != raw) {
            bindingValues = true
            row.paramValue.setText(value.toString())
            bindingValues = false
        }
        onChange(value)
        if (config.mode != Mode.CUSTOM) {
            config = config.copy(mode = Mode.CUSTOM)
            bindingValues = true
            binding.modeGroup.check(R.id.modeCustom)
            bindingValues = false
        }
        refreshSummary()
    }

    private fun readValue(row: RowParamBinding): Int =
        row.paramValue.text?.toString()?.trim()?.toIntOrNull() ?: 0

    private fun applyMode(mode: Mode) {
        config = when (mode) {
            Mode.TABATA -> WorkoutConfig(
                mode = Mode.TABATA,
                prepSeconds = 10,
                workSeconds = 20,
                restSeconds = 10,
                rounds = 8,
                cycles = 1,
                restBetweenCyclesSeconds = 0,
                announceHalfway = true,
            )
            Mode.EMOM -> WorkoutConfig(
                mode = Mode.EMOM,
                prepSeconds = 10,
                workSeconds = 60,
                restSeconds = 0,
                rounds = 10,
                cycles = 1,
                restBetweenCyclesSeconds = 0,
                announceHalfway = true,
            )
            Mode.AMRAP -> WorkoutConfig(
                mode = Mode.AMRAP,
                prepSeconds = 10,
                workSeconds = 300,
                restSeconds = 0,
                rounds = 1,
                cycles = 1,
                restBetweenCyclesSeconds = 0,
                announceHalfway = false,
            )
            Mode.CUSTOM -> config.copy(mode = Mode.CUSTOM)
        }
        bindingValues = true
        binding.rowPrep.paramValue.setText(config.prepSeconds.toString())
        binding.rowWork.paramValue.setText(config.workSeconds.toString())
        binding.rowRest.paramValue.setText(config.restSeconds.toString())
        binding.rowRounds.paramValue.setText(config.rounds.toString())
        binding.rowCycles.paramValue.setText(config.cycles.toString())
        binding.rowRestCycles.paramValue.setText(config.restBetweenCyclesSeconds.toString())
        bindingValues = false
        refreshSummary()
    }

    private fun refreshSummary() {
        binding.totalText.text = getString(R.string.summary_total, formatSeconds(config.totalSeconds()))
        binding.warningText.isVisible = config.cycles > 1 && config.restBetweenCyclesSeconds == 0
        binding.halfwayText.isVisible = config.announceHalfway
    }

    private fun testSound() {
        if (!settings.soundEnabled) return
        val player = BeepPlayer(settings.soundOutput)
        player.volume = settings.volume
        player.play(1318.0, 240)
    }

    private fun startWorkout() {
        store.saveConfig(config)
        store.saveSettings(settings)
        requestNotificationsIfNeeded()
        TimerService.start(this, config)
        startActivity(Intent(this, WorkoutActivity::class.java))
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun buttonForMode(mode: Mode): Int = when (mode) {
        Mode.TABATA -> R.id.modeTabata
        Mode.EMOM -> R.id.modeEmom
        Mode.AMRAP -> R.id.modeAmrap
        Mode.CUSTOM -> R.id.modeCustom
    }

    private fun modeForButton(id: Int): Mode = when (id) {
        R.id.modeTabata -> Mode.TABATA
        R.id.modeEmom -> Mode.EMOM
        R.id.modeAmrap -> Mode.AMRAP
        else -> Mode.CUSTOM
    }

    private fun formatSeconds(seconds: Int): String =
        String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}