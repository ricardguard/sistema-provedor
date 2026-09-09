package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.ContratoDao
import br.com.provedor.dao.FaturaDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.OrdemServicoDao
import br.com.provedor.dao.PlanoDao
import br.com.provedor.modelo.Contrato
import br.com.provedor.modelo.Fatura
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.OrdemServico
import br.com.provedor.modelo.StatusContrato
import br.com.provedor.modelo.StatusFatura
import br.com.provedor.modelo.TipoOrdem
import br.com.provedor.util.Empresa
import br.com.provedor.util.Validacao
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fluxo do servico vendido pelo provedor: contrato do plano, faturas do mes
 * e recebimento. Toda parte de dinheiro passa pelo Caixa.
 */
class ServicoContrato {

    private val contratoDao = ContratoDao()
    private val faturaDao = FaturaDao()
    private val planoDao = PlanoDao()
    private val clienteDao = ClienteDao()
    private val funcionarioDao = FuncionarioDao()
    private val ordemDao = OrdemServicoDao()

    /**
     * Fecha o contrato do cliente. Se o plano tiver taxa de instalacao e o
     * cliente pagar na hora, ja entra no caixa. Alem disso abre a OS de
     * instalacao pro pessoal do suporte.
     */
    fun contratarPlano(
        clienteId: Int,
        planoId: Int,
        vendedorId: Int?,
        diaVencimento: Int,
        cobrarInstalacaoAgora: Boolean,
        responsavel: Funcionario
    ): Contrato {

        val cliente = clienteDao.buscarPorId(clienteId)
            ?: throw RegraDeNegocioException("Cliente nao encontrado.")
        if (!cliente.ativo) throw RegraDeNegocioException("Cliente inativo, reative o cadastro antes.")

        val plano = planoDao.buscarPorId(planoId)
            ?: throw RegraDeNegocioException("Plano nao encontrado.")
        if (!plano.ativo) throw RegraDeNegocioException("Esse plano nao esta mais sendo vendido.")

        if (diaVencimento !in 1..28) {
            throw RegraDeNegocioException("O dia de vencimento tem que ser entre 1 e 28.")
        }

        // Sem essa checagem o codigo errado ia direto pro banco e o operador
        // levava um erro cru de chave estrangeira na tela.
        if (vendedorId != null) {
            val vendedor = funcionarioDao.buscarPorId(vendedorId)
                ?: throw RegraDeNegocioException("Vendedor nao encontrado.")
            if (!vendedor.ativo) throw RegraDeNegocioException("Esse vendedor esta desligado.")
        }

        val jaTemAtivo = contratoDao.listarPorCliente(clienteId)
            .any { it.status == StatusContrato.ATIVO && it.planoId == planoId }
        if (jaTemAtivo) {
            throw RegraDeNegocioException("Esse cliente ja tem um contrato ativo nesse mesmo plano.")
        }

        val resultado = Transacao.executar {
            val novo = Contrato(
                clienteId = cliente.id,
                planoId = plano.id,
                vendedorId = vendedorId,
                // congela o preco do dia da assinatura no proprio contrato
                valorMensal = plano.valorMensal,
                dataInicio = LocalDate.now(),
                diaVencimento = diaVencimento
            )
            val id = contratoDao.inserir(novo)

            ordemDao.inserir(
                OrdemServico(
                    clienteId = cliente.id,
                    tipo = TipoOrdem.INSTALACAO,
                    descricao = "Instalacao do plano ${plano.nome} (contrato $id)",
                    valor = BigDecimal.ZERO
                )
            )

            if (plano.taxaInstalacao > BigDecimal.ZERO) {
                if (cobrarInstalacaoAgora) {
                    Caixa.registrarEntrada(
                        valor = plano.taxaInstalacao,
                        categoria = "TAXA_INSTALACAO",
                        pagador = cliente,
                        recebedor = Empresa,
                        descricao = "Taxa de instalacao do contrato $id - plano ${plano.nome}",
                        responsavel = responsavel
                    )
                } else {
                    // Se o cliente nao paga agora, a taxa vira fatura em aberto.
                    // Antes ela simplesmente sumia: a empresa nunca ficava
                    // sabendo que aquele cliente ainda devia a instalacao.
                    faturaDao.inserir(
                        Fatura(
                            contratoId = id,
                            competencia = competenciaDe(LocalDate.now()),
                            valor = plano.taxaInstalacao,
                            vencimento = proximoVencimento(diaVencimento)
                        )
                    )
                }
            }

            novo.copy(
                id = id,
                clienteNome = cliente.nome,
                planoNome = plano.nome,
                valorMensal = plano.valorMensal
            )
        }

        // a transacao fechou, entao agora da pra reler o saldo
        Caixa.sincronizar()
        return resultado
    }

