package com.audiogames.audioroulette

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Tela de boas-vindas, mostrada só quando ainda não existe conta
 * (nem nome local, nem login Google). Diferente do resto do app, essa
 * tela usa componentes NATIVOS do Android (Button, EditText, TextView)
 * de propósito - ela é lida e navegada pelo leitor de tela do próprio
 * sistema (TalkBack), não pelo sistema de voz e gestos do jogo.
 */
class WelcomeActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var googleAuth: GoogleAuthManager
    private lateinit var cloudProfile: CloudProfileManager

    private lateinit var campoNome: EditText
    private lateinit var botaoConfirmarNome: Button
    private lateinit var textoStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)
        googleAuth = GoogleAuthManager(this)
        cloudProfile = CloudProfileManager(
            SaveManager(this),
            AchievementManager(this),
            OnlineStatsManager(this)
        )

        val padding = (24 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(padding, padding * 2, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titulo = TextView(this).apply {
            text = "Seja bem-vindo ao AudioRoulette!"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, padding)
        }

        val explicacao = TextView(this).apply {
            text = "Antes de começar, crie uma conta. Você pode usar só um nome local no aparelho, " +
                "ou entrar com sua conta Google pra manter seu progresso salvo na nuvem."
            textSize = 17f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, padding * 2)
        }

        val botaoGoogle = Button(this).apply {
            text = "Entrar com Google"
            setOnClickListener { iniciarLoginGoogle() }
        }

        val separador = TextView(this).apply {
            text = "ou"
            textSize = 15f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
        }

        val botaoContaLocal = Button(this).apply {
            text = "Criar conta local (só um nome)"
            setOnClickListener { alternarCampoNome() }
        }

        campoNome = EditText(this).apply {
            hint = "Digite seu nome de usuário"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            visibility = View.GONE
            contentDescription = "Campo de nome de usuário"
        }

        botaoConfirmarNome = Button(this).apply {
            text = "Confirmar nome"
            visibility = View.GONE
            setOnClickListener { confirmarContaLocal() }
        }

        textoStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.YELLOW)
            setPadding(0, padding, 0, 0)
        }

        layout.addView(titulo)
        layout.addView(explicacao)
        layout.addView(botaoGoogle)
        layout.addView(separador)
        layout.addView(botaoContaLocal)
        layout.addView(campoNome)
        layout.addView(botaoConfirmarNome)
        layout.addView(textoStatus)

        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)

        titulo.requestFocus()
        titulo.announceForAccessibility("Seja bem-vindo ao AudioRoulette! Escolha como criar sua conta.")
    }

    private fun alternarCampoNome() {
        val mostrando = campoNome.visibility == View.VISIBLE
        campoNome.visibility = if (mostrando) View.GONE else View.VISIBLE
        botaoConfirmarNome.visibility = if (mostrando) View.GONE else View.VISIBLE
        if (!mostrando) {
            campoNome.requestFocus()
        }
    }

    private fun confirmarContaLocal() {
        val nome = campoNome.text.toString().trim()
        if (nome.isEmpty()) {
            textoStatus.text = "Digite um nome antes de confirmar."
            textoStatus.announceForAccessibility(textoStatus.text)
            return
        }
        settingsManager.username = nome
        Toast.makeText(this, "Conta local criada como $nome", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun iniciarLoginGoogle() {
        startActivityForResult(googleAuth.iniciarLogin(), GoogleAuthManager.REQUEST_CODE_GOOGLE_SIGN_IN)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GoogleAuthManager.REQUEST_CODE_GOOGLE_SIGN_IN) return

        googleAuth.tratarResultadoLogin(
            data,
            onSucesso = { nome ->
                settingsManager.username = nome
                Toast.makeText(this, "Login feito como $nome", Toast.LENGTH_SHORT).show()
                cloudProfile.sincronizarAposLogin {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Progresso restaurado da nuvem!", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            },
            onErro = { mensagem ->
                textoStatus.text = mensagem
                textoStatus.announceForAccessibility(mensagem)
            }
        )
    }
}
