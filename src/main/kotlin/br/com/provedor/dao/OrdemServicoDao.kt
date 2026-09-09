package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.ItemOrdemServico
import br.com.provedor.modelo.OrdemServico
import br.com.provedor.modelo.StatusOrdem
import br.com.provedor.modelo.TipoOrdem
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.time.LocalDateTime

class OrdemServicoDao {

    private val selectBase = """
        SELECT o.id, o.cliente_id, cl.nome AS cliente_nome, o.tecnico_id, f.nome AS tecnico_nome,
               o.tipo, o.descricao, o.valor, o.abertura, o.encerramento, o.status
          FROM ordem_servico o
          JOIN cliente cl ON cl.id = o.cliente_id
          LEFT JOIN funcionario f ON f.id = o.tecnico_id
    """.trimIndent()

    fun inserir(ordem: OrdemServico): Int {
        val sql = """
            INSERT INTO ordem_servico (cliente_id, tecnico_id, tipo, descricao, valor, status)
            VALUES (?, ?, ?, ?, ?, 'ABERTA')
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, ordem.clienteId)
            if (ordem.tecnicoId == null) ps.setNull(2, Types.INTEGER) else ps.setInt(2, ordem.tecnicoId)
            ps.setString(3, ordem.tipo.name)
            ps.setString(4, ordem.descricao)
            ps.setBigDecimal(5, ordem.valor)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(apenasAbertas: Boolean = false): List<OrdemServico> {
        val sql = selectBase + if (apenasAbertas) " WHERE o.status = 'ABERTA' ORDER BY o.abertura"
                               else " ORDER BY o.id DESC"
        val lista = mutableListOf<OrdemServico>()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarPorTecnico(tecnicoId: Int): List<OrdemServico> {
        val lista = mutableListOf<OrdemServico>()
        Conexao.get().prepareStatement("$selectBase WHERE o.tecnico_id = ? ORDER BY o.id DESC").use { ps ->
            ps.setInt(1, tecnicoId)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): OrdemServico? {
        Conexao.get().prepareStatement("$selectBase WHERE o.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    /**
     * Define o tecnico e o valor da OS. Precisa existir porque a OS de
     * instalacao e aberta automaticamente quando o contrato e fechado, sem
     * tecnico e com valor zero - sem esta tela ela ficava assim pra sempre.
     */
    fun atualizarTecnicoEValor(id: Int, tecnicoId: Int?, valor: java.math.BigDecimal): Boolean {
        val sql = """
            UPDATE ordem_servico SET tecnico_id = ?, valor = ?
             WHERE id = ? AND status = 'ABERTA'
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            if (tecnicoId == null) ps.setNull(1, Types.INTEGER) else ps.setInt(1, tecnicoId)
            ps.setBigDecimal(2, valor)
            ps.setInt(3, id)
            return ps.executeUpdate() > 0
        }
    }

    fun encerrar(id: Int, quando: LocalDateTime): Boolean {
        val sql = """
            UPDATE ordem_servico SET status = 'ENCERRADA', encerramento = ?
             WHERE id = ? AND status = 'ABERTA'
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(quando))
            ps.setInt(2, id)
            return ps.executeUpdate() > 0
        }
    }

    fun cancelar(id: Int): Boolean {
        val sql = "UPDATE ordem_servico SET status = 'CANCELADA' WHERE id = ? AND status = 'ABERTA'"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, id)
            return ps.executeUpdate() > 0
        }
    }

    // ----- material usado na OS -----

    fun inserirItem(item: ItemOrdemServico): Int {
        val sql = "INSERT INTO item_ordem_servico (ordem_id, produto_id, quantidade) VALUES (?, ?, ?)"
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, item.ordemId)
            ps.setInt(2, item.produtoId)
            ps.setInt(3, item.quantidade)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listarItens(ordemId: Int): List<ItemOrdemServico> {
        val lista = mutableListOf<ItemOrdemServico>()
        val sql = """
            SELECT i.id, i.ordem_id, i.produto_id, p.descricao, i.quantidade
              FROM item_ordem_servico i
              JOIN produto p ON p.id = i.produto_id
             WHERE i.ordem_id = ?
             ORDER BY i.id
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, ordemId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    lista.add(
                        ItemOrdemServico(
                            id = rs.getInt("id"),
                            ordemId = rs.getInt("ordem_id"),
                            produtoId = rs.getInt("produto_id"),
                            produtoDescricao = rs.getString("descricao"),
                            quantidade = rs.getInt("quantidade")
                        )
                    )
                }
            }
        }
        return lista
    }

    private fun montar(rs: ResultSet): OrdemServico {
        val tecnico = rs.getInt("tecnico_id")
        val tecnicoId: Int? = if (rs.wasNull()) null else tecnico
        return OrdemServico(
            id = rs.getInt("id"),
            clienteId = rs.getInt("cliente_id"),
            clienteNome = rs.getString("cliente_nome"),
            tecnicoId = tecnicoId,
            tecnicoNome = rs.getString("tecnico_nome"),
            tipo = TipoOrdem.valueOf(rs.getString("tipo")),
            descricao = rs.getString("descricao"),
            valor = rs.getBigDecimal("valor"),
            abertura = rs.getTimestamp("abertura").toLocalDateTime(),
            encerramento = rs.getTimestamp("encerramento")?.toLocalDateTime(),
            status = StatusOrdem.valueOf(rs.getString("status"))
        )
    }
}
