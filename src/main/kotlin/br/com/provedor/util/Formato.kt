package br.com.provedor.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Só formatacao de tela, pra nao ficar montando string no meio do menu. */
object Formato {

    private val BR = Locale.of("pt", "BR")
    private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun moeda(valor: BigDecimal?): String {
        val v = valor ?: BigDecimal.ZERO
        return String.format(BR, "R$ %,.2f", v.setScale(2, RoundingMode.HALF_UP))
    }

    fun data(data: LocalDate?): String = data?.format(DATA) ?: "-"

    fun dataHora(dataHora: LocalDateTime?): String = dataHora?.format(DATA_HORA) ?: "-"

    fun documento(doc: String?): String {
        val n = Validacao.somenteDigitos(doc)
        return when (n.length) {
            11 -> "${n.substring(0, 3)}.${n.substring(3, 6)}.${n.substring(6, 9)}-${n.substring(9)}"
            14 -> "${n.substring(0, 2)}.${n.substring(2, 5)}.${n.substring(5, 8)}/" +
                    "${n.substring(8, 12)}-${n.substring(12)}"
            else -> doc ?: "-"
        }
    }

    fun telefone(tel: String?): String {
        val n = Validacao.somenteDigitos(tel)
        return when (n.length) {
            10 -> "(${n.substring(0, 2)}) ${n.substring(2, 6)}-${n.substring(6)}"
            11 -> "(${n.substring(0, 2)}) ${n.substring(2, 7)}-${n.substring(7)}"
            else -> if (n.isEmpty()) "-" else n
        }
    }

    /** Corta o texto pra tabela do console nao quebrar a linha. */
    fun encurtar(texto: String?, tamanho: Int): String {
        val t = texto ?: "-"
        return if (t.length <= tamanho) t.padEnd(tamanho) else t.substring(0, tamanho - 3) + "..."
    }

    fun linha(tamanho: Int = 62): String = "-".repeat(tamanho)

    fun titulo(texto: String) {
        println()
        println(linha())
        println("  " + texto.uppercase())
        println(linha())
    }
}
