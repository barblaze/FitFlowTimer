package com.fitflow.timer.data

import android.content.Context
import com.fitflow.timer.R
import com.fitflow.timer.core.Mode
import com.fitflow.timer.core.WorkoutConfig
import org.json.JSONObject

enum class OrientationMode { AUTOMATIC, PORTRAIT, LANDSCAPE }

/** Media = se mezcla con la musica de otras apps. Sistema = volumen independiente. */
enum class SoundOutput { MEDIA, SYSTEM }

enum class CoachStyle(val folder: String, val labelRes: Int, val indicatorRes: Int) {
    TRAINER("entrenador", R.string.coach_style_trainer, R.string.coach_indicator_trainer),
    FLAITE("flaite", R.string.coach_style_flaite, R.string.coach_indicator_flaite),
    NORMAL("normal", R.string.coach_style_normal, R.string.coach_indicator_normal),
    MEMES("memes", R.string.coach_style_memes, R.string.coach_indicator_memes)
}

data class AppSettings(
    val orientation: OrientationMode = OrientationMode.AUTOMATIC,
    val soundEnabled: Boolean = true,
    val soundOutput: SoundOutput = SoundOutput.MEDIA,
    val volume: Float = 0.8f,
    val countdownBeeps: Boolean = true,
    val startTone: Boolean = true,
    val keepScreenOn: Boolean = true,
    val vibration: Boolean = true,
    val voiceEnabled: Boolean = false,
    val coachStyle: CoachStyle = CoachStyle.TRAINER,
    val coachVoiceVolume: Float = 1.0f,
)

class ConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("fitflow", Context.MODE_PRIVATE)

    /** Guarda la configuracion como la ultima usada Y dentro de su propio perfil. */
    fun saveConfig(config: WorkoutConfig) {
        val raw = toJson(config)
        prefs.edit()
            .putString(KEY_CONFIG, raw)
            .putString(profileKey(config.mode), raw)
            .apply()
    }

    /** Ultima configuracion usada (incluye el perfil que estaba seleccionado). */
    fun loadConfig(): WorkoutConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return WorkoutConfig()
        val config = fromJson(raw, Mode.TABATA)
        // Migracion: si ese perfil aun no tenia guardado propio, lo siembra con lo ultimo usado
        if (!prefs.contains(profileKey(config.mode))) {
            prefs.edit().putString(profileKey(config.mode), raw).apply()
        }
        return config
    }

    /** Configuracion guardada de un perfil; si nunca se personalizo, su preset inicial. */
    fun loadProfile(mode: Mode): WorkoutConfig {
        val raw = prefs.getString(profileKey(mode), null) ?: return WorkoutConfig.preset(mode)
        return fromJson(raw, mode).copy(mode = mode)
    }

    private fun toJson(config: WorkoutConfig): String = JSONObject().apply {
        put("mode", config.mode.name)
        put("prep", config.prepSeconds)
        put("work", config.workSeconds)
        put("rest", config.restSeconds)
        put("rounds", config.rounds)
        put("cycles", config.cycles)
        put("restCycles", config.restBetweenCyclesSeconds)
        put("halfway", config.announceHalfway)
    }.toString()

    private fun fromJson(raw: String, fallbackMode: Mode): WorkoutConfig = try {
        val json = JSONObject(raw)
        WorkoutConfig(
            mode = enumValueOrDefault(json.optString("mode"), fallbackMode),
            prepSeconds = json.optInt("prep", 10),
            workSeconds = json.optInt("work", 20),
            restSeconds = json.optInt("rest", 10),
            rounds = json.optInt("rounds", 8),
            cycles = json.optInt("cycles", 1),
            restBetweenCyclesSeconds = json.optInt("restCycles", 0),
            announceHalfway = json.optBoolean("halfway", false),
        )
    } catch (error: Exception) {
        WorkoutConfig.preset(fallbackMode)
    }

    private fun profileKey(mode: Mode) = "profile_${mode.name}"

    fun saveSettings(settings: AppSettings) {
        val json = JSONObject().apply {
            put("orientation", settings.orientation.name)
            put("soundEnabled", settings.soundEnabled)
            put("soundOutput", settings.soundOutput.name)
            put("volume", settings.volume.toDouble())
            put("countdownBeeps", settings.countdownBeeps)
            put("startTone", settings.startTone)
            put("keepScreenOn", settings.keepScreenOn)
            put("vibration", settings.vibration)
            put("voiceEnabled", settings.voiceEnabled)
            put("coachStyle", settings.coachStyle.name)
            put("coachVoiceVolume", settings.coachVoiceVolume.toDouble())
        }
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            val json = JSONObject(raw)
            AppSettings(
                orientation = enumValueOrDefault(json.optString("orientation"), OrientationMode.AUTOMATIC),
                soundEnabled = json.optBoolean("soundEnabled", true),
                soundOutput = enumValueOrDefault(json.optString("soundOutput"), SoundOutput.MEDIA),
                volume = json.optDouble("volume", 0.8).toFloat().coerceIn(0f, 1f),
                countdownBeeps = json.optBoolean("countdownBeeps", true),
                startTone = json.optBoolean("startTone", true),
                keepScreenOn = json.optBoolean("keepScreenOn", true),
                vibration = json.optBoolean("vibration", true),
                voiceEnabled = json.optBoolean("voiceEnabled", false),
                coachStyle = enumValueOrDefault(json.optString("coachStyle"), CoachStyle.TRAINER),
                coachVoiceVolume = json.optDouble("coachVoiceVolume", 1.0).toFloat().coerceIn(0f, 1f),
            )
        } catch (error: Exception) {
            AppSettings()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_CONFIG = "config"
        const val KEY_SETTINGS = "settings"
    }
}