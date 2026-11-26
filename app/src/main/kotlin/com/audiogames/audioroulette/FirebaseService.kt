package com.audiogames.audioroulette

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Login com Google (e o progresso na nuvem) desligado por enquanto: na Play Store ele exigiria
 * cadastrar no Firebase a impressão digital SHA-1 da assinatura da loja. O código continua no
 * projeto; para voltar, troque para true e cadastre o SHA-1 (Play Console, Integridade do app).
 * O modo online não depende disso: ele usa o login anônimo abaixo.
 */
const val LOGIN_GOOGLE_ATIVO = false

/** Centraliza o acesso ao Firebase: login anônimo e referência da sala. */
object FirebaseService {

    fun ensureSignedIn(onReady: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady()
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady() }
            .addOnFailureListener { onReady() }
    }

    fun salaRef(codigo: String): DatabaseReference {
        return FirebaseDatabase.getInstance().reference.child("salas").child(codigo)
    }
}
