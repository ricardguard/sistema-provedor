package br.com.provedor.servico

import br.com.provedor.banco.Conexao
import br.com.provedor.banco.Transacao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Movimentacao
import br.com.provedor.modelo.TipoMovimentacao
import java.math.BigDecimal
import java.sql.SQLException
import java.sql.Statement
import java.time.LocalDateTime

/**
 * Caixa da empresa - e aqui que mora o encapsulamento pedido no enunciado.
 *
 * O saldo e um campo privado sem setter, mas so isso nao bastaria: se
 * existisse um DAO publico capaz de gravar na tabela caixa, qualquer classe
 * mudaria o dinheiro sem gerar movimentacao. Por isso o SQL que mexe no saldo
 * e o que grava o lancamento sao funcoes PRIVADAS deste objeto. Fora daqui so
 * existem dois caminhos: registrarEntrada e registrarSaida, que validam tudo
 * antes e gravam lancamento + saldo na mesma transacao.
 */
object Caixa {

    private var saldo: BigDecimal = BigDecimal.ZERO

    /** Leitura publica: da pra consultar, nao da pra alterar de fora. */
    val saldoAtual: BigDecimal
        get() = saldo

    /** Busca no banco o saldo de verdade. Chamo na abertura e depois de cada operacao. */
    fun atualizarDoBanco() {
        saldo = lerSaldo()
    }

    /**
     * Rele o saldo, mas so quando nao tem transacao aberta. Se lesse no meio
     * de uma transacao, o cache guardaria um valor que ainda pode sofrer
     * rollback. Os servicos chamam isso logo depois de fechar a transacao
     * deles, pra tela mostrar o saldo certo.
     */
    fun sincronizar() {
        if (Conexao.get().autoCommit) atualizarDoBanco()
    }

    fun registrarEntrada(
        valor: BigDecimal,
        categoria: String,
        pagador: String,
        recebedor: String,
        descricao: String,
        responsavel: Funcionario
    ): Movimentacao =
        registrar(TipoMovimentacao.ENTRADA, valor, categoria, pagador, recebedor, descricao, responsavel)

    fun registrarSaida(
        valor: BigDecimal,
        categoria: String,
        pagador: String,
        recebedor: String,
        descricao: String,
        responsavel: Funcionario
    ): Movimentacao =
        registrar(TipoMovimentacao.SAIDA, valor, categoria, pagador, recebedor, descricao, responsavel)

    private fun registrar(
        tipo: TipoMovimentacao,
        valor: BigDecimal,
        categoria: String,
        pagador: String,
        recebedor: String,
        descricao: String,
        responsavel: Funcionario
    ): Movimentacao {

        // 1) o que da pra barrar antes de encostar no banco
        if (valor <= BigDecimal.ZERO) {
            throw RegraDeNegocioException("O valor da movimentacao precisa ser maior que zero.")
        }
        if (descricao.isBlank()) {
            throw RegraDeNegocioException("Toda movimentacao precisa de um motivo/descricao.")
        }
        if (pagador.isBlank() || recebedor.isBlank()) {
            throw RegraDeNegocioException("Informe quem pagou e quem recebeu.")
        }
        if (responsavel.id <= 0) {
            throw RegraDeNegocioException("Movimentacao sem responsavel nao pode ser gravada.")
        }
        if (!responsavel.ativo) {
            throw RegraDeNegocioException("Funcionario desligado nao pode responder por movimentacao.")
        }

        try {
            val movimentacaoGravada = Transacao.executar {
                // 2) trava a linha do caixa e le o saldo que esta valendo agora
                val saldoNoBanco = lerSaldoParaAtualizar()

                val novoSaldo = if (tipo == TipoMovimentacao.ENTRADA) {
                    saldoNoBanco.add(valor)
                } else {
                    if (saldoNoBanco < valor) {
                        throw SaldoInsuficienteException(
                            "Saldo insuficiente: tem ${saldoNoBanco.toPlainString()} em caixa " +
                                    "e a saida e de ${valor.toPlainString()}."
                        )
                    }
                    saldoNoBanco.subtract(valor)
                }

                val movimentacao = Movimentacao(
                    tipo = tipo,
                    categoria = categoria,
                    valor = valor,
                    pagador = pagador.trim(),
                    recebedor = recebedor.trim(),
                    dataHora = LocalDateTime.now(),
                    descricao = descricao.trim(),
                    responsavelId = responsavel.id,
                    responsavelNome = responsavel.nome,
                    saldoApos = novoSaldo
                )

                // 3) lancamento e saldo gravados juntos
                val id = gravarMovimentacao(movimentacao)
                gravarSaldo(novoSaldo)
                movimentacao.copy(id = id)
            }

            // So atualizo o saldo em memoria quando a transacao realmente fechou.
            // Se este registrar foi chamado de dentro de um servico maior, a
            // transacao ainda esta aberta e quem confirma o saldo e o servico.
            sincronizar()

            return movimentacaoGravada

        } catch (e: SQLException) {
            throw RegraDeNegocioException("Erro do banco ao gravar a movimentacao: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Daqui pra baixo e tudo privado: e o unico ponto do sistema que
    // escreve na tabela caixa e na movimentacao_financeira.
    // ------------------------------------------------------------------

    private fun lerSaldo(): BigDecimal {
        Conexao.get().prepareStatement("SELECT saldo FROM caixa WHERE id = 1").use { ps ->
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getBigDecimal("saldo") else BigDecimal.ZERO
            }
        }
    }

    /**
     * O FOR UPDATE trava a linha do caixa ate o commit, senao duas operacoes
     * poderiam ler o mesmo saldo e gravar valores errados.
     */
    private fun lerSaldoParaAtualizar(): BigDecimal {
        Conexao.get().prepareStatement("SELECT saldo FROM caixa WHERE id = 1 FOR UPDATE").use { ps ->
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw IllegalStateException("Registro do caixa nao encontrado.")
                return rs.getBigDecimal("saldo")
            }
        }
    }

    private fun gravarSaldo(novoSaldo: BigDecimal) {
        Conexao.get().prepareStatement("UPDATE caixa SET saldo = ?, atualizado_em = NOW() WHERE id = 1").use { ps ->
            ps.setBigDecimal(1, novoSaldo)
            ps.executeUpdate()
        }
    }

    private fun gravarMovimentacao(mov: Movimentacao): Int {
        val sql = """
            INSERT INTO movimentacao_financeira
                (tipo, categoria, valor, pagador, recebedor, data_hora, descricao, responsavel_id, saldo_apos)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, mov.tipo.name)
            ps.setString(2, mov.categoria)
            ps.setBigDecimal(3, mov.valor)
            ps.setString(4, mov.pagador)
            ps.setString(5, mov.recebedor)
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(mov.dataHora))
            ps.setString(7, mov.descricao)
            ps.setInt(8, mov.responsavelId)
            ps.setBigDecimal(9, mov.saldoApos)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }
}
