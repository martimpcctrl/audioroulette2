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
}
