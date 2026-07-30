package com.audiogames.audioroulette

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

/**
 * Lógica do modo online (2 jogadores). Cada sala é um único objeto
 * SalaOnline no Firebase Realtime Database. Quem cria a sala é
 * "jogador1", quem entra é "jogador2". Só quem tem a vez pode escrever
 * uma nova jogada; o outro lado só escuta e narra o que aconteceu.
 *
 * Itens que revelam informação privada (lupa, celular) são narrados
 * SÓ no aparelho de quem usou, na hora - o texto compartilhado no
 * banco de dados não leva a informação secreta.
 */
class OnlineGameCore(
    private val audio: AudioManager,
    private val voice: VoiceManager,
    private val achievements: AchievementManager,
    private val stats: OnlineStatsManager,
    private val onStatusUpdate: (String) -> Unit = {},
    private val onEstadoMudou: () -> Unit = {}
) {
    var meuLado: String = "jogador1"
        private set
    var codigo: String = ""
        private set

    var estadoAtual: SalaOnline = SalaOnline()
        private set

    private var salaRef: DatabaseReference? = null
    private var listenerAtivo: ValueEventListener? = null
    private var ultimoIdNarrado: Long = 0

    var aim: String? = null
    var selectedIndex: Int = 0
    var active: Boolean = false
    private var resultadoJaRegistrado = false

    private fun oponenteLado(): String = if (meuLado == "jogador1") "jogador2" else "jogador1"

    // -------------------- HELPERS DE CAMPO --------------------

    private fun getVida(s: SalaOnline, lado: String): Int = if (lado == "jogador1") s.jogador1Hp else s.jogador2Hp
    private fun setVida(s: SalaOnline, lado: String, v: Int) {
        if (lado == "jogador1") s.jogador1Hp = v else s.jogador2Hp = v
    }
    private fun getAlgemado(s: SalaOnline, lado: String): Boolean =
        if (lado == "jogador1") s.jogador1Algemado else s.jogador2Algemado
    private fun setAlgemado(s: SalaOnline, lado: String, v: Boolean) {
        if (lado == "jogador1") s.jogador1Algemado = v else s.jogador2Algemado = v
    }
    private fun getSerrou(s: SalaOnline, lado: String): Boolean =
        if (lado == "jogador1") s.jogador1Serrou else s.jogador2Serrou
    private fun setSerrou(s: SalaOnline, lado: String, v: Boolean) {
        if (lado == "jogador1") s.jogador1Serrou = v else s.jogador2Serrou = v
    }
    private fun getItems(s: SalaOnline, lado: String): Map<String, Int> =
        if (lado == "jogador1") s.jogador1Items else s.jogador2Items
    private fun setItems(s: SalaOnline, lado: String, v: Map<String, Int>) {
        if (lado == "jogador1") s.jogador1Items = v else s.jogador2Items = v
    }

    fun minhaVida(): Int = getVida(estadoAtual, meuLado)
    fun vidaOponente(): Int = getVida(estadoAtual, oponenteLado())
    fun meusItens(): Map<String, Int> = getItems(estadoAtual, meuLado)

    private fun gerarItensIniciais(): Map<String, Int> {
        val mapa = mutableMapOf<String, Int>()
        val n = (2..MAX_ITEMS_TOTAL).random()
        repeat(n) {
            val item = ITEM_KEYS.random()
            mapa[item] = (mapa[item] ?: 0) + 1
        }
        return mapa
    }

    // -------------------- CRIAR / ENTRAR --------------------

    fun criarSala(onCodigoPronto: (String) -> Unit) {
        meuLado = "jogador1"
        val novoCodigo = (1000..9999).random().toString()
        codigo = novoCodigo
        val ref = FirebaseService.salaRef(novoCodigo)
        salaRef = ref

        FirebaseService.ensureSignedIn {
            val inicial = SalaOnline(estado = "aguardando")
            estadoAtual = inicial
            ref.setValue(inicial)
                .addOnSuccessListener {
                    onCodigoPronto(novoCodigo)
                    escutarEspera()
                }
                .addOnFailureListener { erro ->
                    voice.say("Erro ao criar a sala. ${erro.message ?: "Verifique sua conexão e a configuração do Firebase."}")
                }
        }
    }

    private fun escutarEspera() {
        val ref = salaRef ?: return
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nova = snapshot.getValue(SalaOnline::class.java) ?: return
                if (nova.jogador2Pronto && nova.estado == "aguardando") {
                    ref.removeEventListener(this)
                    iniciarPartida()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun iniciarPartida() {
        val total = (2..8).random()
        val reais = (1 until total).random()
        val falsas = total - reais
        val shellsGeradas = (List(reais) { true } + List(falsas) { false }).shuffled()

        val novo = SalaOnline(
            estado = "jogando",
            maxHp = 3,
            shells = shellsGeradas,
            turno = "jogador1",
            jogador1Hp = 3,
            jogador2Hp = 3,
            jogador1Items = gerarItensIniciais(),
            jogador2Items = gerarItensIniciais(),
            jogador2Pronto = true,
            ultimaAcaoTexto = "INICIO",
            ultimaAcaoId = 1
        )
        estadoAtual = novo
        ultimoIdNarrado = 1
        active = true
        salaRef?.setValue(novo)
        escutarPartida()
        status()
        audio.playMusic("musica_fundo", true)
        voice.say(if (novo.turno == meuLado) "Oponente entrou! A partida começou. Sua vez." else "Oponente entrou! A partida começou.")
    }

    fun entrarSala(codigoDigitado: String, onResultado: (Boolean) -> Unit) {
        meuLado = "jogador2"
        codigo = codigoDigitado
        val ref = FirebaseService.salaRef(codigoDigitado)
        salaRef = ref

        FirebaseService.ensureSignedIn {
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val existente = snapshot.getValue(SalaOnline::class.java)
                    if (existente == null || existente.estado != "aguardando") {
                        onResultado(false)
                        return
                    }
                    active = true
                    ref.child("jogador2Pronto").setValue(true)
                        .addOnSuccessListener {
                            escutarPartida()
                            onResultado(true)
                        }
                        .addOnFailureListener { erro ->
                            active = false
                            voice.say("Erro ao entrar na sala. ${erro.message ?: "Verifique sua conexão e a configuração do Firebase."}")
                            onResultado(false)
                        }
                }
                override fun onCancelled(error: DatabaseError) {
                    onResultado(false)
                }
            })
        }
    }

    private fun escutarPartida() {
        val ref = salaRef ?: return
        listenerAtivo?.let { ref.removeEventListener(it) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nova = snapshot.getValue(SalaOnline::class.java) ?: return
                estadoAtual = nova
                status()
                onEstadoMudou()
                if (nova.ultimaAcaoId != ultimoIdNarrado && nova.ultimaAcaoId > 0) {
                    ultimoIdNarrado = nova.ultimaAcaoId
                    narrarAcao(nova.ultimaAcaoTexto)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        listenerAtivo = listener
        ref.addValueEventListener(listener)
    }

    fun sair() {
        salaRef?.let { ref -> listenerAtivo?.let { ref.removeEventListener(it) } }
        listenerAtivo = null
        active = false
        salaRef = null
        estadoAtual = SalaOnline()
        aim = null
        selectedIndex = 0
        ultimoIdNarrado = 0
        audio.stopMusic()
    }

    private fun status() {
        onStatusUpdate("Online | Você: ${minhaVida()} | Oponente: ${vidaOponente()}")
    }

    // -------------------- AÇÕES DO JOGADOR --------------------

    fun podeAgir(): Boolean =
        active && estadoAtual.estado == "jogando" && estadoAtual.turno == meuLado && estadoAtual.vencedor == null

    fun escolherAlvo(alvo: String) {
        if (!podeAgir()) return
        aim = alvo
        voice.say(if (alvo == "self") "Mirando em você mesmo." else "Mirando no oponente.")
    }

    fun cycleItem(direction: Int) {
        if (!podeAgir()) return
        audio.play("blip_menu", PAN_SELF, 0.5f)
        val n = ITEM_KEYS.size + 1
        selectedIndex = ((selectedIndex + direction) % n + n) % n
        if (selectedIndex == 0) {
            voice.say("Arma pronta.")
            return
        }
        val item = ITEM_KEYS[selectedIndex - 1]
        val count = meusItens()[item] ?: 0
        val nome = ITEM_NOMES[item] ?: item
        voice.say(if (count <= 0) "$nome. Você não tem nenhum." else "$nome. Você tem $count.")
    }

    fun confirmarAcao() {
        if (!podeAgir()) {
            voice.say("Não é sua vez.")
            return
        }
        if (selectedIndex == 0) {
            val a = aim
            if (a == null) {
                voice.say("Escolha um alvo antes de atirar.")
                return
            }
            atirar(a)
            aim = null
            selectedIndex = 0
        } else {
            val item = ITEM_KEYS[selectedIndex - 1]
            usarItem(item)
        }
    }

    private fun publicarEstado(novo: SalaOnline, textoAcao: String) {
        novo.ultimaAcaoId = estadoAtual.ultimaAcaoId + 1
        novo.ultimaAcaoTexto = textoAcao
        estadoAtual = novo
        salaRef?.setValue(novo)
    }

    private fun atirar(alvoEscolha: String) {
        val shellsAtuais = estadoAtual.shells
        if (shellsAtuais.isEmpty()) {
            recarregar()
            return
        }
        val real = shellsAtuais[0]
        val restantes = shellsAtuais.drop(1)
        val ladoAlvo = if (alvoEscolha == "self") meuLado else oponenteLado()
        val dano = if (getSerrou(estadoAtual, meuLado)) 2 else 1

        val novo = estadoAtual.copy(shells = restantes)
        setSerrou(novo, meuLado, false)

        val textoAcao: String
        if (real) {
            val vidaNova = maxOf(0, getVida(novo, ladoAlvo) - dano)
            setVida(novo, ladoAlvo, vidaNova)
            textoAcao = "ATIROU|$meuLado|$ladoAlvo|real|$dano"
            if (vidaNova <= 0) {
                // Se o alvo era eu mesmo (tiro em si), quem morreu fui eu -
                // o vencedor é o oponente, não eu.
                novo.vencedor = if (ladoAlvo == meuLado) oponenteLado() else meuLado
                novo.estado = "finalizado"
                publicarEstado(novo, "$textoAcao|none")
                return
            }
        } else {
            textoAcao = "ATIROU|$meuLado|$ladoAlvo|falsa|0"
        }

        val turnoExtra = (alvoEscolha == "self") && !real
        var proximoTurno = if (turnoExtra) meuLado else oponenteLado()

        // se quem receberia a vez estiver algemado, pula a vez dele
        var pulou = "none"
        if (getAlgemado(novo, proximoTurno)) {
            setAlgemado(novo, proximoTurno, false)
            pulou = proximoTurno
            proximoTurno = if (proximoTurno == "jogador1") "jogador2" else "jogador1"
        }

        novo.turno = proximoTurno
        publicarEstado(novo, "$textoAcao|$pulou")
    }

    private fun usarItem(item: String) {
        val meusItensAtuais = getItems(estadoAtual, meuLado).toMutableMap()
        val qtd = meusItensAtuais[item] ?: 0
        if (qtd <= 0) {
            voice.say("Você não possui este item.")
            return
        }
        meusItensAtuais[item] = qtd - 1

        val novo = estadoAtual.copy()
        setItems(novo, meuLado, meusItensAtuais)

        var textoAcao = "ITEM|$meuLado|$item|"

        when (item) {
            "cigarro" -> {
                val vidaAtual = getVida(novo, meuLado)
                if (vidaAtual < novo.maxHp) {
                    setVida(novo, meuLado, vidaAtual + 1)
                    textoAcao += "curou"
                } else {
                    textoAcao += "semefeito"
                }
            }
            "cerveja" -> {
                if (novo.shells.isNotEmpty()) {
                    val ejetada = novo.shells[0]
                    novo.shells = novo.shells.drop(1)
                    textoAcao += if (ejetada) "real" else "falsa"
                } else {
                    textoAcao += "vazio"
                }
            }
            "serra" -> {
                setSerrou(novo, meuLado, true)
                textoAcao += "aplicada"
            }
            "lupa" -> {
                if (novo.shells.isNotEmpty()) {
                    val real = novo.shells[0]
                    voice.say(if (real) "É uma bala real." else "É uma bala falsa.")
                    textoAcao += "usada"
                } else {
                    textoAcao += "vazio"
                }
            }
            "algemas" -> {
                val ladoAlvo = oponenteLado()
                if (!getAlgemado(novo, ladoAlvo)) {
                    setAlgemado(novo, ladoAlvo, true)
                    textoAcao += "aplicada"
                } else {
                    textoAcao += "semefeito"
                }
            }
            "celular" -> {
                if (novo.shells.size > 1) {
                    val idx = (1 until novo.shells.size).random()
                    val real = novo.shells[idx]
                    voice.say("A bala na posição ${idx + 1} é ${if (real) "real" else "falsa"}.")
                    textoAcao += "usado"
                } else {
                    textoAcao += "semdados"
                }
            }
            "inversor" -> {
                if (novo.shells.isNotEmpty()) {
                    val novasShells = novo.shells.toMutableList()
                    novasShells[0] = !novasShells[0]
                    novo.shells = novasShells
                    textoAcao += "aplicado"
                } else {
                    textoAcao += "semefeito"
                }
            }
            "remedio" -> {
                val funcionou = (0..1).random() == 1
                val vidaAtual = getVida(novo, meuLado)
                if (funcionou) {
                    setVida(novo, meuLado, minOf(novo.maxHp, vidaAtual + 2))
                    textoAcao += "curou"
                } else {
                    val vidaNova = maxOf(0, vidaAtual - 1)
                    setVida(novo, meuLado, vidaNova)
                    textoAcao += "piorou"
                    if (vidaNova <= 0) {
                        novo.vencedor = oponenteLado()
                        novo.estado = "finalizado"
                    }
                }
            }
            "adrenalina" -> {
                val itensOponente = getItems(novo, oponenteLado()).toMutableMap()
                val disponiveis = itensOponente.filter { it.value > 0 && it.key != "adrenalina" }
                if (disponiveis.isEmpty()) {
                    textoAcao += "semitens"
                } else {
                    val roubado = disponiveis.keys.random()
                    itensOponente[roubado] = (itensOponente[roubado] ?: 1) - 1
                    setItems(novo, oponenteLado(), itensOponente)
                    textoAcao += "roubou_$roubado"
                    if (roubado == "cigarro" && getVida(novo, meuLado) < novo.maxHp) {
                        setVida(novo, meuLado, getVida(novo, meuLado) + 1)
                    }
                }
            }
        }

        publicarEstado(novo, textoAcao)
    }

    private fun recarregar() {
        val total = (2..8).random()
        val reais = (1 until total).random()
        val falsas = total - reais
        val novasShells = (List(reais) { true } + List(falsas) { false }).shuffled()
        val novo = estadoAtual.copy(shells = novasShells)

        // Toda recarga também entrega novos itens pra cada jogador, igual
        // ao modo offline (2 a 4 itens, respeitando o limite máximo).
        setItems(novo, "jogador1", adicionarItensAleatorios(getItems(estadoAtual, "jogador1")))
        setItems(novo, "jogador2", adicionarItensAleatorios(getItems(estadoAtual, "jogador2")))

        publicarEstado(novo, "RECARREGOU|$reais|$falsas")
    }

    private fun adicionarItensAleatorios(atuais: Map<String, Int>): Map<String, Int> {
        val mapa = atuais.toMutableMap()
        var total = mapa.values.sum()
        val n = (2..4).random()
        repeat(n) {
            if (total < MAX_ITEMS_TOTAL) {
                val item = ITEM_KEYS.random()
                mapa[item] = (mapa[item] ?: 0) + 1
                total++
            }
        }
        return mapa
    }

    // -------------------- NARRAÇÃO --------------------

    private fun panPara(lado: String): Float = if (lado == meuLado) PAN_SELF else PAN_DEALER

    private fun narrarAcao(texto: String) {
        if (texto.isEmpty()) return

        if (texto == "INICIO") {
            audio.playMusic("musica_fundo", true)
            voice.say("A partida começou!") {
                voice.say(if (estadoAtual.turno == meuLado) "Sua vez." else "Vez do oponente.")
            }
            return
        }

        val partes = texto.split("|")
        when (partes.getOrNull(0)) {
            "REVANCHE_PEDIDA" -> {
                val quemPediu = partes.getOrNull(1) ?: return
                if (quemPediu != meuLado) {
                    voice.say("O oponente quer jogar de novo! Toque duas vezes pra aceitar.")
                }
                return
            }
            "ATIROU" -> {
                val alvo = partes.getOrNull(2) ?: return
                val tipo = partes.getOrNull(3) ?: return
                val pulou = partes.getOrNull(5) ?: "none"
                audio.play("disparo_martelo", PAN_CENTER)
                if (tipo == "real") {
                    audio.play("tiro_real", panPara(alvo))
                    audio.play("dano_sofrido", panPara(alvo))
                    audio.vibrarTiroReal()
                    voice.say(if (alvo == meuLado) "Bala real! Você levou dano." else "Bala real! O oponente levou dano.")
                } else {
                    audio.play("click_falso", panPara(alvo))
                    audio.vibrarTiroFalso()
                    voice.say("Clique vazio.")
                }
                if (pulou != "none") {
                    voice.say(if (pulou == meuLado) "Você estava algemado e perdeu a vez." else "O oponente estava algemado e perdeu a vez.")
                }
            }
            "ITEM" -> {
                val autor = partes.getOrNull(1) ?: return
                val item = partes.getOrNull(2) ?: return
                val detalhe = partes.getOrNull(3) ?: ""
                val pan = panPara(autor)
                val nome = ITEM_NOMES[item] ?: item
                audio.play("usar_$item", pan)
                val quem = if (autor == meuLado) "Você usou" else "O oponente usou"
                voice.say("$quem $nome.")
                when {
                    item == "cerveja" && detalhe == "real" ->
                        voice.say("Cartucho ejetado: bala real.")
                    item == "cerveja" && detalhe == "falsa" ->
                        voice.say("Cartucho ejetado: bala falsa.")
                    item == "cerveja" && detalhe == "vazio" ->
                        voice.say("A arma já estava vazia.")
                    item == "cigarro" && detalhe == "curou" ->
                        voice.say(if (autor == meuLado) "Vida recuperada." else "Oponente recuperou vida.")
                    item == "cigarro" && detalhe == "semefeito" ->
                        voice.say("Sem efeito, vida já está cheia.")
                    autor != meuLado && item == "algemas" && detalhe == "aplicada" ->
                        voice.say("Você está algemado, vai perder a próxima vez.")
                    item == "remedio" && detalhe == "curou" ->
                        voice.say(if (autor == meuLado) "Funcionou! Duas vidas recuperadas." else "O remédio do oponente funcionou.")
                    item == "remedio" && detalhe == "piorou" ->
                        voice.say(if (autor == meuLado) "Piorou! Perdeu uma vida." else "O remédio do oponente piorou a vida dele.")
                    item == "adrenalina" && detalhe == "semitens" ->
                        voice.say(if (autor == meuLado) "Oponente não tem itens pra roubar." else "Ele tentou roubar um item seu, mas você não tinha nenhum.")
                    item == "adrenalina" && detalhe.startsWith("roubou_") -> {
                        val roubadoKey = detalhe.removePrefix("roubou_")
                        val roubadoNome = ITEM_NOMES[roubadoKey] ?: roubadoKey
                        voice.say(if (autor == meuLado) "Roubou e usou: $roubadoNome." else "Ele roubou e usou seu $roubadoNome!")
                    }
                }
            }
            "RECARREGOU" -> {
                val reais = partes.getOrNull(1) ?: "?"
                val falsas = partes.getOrNull(2) ?: "?"
                audio.play("recarregar", PAN_CENTER)
                voice.say("Recarregando. $reais balas reais, $falsas falsas. Novos itens chegaram.")
            }
        }

        if (estadoAtual.estado == "finalizado" && estadoAtual.vencedor != null) {
            audio.stopMusic()
            val euVenci = estadoAtual.vencedor == meuLado
            if (!resultadoJaRegistrado) {
                resultadoJaRegistrado = true
                if (euVenci) stats.registrarVitoria() else stats.registrarDerrota()
            }
            if (euVenci) achievements.desbloquear("duelista_online", voice)
            voice.say(if (euVenci) "Você venceu o duelo online!" else "Você perdeu o duelo online.") {
                voice.say("Toque duas vezes pra jogar de novo com o mesmo oponente, ou aperte voltar pra sair.")
            }
        } else if (estadoAtual.turno == meuLado) {
            voice.say("Sua vez.")
        } else {
            voice.say("Vez do oponente.")
        }
    }

    /**
     * Pede revanche na mesma sala, contra o mesmo oponente, sem precisar
     * sair e digitar código de novo. Quando os dois lados pedirem, a
     * partida reinicia automaticamente com vida e itens do zero.
     */
    fun pedirRevanche() {
        if (estadoAtual.estado != "finalizado") return
        val jaPediEu = if (meuLado == "jogador1") estadoAtual.jogador1QuerRevanche else estadoAtual.jogador2QuerRevanche
        if (jaPediEu) return

        val novo = estadoAtual.copy()
        if (meuLado == "jogador1") novo.jogador1QuerRevanche = true else novo.jogador2QuerRevanche = true

        if (novo.jogador1QuerRevanche && novo.jogador2QuerRevanche) {
            val total = (2..8).random()
            val reais = (1 until total).random()
            val falsas = total - reais
            val shellsGeradas = (List(reais) { true } + List(falsas) { false }).shuffled()
            val maxHp = novo.maxHp

            val reiniciado = novo.copy(
                estado = "jogando",
                shells = shellsGeradas,
                turno = if (Random.nextBoolean()) "jogador1" else "jogador2",
                vencedor = null,
                jogador1Hp = maxHp,
                jogador2Hp = maxHp,
                jogador1Items = gerarItensIniciais(),
                jogador2Items = gerarItensIniciais(),
                jogador1Algemado = false,
                jogador2Algemado = false,
                jogador1Serrou = false,
                jogador2Serrou = false,
                jogador1QuerRevanche = false,
                jogador2QuerRevanche = false
            )
            resultadoJaRegistrado = false
            publicarEstado(reiniciado, "INICIO")
            audio.playMusic("musica_fundo", true)
        } else {
            publicarEstado(novo, "REVANCHE_PEDIDA|$meuLado")
        }
    }
}
