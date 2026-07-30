package com.audiogames.audioroulette

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

const val PAN_SELF = -1f
const val PAN_DEALER = 1f
const val PAN_CENTER = 0f

val ITEM_KEYS = listOf(
    "cigarro", "cerveja", "serra", "lupa", "algemas",
    "celular", "inversor", "remedio", "adrenalina"
)

val ITEM_NOMES = mapOf(
    "cigarro" to "Cigarro",
    "cerveja" to "Cerveja",
    "serra" to "Serra",
    "lupa" to "Lupa",
    "algemas" to "Algemas",
    "celular" to "Celular",
    "inversor" to "Inversor",
    "remedio" to "Remédio Vencido",
    "adrenalina" to "Adrenalina"
)

const val MAX_ITEMS_TOTAL = 4

enum class GameMode { CAMPANHA, RAPIDO, INFINITO }

class Fighter(val name: String, val pan: Float) {
    var maxHp: Int = 3
    var hp: Int = 3
    val items: MutableMap<String, Int> = ITEM_KEYS.associateWith { 0 }.toMutableMap()
    var handcuffed: Boolean = false
    var sawed: Boolean = false
    var knowsShell: Boolean? = null

    fun totalItems(): Int = items.values.sum()
    fun isAlive(): Boolean = hp > 0

    fun resetRoundFlags() {
        sawed = false
        knowsShell = null
    }
}

