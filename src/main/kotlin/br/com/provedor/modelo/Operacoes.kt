package br.com.provedor.modelo

import java.math.BigDecimal
import java.time.LocalDateTime

enum class TipoOrdem { INSTALACAO, MANUTENCAO, RETIRADA }

enum class StatusOrdem { ABERTA, ENCERRADA, CANCELADA }

data class OrdemServico(
    val id: Int = 0,
    val clienteId: Int,
    val clienteNome: String = "",
    val tecnicoId: Int? = null,
    val tecnicoNome: String? = null,
    val tipo: TipoOrdem,
    val descricao: String,
    val valor: BigDecimal = BigDecimal.ZERO,
    val abertura: LocalDateTime = LocalDateTime.now(),
    val encerramento: LocalDateTime? = null,
    val status: StatusOrdem = StatusOrdem.ABERTA
)

/** Material que o tecnico gastou na OS. */
data class ItemOrdemServico(
    val id: Int = 0,
    val ordemId: Int,
    val produtoId: Int,
    val produtoDescricao: String = "",
    val quantidade: Int
)

data class Compra(
    val id: Int = 0,
    val fornecedorId: Int,
    val fornecedorNome: String = "",
    val produtoId: Int,
    val produtoDescricao: String = "",
    val quantidade: Int,
    val valorUnitario: BigDecimal,
    val responsavelId: Int,
    val dataCompra: LocalDateTime = LocalDateTime.now()
) {
    val valorTotal: BigDecimal
        get() = valorUnitario.multiply(BigDecimal(quantidade))
}

enum class TipoMovimentacao { ENTRADA, SAIDA }

/**
 * Registro do livro caixa. Guarda tudo que o enunciado pede:
 * quanto, quem pagou, quem recebeu, quando, por que e quem foi o responsavel.
 */
data class Movimentacao(
    val id: Int = 0,
    val tipo: TipoMovimentacao,
    val categoria: String,
    val valor: BigDecimal,
    val pagador: String,
    val recebedor: String,
    val dataHora: LocalDateTime = LocalDateTime.now(),
    val descricao: String,
    val responsavelId: Int,
    val responsavelNome: String = "",
    val saldoApos: BigDecimal = BigDecimal.ZERO
)
