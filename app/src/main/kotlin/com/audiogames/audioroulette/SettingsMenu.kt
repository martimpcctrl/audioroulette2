package com.audiogames.audioroulette

import android.os.Handler
import android.os.Looper

/**
 * Tela de configurações. Navegação: swipe cima/baixo troca de campo,
 * swipe esquerda/direita diminui/aumenta o valor, duplo toque confirma
 * (ou volta ao menu, no campo "Voltar").
 */
class SettingsMenu(
    private val audio: AudioManager,
    private val voice: VoiceManager,
    private val settings: SettingsManager,
    private val onStatusUpdate: (String) -> Unit,
    private val onEditUsername: () -> Unit,
    private val estaLogadoComGoogle: () -> Boolean,
    private val nomeGoogleAtual: () -> String?,
    private val onEntrarGoogle: () -> Unit,
    private val onSairGoogle: () -> Unit,
    private val onBack: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    private val speechRates = listOf(0.7f, 0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.75f)
    private val volumeSteps = listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f)
    private val campos = listOfNotNull("usuario", if (LOGIN_GOOGLE_ATIVO) "conta_google" else null, "voz", "efeitos", "musica", "dificuldade", "voltar")

    var index = 0
    var active = false

    fun open() {
        active = true
        index = 0
        voice.clearQueue()
        onStatusUpdate("CONFIGURAÇÕES")
        voice.say("Menu de configurações.")
        anunciarCampo()
    }

    private fun anunciarCampo() {
        audio.play("blip_menu", PAN_CENTER, 0.5f)
        when (campos[index]) {
            "usuario" -> {
                val nome = settings.username
                if (nome.isBlank()) {
                    voice.sayImmediate("Nome de usuário: não definido. Necessário pra jogar online. Toque duas vezes pra definir.")
                } else {
                    voice.sayImmediate("Nome de usuário: $nome. Toque duas vezes pra mudar.")
                }
            }
            "conta_google" -> {
                if (estaLogadoComGoogle()) {
                    voice.sayImmediate("Conta Google: conectado como ${nomeGoogleAtual()}. Toque duas vezes pra sair.")
                } else {
                    voice.sayImmediate("Conta Google: não conectada. Toque duas vezes pra entrar com Google.")
                }
            }
            "voz" -> voice.sayImmediate("Velocidade da voz: ${formatRate(settings.speechRate)}.")
            "efeitos" -> voice.sayImmediate("Volume dos efeitos: ${pct(settings.sfxVolume)} por cento.")
            "musica" -> voice.sayImmediate("Volume da música: ${pct(settings.musicVolume)} por cento.")
            "dificuldade" -> voice.sayImmediate("Dificuldade: ${nomeDificuldade(settings.dificuldade)}.")
            "voltar" -> voice.sayImmediate("Voltar ao menu principal.")
        }
    }

    private fun pct(v: Float): Int = (v * 100).toInt()

    private fun formatRate(rate: Float): String = when {
        rate <= 0.75f -> "bem lenta"
        rate <= 0.9f -> "lenta"
        rate <= 1.05f -> "normal"
        rate <= 1.2f -> "rápida"
        rate <= 1.4f -> "muito rápida"
        else -> "máxima"
    }

    private fun nomeDificuldade(d: Dificuldade): String = when (d) {
        Dificuldade.FACIL -> "Fácil"
        Dificuldade.NORMAL -> "Normal"
        Dificuldade.DIFICIL -> "Difícil"
    }

    fun cycleField(direction: Int) {
        if (!active) return
        val n = campos.size
        index = ((index + direction) % n + n) % n
        anunciarCampo()
    }

    fun adjustValue(direction: Int) {
        if (!active) return
        when (campos[index]) {
            "voz" -> {
                val atual = speechRates.indexOf(settings.speechRate).let { if (it < 0) 2 else it }
                val novo = (atual + direction).coerceIn(0, speechRates.size - 1)
                settings.speechRate = speechRates[novo]
                voice.setRate(settings.speechRate)
                voice.sayImmediate(formatRate(settings.speechRate))
            }
            "efeitos" -> {
                val atual = volumeSteps.indexOf(settings.sfxVolume).let { if (it < 0) 4 else it }
                val novo = (atual + direction).coerceIn(0, volumeSteps.size - 1)
                settings.sfxVolume = volumeSteps[novo]
                audio.sfxVolumeScale = settings.sfxVolume
                voice.sayImmediate("${pct(settings.sfxVolume)} por cento")
                audio.play("blip_confirmar", PAN_CENTER)
            }
            "musica" -> {
                val atual = volumeSteps.indexOf(settings.musicVolume).let { if (it < 0) 2 else it }
                val novo = (atual + direction).coerceIn(0, volumeSteps.size - 1)
                settings.musicVolume = volumeSteps[novo]
                audio.musicVolumeScale = settings.musicVolume
                voice.sayImmediate("${pct(settings.musicVolume)} por cento")
            }
            "dificuldade" -> {
                val ordem = listOf(Dificuldade.FACIL, Dificuldade.NORMAL, Dificuldade.DIFICIL)
                val atual = ordem.indexOf(settings.dificuldade)
                val novo = (atual + direction).coerceIn(0, ordem.size - 1)
                settings.dificuldade = ordem[novo]
                voice.sayImmediate(nomeDificuldade(settings.dificuldade))
            }
            else -> {}
        }
    }

    fun confirm() {
        if (!active) return
        when (campos[index]) {
            "voltar" -> {
                audio.play("blip_confirmar", PAN_CENTER)
                active = false
                handler.postDelayed({ onBack() }, 300)
            }
            "usuario" -> {
                audio.play("blip_confirmar", PAN_CENTER)
                active = false
                handler.postDelayed({ onEditUsername() }, 300)
            }
            "conta_google" -> {
                audio.play("blip_confirmar", PAN_CENTER)
                if (estaLogadoComGoogle()) {
                    onSairGoogle()
                } else {
                    onEntrarGoogle()
                }
            }
            else -> anunciarCampo()
        }
    }
}
