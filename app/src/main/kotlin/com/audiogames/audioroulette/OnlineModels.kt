package com.audiogames.audioroulette

/**
 * Representa o estado completo de uma sala online. O Firebase mapeia isso
 * automaticamente (por isso os valores padrão em todo campo - exigência
 * da biblioteca pra conseguir reconstruir o objeto).
 */
data class SalaOnline(
    var estado: String = "aguardando",      // aguardando | jogando | finalizado
    var maxHp: Int = 3,
    var shells: List<Boolean> = listOf(),
    var turno: String = "jogador1",          // jogador1 | jogador2
    var vencedor: String? = null,
    var jogador1Hp: Int = 3,
    var jogador2Hp: Int = 3,
    var jogador1Items: Map<String, Int> = mapOf(),
    var jogador2Items: Map<String, Int> = mapOf(),
    var jogador1Algemado: Boolean = false,
    var jogador2Algemado: Boolean = false,
    var jogador1Serrou: Boolean = false,
    var jogador2Serrou: Boolean = false,
    var jogador2Pronto: Boolean = false,
    var jogador1QuerRevanche: Boolean = false,
    var jogador2QuerRevanche: Boolean = false,
    var jogador1Nome: String = "",
    var jogador2Nome: String = "",
    var ultimaAcaoTexto: String = "",
    var ultimaAcaoId: Long = 0
)
