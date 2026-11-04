#!/usr/bin/env python3
"""Verifica configurações críticas antes de publicar uma loja; nunca exibe valores secretos."""
import pathlib
import re
import stat
import sys


def load_env(path):
    settings = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Linha {number} não possui '='.")
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", key):
            raise ValueError(f"Chave inválida na linha {number}.")
        if key in settings:
            raise ValueError(f"Variável duplicada: {key}.")
        value = value.strip()
        if value.startswith(("'", '"')) and value.endswith(value[0]):
            value = value[1:-1]
        else:
            value = re.split(r"\s+#", value, maxsplit=1)[0].strip()
        settings[key] = value
    return settings


def validate(settings):
    problems = []
    get = lambda key: settings.get(key, "").strip()
    for key in ("APP_DOMAIN", "APP_PUBLIC_URL", "POSTGRES_DB", "POSTGRES_USER",
                "POSTGRES_PASSWORD", "MAIL_HOST", "MAIL_PORT", "MAIL_FROM",
                "MAIL_USERNAME", "MAIL_PASSWORD"):
        if not get(key):
            problems.append(f"Defina {key}.")
    domain = get("APP_DOMAIN").lower()
    labels = domain.split(".")
    if (len(labels) < 2 or any(
            not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label)
            for label in labels
    ) or domain.endswith((".local", ".test", ".invalid", ".example"))
            or domain in ("example.com", "example.org", "localhost")):
        problems.append("APP_DOMAIN deve ser um domínio público válido, não um exemplo.")
    if get("APP_PUBLIC_URL") != "https://" + domain:
        problems.append("APP_PUBLIC_URL deve ser exatamente https://APP_DOMAIN.")
    if get("SESSION_COOKIE_SECURE").lower() != "true":
        problems.append("SESSION_COOKIE_SECURE deve ser true.")
    if len(get("POSTGRES_PASSWORD")) < 16 or get("POSTGRES_PASSWORD") in (
            "local-only-change-this", "password", "changeme"):
        problems.append("Substitua POSTGRES_PASSWORD por senha privada forte (16+ caracteres).")
    if get("MAIL_HOST").lower() in ("localhost", "127.0.0.1", "mailpit", ""):
        problems.append("MAIL_HOST deve apontar para um SMTP externo real.")
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", get("MAIL_FROM")):
        problems.append("MAIL_FROM deve ser um endereço de remetente válido.")
    if get("MAIL_FROM").endswith(".local"):
        problems.append("MAIL_FROM não pode ser remetente de teste .local.")
    if get("MAIL_AUTH").lower() != "true" or len(get("MAIL_PASSWORD")) < 12:
        problems.append("Configure SMTP autenticado e MAIL_PASSWORD real.")
    if (get("MAIL_STARTTLS").lower() != "true" and
            get("MAIL_SSL").lower() != "true"):
        problems.append("Ative MAIL_STARTTLS ou MAIL_SSL para SMTP.")
    if get("APP_JOBS_ENABLED").lower() != "true":
        problems.append("APP_JOBS_ENABLED deve ser true: reservas e e-mails requerem jobs.")
    profile = get("SPRING_PROFILES_ACTIVE") or "prod"
    bootstrap = get("BOOTSTRAP_ADMIN_ENABLED").lower() == "true"
    if bootstrap:
        if profile != "prod,bootstrap":
            problems.append("Na criação do primeiro dono, use SPRING_PROFILES_ACTIVE=prod,bootstrap.")
        if len(get("BOOTSTRAP_ADMIN_PASSWORD")) < 12:
            problems.append("BOOTSTRAP_ADMIN_PASSWORD deve conter ao menos 12 caracteres.")
    elif profile != "prod":
        problems.append("Em operação normal, use SPRING_PROFILES_ACTIVE=prod e bootstrap desativado.")
    return problems


def main():
    path = pathlib.Path(sys.argv[1] if len(sys.argv) == 2 else ".env")
    if not path.is_file():
        print(f"ERRO: arquivo {path} não encontrado.", file=sys.stderr)
        return 1
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        print("ERRO: restrinja o .env com chmod 600 antes de publicar.", file=sys.stderr)
        return 1
    try:
        problems = validate(load_env(path))
    except (OSError, ValueError) as error:
        print(f"ERRO: {error}", file=sys.stderr)
        return 1
    if problems:
        print("Publicação bloqueada até corrigir:", file=sys.stderr)
        for problem in problems:
            print(f" - {problem}", file=sys.stderr)
        return 1
    print("Configuração básica aprovada. Ainda é obrigatória a homologação de HTTPS, SMTP e backups.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
