package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Compra
import java.sql.ResultSet
import java.sql.Statement

class CompraDao {

    private val selectBase = """
        SELECT c.id, c.fornecedor_id, fo.razao_social, c.produto_id, p.descricao,
               c.quantidade, c.valor_unitario, c.responsavel_id, c.data_compra
          FROM compra c
          JOIN fornecedor fo ON fo.id = c.fornecedor_id
          JOIN produto p ON p.id = c.produto_id
    """.trimIndent()

    fun inserir(compra: Compra): Int {
        val sql = """
            INSERT INTO compra (fornecedor_id, produto_id, quantidade, valor_unitario, responsavel_id)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, compra.fornecedorId)
            ps.setInt(2, compra.produtoId)
            ps.setInt(3, compra.quantidade)
            ps.setBigDecimal(4, compra.valorUnitario)
            ps.setInt(5, compra.responsavelId)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listarUltimas(limite: Int = 20): List<Compra> {
        val lista = mutableListOf<Compra>()
        Conexao.get().prepareStatement("$selectBase ORDER BY c.data_compra DESC LIMIT ?").use { ps ->
            ps.setInt(1, limite)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    private fun montar(rs: ResultSet) = Compra(
        id = rs.getInt("id"),
        fornecedorId = rs.getInt("fornecedor_id"),
        fornecedorNome = rs.getString("razao_social"),
        produtoId = rs.getInt("produto_id"),
        produtoDescricao = rs.getString("descricao"),
        quantidade = rs.getInt("quantidade"),
        valorUnitario = rs.getBigDecimal("valor_unitario"),
        responsavelId = rs.getInt("responsavel_id"),
        dataCompra = rs.getTimestamp("data_compra").toLocalDateTime()
    )
}
