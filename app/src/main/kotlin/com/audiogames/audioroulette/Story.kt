package com.audiogames.audioroulette

import kotlin.random.Random

/**
 * Todo o texto narrativo do jogo: introdução, falas entre fases, provocações
 * do Dealer, e desfechos. Como usamos Text-to-Speech, qualquer frase aqui
 * pode ser dita sem precisar gravar áudio nenhum.
 */
object Story {

    val introducao = listOf(
        "Você acorda numa sala escura, com cheiro de pólvora e fumaça de charuto.",
        "Uma voz grave fala do outro lado da mesa: bem-vindo à Sala de Espera.",
        "Aqui, só existe uma regra: sobreviva ao Dealer, rodada após rodada, e você é livre.",
        "Entre nós, uma espingarda carregada com balas reais e falsas, embaralhadas.",
        "Cinco desafios te separam da saída. Prepare-se."
    )

    val nomesFase = mapOf(
        1 to "Fase Um: A Sala de Espera",
        2 to "Fase Dois: O Corredor dos Espelhos",
        3 to "Fase Três: A Mesa Rachada",
        4 to "Fase Quatro: O Último Cigarro",
        5 to "Fase Cinco: O Confronto Final",
        6 to "Fase Seis: A Porta Errada",
        7 to "Fase Sete: O Segundo Dealer",
        8 to "Fase Oito: A Saída de Verdade"
    )

    val introFase = mapOf(
        1 to "O Dealer acende um cigarro e sorri. Vamos começar, diz ele, com calma.",
        2 to "As paredes parecem mais próximas agora. O Dealer já não sorri tanto.",
        3 to "Alguma coisa rachou na mesa entre vocês dois. O Dealer nem percebe, ou finge que não.",
        4 to "O Dealer está com menos paciência agora. Isso está ficando pessoal, ele murmura.",
        5 to "Silêncio total. Essa é a última porta. Só um de vocês sai dessa sala.",
        6 to "Espera. Isso não deveria estar aqui. O Dealer também parece confuso, só por um segundo.",
        7 to "Uma segunda cadeira aparece do nada, do outro lado da mesa. O primeiro Dealer nunca foi o único.",
        8 to "Por trás de tudo isso, uma porta que você nunca tinha notado. Última rodada. De verdade, dessa vez."
    )

    val provocacoesDealer = listOf(
        "Sente o cheiro disso? É medo.",
        "Você treme. Eu adoro quando vocês tremem.",
        "Aposto meu próximo cigarro que essa é real.",
        "Relaxa. Só uma entre várias chances de tudo dar errado.",
        "Você já pensou em desistir? Ainda dá tempo.",
        "Interessante escolha. Vamos ver se foi sábia.",
        "Eu já fiz isso centenas de vezes. Você, nem um pouco.",
        "O silêncio antes do estalo é a minha parte favorita."
    )

    val dealerFerido = listOf(
        "Argh! Isso... isso doeu de verdade.",
        "Tsc. Sorte sua, dessa vez.",
        "Você tem coragem, eu admito.",
        "Isso não estava nos meus planos."
    )

    val jogadorFerido = listOf(
        "Isso deve ter doído. Quer parar por aqui?",
        "Vejo que você sangra como qualquer um.",
        "A dor é só o começo, meu amigo."
    )

    val faseVencidaFalas = listOf(
        "O Dealer cai da cadeira. Uma porta se abre com um estalo pesado.",
        "Silêncio. Depois, um aplauso lento e sarcástico do Dealer caído.",
        "Você respira fundo. Uma porta se abre à sua frente."
    )

    val campanhaVencidaFalas = listOf(
        "A última porta se abre para a luz do dia. Você sobreviveu à Sala de Espera.",
        "O Dealer não vai mais provocar ninguém. Você está livre.",
        "Cinco fases. Um sobrevivente. Você."
    )

    val derrotaFalas = listOf(
        "Tudo escurece. O Dealer acende outro cigarro, como se nada tivesse acontecido.",
        "A última coisa que você ouve é uma risada baixa do outro lado da mesa.",
        "A Sala de Espera ganha mais um hóspede permanente."
    )

    fun sorteiaProvocacao(): String = provocacoesDealer[Random.nextInt(provocacoesDealer.size)]
    fun sorteiaDealerFerido(): String = dealerFerido[Random.nextInt(dealerFerido.size)]
    fun sorteiaJogadorFerido(): String = jogadorFerido[Random.nextInt(jogadorFerido.size)]
    fun sorteiaFaseVencida(): String = faseVencidaFalas[Random.nextInt(faseVencidaFalas.size)]
    fun sorteiaCampanhaVencida(): String = campanhaVencidaFalas[Random.nextInt(campanhaVencidaFalas.size)]
    fun sorteiaDerrota(): String = derrotaFalas[Random.nextInt(derrotaFalas.size)]
}
