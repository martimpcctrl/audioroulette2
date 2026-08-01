package com.audiogames.audioroulette

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
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
     * Chame isso logo depois de um login com Google bem-sucedido.
     * Lê o que já existe na nuvem, mescla com o que está no aparelho
     * (pegando sempre o maior valor / a união das conquistas), salva o
     * resultado nos dois lugares, e avisa por voz se algo foi restaurado.
     */
    fun sincronizarAposLogin(onRestaurado: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(uid).child("perfil")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val progressoRestaurado = mesclarComNuvem(snapshot)
                salvarNaNuvem(ref)
                if (progressoRestaurado) {
                    onRestaurado()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Sem internet ou sem permissão - segue só com os dados
                // locais, sem travar o login por causa disso.
            }
        })
    }

    /** Chame sempre que uma conquista, vitória ou recorde local mudar, pra manter a nuvem atualizada. */
    fun sincronizarSilenciosamente() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (FirebaseAuth.getInstance().currentUser?.isAnonymous != false) return
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(uid).child("perfil")
        salvarNaNuvem(ref)
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

    private fun salvarNaNuvem(ref: com.google.firebase.database.DatabaseReference) {
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
    }
}
