package br.com.provedor.modelo

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Regime de contratacao do funcionario.
 *
 * Aqui esta o polimorfismo do sistema: os tres regimes pagam de forma
 * diferente, e quem manda pagar (o ServicoFinanceiro) nao sabe qual e o
 * regime. Ele so chama valorLiquido() e categoriaNoCaixa() e cada objeto
 * responde do seu jeito.
 *
 * E sealed pra garantir que so existem esses tres - se amanha entrar
 * "Jovem Aprendiz", eu crio mais um object aqui e acrescento uma linha no
 * de(). Nenhum servico, DAO ou menu precisa ser alterado.
 *
 * Obs.: a aliquota do INSS esta simplificada em 11% de proposito, o foco do
 * trabalho nao e folha de pagamento - o que importa e o calculo mudar
 * conforme o tipo.
 */
sealed class Contratacao {

    abstract val rotulo: String

    /** Categoria que vai pro livro caixa - cada regime entra numa conta. */
    abstract val categoriaNoCaixa: String

    /** Quanto sai do caixa de fato, partindo do valor cadastrado. */
    abstract fun valorLiquido(valorBase: BigDecimal): BigDecimal

    /** Texto que aparece na descricao da movimentacao. */
    abstract fun descricaoDoPagamento(competencia: String): String

    object Clt : Contratacao() {
        private val ALIQUOTA_INSS = BigDecimal("0.11")

        override val rotulo = "CLT"
        override val categoriaNoCaixa = "FOLHA_PAGAMENTO"

        override fun valorLiquido(valorBase: BigDecimal): BigDecimal =
            valorBase.subtract(valorBase.multiply(ALIQUOTA_INSS)).setScale(2, RoundingMode.HALF_UP)

        override fun descricaoDoPagamento(competencia: String) =
            "Salario CLT de $competencia (liquido, ja descontado o INSS)"
    }

    object Estagio : Contratacao() {
        override val rotulo = "Estagiario"
        override val categoriaNoCaixa = "BOLSA_ESTAGIO"

        // Bolsa de estagio nao sofre o desconto, sai integral.
        override fun valorLiquido(valorBase: BigDecimal): BigDecimal =
            valorBase.setScale(2, RoundingMode.HALF_UP)

        override fun descricaoDoPagamento(competencia: String) =
            "Bolsa-auxilio de estagio referente a $competencia"
    }

    object Pj : Contratacao() {
        override val rotulo = "PJ"
        override val categoriaNoCaixa = "PRESTADOR_PJ"

        // Prestador recebe o valor cheio da nota, as retencoes sao com ele.
        override fun valorLiquido(valorBase: BigDecimal): BigDecimal =
            valorBase.setScale(2, RoundingMode.HALF_UP)

        override fun descricaoDoPagamento(competencia: String) =
            "Nota de prestacao de servico referente a $competencia"
    }

    companion object {

        /**
         * Tem que ser get() e nao um val direto: os objects sao filhos desta
         * mesma classe, entao montar a lista na inicializacao do companion
         * daria uma referencia circular e a lista vinha com null dentro.
         * Descobri isso testando - a tela de cadastro quebrava na hora de
         * listar os regimes.
         */
        val todas: List<Contratacao> get() = listOf(Clt, Estagio, Pj)

        /** Converte o que esta gravado no banco de volta pro objeto. */
        fun de(codigo: String?): Contratacao = when (codigo?.uppercase()) {
            "ESTAGIO" -> Estagio
            "PJ" -> Pj
            else -> Clt
        }

        /** Codigo que vai pro banco. */
        fun codigoDe(contratacao: Contratacao): String = when (contratacao) {
            is Clt -> "CLT"
            is Estagio -> "ESTAGIO"
            is Pj -> "PJ"
        }
    }
}
