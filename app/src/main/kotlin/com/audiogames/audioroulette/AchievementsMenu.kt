package com.audiogames.audioroulette

import android.os.Handler
import android.os.Looper

/** Tela de conquistas: navega com cima/baixo, duplo toque em "Voltar" retorna ao menu. */
class AchievementsMenu(
    private val audio: AudioManager,
    private val voice: VoiceManager,
    private val achievements: AchievementManager,
    private val onStatusUpdate: (String) -> Unit,
    private val onBack: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var index = 0
    var active = false

    // lista de conquistas + item extra "voltar" no final
    private fun tamanho(): Int = achievements.todas.size + 1

    fun open() {
        active = true
        index = 0
        voice.clearQueue()
        onStatusUpdate("CONQUISTAS")
        val total = achievements.totalDesbloqueadas()
        voice.say("Conquistas: $total de ${achievements.todas.size} desbloqueadas.")
        anunciarAtual()
    }

    private fun anunciarAtual() {
        audio.play("blip_menu", PAN_CENTER, 0.5f)
        if (index == achievements.todas.size) {
            voice.sayImmediate("Voltar ao menu principal.")
            return
        }
        val c = achievements.todas[index]
        val status = if (achievements.desbloqueada(c.id)) "Desbloqueada" else "Bloqueada"
        voice.sayImmediate("$status: ${c.nome}. ${c.descricao}")
    }

    fun cycle(direction: Int) {
        if (!active) return
        val n = tamanho()
        index = ((index + direction) % n + n) % n
        anunciarAtual()
    }

    fun confirm() {
        if (!active) return
        if (index == achievements.todas.size) {
            audio.play("blip_confirmar", PAN_CENTER)
            active = false
            handler.postDelayed({ onBack() }, 300)
        } else {
            anunciarAtual()
        }
    }
}
