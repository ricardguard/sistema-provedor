# Sistema de gestão de provedor de internet

Trabalho da disciplina, feito em Kotlin com PostgreSQL. É um sistema de console:
tudo acontece por menu, sem tela gráfica.

A ideia é um provedor pequeno, desses de cidade do interior. O sistema controla
quem é cliente, qual plano cada um assinou, as faturas do mês, as ordens de
serviço do pessoal de campo, o estoque de material e o caixa da empresa.

## O que dá pra fazer

- **Cadastros** — setores, funcionários (CLT, estágio ou PJ), clientes e fornecedores
- **Comercial** — planos de internet, contratos, geração e recebimento de faturas
- **Estoque** — compra do fornecedor, venda pro cliente, ajuste de inventário e aviso de estoque baixo
- **Ordens de serviço** — abertura, técnico responsável, material usado na OS e encerramento
- **Financeiro** — pagamento de salário e de folha por setor, despesas, aportes e extrato do caixa
- **Relatórios** — panorama da empresa, equipe por setor, inadimplência e estoque no limite

Toda movimentação de dinheiro grava quem era o operador na hora, então dá pra
saber depois quem lançou o quê.

## Como rodar

Precisa de **JDK 21** e do **PostgreSQL** rodando na máquina.

**1. Criar o banco:**

```sql
CREATE DATABASE provedor_db;
```

**2. Configurar a conexão.** Copie o modelo e ponha o seu usuário e senha:

```bash
cp src/main/resources/banco.properties.exemplo src/main/resources/banco.properties
```

Esse arquivo não vai pro Git de propósito — cada máquina tem a sua senha.
Se preferir, dá pra usar as variáveis de ambiente `PROVEDOR_DB_URL`,
`PROVEDOR_DB_USER` e `PROVEDOR_DB_PASSWORD` no lugar dele.

**3. Rodar:**

```bash
./gradlew run
```

Pelo IntelliJ, é só abrir a pasta e dar play no `Main.kt`.

Não precisa criar tabela na mão: o sistema executa o `schema.sql` sozinho toda
vez que sobe. Na primeira execução ele pede o cadastro do primeiro funcionário,
que é quem vai operar o caixa dali em diante.

## Banco de dados

O script fica em [`banco/schema.sql`](banco/schema.sql) e são 16 tabelas.
A explicação da modelagem — o que cada tabela guarda, por que ficou assim e as
consultas de exemplo — está em [`banco/README.md`](banco/README.md).

## Organização do código

```
src/main/kotlin/br/com/provedor/
├── Main.kt        entrada do programa
├── banco/         conexão, transação e criação das tabelas
├── modelo/        as classes do domínio (Cliente, Contrato, OrdemServico...)
├── dao/           SQL de cada tabela, com PreparedStatement
├── servico/       as regras de negócio
├── menu/          as telas do console
└── util/          leitura do teclado, validações e formatação
```

A separação é essa mesma: o menu só conversa com o usuário, o serviço decide se
pode ou não pode, e o DAO fala com o banco. Assim uma regra fica num lugar só.
