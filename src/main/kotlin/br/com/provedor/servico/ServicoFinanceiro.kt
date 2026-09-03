package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.MovimentacaoDao
import br.com.provedor.dao.PagamentoSalarioDao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Movimentacao
import br.com.provedor.modelo.PagamentoSalario
import br.com.provedor.modelo.TipoMovimentacao
import br.com.provedor.util.Empresa
import br.com.provedor.util.Validacao
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
    private val funcionarioDao: FuncionarioDao = FuncionarioDao(),
    private val pagamentoDao: PagamentoSalarioDao = PagamentoSalarioDao()
) {

    /**
     * Pagamento de salario. Sai do caixa, fica registrado quem autorizou e
     * grava na tabela de folha - que tem UNIQUE por competencia, entao nao
     * tem como pagar o mesmo mes duas vezes.
     */
    fun pagarSalario(funcionarioId: Int, competencia: String, responsavel: Funcionario): Movimentacao {
        val funcionario = funcionarioDao.buscarPorId(funcionarioId)
            ?: throw RegraDeNegocioException("Funcionario nao encontrado.")
        if (!funcionario.ativo) {
            throw RegraDeNegocioException("Funcionario desligado nao entra na folha.")
        }
        if (funcionario.salario <= BigDecimal.ZERO) {
            throw RegraDeNegocioException("O salario cadastrado esta zerado, corrija o cadastro antes.")
        }
        if (!Validacao.competenciaValida(competencia)) {
            throw RegraDeNegocioException("Competencia invalida, use MM/AAAA.")
        }
        if (pagamentoDao.jaPago(funcionario.id, competencia)) {
            throw RegraDeNegocioException("O salario de ${funcionario.nome} referente a $competencia ja foi pago.")
        }

        return Transacao.executar {
            pagamentoDao.inserir(
                PagamentoSalario(
                    funcionarioId = funcionario.id,
                    competencia = competencia,
                    valor = funcionario.salario,
                    responsavelId = responsavel.id
                )
            )
            Caixa.registrarSaida(
                valor = funcionario.salario,
                categoria = "FOLHA_PAGAMENTO",
                pagador = Empresa.NOME,
                recebedor = funcionario.nome,
                descricao = "Salario referente a $competencia - setor ${funcionario.setorNome}",
                responsavel = responsavel
            )
        }.also { Caixa.sincronizar() }
    }

    /**
     * Folha do setor inteiro numa transacao so: ou paga todo mundo ou nao
     * paga ninguem. Antes isso era um laco solto e, se travasse no meio,
     * metade da equipe ficava paga e a outra metade nao.
     */
    fun pagarFolhaDoSetor(setorId: Int, competencia: String, responsavel: Funcionario): List<Movimentacao> {
        val equipe = funcionarioDao.listarPorSetor(setorId).filter { it.ativo }
        if (equipe.isEmpty()) throw RegraDeNegocioException("Esse setor nao tem funcionario ativo.")

        val jaPagos = equipe.filter { pagamentoDao.jaPago(it.id, competencia) }
        if (jaPagos.isNotEmpty()) {
            throw RegraDeNegocioException(
                "Ja tem salario pago em $competencia nesse setor: ${jaPagos.joinToString { it.nome }}."
            )
        }

        // Confere contra o banco, nao contra o valor em memoria, senao um
        // cache defasado poderia aprovar ou recusar a folha por engano.
        Caixa.sincronizar()
        val total = equipe.fold(BigDecimal.ZERO) { soma, f -> soma.add(f.salario) }
        if (total > Caixa.saldoAtual) {
            throw SaldoInsuficienteException(
                "O caixa nao cobre a folha inteira desse setor " +
                        "(folha ${total.toPlainString()}, caixa ${Caixa.saldoAtual.toPlainString()})."
            )
        }

        return Transacao.executar {
            equipe.map { pagarSalario(it.id, competencia, responsavel) }
        }.also { Caixa.sincronizar() }
    }

    fun folhaDaCompetencia(competencia: String) = pagamentoDao.listarPorCompetencia(competencia)

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
