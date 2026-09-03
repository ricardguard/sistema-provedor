package br.com.provedor.banco

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

/**
 * Ponto unico de conexao com o PostgreSQL.
 * Como e uma aplicacao de console de um usuario so, mantenho uma conexao
 * aberta e reaproveito - nao precisa de pool aqui.
 */
object Conexao {

    private var conexao: Connection? = null

    /** Ligado pelo Transacao enquanto tem transacao aberta. */
    internal var transacaoAberta: Boolean = false

    fun get(): Connection {
        val atual = conexao
        if (atual != null && !atual.isClosed) return atual

        // Se a conexao caiu com transacao aberta, reconectar seria pior que
        // falhar: os comandos seguintes iriam num autocommit novo e o rollback
        // no fim nao desfaria nada. Melhor abortar a operacao inteira.
        if (transacaoAberta) {
            transacaoAberta = false
            conexao = null
            throw IllegalStateException(
                "A conexao com o banco caiu no meio de uma operacao. Nada foi gravado, refaca o lancamento."
            )
        }

        val config = carregarConfiguracao()
        try {
            // Registro explicito do driver. O JDBC 4 acha sozinho, mas assim
            // o erro fica claro se o jar do PostgreSQL nao estiver no classpath.
            Class.forName("org.postgresql.Driver")
            val nova = DriverManager.getConnection(
                config.getProperty("db.url"),
                config.getProperty("db.usuario"),
                config.getProperty("db.senha")
            )
            nova.autoCommit = true
            conexao = nova
            return nova
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("Driver do PostgreSQL nao encontrado no projeto.", e)
        } catch (e: SQLException) {
            throw IllegalStateException(
                "Nao consegui conectar no banco (${e.message}). " +
                        "Confere se o PostgreSQL esta ligado e se o banco 'provedor_db' existe.", e
            )
        }
    }

    fun fechar() {
        try {
            conexao?.takeIf { !it.isClosed }?.close()
        } catch (e: SQLException) {
            println("Aviso: erro ao fechar a conexao (${e.message})")
        } finally {
            conexao = null
        }
    }

    /**
     * Le o banco.properties do resources e deixa as variaveis de ambiente
     * sobrescreverem - assim da pra rodar em outra maquina sem mexer no codigo.
     */
    private fun carregarConfiguracao(): Properties {
        val props = Properties()
        val arquivo = Conexao::class.java.getResourceAsStream("/banco.properties")
            ?: throw IllegalStateException("Arquivo banco.properties nao encontrado em resources.")
        arquivo.use { props.load(it) }

        System.getenv("PROVEDOR_DB_URL")?.let { props.setProperty("db.url", it) }
        System.getenv("PROVEDOR_DB_USER")?.let { props.setProperty("db.usuario", it) }
        System.getenv("PROVEDOR_DB_PASSWORD")?.let { props.setProperty("db.senha", it) }
        return props
    }
}
