package br.com.provedor.banco

import java.sql.SQLException

/**
 * Controle de transacao.
 *
 * Operacoes como "comprar do fornecedor" mexem em varias tabelas (compra,
 * estoque e caixa). Ou grava tudo ou nao grava nada.
 *
 * Se ja existe uma transacao aberta na conexao, o bloco entra nela em vez de
 * abrir outra - assim o Caixa pode ser chamado de dentro de um servico maior
 * sem dar commit no meio do caminho.
 */
object Transacao {

    fun <T> executar(bloco: () -> T): T {
        val conexao = Conexao.get()

        if (!conexao.autoCommit) {
            // ja tem transacao rolando, entao so participa dela
            return bloco()
        }

        conexao.autoCommit = false
        Conexao.transacaoAberta = true
        try {
            val resultado = bloco()
            conexao.commit()
            return resultado
        } catch (erro: Exception) {
            try {
                conexao.rollback()
            } catch (erroRollback: SQLException) {
                println("Aviso: falha ao desfazer a transacao (${erroRollback.message})")
            }
            throw erro
        } finally {
            Conexao.transacaoAberta = false
            try {
                conexao.autoCommit = true
            } catch (e: SQLException) {
                // conexao ja morreu; a proxima chamada abre outra
            }
        }
    }
}
