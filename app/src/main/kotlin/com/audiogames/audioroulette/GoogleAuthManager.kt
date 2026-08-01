package com.audiogames.audioroulette

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Login com conta Google, ligado ao Firebase Auth. Isso substitui o
 * login anônimo (usado só pra poder ler/escrever nas salas online) por
 * uma conta de verdade - permite, no futuro, sincronizar conquistas e
 * placar na nuvem, ligados à conta da pessoa, não só ao aparelho.
 */
class GoogleAuthManager(private val activity: Activity) {

    private val googleClient: GoogleSignInClient by lazy {
        val webClientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            null
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply { if (webClientId != null) requestIdToken(webClientId) }
            .requestEmail()
            .build()
        GoogleSignIn.getClient(activity, options)
    }

    /** Usuário do Firebase logado no momento (via Google), ou null se não estiver logado com Google. */
    fun usuarioGoogleAtual(): com.google.firebase.auth.FirebaseUser? {
        val user = FirebaseAuth.getInstance().currentUser
        return if (user != null && !user.isAnonymous) user else null
    }

    fun estaLogadoComGoogle(): Boolean = usuarioGoogleAtual() != null

    fun iniciarLogin(): Intent = googleClient.signInIntent

    /**
     * Chame isso a partir de onActivityResult, passando o Intent
     * recebido. [onSucesso] recebe o nome de exibição da conta Google.
     */
    fun tratarResultadoLogin(data: Intent?, onSucesso: (String) -> Unit, onErro: (String) -> Unit) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val conta = task.getResult(ApiException::class.java)
            val idToken = conta?.idToken

            if (idToken == null) {
                onErro("Não consegui confirmar sua conta Google. Verifique se o login do Google foi configurado no Firebase.")
                return
            }

            val credencial = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credencial)
                .addOnSuccessListener { resultado ->
                    val nome = resultado.user?.displayName ?: conta.displayName ?: "Jogador"
                    onSucesso(nome)
                }
                .addOnFailureListener { erro ->
                    onErro("Erro ao conectar com o Firebase: ${erro.message}")
                }
        } catch (e: ApiException) {
            onErro("Login cancelado ou falhou (código ${e.statusCode}).")
        }
    }

    fun sair(onConcluido: () -> Unit) {
        FirebaseAuth.getInstance().signOut()
        googleClient.signOut().addOnCompleteListener {
            // Depois de sair da conta Google, volta pro login anônimo,
            // pra continuar podendo jogar online normalmente.
            FirebaseService.ensureSignedIn { onConcluido() }
        }
    }

    companion object {
        const val REQUEST_CODE_GOOGLE_SIGN_IN = 9001
    }
}
