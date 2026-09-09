package br.com.provedor.modelo

import br.com.provedor.util.Formato

/**
 * Contrato comum de quem pode aparecer numa movimentacao do caixa.
 *
 * Cliente, Fornecedor, Funcionario e a propria Empresa sao coisas bem
 * diferentes no sistema - tem tabela propria, campo proprio, regra propria -
 * mas na hora de lancar dinheiro todas precisam responder a mesma coisa:
 * quem e voce e qual o seu documento. Por isso e interface e nao herança:
 * elas nao compartilham comportamento nem estado, so essa obrigacao.
 *
 * O ganho pratico: o Caixa recebe Pessoa e nao se importa com o tipo
 * concreto. Antes ele recebia String solta, e nada impedia de gravar um
 * pagador digitado errado.
 */
interface Pessoa {

    val nome: String

    /** CPF, CNPJ ou vazio quando e alguem de fora que nao esta cadastrado. */
    val documento: String

    /** Cada implementacao responde o que ela e - usado no extrato do caixa. */
    fun tipoDePessoa(): String

    /**
     * Implementacao padrao da interface: monta a identificacao completa a
     * partir do que cada tipo respondeu. Quem quiser pode sobrescrever, mas
     * nenhuma implementacao precisa reescrever isso.
     *
     * Terceiro nao tem documento, entao sai so o nome e o tipo.
     */
    fun identificacao(): String {
        if (documento.isBlank()) {
            return "$nome (${tipoDePessoa()})"
        }
        return "$nome (${tipoDePessoa()} - ${Formato.documento(documento)})"
    }
}

/**
 * Alguem que participa da movimentacao mas nao tem cadastro no sistema:
 * a concessionaria de energia, o dono que faz um aporte, o locador da sala.
 * Sem isso eu teria que cadastrar a Copel como fornecedor so pra pagar a luz.
 */
data class Terceiro(
    override val nome: String,
    override val documento: String = ""
) : Pessoa {
    override fun tipoDePessoa(): String = "Terceiro"
}
