package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import java.math.BigDecimal

/**
 * A tabela caixa tem uma linha so (id = 1) com o saldo atual.
 * Esse DAO e usado somente pela classe Caixa, que controla a transacao.
 */
class CaixaDao {

    fun lerSaldo(): BigDecimal {
        Conexao.get().prepareStatement("SELECT saldo FROM caixa WHERE id = 1").use { ps ->
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getBigDecimal("saldo") else BigDecimal.ZERO
            }
        }
    }

    /**
     * SELECT ... FOR UPDATE trava a linha do caixa ate o commit.
     * Assim duas operacoes nao leem o mesmo saldo e gravam valores errados.
     */
    fun lerSaldoParaAtualizar(): BigDecimal {
        Conexao.get().prepareStatement("SELECT saldo FROM caixa WHERE id = 1 FOR UPDATE").use { ps ->
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw IllegalStateException("Registro do caixa nao encontrado.")
                return rs.getBigDecimal("saldo")
            }
        }
    }

    fun gravarSaldo(novoSaldo: BigDecimal) {
        val sql = "UPDATE caixa SET saldo = ?, atualizado_em = NOW() WHERE id = 1"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setBigDecimal(1, novoSaldo)
            ps.executeUpdate()
        }
    }
}
