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
    private val settings: SettingsManager,
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
    var escolhendoRoubo: Boolean = false
    private var opcoesRoubo: List<String> = listOf()
    private var indiceRoubo: Int = 0
    private var estadoRouboBase: SalaOnline? = null
    var active: Boolean = false
    private var resultadoJaRegistrado = false

    private fun oponenteLado(): String = if (meuLado == "jogador1") "jogador2" else "jogador1"

    private fun nomeOponente(): String {
        val nome = if (oponenteLado() == "jogador1") estadoAtual.jogador1Nome else estadoAtual.jogador2Nome
        return nome.ifBlank { "oponente" }
    }

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

    fun criarSala(vidaInicial: Int, onCodigoPronto: (String) -> Unit) {
        meuLado = "jogador1"
        val novoCodigo = (1000..9999).random().toString()
        codigo = novoCodigo
        val ref = FirebaseService.salaRef(novoCodigo)
        salaRef = ref

        FirebaseService.ensureSignedIn {
            val inicial = SalaOnline(estado = "aguardando", jogador1Nome = settings.username, maxHp = vidaInicial)
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
                    iniciarPartida(nova.jogador1Nome, nova.jogador2Nome)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun iniciarPartida(nomeJ1: String, nomeJ2: String) {
        val vidaInicial = estadoAtual.maxHp
        val total = (2..8).random()
        val reais = (1 until total).random()
        val falsas = total - reais
        val shellsGeradas = (List(reais) { true } + List(falsas) { false }).shuffled()

        val novo = SalaOnline(
            estado = "jogando",
            maxHp = vidaInicial,
            shells = shellsGeradas,
            turno = "jogador1",
            jogador1Hp = vidaInicial,
            jogador2Hp = vidaInicial,
            jogador1Items = gerarItensIniciais(),
            jogador2Items = gerarItensIniciais(),
            jogador2Pronto = true,
            jogador1Nome = nomeJ1,
            jogador2Nome = nomeJ2,
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
        voice.say(if (novo.turno == meuLado) "${nomeOponente()} entrou! A partida começou. Sua vez." else "${nomeOponente()} entrou! A partida começou.")
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
                    ref.updateChildren(mapOf("jogador2Pronto" to true, "jogador2Nome" to settings.username))
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
        val ref = salaRef
        if (ref != null) {
            listenerAtivo?.let { ref.removeEventListener(it) }
            if (active && estadoAtual.estado == "jogando" && estadoAtual.vencedor == null) {
                // saiu no meio: o outro vence e fica sabendo (antes a sala ficava parada para sempre)
                val novo = estadoAtual.copy(estado = "finalizado", vencedor = oponenteLado())
                novo.ultimaAcaoId = estadoAtual.ultimaAcaoId + 1
                novo.ultimaAcaoTexto = "ABANDONOU|$meuLado"
                ref.setValue(novo)
                if (!resultadoJaRegistrado) stats.registrarDerrota()
            } else if (estadoAtual.estado == "aguardando" && meuLado == "jogador1") {
                ref.removeValue() // ninguém entrou: apaga a sala
            }
        }
        listenerAtivo = null
        active = false
        salaRef = null
        estadoAtual = SalaOnline()
        aim = null
        selectedIndex = 0
        ultimoIdNarrado = 0
        escolhendoRoubo = false
        estadoRouboBase = null
        resultadoJaRegistrado = false // senão o resultado da próxima partida online não era contado
        audio.stopMusic()
    }

    private fun status() {
        onStatusUpdate("Online | Você: ${minhaVida()} | ${nomeOponente()}: ${vidaOponente()}")
    }

    // -------------------- AÇÕES DO JOGADOR --------------------

    fun podeAgir(): Boolean =
        active && estadoAtual.estado == "jogando" && estadoAtual.turno == meuLado && estadoAtual.vencedor == null

    fun escolherAlvo(alvo: String) {
        if (!podeAgir()) return
        aim = alvo
        voice.sayImmediate(if (alvo == "self") "Mirando em você mesmo." else "Mirando em ${nomeOponente()}.")
    }

    fun cycleItem(direction: Int) {
        if (escolhendoRoubo) {
            cycleRoubo(direction)
            return
        }
        if (!podeAgir()) return
        audio.play("blip_menu", PAN_SELF, 0.5f)
        val n = ITEM_KEYS.size + 1
        selectedIndex = ((selectedIndex + direction) % n + n) % n
        if (selectedIndex == 0) {
            voice.sayImmediate("Arma pronta.")
            return
        }
        val item = ITEM_KEYS[selectedIndex - 1]
        val count = meusItens()[item] ?: 0
        val nome = ITEM_NOMES[item] ?: item
        voice.sayImmediate(if (count <= 0) "$nome. Você não tem nenhum." else "$nome. Você tem $count.")
    }

    fun confirmarAcao() {
        if (escolhendoRoubo) {
            confirmarRoubo()
            return
        }
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
        var texto = textoAcao
        // a espingarda esvaziou (tiro ou cerveja): recarrega na mesma jogada, com itens novos,
        // em vez de gastar o próximo toque de quem joga só para recarregar
        if (novo.estado == "jogando" && novo.vencedor == null && novo.shells.isEmpty()) {
            texto += ";" + encherEspingarda(novo)
        }
        novo.ultimaAcaoId = estadoAtual.ultimaAcaoId + 1
        novo.ultimaAcaoTexto = texto
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

        if (item == "adrenalina") {
            val itensOponente = getItems(novo, oponenteLado())
            val disponiveis = itensOponente.filter { it.value > 0 && it.key != "adrenalina" }
            when {
                disponiveis.isEmpty() -> {
                    publicarEstado(novo, "ITEM|$meuLado|adrenalina|semitens")
                }
                disponiveis.size == 1 -> {
                    val roubado = disponiveis.keys.first()
                    val itensOp = itensOponente.toMutableMap()
                    itensOp[roubado] = maxOf(0, (itensOp[roubado] ?: 1) - 1)
                    setItems(novo, oponenteLado(), itensOp)
                    aplicarEfeitoItem(novo, roubado)
                    publicarEstado(novo, "ITEM|$meuLado|adrenalina|roubou_$roubado")
                }
                else -> {
                    escolhendoRoubo = true
                    opcoesRoubo = disponiveis.keys.toList()
                    indiceRoubo = 0
                    estadoRouboBase = novo
                    publicarEstado(novo, "ITEM|$meuLado|adrenalina|escolhendo")
                    voice.say("Adrenalina! Escolha qual item roubar. Deslize cima ou baixo pra navegar, toque duas vezes pra confirmar.") {
                        anunciarOpcaoRoubo()
                    }
                }
            }
            return
        }

        val detalhe = aplicarEfeitoItem(novo, item)
        publicarEstado(novo, "ITEM|$meuLado|$item|$detalhe")
    }

    /** Aplica o efeito de um item (usado normalmente ou roubado via Adrenalina) e devolve o "detalhe" pra narração. */
    private fun aplicarEfeitoItem(novo: SalaOnline, item: String): String {
        when (item) {
            "cigarro" -> {
                val vidaAtual = getVida(novo, meuLado)
                return if (vidaAtual < novo.maxHp) {
                    setVida(novo, meuLado, vidaAtual + 1)
                    "curou"
                } else {
                    "semefeito"
                }
            }
            "cerveja" -> {
                if (novo.shells.isNotEmpty()) {
                    val ejetada = novo.shells[0]
                    novo.shells = novo.shells.drop(1)
                    return if (ejetada) "real" else "falsa"
                }
                return "vazio"
            }
            "serra" -> {
                setSerrou(novo, meuLado, true)
                return "aplicada"
            }
            "lupa" -> {
                if (novo.shells.isNotEmpty()) {
                    val real = novo.shells[0]
                    voice.say(if (real) "É uma bala real." else "É uma bala falsa.")
                    return "usada"
                }
                return "vazio"
            }
            "algemas" -> {
                val ladoAlvo = oponenteLado()
                if (!getAlgemado(novo, ladoAlvo)) {
                    setAlgemado(novo, ladoAlvo, true)
                    return "aplicada"
                }
                return "semefeito"
            }
            "celular" -> {
                if (novo.shells.size > 1) {
                    val idx = (1 until novo.shells.size).random()
                    val real = novo.shells[idx]
                    voice.say("A bala na posição ${idx + 1} é ${if (real) "real" else "falsa"}.")
                    return "usado"
                }
                return "semdados"
            }
            "inversor" -> {
                if (novo.shells.isNotEmpty()) {
                    val novasShells = novo.shells.toMutableList()
                    novasShells[0] = !novasShells[0]
                    novo.shells = novasShells
                    return "aplicado"
                }
                return "semefeito"
            }
            "remedio" -> {
                val funcionou = (0..1).random() == 1
                val vidaAtual = getVida(novo, meuLado)
                return if (funcionou) {
                    setVida(novo, meuLado, minOf(novo.maxHp, vidaAtual + 2))
                    "curou"
                } else {
                    val vidaNova = maxOf(0, vidaAtual - 1)
                    setVida(novo, meuLado, vidaNova)
                    if (vidaNova <= 0) {
                        novo.vencedor = oponenteLado()
                        novo.estado = "finalizado"
                    }
                    "piorou"
                }
            }
        }
        return ""
    }

    private fun anunciarOpcaoRoubo() {
        if (opcoesRoubo.isEmpty()) return
        val item = opcoesRoubo[indiceRoubo]
        val qtd = estadoRouboBase?.let { getItems(it, oponenteLado())[item] } ?: 0
        audio.play("blip_menu", PAN_SELF, 0.5f)
        voice.sayImmediate("${ITEM_NOMES[item] ?: item}, o oponente tem $qtd.")
    }

    fun cycleRoubo(direction: Int) {
        if (!escolhendoRoubo || opcoesRoubo.isEmpty()) return
        val n = opcoesRoubo.size
        indiceRoubo = ((indiceRoubo + direction) % n + n) % n
        anunciarOpcaoRoubo()
    }

    fun confirmarRoubo() {
        if (!escolhendoRoubo || opcoesRoubo.isEmpty()) return
        val item = opcoesRoubo[indiceRoubo]
        val base = estadoRouboBase
        escolhendoRoubo = false
        opcoesRoubo = listOf()
        estadoRouboBase = null
        if (base == null) return

        val novo = base.copy()
        val itensOponente = getItems(novo, oponenteLado()).toMutableMap()
        itensOponente[item] = maxOf(0, (itensOponente[item] ?: 1) - 1)
        setItems(novo, oponenteLado(), itensOponente)
        aplicarEfeitoItem(novo, item)

        audio.play("blip_confirmar", PAN_SELF)
        voice.say("Roubou e usou: ${ITEM_NOMES[item] ?: item}.")
        publicarEstado(novo, "ITEM|$meuLado|adrenalina|roubou_$item")
    }

    private fun recarregar() {
        val novo = estadoAtual.copy()
        publicarEstado(novo, encherEspingarda(novo))
    }

    // põe cartuchos novos e entrega itens aos dois (2 a 4, respeitando o limite); devolve o texto da narração
    private fun encherEspingarda(novo: SalaOnline): String {
        val total = (2..8).random()
        val reais = (1 until total).random()
        val falsas = total - reais
        novo.shells = (List(reais) { true } + List(falsas) { false }).shuffled()
        setItems(novo, "jogador1", adicionarItensAleatorios(getItems(novo, "jogador1")))
        setItems(novo, "jogador2", adicionarItensAleatorios(getItems(novo, "jogador2")))
        return "RECARREGOU|$reais|$falsas"
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

    // uma jogada pode ter mais de uma parte, separadas por ";" (por exemplo o tiro e a recarga)
    private fun narrarAcao(texto: String) {
        if (texto.isEmpty()) return
        var anunciarVez = true
        for (parte in texto.split(";")) {
            if (!narrarParte(parte)) anunciarVez = false
        }
        if (anunciarVez) anunciarFimOuVez()
    }

    // devolve false quando a própria parte já disse de quem é a vez (ou não é para dizer)
    private fun narrarParte(texto: String): Boolean {
        if (texto == "INICIO") {
            audio.playMusic("musica_fundo", true)
            voice.say("A partida começou!") {
                voice.say(if (estadoAtual.turno == meuLado) "Sua vez." else "Vez de ${nomeOponente()}.")
            }
            return false
        }

        val partes = texto.split("|")
        when (partes.getOrNull(0)) {
            "REVANCHE_PEDIDA" -> {
                val quemPediu = partes.getOrNull(1) ?: return false
                if (quemPediu != meuLado) {
                    voice.say("${nomeOponente()} quer jogar de novo! Toque duas vezes pra aceitar.")
                }
                return false
            }
            "ABANDONOU" -> {
                val quem = partes.getOrNull(1) ?: return true
                if (quem == meuLado) return false
                audio.stopMusic()
                if (!resultadoJaRegistrado) {
                    resultadoJaRegistrado = true
                    stats.registrarVitoria()
                }
                voice.say("${nomeOponente()} saiu da partida. Você venceu! Aperte voltar pra sair.")
                return false
            }
            "ATIROU" -> {
                val alvo = partes.getOrNull(2) ?: return true
                val tipo = partes.getOrNull(3) ?: return true
                val pulou = partes.getOrNull(5) ?: "none"
                audio.play("disparo_martelo", PAN_CENTER)
                if (tipo == "real") {
                    audio.play("tiro_real", panPara(alvo))
                    audio.play("dano_sofrido", panPara(alvo))
                    audio.vibrarTiroReal()
                    voice.say(if (alvo == meuLado) "Bala real! Você levou dano." else "Bala real! ${nomeOponente()} levou dano.")
                } else {
                    audio.play("click_falso", panPara(alvo))
                    audio.vibrarTiroFalso()
                    voice.say("Clique vazio.")
                }
                if (pulou != "none") {
                    voice.say(if (pulou == meuLado) "Você estava algemado e perdeu a vez." else "${nomeOponente()} estava algemado e perdeu a vez.")
                }
            }
            "ITEM" -> {
                val autor = partes.getOrNull(1) ?: return true
                val item = partes.getOrNull(2) ?: return true
                val detalhe = partes.getOrNull(3) ?: ""
                val pan = panPara(autor)
                val nome = ITEM_NOMES[item] ?: item
                audio.play("usar_$item", pan)
                val quem = if (autor == meuLado) "Você usou" else "${nomeOponente()} usou"
                voice.say("$quem $nome.")
                when {
                    item == "cerveja" && detalhe == "real" ->
                        voice.say("Cartucho ejetado: bala real.")
                    item == "cerveja" && detalhe == "falsa" ->
                        voice.say("Cartucho ejetado: bala falsa.")
                    item == "cerveja" && detalhe == "vazio" ->
                        voice.say("A arma já estava vazia.")
                    item == "cigarro" && detalhe == "curou" ->
                        voice.say(if (autor == meuLado) "Vida recuperada." else "${nomeOponente()} recuperou vida.")
                    item == "cigarro" && detalhe == "semefeito" ->
                        voice.say("Sem efeito, vida já está cheia.")
                    autor != meuLado && item == "algemas" && detalhe == "aplicada" ->
                        voice.say("Você está algemado, vai perder a próxima vez.")
                    item == "remedio" && detalhe == "curou" ->
                        voice.say(if (autor == meuLado) "Funcionou! Duas vidas recuperadas." else "O remédio de ${nomeOponente()} funcionou.")
                    item == "remedio" && detalhe == "piorou" ->
                        voice.say(if (autor == meuLado) "Piorou! Perdeu uma vida." else "O remédio de ${nomeOponente()} piorou a vida dele.")
                    item == "adrenalina" && detalhe == "semitens" ->
                        voice.say(if (autor == meuLado) "${nomeOponente()} não tem itens pra roubar." else "${nomeOponente()} tentou roubar um item seu, mas você não tinha nenhum.")
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
        return true
    }

    private fun anunciarFimOuVez() {
        if (estadoAtual.estado == "finalizado" && estadoAtual.vencedor != null) {
            audio.stopMusic()
            val euVenci = estadoAtual.vencedor == meuLado
            if (!resultadoJaRegistrado) {
                resultadoJaRegistrado = true
                if (euVenci) stats.registrarVitoria() else stats.registrarDerrota()
            }
            if (euVenci) achievements.desbloquear("duelista_online", voice)
            voice.say(if (euVenci) "Você venceu o duelo contra ${nomeOponente()}!" else "Você perdeu o duelo pra ${nomeOponente()}.") {
                voice.say("Toque duas vezes pra jogar de novo com ${nomeOponente()}, ou aperte voltar pra sair.")
            }
        } else if (estadoAtual.turno == meuLado) {
            voice.say("Sua vez.")
        } else {
            voice.say("Vez de ${nomeOponente()}.")
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