    /**
     * Gera a fatura do mes de cada contrato ativo.
     * Se a competencia ja foi gerada antes, pula (o banco tambem barra pela UNIQUE).
     */
    fun gerarFaturasDaCompetencia(competencia: String): Int {
        // Valido aqui e nao so no menu: se um dia outra tela chamar este
        // metodo, um "092026" quebraria no split com um erro sem sentido.
        if (!Validacao.competenciaValida(competencia)) {
            throw RegraDeNegocioException("Competencia invalida, use MM/AAAA.")
        }
        val partes = competencia.split("/")
        val mes = partes[0].toInt()
        val ano = partes[1].toInt()

        var geradas = 0
        Transacao.executar {
            contratoDao.listarAtivos().forEach { contrato ->
                if (!faturaDao.existeCompetencia(contrato.id, competencia)) {
                    faturaDao.inserir(
                        Fatura(
                            contratoId = contrato.id,
                            competencia = competencia,
                            valor = contrato.valorMensal,
                            vencimento = LocalDate.of(ano, mes, contrato.diaVencimento)
                        )
                    )
                    geradas++
                }
            }
        }
        return geradas
    }

    /** Data como MM/AAAA, do jeito que a competencia e guardada. */
    private fun competenciaDe(data: LocalDate): String =
        String.format("%02d/%d", data.monthValue, data.year)

    /** Proximo vencimento a partir de hoje, respeitando o dia do contrato. */
    private fun proximoVencimento(diaVencimento: Int): LocalDate {
        val hoje = LocalDate.now()
        val esteMes = LocalDate.of(hoje.year, hoje.monthValue, diaVencimento)
        return if (esteMes.isBefore(hoje)) esteMes.plusMonths(1) else esteMes
    }

    /** Cliente pagou a mensalidade: baixa a fatura e entra dinheiro no caixa. */
    fun receberFatura(faturaId: Int, responsavel: Funcionario): Fatura {
        val fatura = faturaDao.buscarPorId(faturaId)
            ?: throw RegraDeNegocioException("Fatura nao encontrada.")
        if (fatura.status != StatusFatura.ABERTA) {
            throw RegraDeNegocioException("Essa fatura ja esta ${fatura.status.name.lowercase()}.")
        }

        // Busco o cliente de verdade em vez de usar o nome que veio no JOIN:
        // o Caixa quer uma Pessoa, nao um texto solto.
        val contrato = contratoDao.buscarPorId(fatura.contratoId)
            ?: throw RegraDeNegocioException("Contrato da fatura nao encontrado.")
        val cliente = clienteDao.buscarPorId(contrato.clienteId)
            ?: throw RegraDeNegocioException("Cliente do contrato nao encontrado.")

        val resultado = Transacao.executar {
            val agora = LocalDateTime.now()
            if (!faturaDao.marcarComoPaga(fatura.id, agora)) {
                throw RegraDeNegocioException("Nao consegui baixar a fatura, tente de novo.")
            }
            Caixa.registrarEntrada(
                valor = fatura.valor,
                categoria = "MENSALIDADE",
                pagador = cliente,
                recebedor = Empresa,
                descricao = "Mensalidade ${fatura.competencia} - contrato ${fatura.contratoId}",
                responsavel = responsavel
            )
            fatura.copy(status = StatusFatura.PAGA, dataPagamento = agora)
        }

        // a transacao fechou, entao agora da pra reler o saldo
        Caixa.sincronizar()
        return resultado
    }

    /** Cancela o contrato e as faturas que ainda estavam em aberto. */
    fun cancelarContrato(contratoId: Int, abrirRetirada: Boolean): Contrato {
        val contrato = contratoDao.buscarPorId(contratoId)
            ?: throw RegraDeNegocioException("Contrato nao encontrado.")
        if (contrato.status == StatusContrato.CANCELADO) {
            throw RegraDeNegocioException("Esse contrato ja esta cancelado.")
        }

        return Transacao.executar {
            if (!contratoDao.alterarStatus(contrato.id, StatusContrato.CANCELADO, LocalDate.now())) {
                throw RegraDeNegocioException("Nao consegui cancelar o contrato ${contrato.id}.")
            }
            faturaDao.listarPorContrato(contrato.id)
                .filter { it.status == StatusFatura.ABERTA }
                .forEach { faturaDao.cancelar(it.id) }

            if (abrirRetirada) {
                ordemDao.inserir(
                    OrdemServico(
                        clienteId = contrato.clienteId,
                        tipo = TipoOrdem.RETIRADA,
                        descricao = "Retirada de equipamento - contrato ${contrato.id}",
                        valor = BigDecimal.ZERO
                    )
                )
            }
            contrato.copy(status = StatusContrato.CANCELADO, dataCancelamento = LocalDate.now())
        }
    }

    fun suspenderOuReativar(contratoId: Int, novoStatus: StatusContrato): Contrato {
        // So aceito os dois status que essa operacao existe pra alternar.
        // Cancelamento tem regra propria (cancela as faturas, grava a data),
        // entao nao pode entrar por aqui.
        if (novoStatus != StatusContrato.ATIVO && novoStatus != StatusContrato.SUSPENSO) {
            throw RegraDeNegocioException("Use o cancelamento de contrato para cancelar.")
        }
        val contrato = contratoDao.buscarPorId(contratoId)
            ?: throw RegraDeNegocioException("Contrato nao encontrado.")
        if (contrato.status == StatusContrato.CANCELADO) {
            throw RegraDeNegocioException("Contrato cancelado nao volta atras, faca um novo.")
        }
        if (!contratoDao.alterarStatus(contrato.id, novoStatus)) {
            throw RegraDeNegocioException("Nao consegui mudar a situacao do contrato ${contrato.id}.")
        }
        return contrato.copy(status = novoStatus)
    }
}
