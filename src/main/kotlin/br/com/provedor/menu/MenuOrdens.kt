package br.com.provedor.menu

import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.OrdemServicoDao
import br.com.provedor.dao.ProdutoDao
import br.com.provedor.modelo.OrdemServico
import br.com.provedor.modelo.TipoOrdem
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.servico.ServicoOrdem
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import java.math.BigDecimal

/** Ordens de servico: instalacao, manutencao e retirada em campo. */
object MenuOrdens {

    private val ordemDao = OrdemServicoDao()
    private val clienteDao = ClienteDao()
    private val funcionarioDao = FuncionarioDao()
    private val produtoDao = ProdutoDao()
    private val servicoOrdem = ServicoOrdem()

    fun exibir() {
        while (true) {
            Formato.titulo("Ordens de servico")
            println("  1 - OS em aberto")
            println("  2 - Abrir OS")
            println("  3 - Lancar material usado")
            println("  4 - Encerrar OS")
            println("  5 - Cancelar OS")
            println("  6 - Detalhe da OS")
            println("  7 - OS por tecnico")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 7)) {
                1 -> protegido { listar(ordemDao.listar(apenasAbertas = true), "OS em aberto") }
                2 -> protegido { abrir() }
                3 -> protegido { lancarMaterial() }
                4 -> protegido { encerrar() }
                5 -> protegido { cancelar() }
                6 -> protegido { detalhe() }
                7 -> protegido { porTecnico() }
                0 -> return
            }
        }
    }

    private fun listar(lista: List<OrdemServico>, titulo: String) {
        Formato.titulo(titulo)
        if (lista.isEmpty()) {
            println("  Nenhuma OS nessa listagem.")
            return
        }
        println("  COD TIPO         CLIENTE                 TECNICO           SITUACAO")
        lista.forEach {
            println(
                "  ${it.id.toString().padStart(3)} ${Formato.encurtar(it.tipo.name, 12)} " +
                        "${Formato.encurtar(it.clienteNome, 23)} " +
                        "${Formato.encurtar(it.tecnicoNome ?: "sem tecnico", 17)} ${it.status}"
            )
        }
    }

    private fun abrir() {
        Formato.titulo("Abrir OS")

        val clientes = clienteDao.listar(somenteAtivos = true)
        if (clientes.isEmpty()) throw RegraDeNegocioException("Nao ha cliente ativo cadastrado.")
        clientes.forEach { println("   [${it.id}] ${Formato.encurtar(it.nome, 30)} ${it.cidade ?: ""}") }
        val clienteId = Entrada.inteiro("Codigo do cliente: ", 1)

        println("\n  Tipo da OS:")
        TipoOrdem.entries.forEachIndexed { indice, tipo -> println("   ${indice + 1} - $tipo") }
        val tipo = TipoOrdem.entries[Entrada.inteiro("Tipo: ", 1, TipoOrdem.entries.size) - 1]

        val descricao = Entrada.texto("Descricao do servico: ", 250, { it.length >= 5 })
        val valor = Entrada.decimal("Valor da mao de obra (0 se for cortesia): ")

        val tecnicos = funcionarioDao.listar(somenteAtivos = true)
        println()
        tecnicos.forEach { println("   [${it.id}] ${Formato.encurtar(it.nome, 26)} ${it.setorNome}") }
        val tecnicoId = Entrada.inteiro("Tecnico responsavel (0 = definir depois): ", 0)

        val ordem = servicoOrdem.abrir(
            clienteId = clienteId,
            tipo = tipo,
            descricao = descricao,
            valor = valor,
            tecnicoId = if (tecnicoId == 0) null else tecnicoId
        )
        println("\n  OS ${ordem.id} aberta.")
    }

    private fun lancarMaterial() {
        listar(ordemDao.listar(apenasAbertas = true), "OS em aberto")
        val ordemId = Entrada.inteiro("Codigo da OS (0 cancela): ", 0)
        if (ordemId == 0) return

        val produtos = produtoDao.listar().filter { it.quantidadeEstoque > 0 }
        if (produtos.isEmpty()) throw RegraDeNegocioException("Nao ha produto com saldo em estoque.")
        println()
        produtos.forEach {
            println("   [${it.id}] ${Formato.encurtar(it.descricao, 28)} saldo ${it.quantidadeEstoque} ${it.unidade}")
        }
        val produtoId = Entrada.inteiro("Codigo do produto: ", 1)
        val quantidade = Entrada.inteiro("Quantidade usada: ", 1)

        val item = servicoOrdem.lancarMaterial(ordemId, produtoId, quantidade)
        println("\n  Material lancado na OS $ordemId e baixado do estoque (item ${item.id}).")
    }

    private fun encerrar() {
        listar(ordemDao.listar(apenasAbertas = true), "OS em aberto")
        val ordemId = Entrada.inteiro("Codigo da OS (0 cancela): ", 0)
        if (ordemId == 0) return

        val ordem = ordemDao.buscarPorId(ordemId) ?: throw RegraDeNegocioException("OS nao encontrada.")
        println("\n  Cliente: ${ordem.clienteNome}")
        println("  Servico: ${ordem.descricao}")
        println("  Valor:   ${Formato.moeda(ordem.valor)}")

        val receber = if (ordem.valor > BigDecimal.ZERO) {
            Entrada.confirmar("O cliente pagou o servico agora?")
        } else {
            false
        }
        if (!Entrada.confirmar("Confirma o encerramento da OS ${ordem.id}?")) return

        servicoOrdem.encerrar(ordem.id, receber, Sessao.logado)
        println("\n  OS encerrada." + if (receber) " Valor lancado no caixa." else "")
    }

    private fun cancelar() {
        listar(ordemDao.listar(apenasAbertas = true), "OS em aberto")
        val ordemId = Entrada.inteiro("Codigo da OS (0 cancela): ", 0)
        if (ordemId == 0) return
        if (!Entrada.confirmar("Cancelar a OS $ordemId e devolver o material pro estoque?")) return
        servicoOrdem.cancelar(ordemId)
        println("\n  OS cancelada e material devolvido.")
    }

    private fun detalhe() {
        listar(ordemDao.listar(), "Todas as OS")
        val ordemId = Entrada.inteiro("Codigo da OS (0 cancela): ", 0)
        if (ordemId == 0) return
        val ordem = ordemDao.buscarPorId(ordemId) ?: throw RegraDeNegocioException("OS nao encontrada.")

        Formato.titulo("OS ${ordem.id}")
        println("  Cliente:      ${ordem.clienteNome}")
        println("  Tipo:         ${ordem.tipo}")
        println("  Tecnico:      ${ordem.tecnicoNome ?: "nao definido"}")
        println("  Descricao:    ${ordem.descricao}")
        println("  Valor:        ${Formato.moeda(ordem.valor)}")
        println("  Abertura:     ${Formato.dataHora(ordem.abertura)}")
        println("  Encerramento: ${Formato.dataHora(ordem.encerramento)}")
        println("  Situacao:     ${ordem.status}")

        val itens = ordemDao.listarItens(ordem.id)
        println("\n  Material usado:")
        if (itens.isEmpty()) {
            println("   nenhum")
        } else {
            itens.forEach { println("   - ${it.quantidade}x ${it.produtoDescricao}") }
        }
    }

    private fun porTecnico() {
        val tecnicos = funcionarioDao.listar(somenteAtivos = true)
        tecnicos.forEach { println("   [${it.id}] ${Formato.encurtar(it.nome, 26)} ${it.setorNome}") }
        val id = Entrada.inteiro("Codigo do tecnico (0 cancela): ", 0)
        if (id == 0) return
        listar(ordemDao.listarPorTecnico(id), "OS do tecnico $id")
    }
}
