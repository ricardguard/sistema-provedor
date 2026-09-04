package br.com.provedor.menu

import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.SetorDao
import br.com.provedor.modelo.TipoMovimentacao
import br.com.provedor.servico.Caixa
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.servico.ServicoFinanceiro
import br.com.provedor.util.Empresa
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import br.com.provedor.util.Validacao
import java.math.BigDecimal
import java.time.LocalDate

/** Caixa, folha de pagamento, despesas e relatorios. */
object MenuFinanceiro {

    private val servicoFinanceiro = ServicoFinanceiro()
    private val funcionarioDao = FuncionarioDao()
    private val setorDao = SetorDao()

    fun exibir() {
        while (true) {
            Caixa.atualizarDoBanco()
            Formato.titulo("Financeiro")
            println("  Saldo em caixa: ${Formato.moeda(Caixa.saldoAtual)}")
            println(Formato.linha())
            println("  1 - Pagar salario de funcionario")
            println("  2 - Pagar folha de um setor inteiro")
            println("  3 - Pagar despesa")
            println("  4 - Registrar aporte no caixa")
            println("  5 - Ultimas movimentacoes")
            println("  6 - Extrato por periodo")
            println("  7 - Resumo do periodo (entradas x saidas)")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 7)) {
                1 -> protegido { pagarSalario() }
                2 -> protegido { pagarFolhaDoSetor() }
                3 -> protegido { pagarDespesa() }
                4 -> protegido { registrarAporte() }
                5 -> protegido { ultimasMovimentacoes() }
                6 -> protegido { extratoPorPeriodo() }
                7 -> protegido { resumoDoPeriodo() }
                0 -> return
            }
        }
    }

    private fun pagarSalario() {
        Formato.titulo("Pagamento de salario")
        val funcionarios = funcionarioDao.listar(somenteAtivos = true)
        if (funcionarios.isEmpty()) throw RegraDeNegocioException("Nao ha funcionario ativo.")

        funcionarios.forEach {
            println("   [${it.id}] ${Formato.encurtar(it.nome, 24)} ${Formato.encurtar(it.setorNome, 14)} " +
                    "${Formato.encurtar(it.contratacao.rotulo, 11)} ${Formato.moeda(it.valorDoPagamento)}")
        }
        println("  (valor ja considerando o regime de cada um)")
        val id = Entrada.inteiro("Codigo do funcionario (0 cancela): ", 0)
        if (id == 0) return

        val competencia = Entrada.texto(
            "Competencia (MM/AAAA): ", 7,
            { Validacao.competenciaValida(it) }, "Use o formato MM/AAAA."
        )
        val funcionario = funcionarioDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Funcionario nao encontrado.")

        println("\n  Regime: ${funcionario.contratacao.rotulo}")
        println("  Cadastrado: ${Formato.moeda(funcionario.salario)}")
        println("  Vai sair do caixa: ${Formato.moeda(funcionario.valorDoPagamento)} " +
                "(saldo atual ${Formato.moeda(Caixa.saldoAtual)}).")
        if (!Entrada.confirmar("Confirma o pagamento?")) return

        val mov = servicoFinanceiro.pagarSalario(id, competencia, Sessao.logado)
        println("\n  Pagamento registrado (movimentacao ${mov.id}).")
        println("  Saldo em caixa: ${Formato.moeda(mov.saldoApos)}")
    }

    private fun pagarFolhaDoSetor() {
        Formato.titulo("Folha por setor")
        val setores = setorDao.listar()
        setores.forEach { println("   [${it.id}] ${it.nome}") }
        val setorId = Entrada.inteiro("Codigo do setor (0 cancela): ", 0)
        if (setorId == 0) return

        val equipe = funcionarioDao.listarPorSetor(setorId).filter { it.ativo }
        if (equipe.isEmpty()) throw RegraDeNegocioException("Esse setor nao tem funcionario ativo.")

        val total = equipe.fold(BigDecimal.ZERO) { soma, f -> soma.add(f.valorDoPagamento) }
        println("\n  ${equipe.size} funcionario(s), total de ${Formato.moeda(total)}.")
        println("  Saldo em caixa: ${Formato.moeda(Caixa.saldoAtual)}")

        val competencia = Entrada.texto(
            "Competencia (MM/AAAA): ", 7,
            { Validacao.competenciaValida(it) }, "Use o formato MM/AAAA."
        )
        if (!Entrada.confirmar("Confirma o pagamento da folha do setor?")) return

        // O servico paga a folha inteira numa transacao so: cada salario vira
        // uma movimentacao propria, mas ou entram todas ou nao entra nenhuma.
        val lancamentos = servicoFinanceiro.pagarFolhaDoSetor(setorId, competencia, Sessao.logado)
        println("\n  ${lancamentos.size} salario(s) pagos. Saldo em caixa: ${Formato.moeda(Caixa.saldoAtual)}")
    }

    private fun pagarDespesa() {
        Formato.titulo("Pagamento de despesa")
        println("  Categorias comuns: ENERGIA, ALUGUEL, LINK_INTERNET, COMBUSTIVEL, MANUTENCAO, IMPOSTOS")

        val categoria = Entrada.texto("Categoria: ", 30, { it.length >= 3 }).uppercase().replace(" ", "_")
        val favorecido = Entrada.texto("Quem vai receber: ", 120, { it.length >= 3 })
        val descricao = Entrada.texto("Motivo/descricao: ", 250, { it.length >= 5 })
        val valor = Entrada.decimal("Valor: ", BigDecimal("0.01"))

        println("\n  Saldo atual: ${Formato.moeda(Caixa.saldoAtual)}")
        if (!Entrada.confirmar("Confirma a saida de ${Formato.moeda(valor)}?")) return

        val mov = servicoFinanceiro.pagarDespesa(valor, categoria, favorecido, descricao, Sessao.logado)
        println("\n  Despesa lancada (movimentacao ${mov.id}). Saldo: ${Formato.moeda(mov.saldoApos)}")
    }

    private fun registrarAporte() {
        Formato.titulo("Aporte no caixa")
        val origem = Entrada.texto("Quem esta colocando o dinheiro: ", 120, { it.length >= 3 })
        val descricao = Entrada.texto("Motivo: ", 250, { it.length >= 5 })
        val valor = Entrada.decimal("Valor: ", BigDecimal("0.01"))

        if (!Entrada.confirmar("Confirma a entrada de ${Formato.moeda(valor)}?")) return

        val mov = servicoFinanceiro.registrarAporte(valor, origem, descricao, Sessao.logado)
        println("\n  Aporte registrado. Saldo: ${Formato.moeda(mov.saldoApos)}")
    }

    private fun ultimasMovimentacoes() {
        val movimentacoes = servicoFinanceiro.ultimasMovimentacoes(15)
        Formato.titulo("Ultimas movimentacoes")
        if (movimentacoes.isEmpty()) {
            println("  Ainda nao tem movimentacao registrada.")
            return
        }
        movimentacoes.forEach { m ->
            val sinal = if (m.tipo == TipoMovimentacao.ENTRADA) "+" else "-"
            println("  ${Formato.dataHora(m.dataHora)}  $sinal${Formato.moeda(m.valor)}  ${m.categoria}")
            println("     de ${m.pagador} para ${m.recebedor}")
            println("     ${m.descricao}")
            println("     responsavel: ${m.responsavelNome} | saldo apos: ${Formato.moeda(m.saldoApos)}")
            println()
        }
    }

    private fun extratoPorPeriodo() {
        Formato.titulo("Extrato do caixa")
        val inicio = Entrada.data("Data inicial", LocalDate.now().withDayOfMonth(1))
        val fim = Entrada.data("Data final", LocalDate.now())
        if (fim.isBefore(inicio)) throw RegraDeNegocioException("A data final esta antes da inicial.")

        val extrato = servicoFinanceiro.extrato(inicio, fim)
        if (extrato.isEmpty()) {
            println("\n  Sem movimentacao nesse periodo.")
            return
        }
        println("\n  DATA/HORA         TIPO     VALOR         CATEGORIA        RESPONSAVEL")
        extrato.forEach { m ->
            println(
                "  ${Formato.dataHora(m.dataHora)}  ${m.tipo.name.padEnd(8)}" +
                        "${Formato.moeda(m.valor).padEnd(14)}${Formato.encurtar(m.categoria, 16)} " +
                        Formato.encurtar(m.responsavelNome, 18)
            )
        }
        println("\n  ${extrato.size} lancamento(s).")
    }

    private fun resumoDoPeriodo() {
        Formato.titulo("Resumo do caixa")
        val inicio = Entrada.data("Data inicial", LocalDate.now().withDayOfMonth(1))
        val fim = Entrada.data("Data final", LocalDate.now())

        val resumo = servicoFinanceiro.resumoDoPeriodo(inicio, fim)
        println()
        println("  Periodo:   ${Formato.data(resumo.inicio)} a ${Formato.data(resumo.fim)}")
        println("  Entradas:  ${Formato.moeda(resumo.entradas)}")
        println("  Saidas:    ${Formato.moeda(resumo.saidas)}")
        println("  Resultado: ${Formato.moeda(resumo.resultado)}")
        println("  Saldo em caixa hoje: ${Formato.moeda(resumo.saldoAtual)}")

        val categorias = servicoFinanceiro.totaisPorCategoria(inicio, fim)
        if (categorias.isNotEmpty()) {
            println("\n  Por categoria:")
            categorias.forEach { (tipo, categoria, total) ->
                println("   ${tipo.padEnd(8)} ${Formato.encurtar(categoria, 20)} ${Formato.moeda(total)}")
            }
        }
        println("\n  Empresa: ${Empresa.NOME}")
    }
}
