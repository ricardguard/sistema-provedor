package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.PagamentoSalario
import java.sql.ResultSet
import java.sql.Statement

/**
 * Registro da folha. Existe pra nao pagar o mesmo salario duas vezes na
 * mesma competencia - a UNIQUE (funcionario_id, competencia) segura isso
 * no banco mesmo se a checagem do Kotlin falhar.
 */
class PagamentoSalarioDao {

    fun inserir(pagamento: PagamentoSalario): Int {
        val sql = """
            INSERT INTO pagamento_salario (funcionario_id, competencia, valor, responsavel_id)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, pagamento.funcionarioId)
            ps.setString(2, pagamento.competencia)
            ps.setBigDecimal(3, pagamento.valor)
            ps.setInt(4, pagamento.responsavelId)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun jaPago(funcionarioId: Int, competencia: String): Boolean {
        val sql = "SELECT 1 FROM pagamento_salario WHERE funcionario_id = ? AND competencia = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, funcionarioId)
            ps.setString(2, competencia)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    fun listarPorCompetencia(competencia: String): List<PagamentoSalario> {
        val lista = mutableListOf<PagamentoSalario>()
        val sql = """
            SELECT ps.id, ps.funcionario_id, f.nome AS funcionario_nome, ps.competencia,
                   ps.valor, ps.responsavel_id, ps.data_pagamento
              FROM pagamento_salario ps
              JOIN funcionario f ON f.id = ps.funcionario_id
             WHERE ps.competencia = ?
             ORDER BY f.nome
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, competencia)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    private fun montar(rs: ResultSet) = PagamentoSalario(
        id = rs.getInt("id"),
        funcionarioId = rs.getInt("funcionario_id"),
        funcionarioNome = rs.getString("funcionario_nome"),
        competencia = rs.getString("competencia"),
        valor = rs.getBigDecimal("valor"),
        responsavelId = rs.getInt("responsavel_id"),
        dataPagamento = rs.getTimestamp("data_pagamento").toLocalDateTime()
    )
}
