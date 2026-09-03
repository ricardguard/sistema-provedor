package br.com.provedor.menu

import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.servico.Caixa
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.util.Entrada
import br.com.provedor.util.EntradaEncerradaException
import br.com.provedor.util.Formato
import java.sql.SQLException

/**
 * Guarda quem esta operando o sistema no momento.
 * Esse funcionario e gravado como responsavel em toda movimentacao do caixa,
 * que e um dos itens pedidos no enunciado.
 */
object Sessao {

    private var operador: Funcionario? = null

    val logado: Funcionario
        get() = operador ?: throw RegraDeNegocioException("Nenhum operador identificado na sessao.")

    fun identificar() {
        val dao = FuncionarioDao()
        val ativos = dao.listar(somenteAtivos = true)
        if (ativos.isEmpty()) {
            throw RegraDeNegocioException("Nao existe funcionario ativo pra operar o sistema.")
        }

        Formato.titulo("Identificacao do operador")
        ativos.forEach { f ->
            println("  [${f.id}] ${Formato.encurtar(f.nome, 28)} ${Formato.encurtar(f.cargo, 20)} ${f.setorNome}")
        }
        println(Formato.linha())

        while (true) {
            val id = Entrada.inteiro("Codigo do funcionario: ", 1)
            val escolhido = ativos.firstOrNull { it.id == id }
            if (escolhido == null) {
                println("  > Codigo nao esta na lista.")
            } else {
                operador = escolhido
                println("\nBem-vindo(a), ${escolhido.nome} - ${escolhido.setorNome}.")
                return
            }
        }
    }
}

/**
 * Envelopa as acoes do menu. Assim um erro de digitacao ou de banco nao
 * derruba o programa inteiro: mostra a mensagem e volta pro menu.
 */
fun protegido(acao: () -> Unit) {
    try {
        acao()
    } catch (e: EntradaEncerradaException) {
        throw e                       // esse aqui precisa subir pra encerrar o sistema
    } catch (e: RegraDeNegocioException) {
        println("\n  [!] ${e.message}")
    } catch (e: SQLException) {
        println("\n  [!] Problema no banco de dados: ${e.message}")
    } catch (e: Exception) {
        println("\n  [!] Nao consegui concluir a operacao: ${e.message}")
    } finally {
        try {
            Caixa.atualizarDoBanco()
        } catch (e: SQLException) {
            // se o banco caiu, o erro ja apareceu acima
        }
    }
    Entrada.pausar()
}
