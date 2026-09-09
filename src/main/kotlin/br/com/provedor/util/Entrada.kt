package br.com.provedor.util

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Sobe quando o console e fechado (Ctrl+D / fim do arquivo de entrada). */
class EntradaEncerradaException : RuntimeException("Entrada do console encerrada.")

/**
 * Leitura do teclado.
 *
 * Toda funcao aqui fica presa num laco ate receber um valor valido, entao
 * quem chama nao precisa conferir nada depois. Tem duas partes:
 *
 *  - as funcoes genericas (texto, inteiro, decimal, data);
 *  - uma funcao por tipo de campo do sistema (nome, cpf, cnpj, email...),
 *    cada uma com a sua propria regra de validacao.
 *
 * Fiz uma funcao por campo de proposito. Assim quem le o menu ve
 * "Entrada.cpf(...)" e sabe na hora o que vai ser cobrado do usuario.
 */
object Entrada {

    private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Formatos de dinheiro que eu aceito, e so esses:
     *   1500            - so numero
     *   1500,50 ou 1500.50 - centavos com virgula ou ponto
     *   1.500 / 1.500,50 / 12.345.678,90 - com separador de milhar
     * Coisa ambigua tipo "1.5.5", "1,50.75" ou "10,999" e recusada de proposito:
     * melhor pedir pra digitar de novo do que adivinhar errado o valor.
     */
    private val REGEX_VALOR = Regex(
        "^\\d+$|^\\d+[.,]\\d{1,2}$|^\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?$"
    )

    private fun ler(rotulo: String): String {
        print(rotulo)
        // readlnOrNull devolve null quando a entrada acaba - por isso o nullable.
        val linha = readlnOrNull() ?: throw EntradaEncerradaException()
        return linha.trim()
    }

    // ------------------------------------------------------------------
    // TEXTO
    // ------------------------------------------------------------------

    /** Campo de texto obrigatorio, com tamanho minimo e maximo. */
    fun texto(rotulo: String, maximo: Int = 120, minimo: Int = 1): String {
        while (true) {
            val valor = ler(rotulo)
            if (valor.isBlank()) {
                println("  > Esse campo e obrigatorio.")
            } else if (valor.length < minimo) {
                println("  > Precisa ter pelo menos $minimo caracteres.")
            } else if (valor.length > maximo) {
                println("  > No maximo $maximo caracteres.")
            } else {
                return valor
            }
        }
    }

    /** Mesma coisa, mas nas telas de alteracao: enter vazio mantem o valor atual. */
    fun texto(rotulo: String, padrao: String, maximo: Int = 120, minimo: Int = 1): String {
        while (true) {
            val valor = ler("$rotulo [$padrao] (enter mantem): ")
            if (valor.isBlank()) {
                return padrao
            } else if (valor.length < minimo) {
                println("  > Precisa ter pelo menos $minimo caracteres.")
            } else if (valor.length > maximo) {
                println("  > No maximo $maximo caracteres.")
            } else {
                return valor
            }
        }
    }

    /** Campo que pode ficar em branco. Devolve null quando o usuario so aperta enter. */
    fun textoOpcional(rotulo: String, maximo: Int = 120): String? {
        while (true) {
            val valor = ler("$rotulo (enter pra deixar em branco): ")
            if (valor.isBlank()) {
                return null
            } else if (valor.length > maximo) {
                println("  > No maximo $maximo caracteres.")
            } else {
                return valor
            }
        }
    }

    // ------------------------------------------------------------------
    // CAMPOS DO SISTEMA - cada um com a sua regra
    // ------------------------------------------------------------------

    /** Nome de pessoa fisica: so letra, espaco, apostrofo e hifen. */
    fun nome(rotulo: String): String {
        while (true) {
            val valor = texto(rotulo, 120)
            if (Validacao.nomeValido(valor)) {
                return valor
            }
            println("  > Nome invalido (so letras e espaco, minimo 3).")
        }
    }

