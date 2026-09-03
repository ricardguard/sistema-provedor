package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.CaixaDao
import br.com.provedor.dao.MovimentacaoDao
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Movimentacao
import br.com.provedor.modelo.TipoMovimentacao
import java.math.BigDecimal
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Caixa da empresa.
 *
 * Aqui esta o encapsulamento pedido no enunciado: o campo "saldo" e privado e
 * nao tem setter. Nenhuma outra classe consegue mexer no dinheiro direto - o
 * unico caminho e chamar registrarEntrada / registrarSaida, que validam o
 * valor, gravam a movimentacao e atualizam o saldo dentro da mesma transacao.
 * Se qualquer passo falhar, da rollback e nada fica gravado pela metade.
 */
object Caixa {

    private val caixaDao = CaixaDao()
    private val movimentacaoDao = MovimentacaoDao()

    private var saldo: BigDecimal = BigDecimal.ZERO

    /** Leitura publica: da pra consultar, nao da pra alterar de fora. */
    val saldoAtual: BigDecimal
        get() = saldo

    /** Busca no banco o saldo de verdade. Chamo na abertura e depois de cada operacao. */
    fun atualizarDoBanco() {
        saldo = caixaDao.lerSaldo()
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
                val saldoNoBanco = caixaDao.lerSaldoParaAtualizar()

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
                val id = movimentacaoDao.inserir(movimentacao)
                caixaDao.gravarSaldo(novoSaldo)
                movimentacao.copy(id = id)
            }

            atualizarDoBanco()
            return movimentacaoGravada

        } catch (e: SQLException) {
            throw RegraDeNegocioException("Erro do banco ao gravar a movimentacao: ${e.message}")
        }
    }
}
