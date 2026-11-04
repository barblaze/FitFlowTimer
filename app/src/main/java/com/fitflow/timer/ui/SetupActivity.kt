package com.fitflow.timer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.fitflow.timer.R
import com.fitflow.timer.audio.BeepPlayer
import com.fitflow.timer.audio.CoachVoicePlayer
import com.fitflow.timer.core.DurationFormat
import com.fitflow.timer.core.Mode
import com.fitflow.timer.core.VoiceEvent
import com.fitflow.timer.core.WorkoutConfig
import com.fitflow.timer.data.AppSettings
import com.fitflow.timer.data.CoachStyle
import com.fitflow.timer.data.ConfigStore
import com.fitflow.timer.data.SoundOutput
import com.fitflow.timer.databinding.ActivitySetupBinding
import com.fitflow.timer.databinding.DialogDurationBinding
import com.fitflow.timer.databinding.RowParamBinding
import com.fitflow.timer.databinding.RowSwitchBinding
import com.fitflow.timer.service.TimerService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlin.math.roundToInt

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var store: ConfigStore
    private var config: WorkoutConfig = WorkoutConfig()
    private var settings: AppSettings = AppSettings()
    private var bindingValues = false
    private var lastSummary: String? = null
    private var lastCoachTestPlayer: CoachVoicePlayer? = null

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
        val profileLabels = mapOf(
            Mode.TABATA to R.string.profile_1,
            Mode.EMOM to R.string.profile_2,
            Mode.AMRAP to R.string.profile_3,
            Mode.CUSTOM to R.string.profile_4,
        )
        modeCards.forEach { (mode, card) ->
            card.modeTitle.setText(profileLabels.getValue(mode))
            card.modeSubtitle.isVisible = false
        }
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
            getString(R.string.unit_min_sec),
            R.drawable.ic_hourglass,
            0,
            600,
            5,
            config.prepSeconds,
            isDuration = true,
        ) { config = config.copy(prepSeconds = it) }

        bindRow(
            binding.rowWork,
            getString(R.string.param_work),
            getString(R.string.unit_min_sec),
            R.drawable.ic_flame,
            1,
            3600,
            5,
            config.workSeconds,
            isDuration = true,
        ) { config = config.copy(workSeconds = it) }

        bindRow(
            binding.rowRest,
            getString(R.string.param_rest),
            getString(R.string.unit_min_sec),
            R.drawable.ic_moon,
            0,
            600,
            5,
            config.restSeconds,
            isDuration = true,
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
            isDuration = false,
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
            isDuration = false,
        ) { config = config.copy(cycles = it) }

        bindRow(
            binding.rowRestCycles,
            getString(R.string.param_rest_cycles),
            getString(R.string.unit_min_sec),
            R.drawable.ic_moon,
            0,
            600,
            10,
            config.restBetweenCyclesSeconds,
            isDuration = true,
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
        setupCoachSelector()
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

    private fun setupCoachSelector() {
        val block = binding.coachStyleBlock
        val spinner = binding.coachStyleSpinner
        val slider = binding.coachVolumeSlider
        val testBtn = binding.testCoachVoiceButton
        val labels = listOf(
            getString(R.string.coach_style_trainer),
            getString(R.string.coach_style_flaite),
            getString(R.string.coach_style_normal),
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = when (settings.coachStyle) {
            CoachStyle.TRAINER -> 0
            CoachStyle.FLAITE -> 1
            CoachStyle.NORMAL -> 2
        }
        spinner.setSelection(idx)
        slider.value = settings.coachVoiceVolume.coerceIn(0f, 1f)
        block.visibility = if (settings.voiceEnabled) View.VISIBLE else View.GONE
        updateCoachIndicator()
        binding.rowVoice.switchValue.setOnCheckedChangeListener { _, isChecked ->
            if (bindingValues) return@setOnCheckedChangeListener
            settings = settings.copy(voiceEnabled = isChecked)
            block.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateCoachIndicator()
        }
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingValues) return
                settings = when (position) {
                    0 -> settings.copy(coachStyle = CoachStyle.TRAINER)
                    1 -> settings.copy(coachStyle = CoachStyle.FLAITE)
                    2 -> settings.copy(coachStyle = CoachStyle.NORMAL)
                    else -> settings.copy(coachStyle = CoachStyle.TRAINER)
                }
                updateCoachIndicator()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (bindingValues || !fromUser) return@addOnChangeListener
            settings = settings.copy(coachVoiceVolume = value.coerceIn(0f, 1f))
        }
        testBtn.setOnClickListener { testCoachVoice() }
    }

    private fun testCoachVoice() {
        try {
            lastCoachTestPlayer?.release()
            lastCoachTestPlayer = null
        } catch (_: Exception) {}
        val style = when (binding.coachStyleSpinner.selectedItemPosition) {
            0 -> CoachStyle.TRAINER
            1 -> CoachStyle.FLAITE
            2 -> CoachStyle.NORMAL
            else -> CoachStyle.TRAINER
        }
        try {
            val player = CoachVoicePlayer(this, style)
            player.volume = settings.coachVoiceVolume.coerceIn(0f, 1f)
            lastCoachTestPlayer = player
            player.play(VoiceEvent.WorkStarted(1, 1))
            binding.coachStyleBlock.postDelayed({
                try {
                    lastCoachTestPlayer?.release()
                    lastCoachTestPlayer = null
                } catch (_: Exception) {}
            }, 5000)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo reproducir la voz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCoachIndicator() {
        val v = binding.coachCurrentIndicator
        if (!settings.voiceEnabled) {
            v.visibility = View.GONE
            return
        }
        v.text = when (settings.coachStyle) {
            CoachStyle.TRAINER -> getString(R.string.coach_indicator_trainer)
            CoachStyle.FLAITE -> getString(R.string.coach_indicator_flaite)
            CoachStyle.NORMAL -> getString(R.string.coach_indicator_normal)
        }
        v.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            lastCoachTestPlayer?.release()
            lastCoachTestPlayer = null
        } catch (_: Exception) {}
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
        isDuration: Boolean,
        onChange: (Int) -> Unit,
    ) {
        row.paramLabel.text = label
        row.paramUnit.text = unit
        row.paramIcon.setImageResource(iconRes)
        setRowValue(row, current, isDuration)

        if (isDuration) {
            row.paramValue.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = true
                isLongClickable = false
                isCursorVisible = false
                setTextIsSelectable(false)
                inputType = InputType.TYPE_NULL
                contentDescription = getString(R.string.cd_edit_time, label)
                setOnClickListener { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    bounce(view)
                    showDurationDialog(label, readValue(row, true), min, max) { value ->
                        setRowValue(row, value, true)
                        commitValue(value, onChange)
                    }
                }
            }
        } else {
            row.paramValue.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!bindingValues) commit(row, min, max, onChange)
                }
            })
        }

        row.paramMinus.setOnClickListener { view ->
            if (bindingValues) return@setOnClickListener
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val value = (readValue(row, isDuration) - step).coerceAtLeast(min)
            setRowValue(row, value, isDuration)
            if (isDuration) commitValue(value, onChange)
        }
        row.paramPlus.setOnClickListener { view ->
            if (bindingValues) return@setOnClickListener
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val value = (readValue(row, isDuration) + step).coerceAtMost(max)
            setRowValue(row, value, isDuration)
            if (isDuration) commitValue(value, onChange)
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

    private fun setRowValue(row: RowParamBinding, value: Int, isDuration: Boolean) {
        row.paramValue.setText(
            if (isDuration) DurationFormat.formatMinSec(value) else value.toString()
        )
    }

    private fun commit(row: RowParamBinding, min: Int, max: Int, onChange: (Int) -> Unit) {
        val raw = readValue(row, false)
        val value = raw.coerceIn(min, max)
        if (value != raw) {
            bindingValues = true
            setRowValue(row, value, false)
            bindingValues = false
        }
        commitValue(value, onChange)
    }

    private fun commitValue(value: Int, onChange: (Int) -> Unit) {
        onChange(value)
        if (config.mode != Mode.CUSTOM) {
            config = config.copy(mode = Mode.CUSTOM)
            updateModeCards(animated = false)
        }
        refreshSummary()
    }

    private fun readValue(row: RowParamBinding, isDuration: Boolean): Int {
        val text = row.paramValue.text?.toString()?.trim().orEmpty()
        return if (isDuration) {
            DurationFormat.parseMinSec(text) ?: 0
        } else {
            text.toIntOrNull() ?: 0
        }
    }

    private fun showDurationDialog(
        label: String,
        initialSeconds: Int,
        min: Int,
        max: Int,
        onValue: (Int) -> Unit,
    ) {
        val dialogBinding = DialogDurationBinding.inflate(layoutInflater)
        val (initialMinutes, initialRemainder) = DurationFormat.split(initialSeconds)
        dialogBinding.minutesInput.setText(initialMinutes.toString())
        dialogBinding.secondsInput.setText(initialRemainder.toString().padStart(2, '0'))
        dialogBinding.durationRange.text = getString(
            R.string.duration_range,
            DurationFormat.formatMinSec(min),
            DurationFormat.formatMinSec(max),
        )

        fun updatePreview() {
            val minutes = dialogBinding.minutesInput.text?.toString()?.toIntOrNull() ?: 0
            val seconds = dialogBinding.secondsInput.text?.toString()?.toIntOrNull() ?: 0
            dialogBinding.durationPreview.setTime(
                DurationFormat.formatMinSec(DurationFormat.toSeconds(minutes, seconds))
            )
        }

        dialogBinding.durationPreview.setTimeColor(
            ContextCompat.getColor(this, R.color.brand),
            animate = false,
        )
        updatePreview()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        dialogBinding.minutesInput.addTextChangedListener(watcher)
        dialogBinding.secondsInput.addTextChangedListener(watcher)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(label)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_accept, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutesText = dialogBinding.minutesInput.text?.toString()?.trim().orEmpty()
                val secondsText = dialogBinding.secondsInput.text?.toString()?.trim().orEmpty()
                val minutes = minutesText.toIntOrNull()
                val seconds = secondsText.toIntOrNull()
                var valid = true

                if (minutesText.isNotEmpty() && minutes == null) {
                    dialogBinding.minutesLayout.error = getString(R.string.duration_invalid)
                    valid = false
                } else {
                    dialogBinding.minutesLayout.error = null
                }
                if (secondsText.isNotEmpty() && seconds == null) {
                    dialogBinding.secondsLayout.error = getString(R.string.duration_invalid)
                    valid = false
                } else {
                    dialogBinding.secondsLayout.error = null
                }
                if (!valid) return@setOnClickListener

                val total = DurationFormat.toSeconds(minutes ?: 0, seconds ?: 0)
                val clamped = total.coerceIn(min, max)
                onValue(clamped)
                dialog.dismiss()
                if (clamped != total) {
                    Toast.makeText(
                        this@SetupActivity,
                        getString(R.string.duration_adjusted, DurationFormat.formatMinSec(clamped)),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        dialog.show()
    }

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
        setRowValue(binding.rowPrep, config.prepSeconds, true)
        setRowValue(binding.rowWork, config.workSeconds, true)
        setRowValue(binding.rowRest, config.restSeconds, true)
        setRowValue(binding.rowRounds, config.rounds, false)
        setRowValue(binding.rowCycles, config.cycles, false)
        setRowValue(binding.rowRestCycles, config.restBetweenCyclesSeconds, true)
        bindingValues = false
        updateModeCards(animated = true)
        refreshSummary()
    }

    private fun refreshSummary() {
        val total = DurationFormat.formatMinSec(config.totalSeconds())
        binding.totalText.text = total
        binding.structureText.text = getString(
            R.string.summary_structure,
            config.rounds,
            config.cycles,
            DurationFormat.formatMinSec(config.workSeconds),
            DurationFormat.formatMinSec(config.restSeconds),
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
}
