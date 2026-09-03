package br.com.provedor.menu

import br.com.provedor.dao.CompraDao
import br.com.provedor.dao.FornecedorDao
import br.com.provedor.dao.ProdutoDao
import br.com.provedor.modelo.Produto
import br.com.provedor.servico.Caixa
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.servico.ServicoEstoque
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import java.math.BigDecimal

/** Produtos, estoque e compra de material do fornecedor. */
object MenuEstoque {

    private val produtoDao = ProdutoDao()
    private val fornecedorDao = FornecedorDao()
    private val compraDao = CompraDao()
    private val servicoEstoque = ServicoEstoque()

    fun exibir() {
        while (true) {
            Formato.titulo("Estoque e compras")
            println("  1 - Listar produtos")
            println("  2 - Cadastrar produto")
            println("  3 - Alterar produto")
            println("  4 - Comprar do fornecedor")
            println("  5 - Produtos abaixo do minimo")
            println("  6 - Ajuste de inventario")
            println("  7 - Ultimas compras")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 7)) {
                1 -> protegido { listarProdutos(produtoDao.listar()) }
                2 -> protegido { cadastrarProduto() }
                3 -> protegido { alterarProduto() }
                4 -> protegido { comprar() }
                5 -> protegido { listarProdutos(produtoDao.listarAbaixoDoMinimo()) }
                6 -> protegido { ajustarInventario() }
                7 -> protegido { ultimasCompras() }
                0 -> return
            }
        }
    }

    private fun listarProdutos(lista: List<Produto>) {
        Formato.titulo("Produtos")
        if (lista.isEmpty()) {
            println("  Nenhum produto nessa listagem.")
            return
        }
        println("  COD DESCRICAO                  UN   QTDE  MIN   CUSTO       VENDA")
        lista.forEach {
            val alerta = if (it.abaixoDoMinimo) "<" else " "
            println(
                "  ${it.id.toString().padStart(3)}$alerta${Formato.encurtar(it.descricao, 25)} " +
                        "${it.unidade.padEnd(4)} ${it.quantidadeEstoque.toString().padStart(5)} " +
                        "${it.estoqueMinimo.toString().padStart(4)}  " +
                        "${Formato.moeda(it.precoCusto).padEnd(11)} ${Formato.moeda(it.precoVenda)}"
            )
        }
        println("\n  (< = estoque no limite ou abaixo do minimo)")
    }

    private fun cadastrarProduto() {
        Formato.titulo("Novo produto")
        val descricao = Entrada.texto("Descricao: ", 120, { it.length >= 3 })
        val unidade = Entrada.texto("Unidade (UN, MT, CX): ", 10, { it.length in 1..10 }).uppercase()
        val quantidade = Entrada.inteiro("Quantidade inicial em estoque: ", 0)
        val minimo = Entrada.inteiro("Estoque minimo: ", 0)
        val custo = Entrada.decimal("Preco de custo: ")
        val venda = Entrada.decimal("Preco de venda: ")

        val fornecedores = fornecedorDao.listar(somenteAtivos = true)
        var fornecedorId: Int? = null
        if (fornecedores.isNotEmpty()) {
            println("\n  Fornecedores:")
            fornecedores.forEach { println("   [${it.id}] ${it.razaoSocial}") }
            val escolha = Entrada.inteiro("Fornecedor padrao (0 = nenhum): ", 0)
            if (escolha != 0) {
                fornecedorDao.buscarPorId(escolha) ?: throw RegraDeNegocioException("Fornecedor nao encontrado.")
                fornecedorId = escolha
            }
        }

        val id = produtoDao.inserir(
            Produto(
                descricao = descricao, unidade = unidade, quantidadeEstoque = quantidade,
                estoqueMinimo = minimo, precoCusto = custo, precoVenda = venda,
                fornecedorId = fornecedorId
            )
        )
        println("\n  Produto cadastrado com o codigo $id.")
    }

    private fun alterarProduto() {
        listarProdutos(produtoDao.listar())
        val produto = selecionarProduto() ?: return

        val descricao = Entrada.texto("Descricao [${produto.descricao}]: ", 120, { it.length >= 3 })
        val unidade = Entrada.texto("Unidade [${produto.unidade}]: ", 10, { it.length in 1..10 }).uppercase()
        val minimo = Entrada.inteiro("Estoque minimo [${produto.estoqueMinimo}]: ", 0)
        val custo = Entrada.decimal("Custo [${Formato.moeda(produto.precoCusto)}]: ")
        val venda = Entrada.decimal("Venda [${Formato.moeda(produto.precoVenda)}]: ")

        produtoDao.atualizar(
            produto.copy(
                descricao = descricao, unidade = unidade, estoqueMinimo = minimo,
                precoCusto = custo, precoVenda = venda
            )
        )
        println("\n  Produto atualizado. (a quantidade so muda por compra, OS ou inventario)")
    }

    private fun comprar() {
        Formato.titulo("Compra de material")
        println("  Saldo em caixa: ${Formato.moeda(Caixa.saldoAtual)}")

        val fornecedores = fornecedorDao.listar(somenteAtivos = true)
        if (fornecedores.isEmpty()) throw RegraDeNegocioException("Cadastre um fornecedor ativo antes.")
        println()
        fornecedores.forEach { println("   [${it.id}] ${it.razaoSocial}") }
        val fornecedorId = Entrada.inteiro("Codigo do fornecedor: ", 1)

        listarProdutos(produtoDao.listar())
        val produto = selecionarProduto() ?: return

        val quantidade = Entrada.inteiro("Quantidade comprada: ", 1)
        val valorUnitario = Entrada.decimal("Valor unitario: ", BigDecimal("0.01"))
        val total = valorUnitario.multiply(BigDecimal(quantidade))

        println("\n  Total da compra: ${Formato.moeda(total)}")
        if (!Entrada.confirmar("Confirma a compra e a saida do caixa?")) return

        val compra = servicoEstoque.comprarDoFornecedor(
            produto = produto,
            fornecedorId = fornecedorId,
            quantidade = quantidade,
            valorUnitario = valorUnitario,
            responsavel = Sessao.logado
        )
        println("\n  Compra ${compra.id} registrada, estoque atualizado e caixa debitado.")
        println("  Saldo em caixa agora: ${Formato.moeda(Caixa.saldoAtual)}")
    }

    private fun ajustarInventario() {
        listarProdutos(produtoDao.listar())
        val produto = selecionarProduto() ?: return
        println("  Sistema aponta ${produto.quantidadeEstoque} ${produto.unidade}.")
        val contado = Entrada.inteiro("Quantidade contada na prateleira: ", 0)
        if (contado == produto.quantidadeEstoque) {
            println("\n  Contagem bateu com o sistema, nada a ajustar.")
            return
        }
        if (Entrada.confirmar("Ajustar de ${produto.quantidadeEstoque} para $contado?")) {
            servicoEstoque.ajustarInventario(produto, contado)
            println("\n  Estoque ajustado.")
        }
    }

    private fun ultimasCompras() {
        val compras = compraDao.listarUltimas()
        Formato.titulo("Ultimas compras")
        if (compras.isEmpty()) {
            println("  Nenhuma compra registrada.")
            return
        }
        compras.forEach {
            println(
                "  [${it.id}] ${Formato.dataHora(it.dataCompra)} ${Formato.encurtar(it.produtoDescricao, 22)} " +
                        "x${it.quantidade} - ${Formato.moeda(it.valorTotal)} - ${it.fornecedorNome}"
            )
        }
    }

    private fun selecionarProduto(): Produto? {
        val id = Entrada.inteiro("Codigo do produto (0 cancela): ", 0)
        if (id == 0) return null
        return produtoDao.buscarPorId(id) ?: throw RegraDeNegocioException("Produto $id nao existe.")
    }
}
