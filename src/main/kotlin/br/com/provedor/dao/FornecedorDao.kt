package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Fornecedor
import java.sql.ResultSet
import java.sql.Statement

class FornecedorDao {

    private val selectBase = "SELECT id, razao_social, cnpj, email, telefone, ativo FROM fornecedor"

    fun inserir(fornecedor: Fornecedor): Int {
        val sql = "INSERT INTO fornecedor (razao_social, cnpj, email, telefone) VALUES (?, ?, ?, ?)"
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, fornecedor.razaoSocial)
            ps.setString(2, fornecedor.cnpj)
            ps.setString(3, fornecedor.email)
            ps.setString(4, fornecedor.telefone)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(somenteAtivos: Boolean = false): List<Fornecedor> {
        val sql = selectBase + if (somenteAtivos) " WHERE ativo = TRUE ORDER BY razao_social"
                               else " ORDER BY razao_social"
        val lista = mutableListOf<Fornecedor>()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Fornecedor? {
        Conexao.get().prepareStatement("$selectBase WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun existeCnpj(cnpj: String): Boolean {
        Conexao.get().prepareStatement("SELECT 1 FROM fornecedor WHERE cnpj = ?").use { ps ->
            ps.setString(1, cnpj)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    fun atualizar(fornecedor: Fornecedor): Boolean {
        val sql = "UPDATE fornecedor SET razao_social = ?, email = ?, telefone = ? WHERE id = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, fornecedor.razaoSocial)
            ps.setString(2, fornecedor.email)
            ps.setString(3, fornecedor.telefone)
            ps.setInt(4, fornecedor.id)
            return ps.executeUpdate() > 0
        }
    }

    fun alterarSituacao(id: Int, ativo: Boolean): Boolean {
        Conexao.get().prepareStatement("UPDATE fornecedor SET ativo = ? WHERE id = ?").use { ps ->
            ps.setBoolean(1, ativo)
            ps.setInt(2, id)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet) = Fornecedor(
        id = rs.getInt("id"),
        razaoSocial = rs.getString("razao_social"),
        cnpj = rs.getString("cnpj"),
        email = rs.getString("email"),
        telefone = rs.getString("telefone"),
        ativo = rs.getBoolean("ativo")
    )
}