    fun nome(rotulo: String, padrao: String): String {
        while (true) {
            val valor = texto(rotulo, padrao, 120)
            if (Validacao.nomeValido(valor)) {
                return valor
            }
            println("  > Nome invalido (so letras e espaco, minimo 3).")
        }
    }

    /** Nome de cliente ou fornecedor: aceita numero, ponto, hifen e & (empresa). */
    fun razaoSocial(rotulo: String): String {
        while (true) {
            val valor = texto(rotulo, 120)
            if (Validacao.razaoSocialValida(valor)) {
                return valor
            }
            println("  > Nome invalido (minimo 3 caracteres).")
        }
    }

    fun razaoSocial(rotulo: String, padrao: String): String {
        while (true) {
            val valor = texto(rotulo, padrao, 120)
            if (Validacao.razaoSocialValida(valor)) {
                return valor
            }
            println("  > Nome invalido (minimo 3 caracteres).")
        }
    }

    /** CPF. Devolve so os 11 digitos, sem ponto nem traco. */
    fun cpf(rotulo: String): String {
        while (true) {
            val valor = texto(rotulo, 18)
            if (Validacao.cpfValido(valor)) {
                return Validacao.somenteDigitos(valor)
            }
            println("  > CPF invalido - confere os digitos.")
        }
    }

    /** CNPJ. Devolve so os 14 digitos. */
    fun cnpj(rotulo: String): String {
        while (true) {
            val valor = texto(rotulo, 20)
            if (Validacao.cnpjValido(valor)) {
                return Validacao.somenteDigitos(valor)
            }
            println("  > CNPJ invalido - confere os digitos.")
        }
    }

    /** Cliente pode ser pessoa fisica ou juridica, entao aceita CPF ou CNPJ. */
    fun documento(rotulo: String): String {
        while (true) {
            val valor = texto(rotulo, 20)
            if (Validacao.documentoValido(valor)) {
                return Validacao.somenteDigitos(valor)
            }
            println("  > Documento invalido - confere os digitos.")
        }
    }

    /** E-mail e opcional. Se veio preenchido, tem que estar no formato certo. */
    fun emailOpcional(rotulo: String): String? {
        while (true) {
            val valor = textoOpcional(rotulo, 120)
            if (valor == null) {
                return null
            }
            if (Validacao.emailValido(valor)) {
                return valor
            }
            println("  > E-mail fora do formato nome@dominio.com.")
        }
    }

    /** Telefone tambem e opcional. Devolve so os digitos. */
    fun telefoneOpcional(rotulo: String): String? {
        while (true) {
            val valor = textoOpcional(rotulo, 20)
            if (valor == null) {
                return null
            }
            if (Validacao.telefoneValido(valor)) {
                return Validacao.somenteDigitos(valor)
            }
            println("  > Telefone precisa ter 10 ou 11 numeros.")
        }
    }

    /** Competencia da fatura ou da folha, no formato MM/AAAA. */
    fun competencia(rotulo: String): String {
        while (true) {
            val valor = texto(rotulo, 7)
            if (Validacao.competenciaValida(valor)) {
                return valor
            }
            println("  > Use o formato MM/AAAA, exemplo 09/2026.")
        }
    }

    // ------------------------------------------------------------------
    // NUMEROS E DATAS
    // ------------------------------------------------------------------

    fun inteiro(rotulo: String, minimo: Int = Int.MIN_VALUE, maximo: Int = Int.MAX_VALUE): Int {
        while (true) {
            val numero = ler(rotulo).toIntOrNull()
            if (numero == null) {
                println("  > Digita um numero inteiro.")
            } else if (numero < minimo || numero > maximo) {
                println("  > Precisa estar entre $minimo e $maximo.")
            } else {
                return numero
            }
        }
    }

    /** Versao pra tela de alteracao: enter vazio mantem o valor atual. */
    fun inteiro(rotulo: String, padrao: Int, minimo: Int, maximo: Int): Int {
        while (true) {
            val bruto = ler("$rotulo [$padrao] (enter mantem): ")
            if (bruto.isBlank()) {
                return padrao
            }
            val numero = bruto.toIntOrNull()
            if (numero == null) {
                println("  > Digita um numero inteiro.")
            } else if (numero < minimo || numero > maximo) {
                println("  > Precisa estar entre $minimo e $maximo.")
            } else {
                return numero
            }
        }
    }

