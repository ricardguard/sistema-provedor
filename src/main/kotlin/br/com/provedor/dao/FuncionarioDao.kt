package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Contratacao
import br.com.provedor.modelo.Funcionario
import java.sql.ResultSet
import java.sql.Statement

class FuncionarioDao {

    private val selectBase = """
        SELECT f.id, f.nome, f.cpf, f.email, f.telefone, f.cargo, f.salario,
               f.setor_id, s.nome AS setor_nome, f.contratacao, f.data_admissao, f.ativo
          FROM funcionario f
          JOIN setor s ON s.id = f.setor_id
    """.trimIndent()

    fun inserir(func: Funcionario): Int {
        val sql = """
            INSERT INTO funcionario (nome, cpf, email, telefone, cargo, salario, setor_id,
                                     contratacao, data_admissao, ativo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, func.nome)
            ps.setString(2, func.cpf)
            ps.setString(3, func.email)          // aceita null, o campo e opcional
            ps.setString(4, func.telefone)
            ps.setString(5, func.cargo)
            ps.setBigDecimal(6, func.salario)
            ps.setInt(7, func.setorId)
            ps.setString(8, Contratacao.codigoDe(func.contratacao))
            ps.setDate(9, java.sql.Date.valueOf(func.dataAdmissao))
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(somenteAtivos: Boolean = false): List<Funcionario> {
        val sql = selectBase + if (somenteAtivos) " WHERE f.ativo = TRUE ORDER BY f.nome" else " ORDER BY f.nome"
        val lista = mutableListOf<Funcionario>()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarPorSetor(setorId: Int): List<Funcionario> {
        val lista = mutableListOf<Funcionario>()
        Conexao.get().prepareStatement("$selectBase WHERE f.setor_id = ? ORDER BY f.nome").use { ps ->
            ps.setInt(1, setorId)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Funcionario? {
        Conexao.get().prepareStatement("$selectBase WHERE f.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun existeCpf(cpf: String): Boolean {
        Conexao.get().prepareStatement("SELECT 1 FROM funcionario WHERE cpf = ?").use { ps ->
            ps.setString(1, cpf)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    fun atualizar(func: Funcionario): Boolean {
        val sql = """
            UPDATE funcionario
               SET nome = ?, email = ?, telefone = ?, cargo = ?, salario = ?, setor_id = ?,
                   contratacao = ?
             WHERE id = ?
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, func.nome)
            ps.setString(2, func.email)
            ps.setString(3, func.telefone)
            ps.setString(4, func.cargo)
            ps.setBigDecimal(5, func.salario)
            ps.setInt(6, func.setorId)
            ps.setString(7, Contratacao.codigoDe(func.contratacao))
            ps.setInt(8, func.id)
            return ps.executeUpdate() > 0
        }
    }

    /**
     * Funcionario nao e apagado do banco, so desligado. Se apagasse,
     * as movimentacoes financeiras dele ficariam sem responsavel.
     */
    fun alterarSituacao(id: Int, ativo: Boolean): Boolean {
        Conexao.get().prepareStatement("UPDATE funcionario SET ativo = ? WHERE id = ?").use { ps ->
            ps.setBoolean(1, ativo)
            ps.setInt(2, id)
            return ps.executeUpdate() > 0
        }
    }

    fun contar(): Int {
        Conexao.get().prepareStatement("SELECT COUNT(*) FROM funcionario").use { ps ->
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    private fun montar(rs: ResultSet) = Funcionario(
        id = rs.getInt("id"),
        nome = rs.getString("nome"),
        cpf = rs.getString("cpf"),
        email = rs.getString("email"),
        telefone = rs.getString("telefone"),
        cargo = rs.getString("cargo"),
        salario = rs.getBigDecimal("salario"),
        setorId = rs.getInt("setor_id"),
        setorNome = rs.getString("setor_nome"),
        contratacao = Contratacao.de(rs.getString("contratacao")),
        dataAdmissao = rs.getDate("data_admissao").toLocalDate(),
        ativo = rs.getBoolean("ativo")
    )
}
