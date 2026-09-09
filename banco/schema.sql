-- Script de criacao do banco do sistema do provedor.
-- Roda sozinho toda vez que a aplicacao sobe (usa IF NOT EXISTS pra nao dar erro).

CREATE TABLE IF NOT EXISTS setor (
    id          SERIAL PRIMARY KEY,
    nome        VARCHAR(60) NOT NULL UNIQUE,
    descricao   VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS funcionario (
    id              SERIAL PRIMARY KEY,
    nome            VARCHAR(120) NOT NULL,
    cpf             CHAR(11) NOT NULL UNIQUE,
    email           VARCHAR(120),
    telefone        VARCHAR(11),
    cargo           VARCHAR(60) NOT NULL,
    salario         NUMERIC(10,2) NOT NULL CHECK (salario >= 0),
    setor_id        INTEGER NOT NULL REFERENCES setor (id),
    contratacao     VARCHAR(10) NOT NULL DEFAULT 'CLT'
                    CHECK (contratacao IN ('CLT', 'ESTAGIO', 'PJ')),
    data_admissao   DATE NOT NULL DEFAULT CURRENT_DATE,
    ativo           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cliente (
    id              SERIAL PRIMARY KEY,
    nome            VARCHAR(120) NOT NULL,
    cpf_cnpj        VARCHAR(14) NOT NULL UNIQUE,
    email           VARCHAR(120),
    telefone        VARCHAR(11),
    endereco        VARCHAR(150),
    cidade          VARCHAR(60),
    data_cadastro   TIMESTAMP NOT NULL DEFAULT NOW(),
    ativo           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS fornecedor (
    id              SERIAL PRIMARY KEY,
    razao_social    VARCHAR(120) NOT NULL,
    cnpj            CHAR(14) NOT NULL UNIQUE,
    email           VARCHAR(120),
    telefone        VARCHAR(11),
    ativo           BOOLEAN NOT NULL DEFAULT TRUE
);

-- O "servico" vendido pelo provedor: o plano de internet.
CREATE TABLE IF NOT EXISTS plano (
    id                  SERIAL PRIMARY KEY,
    nome                VARCHAR(60) NOT NULL UNIQUE,
    velocidade_mega     INTEGER NOT NULL CHECK (velocidade_mega > 0),
    valor_mensal        NUMERIC(10,2) NOT NULL CHECK (valor_mensal > 0),
    taxa_instalacao     NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (taxa_instalacao >= 0),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE
);

-- O "produto": roteador, ONU, conector, caixa de emenda, cabo drop etc.
CREATE TABLE IF NOT EXISTS produto (
    id                  SERIAL PRIMARY KEY,
    descricao           VARCHAR(120) NOT NULL,
    unidade             VARCHAR(10) NOT NULL DEFAULT 'UN',
    quantidade_estoque  INTEGER NOT NULL DEFAULT 0 CHECK (quantidade_estoque >= 0),
    estoque_minimo      INTEGER NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0),
    preco_custo         NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (preco_custo >= 0),
    preco_venda         NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (preco_venda >= 0),
    fornecedor_id       INTEGER REFERENCES fornecedor (id)
);

-- O valor_mensal fica gravado no contrato de proposito: se o plano mudar de
-- preco depois, quem ja assinou continua pagando o que contratou.
CREATE TABLE IF NOT EXISTS contrato (
    id                  SERIAL PRIMARY KEY,
    cliente_id          INTEGER NOT NULL REFERENCES cliente (id),
    plano_id            INTEGER NOT NULL REFERENCES plano (id),
    vendedor_id         INTEGER REFERENCES funcionario (id),
    valor_mensal        NUMERIC(10,2) NOT NULL CHECK (valor_mensal > 0),
    data_inicio         DATE NOT NULL DEFAULT CURRENT_DATE,
    dia_vencimento      INTEGER NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 28),
    status              VARCHAR(12) NOT NULL DEFAULT 'ATIVO'
                        CHECK (status IN ('ATIVO', 'SUSPENSO', 'CANCELADO')),
    data_cancelamento   DATE
);

CREATE TABLE IF NOT EXISTS fatura (
    id              SERIAL PRIMARY KEY,
    contrato_id     INTEGER NOT NULL REFERENCES contrato (id),
    competencia     CHAR(7) NOT NULL,
    valor           NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    vencimento      DATE NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'ABERTA'
                    CHECK (status IN ('ABERTA', 'PAGA', 'CANCELADA')),
    data_pagamento  TIMESTAMP,
    UNIQUE (contrato_id, competencia)
);

CREATE TABLE IF NOT EXISTS ordem_servico (
    id              SERIAL PRIMARY KEY,
    cliente_id      INTEGER NOT NULL REFERENCES cliente (id),
    tecnico_id      INTEGER REFERENCES funcionario (id),
    tipo            VARCHAR(12) NOT NULL
                    CHECK (tipo IN ('INSTALACAO', 'MANUTENCAO', 'RETIRADA')),
    descricao       VARCHAR(250) NOT NULL,
    valor           NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (valor >= 0),
    abertura        TIMESTAMP NOT NULL DEFAULT NOW(),
    encerramento    TIMESTAMP,
    status          VARCHAR(12) NOT NULL DEFAULT 'ABERTA'
                    CHECK (status IN ('ABERTA', 'ENCERRADA', 'CANCELADA'))
);

-- Material que o tecnico gastou na OS (sai do estoque).
CREATE TABLE IF NOT EXISTS item_ordem_servico (
    id          SERIAL PRIMARY KEY,
    ordem_id    INTEGER NOT NULL REFERENCES ordem_servico (id),
    produto_id  INTEGER NOT NULL REFERENCES produto (id),
    quantidade  INTEGER NOT NULL CHECK (quantidade > 0)
);

-- Venda de produto direto pro cliente (roteador avulso, cabo, conector).
-- Diferente da compra: aqui sai do estoque e entra dinheiro no caixa.
CREATE TABLE IF NOT EXISTS venda (
    id              SERIAL PRIMARY KEY,
    cliente_id      INTEGER NOT NULL REFERENCES cliente (id),
    produto_id      INTEGER NOT NULL REFERENCES produto (id),
    quantidade      INTEGER NOT NULL CHECK (quantidade > 0),
    valor_unitario  NUMERIC(10,2) NOT NULL CHECK (valor_unitario > 0),
    responsavel_id  INTEGER NOT NULL REFERENCES funcionario (id),
    data_venda      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS compra (
    id              SERIAL PRIMARY KEY,
    fornecedor_id   INTEGER NOT NULL REFERENCES fornecedor (id),
    produto_id      INTEGER NOT NULL REFERENCES produto (id),
    quantidade      INTEGER NOT NULL CHECK (quantidade > 0),
    valor_unitario  NUMERIC(10,2) NOT NULL CHECK (valor_unitario > 0),
    responsavel_id  INTEGER NOT NULL REFERENCES funcionario (id),
    data_compra     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Livro caixa: toda operacao com dinheiro cai aqui.
CREATE TABLE IF NOT EXISTS movimentacao_financeira (
    id              SERIAL PRIMARY KEY,
    tipo            VARCHAR(7) NOT NULL CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    categoria       VARCHAR(30) NOT NULL,
    valor           NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    pagador         VARCHAR(120) NOT NULL,
    recebedor       VARCHAR(120) NOT NULL,
    data_hora       TIMESTAMP NOT NULL DEFAULT NOW(),
    descricao       VARCHAR(250) NOT NULL,
    responsavel_id  INTEGER NOT NULL REFERENCES funcionario (id),
    saldo_apos      NUMERIC(12,2) NOT NULL
);

-- Linha unica com o saldo atual. O CHECK garante que so existe o registro 1.
CREATE TABLE IF NOT EXISTS caixa (
    id              INTEGER PRIMARY KEY,
    saldo           NUMERIC(12,2) NOT NULL DEFAULT 0,
    atualizado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT caixa_linha_unica CHECK (id = 1)
);

-- A UNIQUE aqui e o que impede pagar o mesmo salario duas vezes no mes.
CREATE TABLE IF NOT EXISTS pagamento_salario (
    id              SERIAL PRIMARY KEY,
    funcionario_id  INTEGER NOT NULL REFERENCES funcionario (id),
    competencia     CHAR(7) NOT NULL,
    valor           NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    responsavel_id  INTEGER NOT NULL REFERENCES funcionario (id),
    data_pagamento  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (funcionario_id, competencia)
);

-- Toda contagem de inventario que muda o estoque fica registrada com
-- responsavel e motivo, senao material some sem deixar rastro.
CREATE TABLE IF NOT EXISTS ajuste_estoque (
    id                  SERIAL PRIMARY KEY,
    produto_id          INTEGER NOT NULL REFERENCES produto (id),
    quantidade_anterior INTEGER NOT NULL,
    quantidade_nova     INTEGER NOT NULL,
    motivo              VARCHAR(250) NOT NULL,
    responsavel_id      INTEGER NOT NULL REFERENCES funcionario (id),
    data_ajuste         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mov_data ON movimentacao_financeira (data_hora);
CREATE INDEX IF NOT EXISTS idx_fatura_status ON fatura (status);
CREATE INDEX IF NOT EXISTS idx_os_status ON ordem_servico (status);

-- Ajuste pra quem ja tinha o banco criado antes da coluna valor_mensal existir.
-- Em banco novo nao faz nada, porque a coluna ja vem no CREATE acima.
ALTER TABLE contrato ADD COLUMN IF NOT EXISTS valor_mensal NUMERIC(10,2);

-- Mesma ideia pro regime de contratacao, pra quem ja tinha a tabela criada.
ALTER TABLE funcionario ADD COLUMN IF NOT EXISTS contratacao VARCHAR(10) NOT NULL DEFAULT 'CLT';

ALTER TABLE funcionario DROP CONSTRAINT IF EXISTS funcionario_contratacao_check;

ALTER TABLE funcionario ADD CONSTRAINT funcionario_contratacao_check
    CHECK (contratacao IN ('CLT', 'ESTAGIO', 'PJ'));

UPDATE contrato c
   SET valor_mensal = p.valor_mensal
  FROM plano p
 WHERE p.id = c.plano_id AND c.valor_mensal IS NULL;

-- Depois de preencher, a coluna fica com as mesmas regras do banco novo.
-- Rodar isso de novo nao muda nada, entao pode ficar no script.
ALTER TABLE contrato ALTER COLUMN valor_mensal SET NOT NULL;

ALTER TABLE contrato DROP CONSTRAINT IF EXISTS contrato_valor_mensal_check;

ALTER TABLE contrato ADD CONSTRAINT contrato_valor_mensal_check CHECK (valor_mensal > 0);

INSERT INTO caixa (id, saldo) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;

-- Setores minimos pedidos no enunciado (o sistema aceita cadastrar mais).
INSERT INTO setor (nome, descricao) VALUES
    ('Financeiro', 'Contas a pagar e a receber, folha e conferencia do caixa'),
    ('Comercial', 'Venda de planos, contratos e atendimento ao cliente'),
    ('Suporte Tecnico', 'Instalacoes, manutencoes e ordens de servico em campo'),
    ('Administrativo', 'Compras, fornecedores e controle de estoque')
ON CONFLICT (nome) DO NOTHING;