    fun decimal(rotulo: String, minimo: BigDecimal = BigDecimal.ZERO): BigDecimal {
        while (true) {
            val valor = converterValor(ler(rotulo))
            if (valor == null) {
                println("  > Valor invalido (exemplo: 129,90).")
            } else if (valor < minimo) {
                println("  > O valor nao pode ser menor que $minimo.")
            } else {
                return valor
            }
        }
    }

    /**
     * Versao pra tela de alteracao. Sem isso o operador que so queria arrumar
     * o nome era obrigado a redigitar o salario, e um dedo torto ali mexia
     * na folha sem ninguem perceber.
     */
    fun decimal(rotulo: String, padrao: BigDecimal, minimo: BigDecimal = BigDecimal.ZERO): BigDecimal {
        while (true) {
            val bruto = ler("$rotulo [${Formato.moeda(padrao)}] (enter mantem): ")
            if (bruto.isBlank()) {
                return padrao
            }
            val valor = converterValor(bruto)
            if (valor == null) {
                println("  > Valor invalido (exemplo: 129,90).")
            } else if (valor < minimo) {
                println("  > O valor nao pode ser menor que $minimo.")
            } else {
                return valor
            }
        }
    }

    /**
     * Transforma o que foi digitado em valor de dinheiro, ou devolve null se
     * nao der. A regra e olhar o ultimo separador: se sobrar 1 ou 2 digitos
     * depois dele, ele e a virgula dos centavos; senao e separador de milhar.
     */
    fun converterValor(bruto: String): BigDecimal? {
        val texto = bruto.trim().replace(" ", "").replace("R$", "")
        if (!REGEX_VALOR.matches(texto)) {
            return null
        }

        val posicaoSeparador = maxOf(texto.lastIndexOf('.'), texto.lastIndexOf(','))
        val casasDepois = texto.length - posicaoSeparador - 1

        val normalizado: String
        if (posicaoSeparador < 0) {
            normalizado = texto
        } else if (casasDepois == 1 || casasDepois == 2) {
            val parteInteira = texto.substring(0, posicaoSeparador).filter { it.isDigit() }
            val centavos = texto.substring(posicaoSeparador + 1)
            normalizado = "$parteInteira.$centavos"
        } else {
            normalizado = texto.filter { it.isDigit() }
        }

        try {
            return BigDecimal(normalizado).setScale(2, java.math.RoundingMode.HALF_UP)
        } catch (naoEhNumero: NumberFormatException) {
            return null
        }
    }

    fun data(rotulo: String, padrao: LocalDate? = null): LocalDate {
        while (true) {
            val sufixo = if (padrao != null) " [enter = ${Formato.data(padrao)}]: " else " (dd/mm/aaaa): "
            val bruto = ler(rotulo + sufixo)
            if (bruto.isBlank() && padrao != null) {
                return padrao
            }
            try {
                return LocalDate.parse(bruto, FORMATO_DATA)
            } catch (dataInvalida: Exception) {
                println("  > Data invalida. Usa o formato dd/mm/aaaa.")
            }
        }
    }

    // ------------------------------------------------------------------
    // MENU
    // ------------------------------------------------------------------

    fun opcao(minimo: Int, maximo: Int): Int = inteiro("Opcao: ", minimo, maximo)

    fun confirmar(pergunta: String): Boolean {
        while (true) {
            val resposta = ler("$pergunta (s/n): ").lowercase()
            if (resposta == "s" || resposta == "sim") {
                return true
            } else if (resposta == "n" || resposta == "nao" || resposta == "não") {
                return false
            } else {
                println("  > Responde com s ou n.")
            }
        }
    }

    fun pausar() {
        print("\nEnter pra voltar...")
        readlnOrNull()
    }
}
