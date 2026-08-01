package com.audiogames.audioroulette

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

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
