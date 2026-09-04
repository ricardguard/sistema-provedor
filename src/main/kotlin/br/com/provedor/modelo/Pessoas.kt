package br.com.provedor.modelo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Setor da empresa (financeiro, comercial, suporte, administrativo...). */
data class Setor(
    val id: Int = 0,
    val nome: String,
    val descricao: String? = null
)

data class Funcionario(
    val id: Int = 0,
    override val nome: String,
    val cpf: String,
    val email: String? = null,
    val telefone: String? = null,
    val cargo: String,
    val salario: BigDecimal,
    val setorId: Int,
    val setorNome: String = "",
    val contratacao: Contratacao = Contratacao.Clt,
    val dataAdmissao: LocalDate = LocalDate.now(),
    val ativo: Boolean = true
) : Pessoa {

    override val documento: String get() = cpf

    override fun tipoDePessoa(): String = "Funcionario"

    /** Quanto sai do caixa por este funcionario - depende do regime dele. */
    val valorDoPagamento: BigDecimal get() = contratacao.valorLiquido(salario)
}

data class Cliente(
    val id: Int = 0,
    override val nome: String,
    val cpfCnpj: String,
    val email: String? = null,
    val telefone: String? = null,
    val endereco: String? = null,
    val cidade: String? = null,
    val dataCadastro: LocalDateTime = LocalDateTime.now(),
    val ativo: Boolean = true
) : Pessoa {

    override val documento: String get() = cpfCnpj

    override fun tipoDePessoa(): String = "Cliente"

    /** Cliente com 14 digitos e empresa, com 11 e pessoa fisica. */
    val pessoaJuridica: Boolean get() = cpfCnpj.filter { it.isDigit() }.length == 14
}

data class Fornecedor(
    val id: Int = 0,
    val razaoSocial: String,
    val cnpj: String,
    val email: String? = null,
    val telefone: String? = null,
    val ativo: Boolean = true
) : Pessoa {

    // Fornecedor guarda "razao social", nao "nome" - a interface resolve
    // essa diferenca de vocabulario sem precisar renomear a coluna.
    override val nome: String get() = razaoSocial

    override val documento: String get() = cnpj

    override fun tipoDePessoa(): String = "Fornecedor"
}
