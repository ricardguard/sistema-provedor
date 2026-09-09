package br.com.provedor

import br.com.provedor.banco.Conexao
import br.com.provedor.banco.Migracao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.SetorDao
import br.com.provedor.menu.MenuPrincipal
import br.com.provedor.menu.Sessao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.servico.Caixa
import br.com.provedor.util.Empresa
import br.com.provedor.util.Entrada
import br.com.provedor.util.EntradaEncerradaException
import br.com.provedor.util.Formato
import java.math.BigDecimal

/**
 * Sistema de gestao de um provedor de internet.
 * Trabalho da disciplina - Kotlin + PostgreSQL, tudo via menu de console.
 */
fun main() {
    println()
    println(Formato.linha())
    println("  ${Empresa.NOME}")
    println("  Sistema de gestao - versao 1.0")
    println(Formato.linha())

    try {
        print("\nConectando no banco... ")
        Migracao.criarEstrutura()
        Caixa.atualizarDoBanco()
        println("ok.")

        primeiroAcesso()
        Sessao.identificar()
        MenuPrincipal.exibir()

    } catch (fim: EntradaEncerradaException) {
        println("\n\nEntrada encerrada, fechando o sistema.")
    } catch (e: Exception) {
        println("\n[ERRO] ${e.message}")
        println("Confere se o PostgreSQL esta rodando e se os dados do banco.properties estao certos.")
    } finally {
        Conexao.fechar()
    }
}

/**
 * Na primeira execucao o banco esta vazio e nao tem ninguem pra logar,
 * entao cadastro aqui o primeiro funcionario (o dono/administrador).
 */
private fun primeiroAcesso() {
    val funcionarioDao = FuncionarioDao()
    if (funcionarioDao.contar() > 0) return

    val setorDao = SetorDao()
    val setores = setorDao.listar()
    if (setores.isEmpty()) {
        // O schema.sql ja cria quatro setores. Se chegou aqui vazio e porque
        // alguem apagou tudo direto no banco - melhor avisar do que ficar
        // pedindo um codigo que nunca vai existir.
        throw IllegalStateException(
            "Nao ha nenhum setor cadastrado. Rode o schema.sql de novo ou insira um setor no banco."
        )
    }

    Formato.titulo("Primeiro acesso")
    println("  O banco esta vazio. Vamos cadastrar o primeiro funcionario,")
    println("  que vai ser o responsavel pelas primeiras operacoes do caixa.")
    println()

    val nome = Entrada.nome("Nome completo: ")
    val cpf = Entrada.cpf("CPF: ")
    val email = Entrada.emailOpcional("E-mail")
    val cargo = Entrada.texto("Cargo: ", 60, 3)
    val salario = Entrada.decimal("Salario: ", BigDecimal("1.00"))

    println("\n  Setores ja criados:")
    setores.forEach { println("   [${it.id}] ${it.nome}") }

    // Fica pedindo ate vir um codigo que existe mesmo. Sem isso o INSERT
    // quebrava na chave estrangeira e o sistema fechava no primeiro acesso.
    var setorId: Int
    while (true) {
        setorId = Entrada.inteiro("Setor: ", 1)
        if (setores.any { it.id == setorId }) break
        println("  > Codigo nao esta na lista de setores.")
    }

    val id = funcionarioDao.inserir(
        Funcionario(
            nome = nome, cpf = cpf, email = email, cargo = cargo,
            salario = salario, setorId = setorId
        )
    )
    println("\n  Cadastrado com o codigo $id. Agora da pra usar o sistema.")
}
