package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Contrato
import br.com.provedor.modelo.StatusContrato
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate

class ContratoDao {

    private val selectBase = """
        SELECT c.id, c.cliente_id, cl.nome AS cliente_nome, c.plano_id, p.nome AS plano_nome,
               c.valor_mensal, c.vendedor_id, c.data_inicio, c.dia_vencimento,
               c.status, c.data_cancelamento
          FROM contrato c
          JOIN cliente cl ON cl.id = c.cliente_id
          JOIN plano p ON p.id = c.plano_id
    """.trimIndent()

    fun inserir(contrato: Contrato): Int {
        val sql = """
            INSERT INTO contrato (cliente_id, plano_id, vendedor_id, valor_mensal,
                                  data_inicio, dia_vencimento, status)
            VALUES (?, ?, ?, ?, ?, ?, 'ATIVO')
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setInt(1, contrato.clienteId)
            ps.setInt(2, contrato.planoId)
            if (contrato.vendedorId == null) ps.setNull(3, Types.INTEGER) else ps.setInt(3, contrato.vendedorId)
            ps.setBigDecimal(4, contrato.valorMensal)
            ps.setDate(5, java.sql.Date.valueOf(contrato.dataInicio))
            ps.setInt(6, contrato.diaVencimento)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(): List<Contrato> {
        val lista = mutableListOf<Contrato>()
        Conexao.get().prepareStatement("$selectBase ORDER BY c.id").use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarAtivos(): List<Contrato> {
        val lista = mutableListOf<Contrato>()
        Conexao.get().prepareStatement("$selectBase WHERE c.status = 'ATIVO' ORDER BY cl.nome").use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarPorCliente(clienteId: Int): List<Contrato> {
        val lista = mutableListOf<Contrato>()
        Conexao.get().prepareStatement("$selectBase WHERE c.cliente_id = ? ORDER BY c.id").use { ps ->
            ps.setInt(1, clienteId)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Contrato? {
        Conexao.get().prepareStatement("$selectBase WHERE c.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun alterarStatus(id: Int, status: StatusContrato, cancelamento: LocalDate? = null): Boolean {
        val sql = "UPDATE contrato SET status = ?, data_cancelamento = ? WHERE id = ?"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, status.name)
            if (cancelamento == null) ps.setNull(2, Types.DATE)
            else ps.setDate(2, java.sql.Date.valueOf(cancelamento))
            ps.setInt(3, id)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet): Contrato {
        val vendedor = rs.getInt("vendedor_id")
        val vendedorId: Int? = if (rs.wasNull()) null else vendedor
        return Contrato(
            id = rs.getInt("id"),
            clienteId = rs.getInt("cliente_id"),
            clienteNome = rs.getString("cliente_nome"),
            planoId = rs.getInt("plano_id"),
            planoNome = rs.getString("plano_nome"),
            valorMensal = rs.getBigDecimal("valor_mensal"),
            vendedorId = vendedorId,
            dataInicio = rs.getDate("data_inicio").toLocalDate(),
            diaVencimento = rs.getInt("dia_vencimento"),
            status = StatusContrato.valueOf(rs.getString("status")),
            dataCancelamento = rs.getDate("data_cancelamento")?.toLocalDate()
        )
    }
}
