package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.OrdemServicoDao
import br.com.provedor.dao.ProdutoDao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.ItemOrdemServico
import br.com.provedor.modelo.OrdemServico
import br.com.provedor.modelo.StatusOrdem
import br.com.provedor.modelo.TipoOrdem
import br.com.provedor.util.Empresa
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Ordem de servico: instalacao, manutencao e retirada.
 * E aqui que o produto do estoque encontra o servico prestado ao cliente.
 */
class ServicoOrdem(
    private val ordemDao: OrdemServicoDao = OrdemServicoDao(),
    private val clienteDao: ClienteDao = ClienteDao(),
    private val funcionarioDao: FuncionarioDao = FuncionarioDao(),
    private val produtoDao: ProdutoDao = ProdutoDao(),
    private val servicoEstoque: ServicoEstoque = ServicoEstoque()
) {

    fun abrir(
        clienteId: Int,
        tipo: TipoOrdem,
        descricao: String,
        valor: BigDecimal,
        tecnicoId: Int?
    ): OrdemServico {

        val cliente = clienteDao.buscarPorId(clienteId)
            ?: throw RegraDeNegocioException("Cliente nao encontrado.")
        if (descricao.isBlank()) throw RegraDeNegocioException("Descreva o que precisa ser feito.")
        if (valor < BigDecimal.ZERO) throw RegraDeNegocioException("Valor da OS nao pode ser negativo.")

        if (tecnicoId != null) {
            val tecnico = funcionarioDao.buscarPorId(tecnicoId)
                ?: throw RegraDeNegocioException("Tecnico nao encontrado.")
            if (!tecnico.ativo) throw RegraDeNegocioException("Esse funcionario esta desligado.")
        }

        val ordem = OrdemServico(
            clienteId = cliente.id,
            clienteNome = cliente.nome,
            tecnicoId = tecnicoId,
            tipo = tipo,
            descricao = descricao.trim(),
            valor = valor
        )
        val id = ordemDao.inserir(ordem)
        return ordem.copy(id = id)
    }

    /** Material usado na OS: registra o item e ja baixa do estoque. */
    fun lancarMaterial(ordemId: Int, produtoId: Int, quantidade: Int): ItemOrdemServico {
        val ordem = ordemDao.buscarPorId(ordemId)
            ?: throw RegraDeNegocioException("OS nao encontrada.")
        if (ordem.status != StatusOrdem.ABERTA) {
            throw RegraDeNegocioException("So da pra lancar material em OS aberta.")
        }
        val produto = produtoDao.buscarPorId(produtoId)
            ?: throw RegraDeNegocioException("Produto nao encontrado.")

        return Transacao.executar {
            servicoEstoque.baixarEstoque(produto, quantidade)
            val item = ItemOrdemServico(
                ordemId = ordem.id,
                produtoId = produto.id,
                produtoDescricao = produto.descricao,
                quantidade = quantidade
            )
            val id = ordemDao.inserirItem(item)
            item.copy(id = id)
        }
    }

    /**
     * Encerra a OS. Se tiver valor de mao de obra e o cliente pagar na hora,
     * entra no caixa junto - senao a OS so e fechada.
     */
    fun encerrar(ordemId: Int, receberAgora: Boolean, responsavel: Funcionario): OrdemServico {
        val ordem = ordemDao.buscarPorId(ordemId)
            ?: throw RegraDeNegocioException("OS nao encontrada.")
        if (ordem.status != StatusOrdem.ABERTA) {
            throw RegraDeNegocioException("Essa OS ja esta ${ordem.status.name.lowercase()}.")
        }

        return Transacao.executar {
            val agora = LocalDateTime.now()
            if (!ordemDao.encerrar(ordem.id, agora)) {
                throw RegraDeNegocioException("Nao consegui encerrar a OS.")
            }
            if (receberAgora && ordem.valor > BigDecimal.ZERO) {
                Caixa.registrarEntrada(
                    valor = ordem.valor,
                    categoria = "SERVICO_TECNICO",
                    pagador = ordem.clienteNome,
                    recebedor = Empresa.NOME,
                    descricao = "OS ${ordem.id} - ${ordem.tipo.name.lowercase()}",
                    responsavel = responsavel
                )
            }
            ordem.copy(status = StatusOrdem.ENCERRADA, encerramento = agora)
        }
    }

    /**
     * Cancela a OS e devolve pro estoque o material que tinha sido lancado.
     */
    fun cancelar(ordemId: Int): OrdemServico {
        val ordem = ordemDao.buscarPorId(ordemId)
            ?: throw RegraDeNegocioException("OS nao encontrada.")
        if (ordem.status != StatusOrdem.ABERTA) {
            throw RegraDeNegocioException("So da pra cancelar OS que esta aberta.")
        }

        return Transacao.executar {
            ordemDao.listarItens(ordem.id).forEach { item ->
                produtoDao.movimentarEstoque(item.produtoId, item.quantidade)
            }
            if (!ordemDao.cancelar(ordem.id)) {
                throw RegraDeNegocioException("Nao consegui cancelar a OS.")
            }
            ordem.copy(status = StatusOrdem.CANCELADA)
        }
    }
}
