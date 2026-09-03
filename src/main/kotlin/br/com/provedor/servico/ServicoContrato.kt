package br.com.provedor.servico

import br.com.provedor.banco.Transacao
import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.ContratoDao
import br.com.provedor.dao.FaturaDao
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fluxo do servico vendido pelo provedor: contrato do plano, faturas do mes
 * e recebimento. Toda parte de dinheiro passa pelo Caixa.
 */
class ServicoContrato(
    private val contratoDao: ContratoDao = ContratoDao(),
    private val faturaDao: FaturaDao = FaturaDao(),
    private val planoDao: PlanoDao = PlanoDao(),
    private val clienteDao: ClienteDao = ClienteDao(),
    private val ordemDao: OrdemServicoDao = OrdemServicoDao()
) {

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

        val jaTemAtivo = contratoDao.listarPorCliente(clienteId)
            .any { it.status == StatusContrato.ATIVO && it.planoId == planoId }
        if (jaTemAtivo) {
            throw RegraDeNegocioException("Esse cliente ja tem um contrato ativo nesse mesmo plano.")
        }

        return Transacao.executar {
            val novo = Contrato(
                clienteId = cliente.id,
                planoId = plano.id,
                vendedorId = vendedorId,
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

            if (cobrarInstalacaoAgora && plano.taxaInstalacao > BigDecimal.ZERO) {
                Caixa.registrarEntrada(
                    valor = plano.taxaInstalacao,
                    categoria = "TAXA_INSTALACAO",
                    pagador = cliente.nome,
                    recebedor = Empresa.NOME,
                    descricao = "Taxa de instalacao do contrato $id - plano ${plano.nome}",
                    responsavel = responsavel
                )
            }

            novo.copy(
                id = id,
                clienteNome = cliente.nome,
                planoNome = plano.nome,
                valorMensal = plano.valorMensal
            )
        }
    }

    /**
     * Gera a fatura do mes de cada contrato ativo.
     * Se a competencia ja foi gerada antes, pula (o banco tambem barra pela UNIQUE).
     */
    fun gerarFaturasDaCompetencia(competencia: String): Int {
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

    /** Cliente pagou a mensalidade: baixa a fatura e entra dinheiro no caixa. */
    fun receberFatura(faturaId: Int, responsavel: Funcionario): Fatura {
        val fatura = faturaDao.buscarPorId(faturaId)
            ?: throw RegraDeNegocioException("Fatura nao encontrada.")
        if (fatura.status != StatusFatura.ABERTA) {
            throw RegraDeNegocioException("Essa fatura ja esta ${fatura.status.name.lowercase()}.")
        }

        return Transacao.executar {
            val agora = LocalDateTime.now()
            if (!faturaDao.marcarComoPaga(fatura.id, agora)) {
                throw RegraDeNegocioException("Nao consegui baixar a fatura, tente de novo.")
            }
            Caixa.registrarEntrada(
                valor = fatura.valor,
                categoria = "MENSALIDADE",
                pagador = fatura.clienteNome,
                recebedor = Empresa.NOME,
                descricao = "Mensalidade ${fatura.competencia} - contrato ${fatura.contratoId}",
                responsavel = responsavel
            )
            fatura.copy(status = StatusFatura.PAGA, dataPagamento = agora)
        }
    }

    /** Cancela o contrato e as faturas que ainda estavam em aberto. */
    fun cancelarContrato(contratoId: Int, abrirRetirada: Boolean): Contrato {
        val contrato = contratoDao.buscarPorId(contratoId)
            ?: throw RegraDeNegocioException("Contrato nao encontrado.")
        if (contrato.status == StatusContrato.CANCELADO) {
            throw RegraDeNegocioException("Esse contrato ja esta cancelado.")
        }

        return Transacao.executar {
            contratoDao.alterarStatus(contrato.id, StatusContrato.CANCELADO, LocalDate.now())
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
        val contrato = contratoDao.buscarPorId(contratoId)
            ?: throw RegraDeNegocioException("Contrato nao encontrado.")
        if (contrato.status == StatusContrato.CANCELADO) {
            throw RegraDeNegocioException("Contrato cancelado nao volta atras, faca um novo.")
        }
        contratoDao.alterarStatus(contrato.id, novoStatus)
        return contrato.copy(status = novoStatus)
    }
}
