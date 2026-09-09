package br.com.provedor.menu

import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.FornecedorDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.SetorDao
import br.com.provedor.modelo.Cliente
import br.com.provedor.modelo.Contratacao
import br.com.provedor.modelo.Fornecedor
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Setor
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import java.math.BigDecimal
import java.time.LocalDate

/** Cadastro das pessoas e dos setores da empresa. */
object MenuCadastros {

    private val setorDao = SetorDao()
    private val funcionarioDao = FuncionarioDao()
    private val clienteDao = ClienteDao()
    private val fornecedorDao = FornecedorDao()

    fun exibir() {
        while (true) {
            Formato.titulo("Cadastros")
            println("  1 - Setores")
            println("  2 - Funcionarios")
            println("  3 - Clientes")
            println("  4 - Fornecedores")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 4)) {
                1 -> menuSetores()
                2 -> menuFuncionarios()
                3 -> menuClientes()
                4 -> menuFornecedores()
                0 -> return
            }
        }
    }

    // ------------------------------------------------------------ setores

    private fun menuSetores() {
        while (true) {
            Formato.titulo("Setores")
            println("  1 - Listar")
            println("  2 - Cadastrar")
            println("  3 - Alterar")
            println("  4 - Excluir")
            println("  5 - Ver equipe do setor")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 5)) {
                1 -> protegido { listarSetores() }
                2 -> protegido { cadastrarSetor() }
                3 -> protegido { alterarSetor() }
                4 -> protegido { excluirSetor() }
                5 -> protegido { equipeDoSetor() }
                0 -> return
            }
        }
    }

    private fun listarSetores() {
        val setores = setorDao.listar()
        Formato.titulo("Setores cadastrados")
        if (setores.isEmpty()) {
            println("  Nenhum setor cadastrado.")
            return
        }
        setores.forEach {
            println("  [${it.id}] ${Formato.encurtar(it.nome, 20)} ${it.descricao ?: "-"}")
        }
    }

    private fun cadastrarSetor() {
        Formato.titulo("Novo setor")
        val nome = Entrada.texto("Nome do setor: ", 60, 3)
        if (setorDao.listar().any { it.nome.equals(nome, ignoreCase = true) }) {
            throw RegraDeNegocioException("Ja existe um setor com esse nome.")
        }
        val descricao = Entrada.textoOpcional("Descricao", 200)
        val id = setorDao.inserir(Setor(nome = nome, descricao = descricao))
        println("\n  Setor cadastrado com o codigo $id.")
    }

    private fun alterarSetor() {
        listarSetores()
        val setor = selecionarSetor() ?: return
        val nome = Entrada.texto("Nome do setor", setor.nome, 60, 3)
        val repetido = setorDao.listar().any { it.id != setor.id && it.nome.equals(nome, ignoreCase = true) }
        if (repetido) throw RegraDeNegocioException("Ja existe outro setor com esse nome.")

        val descricao = Entrada.opcionalOuManter("Descricao", setor.descricao, 200)
        val ok = setorDao.atualizar(setor.copy(nome = nome, descricao = descricao))
        println(if (ok) "\n  Setor atualizado." else "\n  Nada foi alterado, confere o codigo.")
    }

    private fun excluirSetor() {
        listarSetores()
        val setor = selecionarSetor() ?: return
        val equipe = setorDao.contarFuncionarios(setor.id)
        if (equipe > 0) {
            throw RegraDeNegocioException("O setor ${setor.nome} tem $equipe funcionario(s), remaneje antes.")
        }
        if (Entrada.confirmar("Confirma excluir o setor ${setor.nome}?")) {
            setorDao.excluir(setor.id)
            println("\n  Setor excluido.")
        }
    }

    private fun equipeDoSetor() {
        listarSetores()
        val setor = selecionarSetor() ?: return
        val equipe = funcionarioDao.listarPorSetor(setor.id)
        Formato.titulo("Equipe do setor ${setor.nome}")
        if (equipe.isEmpty()) {
            println("  Setor sem funcionarios.")
            return
        }
        equipe.forEach {
            val situacao = if (it.ativo) "ativo" else "desligado"
            println("  [${it.id}] ${Formato.encurtar(it.nome, 26)} ${Formato.encurtar(it.cargo, 18)} $situacao")
        }
    }

    private fun selecionarSetor(): Setor? {
        val id = Entrada.inteiro("Codigo do setor (0 cancela): ", 0)
        if (id == 0) return null
        return setorDao.buscarPorId(id) ?: throw RegraDeNegocioException("Setor $id nao existe.")
    }

    // ------------------------------------------------------- funcionarios

    private fun menuFuncionarios() {
        while (true) {
            Formato.titulo("Funcionarios")
            println("  1 - Listar todos")
            println("  2 - Cadastrar")
            println("  3 - Alterar dados")
            println("  4 - Desligar / reativar")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 4)) {
                1 -> protegido { listarFuncionarios() }
                2 -> protegido { cadastrarFuncionario() }
                3 -> protegido { alterarFuncionario() }
                4 -> protegido { alterarSituacaoFuncionario() }
                0 -> return
            }
        }
    }

    private fun listarFuncionarios() {
        val lista = funcionarioDao.listar()
        Formato.titulo("Funcionarios")
        if (lista.isEmpty()) {
            println("  Nenhum funcionario cadastrado.")
            return
        }
        println("  COD NOME                     CARGO           SETOR         REGIME     SALARIO")
        lista.forEach {
            val marca = if (it.ativo) " " else "*"
            println(
                "  ${it.id.toString().padStart(3)}$marca${Formato.encurtar(it.nome, 24)} " +
                        "${Formato.encurtar(it.cargo, 15)} ${Formato.encurtar(it.setorNome, 13)} " +
                        "${Formato.encurtar(it.contratacao.rotulo, 10)} " +
                        Formato.moeda(it.salario)
            )
        }
        println("\n  (* = desligado)")
    }

    private fun cadastrarFuncionario() {
        Formato.titulo("Novo funcionario")
        val setores = setorDao.listar()
        if (setores.isEmpty()) throw RegraDeNegocioException("Cadastre um setor antes.")

        val nome = Entrada.nome("Nome completo: ")
        val cpf = Entrada.cpf("CPF: ")

        if (funcionarioDao.existeCpf(cpf)) {
            throw RegraDeNegocioException("Ja tem funcionario cadastrado com esse CPF.")
        }

        val email = Entrada.emailOpcional("E-mail")
        val telefone = Entrada.telefoneOpcional("Telefone com DDD")

        val cargo = Entrada.texto("Cargo: ", 60, 3)
        val contratacao = escolherContratacao()
        val salario = Entrada.decimal("Salario / bolsa / valor da nota: ", BigDecimal("1.00"))

        println("\n  Setores:")
        setores.forEach { println("   [${it.id}] ${it.nome}") }
        val setorId = Entrada.inteiro("Codigo do setor: ", 1)
        val setor = setorDao.buscarPorId(setorId) ?: throw RegraDeNegocioException("Setor nao encontrado.")

        val admissao = Entrada.data("Data de admissao", LocalDate.now())
        if (admissao.isAfter(LocalDate.now())) {
            throw RegraDeNegocioException("Data de admissao no futuro nao rola.")
        }

        val id = funcionarioDao.inserir(
            Funcionario(
                nome = nome, cpf = cpf, email = email, telefone = telefone,
                cargo = cargo, salario = salario, setorId = setor.id,
                contratacao = contratacao, dataAdmissao = admissao
            )
        )
        println("\n  Funcionario cadastrado com o codigo $id no setor ${setor.nome}.")
    }

    private fun alterarFuncionario() {
        listarFuncionarios()
        val id = Entrada.inteiro("Codigo do funcionario (0 cancela): ", 0)
        if (id == 0) return
        val func = funcionarioDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Funcionario nao encontrado.")

        val nome = Entrada.nome("Nome", func.nome)
        val email = Entrada.emailOuManter("E-mail", func.email)
        val telefone = Entrada.telefoneOuManter("Telefone", func.telefone)
        val cargo = Entrada.texto("Cargo", func.cargo, 60, 3)
        println("  Regime atual: ${func.contratacao.rotulo}")
        val contratacao = escolherContratacao()
        val salario = Entrada.decimalOuManter("Salario / bolsa / valor da nota", func.salario, BigDecimal("1.00"))

        setorDao.listar().forEach { println("   [${it.id}] ${it.nome}") }
        val setorId = Entrada.inteiro("Setor", func.setorId, 1, Int.MAX_VALUE)
        setorDao.buscarPorId(setorId) ?: throw RegraDeNegocioException("Setor nao encontrado.")

        val ok = funcionarioDao.atualizar(
            func.copy(
                nome = nome,
                email = email,
                telefone = telefone,
                cargo = cargo,
                salario = salario,
                setorId = setorId,
                contratacao = contratacao
            )
        )
        println(if (ok) "\n  Cadastro atualizado." else "\n  Nada foi alterado, confere o codigo.")
    }

    /**
     * Cada regime calcula o pagamento de um jeito, por isso ele e escolhido
     * no cadastro e nao fica so como texto solto.
     */
    private fun escolherContratacao(): Contratacao {
        println("\n  Regime de contratacao:")
        Contratacao.todas.forEachIndexed { indice, regime ->
            println("   ${indice + 1} - ${regime.rotulo}")
        }
        val escolha = Entrada.inteiro("Regime: ", 1, Contratacao.todas.size)
        return Contratacao.todas[escolha - 1]
    }

    private fun alterarSituacaoFuncionario() {
        listarFuncionarios()
        val id = Entrada.inteiro("Codigo do funcionario (0 cancela): ", 0)
        if (id == 0) return
        val func = funcionarioDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Funcionario nao encontrado.")

        if (func.id == Sessao.logado.id && func.ativo) {
            throw RegraDeNegocioException("Voce nao pode desligar o proprio usuario logado.")
        }

        val novaSituacao = !func.ativo
        val texto = if (novaSituacao) "reativar" else "desligar"
        if (Entrada.confirmar("Confirma $texto ${func.nome}?")) {
            funcionarioDao.alterarSituacao(func.id, novaSituacao)
            println("\n  Cadastro de ${func.nome} atualizado.")
        }
    }

    // ----------------------------------------------------------- clientes

    private fun menuClientes() {
        while (true) {
            Formato.titulo("Clientes")
            println("  1 - Listar")
            println("  2 - Cadastrar")
            println("  3 - Buscar por nome/documento")
            println("  4 - Alterar dados")
            println("  5 - Ativar / inativar")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 5)) {
                1 -> protegido { listarClientes(clienteDao.listar()) }
                2 -> protegido { cadastrarCliente() }
                3 -> protegido { buscarCliente() }
                4 -> protegido { alterarCliente() }
                5 -> protegido { alterarSituacaoCliente() }
                0 -> return
            }
        }
    }

    private fun listarClientes(lista: List<Cliente>) {
        Formato.titulo("Clientes")
        if (lista.isEmpty()) {
            println("  Nenhum cliente encontrado.")
            return
        }
        println("  COD NOME                       DOCUMENTO           CIDADE")
        lista.forEach {
            val marca = if (it.ativo) " " else "*"
            println(
                "  ${it.id.toString().padStart(3)}$marca${Formato.encurtar(it.nome, 26)} " +
                        "${Formato.encurtar(Formato.documento(it.cpfCnpj), 19)} ${it.cidade ?: "-"}"
            )
        }
        println("\n  (* = inativo)")
    }

    private fun cadastrarCliente() {
        Formato.titulo("Novo cliente")
        val nome = Entrada.razaoSocial("Nome / razao social: ")
        val documento = Entrada.documento("CPF ou CNPJ: ")

        if (clienteDao.existeDocumento(documento)) {
            throw RegraDeNegocioException("Ja existe cliente com esse documento.")
        }

        val email = Entrada.emailOpcional("E-mail")
        val telefone = Entrada.telefoneOpcional("Telefone com DDD")
        val endereco = Entrada.textoOpcional("Endereco", 150)
        val cidade = Entrada.textoOpcional("Cidade", 60)

        val id = clienteDao.inserir(
            Cliente(
                nome = nome, cpfCnpj = documento, email = email,
                telefone = telefone, endereco = endereco, cidade = cidade
            )
        )
        println("\n  Cliente cadastrado com o codigo $id.")
    }

    private fun buscarCliente() {
        val termo = Entrada.texto("Nome ou documento: ", 120, 2)
        listarClientes(clienteDao.buscarPorNomeOuDocumento(termo))
    }

    private fun alterarCliente() {
        listarClientes(clienteDao.listar())
        val id = Entrada.inteiro("Codigo do cliente (0 cancela): ", 0)
        if (id == 0) return
        val cliente = clienteDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Cliente nao encontrado.")

        val nome = Entrada.razaoSocial("Nome", cliente.nome)
        val email = Entrada.emailOuManter("E-mail", cliente.email)
        val telefone = Entrada.telefoneOuManter("Telefone", cliente.telefone)
        val endereco = Entrada.opcionalOuManter("Endereco", cliente.endereco, 150)
        val cidade = Entrada.opcionalOuManter("Cidade", cliente.cidade, 60)

        val ok = clienteDao.atualizar(
            cliente.copy(
                nome = nome,
                email = email,
                telefone = telefone,
                endereco = endereco,
                cidade = cidade
            )
        )
        println(if (ok) "\n  Cadastro atualizado." else "\n  Nada foi alterado, confere o codigo.")
    }

    private fun alterarSituacaoCliente() {
        listarClientes(clienteDao.listar())
        val id = Entrada.inteiro("Codigo do cliente (0 cancela): ", 0)
        if (id == 0) return
        val cliente = clienteDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Cliente nao encontrado.")

        val nova = !cliente.ativo
        if (Entrada.confirmar("Confirma ${if (nova) "ativar" else "inativar"} ${cliente.nome}?")) {
            clienteDao.alterarSituacao(cliente.id, nova)
            println("\n  Cadastro atualizado.")
        }
    }

    // -------------------------------------------------------- fornecedores

    private fun menuFornecedores() {
        while (true) {
            Formato.titulo("Fornecedores")
            println("  1 - Listar")
            println("  2 - Cadastrar")
            println("  3 - Alterar dados")
            println("  4 - Ativar / inativar")
            println("  0 - Voltar")
            println(Formato.linha())

            when (Entrada.opcao(0, 4)) {
                1 -> protegido { listarFornecedores() }
                2 -> protegido { cadastrarFornecedor() }
                3 -> protegido { alterarFornecedor() }
                4 -> protegido { alterarSituacaoFornecedor() }
                0 -> return
            }
        }
    }

    private fun listarFornecedores() {
        val lista = fornecedorDao.listar()
        Formato.titulo("Fornecedores")
        if (lista.isEmpty()) {
            println("  Nenhum fornecedor cadastrado.")
            return
        }
        lista.forEach {
            val marca = if (it.ativo) " " else "*"
            println(
                "  [${it.id}]$marca${Formato.encurtar(it.razaoSocial, 30)} " +
                        "${Formato.documento(it.cnpj)}  ${Formato.telefone(it.telefone)}"
            )
        }
        println("\n  (* = inativo)")
    }

    private fun cadastrarFornecedor() {
        Formato.titulo("Novo fornecedor")
        val razao = Entrada.razaoSocial("Razao social: ")
        val cnpj = Entrada.cnpj("CNPJ: ")

        if (fornecedorDao.existeCnpj(cnpj)) {
            throw RegraDeNegocioException("Esse CNPJ ja esta cadastrado.")
        }

        val email = Entrada.emailOpcional("E-mail")
        val telefone = Entrada.telefoneOpcional("Telefone com DDD")

        val id = fornecedorDao.inserir(
            Fornecedor(razaoSocial = razao, cnpj = cnpj, email = email, telefone = telefone)
        )
        println("\n  Fornecedor cadastrado com o codigo $id.")
    }

    private fun alterarFornecedor() {
        listarFornecedores()
        val id = Entrada.inteiro("Codigo do fornecedor (0 cancela): ", 0)
        if (id == 0) return
        val fornecedor = fornecedorDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Fornecedor nao encontrado.")

        val razao = Entrada.razaoSocial("Razao social", fornecedor.razaoSocial)
        val email = Entrada.emailOuManter("E-mail", fornecedor.email)
        val telefone = Entrada.telefoneOuManter("Telefone", fornecedor.telefone)

        val ok = fornecedorDao.atualizar(
            fornecedor.copy(
                razaoSocial = razao,
                email = email,
                telefone = telefone
            )
        )
        println(if (ok) "\n  Cadastro atualizado." else "\n  Nada foi alterado, confere o codigo.")
    }

    private fun alterarSituacaoFornecedor() {
        listarFornecedores()
        val id = Entrada.inteiro("Codigo do fornecedor (0 cancela): ", 0)
        if (id == 0) return
        val fornecedor = fornecedorDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Fornecedor nao encontrado.")

        val nova = !fornecedor.ativo
        if (Entrada.confirmar("Confirma ${if (nova) "ativar" else "inativar"} ${fornecedor.razaoSocial}?")) {
            fornecedorDao.alterarSituacao(fornecedor.id, nova)
            println("\n  Cadastro atualizado.")
        }
    }
}