class GameCore(
    private val audio: AudioManager,
    private val voice: VoiceManager,
    private val settings: SettingsManager,
    private val achievements: AchievementManager,
    private val onStatusUpdate: (String) -> Unit = {},
    private val onCampaignEnd: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())

    val player = Fighter("player", PAN_SELF)
    val dealer = Fighter("dealer", PAN_DEALER)

    var shells: MutableList<Boolean> = mutableListOf()
    var turn: String = "player"
    var aim: String? = null
    var selectedIndex: Int = 0
    var busy: Boolean = false
    var gameOver: Boolean = false
    var active: Boolean = false

    var mode: GameMode = GameMode.CAMPANHA
    var fase: Int = 1
    private val totalFasesCampanha = 8

    private var dealerActionCount = 0
    private val maxDealerActions = 8

    // -------------------- ESCOLHA DE ITENS (mecânica real do jogo) --------------------

    var escolhendoItens: Boolean = false
    var opcoesItens: List<String> = listOf()
    var indiceOpcaoAtual: Int = 0
    private var itensEscolhidosCount = 0
    private var pendingItemChoiceComplete: (() -> Unit)? = null
    private var itemUsadoNestaFase = false

    // -------------------- DIFICULDADE --------------------

    private fun dificuldade(): Triple<Int, Float, Int> {
        val diffMult = when (settings.dificuldade) {
            Dificuldade.FACIL -> 0.7f
            Dificuldade.NORMAL -> 1.0f
            Dificuldade.DIFICIL -> 1.3f
        }
        val maxHp = minOf(3 + (fase - 1) / 2, 8)
        val dealerSkill = minOf(0.40f + fase * 0.07f * diffMult, 0.95f)
        val itemMinBase = maxOf(1, 3 - fase / 3)
        val itemMinRaw = when (settings.dificuldade) {
            Dificuldade.FACIL -> itemMinBase + 1
            Dificuldade.DIFICIL -> maxOf(1, itemMinBase - 1)
            Dificuldade.NORMAL -> itemMinBase
        }
        // itemMin nunca pode chegar a 4: o sorteio é Random.nextInt(itemMin, 4),
        // e esse limite superior precisa ser sempre maior que o inferior.
        val itemMin = minOf(itemMinRaw, 3)
        return Triple(maxHp, dealerSkill, itemMin)
    }

    // -------------------- INÍCIO DE PARTIDA --------------------

    fun startCampaign(selectedMode: GameMode) {
        active = true
        gameOver = false
        mode = selectedMode
        fase = 1

        if (mode == GameMode.CAMPANHA) {
            narrarSequencia(Story.introducao) { startFase() }
        } else {
            startFase()
        }
    }

    /**
     * Retoma uma partida salva. O SaveManager já deve ter preenchido
     * fase, vida, itens, balas e turno antes de chamar isso.
     */
    fun resumeSavedGame() {
        active = true
        gameOver = false
        aim = null
        selectedIndex = 0
        busy = false
        dealerActionCount = 0

        audio.playMusic("musica_fundo", true)
        status("Fase $fase | Vida: ${player.hp}/${player.maxHp}")

        voice.say("Continuando de onde você parou. Fase $fase.") {
            startTurn(turn)
        }
    }

    private fun startFase() {
        val (maxHp, _, _) = dificuldade()
        player.maxHp = maxHp
        player.hp = maxHp
        dealer.maxHp = maxHp
        dealer.hp = maxHp
        player.items.replaceAll { _, _ -> 0 }
        dealer.items.replaceAll { _, _ -> 0 }
        turn = "player"
        aim = null
        selectedIndex = 0
        gameOver = false
        itemUsadoNestaFase = false

        audio.playMusic("musica_fundo", true)

        val nomeIntro = if (mode == GameMode.CAMPANHA) {
            listOfNotNull(Story.nomesFase[minOf(fase, 8)], Story.introFase[minOf(fase, 8)])
        } else if (mode == GameMode.INFINITO) {
            listOf("Rodada $fase. Cuidado, fica mais difícil a cada rodada.")
        } else {
            listOf("Duelo rápido. Boa sorte.")
        }

        narrarSequencia(nomeIntro) { beginRound() }
    }

    private fun beginRound() {
        reloadShells {
            giveItems {
                status("Fase $fase | Vida: ${player.hp}/${player.maxHp}")
                postDelay(400) { startTurn("player") }
            }
        }
    }

    fun reloadShells(onComplete: () -> Unit) {
        val total = Random.nextInt(2, 9)
        val realCount = Random.nextInt(1, total)
        val blankCount = total - realCount
        shells = (List(realCount) { true } + List(blankCount) { false }).shuffled().toMutableList()

        player.resetRoundFlags()
        dealer.resetRoundFlags()

        audio.play("recarregar", PAN_CENTER)
        narrarSequencia(
            listOf(
                "Recarregando a espingarda.",
                "$realCount balas reais.",
                "$blankCount balas falsas."
            )
        ) { onComplete() }
    }

    fun giveItems(onComplete: () -> Unit) {
        val (_, _, itemMin) = dificuldade()

        // Dealer recebe itens aleatórios direto, sem interação
        val nDealer = Random.nextInt(itemMin, 4)
        repeat(nDealer) {
            if (dealer.totalItems() < MAX_ITEMS_TOTAL) {
                val item = ITEM_KEYS[Random.nextInt(ITEM_KEYS.size)]
                dealer.items[item] = (dealer.items[item] ?: 0) + 1
            }
        }

        // Jogador também recebe itens sorteados automaticamente - sem
        // escolha manual, igual ao dealer e ao modo online (é assim que
        // a regra real do jogo funciona: a mesa entrega os itens).
        val nPlayer = Random.nextInt(itemMin, 4)
        val itensRecebidos = mutableListOf<String>()
        repeat(nPlayer) {
            if (player.totalItems() < MAX_ITEMS_TOTAL) {
                val item = ITEM_KEYS[Random.nextInt(ITEM_KEYS.size)]
                player.items[item] = (player.items[item] ?: 0) + 1
                itensRecebidos.add(item)
            }
        }
        if (player.totalItems() >= MAX_ITEMS_TOTAL) achievements.desbloquear("colecionador", voice)

        if (itensRecebidos.isEmpty()) {
            voice.say("Nenhum item novo dessa vez.") { onComplete() }
        } else {
            val nomes = itensRecebidos.joinToString(", ") { ITEM_NOMES[it] ?: it }
            voice.say("Você recebeu: $nomes.") { onComplete() }
        }
    }

    private fun iniciarEscolhaDeItens(onComplete: () -> Unit) {
        escolhendoItens = true
        itensEscolhidosCount = 0
        indiceOpcaoAtual = 0
        pendingItemChoiceComplete = onComplete
        opcoesItens = List(3) { ITEM_KEYS[Random.nextInt(ITEM_KEYS.size)] }
        voice.say("Escolha 2 itens entre as opções. Deslize cima ou baixo pra navegar, toque duas vezes pra escolher.") {
            anunciarOpcaoItemAtual()
        }
    }

    private fun anunciarOpcaoItemAtual() {
        if (opcoesItens.isEmpty()) return
        val item = opcoesItens[indiceOpcaoAtual]
        audio.play("blip_menu", PAN_SELF, 0.5f)
        voice.sayImmediate("Opção ${indiceOpcaoAtual + 1} de ${opcoesItens.size}: ${ITEM_NOMES[item]}")
    }

    fun cycleItemChoice(direction: Int) {
        if (!escolhendoItens || opcoesItens.isEmpty()) return
        val n = opcoesItens.size
        indiceOpcaoAtual = ((indiceOpcaoAtual + direction) % n + n) % n
        anunciarOpcaoItemAtual()
    }

    fun confirmItemChoice() {
        if (!escolhendoItens || opcoesItens.isEmpty()) return
        val item = opcoesItens[indiceOpcaoAtual]
        player.items[item] = (player.items[item] ?: 0) + 1
        itensEscolhidosCount += 1
        audio.play("blip_confirmar", PAN_SELF)
        if (player.totalItems() >= MAX_ITEMS_TOTAL) achievements.desbloquear("colecionador", voice)

        if (itensEscolhidosCount >= 2) {
            escolhendoItens = false
            opcoesItens = listOf()
            voice.say("Você escolheu ${ITEM_NOMES[item]}. Itens prontos.") {
                val callback = pendingItemChoiceComplete
                pendingItemChoiceComplete = null
                callback?.invoke()
            }
        } else {
            opcoesItens = List(3) { ITEM_KEYS[Random.nextInt(ITEM_KEYS.size)] }
            indiceOpcaoAtual = 0
            voice.say("Você escolheu ${ITEM_NOMES[item]}. Escolha mais 1.") {
                anunciarOpcaoItemAtual()
            }
        }
    }

    private fun status(text: String) = onStatusUpdate(text)

    private fun postDelay(ms: Long, action: () -> Unit) {
        handler.postDelayed(action, ms)
    }

    private fun narrarSequencia(linhas: List<String>, onComplete: () -> Unit) {
        if (linhas.isEmpty()) {
            onComplete()
            return
        }
        for (i in 0 until linhas.size - 1) {
            voice.say(linhas[i])
        }
        voice.say(linhas.last(), onComplete)
    }

    // -------------------- TURNOS --------------------

    fun startTurn(who: String) {
        if (gameOver || !active) return
        turn = who
        aim = null
        selectedIndex = 0
        val fighter = if (who == "player") player else dealer

        if (fighter.handcuffed) {
            fighter.handcuffed = false
            val txt = if (who == "dealer") "O Dealer está algemado. Perdeu a vez." else "Você está algemado. Perdeu a vez."
            voice.say(txt) {
                val other = if (who == "player") "dealer" else "player"
                postDelay(300) { startTurn(other) }
            }
            return
        }

        if (who == "player") {
            busy = false
            voice.say("Sua vez.")
            status("Fase $fase | SUA VEZ | Vida: ${player.hp} | Dealer: ${dealer.hp}")
        } else {
            busy = true
            voice.say("Vez do Dealer.")
            status("Fase $fase | Vez do Dealer | Vida: ${player.hp} | Dealer: ${dealer.hp}")
            dealerActionCount = 0
            postDelay(900) { dealerTakeAction() }
        }
    }

    fun checkShellsEmptyAndMaybeReload(onComplete: () -> Unit) {
        if (shells.isEmpty()) {
            postDelay(300) { reloadShells(onComplete) }
        } else {
            onComplete()
        }
    }

    // -------------------- AÇÕES DO JOGADOR --------------------

    fun playerCanAct(): Boolean = active && !gameOver && !busy && !escolhendoItens && turn == "player"

    fun playerSetAim(target: String) {
        if (!playerCanAct()) return
        aim = target
        voice.say(if (target == "self") "Mirando em você mesmo." else "Mirando no Dealer.")
    }

    fun playerCycleItem(direction: Int) {
        if (!playerCanAct()) return
        audio.play("blip_menu", PAN_SELF, 0.5f)
        val n = ITEM_KEYS.size + 1
        selectedIndex = ((selectedIndex + direction) % n + n) % n
        if (selectedIndex == 0) {
            voice.sayImmediate("Arma pronta.")
            return
        }
        val item = ITEM_KEYS[selectedIndex - 1]
        val count = player.items[item] ?: 0
        val nome = ITEM_NOMES[item] ?: item
        voice.sayImmediate(if (count <= 0) "$nome. Você não tem nenhum." else "$nome. Você tem $count.")
    }

    fun playerConfirmAction() {
        if (!playerCanAct()) {
            voice.say("Ação inválida agora.")
            return
        }
        if (selectedIndex == 0) {
            val a = aim
            if (a == null) {
                voice.say("Escolha um alvo antes de atirar.")
                return
            }
            fireWeapon(player, dealer, a)
        } else {
            val item = ITEM_KEYS[selectedIndex - 1]
            useItem(player, dealer, item)
        }
    }

    // -------------------- ITENS --------------------

    fun useItem(user: Fighter, other: Fighter, item: String, isStolen: Boolean = false) {
        if ((user.items[item] ?: 0) <= 0) {
            if (user === player) voice.say("Você não possui este item.")
            return
        }
        if (!isStolen) {
            user.items[item] = (user.items[item] ?: 1) - 1
        }
        if (user === player) {
            itemUsadoNestaFase = true
        }
        val pan = user.pan
        val nome = ITEM_NOMES[item] ?: item

        when (item) {
            "cigarro" -> {
                audio.play("usar_cigarro", pan)
                if (user.hp < user.maxHp) {
                    user.hp += 1
                    voice.say("Cigarro. Vida recuperada.")
                } else {
                    voice.say("Cigarro. Sem efeito, vida já está cheia.")
                }
            }
            "cerveja" -> {
                audio.play("usar_cerveja", pan)
                if (shells.isNotEmpty()) {
                    val ejected = shells.removeAt(0)
                    voice.say("Cerveja. Cartucho ejetado: ${if (ejected) "bala real" else "bala falsa"}.")
                    checkShellsEmptyAndMaybeReload {}
                }
            }
            "serra" -> {
                audio.play("usar_serra", pan)
                user.sawed = true
                voice.say("Serra aplicada. Próximo tiro causa dano dobrado.")
            }
            "lupa" -> {
                audio.play("usar_lupa", pan)
                if (shells.isNotEmpty()) {
                    val real = shells[0]
                    if (user === player) {
                        voice.say(if (real) "É uma bala real." else "É uma bala falsa.")
                    } else {
                        user.knowsShell = real
                    }
                }
            }
            "algemas" -> {
                audio.play("usar_algemas", pan)
                if (!other.handcuffed) {
                    other.handcuffed = true
                    voice.say("Algemas usadas. Oponente vai perder a próxima vez.")
                    if (user === player) {
                        val total = achievements.incrementarContador("algemas_usadas")
                        if (total >= 10) achievements.desbloquear("algemador", voice)
                    }
                } else {
                    voice.say("Sem efeito, oponente já está algemado.")
                }
            }
            "celular" -> {
                audio.play("usar_celular", pan)
                if (shells.size > 1) {
                    val idx = Random.nextInt(1, shells.size)
                    val real = shells[idx]
                    if (user === player) {
                        voice.say(
                            "Celular. A bala na posição ${idx + 1} da fila é " +
                                (if (real) "real." else "falsa.")
                        )
                    }
                } else {
                    voice.say("Celular. Não há cartuchos futuros suficientes pra checar.")
                }
            }
            "inversor" -> {
                audio.play("usar_inversor", pan)
                if (shells.isNotEmpty()) {
                    shells[0] = !shells[0]
                    voice.say("Inversor usado. O próximo cartucho foi invertido.")
                }
            }
            "remedio" -> {
                audio.play("usar_remedio", pan)
                if (Random.nextBoolean()) {
                    user.hp = minOf(user.maxHp, user.hp + 2)
                    voice.say("Remédio vencido. Funcionou! Duas vidas recuperadas.")
                } else {
                    user.hp = maxOf(0, user.hp - 1)
                    voice.say("Remédio vencido. Piorou. Perdeu uma vida.")
                }
                if (user.hp <= 0) {
                    endFase(if (user === player) dealer else player)
                }
            }
            "adrenalina" -> {
                audio.play("usar_adrenalina", pan)
                val disponiveis = other.items.filter { it.value > 0 && it.key != "adrenalina" }
                if (disponiveis.isEmpty()) {
                    voice.say("Adrenalina. Oponente não tem itens pra roubar.")
                } else {
                    val roubado = disponiveis.keys.random()
                    other.items[roubado] = (other.items[roubado] ?: 1) - 1
                    voice.say("Adrenalina! Roubou e usou: ${ITEM_NOMES[roubado]}.") {
                        useItem(user, other, roubado, isStolen = true)
                    }
                }
            }
        }

        status("${user.name} usou $nome")
    }

    // -------------------- DISPARO --------------------

    fun fireWeapon(shooter: Fighter, other: Fighter, aimChoice: String) {
        if (shells.isEmpty()) {
            reloadShells { fireWeapon(shooter, other, aimChoice) }
            return
        }

        val target = if (aimChoice == "self") shooter else other
        val shellReal = shells.removeAt(0)
        val damage = if (shooter.sawed) 2 else 1
        shooter.sawed = false

        audio.play("disparo_martelo", PAN_CENTER)

        postDelay(700) {
            if (shellReal) {
                target.hp = maxOf(0, target.hp - damage)
                audio.play("tiro_real", target.pan)
                audio.play("dano_sofrido", target.pan)
                audio.vibrarTiroReal()
                val falaExtra = if (target === dealer) Story.sorteiaDealerFerido() else Story.sorteiaJogadorFerido()
                voice.say(falaExtra)
                status("BALA REAL! ${target.name} sofreu $damage de dano. HP: ${target.hp}")

                if (target.hp <= 0) {
                    // Se o alvo é o próprio atirador (tiro em si mesmo), quem
                    // morreu foi o atirador - o vencedor é o outro, não ele.
                    val vencedor = if (target === shooter) other else shooter
                    postDelay(400) { endFase(vencedor) }
                } else {
                    afterShot(shooter, aimChoice, true)
                }
            } else {
                audio.play("click_falso", target.pan)
                audio.vibrarTiroFalso()
                voice.say("Clique vazio.")
                status("Clique vazio. ${target.name} está seguro.")
                afterShot(shooter, aimChoice, false)
            }
        }
    }

    private fun afterShot(shooter: Fighter, aimChoice: String, wasReal: Boolean) {
        checkShellsEmptyAndMaybeReload {}

        val extraTurn = (aimChoice == "self") && !wasReal

        if (extraTurn) {
            voice.say("Turno extra!") {
                if (shooter === player) {
                    postDelay(300) { startTurn("player") }
                } else {
                    dealerActionCount += 1
                    if (dealerActionCount >= maxDealerActions) {
                        postDelay(300) { startTurn("player") }
                    } else {
                        postDelay(900) { dealerTakeAction() }
                    }
                }
            }
        } else {
            val nextTurn = if (shooter === player) "dealer" else "player"
            postDelay(500) { startTurn(nextTurn) }
        }
    }

    private fun endFase(shooter: Fighter) {
        busy = true
        audio.stopMusic()

        if (shooter === player) {
            if (mode == GameMode.CAMPANHA) {
                achievements.desbloquear("primeira_vitoria", voice)
            }
            if (player.hp == 1) {
                achievements.desbloquear("um_de_vida", voice)
            }
            if (!itemUsadoNestaFase) {
                achievements.desbloquear("sem_itens", voice)
            }
            if (mode == GameMode.INFINITO && fase >= 10) {
                achievements.desbloquear("maratonista", voice)
            }

            val falaVitoria = if (mode == GameMode.CAMPANHA) Story.sorteiaFaseVencida() else "Você venceu esta rodada!"
            voice.say(falaVitoria) {
                when (mode) {
                    GameMode.CAMPANHA -> {
                        if (fase >= totalFasesCampanha) {
                            gameOver = true
                            active = false
                            achievements.desbloquear("campanha_completa", voice)
                            voice.say(Story.sorteiaCampanhaVencida(), onCampaignEnd)
                        } else {
                            fase += 1
                            startFase()
                        }
                    }
                    GameMode.INFINITO -> {
                        fase += 1
                        startFase()
                    }
                    GameMode.RAPIDO -> {
                        gameOver = true
                        active = false
                        voice.say("Fim de jogo. Você venceu.", onCampaignEnd)
                    }
                }
            }
        } else {
            gameOver = true
            active = false
            val fala = if (mode == GameMode.INFINITO) {
                "Você chegou até a rodada $fase. " + Story.sorteiaDerrota()
            } else {
                Story.sorteiaDerrota()
            }
            voice.say(fala, onCampaignEnd)
        }
    }

    // -------------------- IA DO DEALER --------------------

    fun dealerTakeAction() {
        if (gameOver || !active) return
        dealerActionCount += 1
        if (dealerActionCount > maxDealerActions) {
            postDelay(400) { startTurn("player") }
            return
        }

        if (shells.isEmpty()) {
            reloadShells { dealerTakeAction() }
            return
        }

        val (_, dealerSkill, _) = dificuldade()

        if (Random.nextFloat() < 0.12f) {
            voice.say(Story.sorteiaProvocacao())
        }

        if (dealer.hp < dealer.maxHp && (dealer.items["cigarro"] ?: 0) > 0 && Random.nextFloat() < dealerSkill) {
            useItem(dealer, player, "cigarro")
            postDelay(900) { dealerTakeAction() }
            return
        }

        if (dealer.knowsShell == null && (dealer.items["lupa"] ?: 0) > 0 && Random.nextFloat() < dealerSkill) {
            useItem(dealer, player, "lupa")
            postDelay(900) { dealerTakeAction() }
            return
        }

        if (!player.handcuffed && (dealer.items["algemas"] ?: 0) > 0 && Random.nextFloat() < dealerSkill * 0.5f) {
            useItem(dealer, player, "algemas")
            postDelay(900) { dealerTakeAction() }
            return
        }

        var aimChoice: String
        val knows = dealer.knowsShell
        if (knows != null) {
            if (!knows) {
                aimChoice = "self"
            } else {
                aimChoice = "dealer"
                if ((dealer.items["serra"] ?: 0) > 0 && Random.nextFloat() < dealerSkill) {
                    useItem(dealer, player, "serra")
                }
            }
        } else {
            val realLeft = shells.count { it }
            val totalLeft = shells.size
            val probReal = if (totalLeft > 0) realLeft.toFloat() / totalLeft else 0.5f
            val bias = 0.15f + dealerSkill * 0.1f
            aimChoice = if (Random.nextFloat() < probReal * 0.8f + bias) "dealer" else "self"
        }

        postDelay(600) { fireWeapon(dealer, player, aimChoice) }
    }
}
