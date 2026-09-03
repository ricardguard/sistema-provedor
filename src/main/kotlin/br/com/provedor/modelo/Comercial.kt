package br.com.provedor.modelo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Plano de internet - e o servico que o provedor vende. */
data class Plano(
    val id: Int = 0,
    val nome: String,
    val velocidadeMega: Int,
    val valorMensal: BigDecimal,
    val taxaInstalacao: BigDecimal = BigDecimal.ZERO,
    val ativo: Boolean = true
)

/** Produto de estoque: roteador, ONU, cabo drop, conector etc. */
data class Produto(
    val id: Int = 0,
    val descricao: String,
    val unidade: String = "UN",
    val quantidadeEstoque: Int = 0,
    val estoqueMinimo: Int = 0,
    val precoCusto: BigDecimal = BigDecimal.ZERO,
    val precoVenda: BigDecimal = BigDecimal.ZERO,
    val fornecedorId: Int? = null
) {
    val abaixoDoMinimo: Boolean
        get() = quantidadeEstoque <= estoqueMinimo
}

enum class StatusContrato { ATIVO, SUSPENSO, CANCELADO }

data class Contrato(
    val id: Int = 0,
    val clienteId: Int,
    val clienteNome: String = "",
    val planoId: Int,
    val planoNome: String = "",
    val valorMensal: BigDecimal = BigDecimal.ZERO,
    val vendedorId: Int? = null,
    val dataInicio: LocalDate = LocalDate.now(),
    val diaVencimento: Int,
    val status: StatusContrato = StatusContrato.ATIVO,
    val dataCancelamento: LocalDate? = null
)

enum class StatusFatura { ABERTA, PAGA, CANCELADA }

data class Fatura(
    val id: Int = 0,
    val contratoId: Int,
    val clienteNome: String = "",
    val competencia: String,
    val valor: BigDecimal,
    val vencimento: LocalDate,
    val status: StatusFatura = StatusFatura.ABERTA,
    val dataPagamento: LocalDateTime? = null
) {
    val emAtraso: Boolean
        get() = status == StatusFatura.ABERTA && vencimento.isBefore(LocalDate.now())
}
