package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.MovimentacaoDao
import br.com.provedor.dao.PagamentoSalarioDao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Movimentacao
import br.com.provedor.modelo.PagamentoSalario
import br.com.provedor.modelo.Terceiro
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
class ServicoFinanceiro {

    private val movimentacaoDao = MovimentacaoDao()
    private val funcionarioDao = FuncionarioDao()
    private val pagamentoDao = PagamentoSalarioDao()

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

        // Aqui esta o polimorfismo trabalhando: este servico nao sabe se o
        // funcionario e CLT, estagiario ou PJ. Ele pergunta ao objeto quanto
        // pagar, em que categoria lancar e o que escrever na descricao.
        val valorAPagar = funcionario.valorDoPagamento

        val resultado = Transacao.executar {
            pagamentoDao.inserir(
                PagamentoSalario(
                    funcionarioId = funcionario.id,
                    competencia = competencia,
                    valor = valorAPagar,
                    responsavelId = responsavel.id
                )
            )
            Caixa.registrarSaida(
                valor = valorAPagar,
                categoria = funcionario.contratacao.categoriaNoCaixa,
                pagador = Empresa,
                recebedor = funcionario,
                descricao = funcionario.contratacao.descricaoDoPagamento(competencia) +
                        " - setor ${funcionario.setorNome}",
                responsavel = responsavel
            )
        }

        // a transacao fechou, entao agora da pra reler o saldo
        Caixa.sincronizar()
        return resultado
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
        // soma o que cada um vai receber de fato, nao o valor cadastrado
        var total = BigDecimal.ZERO
        for (funcionario in equipe) {
            total = total.add(funcionario.valorDoPagamento)
        }
        if (total > Caixa.saldoAtual) {
            throw SaldoInsuficienteException(
                "O caixa nao cobre a folha inteira desse setor " +
                        "(folha ${total.toPlainString()}, caixa ${Caixa.saldoAtual.toPlainString()})."
            )
        }

        val resultado = Transacao.executar {
            equipe.map { pagarSalario(it.id, competencia, responsavel) }
        }

        // a transacao fechou, entao agora da pra reler o saldo
        Caixa.sincronizar()
        return resultado
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
        pagador = Empresa,
        recebedor = Terceiro(favorecido),
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
        pagador = Terceiro(origem),
        recebedor = Empresa,
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
