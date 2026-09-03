package br.com.provedor.util

/**
 * Tudo que entra no sistema passa por aqui antes de ir pro banco.
 * A ideia e nao confiar em nada que o usuario digita.
 */
object Validacao {

    // Nome de pessoa: acento, espaco, apostrofo e hifen (Ana Lima-Souza). Minimo 3.
    private val REGEX_NOME = Regex("^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'´`\\- ]{2,}$")

    // Razao social aceita numero e os sinais que aparecem em nome de empresa:
    // "Padaria 2 Irmaos", "J. Silva Comercio", "Costa & Filhos Ltda - ME"
    private val REGEX_RAZAO_SOCIAL = Regex("^[A-Za-zÀ-ÿ0-9][A-Za-zÀ-ÿ0-9'´`.,&/\\-_ ]{2,}$")

    // Formato basico de e-mail: alguma.coisa@dominio.algo
    private val REGEX_EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // Telefone ja sem mascara: DDD + numero (10 digitos fixo, 11 celular)
    private val REGEX_TELEFONE = Regex("^\\d{10,11}$")

    // Competencia da fatura no formato MM/AAAA
    private val REGEX_COMPETENCIA = Regex("^(0[1-9]|1[0-2])/(19|20)\\d{2}$")

    /** Tira ponto, traco, barra, parenteses e espaco. Sobra so numero. */
    fun somenteDigitos(texto: String?): String = texto?.filter { it.isDigit() } ?: ""

    fun nomeValido(nome: String?): Boolean {
        val limpo = nome?.trim() ?: return false
        return limpo.length <= 120 && REGEX_NOME.matches(limpo)
    }

    /**
     * Cliente e fornecedor podem ser pessoa fisica ou juridica, entao aqui a
     * regra e mais larga que a do nome de funcionario.
     */
    fun razaoSocialValida(nome: String?): Boolean {
        val limpo = nome?.trim() ?: return false
        return limpo.length <= 120 && REGEX_RAZAO_SOCIAL.matches(limpo)
    }

    /** E-mail e opcional no cadastro, entao nulo/vazio passa. Se preencheu, tem que estar certo. */
    fun emailValido(email: String?): Boolean {
        if (email.isNullOrBlank()) return true
        return email.length <= 120 && REGEX_EMAIL.matches(email.trim())
    }

    /** Mesma logica do e-mail: campo opcional, mas se veio preenchido tem que bater no regex. */
    fun telefoneValido(telefone: String?): Boolean {
        if (telefone.isNullOrBlank()) return true
        return REGEX_TELEFONE.matches(somenteDigitos(telefone))
    }

    fun competenciaValida(competencia: String?): Boolean =
        competencia != null && REGEX_COMPETENCIA.matches(competencia.trim())

    /**
     * CPF: 11 digitos e os dois digitos verificadores conferindo.
     * So o regex nao resolve porque 111.111.111-11 passaria no formato.
     */
    fun cpfValido(cpf: String?): Boolean {
        val numeros = somenteDigitos(cpf)
        if (numeros.length != 11) return false
        if (numeros.all { it == numeros[0] }) return false

        val digitos = numeros.map { it - '0' }
        var soma = 0
        for (i in 0..8) soma += digitos[i] * (10 - i)
        val primeiro = calculaDigito(soma)
        if (primeiro != digitos[9]) return false

        soma = 0
        for (i in 0..9) soma += digitos[i] * (11 - i)
        return calculaDigito(soma) == digitos[10]
    }

    /** Mesma coisa do CPF, so muda a tabela de pesos. */
    fun cnpjValido(cnpj: String?): Boolean {
        val numeros = somenteDigitos(cnpj)
        if (numeros.length != 14) return false
        if (numeros.all { it == numeros[0] }) return false

        val digitos = numeros.map { it - '0' }
        val pesosPrimeiro = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val pesosSegundo = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)

        var soma = 0
        for (i in pesosPrimeiro.indices) soma += digitos[i] * pesosPrimeiro[i]
        if (calculaDigito(soma) != digitos[12]) return false

        soma = 0
        for (i in pesosSegundo.indices) soma += digitos[i] * pesosSegundo[i]
        return calculaDigito(soma) == digitos[13]
    }

    /** Cliente pode ser pessoa fisica ou juridica, entao vale CPF ou CNPJ. */
    fun documentoValido(documento: String?): Boolean {
        val numeros = somenteDigitos(documento)
        return when (numeros.length) {
            11 -> cpfValido(numeros)
            14 -> cnpjValido(numeros)
            else -> false
        }
    }

    private fun calculaDigito(soma: Int): Int {
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }
}
