package com.audiogames.audioroulette

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Sincroniza o progresso (conquistas, vitórias/derrotas, recorde do
 * Infinito) com a nuvem, ligado à conta Google da pessoa - assim, se
 * ela desinstalar e reinstalar o app (ou trocar de celular), o
 * progresso não se perde, desde que ela entre com a mesma conta de novo.
 *
 * A sincronização é sempre por "o maior valor vale": nunca sobrescreve
 * um progresso melhor (local ou da nuvem) com um pior.
 */
class CloudProfileManager(
    private val saveManager: SaveManager,
    private val achievements: AchievementManager,
    private val onlineStats: OnlineStatsManager
) {
    /**
     * Chame isso logo depois de um login com Google bem-sucedido (ou ao
     * abrir o app já logado). Lê o que já existe na nuvem, mescla com o
     * que está no aparelho, salva o resultado nos dois lugares, e avisa
     * se algo foi restaurado. [onErro] recebe o motivo, se algo falhar -
     * útil pra saber exatamente o que deu errado (permissão, rede, etc.).
     */
    fun sincronizarAposLogin(onRestaurado: () -> Unit, onErro: (String) -> Unit = {}) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onErro("Não há usuário Google logado no momento.")
            return
        }
        val uid = user.uid
        val infoAuth = "anônimo: ${user.isAnonymous}, uid: ${uid.take(6)}..."
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(uid).child("perfil")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val progressoRestaurado = mesclarComNuvem(snapshot)
                salvarNaNuvem(ref, onErro = onErro)
                if (progressoRestaurado) {
                    onRestaurado()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onErro("Falha ao LER da nuvem: ${error.message} (código ${error.code}). $infoAuth")
            }
        })
    }

    /** Chame sempre que uma conquista, vitória ou recorde local mudar, pra manter a nuvem atualizada. */
    fun sincronizarSilenciosamente(onErro: (String) -> Unit = {}) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) return
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(user.uid).child("perfil")
        salvarNaNuvem(ref, onErro = { erro -> onErro("$erro (anônimo: ${user.isAnonymous}, uid: ${user.uid.take(6)}...)") })
    }

    /** Retorna true se algum dado da nuvem era melhor que o local (ou seja, algo foi restaurado). */
    private fun mesclarComNuvem(snapshot: DataSnapshot): Boolean {
        if (!snapshot.exists()) return false

        var restaurouAlgo = false

        val vitoriasOfflineNuvem = snapshot.child("vitoriasOffline").getValue(Int::class.java) ?: 0
        if (vitoriasOfflineNuvem > saveManager.vitoriasOffline()) restaurouAlgo = true
        saveManager.restaurarVitoriasOffline(vitoriasOfflineNuvem)

        val derrotasOfflineNuvem = snapshot.child("derrotasOffline").getValue(Int::class.java) ?: 0
        saveManager.restaurarDerrotasOffline(derrotasOfflineNuvem)

        val recordeNuvem = snapshot.child("recordeInfinito").getValue(Int::class.java) ?: 0
        if (recordeNuvem > saveManager.melhorFaseInfinito()) restaurouAlgo = true
        saveManager.restaurarMelhorFaseInfinito(recordeNuvem)

        val vitoriasOnlineNuvem = snapshot.child("vitoriasOnline").getValue(Int::class.java) ?: 0
        if (vitoriasOnlineNuvem > onlineStats.vitorias()) restaurouAlgo = true
        onlineStats.restaurarVitorias(vitoriasOnlineNuvem)

        val derrotasOnlineNuvem = snapshot.child("derrotasOnline").getValue(Int::class.java) ?: 0
        onlineStats.restaurarDerrotas(derrotasOnlineNuvem)

        val conquistasNuvem = snapshot.child("conquistas").children.mapNotNull { it.getValue(String::class.java) }
        val idsLocaisAntes = achievements.idsDesbloqueadas().toSet()
        conquistasNuvem.forEach { id ->
            if (id !in idsLocaisAntes) restaurouAlgo = true
            achievements.restaurarDesbloqueada(id)
        }

        val algemadorNuvem = snapshot.child("contadorAlgemador").getValue(Int::class.java) ?: 0
        achievements.restaurarContador("algemador", algemadorNuvem)

        return restaurouAlgo
    }

    private fun salvarNaNuvem(ref: DatabaseReference, onErro: (String) -> Unit) {
        val dados = mapOf(
            "vitoriasOffline" to saveManager.vitoriasOffline(),
            "derrotasOffline" to saveManager.derrotasOffline(),
            "recordeInfinito" to saveManager.melhorFaseInfinito(),
            "vitoriasOnline" to onlineStats.vitorias(),
            "derrotasOnline" to onlineStats.derrotas(),
            "conquistas" to achievements.idsDesbloqueadas(),
            "contadorAlgemador" to achievements.contador("algemador")
        )
        ref.setValue(dados)
            .addOnFailureListener { erro ->
                onErro("Falha ao ESCREVER na nuvem: ${erro.message}")
            }
    }
}
