package br.com.provedor.servico

/** Erro de regra de negocio: o usuario tentou fazer algo que o sistema nao permite. */
open class RegraDeNegocioException(mensagem: String) : RuntimeException(mensagem)

class SaldoInsuficienteException(mensagem: String) : RegraDeNegocioException(mensagem)

class EstoqueInsuficienteException(mensagem: String) : RegraDeNegocioException(mensagem)
