package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Movimentacao
import br.com.provedor.modelo.TipoMovimentacao
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate

class MovimentacaoDao {

    private val selectBase = """
        SELECT m.id, m.tipo, m.categoria, m.valor, m.pagador, m.recebedor, m.data_hora,
               m.descricao, m.responsavel_id, f.nome AS responsavel_nome, m.saldo_apos
          FROM movimentacao_financeira m
          JOIN funcionario f ON f.id = m.responsavel_id
    """.trimIndent()

    // Este DAO so LE. Quem grava movimentacao e o objeto Caixa, que faz isso
    // por funcao privada dele - assim nao existe caminho paralelo pro dinheiro.

    fun listarUltimas(limite: Int = 20): List<Movimentacao> {
        val lista = mutableListOf<Movimentacao>()
        Conexao.get().prepareStatement("$selectBase ORDER BY m.data_hora DESC, m.id DESC LIMIT ?").use { ps ->
            ps.setInt(1, limite)
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarPorPeriodo(inicio: LocalDate, fim: LocalDate): List<Movimentacao> {
        val lista = mutableListOf<Movimentacao>()
        val sql = "$selectBase WHERE m.data_hora >= ? AND m.data_hora < ? ORDER BY m.data_hora"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(inicio.atStartOfDay()))
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(fim.plusDays(1).atStartOfDay()))
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    /** Soma por tipo dentro do periodo - uso no relatorio do caixa. */
    fun totalPorTipo(tipo: TipoMovimentacao, inicio: LocalDate, fim: LocalDate): BigDecimal {
        val sql = """
            SELECT COALESCE(SUM(valor), 0) FROM movimentacao_financeira
             WHERE tipo = ? AND data_hora >= ? AND data_hora < ?
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, tipo.name)
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(inicio.atStartOfDay()))
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(fim.plusDays(1).atStartOfDay()))
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getBigDecimal(1) else BigDecimal.ZERO }
        }
    }

    fun totaisPorCategoria(inicio: LocalDate, fim: LocalDate): List<Triple<String, String, BigDecimal>> {
        val lista = mutableListOf<Triple<String, String, BigDecimal>>()
        val sql = """
            SELECT tipo, categoria, SUM(valor) AS total
              FROM movimentacao_financeira
             WHERE data_hora >= ? AND data_hora < ?
             GROUP BY tipo, categoria
             ORDER BY tipo, total DESC
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(inicio.atStartOfDay()))
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(fim.plusDays(1).atStartOfDay()))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    lista.add(Triple(rs.getString("tipo"), rs.getString("categoria"), rs.getBigDecimal("total")))
                }
            }
        }
        return lista
    }

    private fun montar(rs: ResultSet) = Movimentacao(
        id = rs.getInt("id"),
        tipo = TipoMovimentacao.valueOf(rs.getString("tipo")),
        categoria = rs.getString("categoria"),
        valor = rs.getBigDecimal("valor"),
        pagador = rs.getString("pagador"),
        recebedor = rs.getString("recebedor"),
        dataHora = rs.getTimestamp("data_hora").toLocalDateTime(),
        descricao = rs.getString("descricao"),
        responsavelId = rs.getInt("responsavel_id"),
        responsavelNome = rs.getString("responsavel_nome"),
        saldoApos = rs.getBigDecimal("saldo_apos")
    )
}
