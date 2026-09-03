# Sistema de gestao - Conecta Fibra (provedor de internet)

Trabalho feito em Kotlin com PostgreSQL, rodando por menus no console.
A ideia foi montar o sistema de um provedor de internet pequeno: ele vende o
servico (plano de internet), controla o material que usa nas instalacoes
(roteador, ONU, cabo), cuida das pessoas envolvidas (funcionarios divididos em
setores, clientes e fornecedores) e fecha tudo no caixa, com cada movimentacao
gravada no banco.

## O que precisa pra rodar

- JDK 21
- PostgreSQL 14 ou mais novo
- IntelliJ IDEA (o projeto e Gradle, o proprio IntelliJ resolve as dependencias)

## Preparando o banco

Cria o banco vazio uma vez:

```sql
CREATE DATABASE provedor_db;
```

Depois confere usuario e senha em `src/main/resources/banco.properties`:

```properties
db.url=jdbc:postgresql://localhost:5432/provedor_db
db.usuario=postgres
db.senha=postgres
```

Se preferir nao mexer no arquivo, da pra sobrescrever por variavel de ambiente
(`PROVEDOR_DB_URL`, `PROVEDOR_DB_USER`, `PROVEDOR_DB_PASSWORD`).

As tabelas nao precisam ser criadas na mao: na primeira execucao o sistema roda
o `src/main/resources/schema.sql` sozinho. O script usa `IF NOT EXISTS` e
`ON CONFLICT`, entao pode rodar quantas vezes for sem duplicar nada. Ele ja
deixa os quatro setores criados (Financeiro, Comercial, Suporte Tecnico e
Administrativo).

## Rodando

Pelo IntelliJ: abre a pasta do projeto, espera o Gradle sincronizar e da play na
`Main.kt`.

Pelo terminal:

```bash
./gradlew run          # ou "gradle run" se o wrapper ainda nao tiver sido gerado
```

Na primeira vez o banco esta vazio, entao o sistema pede o cadastro do primeiro
funcionario (o administrador). Depois disso toda vez que abrir ele pergunta qual
funcionario esta operando - esse e o responsavel gravado em cada movimentacao do
caixa.

## Como o sistema esta organizado

```
src/main/kotlin/br/com/provedor/
├── Main.kt          ponto de entrada e o cadastro do primeiro acesso
├── banco/           conexao JDBC, criacao do schema e controle de transacao
├── modelo/          as classes de dominio (data classes e enums)
├── dao/             acesso ao banco, um DAO por tabela, tudo com PreparedStatement
├── servico/         regras de negocio: Caixa, contratos, estoque, OS e financeiro
├── menu/            os menus de console
└── util/            validacao (regex), leitura do teclado e formatacao
```

## Fluxos que o sistema cobre

- **Servico**: cadastro dos planos, contrato do cliente, geracao das faturas do
  mes e recebimento da mensalidade.
- **Produto**: cadastro do material, compra do fornecedor (entra no estoque e sai
  do caixa), baixa do material usado na ordem de servico e ajuste de inventario.
- **Manutencao/instalacao**: ordem de servico com tecnico, material consumido e
  cobranca da mao de obra no encerramento. Ao fechar um contrato o sistema ja
  abre a OS de instalacao; ao cancelar, abre a de retirada.
- **Pessoas**: funcionarios (com setor), clientes e fornecedores.
- **Setores**: quatro ja vem criados e da pra cadastrar outros. Tem relatorio de
  funcionarios e folha por setor.
- **Caixa**: aporte, pagamento de salario (individual ou do setor inteiro),
  despesas, extrato por periodo e resumo de entradas x saidas.

## Sobre o caixa (encapsulamento)

A classe `servico/Caixa.kt` e um `object` com o campo `saldo` privado e sem
setter. Nenhuma outra parte do sistema consegue mudar o valor direto: o unico
caminho e `registrarEntrada` / `registrarSaida`, que antes de gravar conferem se
o valor e maior que zero, se tem descricao, se tem pagador e recebedor e se o
responsavel esta ativo. Em saida ainda checa se tem saldo suficiente.

Cada lancamento grava na tabela `movimentacao_financeira`:

| coluna | o que guarda |
|---|---|
| `valor` | quanto de dinheiro foi usado |
| `pagador` | quem pagou |
| `recebedor` | quem recebeu |
| `data_hora` | data e hora do lancamento |
| `descricao` | o motivo |
| `responsavel_id` | o funcionario que respondeu pela transacao |
| `saldo_apos` | como ficou o caixa depois (serve de conferencia) |

O saldo fica numa linha unica da tabela `caixa`. Antes de calcular o novo valor
o sistema faz `SELECT ... FOR UPDATE`, que trava a linha ate o commit, e grava a
movimentacao junto com o saldo dentro da mesma transacao (`banco/Transacao.kt`).
Se qualquer passo der errado, o rollback desfaz tudo - numa compra, por exemplo,
se faltar saldo o estoque nao e alterado.

## Validacoes

- **REGEX** em `util/Validacao.kt` para nome, e-mail, telefone e competencia
  (MM/AAAA). CPF e CNPJ passam pelo regex e ainda pelo calculo dos digitos
  verificadores, senao um CPF tipo 111.111.111-11 entraria.
- **TRY/CATCH** na conexao, na leitura de numero e data, e em volta de cada acao
  de menu (funcao `protegido` em `menu/Sessao.kt`), pra um erro nao derrubar o
  programa.
- **NULLABLE** nos campos que o banco aceita como NULL (e-mail, telefone,
  endereco, tecnico da OS, vendedor do contrato). A leitura do teclado usa
  `readlnOrNull`, e os DAOs tratam com `setNull` e `wasNull`.
- O banco tambem tem sua parte: `CHECK`, `UNIQUE` e chave estrangeira em quase
  todas as tabelas, entao mesmo que passasse pelo Kotlin nao entraria valor
  furado.

## Observacao

Funcionario, cliente, fornecedor e plano nao sao apagados do banco, so ficam
inativos. Se apagasse, as movimentacoes antigas ficariam sem responsavel e o
historico do caixa perderia sentido.
