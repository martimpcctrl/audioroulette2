package com.audiogames.audioroulette

import android.os.Handler
import android.os.Looper

class MenuCore(
    private val audio: AudioManager,
    private val voice: VoiceManager,
    private val saveManager: SaveManager,
    private val onStatusUpdate: (String) -> Unit,
    private val onStartGame: (GameMode) -> Unit,
    private val onContinueGame: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenOnline: () -> Unit,
    private val onOpenAchievements: () -> Unit,
    private val onShowCredits: () -> Unit,
    private val onShowStats: () -> Unit,
    private val onOpenTutorialWeb: () -> Unit,
    private val onQuit: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    data class Opcao(val id: String, val nome: String)

    private var opcoes: List<Opcao> = listOf()

    var index = 0
    var active = false

    private fun montarOpcoes(): List<Opcao> {
        val lista = mutableListOf<Opcao>()
        if (saveManager.temJogoSalvo()) {
            lista.add(Opcao("continuar", "Continuar de onde parou"))
        }
        lista.add(Opcao("campanha", "Modo Campanha, com história"))
        lista.add(Opcao("rapido", "Modo Rápido, duelo único"))
        val recordeInfinito = saveManager.melhorFaseInfinito()
        val descInfinito = if (recordeInfinito > 0) {
            "Modo Infinito, sobreviva o quanto puder. Seu recorde: rodada $recordeInfinito"
        } else {
            "Modo Infinito, sobreviva o quanto puder"
        }
        lista.add(Opcao("infinito", descInfinito))
        lista.add(Opcao("online", "Modo Online, jogue com um amigo"))
        lista.add(Opcao("conquistas", "Conquistas"))
        lista.add(Opcao("estatisticas", "Estatísticas: vitórias e derrotas"))
        lista.add(Opcao("configuracoes", "Configurações"))
        lista.add(Opcao("instrucoes", "Como jogar"))
        lista.add(Opcao("creditos", "Créditos"))
        lista.add(Opcao("sair", "Sair"))
        return lista
    }

    fun open() {
        active = true
        index = 0
        opcoes = montarOpcoes()
        voice.clearQueue()
        onStatusUpdate("MENU PRINCIPAL")
        voice.say("Audio Roulette. Um duelo mortal de espingarda contra o Dealer.")
        anunciarAtual()
    }

    private fun anunciarAtual() {
        if (opcoes.isEmpty()) return
        audio.play("blip_menu", PAN_CENTER, 0.6f)
        voice.sayImmediate(opcoes[index].nome)
    }

    fun cycle(direction: Int) {
        if (!active || opcoes.isEmpty()) return
        val n = opcoes.size
        index = ((index + direction) % n + n) % n
        anunciarAtual()
    }

    fun confirm() {
        if (!active || opcoes.isEmpty()) return
        val escolha = opcoes[index]
        audio.play("blip_confirmar", PAN_CENTER, 0.6f)

        when (escolha.id) {
            "continuar" -> {
                active = false
                handler.postDelayed({ onContinueGame() }, 300)
            }
            "campanha" -> iniciar(GameMode.CAMPANHA)
            "rapido" -> iniciar(GameMode.RAPIDO)
            "infinito" -> iniciar(GameMode.INFINITO)
            "online" -> {
                active = false
                handler.postDelayed({ onOpenOnline() }, 300)
            }
            "conquistas" -> {
                active = false
                handler.postDelayed({ onOpenAchievements() }, 300)
            }
            "configuracoes" -> {
                active = false
                handler.postDelayed({ onOpenSettings() }, 300)
            }
            "instrucoes" -> playInstructions()
            "creditos" -> onShowCredits()
            "estatisticas" -> onShowStats()
            "sair" -> handler.postDelayed({ onQuit() }, 300)
        }
    }

    private fun iniciar(mode: GameMode) {
        active = false
        handler.postDelayed({ onStartGame(mode) }, 350)
    }

    private fun playInstructions() {
        active = false
        onStatusUpdate("INSTRUÇÕES")
        onOpenTutorialWeb()
        val linhas = listOf(
            "AudioRoulette é um jogo de roleta russa, todo narrado por voz. Você e um adversário se revezam com uma espingarda carregada com cartuchos reais e de festim, embaralhados e escondidos. Em cada turno, você escolhe atirar no adversário ou em você mesmo, e pode usar itens estratégicos antes de decidir. O objetivo é reduzir a vida do outro a zero antes que a sua chegue a zero.",
            "Deslize para a esquerda para mirar em você mesmo.",
            "Deslize para a direita para mirar no Dealer, ou no oponente online.",
            "Deslize para cima ou para baixo para escolher um item, entre nove disponíveis.",
            "Toque duas vezes rápido na tela para atirar, ou para usar o item selecionado.",
            "Toque com dois dedos uma vez pra ouvir sua vida. Duas vezes, a vida do oponente. Três vezes, quantos cartuchos restam nessa rodada, sem revelar quantos são reais.",
            "Toda vez que a espingarda é recarregada, você e o adversário recebem itens sorteados, com limite de 4 itens guardados ao mesmo tempo. As algemas fazem o oponente perder a próxima vez, mas não dá pra algemar de novo quem acabou de se soltar.",
            "No modo campanha, você enfrenta oito fases com história. No infinito, a dificuldade sobe pra sempre. No modo online, jogue com um amigo de verdade, cada um no seu celular. Se sair no meio de uma partida, pode continuar de onde parou depois, pelo menu. Existem conquistas pra desbloquear, veja no menu principal. Voltando ao menu."
        )
        for (i in 0 until linhas.size - 1) {
            voice.say(linhas[i])
        }
        voice.say(linhas.last()) { open() }
    }
}
