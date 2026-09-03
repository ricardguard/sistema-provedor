package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Cliente
import java.sql.ResultSet
import java.sql.Statement

class ClienteDao {

    private val selectBase = """
        SELECT id, nome, cpf_cnpj, email, telefone, endereco, cidade, data_cadastro, ativo
          FROM cliente
    """.trimIndent()

    fun inserir(cliente: Cliente): Int {
        val sql = """
            INSERT INTO cliente (nome, cpf_cnpj, email, telefone, endereco, cidade)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, cliente.nome)
            ps.setString(2, cliente.cpfCnpj)
            ps.setString(3, cliente.email)
            ps.setString(4, cliente.telefone)
            ps.setString(5, cliente.endereco)
            ps.setString(6, cliente.cidade)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(somenteAtivos: Boolean = false): List<Cliente> {
        val sql = selectBase + if (somenteAtivos) " WHERE ativo = TRUE ORDER BY nome" else " ORDER BY nome"
        val lista = mutableListOf<Cliente>()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    /** Busca por parte do nome ou pelo documento, do jeito que o atendente tem em maos. */
    fun buscarPorNomeOuDocumento(termo: String): List<Cliente> {
        val lista = mutableListOf<Cliente>()
        val sql = "$selectBase WHERE UPPER(nome) LIKE UPPER(?) OR cpf_cnpj LIKE ? ORDER BY nome"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, "%$termo%")
            ps.setString(2, "%$termo%")
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Cliente? {
        Conexao.get().prepareStatement("$selectBase WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun existeDocumento(documento: String): Boolean {
        Conexao.get().prepareStatement("SELECT 1 FROM cliente WHERE cpf_cnpj = ?").use { ps ->
            ps.setString(1, documento)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    fun atualizar(cliente: Cliente): Boolean {
        val sql = """
            UPDATE cliente
               SET nome = ?, email = ?, telefone = ?, endereco = ?, cidade = ?
             WHERE id = ?
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, cliente.nome)
            ps.setString(2, cliente.email)
            ps.setString(3, cliente.telefone)
            ps.setString(4, cliente.endereco)
            ps.setString(5, cliente.cidade)
            ps.setInt(6, cliente.id)
            return ps.executeUpdate() > 0
        }
    }

    fun alterarSituacao(id: Int, ativo: Boolean): Boolean {
        Conexao.get().prepareStatement("UPDATE cliente SET ativo = ? WHERE id = ?").use { ps ->
            ps.setBoolean(1, ativo)
            ps.setInt(2, id)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet) = Cliente(
        id = rs.getInt("id"),
        nome = rs.getString("nome"),
        cpfCnpj = rs.getString("cpf_cnpj"),
        email = rs.getString("email"),
        telefone = rs.getString("telefone"),
        endereco = rs.getString("endereco"),
        cidade = rs.getString("cidade"),
        dataCadastro = rs.getTimestamp("data_cadastro").toLocalDateTime(),
        ativo = rs.getBoolean("ativo")
    )
}
