package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.CompraDao
import br.com.provedor.dao.FornecedorDao
import br.com.provedor.dao.ProdutoDao
import br.com.provedor.modelo.Compra
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Produto
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
    private val fornecedorDao: FornecedorDao = FornecedorDao()
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
        }
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

    /** Ajuste manual de inventario (quando a contagem fisica nao bate com o sistema). */
    fun ajustarInventario(produto: Produto, quantidadeContada: Int) {
        if (quantidadeContada < 0) throw RegraDeNegocioException("Quantidade contada nao pode ser negativa.")
        val diferenca = quantidadeContada - produto.quantidadeEstoque
        if (diferenca == 0) return
        if (!produtoDao.movimentarEstoque(produto.id, diferenca)) {
            throw RegraDeNegocioException("Nao consegui ajustar o estoque de ${produto.descricao}.")
        }
    }
}
