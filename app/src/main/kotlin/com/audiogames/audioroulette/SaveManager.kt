package com.audiogames.audioroulette

import android.content.Context

/**
 * Salva e retoma o progresso de uma partida (fase, vida, itens, balas,
 * de quem é a vez). Assim, se o jogador fechar o app no meio de uma
 * missão, pode continuar de onde parou depois.
 */
class SaveManager(context: Context) {
    private val prefs = context.getSharedPreferences("audioroulette_save", Context.MODE_PRIVATE)

    fun temJogoSalvo(): Boolean = prefs.getBoolean("tem_save", false)

    fun salvar(core: GameCore) {
        if (!core.active || core.gameOver) return
        prefs.edit()
            .putBoolean("tem_save", true)
            .putString("modo", core.mode.name)
            .putInt("fase", core.fase)
            .putInt("playerHp", core.player.hp)
            .putInt("playerMaxHp", core.player.maxHp)
            .putInt("dealerHp", core.dealer.hp)
            .putInt("dealerMaxHp", core.dealer.maxHp)
            .putString("playerItems", serializeItems(core.player.items))
            .putString("dealerItems", serializeItems(core.dealer.items))
            .putString("shells", core.shells.joinToString(",") { if (it) "1" else "0" })
            .putString("turno", core.turn)
            .apply()
    }

    fun limpar() {
        prefs.edit().putBoolean("tem_save", false).apply()
    }

    fun carregar(core: GameCore) {
        core.mode = try {
            GameMode.valueOf(prefs.getString("modo", GameMode.CAMPANHA.name) ?: GameMode.CAMPANHA.name)
        } catch (e: Exception) {
            GameMode.CAMPANHA
        }
        core.fase = prefs.getInt("fase", 1)
        core.player.hp = prefs.getInt("playerHp", 3)
        core.player.maxHp = prefs.getInt("playerMaxHp", 3)
        core.dealer.hp = prefs.getInt("dealerHp", 3)
        core.dealer.maxHp = prefs.getInt("dealerMaxHp", 3)
        deserializeItems(prefs.getString("playerItems", "") ?: "", core.player.items)
        deserializeItems(prefs.getString("dealerItems", "") ?: "", core.dealer.items)
        val shellsStr = prefs.getString("shells", "") ?: ""
        core.shells = if (shellsStr.isEmpty()) {
            mutableListOf()
        } else {
            shellsStr.split(",").map { it == "1" }.toMutableList()
        }
        core.turn = prefs.getString("turno", "player") ?: "player"
    }

    private fun serializeItems(items: Map<String, Int>): String =
        items.entries.joinToString(";") { "${it.key}:${it.value}" }

    private fun deserializeItems(data: String, target: MutableMap<String, Int>) {
        if (data.isEmpty()) return
        data.split(";").forEach { par ->
            val partes = par.split(":")
            if (partes.size == 2) {
                val valor = partes[1].toIntOrNull() ?: 0
                if (target.containsKey(partes[0])) {
                    target[partes[0]] = valor
                }
            }
        }
    }

    // -------------------- RECORDE DO MODO INFINITO --------------------

    fun melhorFaseInfinito(): Int = prefs.getInt("recorde_infinito", 0)

    /** Atualiza o recorde se a fase alcançada for maior que a atual. Retorna true se bateu recorde novo. */
    fun registrarFaseInfinito(faseAlcancada: Int): Boolean {
        val atual = melhorFaseInfinito()
        if (faseAlcancada > atual) {
            prefs.edit().putInt("recorde_infinito", faseAlcancada).apply()
            return true
        }
        return false
    }

    // -------------------- VITORIAS E DERROTAS (OFFLINE) --------------------

    fun vitoriasOffline(): Int = prefs.getInt("vitorias_offline", 0)
    fun derrotasOffline(): Int = prefs.getInt("derrotas_offline", 0)

    fun registrarVitoriaOffline() {
        prefs.edit().putInt("vitorias_offline", vitoriasOffline() + 1).apply()
    }

    fun registrarDerrotaOffline() {
        prefs.edit().putInt("derrotas_offline", derrotasOffline() + 1).apply()
    }

    /** Restaura valores vindos da nuvem, só se forem maiores que o que já está salvo localmente. */
    fun restaurarVitoriasOffline(valor: Int) {
        if (valor > vitoriasOffline()) prefs.edit().putInt("vitorias_offline", valor).apply()
    }

    fun restaurarDerrotasOffline(valor: Int) {
        if (valor > derrotasOffline()) prefs.edit().putInt("derrotas_offline", valor).apply()
    }

    fun restaurarMelhorFaseInfinito(valor: Int) {
        if (valor > melhorFaseInfinito()) prefs.edit().putInt("recorde_infinito", valor).apply()
    }
}
