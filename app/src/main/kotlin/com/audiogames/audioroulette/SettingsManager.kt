package com.audiogames.audioroulette

import android.content.Context
import android.provider.Settings

enum class Dificuldade { FACIL, NORMAL, DIFICIL }

/**
 * Guarda as preferências do jogador de forma persistente (sobrevive
 * fechar/abrir o app), usando o armazenamento nativo do Android.
 */
class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("audioroulette_settings", Context.MODE_PRIVATE)

    var speechRate: Float
        get() {
            if (prefs.contains("speech_rate")) {
                return prefs.getFloat("speech_rate", 1.0f)
            }
            return velocidadePadraoDoSistema()
        }
        set(value) = prefs.edit().putFloat("speech_rate", value).apply()

    var sfxVolume: Float
        get() = prefs.getFloat("sfx_volume", 1.0f)
        set(value) = prefs.edit().putFloat("sfx_volume", value).apply()

    var musicVolume: Float
        get() = prefs.getFloat("music_volume", 0.5f)
        set(value) = prefs.edit().putFloat("music_volume", value).apply()

    var dificuldade: Dificuldade
        get() = Dificuldade.valueOf(
            prefs.getString("dificuldade", Dificuldade.NORMAL.name) ?: Dificuldade.NORMAL.name
        )
        set(value) = prefs.edit().putString("dificuldade", value.name).apply()

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value.trim().take(16)).apply()

    fun temUsername(): Boolean = username.isNotBlank()

    /** Usa a velocidade de fala já configurada pelo usuário no sistema (Acessibilidade > Texto pra fala). */
    private fun velocidadePadraoDoSistema(): Float {
        return try {
            val bruto = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.TTS_DEFAULT_RATE,
                100
            )
            (bruto / 100f).coerceIn(0.5f, 2.5f)
        } catch (e: Exception) {
            1.0f
        }
    }
}
