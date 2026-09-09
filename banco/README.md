# Banco de dados

Modelagem do sistema de gestão do provedor. O script completo está em
[`schema.sql`](schema.sql), nesta mesma pasta.

O sistema roda esse script sozinho toda vez que sobe (`banco/Migracao.kt`), então
não precisa executar nada na mão. Como o script usa `IF NOT EXISTS` e
`ON CONFLICT`, pode rodar quantas vezes for que não duplica nem quebra.

## Como preparar

```sql
CREATE DATABASE provedor_db;
```

Depois é só ajustar usuário e senha em `src/main/resources/banco.properties`
(tem um `banco.properties.exemplo` de modelo) e rodar a aplicação. As tabelas
são criadas na primeira execução, já com os quatro setores cadastrados.

Se quiser criar as tabelas antes, por fora do sistema:

```bash
psql -d provedor_db -f banco/schema.sql
```

## As tabelas

São 16, divididas em quatro blocos.

### Pessoas e organização

| Tabela | O que guarda |
|---|---|
| `setor` | Financeiro, Comercial, Suporte Técnico e Administrativo. Já vêm criados pelo script. |
| `funcionario` | Nome, CPF, cargo, salário, regime de contratação e o setor. |
| `cliente` | Pessoa física ou jurídica - o campo `cpf_cnpj` aceita 11 ou 14 dígitos. |
| `fornecedor` | De quem o provedor compra material. |

### Produto e serviço

| Tabela | O que guarda |
|---|---|
| `plano` | O serviço vendido: velocidade, mensalidade e taxa de instalação. |
| `produto` | O material: roteador, ONU, cabo drop. Tem estoque e estoque mínimo. |
| `contrato` | Liga cliente e plano. Guarda o valor contratado e o dia de vencimento. |
| `fatura` | A mensalidade de cada competência (MM/AAAA) de um contrato. |
| `ordem_servico` | Instalação, manutenção ou retirada, com técnico e valor de mão de obra. |
| `item_ordem_servico` | O material que o técnico gastou em cada OS. |

### Movimento de estoque

| Tabela | O que guarda |
|---|---|
| `compra` | Entrada de material vinda do fornecedor. |
| `venda` | Saída de produto vendido direto pro cliente. |
| `ajuste_estoque` | Correção de inventário, com responsável, motivo e de/para. |

### Financeiro

| Tabela | O que guarda |
|---|---|
| `movimentacao_financeira` | O livro caixa. Toda operação com dinheiro cai aqui. |
| `pagamento_salario` | Registro da folha, com `UNIQUE` por funcionário e competência. |
| `caixa` | Uma linha só, com o saldo atual. |

## Decisões da modelagem

**O valor da mensalidade fica gravado no contrato, não só no plano.**
`contrato.valor_mensal` é uma cópia do preço no dia da assinatura. Se o plano
subir de R$ 99,90 para R$ 149,90 amanhã, quem já assinou continua pagando o que
contratou - e as faturas antigas não são reescritas.

**A tabela `caixa` tem uma linha só.** O `CHECK (id = 1)` garante isso. Antes de
calcular um novo saldo, o sistema faz `SELECT ... FOR UPDATE` nessa linha, que a
trava até o commit. Sem isso, duas operações simultâneas leriam o mesmo saldo e a
última sobrescreveria a outra.

**`item_ordem_servico` é o relacionamento N:N** entre ordem de serviço e produto.
Ela existe como tabela e não como um par de chaves porque tem atributo próprio: a
`quantidade` usada. Uma OS consome vários produtos, e um produto entra em várias OS.

**Ninguém é excluído, só inativado.** `funcionario`, `cliente`, `fornecedor` e
`plano` têm a coluna `ativo`. Se apagasse um funcionário, as movimentações
financeiras dele ficariam sem responsável e o histórico do caixa perderia sentido.

**O `saldo_apos` na movimentação é trilha de auditoria.** Guarda como o caixa ficou
depois de cada lançamento. Se o saldo da tabela `caixa` não bater com o último
`saldo_apos`, alguma coisa foi gravada por fora.

## Integridade

Além das chaves estrangeiras, o banco tem uma segunda linha de defesa que vale
mesmo se alguém inserir direto pelo `psql`:

- `CHECK (quantidade_estoque >= 0)` - estoque nunca fica negativo.
- `CHECK (valor > 0)` na movimentação - não existe lançamento de valor zero.
- `CHECK (dia_vencimento BETWEEN 1 AND 28)` - evita dia 30 em fevereiro.
- `CHECK (status IN (...))` em contrato, fatura e OS - espelha os `enum class` do Kotlin.
- `UNIQUE (contrato_id, competencia)` - não fatura o mesmo mês duas vezes.
- `UNIQUE (funcionario_id, competencia)` - não paga o mesmo salário duas vezes.
- `movimentacao_financeira.responsavel_id NOT NULL` - não existe lançamento sem
  quem autorizou.

## Consultas para conferir

```sql
-- o livro caixa inteiro, na ordem
SELECT id, tipo, categoria, valor, pagador, recebedor, data_hora, saldo_apos
  FROM movimentacao_financeira
 ORDER BY id;

-- o saldo tem que bater com o saldo_apos do ultimo lancamento
SELECT saldo FROM caixa;

-- funcionarios por setor
SELECT s.nome AS setor, f.nome, f.cargo, f.contratacao, f.salario
  FROM funcionario f
  JOIN setor s ON s.id = f.setor_id
 WHERE f.ativo
 ORDER BY s.nome, f.nome;

-- faturas vencidas e nao pagas
SELECT c.nome, f.competencia, f.vencimento, f.valor
  FROM fatura f
  JOIN contrato ct ON ct.id = f.contrato_id
  JOIN cliente c ON c.id = ct.cliente_id
 WHERE f.status = 'ABERTA' AND f.vencimento < CURRENT_DATE
 ORDER BY f.vencimento;

-- material consumido em cada OS (o N:N)
SELECT o.id AS os, cl.nome AS cliente, p.descricao, i.quantidade
  FROM item_ordem_servico i
  JOIN ordem_servico o ON o.id = i.ordem_id
  JOIN produto p ON p.id = i.produto_id
  JOIN cliente cl ON cl.id = o.cliente_id
 ORDER BY o.id;
```
