package com.audiogames.audioroulette

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : Activity() {

    private lateinit var statusLabel: TextView
    private lateinit var codeEditText: EditText
    private lateinit var audioManager: AudioManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var saveManager: SaveManager
    private lateinit var achievementManager: AchievementManager
    private lateinit var achievementsMenu: AchievementsMenu
    private lateinit var menuCore: MenuCore
    private lateinit var gameCore: GameCore
    private lateinit var settingsMenu: SettingsMenu
    private lateinit var onlineCore: OnlineGameCore
    private lateinit var onlineMenu: OnlineMenu
    private lateinit var onlineStats: OnlineStatsManager
    private lateinit var updateManager: UpdateManager
    private lateinit var googleAuth: GoogleAuthManager

    private var mode = "menu" // "menu", "playing", "settings", "online_menu" ou "online_playing"
    private var started = false
    private var jaAnunciouFimOnline = false
    private var textInputPurpose = "code"
    private var usernameVoltarPara = "settings"

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val swipeThreshold = 80f
    private val doubleTapTimeMs = 400L
    private val doubleTapDist = 70f

    private var maxPointerCount = 1
    private var gestureStartTime = 0L
    private val twoFingerTapMaxDurationMs = 400L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        statusLabel = TextView(this).apply {
            text = "Toque para começar"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        root.addView(
            statusLabel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        codeEditText = EditText(this).apply {
            hint = "Código de 4 números"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 28f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    handleCodeSubmit()
                    true
                } else {
                    false
                }
            }
        }
        root.addView(
            codeEditText,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        setContentView(root)

        settingsManager = SettingsManager(this)
        saveManager = SaveManager(this)
        achievementManager = AchievementManager(this)
        onlineStats = OnlineStatsManager(this)
        updateManager = UpdateManager(this)
        googleAuth = GoogleAuthManager(this)
        audioManager = AudioManager(this).apply {
            sfxVolumeScale = settingsManager.sfxVolume
            musicVolumeScale = settingsManager.musicVolume
        }
        voiceManager = VoiceManager(this, initialRate = settingsManager.speechRate)

        menuCore = MenuCore(
            audio = audioManager,
            voice = voiceManager,
            saveManager = saveManager,
            onStatusUpdate = { setStatus(it) },
            onStartGame = { selectedMode -> startGame(selectedMode) },
            onContinueGame = { continueGame() },
            onOpenSettings = { openSettings() },
            onOpenOnline = { openOnlineMenu() },
            onOpenAchievements = { openAchievements() },
            onCheckUpdate = { checkUpdate(manual = true) },
            onShowStats = { showStats() },
            onQuit = { finishAffinity() }
        )

        gameCore = GameCore(
            audio = audioManager,
            voice = voiceManager,
            settings = settingsManager,
            achievements = achievementManager,
            saveManager = saveManager,
            onStatusUpdate = { setStatus(it) },
            onCampaignEnd = { saveManager.limpar(); backToMenu() }
        )

        settingsMenu = SettingsMenu(
            audio = audioManager,
            voice = voiceManager,
            settings = settingsManager,
            onStatusUpdate = { setStatus(it) },
            onEditUsername = { openUsernameInput(voltarPara = "settings") },
            estaLogadoComGoogle = { googleAuth.estaLogadoComGoogle() },
            nomeGoogleAtual = { googleAuth.usuarioGoogleAtual()?.displayName },
            onEntrarGoogle = { entrarComGoogle() },
            onSairGoogle = { sairDaContaGoogle() },
            onBack = { backToMenuFromSettings() }
        )

        onlineCore = OnlineGameCore(
            audio = audioManager,
            voice = voiceManager,
            achievements = achievementManager,
            stats = onlineStats,
            settings = settingsManager,
            onStatusUpdate = { setStatus(it) },
            onEstadoMudou = { onOnlineStateChanged() }
        )

        onlineMenu = OnlineMenu(
            audio = audioManager,
            voice = voiceManager,
            onlineCore = onlineCore,
            stats = onlineStats,
            settings = settingsManager,
            onStatusUpdate = { setStatus(it) },
            onSalaCriada = { mode = "online_playing" },
            onSalaEntrou = { mode = "online_playing" },
            onRequestCodeInput = { openCodeInput() },
            onNeedUsername = { openUsernameInput(voltarPara = "online") },
            onBack = { backToMenuFromOnline() }
        )

        achievementsMenu = AchievementsMenu(
            audio = audioManager,
            voice = voiceManager,
            achievements = achievementManager,
            onStatusUpdate = { setStatus(it) },
            onBack = { backToMenuFromAchievements() }
        )

        root.setOnTouchListener { _, event -> handleTouch(event) }

        googleAuth.usuarioGoogleAtual()?.displayName?.let { nomeGoogle ->
            if (settingsManager.username != nomeGoogle) {
                settingsManager.username = nomeGoogle
            }
        }

        checkUpdate(manual = false)
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Trata o link "audioroulette://entrar/1234" - quando alguém toca num
     * convite (mandado pelo WhatsApp, SMS, etc.), o app abre já entrando
     * direto na sala, sem precisar digitar o código na mão.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "audioroulette" || uri.host != "entrar") return
        val codigo = uri.lastPathSegment?.trim() ?: return
        if (codigo.length != 4 || codigo.any { !it.isDigit() }) return

        started = true
        mode = "online_menu"
        voiceManager.clearQueue()
        voiceManager.say("Convite recebido! Entrando na sala $codigo automaticamente.")
        onlineMenu.submeterCodigo(codigo)
    }

    private var atualizacaoJaAnunciada = false

    private fun showStats() {
        val vOffline = saveManager.vitoriasOffline()
        val dOffline = saveManager.derrotasOffline()
        val totalOffline = vOffline + dOffline
        val recorde = saveManager.melhorFaseInfinito()

        val partesOffline = if (totalOffline == 0) {
            "Você ainda não completou nenhuma partida offline."
        } else {
            "No modo offline: $vOffline vitórias e $dOffline derrotas."
        }
        val partesRecorde = if (recorde > 0) " Seu recorde no Modo Infinito é a rodada $recorde." else ""

        voiceManager.say("$partesOffline$partesRecorde ${onlineStats.resumoFalado()}")
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleAuthManager.REQUEST_CODE_GOOGLE_SIGN_IN) {
            googleAuth.tratarResultadoLogin(
                data,
                onSucesso = { nome ->
                    settingsManager.username = nome
                    voiceManager.say("Login com Google feito com sucesso. Jogando como $nome.")
                    if (mode == "settings") settingsMenu.open()
                },
                onErro = { mensagem ->
                    voiceManager.say(mensagem)
                }
            )
        }
    }

    private fun entrarComGoogle() {
        startActivityForResult(googleAuth.iniciarLogin(), GoogleAuthManager.REQUEST_CODE_GOOGLE_SIGN_IN)
    }

    private fun sairDaContaGoogle() {
        voiceManager.say("Saindo da conta Google...")
        googleAuth.sair {
            voiceManager.say("Você saiu da conta Google.")
            if (mode == "settings") settingsMenu.open()
        }
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) {
            voiceManager.say("Verificando atualizações...")
        }
        updateManager.verificarSilenciosamente { atualizacao ->
            atualizacaoJaAnunciada = true
            voiceManager.say(
                "Nova versão disponível: ${atualizacao.versionName}. Baixando e preparando a instalação. " +
                    "O Android vai pedir sua confirmação antes de instalar."
            ) {
                updateManager.baixarEInstalar(
                    atualizacao,
                    onProgresso = { voiceManager.say(it) },
                    onErro = { voiceManager.say(it) }
                )
            }
        }
        if (manual) {
            statusLabel.postDelayed({
                if (!atualizacaoJaAnunciada) {
                    voiceManager.say("Você já está na versão mais recente. Versão instalada: ${BuildConfig.VERSION_NAME}, código ${BuildConfig.VERSION_CODE}.")
                }
            }, 4000)
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusLabel.text = text }
    }

    private fun startGame(selectedMode: GameMode) {
        mode = "playing"
        gameCore.startCampaign(selectedMode)
    }

    private fun continueGame() {
        mode = "playing"
        saveManager.carregar(gameCore)
        gameCore.resumeSavedGame()
    }

    override fun onPause() {
        super.onPause()
        if (mode == "playing" && gameCore.active && !gameCore.gameOver) {
            saveManager.salvar(gameCore)
        }
    }

    private fun openSettings() {
        mode = "settings"
        settingsMenu.open()
    }

    private fun backToMenuFromSettings() {
        mode = "menu"
        menuCore.open()
    }

    private fun openOnlineMenu() {
        mode = "online_menu"
        onlineMenu.open()
    }

    private fun openAchievements() {
        mode = "achievements"
        achievementsMenu.open()
    }

    private fun backToMenuFromAchievements() {
        mode = "menu"
        menuCore.open()
    }

    private fun openCodeInput() {
        mode = "online_code_input"
        textInputPurpose = "code"
        codeEditText.hint = "Código de 4 números"
        codeEditText.inputType = InputType.TYPE_CLASS_NUMBER
        codeEditText.setText("")
        codeEditText.visibility = View.VISIBLE
        codeEditText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(codeEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    /** [voltarPara] indica pra onde voltar depois de salvar: "settings" ou "online". */
    private fun openUsernameInput(voltarPara: String) {
        usernameVoltarPara = voltarPara
        mode = "username_input"
        textInputPurpose = "username"
        codeEditText.hint = "Seu nome de usuário"
        codeEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        codeEditText.setText(settingsManager.username)
        codeEditText.visibility = View.VISIBLE
        codeEditText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(codeEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun handleCodeSubmit() {
        val texto = codeEditText.text.toString()
        codeEditText.visibility = View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(codeEditText.windowToken, 0)

        if (textInputPurpose == "username") {
            if (texto.isBlank()) {
                voiceManager.say("Nome não pode ficar vazio.")
            } else {
                settingsManager.username = texto
                voiceManager.say("Nome de usuário definido como ${settingsManager.username}.")
            }
            when (usernameVoltarPara) {
                "online" -> {
                    mode = "online_menu"
                    onlineMenu.open()
                }
                else -> {
                    mode = "settings"
                    settingsMenu.open()
                }
            }
        } else {
            mode = "online_menu"
            onlineMenu.submeterCodigo(texto)
        }
    }

    private fun backToMenuFromOnline() {
        onlineCore.sair()
        jaAnunciouFimOnline = false
        mode = "menu"
        menuCore.open()
    }

    private fun onOnlineStateChanged() {
        if (mode != "online_playing") return
        val estado = onlineCore.estadoAtual
        if (estado.estado == "finalizado" && !jaAnunciouFimOnline) {
            jaAnunciouFimOnline = true
            // Dá bastante tempo pra pessoa decidir entre pedir revanche
            // (toque duplo) ou voltar ao menu (deslizar/voltar). Só sai
            // sozinho se ninguém fizer nada por um bom tempo.
            statusLabel.postDelayed({
                if (mode == "online_playing" && onlineCore.estadoAtual.estado == "finalizado") {
                    backToMenuFromOnline()
                }
            }, 25000)
        } else if (estado.estado == "jogando") {
            // Partida (re)começou - se estávamos na tela de "fim de jogo"
            // esperando revanche, isso significa que os dois aceitaram.
            jaAnunciouFimOnline = false
        }
    }

    private fun backToMenu() {
        mode = "menu"
        gameCore.active = false
        statusLabel.postDelayed({ menuCore.open() }, 500)
    }

    private fun onConfirm() {
        when (mode) {
            "menu" -> menuCore.confirm()
            "playing" -> {
                if (gameCore.escolhendoItens) {
                    gameCore.confirmItemChoice()
                } else {
                    gameCore.playerConfirmAction()
                }
            }
            "settings" -> settingsMenu.confirm()
            "online_menu" -> onlineMenu.confirm()
            "online_playing" -> {
                if (onlineCore.estadoAtual.estado == "finalizado") {
                    onlineCore.pedirRevanche()
                } else {
                    onlineCore.confirmarAcao()
                }
            }
            "achievements" -> achievementsMenu.confirm()
        }
    }

    private fun announceOwnHp() {
        if (mode == "playing" && gameCore.active) {
            audioManager.play("blip_menu", PAN_SELF, 0.4f)
            voiceManager.say("Sua vida: ${gameCore.player.hp} de ${gameCore.player.maxHp}.")
        } else if (mode == "online_playing" && onlineCore.active) {
            if (onlineCore.estadoAtual.estado == "aguardando") {
                repetirCodigoSala()
            } else {
                audioManager.play("blip_menu", PAN_SELF, 0.4f)
                voiceManager.say("Sua vida: ${onlineCore.minhaVida()} de ${onlineCore.estadoAtual.maxHp}.")
            }
        }
    }

    private fun repetirCodigoSala() {
        val codigo = onlineCore.codigo
        if (codigo.isEmpty()) return
        val falado = codigo.map { it.toString() }.joinToString(", ")
        voiceManager.say("Aguardando o oponente entrar. O código da sala é: $falado. Toque com 3 dedos pra enviar um convite.")
    }

    private fun compartilharConviteSala() {
        val codigo = onlineCore.codigo
        if (codigo.isEmpty() || mode != "online_playing" || onlineCore.estadoAtual.estado != "aguardando") {
            return
        }
        val linkConvite = "https://martimpcctrl.github.io/audioroulette2/join.html?c=$codigo"
        val mensagem = "Vem jogar AudioRoulette comigo! Toque no link pra entrar direto na sala:\n$linkConvite\n\n(Se preferir, abre o jogo, escolhe \"Entrar em Sala\" e digita o código: $codigo)"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensagem)
        }
        try {
            startActivity(Intent.createChooser(intent, "Convidar pra sala"))
        } catch (e: Exception) {
            voiceManager.say("Não consegui abrir o compartilhamento nesse aparelho.")
        }
    }

    private fun announceOpponentHp() {
        if (mode == "playing" && gameCore.active) {
            audioManager.play("blip_menu", PAN_DEALER, 0.4f)
            voiceManager.say("Vida do Dealer: ${gameCore.dealer.hp} de ${gameCore.dealer.maxHp}.")
        } else if (mode == "online_playing" && onlineCore.active) {
            if (onlineCore.estadoAtual.estado == "aguardando") {
                repetirCodigoSala()
            } else {
                audioManager.play("blip_menu", PAN_DEALER, 0.4f)
                voiceManager.say("Vida do oponente: ${onlineCore.vidaOponente()} de ${onlineCore.estadoAtual.maxHp}.")
            }
        }
    }

    private fun announceAmmo() {
        val shells = when {
            mode == "playing" && gameCore.active -> gameCore.shells
            mode == "online_playing" && onlineCore.active -> onlineCore.estadoAtual.shells
            else -> null
        } ?: return

        val total = shells.size
        val reais = shells.count { it }
        val falsas = total - reais
        audioManager.play("blip_menu", PAN_CENTER, 0.4f)
        if (total == 0) {
            voiceManager.say("Sem cartuchos na câmara no momento.")
        } else {
            voiceManager.say("Munições restantes: $total. $reais reais, $falsas falsas.")
        }
    }

    // Toque com 2 dedos: 1 toque = sua vida, 2 toques = vida do oponente,
    // 3 toques ou mais = quantas munições restam (reais e falsas)
    private var twoFingerTapCount = 0
    private var twoFingerTapSeriesStart = 0L

    private fun handleTwoFingerTap() {
        val now = System.currentTimeMillis()
        if (twoFingerTapCount == 0 || (now - twoFingerTapSeriesStart) > doubleTapTimeMs) {
            twoFingerTapCount = 1
            twoFingerTapSeriesStart = now
        } else {
            twoFingerTapCount += 1
        }
        val contagemDesteToque = twoFingerTapCount
        statusLabel.postDelayed({
            if (twoFingerTapCount == contagemDesteToque) {
                when (contagemDesteToque) {
                    1 -> announceOwnHp()
                    2 -> announceOpponentHp()
                    else -> announceAmmo()
                }
                twoFingerTapCount = 0
            }
        }, doubleTapTimeMs + 30)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (mode == "online_code_input") {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!started) {
                    started = true
                    menuCore.open()
                    return true
                }
                touchStartX = event.x
                touchStartY = event.y
                maxPointerCount = 1
                gestureStartTime = System.currentTimeMillis()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointerCount = maxOf(maxPointerCount, event.pointerCount)
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - gestureStartTime

                if (maxPointerCount >= 2) {
                    if (maxPointerCount == 2 && duration <= twoFingerTapMaxDurationMs) {
                        handleTwoFingerTap()
                    } else if (maxPointerCount >= 3 && duration <= twoFingerTapMaxDurationMs) {
                        compartilharConviteSala()
                    }
                    maxPointerCount = 1
                    return true
                }

                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val now = System.currentTimeMillis()

                if (dist < doubleTapDist) {
                    val dPrev = hypot((event.x - lastTapX).toDouble(), (event.y - lastTapY).toDouble()).toFloat()
                    if (lastTapTime != 0L && (now - lastTapTime) <= doubleTapTimeMs && dPrev < doubleTapDist) {
                        onConfirm()
                        lastTapTime = 0L
                        return true
                    }
                    lastTapTime = now
                    lastTapX = event.x
                    lastTapY = event.y
                    return true
                }

                if (abs(dx) > abs(dy)) {
                    if (abs(dx) >= swipeThreshold) {
                        when (mode) {
                            "playing" -> gameCore.playerSetAim(if (dx > 0) "dealer" else "self")
                            "settings" -> settingsMenu.adjustValue(if (dx > 0) 1 else -1)
                            "online_playing" -> onlineCore.escolherAlvo(if (dx > 0) "oponente" else "self")
                        }
                    }
                } else {
                    if (abs(dy) >= swipeThreshold) {
                        val direction = if (dy > 0) 1 else -1
                        when (mode) {
                            "menu" -> menuCore.cycle(direction)
                            "playing" -> {
                                if (gameCore.escolhendoItens) {
                                    gameCore.cycleItemChoice(direction)
                                } else {
                                    gameCore.playerCycleItem(direction)
                                }
                            }
                            "settings" -> settingsMenu.cycleField(direction)
                            "online_menu" -> onlineMenu.cycle(direction)
                            "achievements" -> achievementsMenu.cycle(direction)
                            "online_playing" -> onlineCore.cycleItem(direction)
                        }
                    }
                }
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (mode == "online_playing" && onlineCore.active) {
            backToMenuFromOnline()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        voiceManager.shutdown()
    }
}
