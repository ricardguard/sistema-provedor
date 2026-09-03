package br.com.provedor.menu

import br.com.provedor.dao.ClienteDao
import br.com.provedor.dao.FornecedorDao
import br.com.provedor.dao.FuncionarioDao
import br.com.provedor.dao.SetorDao
import br.com.provedor.modelo.Cliente
import br.com.provedor.modelo.Fornecedor
import br.com.provedor.modelo.Funcionario
import br.com.provedor.modelo.Setor
import br.com.provedor.servico.RegraDeNegocioException
import br.com.provedor.util.Entrada
import br.com.provedor.util.Formato
import br.com.provedor.util.Validacao
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
        val nome = Entrada.texto(
            "Nome do setor: ", 60,
            { it.length >= 3 }, "O nome precisa de pelo menos 3 letras."
        )
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
        val nome = Entrada.texto("Novo nome [${setor.nome}]: ", 60, { it.length >= 3 })
        val descricao = Entrada.textoOpcional("Nova descricao", 200)
        setorDao.atualizar(setor.copy(nome = nome, descricao = descricao ?: setor.descricao))
        println("\n  Setor atualizado.")
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
        println("  COD NOME                       CARGO             SETOR           SALARIO")
        lista.forEach {
            val marca = if (it.ativo) " " else "*"
            println(
                "  ${it.id.toString().padStart(3)}$marca${Formato.encurtar(it.nome, 26)} " +
                        "${Formato.encurtar(it.cargo, 17)} ${Formato.encurtar(it.setorNome, 15)} " +
                        Formato.moeda(it.salario)
            )
        }
        println("\n  (* = desligado)")
    }

    private fun cadastrarFuncionario() {
        Formato.titulo("Novo funcionario")
        val setores = setorDao.listar()
        if (setores.isEmpty()) throw RegraDeNegocioException("Cadastre um setor antes.")

        val nome = Entrada.texto(
            "Nome completo: ", 120,
            { Validacao.nomeValido(it) }, "Nome invalido (so letras e espaco, minimo 3)."
        )
        val cpf = Entrada.texto(
            "CPF: ", 18,
            { Validacao.cpfValido(it) }, "CPF invalido - confere os digitos."
        ).let { Validacao.somenteDigitos(it) }

        if (funcionarioDao.existeCpf(cpf)) {
            throw RegraDeNegocioException("Ja tem funcionario cadastrado com esse CPF.")
        }

        val email = Entrada.textoOpcional(
            "E-mail", 120,
            { Validacao.emailValido(it) }, "E-mail fora do formato nome@dominio.com."
        )
        val telefone = Entrada.textoOpcional(
            "Telefone com DDD", 20,
            { Validacao.telefoneValido(it) }, "Telefone precisa ter 10 ou 11 numeros."
        )?.let { Validacao.somenteDigitos(it) }

        val cargo = Entrada.texto("Cargo: ", 60, { it.length >= 3 })
        val salario = Entrada.decimal("Salario: ", BigDecimal("1.00"))

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
                cargo = cargo, salario = salario, setorId = setor.id, dataAdmissao = admissao
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

        val nome = Entrada.texto("Nome [${func.nome}]: ", 120, { Validacao.nomeValido(it) })
        val email = Entrada.textoOpcional("E-mail", 120, { Validacao.emailValido(it) })
        val telefone = Entrada.textoOpcional("Telefone", 20, { Validacao.telefoneValido(it) })
            ?.let { Validacao.somenteDigitos(it) }
        val cargo = Entrada.texto("Cargo [${func.cargo}]: ", 60, { it.length >= 3 })
        val salario = Entrada.decimal("Salario [${Formato.moeda(func.salario)}]: ", BigDecimal("1.00"))

        setorDao.listar().forEach { println("   [${it.id}] ${it.nome}") }
        val setorId = Entrada.inteiro("Setor [${func.setorId}]: ", 1)
        setorDao.buscarPorId(setorId) ?: throw RegraDeNegocioException("Setor nao encontrado.")

        funcionarioDao.atualizar(
            func.copy(
                nome = nome,
                email = email ?: func.email,
                telefone = telefone ?: func.telefone,
                cargo = cargo,
                salario = salario,
                setorId = setorId
            )
        )
        println("\n  Cadastro atualizado.")
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
        val nome = Entrada.texto(
            "Nome / razao social: ", 120,
            { Validacao.nomeValido(it) }, "Nome invalido."
        )
        val documento = Entrada.texto(
            "CPF ou CNPJ: ", 20,
            { Validacao.documentoValido(it) }, "Documento invalido - confere os digitos."
        ).let { Validacao.somenteDigitos(it) }

        if (clienteDao.existeDocumento(documento)) {
            throw RegraDeNegocioException("Ja existe cliente com esse documento.")
        }

        val email = Entrada.textoOpcional("E-mail", 120, { Validacao.emailValido(it) })
        val telefone = Entrada.textoOpcional("Telefone com DDD", 20, { Validacao.telefoneValido(it) })
            ?.let { Validacao.somenteDigitos(it) }
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
        val termo = Entrada.texto("Nome ou documento: ", 120, { it.length >= 2 })
        listarClientes(clienteDao.buscarPorNomeOuDocumento(termo))
    }

    private fun alterarCliente() {
        listarClientes(clienteDao.listar())
        val id = Entrada.inteiro("Codigo do cliente (0 cancela): ", 0)
        if (id == 0) return
        val cliente = clienteDao.buscarPorId(id)
            ?: throw RegraDeNegocioException("Cliente nao encontrado.")

        val nome = Entrada.texto("Nome [${cliente.nome}]: ", 120, { Validacao.nomeValido(it) })
        val email = Entrada.textoOpcional("E-mail", 120, { Validacao.emailValido(it) })
        val telefone = Entrada.textoOpcional("Telefone", 20, { Validacao.telefoneValido(it) })
            ?.let { Validacao.somenteDigitos(it) }
        val endereco = Entrada.textoOpcional("Endereco", 150)
        val cidade = Entrada.textoOpcional("Cidade", 60)

        clienteDao.atualizar(
            cliente.copy(
                nome = nome,
                email = email ?: cliente.email,
                telefone = telefone ?: cliente.telefone,
                endereco = endereco ?: cliente.endereco,
                cidade = cidade ?: cliente.cidade
            )
        )
        println("\n  Cadastro atualizado.")
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
        val razao = Entrada.texto("Razao social: ", 120, { it.length >= 3 })
        val cnpj = Entrada.texto(
            "CNPJ: ", 20,
            { Validacao.cnpjValido(it) }, "CNPJ invalido - confere os digitos."
        ).let { Validacao.somenteDigitos(it) }

        if (fornecedorDao.existeCnpj(cnpj)) {
            throw RegraDeNegocioException("Esse CNPJ ja esta cadastrado.")
        }

        val email = Entrada.textoOpcional("E-mail", 120, { Validacao.emailValido(it) })
        val telefone = Entrada.textoOpcional("Telefone com DDD", 20, { Validacao.telefoneValido(it) })
            ?.let { Validacao.somenteDigitos(it) }

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

        val razao = Entrada.texto("Razao social [${fornecedor.razaoSocial}]: ", 120, { it.length >= 3 })
        val email = Entrada.textoOpcional("E-mail", 120, { Validacao.emailValido(it) })
        val telefone = Entrada.textoOpcional("Telefone", 20, { Validacao.telefoneValido(it) })
            ?.let { Validacao.somenteDigitos(it) }

        fornecedorDao.atualizar(
            fornecedor.copy(
                razaoSocial = razao,
                email = email ?: fornecedor.email,
                telefone = telefone ?: fornecedor.telefone
            )
        )
        println("\n  Cadastro atualizado.")
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
