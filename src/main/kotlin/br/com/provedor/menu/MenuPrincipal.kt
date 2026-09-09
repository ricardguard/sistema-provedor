package br.com.provedor.menu

import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.ContratoDao
import br.com.provedor.dao.FaturaDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.OrdemServicoDao
import br.com.provedor.dao.ProdutoDao
import br.com.provedor.dao.SetorDao
import br.com.provedor.modelo.StatusContrato
import br.com.provedor.servico.Caixa
import br.com.provedor.util.Empresa
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import java.math.BigDecimal

object MenuPrincipal {

    private val setorDao = SetorDao()
    private val funcionarioDao = FuncionarioDao()
    private val clienteDao = ClienteDao()
    private val contratoDao = ContratoDao()
    private val faturaDao = FaturaDao()
    private val produtoDao = ProdutoDao()
    private val ordemDao = OrdemServicoDao()

    fun exibir() {
        while (true) {
            Caixa.atualizarDoBanco()

            println()
            println(Formato.linha())
            println("  ${Empresa.NOME_CURTO} - sistema de gestao")
            println("  Operador: ${Sessao.logado.nome} (${Sessao.logado.setorNome})")
            println("  Caixa: ${Formato.moeda(Caixa.saldoAtual)}")
            println(Formato.linha())
            println("  1 - Cadastros (setores, funcionarios, clientes, fornecedores)")
            println("  2 - Comercial (planos, contratos, faturas)")
            println("  3 - Estoque e compras")
            println("  4 - Ordens de servico")
            println("  5 - Financeiro / caixa")
            println("  6 - Relatorios gerais")
            println("  7 - Trocar de operador")
            println("  0 - Sair")
            println(Formato.linha())

            when (Entrada.opcao(0, 7)) {
                1 -> MenuCadastros.exibir()
                2 -> MenuComercial.exibir()
                3 -> MenuEstoque.exibir()
                4 -> MenuOrdens.exibir()
                5 -> MenuFinanceiro.exibir()
                6 -> menuRelatorios()
                7 -> protegido { Sessao.identificar() }
                0 -> {
                    println("\n  Sistema encerrado. Ate mais.")
                    return
                }
            }
        }
    }

    private fun menuRelatorios() {
        while (true) {
            Formato.titulo("Relatorios gerais")
            println("  1 - Panorama da empresa")
            println("  2 - Funcionarios por setor")
            println("  3 - Inadimplencia (faturas vencidas)")
            println("  4 - Estoque abaixo do minimo")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 4)) {
                1 -> protegido { panorama() }
                2 -> protegido { funcionariosPorSetor() }
                3 -> protegido { inadimplencia() }
                4 -> protegido { estoqueBaixo() }
                0 -> return
            }
        }
    }

    private fun panorama() {
        val contratos = contratoDao.listar()
        val ativos = contratos.count { it.status == StatusContrato.ATIVO }
        var receitaPrevista = BigDecimal.ZERO
        for (contrato in contratos) {
            if (contrato.status == StatusContrato.ATIVO) {
                receitaPrevista = receitaPrevista.add(contrato.valorMensal)
            }
        }

        Formato.titulo("Panorama - ${Empresa.NOME}")
        println("  Setores:                ${setorDao.listar().size}")
        println("  Funcionarios ativos:    ${funcionarioDao.listar(somenteAtivos = true).size}")
        println("  Clientes ativos:        ${clienteDao.listar(somenteAtivos = true).size}")
        println("  Contratos ativos:       $ativos de ${contratos.size}")
        println("  Receita mensal prevista: ${Formato.moeda(receitaPrevista)}")
        println("  Faturas em aberto:      ${faturaDao.listarEmAberto().size}")
        println("  OS em aberto:           ${ordemDao.listar(apenasAbertas = true).size}")
        println("  Itens abaixo do minimo: ${produtoDao.listarAbaixoDoMinimo().size}")
        println("  Saldo em caixa:         ${Formato.moeda(Caixa.saldoAtual)}")
    }

    private fun funcionariosPorSetor() {
        Formato.titulo("Funcionarios por setor")
        val setores = setorDao.listar()
        if (setores.isEmpty()) {
            println("  Nenhum setor cadastrado.")
            return
        }
        setores.forEach { setor ->
            val equipe = funcionarioDao.listarPorSetor(setor.id).filter { it.ativo }
            var folha = BigDecimal.ZERO
            for (funcionario in equipe) {
                folha = folha.add(funcionario.salario)
            }
            println("\n  ${setor.nome} - ${equipe.size} pessoa(s) - folha ${Formato.moeda(folha)}")
            equipe.forEach { println("    - ${Formato.encurtar(it.nome, 28)} ${it.cargo}") }
            if (equipe.isEmpty()) println("    (sem funcionario ativo)")
        }
    }

    private fun inadimplencia() {
        val vencidas = faturaDao.listarEmAberto().filter { it.emAtraso }
        Formato.titulo("Faturas vencidas")
        if (vencidas.isEmpty()) {
            println("  Nenhuma fatura em atraso.")
            return
        }
        var total = BigDecimal.ZERO
        for (fatura in vencidas) {
            total = total.add(fatura.valor)
        }
        vencidas.forEach {
            println("  [${it.id}] ${Formato.encurtar(it.clienteNome, 26)} ${it.competencia} " +
                    "venceu em ${Formato.data(it.vencimento)} - ${Formato.moeda(it.valor)}")
        }
        println("\n  ${vencidas.size} fatura(s) - total de ${Formato.moeda(total)}")
    }

    private fun estoqueBaixo() {
        val produtos = produtoDao.listarAbaixoDoMinimo()
        Formato.titulo("Estoque abaixo do minimo")
        if (produtos.isEmpty()) {
            println("  Estoque tranquilo, nada abaixo do minimo.")
            return
        }
        produtos.forEach {
            println("  [${it.id}] ${Formato.encurtar(it.descricao, 28)} " +
                    "saldo ${it.quantidadeEstoque} ${it.unidade} (minimo ${it.estoqueMinimo})")
        }
    }
}
