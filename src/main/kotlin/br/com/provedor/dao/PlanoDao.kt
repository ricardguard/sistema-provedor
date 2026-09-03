package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Plano
import java.sql.ResultSet
import java.sql.Statement

class PlanoDao {

    private val selectBase =
        "SELECT id, nome, velocidade_mega, valor_mensal, taxa_instalacao, ativo FROM plano"

    fun inserir(plano: Plano): Int {
        val sql = """
            INSERT INTO plano (nome, velocidade_mega, valor_mensal, taxa_instalacao)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, plano.nome)
            ps.setInt(2, plano.velocidadeMega)
            ps.setBigDecimal(3, plano.valorMensal)
            ps.setBigDecimal(4, plano.taxaInstalacao)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(somenteAtivos: Boolean = false): List<Plano> {
        val sql = selectBase + if (somenteAtivos) " WHERE ativo = TRUE ORDER BY velocidade_mega"
                               else " ORDER BY velocidade_mega"
        val lista = mutableListOf<Plano>()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Plano? {
        Conexao.get().prepareStatement("$selectBase WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun atualizar(plano: Plano): Boolean {
        val sql = """
            UPDATE plano SET nome = ?, velocidade_mega = ?, valor_mensal = ?, taxa_instalacao = ?
             WHERE id = ?
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, plano.nome)
            ps.setInt(2, plano.velocidadeMega)
            ps.setBigDecimal(3, plano.valorMensal)
            ps.setBigDecimal(4, plano.taxaInstalacao)
            ps.setInt(5, plano.id)
            return ps.executeUpdate() > 0
        }
    }

    fun alterarSituacao(id: Int, ativo: Boolean): Boolean {
        Conexao.get().prepareStatement("UPDATE plano SET ativo = ? WHERE id = ?").use { ps ->
            ps.setBoolean(1, ativo)
            ps.setInt(2, id)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet) = Plano(
        id = rs.getInt("id"),
        nome = rs.getString("nome"),
        velocidadeMega = rs.getInt("velocidade_mega"),
        valorMensal = rs.getBigDecimal("valor_mensal"),
        taxaInstalacao = rs.getBigDecimal("taxa_instalacao"),
        ativo = rs.getBoolean("ativo")
    )
}
