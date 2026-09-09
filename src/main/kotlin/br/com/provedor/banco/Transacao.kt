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

        // Guarda se o rollback deu certo. Isso importa por causa de uma
        // pegadinha do JDBC: voltar o autoCommit pra true FAZ COMMIT do que
        // estiver pendente. Se o rollback falhou e eu simplesmente religasse
        // o autoCommit, gravaria justamente o que era pra desfazer.
        var desfezDireito = true

        try {
            val resultado = bloco()
            conexao.commit()
            return resultado
        } catch (erro: Exception) {
            try {
                conexao.rollback()
            } catch (erroRollback: SQLException) {
                desfezDireito = false
                println("Aviso: falha ao desfazer a transacao (${erroRollback.message})")
            }
            throw erro
        } finally {
            Conexao.transacaoAberta = false
            if (desfezDireito) {
                try {
                    conexao.autoCommit = true
                } catch (_: SQLException) {
                    // Nao consegui religar o autoCommit. Se deixasse assim, a
                    // conexao ficaria em modo manual pra sempre e nada mais
                    // seria gravado - sem ninguem perceber. Melhor descartar.
                    Conexao.fechar()
                }
            } else {
                // Rollback falhou: essa conexao nao e mais confiavel.
                Conexao.fechar()
            }
        }
    }
}
