package br.com.provedor.util

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Sobe quando o console e fechado (Ctrl+D / fim do arquivo de entrada). */
class EntradaEncerradaException : RuntimeException("Entrada do console encerrada.")

/**
 * Leitura do teclado. Toda funcao aqui fica presa em loop ate receber
 * um valor valido, entao quem chama nao precisa ficar tratando lixo.
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

    fun texto(rotulo: String, maximo: Int = 120, validador: ((String) -> Boolean)? = null,
              mensagemErro: String = "Valor invalido, tenta de novo."): String {
        while (true) {
            val valor = ler(rotulo)
            when {
                valor.isBlank() -> println("  > Esse campo e obrigatorio.")
                valor.length > maximo -> println("  > No maximo $maximo caracteres.")
                validador != null && !validador(valor) -> println("  > $mensagemErro")
                else -> return valor
            }
        }
    }

    /** Campos que podem ficar em branco no banco voltam como null mesmo. */
    fun textoOpcional(rotulo: String, maximo: Int = 120, validador: ((String?) -> Boolean)? = null,
                      mensagemErro: String = "Valor invalido."): String? {
        while (true) {
            val valor = ler("$rotulo (enter pra deixar em branco): ")
            if (valor.isBlank()) return null
            when {
                valor.length > maximo -> println("  > No maximo $maximo caracteres.")
                validador != null && !validador(valor) -> println("  > $mensagemErro")
                else -> return valor
            }
        }
    }

    fun inteiro(rotulo: String, minimo: Int = Int.MIN_VALUE, maximo: Int = Int.MAX_VALUE): Int {
        while (true) {
            val bruto = ler(rotulo)
            val numero = bruto.toIntOrNull()
            when {
                numero == null -> println("  > Digita um numero inteiro.")
                numero < minimo || numero > maximo -> println("  > Precisa estar entre $minimo e $maximo.")
                else -> return numero
            }
        }
    }

    /**
     * Aceita 1500,50 / 1500.50 / 1.500,50 / 1500 - o pessoal digita de todo jeito.
     * A regra e olhar o ultimo separador: se sobrar 1 ou 2 digitos depois dele,
     * ele e a virgula dos centavos; senao e separador de milhar.
     */
    fun decimal(rotulo: String, minimo: BigDecimal = BigDecimal.ZERO): BigDecimal {
        while (true) {
            val valor = converterValor(ler(rotulo))
            when {
                valor == null -> println("  > Valor invalido (exemplo: 129,90).")
                valor < minimo -> println("  > O valor nao pode ser menor que $minimo.")
                else -> return valor
            }
        }
    }

    /**
     * Versao pra tela de alteracao: enter vazio mantem o valor que ja esta la.
     * Sem isso o operador que so queria arrumar o nome era obrigado a redigitar
     * o salario, e um dedo torto ali mexia na folha sem ninguem perceber.
     */
    fun decimal(rotulo: String, padrao: BigDecimal, minimo: BigDecimal = BigDecimal.ZERO): BigDecimal {
        while (true) {
            val bruto = ler("$rotulo [${Formato.moeda(padrao)}] (enter mantem): ")
            if (bruto.isBlank()) return padrao
            val valor = converterValor(bruto)
            when {
                valor == null -> println("  > Valor invalido (exemplo: 129,90).")
                valor < minimo -> println("  > O valor nao pode ser menor que $minimo.")
                else -> return valor
            }
        }
    }

    /** Mesma ideia do decimal com padrao, so que pra texto. */
    fun texto(rotulo: String, padrao: String, maximo: Int = 120,
              validador: ((String) -> Boolean)? = null,
              mensagemErro: String = "Valor invalido, tenta de novo."): String {
        while (true) {
            val valor = ler("$rotulo [$padrao] (enter mantem): ")
            if (valor.isBlank()) return padrao
            when {
                valor.length > maximo -> println("  > No maximo $maximo caracteres.")
                validador != null && !validador(valor) -> println("  > $mensagemErro")
                else -> return valor
            }
        }
    }

    fun inteiro(rotulo: String, padrao: Int, minimo: Int, maximo: Int): Int {
        while (true) {
            val bruto = ler("$rotulo [$padrao] (enter mantem): ")
            if (bruto.isBlank()) return padrao
            val numero = bruto.toIntOrNull()
            when {
                numero == null -> println("  > Digita um numero inteiro.")
                numero < minimo || numero > maximo -> println("  > Precisa estar entre $minimo e $maximo.")
                else -> return numero
            }
        }
    }

    /** Devolve null quando o texto nao e um valor monetario valido. */
    fun converterValor(bruto: String): BigDecimal? {
        val texto = bruto.trim().replace(" ", "").replace("R$", "")
        if (!REGEX_VALOR.matches(texto)) return null

        val posicaoSeparador = maxOf(texto.lastIndexOf('.'), texto.lastIndexOf(','))
        val casasDepois = texto.length - posicaoSeparador - 1

        val normalizado = when {
            posicaoSeparador < 0 -> texto
            casasDepois in 1..2 ->
                texto.substring(0, posicaoSeparador).filter { it.isDigit() } + "." + texto.substring(posicaoSeparador + 1)
            else -> texto.filter { it.isDigit() }
        }

        return try {
            BigDecimal(normalizado).setScale(2, java.math.RoundingMode.HALF_UP)
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun data(rotulo: String, padrao: LocalDate? = null): LocalDate {
        while (true) {
            val sufixo = if (padrao != null) " [enter = ${Formato.data(padrao)}]: " else " (dd/mm/aaaa): "
            val bruto = ler(rotulo + sufixo)
            if (bruto.isBlank() && padrao != null) return padrao
            try {
                return LocalDate.parse(bruto, FORMATO_DATA)
            } catch (e: Exception) {
                println("  > Data invalida. Usa o formato dd/mm/aaaa.")
            }
        }
    }

    fun opcao(minimo: Int, maximo: Int): Int = inteiro("Opcao: ", minimo, maximo)

    fun confirmar(pergunta: String): Boolean {
        while (true) {
            when (ler("$pergunta (s/n): ").lowercase()) {
                "s", "sim" -> return true
                "n", "nao", "não" -> return false
                else -> println("  > Responde com s ou n.")
            }
        }
    }

    fun pausar() {
        print("\nEnter pra voltar...")
        readlnOrNull()
    }
}
