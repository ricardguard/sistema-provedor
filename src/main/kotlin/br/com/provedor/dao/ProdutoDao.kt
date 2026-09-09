package br.com.provedor.dao

import br.com.provedor.banco.Conexao
import br.com.provedor.modelo.Produto
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types

class ProdutoDao {

    private val selectBase = """
        SELECT id, descricao, unidade, quantidade_estoque, estoque_minimo,
               preco_custo, preco_venda, fornecedor_id
          FROM produto
    """.trimIndent()

    fun inserir(produto: Produto): Int {
        val sql = """
            INSERT INTO produto (descricao, unidade, quantidade_estoque, estoque_minimo,
                                 preco_custo, preco_venda, fornecedor_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        Conexao.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, produto.descricao)
            ps.setString(2, produto.unidade)
            ps.setInt(3, produto.quantidadeEstoque)
            ps.setInt(4, produto.estoqueMinimo)
            ps.setBigDecimal(5, produto.precoCusto)
            ps.setBigDecimal(6, produto.precoVenda)
            // fornecedor e opcional, entao mando NULL quando nao veio
            if (produto.fornecedorId == null) ps.setNull(7, Types.INTEGER)
            else ps.setInt(7, produto.fornecedorId)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun listar(): List<Produto> {
        val lista = mutableListOf<Produto>()
        Conexao.get().prepareStatement("$selectBase ORDER BY descricao").use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun listarAbaixoDoMinimo(): List<Produto> {
        val lista = mutableListOf<Produto>()
        val sql = "$selectBase WHERE quantidade_estoque <= estoque_minimo ORDER BY descricao"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) lista.add(montar(rs)) }
        }
        return lista
    }

    fun buscarPorId(id: Int): Produto? {
        Conexao.get().prepareStatement("$selectBase WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> return if (rs.next()) montar(rs) else null }
        }
    }

    fun atualizar(produto: Produto): Boolean {
        val sql = """
            UPDATE produto
               SET descricao = ?, unidade = ?, estoque_minimo = ?, preco_custo = ?,
                   preco_venda = ?, fornecedor_id = ?
             WHERE id = ?
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setString(1, produto.descricao)
            ps.setString(2, produto.unidade)
            ps.setInt(3, produto.estoqueMinimo)
            ps.setBigDecimal(4, produto.precoCusto)
            ps.setBigDecimal(5, produto.precoVenda)
            if (produto.fornecedorId == null) ps.setNull(6, Types.INTEGER) else ps.setInt(6, produto.fornecedorId)
            ps.setInt(7, produto.id)
            return ps.executeUpdate() > 0
        }
    }

    /**
     * Soma (entrada) ou subtrai (saida) do estoque.
     * O "quantidade_estoque + ? >= 0" evita estoque negativo mesmo se
     * duas operacoes acontecerem ao mesmo tempo.
     */
    fun movimentarEstoque(produtoId: Int, quantidade: Int): Boolean {
        val sql = """
            UPDATE produto SET quantidade_estoque = quantidade_estoque + ?
             WHERE id = ? AND quantidade_estoque + ? >= 0
        """.trimIndent()
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, quantidade)
            ps.setInt(2, produtoId)
            ps.setInt(3, quantidade)
            return ps.executeUpdate() > 0
        }
    }

    /**
     * Grava a quantidade contada, e nao uma diferenca. E o certo pro ajuste
     * de inventario: a contagem fisica diz quanto TEM, entao o estoque tem
     * que terminar exatamente nesse numero.
     */
    fun definirEstoque(produtoId: Int, quantidade: Int): Boolean {
        val sql = "UPDATE produto SET quantidade_estoque = ? WHERE id = ? AND ? >= 0"
        Conexao.get().prepareStatement(sql).use { ps ->
            ps.setInt(1, quantidade)
            ps.setInt(2, produtoId)
            ps.setInt(3, quantidade)
            return ps.executeUpdate() > 0
        }
    }

    fun atualizarPrecoCusto(produtoId: Int, custo: java.math.BigDecimal): Boolean {
        Conexao.get().prepareStatement("UPDATE produto SET preco_custo = ? WHERE id = ?").use { ps ->
            ps.setBigDecimal(1, custo)
            ps.setInt(2, produtoId)
            return ps.executeUpdate() > 0
        }
    }

    private fun montar(rs: ResultSet): Produto {
        // getInt devolve 0 quando a coluna e NULL, entao preciso conferir na hora
        val codigoFornecedor = rs.getInt("fornecedor_id")
        val fornecedor: Int? = if (rs.wasNull()) null else codigoFornecedor
        return Produto(
            id = rs.getInt("id"),
            descricao = rs.getString("descricao"),
            unidade = rs.getString("unidade"),
            quantidadeEstoque = rs.getInt("quantidade_estoque"),
            estoqueMinimo = rs.getInt("estoque_minimo"),
            precoCusto = rs.getBigDecimal("preco_custo"),
            precoVenda = rs.getBigDecimal("preco_venda"),
            fornecedorId = fornecedor
        )
    }
}
