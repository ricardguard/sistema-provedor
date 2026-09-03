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

    /** Aceita tanto 1500,50 quanto 1500.50 - o pessoal digita dos dois jeitos. */
    fun decimal(rotulo: String, minimo: BigDecimal = BigDecimal.ZERO): BigDecimal {
        while (true) {
            val bruto = ler(rotulo).replace(".", "").replace(",", ".")
            try {
                val valor = BigDecimal(bruto).setScale(2, java.math.RoundingMode.HALF_UP)
                if (valor < minimo) {
                    println("  > O valor nao pode ser menor que $minimo.")
                } else {
                    return valor
                }
            } catch (e: NumberFormatException) {
                println("  > Valor invalido (exemplo: 129,90).")
            }
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
