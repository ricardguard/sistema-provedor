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
    val nome: String,
    val cpf: String,
    val email: String? = null,
    val telefone: String? = null,
    val cargo: String,
    val salario: BigDecimal,
    val setorId: Int,
    val setorNome: String = "",
    val dataAdmissao: LocalDate = LocalDate.now(),
    val ativo: Boolean = true
)

data class Cliente(
    val id: Int = 0,
    val nome: String,
    val cpfCnpj: String,
    val email: String? = null,
    val telefone: String? = null,
    val endereco: String? = null,
    val cidade: String? = null,
    val dataCadastro: LocalDateTime = LocalDateTime.now(),
    val ativo: Boolean = true
)

data class Fornecedor(
    val id: Int = 0,
    val razaoSocial: String,
    val cnpj: String,
    val email: String? = null,
    val telefone: String? = null,
    val ativo: Boolean = true
)
