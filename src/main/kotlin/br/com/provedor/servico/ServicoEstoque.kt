package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.AjusteEstoqueDao
import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.CompraDao
import br.com.provedor.dao.FornecedorDao
import br.com.provedor.dao.ProdutoDao
import br.com.provedor.dao.VendaDao
import br.com.provedor.modelo.AjusteEstoque
import br.com.provedor.modelo.Compra
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Produto
import br.com.provedor.modelo.Venda
import br.com.provedor.util.Empresa
import java.math.BigDecimal

/**
 * Entrada e saida de material.
 * Comprar do fornecedor mexe em tres lugares (compra, estoque e caixa),
 * por isso tudo acontece dentro de uma transacao so.
 */
class ServicoEstoque(
    private val produtoDao: ProdutoDao = ProdutoDao(),
    private val compraDao: CompraDao = CompraDao(),
    private val vendaDao: VendaDao = VendaDao(),
    private val fornecedorDao: FornecedorDao = FornecedorDao(),
    private val clienteDao: ClienteDao = ClienteDao(),
    private val ajusteDao: AjusteEstoqueDao = AjusteEstoqueDao()
) {

    fun comprarDoFornecedor(
        produto: Produto,
        fornecedorId: Int,
        quantidade: Int,
        valorUnitario: BigDecimal,
        responsavel: Funcionario
    ): Compra {

        if (quantidade <= 0) throw RegraDeNegocioException("A quantidade da compra tem que ser maior que zero.")
        if (valorUnitario <= BigDecimal.ZERO) throw RegraDeNegocioException("O valor unitario tem que ser maior que zero.")

        val fornecedor = fornecedorDao.buscarPorId(fornecedorId)
            ?: throw RegraDeNegocioException("Fornecedor nao encontrado.")
        if (!fornecedor.ativo) throw RegraDeNegocioException("Esse fornecedor esta inativo.")

        val total = valorUnitario.multiply(BigDecimal(quantidade))

        return Transacao.executar {
            val compra = Compra(
                fornecedorId = fornecedor.id,
                produtoId = produto.id,
                quantidade = quantidade,
                valorUnitario = valorUnitario,
                responsavelId = responsavel.id
            )
            val id = compraDao.inserir(compra)

            // entrada no estoque
            if (!produtoDao.movimentarEstoque(produto.id, quantidade)) {
                throw RegraDeNegocioException("Nao consegui atualizar o estoque do produto ${produto.descricao}.")
            }
            // guardo o ultimo custo pago, serve de base pro preco de venda
            produtoDao.atualizarPrecoCusto(produto.id, valorUnitario)

            // saida do caixa - se faltar saldo, o Caixa derruba tudo aqui
            Caixa.registrarSaida(
                valor = total,
                categoria = "COMPRA_MATERIAL",
                pagador = Empresa.NOME,
                recebedor = fornecedor.razaoSocial,
                descricao = "Compra de $quantidade ${produto.unidade} de ${produto.descricao}",
                responsavel = responsavel
            )

            compra.copy(id = id, fornecedorNome = fornecedor.razaoSocial, produtoDescricao = produto.descricao)
        }.also { Caixa.sincronizar() }
    }

    /**
     * Venda de produto direto pro cliente: sai do estoque e entra no caixa.
     * E a contrapartida da compra do fornecedor - as duas pontas que o
     * enunciado pede no fluxo de caixa.
     */
    fun venderParaCliente(
        produto: Produto,
        clienteId: Int,
        quantidade: Int,
        valorUnitario: BigDecimal,
        responsavel: Funcionario
    ): Venda {

        if (quantidade <= 0) throw RegraDeNegocioException("A quantidade da venda tem que ser maior que zero.")
        if (valorUnitario <= BigDecimal.ZERO) throw RegraDeNegocioException("O valor unitario tem que ser maior que zero.")

        val cliente = clienteDao.buscarPorId(clienteId)
            ?: throw RegraDeNegocioException("Cliente nao encontrado.")
        if (!cliente.ativo) throw RegraDeNegocioException("Cliente inativo, reative o cadastro antes de vender.")

        if (produto.quantidadeEstoque < quantidade) {
            throw EstoqueInsuficienteException(
                "Estoque insuficiente de ${produto.descricao}: tem ${produto.quantidadeEstoque} e a venda e de $quantidade."
            )
        }

        val total = valorUnitario.multiply(BigDecimal(quantidade))

        return Transacao.executar {
            val venda = Venda(
                clienteId = cliente.id,
                produtoId = produto.id,
                quantidade = quantidade,
                valorUnitario = valorUnitario,
                responsavelId = responsavel.id
            )
            val id = vendaDao.inserir(venda)

            // saida do estoque
            if (!produtoDao.movimentarEstoque(produto.id, -quantidade)) {
                throw EstoqueInsuficienteException(
                    "Nao consegui baixar o estoque de ${produto.descricao} - alguem mexeu no saldo agora."
                )
            }

            // entrada no caixa
            Caixa.registrarEntrada(
                valor = total,
                categoria = "VENDA_PRODUTO",
                pagador = cliente.nome,
                recebedor = Empresa.NOME,
                descricao = "Venda de $quantidade ${produto.unidade} de ${produto.descricao}",
                responsavel = responsavel
            )

            venda.copy(id = id, clienteNome = cliente.nome, produtoDescricao = produto.descricao)
        }.also { Caixa.sincronizar() }
    }

    /** Baixa de material usado em campo. Nao mexe em dinheiro, so no estoque. */
    fun baixarEstoque(produto: Produto, quantidade: Int) {
        if (quantidade <= 0) throw RegraDeNegocioException("Quantidade invalida para baixa.")
        if (produto.quantidadeEstoque < quantidade) {
            throw EstoqueInsuficienteException(
                "Estoque insuficiente de ${produto.descricao}: tem ${produto.quantidadeEstoque} e precisa de $quantidade."
            )
        }
        if (!produtoDao.movimentarEstoque(produto.id, -quantidade)) {
            throw EstoqueInsuficienteException("Nao foi possivel baixar o estoque de ${produto.descricao}.")
        }
    }

    /**
     * Ajuste manual de inventario (quando a contagem fisica nao bate com o
     * sistema). Fica registrado quem ajustou, de quanto pra quanto e por que -
     * senao sumiria material sem deixar rastro.
     */
    fun ajustarInventario(
        produto: Produto,
        quantidadeContada: Int,
        motivo: String,
        responsavel: Funcionario
    ): AjusteEstoque {
        if (quantidadeContada < 0) throw RegraDeNegocioException("Quantidade contada nao pode ser negativa.")
        if (motivo.isBlank()) throw RegraDeNegocioException("Todo ajuste de inventario precisa de um motivo.")

        val diferenca = quantidadeContada - produto.quantidadeEstoque
        if (diferenca == 0) throw RegraDeNegocioException("A contagem bateu com o sistema, nao ha o que ajustar.")

        return Transacao.executar {
            if (!produtoDao.movimentarEstoque(produto.id, diferenca)) {
                throw RegraDeNegocioException("Nao consegui ajustar o estoque de ${produto.descricao}.")
            }
            val ajuste = AjusteEstoque(
                produtoId = produto.id,
                produtoDescricao = produto.descricao,
                quantidadeAnterior = produto.quantidadeEstoque,
                quantidadeNova = quantidadeContada,
                motivo = motivo.trim(),
                responsavelId = responsavel.id,
                responsavelNome = responsavel.nome
            )
            ajuste.copy(id = ajusteDao.inserir(ajuste))
        }
    }

    fun ultimosAjustes(limite: Int = 15) = ajusteDao.listarUltimos(limite)

    fun ultimasVendas(limite: Int = 20) = vendaDao.listarUltimas(limite)
}
