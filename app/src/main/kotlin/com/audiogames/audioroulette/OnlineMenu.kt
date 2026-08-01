package com.audiogames.audioroulette

import android.os.Handler
import android.os.Looper

/**
 * Submenu do modo online: Criar Sala, Entrar em Sala e Voltar.
 * A digitação do código usa o teclado normal do Android (mais rápido
 * e mais familiar com leitor de tela do que gestos), controlada pela
 * MainActivity através de um campo de texto.
 */
class OnlineMenu(
    private val audio: AudioManager,
    private val voice: VoiceManager,
    private val onlineCore: OnlineGameCore,
    private val stats: OnlineStatsManager,
    private val settings: SettingsManager,
    private val onStatusUpdate: (String) -> Unit,
    private val onSalaCriada: () -> Unit,
    private val onSalaEntrou: () -> Unit,
    private val onRequestCodeInput: () -> Unit,
    private val onNeedUsername: () -> Unit,
    private val onBack: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    private val opcoes = listOf("criar", "entrar", "voltar")
    var index = 0
    var active = false

    private var escolhendoVida = false
    private val opcoesVida = listOf(3, 5, 8, 10)
    private var indiceVida = 0

    fun open() {
        active = true
        index = 0
        voice.clearQueue()
        onStatusUpdate("MODO ONLINE")
        val saudacaoNome = if (settings.temUsername()) "Jogando como ${settings.username}. " else ""
        voice.say("Modo online. $saudacaoNome${stats.resumoFalado()}")
        anunciarOpcao()
    }

    private fun anunciarOpcao() {
        audio.play("blip_menu", PAN_CENTER, 0.5f)
        when (opcoes[index]) {
            "criar" -> voice.sayImmediate("Criar sala nova.")
            "entrar" -> voice.sayImmediate("Entrar numa sala com código.")
            "voltar" -> voice.sayImmediate("Voltar ao menu principal.")
        }
    }

    private fun anunciarVida() {
        audio.play("blip_menu", PAN_CENTER, 0.5f)
        voice.sayImmediate("${opcoesVida[indiceVida]} de vida.")
    }

    fun cycle(direction: Int) {
        if (!active) return
        if (escolhendoVida) {
            val n = opcoesVida.size
            indiceVida = ((indiceVida + direction) % n + n) % n
            anunciarVida()
            return
        }
        val n = opcoes.size
        index = ((index + direction) % n + n) % n
        anunciarOpcao()
    }

    fun confirm() {
        if (!active) return
        audio.play("blip_confirmar", PAN_CENTER)

        if (escolhendoVida) {
            val vidaEscolhida = opcoesVida[indiceVida]
            escolhendoVida = false
            active = false
            voice.say("$vidaEscolhida de vida. Criando sala...")
            onlineCore.criarSala(vidaEscolhida) { codigoGerado ->
                val falado = codigoGerado.map { it.toString() }.joinToString(", ")
                voice.say("Sala criada com sucesso! Seu código é: $falado. Toque com 3 dedos a qualquer momento pra enviar um convite direto pra um amigo. Toque com dois dedos pra ouvir o código de novo.")
            }
            handler.postDelayed({ onSalaCriada() }, 300)
            return
        }

        if (opcoes[index] in listOf("criar", "entrar") && !settings.temUsername()) {
            voice.say("Você precisa definir um nome de usuário antes de jogar online.")
            active = false
            handler.postDelayed({ onNeedUsername() }, 900)
            return
        }

        when (opcoes[index]) {
            "criar" -> {
                escolhendoVida = true
                indiceVida = 0
                voice.say("Escolha quanto de vida a partida vai ter. Deslize cima ou baixo pra mudar, toque duas vezes pra confirmar.") {
                    anunciarVida()
                }
            }
            "entrar" -> {
                active = false
                voice.say("Abrindo o teclado. Digite o código de 4 números e toque em concluir.")
                handler.postDelayed({ onRequestCodeInput() }, 900)
            }
            "voltar" -> {
                active = false
                handler.postDelayed({ onBack() }, 300)
            }
        }
    }

    /** Chamado pela MainActivity depois que o usuário digita e envia o código pelo teclado. */
    fun submeterCodigo(codigoDigitado: String) {
        val codigo = codigoDigitado.trim()
        if (codigo.length != 4 || codigo.any { !it.isDigit() }) {
            voice.say("Código inválido. Precisa ter 4 números.") {
                onRequestCodeInput()
            }
            return
        }
        voice.say("Tentando entrar na sala $codigo.")
        onlineCore.entrarSala(codigo) { sucesso ->
            if (sucesso) {
                handler.postDelayed({ onSalaEntrou() }, 200)
            } else {
                voice.say("Sala não encontrada, ou já está cheia. Tente outro código.") {
                    onRequestCodeInput()
                }
            }
        }
    }
}
