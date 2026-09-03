package br.com.provedor.banco

/**
 * Roda o schema.sql na subida do sistema. Como o script usa IF NOT EXISTS
 * e ON CONFLICT, pode rodar quantas vezes for que nao duplica nada.
 */
object Migracao {

    fun criarEstrutura() {
        val script = Migracao::class.java.getResourceAsStream("/schema.sql")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("schema.sql nao encontrado em resources.")

        val comandos = script
            .lines()
            .filterNot { it.trimStart().startsWith("--") }   // tira os comentarios
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        Conexao.get().createStatement().use { stmt ->
            comandos.forEach { stmt.execute(it) }
        }
    }
}
