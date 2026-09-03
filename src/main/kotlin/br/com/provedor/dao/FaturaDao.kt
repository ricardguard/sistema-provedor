package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Fatura
import br.com.provedor.modelo.StatusFatura
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDateTime

class FaturaDao {

    private val selectBase = """
        SELECT f.id, f.contrato_id, cl.nome AS cliente_nome, f.competencia, f.valor,
               f.vencimento, f.status, f.data_pagamento
          FROM fatura f
          JOIN contrato c ON c.id = f.contrato_id
          JOIN cliente cl ON cl.id = c.cliente_id
    """.trimIndent()

    fun inserir(fatura: Fatura): Int {
        val sql = """
            INSERT INTO fatura (contrato_id, competencia, valor, vencimento, status)
            VALUES (?, ?, ?, ?, 'ABERTA')
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, fatura.contratoId)
            ps.setString(2, fatura.competencia)
            ps.setBigDecimal(3, fatura.valor)
            ps.setDate(4, java.sql.Date.valueOf(fatura.vencimento))
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listarEmAberto(): List<Fatura> {
        val lista = mutableListOf<Fatura>()
        val sql = "$selectBase WHERE f.status = 'ABERTA' ORDER BY f.vencimento"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarPorContrato(contratoId: Int): List<Fatura> {
        val lista = mutableListOf<Fatura>()
        Conexao.get().prepareStatement("$selectBase WHERE f.contrato_id = ? ORDER BY f.vencimento").use { ps ->
            ps.setInt(1, contratoId)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Fatura? {
        Conexao.get().prepareStatement("$selectBase WHERE f.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun existeCompetencia(contratoId: Int, competencia: String): Boolean {
        val sql = "SELECT 1 FROM fatura WHERE contrato_id = ? AND competencia = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, contratoId)
            ps.setString(2, competencia)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    fun marcarComoPaga(id: Int, quando: LocalDateTime): Boolean {
        val sql = "UPDATE fatura SET status = 'PAGA', data_pagamento = ? WHERE id = ? AND status = 'ABERTA'"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(quando))
            ps.setInt(2, id)
            return ps.executeUpdate() > 0
        }
    }

    fun cancelar(id: Int): Boolean {
        val sql = "UPDATE fatura SET status = 'CANCELADA' WHERE id = ? AND status = 'ABERTA'"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, id)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet) = Fatura(
        id = rs.getInt("id"),
        contratoId = rs.getInt("contrato_id"),
        clienteNome = rs.getString("cliente_nome"),
        competencia = rs.getString("competencia"),
        valor = rs.getBigDecimal("valor"),
        vencimento = rs.getDate("vencimento").toLocalDate(),
        status = StatusFatura.valueOf(rs.getString("status")),
        dataPagamento = rs.getTimestamp("data_pagamento")?.toLocalDateTime()
    )
}
