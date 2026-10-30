package com.fitflow.timer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.fitflow.timer.R
import com.fitflow.timer.audio.BeepPlayer
import com.fitflow.timer.core.Mode
import com.fitflow.timer.core.WorkoutConfig
import com.fitflow.timer.data.AppSettings
import com.fitflow.timer.data.ConfigStore
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
    private var lastSummary: String? = null

    private val modeCards by lazy {
        mapOf(
            Mode.TABATA to binding.cardTabata,
            Mode.EMOM to binding.cardEmom,
            Mode.AMRAP to binding.cardAmrap,
            Mode.CUSTOM to binding.cardCustom,
        )
    }

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
        playEntrance()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    private fun applySystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun applyOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTab(tab.position, animate = true)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
    }

    private fun showTab(position: Int, animate: Boolean) {
        val showTraining = position == 0
        val incoming: View = if (showTraining) binding.trainingScroll else binding.settingsScroll
        val outgoing: View = if (showTraining) binding.settingsScroll else binding.trainingScroll
        if (incoming.isVisible && !outgoing.isVisible) return

        if (!animate) {
            outgoing.visibility = View.GONE
            incoming.visibility = View.VISIBLE
            return
        }

        val direction = if (showTraining) 1 else -1
        outgoing.animate().cancel()
        incoming.animate().cancel()

        outgoing.animate()
            .alpha(0f)
            .translationX(-direction * dp(24).toFloat())
            .setDuration(140L)
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.alpha = 1f
                outgoing.translationX = 0f
            }
            .start()

        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = direction * dp(24).toFloat()
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setupModes() {
        bindingValues = true
        updateModeCards(animated = false)
        bindingValues = false

        modeCards.forEach { (mode, card) ->
            card.root.setOnClickListener { view ->
                if (bindingValues) return@setOnClickListener
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (config.mode != mode) {
                    applyMode(mode)
                    bounce(view)
                }
            }
        }
    }

    private fun updateModeCards(animated: Boolean = true) {
        val brand = ContextCompat.getColor(this, R.color.brand)
        val muted = ContextCompat.getColor(this, R.color.text_secondary)

        modeCards.forEach { (mode, card) ->
            val selected = config.mode == mode
            card.root.setCardBackgroundColor(
                ContextCompat.getColor(this, if (selected) R.color.brand_surface else R.color.surface)
            )
            card.root.setStrokeColor(
                ContextCompat.getColor(this, if (selected) R.color.brand else R.color.outline)
            )
            card.root.setStrokeWidth(dp(if (selected) 2 else 1))
            card.root.setCardElevation(dp(if (selected) 6 else 0).toFloat())
            card.modeIcon.imageTintList = ColorStateList.valueOf(if (selected) brand else muted)
            card.modeSubtitle.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.brand_light else R.color.text_secondary)
            )
            card.modeCheck.isVisible = selected

            if (animated && selected) {
                card.modeCheck.alpha = 0f
                card.modeCheck.scaleX = 0.4f
                card.modeCheck.scaleY = 0.4f
                card.modeCheck.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .start()
            }
        }
    }

    private fun bounce(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(70L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun setupParameters() {
        bindRow(
            binding.rowPrep,
            getString(R.string.param_prep),
            getString(R.string.unit_seconds),
            R.drawable.ic_hourglass,
            0,
            600,
            5,
            config.prepSeconds,
        ) { config = config.copy(prepSeconds = it) }

        bindRow(
            binding.rowWork,
            getString(R.string.param_work),
            getString(R.string.unit_seconds),
            R.drawable.ic_flame,
            1,
            3600,
            5,
            config.workSeconds,
        ) { config = config.copy(workSeconds = it) }

        bindRow(
            binding.rowRest,
            getString(R.string.param_rest),
            getString(R.string.unit_seconds),
            R.drawable.ic_moon,
            0,
            600,
            5,
            config.restSeconds,
        ) { config = config.copy(restSeconds = it) }

        bindRow(
            binding.rowRounds,
            getString(R.string.param_rounds),
            getString(R.string.unit_rounds),
            R.drawable.ic_repeat,
            1,
            99,
            1,
            config.rounds,
        ) { config = config.copy(rounds = it) }

        bindRow(
            binding.rowCycles,
            getString(R.string.param_cycles),
            getString(R.string.unit_cycles),
            R.drawable.ic_cycle,
            1,
            99,
            1,
            config.cycles,
        ) { config = config.copy(cycles = it) }

        bindRow(
            binding.rowRestCycles,
            getString(R.string.param_rest_cycles),
            getString(R.string.unit_seconds),
            R.drawable.ic_moon,
            0,
            600,
            10,
            config.restBetweenCyclesSeconds,
        ) { config = config.copy(restBetweenCyclesSeconds = it) }
    }

    private fun setupSettings() {
        bindingValues = true
        bindSwitch(
            binding.rowSound,
            getString(R.string.setting_sound),
            R.drawable.ic_sound,
            settings.soundEnabled,
        ) { settings = settings.copy(soundEnabled = it) }
        bindSwitch(
            binding.rowCountdownBeep,
            getString(R.string.setting_countdown_beeps),
            R.drawable.ic_timer,
            settings.countdownBeeps,
        ) { settings = settings.copy(countdownBeeps = it) }
        bindSwitch(
            binding.rowStartTone,
            getString(R.string.setting_start_tone),
            R.drawable.ic_music,
            settings.startTone,
        ) { settings = settings.copy(startTone = it) }
        bindSwitch(
            binding.rowKeepScreenOn,
            getString(R.string.setting_keep_screen_on),
            R.drawable.ic_screen,
            settings.keepScreenOn,
        ) { settings = settings.copy(keepScreenOn = it) }
        bindSwitch(
            binding.rowVibration,
            getString(R.string.setting_vibration),
            R.drawable.ic_vibration,
            settings.vibration,
        ) { settings = settings.copy(vibration = it) }
        bindSwitch(
            binding.rowVoice,
            getString(R.string.setting_voice),
            R.drawable.ic_voice,
            settings.voiceEnabled,
        ) { settings = settings.copy(voiceEnabled = it) }
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
        binding.volumeValue.text = getString(R.string.volume_value, sliderValue)
        binding.volumeSlider.addOnChangeListener { _, value, _ ->
            val percent = (value / 5f).roundToInt() * 5
            binding.volumeValue.text = getString(R.string.volume_value, percent)
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
        unit: String,
        iconRes: Int,
        min: Int,
        max: Int,
        step: Int,
        current: Int,
        onChange: (Int) -> Unit,
    ) {
        row.paramLabel.text = label
        row.paramUnit.text = unit
        row.paramIcon.setImageResource(iconRes)
        row.paramValue.setText(current.toString())
        row.paramValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!bindingValues) commit(row, min, max, onChange)
            }
        })
        row.paramMinus.setOnClickListener { view ->
            if (bindingValues) return@setOnClickListener
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            row.paramValue.setText((readValue(row) - step).coerceAtLeast(min).toString())
        }
        row.paramPlus.setOnClickListener { view ->
            if (bindingValues) return@setOnClickListener
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            row.paramValue.setText((readValue(row) + step).coerceAtMost(max).toString())
        }
    }

    private fun bindSwitch(
        row: RowSwitchBinding,
        label: String,
        iconRes: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        row.switchLabel.text = label
        row.switchIcon.setImageResource(iconRes)
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
            updateModeCards(animated = false)
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
                announceHalfway = true,
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
        updateModeCards(animated = true)
        refreshSummary()
    }

    private fun refreshSummary() {
        val total = formatSeconds(config.totalSeconds())
        binding.totalText.text = total
        binding.structureText.text = getString(
            R.string.summary_structure,
            config.rounds,
            config.cycles,
            formatSeconds(config.workSeconds),
            formatSeconds(config.restSeconds),
        )
        binding.warningText.isVisible = config.cycles > 1 && config.restBetweenCyclesSeconds == 0
        binding.halfwayText.isVisible = true

        if (lastSummary != null && lastSummary != total) {
            binding.totalText.animate().cancel()
            binding.totalText.scaleX = 1f
            binding.totalText.scaleY = 1f
            binding.totalText.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(90L)
                .withEndAction {
                    binding.totalText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .start()
                }
                .start()
        }
        lastSummary = total
    }

    private fun playEntrance() {
        val views: List<View> = listOf(
            binding.header,
            binding.tabLayout,
            binding.setupTitle,
            binding.setupSubtitle,
            binding.sectionModes,
            binding.cardTabata.root,
            binding.cardEmom.root,
            binding.cardAmrap.root,
            binding.cardCustom.root,
            binding.sectionParams,
            binding.paramsCard,
            binding.summaryCard,
            binding.startButton,
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(16).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(30L * index)
                .setDuration(320L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
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
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun formatSeconds(seconds: Int): String =
        String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
