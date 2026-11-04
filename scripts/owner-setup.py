#!/usr/bin/env python3
"""Cria ou recupera o acesso OWNER 'admin' apenas no Docker local da Loja.

Executar somente no terminal privado do Codespace. Não remove contas, produtos ou vendas.
Senha nunca passa por argumento de shell, arquivo de configuração ou commit.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def settings_from_file(path: Path) -> dict[str, str]:
    settings: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, value = stripped.split("=", 1)
        settings[name.strip()] = value.strip().strip("'\"")
    return settings


def assert_local_compose(data: dict) -> None:
    services = data.get("services", {})
    app = services.get("app", {})
    db = services.get("postgres", {})
    if app.get("environment", {}).get("SPRING_PROFILES_ACTIVE") != "local":
        raise ValueError("A recuperação é permitida somente no perfil local.")
    if not str(db.get("image", "")).startswith("postgres:"):
        raise ValueError("Banco Docker local não confirmado.")


def sql_for_password(password: str) -> str:
    # Usamos apenas o alfabeto ASCII seguro gerado por secrets.token_urlsafe.
    # Nunca inserir uma senha fornecida externamente diretamente em SQL.
    if not re.fullmatch(r"[A-Za-z0-9_-]{20,64}", password):
        raise ValueError("Formato de senha temporária inválido.")
    return f"""
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
DO $owner_setup$
DECLARE owner_id UUID;
BEGIN
    SELECT id INTO owner_id FROM app_user WHERE username = 'admin' FOR UPDATE;
    IF owner_id IS NULL THEN
        INSERT INTO app_user (id, store_id, username, password_hash, enabled)
        VALUES (
            gen_random_uuid(),
            '00000000-0000-0000-0000-000000000001',
            'admin', crypt('{password}', gen_salt('bf', 12)), TRUE
        )
        RETURNING id INTO owner_id;
        INSERT INTO user_role (user_id, role_code)
        VALUES (owner_id, 'ROLE_OWNER');
    ELSIF NOT EXISTS (
        SELECT 1 FROM user_role
        WHERE user_id = owner_id AND role_code = 'ROLE_OWNER'
    ) THEN
        RAISE EXCEPTION 'O usuario admin existe mas nao tem papel OWNER. Nenhum dado alterado.';
    ELSE
        UPDATE app_user
        SET password_hash = crypt('{password}', gen_salt('bf', 12)),
            enabled = TRUE,
            auth_revision = auth_revision + 1
        WHERE id = owner_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM app_user u JOIN user_role r ON r.user_id = u.id
        WHERE u.id = owner_id AND u.enabled
          AND r.role_code = 'ROLE_OWNER'
          AND u.password_hash = crypt('{password}', u.password_hash)
          AND left(u.password_hash, 4) = '$2a$'
    ) THEN
        RAISE EXCEPTION 'Falha ao confirmar a senha BCrypt da conta OWNER.';
    END IF;

    INSERT INTO audit_event (id, store_id, actor_id, actor_name, event_type, entity_id, detail)
    VALUES (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000001',
        owner_id,
        'admin',
        'OWNER_LOCAL_RECOVERY',
        owner_id,
        'Acesso administrativo criado ou recuperado pelo operador no Docker local'
    );
END $owner_setup$;
COMMIT;
SELECT 'OWNER_CONFIGURADO';
"""


def run(*args: str, stdin: str | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(args), cwd=ROOT, input=stdin, text=True,
        capture_output=True, check=False,
    )


def main() -> int:
    if not (sys.stdin.isatty() and sys.stdout.isatty()):
        print("Execute diretamente no terminal interativo do Codespace.", file=sys.stderr)
        return 1
    env_path = ROOT / ".env"
    if not env_path.is_file():
        print("Arquivo .env não encontrado. Inicie a aplicação primeiro.", file=sys.stderr)
        return 1
    settings = settings_from_file(env_path)
    dbname = settings.get("POSTGRES_DB", "")
    dbuser = settings.get("POSTGRES_USER", "")
    if not re.fullmatch(r"[a-zA-Z_][a-zA-Z0-9_]{0,62}", dbname) or not re.fullmatch(
        r"[a-zA-Z_][a-zA-Z0-9_]{0,62}", dbuser
    ):
        print("POSTGRES_DB/POSTGRES_USER não são identificadores válidos.", file=sys.stderr)
        return 1

    compose_args = ("docker", "compose", "--env-file", ".env", "-f", "compose.yaml")
    config = run(*compose_args, "--profile", "app", "config", "--format", "json")
    if config.returncode:
        print("Docker Compose local indisponível ou configuração inválida.", file=sys.stderr)
        return 1
    try:
        assert_local_compose(json.loads(config.stdout))
    except (ValueError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
        return 1

    print("Loja · criar ou recuperar acesso de dono")
    print("Usuário: admin · papel: OWNER")
    print("Este comando redefinirá a senha da conta admin, se ela já for dona.")
    print("Outros usuários, dados comerciais e volumes serão preservados.")
    print("Se havia uma sessão admin ativa, ela será encerrada.")
    if input("Digite CONFIRMAR para continuar: ").strip() != "CONFIRMAR":
        print("Operação cancelada. Nenhuma alteração realizada.")
        return 1

    print("Criando backup de segurança do banco atual...")
    backup = run("bash", "scripts/backup.sh")
    if backup.returncode:
        print("Backup falhou; conta não foi alterada. Confira Docker e PostgreSQL.", file=sys.stderr)
        return 1
    backup_name = backup.stdout.strip().splitlines()[-1]
    if not (ROOT / backup_name).is_file():
        print("Backup não pôde ser confirmado. Operação interrompida.", file=sys.stderr)
        return 1

    temporary = secrets.token_urlsafe(24)
    result = run(
        *compose_args, "exec", "-T", "postgres",
        "psql", "-X", "-v", "ON_ERROR_STOP=1",
        "-U", dbuser, "-d", dbname, stdin=sql_for_password(temporary),
    )
    if result.returncode or "OWNER_CONFIGURADO" not in result.stdout:
        print(
            "A alteração não pôde ser confirmada. Verifique o PostgreSQL local."
            " Se o usuário admin tiver outro papel, consulte a equipe responsável."
            " O backup foi preservado em: " + backup_name,
            file=sys.stderr,
        )
        return 1

    print("\nConta de dono pronta para login:")
    print("Usuário: admin")
    print("Senha temporária: " + temporary)
    print("Backup anterior: " + backup_name)
    print("\nAbra a porta 8080, entre como admin e altere sua senha em")
    print("Usuários > Editar admin > Nova senha.")
    print("A senha temporária NÃO foi salva no Git, no .env ou em arquivo.")
    print("Copie-a agora e não compartilhe esta saída.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
