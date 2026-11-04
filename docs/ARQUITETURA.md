# Arquitetura

Monólito modular Java 21/Spring Boot com Angular standalone e PostgreSQL. O backend usa JPA no cadastro/autenticação e JDBC nos fluxos comerciais; ambos participam da mesma transação Spring. Flyway versiona o banco; Hibernate somente valida.

| Módulo | Responsabilidade |
| --- | --- |
| `auth` | Sessão, usuário/papéis, recuperação/verificação, cadastro do cliente e preferências |
| `product` | Cadastro administrativo e referências de produto |
| `inventory` | Saldo por local, razão imutável, precisão de medidas e movimentação física |
| `commerce` | Contatos, vendas/orçamentos, compras/recebimentos, caixa, financeiro, catálogo, pedidos, reservas e relatórios |
| `notification` | Outbox, preferências aplicadas ao envio, retries, resumo diário e alertas |
| `operations` | Configuração da loja, cadastros auxiliares e consulta de auditoria |
| `config` | Segurança, validação de sessão, limitação de tentativas, bootstrap e fallback da SPA |

## Consistência

Cada comando comercial abre transação, reivindica uma chave idempotente e grava seus documentos/efeitos juntos. A chave é serializada por advisory lock e vinculada a ator/operação/conteúdo. Falha reverte saldo, pagamento, documento, auditoria e outbox.

Produtos são bloqueados em ordem de UUID; depois vêm saldos e registros de caixa/financeiro necessários. Venda confirmada baixa o físico exatamente uma vez. Pedido online pendente aumenta o reservado; confirmação consome a reserva antes da baixa na mesma transação. Cancelamento/expiração liberam reservas. Contagem verifica revisão; movimentos simultâneos não podem deixar disponibilidade negativa.

Snapshots dos itens preservam cadastro e valores comerciais. Originais não são apagados em devoluções: geram movimentos e lançamentos compensatórios. Movimentos físicos, eventos de auditoria, entradas de caixa e linhas de recebimento/devolução têm proteção de imutabilidade no banco. Estados atuais de documentos permanecem consultáveis junto do histórico.

## Segurança e privacidade

Sessão no servidor, cookie HttpOnly/SameSite=Lax e CSRF para SPA, renovado no login e removido no logout. Alteração de usuário/senha/papéis incrementa sua revisão e invalida sessões antigas na próxima requisição. A API aplica permissões independentemente de guards. Cliente só consulta seu próprio cadastro/pedidos; catálogo público tem DTO sem custos.

Senhas usam BCrypt. Links têm token aleatório de 256 bits, hash SHA-256 no banco, validade de 30 minutos e uso único. O corpo do e-mail transacional contém o link para entrega; não entra em logs. Contas com acesso sensível devem ter e-mail verificado para alertas. A limitação de autenticação é por processo/IP; produção com várias instâncias precisa de proteção compartilhada e estratégia de sessão.

## Notificações por email

O evento é inserido na outbox dentro da transação comercial. Um processador usa `FOR UPDATE SKIP LOCKED` e lease para reivindicar mensagens sem manter transação durante SMTP. Confere novamente e-mail, verificação, acesso ativo, papel e preferências antes de enviar.

Há até cinco tentativas, backoff limitado e histórico de status/erro sanitizado. SMTP não garante entrega exatamente uma vez: uma falha entre aceite e confirmação pode repetir uma mensagem. A chave evento/destinatário impede duplicação na criação da outbox. Falha de e-mail não desfaz a venda.

O resumo diário roda às 21h de Brasília. Alertas de reposição são gerados ao atingir o limite; vendas concluídas e seus cancelamentos seguem a preferência correspondente. Desenvolvimento usa Mailpit sem relay externo; produção exige SMTP/remetente configurados.

## Execução

No Docker integrado, o Angular compilado entra no JAR e a SPA/API usam a mesma origem na porta 8080. No desenvolvimento separado, Angular 4200 usa proxy `/api` para o backend 8080. Veja [CODESPACE.md](CODESPACE.md).

O perfil `prod` exige conexão de banco configurada, usa cookie Secure e desliga OpenAPI. Banco/SMTP não são expostos pelo Compose de produção. Um proxy HTTPS deve publicar a aplicação. Backup e restauração em banco separado estão nos scripts; operação real precisa de cópias externas e monitoramento. Pagamento automático e emissão fiscal dependem de provedores e não estão implementados.
