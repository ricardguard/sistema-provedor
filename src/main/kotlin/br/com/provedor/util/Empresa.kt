package br.com.provedor.util

import br.com.provedor.modelo.Pessoa

/**
 * A propria empresa dona do sistema. Implementa Pessoa porque ela e uma das
 * pontas de quase toda movimentacao: ou ela paga, ou ela recebe.
 */
object Empresa : Pessoa {

    const val NOME = "Conecta Fibra Provedor de Internet"
    const val NOME_CURTO = "Conecta Fibra"
    const val CNPJ = "11444777000161"

    override val nome: String get() = NOME

    override val documento: String get() = CNPJ

    override fun tipoDePessoa(): String = "Empresa"
}
