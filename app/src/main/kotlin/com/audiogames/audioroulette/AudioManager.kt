package com.audiogames.audioroulette

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Gerencia os efeitos sonoros (não-falados) do jogo usando SoundPool, que
 * suporta volume independente por canal esquerdo/direito - isso dá o
 * direcionamento estéreo de verdade, calculado em tempo real (sem precisar
 * de arquivos de áudio pré-gravados para cada direção).
 *
 * IMPORTANTE: SoundPool carrega os arquivos em segundo plano de forma
 * assíncrona. Se um som for tocado antes de terminar de carregar, ele
 * simplesmente não toca, sem erro nenhum (é exatamente isso que causava
 * sons de item e de recarregar "sumindo"). Por isso guardamos quais IDs
 * já terminaram de carregar, e tentamos de novo em pouco tempo se ainda
 * não estiver pronto.
 */
class AudioManager(private val context: Context) {

    var sfxVolumeScale: Float = 1.0f
    var musicVolumeScale: Float = 0.5f

    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<String, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private var musicPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val sfxNames = listOf(
        "disparo_martelo", "tiro_real", "click_falso", "dano_sofrido", "recarregar",
        "usar_cigarro", "usar_cerveja", "usar_serra", "usar_lupa", "usar_algemas",
        "usar_celular", "usar_inversor", "usar_remedio", "usar_adrenalina",
        "blip_menu", "blip_confirmar"
    )

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
            }
        }

        for (name in sfxNames) {
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) {
                soundIds[name] = soundPool.load(context, resId, 1)
            }
        }
    }

    /** pan: -1.0 (esquerda / jogador) até 1.0 (direita / dealer), 0 = centro. */
    fun play(key: String, pan: Float = 0f, volume: Float = 1f, tentativas: Int = 4) {
        val id = soundIds[key] ?: return

        if (id !in loadedIds) {
            // ainda carregando - tenta de novo daqui a pouco, em vez de desistir
            if (tentativas > 0) {
                handler.postDelayed({ play(key, pan, volume, tentativas - 1) }, 80)
            }
            return
        }

        val p = pan.coerceIn(-1f, 1f)
        val v = volume * sfxVolumeScale
        val left = v * (1f - maxOf(0f, p))
        val right = v * (1f - maxOf(0f, -p))
        soundPool.play(id, left, right, 1, 0, 1f)
    }

    fun playMusic(resName: String, loop: Boolean = true) {
        stopMusic()
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) return
        try {
            musicPlayer = MediaPlayer.create(context, resId)?.apply {
                isLooping = loop
                setVolume(musicVolumeScale, musicVolumeScale)
                start()
            }
        } catch (e: Exception) {
            musicPlayer = null
        }
    }

    fun stopMusic() {
        musicPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                // ignora - pode já estar liberado
            }
        }
        musicPlayer = null
    }

    /**
     * Vibração forte, usada quando o tiro é uma bala REAL. Padrão bem
     * marcado, tipo "explosão" - fácil de sentir e diferenciar do tique
     * leve da bala falsa.
     */
    fun vibrarTiroReal() {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val padrao = longArrayOf(0, 60, 40, 220)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                v.vibrate(VibrationEffect.createWaveform(padrao, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 60, 40, 220), -1)
            }
        } catch (e: Exception) {
            // ignora - aparelho sem suporte a vibração
        }
    }

    /**
     * Vibração leve, usada quando o tiro é uma bala FALSA (clique vazio).
     * Só um "tique" rápido e fraco, bem diferente da vibração forte da
     * bala real.
     */
    fun vibrarTiroFalso() {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(25, 60))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(25)
            }
        } catch (e: Exception) {
            // ignora - aparelho sem suporte a vibração
        }
    }

    fun release() {
        stopMusic()
        soundPool.release()
    }
}
