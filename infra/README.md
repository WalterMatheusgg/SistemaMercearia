# Infraestrutura local

O `compose.yaml` na raiz inicia somente PostgreSQL 18.6 e Mailpit v1.27.11. As portas são publicadas em loopback, as credenciais vêm do ambiente/`.env` e os dados do PostgreSQL persistem no volume `postgres-data`.

Não há imagens de aplicação, cluster, provedor SMTP externo ou configuração de produção nesta etapa. A implantação de produção deve adicionar TLS, gestão de segredos, backup/restauração, monitoramento e migrações controladas.