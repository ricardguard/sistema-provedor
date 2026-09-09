package br.com.provedor.menu

import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.ContratoDao
import br.com.provedor.dao.FaturaDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.PlanoDao
import br.com.provedor.modelo.Plano
import br.com.provedor.modelo.StatusContrato
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.servico.ServicoContrato
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import br.com.provedor.util.Validacao
import java.math.BigDecimal
import java.time.LocalDate

/** Planos vendidos, contratos dos clientes e as mensalidades. */
object MenuComercial {

    private val planoDao = PlanoDao()
    private val contratoDao = ContratoDao()
    private val faturaDao = FaturaDao()
    private val clienteDao = ClienteDao()
    private val funcionarioDao = FuncionarioDao()
    private val servicoContrato = ServicoContrato()

    fun exibir() {
        while (true) {
            Formato.titulo("Comercial")
            println("  1 - Planos de internet")
            println("  2 - Contratos")
            println("  3 - Faturas / mensalidades")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 3)) {
                1 -> menuPlanos()
                2 -> menuContratos()
                3 -> menuFaturas()
                0 -> return
            }
        }
    }

    // -------------------------------------------------------------- planos

    private fun menuPlanos() {
        while (true) {
            Formato.titulo("Planos")
            println("  1 - Listar")
            println("  2 - Cadastrar")
            println("  3 - Alterar")
            println("  4 - Ativar / desativar")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 4)) {
                1 -> protegido { listarPlanos() }
                2 -> protegido { cadastrarPlano() }
                3 -> protegido { alterarPlano() }
                4 -> protegido { alterarSituacaoPlano() }
                0 -> return
            }
        }
    }

    private fun listarPlanos() {
        val planos = planoDao.listar()
        Formato.titulo("Planos cadastrados")
        if (planos.isEmpty()) {
            println("  Nenhum plano cadastrado.")
            return
        }
        println("  COD PLANO                     VELOC.    MENSALIDADE   INSTALACAO")
        planos.forEach {
            val marca = if (it.ativo) " " else "*"
            println(
                "  ${it.id.toString().padStart(3)}$marca${Formato.encurtar(it.nome, 25)} " +
                        "${it.velocidadeMega.toString().padStart(5)} MB  " +
                        "${Formato.moeda(it.valorMensal).padEnd(13)} ${Formato.moeda(it.taxaInstalacao)}"
            )
        }
        println("\n  (* = fora de venda)")
    }

    private fun cadastrarPlano() {
        Formato.titulo("Novo plano")
        val nome = Entrada.texto("Nome do plano: ", 60, 3)
        if (planoDao.listar().any { it.nome.equals(nome, ignoreCase = true) }) {
            throw RegraDeNegocioException("Ja existe um plano com esse nome.")
        }
        val velocidade = Entrada.inteiro("Velocidade em mega: ", 1, 10000)
        val mensalidade = Entrada.decimal("Valor mensal: ", BigDecimal("1.00"))
        val instalacao = Entrada.decimal("Taxa de instalacao (0 se for gratis): ")

        val id = planoDao.inserir(
            Plano(
                nome = nome, velocidadeMega = velocidade,
                valorMensal = mensalidade, taxaInstalacao = instalacao
            )
        )
        println("\n  Plano cadastrado com o codigo $id.")
    }

    private fun alterarPlano() {
        listarPlanos()
        val id = Entrada.inteiro("Codigo do plano (0 cancela): ", 0)
        if (id == 0) return
        val plano = planoDao.buscarPorId(id) ?: throw RegraDeNegocioException("Plano nao encontrado.")

        val nome = Entrada.texto("Nome", plano.nome, 60, 3)
        val velocidade = Entrada.inteiro("Velocidade em mega", plano.velocidadeMega, 1, 10000)
        val mensalidade = Entrada.decimal("Mensalidade", plano.valorMensal, BigDecimal("1.00"))
        val instalacao = Entrada.decimal("Taxa de instalacao", plano.taxaInstalacao)

        val ok = planoDao.atualizar(
            plano.copy(
                nome = nome, velocidadeMega = velocidade,
                valorMensal = mensalidade, taxaInstalacao = instalacao
            )
        )
        if (!ok) {
            println("\n  Nada foi alterado, confere o codigo do plano.")
            return
        }
        println("\n  Plano atualizado. Os contratos ja assinados continuam no valor")
        println("  que foi congelado neles - o preco novo vale so pras proximas vendas.")
    }

    private fun alterarSituacaoPlano() {
        listarPlanos()
        val id = Entrada.inteiro("Codigo do plano (0 cancela): ", 0)
        if (id == 0) return
        val plano = planoDao.buscarPorId(id) ?: throw RegraDeNegocioException("Plano nao encontrado.")
        val nova = !plano.ativo
        if (Entrada.confirmar("Confirma ${if (nova) "reativar" else "tirar de venda"} o plano ${plano.nome}?")) {
            planoDao.alterarSituacao(plano.id, nova)
            println("\n  Plano atualizado.")
        }
    }

    // ------------------------------------------------------------ contratos

    private fun menuContratos() {
        while (true) {
            Formato.titulo("Contratos")
            println("  1 - Listar todos")
            println("  2 - Novo contrato")
            println("  3 - Suspender / reativar")
            println("  4 - Cancelar contrato")
            println("  5 - Contratos de um cliente")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 5)) {
                1 -> protegido { listarContratos() }
                2 -> protegido { novoContrato() }
                3 -> protegido { suspenderReativar() }
                4 -> protegido { cancelarContrato() }
                5 -> protegido { contratosDoCliente() }
                0 -> return
            }
        }
    }

    private fun listarContratos() {
        val contratos = contratoDao.listar()
        Formato.titulo("Contratos")
        if (contratos.isEmpty()) {
            println("  Nenhum contrato cadastrado.")
            return
        }
        println("  COD CLIENTE                    PLANO             VENC  MENSAL      SITUACAO")
        contratos.forEach {
            println(
                "  ${it.id.toString().padStart(3)} ${Formato.encurtar(it.clienteNome, 26)} " +
                        "${Formato.encurtar(it.planoNome, 17)} " +
                        "${it.diaVencimento.toString().padStart(2)}   " +
                        "${Formato.moeda(it.valorMensal).padEnd(11)} ${it.status}"
            )
        }
    }

    private fun novoContrato() {
        Formato.titulo("Novo contrato")

        val clientes = clienteDao.listar(somenteAtivos = true)
        if (clientes.isEmpty()) throw RegraDeNegocioException("Nao ha cliente ativo cadastrado.")
        clientes.forEach { println("   [${it.id}] ${Formato.encurtar(it.nome, 30)} ${Formato.documento(it.cpfCnpj)}") }
        val clienteId = Entrada.inteiro("Codigo do cliente: ", 1)

        val planos = planoDao.listar(somenteAtivos = true)
        if (planos.isEmpty()) throw RegraDeNegocioException("Nao ha plano ativo pra vender.")
        println()
        planos.forEach {
            println("   [${it.id}] ${Formato.encurtar(it.nome, 22)} ${it.velocidadeMega} MB - " +
                    "${Formato.moeda(it.valorMensal)}/mes - instalacao ${Formato.moeda(it.taxaInstalacao)}")
        }
        val planoId = Entrada.inteiro("Codigo do plano: ", 1)

        val vendedores = funcionarioDao.listar(somenteAtivos = true)
        println()
        vendedores.forEach { println("   [${it.id}] ${Formato.encurtar(it.nome, 26)} ${it.setorNome}") }
        val vendedorId = Entrada.inteiro("Codigo do vendedor (0 = sem vendedor): ", 0)

        val diaVencimento = Entrada.inteiro("Dia de vencimento da mensalidade (1 a 28): ", 1, 28)

        val plano = planoDao.buscarPorId(planoId) ?: throw RegraDeNegocioException("Plano nao encontrado.")
        val cobrarInstalacao = if (plano.taxaInstalacao > BigDecimal.ZERO) {
            Entrada.confirmar("Cliente vai pagar a instalacao (${Formato.moeda(plano.taxaInstalacao)}) agora?")
        } else {
            false
        }

        val contrato = servicoContrato.contratarPlano(
            clienteId = clienteId,
            planoId = planoId,
            vendedorId = if (vendedorId == 0) null else vendedorId,
            diaVencimento = diaVencimento,
            cobrarInstalacaoAgora = cobrarInstalacao,
            responsavel = Sessao.logado
        )

        println("\n  Contrato ${contrato.id} fechado pro cliente ${contrato.clienteNome}.")
        println("  Abri tambem a OS de instalacao pro pessoal do suporte.")
        if (cobrarInstalacao) println("  Taxa de instalacao lancada no caixa.")
    }

    private fun suspenderReativar() {
        listarContratos()
        val id = Entrada.inteiro("Codigo do contrato (0 cancela): ", 0)
        if (id == 0) return
        val contrato = contratoDao.buscarPorId(id) ?: throw RegraDeNegocioException("Contrato nao encontrado.")

        val novoStatus = if (contrato.status == StatusContrato.ATIVO) StatusContrato.SUSPENSO
                         else StatusContrato.ATIVO
        if (Entrada.confirmar("Mudar o contrato ${contrato.id} para $novoStatus?")) {
            servicoContrato.suspenderOuReativar(contrato.id, novoStatus)
            println("\n  Contrato agora esta $novoStatus.")
        }
    }

    private fun cancelarContrato() {
        listarContratos()
        val id = Entrada.inteiro("Codigo do contrato (0 cancela): ", 0)
        if (id == 0) return
        if (!Entrada.confirmar("Cancelar o contrato $id e as faturas em aberto dele?")) return
        val abrirRetirada = Entrada.confirmar("Abrir OS de retirada do equipamento?")
        servicoContrato.cancelarContrato(id, abrirRetirada)
        println("\n  Contrato cancelado.")
    }

    private fun contratosDoCliente() {
        val termo = Entrada.texto("Nome ou documento do cliente: ", 120, 2)
        val encontrados = clienteDao.buscarPorNomeOuDocumento(termo)
        if (encontrados.isEmpty()) throw RegraDeNegocioException("Nenhum cliente com esse termo.")

        encontrados.forEach { println("   [${it.id}] ${it.nome}") }
        val clienteId = Entrada.inteiro("Codigo do cliente: ", 1)

        val contratos = contratoDao.listarPorCliente(clienteId)
        Formato.titulo("Contratos do cliente")
        if (contratos.isEmpty()) {
            println("  Esse cliente nao tem contrato.")
            return
        }
        contratos.forEach {
            println("  [${it.id}] ${Formato.encurtar(it.planoNome, 20)} venc. dia ${it.diaVencimento} - " +
                    "${Formato.moeda(it.valorMensal)} - ${it.status} - desde ${Formato.data(it.dataInicio)}")
        }
    }

    // -------------------------------------------------------------- faturas

    private fun menuFaturas() {
        while (true) {
            Formato.titulo("Faturas")
            println("  1 - Gerar faturas do mes")
            println("  2 - Faturas em aberto")
            println("  3 - Receber pagamento")
            println("  4 - Faturas de um contrato")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 4)) {
                1 -> protegido { gerarFaturas() }
                2 -> protegido { listarEmAberto() }
                3 -> protegido { receberFatura() }
                4 -> protegido { faturasDoContrato() }
                0 -> return
            }
        }
    }

    private fun gerarFaturas() {
        val hoje = LocalDate.now()
        val sugestao = String.format("%02d/%d", hoje.monthValue, hoje.year)
        Formato.titulo("Gerar faturas")
        println("  Competencia sugerida: $sugestao")

        val competencia = Entrada.competencia("Competencia (MM/AAAA): ")
        val quantas = servicoContrato.gerarFaturasDaCompetencia(competencia)
        println(
            if (quantas == 0) "\n  Nenhuma fatura nova - todos os contratos ativos ja tinham a de $competencia."
            else "\n  Foram geradas $quantas fatura(s) da competencia $competencia."
        )
    }

    private fun listarEmAberto() {
        val faturas = faturaDao.listarEmAberto()
        Formato.titulo("Faturas em aberto")
        if (faturas.isEmpty()) {
            println("  Nao tem fatura em aberto.")
            return
        }
        println("  COD CLIENTE                    COMPET.  VENCIMENTO   VALOR")
        faturas.forEach {
            val aviso = if (it.emAtraso) " ATRASADA" else ""
            println(
                "  ${it.id.toString().padStart(3)} ${Formato.encurtar(it.clienteNome, 26)} " +
                        "${it.competencia}  ${Formato.data(it.vencimento)}   " +
                        "${Formato.moeda(it.valor)}$aviso"
            )
        }
    }

    private fun receberFatura() {
        listarEmAberto()
        val id = Entrada.inteiro("Codigo da fatura (0 cancela): ", 0)
        if (id == 0) return
        val fatura = faturaDao.buscarPorId(id) ?: throw RegraDeNegocioException("Fatura nao encontrada.")

        println("\n  Cliente: ${fatura.clienteNome}")
        println("  Valor:   ${Formato.moeda(fatura.valor)}")
        if (!Entrada.confirmar("Confirma o recebimento?")) return

        servicoContrato.receberFatura(fatura.id, Sessao.logado)
        println("\n  Fatura baixada e valor lancado no caixa.")
    }

    private fun faturasDoContrato() {
        listarContratos()
        val id = Entrada.inteiro("Codigo do contrato (0 cancela): ", 0)
        if (id == 0) return
        val faturas = faturaDao.listarPorContrato(id)
        Formato.titulo("Faturas do contrato $id")
        if (faturas.isEmpty()) {
            println("  Esse contrato nao tem fatura gerada.")
            return
        }
        faturas.forEach {
            println("  [${it.id}] ${it.competencia} venc. ${Formato.data(it.vencimento)} - " +
                    "${Formato.moeda(it.valor)} - ${it.status} " +
                    (if (it.dataPagamento != null) "em ${Formato.dataHora(it.dataPagamento)}" else ""))
        }
    }
}
