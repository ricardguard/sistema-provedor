package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.AjusteEstoque
import java.sql.ResultSet
import java.sql.Statement

class AjusteEstoqueDao {

    fun inserir(ajuste: AjusteEstoque): Int {
        val sql = """
            INSERT INTO ajuste_estoque (produto_id, quantidade_anterior, quantidade_nova, motivo, responsavel_id)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, ajuste.produtoId)
            ps.setInt(2, ajuste.quantidadeAnterior)
            ps.setInt(3, ajuste.quantidadeNova)
            ps.setString(4, ajuste.motivo)
            ps.setInt(5, ajuste.responsavelId)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listarUltimos(limite: Int = 20): List<AjusteEstoque> {
        val lista = mutableListOf<AjusteEstoque>()
        val sql = """
            SELECT a.id, a.produto_id, p.descricao, a.quantidade_anterior, a.quantidade_nova,
                   a.motivo, a.responsavel_id, f.nome AS responsavel_nome, a.data_ajuste
              FROM ajuste_estoque a
              JOIN produto p ON p.id = a.produto_id
              JOIN funcionario f ON f.id = a.responsavel_id
             ORDER BY a.data_ajuste DESC
             LIMIT ?
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, limite)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    private fun montar(rs: ResultSet) = AjusteEstoque(
        id = rs.getInt("id"),
        produtoId = rs.getInt("produto_id"),
        produtoDescricao = rs.getString("descricao"),
        quantidadeAnterior = rs.getInt("quantidade_anterior"),
        quantidadeNova = rs.getInt("quantidade_nova"),
        motivo = rs.getString("motivo"),
        responsavelId = rs.getInt("responsavel_id"),
        responsavelNome = rs.getString("responsavel_nome"),
        dataAjuste = rs.getTimestamp("data_ajuste").toLocalDateTime()
    )
}
