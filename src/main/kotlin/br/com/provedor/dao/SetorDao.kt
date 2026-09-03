package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Setor
import java.sql.ResultSet
import java.sql.Statement

class SetorDao {

    fun inserir(setor: Setor): Int {
        val sql = "INSERT INTO setor (nome, descricao) VALUES (?, ?)"
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, setor.nome)
            ps.setString(2, setor.descricao)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(): List<Setor> {
        val lista = mutableListOf<Setor>()
        val sql = "SELECT id, nome, descricao FROM setor ORDER BY nome"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) lista.add(montar(rs))
            }
        }
        return lista
    }

    fun buscarPorId(id: Int): Setor? {
        val sql = "SELECT id, nome, descricao FROM setor WHERE id = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                return if (rs.next()) montar(rs) else null
            }
        }
    }

    fun atualizar(setor: Setor): Boolean {
        val sql = "UPDATE setor SET nome = ?, descricao = ? WHERE id = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, setor.nome)
            ps.setString(2, setor.descricao)
            ps.setInt(3, setor.id)
            return ps.executeUpdate() > 0
        }
    }

    /** Quantos funcionarios estao lotados no setor - uso pra nao deixar excluir setor cheio. */
    fun contarFuncionarios(setorId: Int): Int {
        val sql = "SELECT COUNT(*) FROM funcionario WHERE setor_id = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, setorId)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun excluir(id: Int): Boolean {
        Conexao.get().prepareStatement("DELETE FROM setor WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet) = Setor(
        id = rs.getInt("id"),
        nome = rs.getString("nome"),
        descricao = rs.getString("descricao")
    )
}
