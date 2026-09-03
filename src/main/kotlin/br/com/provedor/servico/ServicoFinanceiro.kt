package br.com.provedor.servico

import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.MovimentacaoDao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Movimentacao
import br.com.provedor.modelo.TipoMovimentacao
import br.com.provedor.util.Empresa
import java.math.BigDecimal
import java.time.LocalDate

/** Resumo do periodo, usado no relatorio do menu financeiro. */
data class ResumoCaixa(
    val inicio: LocalDate,
    val fim: LocalDate,
    val entradas: BigDecimal,
    val saidas: BigDecimal,
    val saldoAtual: BigDecimal
) {
    val resultado: BigDecimal
        get() = entradas.subtract(saidas)
}

/**
 * Operacoes financeiras que nao vem de contrato nem de OS:
 * folha de pagamento, despesas fixas e aporte do socio.
 */
class ServicoFinanceiro(
    private val movimentacaoDao: MovimentacaoDao = MovimentacaoDao(),
    private val funcionarioDao: FuncionarioDao = FuncionarioDao()
) {

    /** Pagamento de salario. Sai do caixa e fica registrado quem autorizou. */
    fun pagarSalario(funcionarioId: Int, competencia: String, responsavel: Funcionario): Movimentacao {
        val funcionario = funcionarioDao.buscarPorId(funcionarioId)
            ?: throw RegraDeNegocioException("Funcionario nao encontrado.")
        if (!funcionario.ativo) {
            throw RegraDeNegocioException("Funcionario desligado nao entra na folha.")
        }
        if (funcionario.salario <= BigDecimal.ZERO) {
            throw RegraDeNegocioException("O salario cadastrado esta zerado, corrija o cadastro antes.")
        }

        return Caixa.registrarSaida(
            valor = funcionario.salario,
            categoria = "FOLHA_PAGAMENTO",
            pagador = Empresa.NOME,
            recebedor = funcionario.nome,
            descricao = "Salario referente a $competencia - setor ${funcionario.setorNome}",
            responsavel = responsavel
        )
    }

    /** Conta de luz, aluguel, link de internet, combustivel da equipe etc. */
    fun pagarDespesa(
        valor: BigDecimal,
        categoria: String,
        favorecido: String,
        descricao: String,
        responsavel: Funcionario
    ): Movimentacao = Caixa.registrarSaida(
        valor = valor,
        categoria = categoria,
        pagador = Empresa.NOME,
        recebedor = favorecido,
        descricao = descricao,
        responsavel = responsavel
    )

    /** Dinheiro que o socio coloca no caixa. */
    fun registrarAporte(
        valor: BigDecimal,
        origem: String,
        descricao: String,
        responsavel: Funcionario
    ): Movimentacao = Caixa.registrarEntrada(
        valor = valor,
        categoria = "APORTE",
        pagador = origem,
        recebedor = Empresa.NOME,
        descricao = descricao,
        responsavel = responsavel
    )

    fun resumoDoPeriodo(inicio: LocalDate, fim: LocalDate): ResumoCaixa {
        if (fim.isBefore(inicio)) {
            throw RegraDeNegocioException("A data final nao pode ser antes da inicial.")
        }
        return ResumoCaixa(
            inicio = inicio,
            fim = fim,
            entradas = movimentacaoDao.totalPorTipo(TipoMovimentacao.ENTRADA, inicio, fim),
            saidas = movimentacaoDao.totalPorTipo(TipoMovimentacao.SAIDA, inicio, fim),
            saldoAtual = Caixa.saldoAtual
        )
    }

    fun totaisPorCategoria(inicio: LocalDate, fim: LocalDate) =
        movimentacaoDao.totaisPorCategoria(inicio, fim)

    fun extrato(inicio: LocalDate, fim: LocalDate) = movimentacaoDao.listarPorPeriodo(inicio, fim)

    fun ultimasMovimentacoes(limite: Int = 15) = movimentacaoDao.listarUltimas(limite)
}
