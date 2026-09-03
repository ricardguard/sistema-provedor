package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Venda
import java.sql.ResultSet
import java.sql.Statement

class VendaDao {

    private val selectBase = """
        SELECT v.id, v.cliente_id, cl.nome AS cliente_nome, v.produto_id, p.descricao,
               v.quantidade, v.valor_unitario, v.responsavel_id, v.data_venda
          FROM venda v
          JOIN cliente cl ON cl.id = v.cliente_id
          JOIN produto p ON p.id = v.produto_id
    """.trimIndent()

    fun inserir(venda: Venda): Int {
        val sql = """
            INSERT INTO venda (cliente_id, produto_id, quantidade, valor_unitario, responsavel_id)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, venda.clienteId)
            ps.setInt(2, venda.produtoId)
            ps.setInt(3, venda.quantidade)
            ps.setBigDecimal(4, venda.valorUnitario)
            ps.setInt(5, venda.responsavelId)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listarUltimas(limite: Int = 20): List<Venda> {
        val lista = mutableListOf<Venda>()
        Conexao.get().prepareStatement("$selectBase ORDER BY v.data_venda DESC LIMIT ?").use { ps ->
            ps.setInt(1, limite)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    private fun montar(rs: ResultSet) = Venda(
        id = rs.getInt("id"),
        clienteId = rs.getInt("cliente_id"),
        clienteNome = rs.getString("cliente_nome"),
        produtoId = rs.getInt("produto_id"),
        produtoDescricao = rs.getString("descricao"),
        quantidade = rs.getInt("quantidade"),
        valorUnitario = rs.getBigDecimal("valor_unitario"),
        responsavelId = rs.getInt("responsavel_id"),
        dataVenda = rs.getTimestamp("data_venda").toLocalDateTime()
    )
}
