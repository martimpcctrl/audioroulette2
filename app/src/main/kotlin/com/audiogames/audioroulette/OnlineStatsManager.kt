package com.audiogames.audioroulette

import android.content.Context

/**
 * Guarda o placar de vitórias e derrotas do modo online, localmente no
 * aparelho (SharedPreferences). Não depende de conexão nem do Firebase -
 * é só um contador pessoal de "como venho me saindo" nos duelos online.
 */
class OnlineStatsManager(context: Context) {
    private val prefs = context.getSharedPreferences("online_stats", Context.MODE_PRIVATE)

    fun vitorias(): Int = prefs.getInt("vitorias", 0)
    fun derrotas(): Int = prefs.getInt("derrotas", 0)

    fun registrarVitoria() {
        prefs.edit().putInt("vitorias", vitorias() + 1).apply()
    }

    fun registrarDerrota() {
        prefs.edit().putInt("derrotas", derrotas() + 1).apply()
    }

    fun resumoFalado(): String {
        val v = vitorias()
        val d = derrotas()
        val total = v + d
        if (total == 0) return "Você ainda não tem duelos online registrados."
        val taxa = (v * 100) / total
        return "Placar online: $v vitórias e $d derrotas, em $total duelos. Taxa de vitória: $taxa por cento."
    }

    fun resetar() {
        prefs.edit().clear().apply()
    }
}
