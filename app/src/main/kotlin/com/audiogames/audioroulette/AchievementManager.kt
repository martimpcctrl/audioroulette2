package com.audiogames.audioroulette

import android.content.Context

data class Conquista(val id: String, val nome: String, val descricao: String)

/**
 * Sistema de conquistas, salvas permanentemente no aparelho.
 */
class AchievementManager(context: Context) {
    private val prefs = context.getSharedPreferences("audioroulette_conquistas", Context.MODE_PRIVATE)

    val todas = listOf(
        Conquista("primeira_vitoria", "Primeiro Sangue", "Vença sua primeira fase da campanha."),
        Conquista("campanha_completa", "Sobrevivente", "Complete as 8 fases da campanha."),
        Conquista("um_de_vida", "Por um Fio", "Vença um duelo com exatamente 1 de vida."),
        Conquista("sem_itens", "Confiança Cega", "Vença uma fase sem usar nenhum item."),
        Conquista("colecionador", "Colecionador", "Tenha 5 ou mais itens ao mesmo tempo."),
        Conquista("duelista_online", "Duelista", "Vença uma partida no modo online."),
        Conquista("maratonista", "Maratonista", "Alcance a fase 10 no modo infinito."),
        Conquista("algemador", "Mestre das Algemas", "Algeme o oponente 10 vezes, no total.")
    )

    fun desbloqueada(id: String): Boolean = prefs.getBoolean(id, false)

    fun desbloquear(id: String, voice: VoiceManager) {
        if (desbloqueada(id)) return
        prefs.edit().putBoolean(id, true).apply()
        val conquista = todas.find { it.id == id } ?: return
        voice.say("Conquista desbloqueada: ${conquista.nome}! ${conquista.descricao}")
    }

    fun totalDesbloqueadas(): Int = todas.count { desbloqueada(it.id) }

    fun idsDesbloqueadas(): List<String> = todas.map { it.id }.filter { desbloqueada(it) }

    /** Marca como desbloqueada sem falar nada - usado ao restaurar da nuvem. */
    fun restaurarDesbloqueada(id: String) {
        prefs.edit().putBoolean(id, true).apply()
    }

    fun contador(chave: String): Int = prefs.getInt("contador_$chave", 0)

    /** Restaura um contador da nuvem, só se o valor da nuvem for maior que o local. */
    fun restaurarContador(chave: String, valor: Int) {
        if (valor > contador(chave)) {
            prefs.edit().putInt("contador_$chave", valor).apply()
        }
    }

    // contador auxiliar pra conquistas cumulativas (ex: algemar 10 vezes)
    fun incrementarContador(chave: String): Int {
        val atual = prefs.getInt("contador_$chave", 0) + 1
        prefs.edit().putInt("contador_$chave", atual).apply()
        return atual
    }
}
