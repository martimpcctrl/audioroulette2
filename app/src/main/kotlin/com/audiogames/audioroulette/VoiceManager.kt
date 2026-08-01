package com.audiogames.audioroulette

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Gerencia toda a narração falada do jogo usando o motor de Text-to-Speech
 * nativo do Android. Mantém uma fila serial: cada fala só começa depois
 * que a anterior termina de verdade (callback real do sistema).
 *
 * Tem um "watchdog": se o Android nunca confirmar que uma fala terminou
 * (isso pode acontecer em alguns aparelhos/situações), depois de alguns
 * segundos o sistema desiste de esperar e libera a fila sozinho, pra
 * nunca ficar mudo pra sempre.
 */
class VoiceManager(context: Context, initialRate: Float = 1.05f, private val onReady: () -> Unit = {}) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = ArrayDeque<Pair<String, (() -> Unit)?>>()
    private var speaking = false
    private var currentCallback: (() -> Unit)? = null
    private var currentUtteranceId: String? = null
    private var rate: Float = initialRate
    private val handler = Handler(Looper.getMainLooper())
    private val watchdogTimeoutMs = 8000L

    fun setRate(newRate: Float) {
        rate = newRate
        tts?.setSpeechRate(rate)
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("pt-BR")
                tts?.setSpeechRate(rate)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        finalizarFala(utteranceId)
                    }

                    override fun onError(utteranceId: String?) {
                        finalizarFala(utteranceId)
                    }
                })
                ready = true
                onReady()
                processQueue()
            }
        }
    }

    private fun finalizarFala(utteranceId: String?) {
        // ignora confirmações atrasadas de uma fala que já foi liberada pelo watchdog
        if (utteranceId != null && utteranceId != currentUtteranceId) return
        val cb = currentCallback
        currentCallback = null
        currentUtteranceId = null
        speaking = false
        cb?.invoke()
        processQueue()
    }

    fun say(text: String, onComplete: (() -> Unit)? = null) {
        queue.addLast(Pair(text, onComplete))
        if (!speaking && ready) processQueue()
    }

    /**
     * Fala IMEDIATAMENTE, interrompendo qualquer fala em andamento e
     * descartando a fila. Usado pra navegação rápida (menus, itens) -
     * onde a pessoa não deve precisar esperar a fala anterior terminar
     * só porque passou rápido pra próxima opção.
     */
    fun sayImmediate(text: String, onComplete: (() -> Unit)? = null) {
        queue.clear()
        tts?.stop()
        speaking = false
        currentCallback = null
        currentUtteranceId = null
        say(text, onComplete)
    }

    fun clearQueue() {
        queue.clear()
        tts?.stop()
        speaking = false
        currentCallback = null
        currentUtteranceId = null
    }

    private fun processQueue() {
        if (!ready || speaking) return
        if (queue.isEmpty()) return
        val (text, callback) = queue.removeFirst()
        speaking = true
        currentCallback = callback
        val id = UUID.randomUUID().toString()
        currentUtteranceId = id
        tts?.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)

        // watchdog: se essa fala especifica nao confirmar termino a tempo,
        // libera a fila sozinho em vez de ficar mudo pra sempre
        handler.postDelayed({
            if (currentUtteranceId == id && speaking) {
                finalizarFala(id)
            }
        }, watchdogTimeoutMs)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
