"""Testes locais e em CI para impedir publicar configurações de desenvolvimento."""
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "preflight-production.py"
VALID = """APP_DOMAIN=loja.ferragens.com.br
APP_PUBLIC_URL=https://loja.ferragens.com.br
POSTGRES_DB=ferragens
POSTGRES_USER=ferragens
POSTGRES_PASSWORD=senha-suficientemente-longa-123
MAIL_HOST=smtp.provedor.com.br
MAIL_PORT=587
MAIL_FROM=contato@ferragens.com.br
MAIL_USERNAME=contato@ferragens.com.br
MAIL_PASSWORD=credencial-smtp-privada-1234
MAIL_AUTH=true
MAIL_STARTTLS=true
MAIL_SSL=false
APP_JOBS_ENABLED=true
SESSION_COOKIE_SECURE=true
BOOTSTRAP_ADMIN_ENABLED=false
SPRING_PROFILES_ACTIVE=prod
"""


def preflight(text):
    with tempfile.TemporaryDirectory() as folder:
        env = pathlib.Path(folder) / ".env"
        env.write_text(text, encoding="utf-8")
        env.chmod(0o600)
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(env)],
            check=False, text=True, capture_output=True
        )


class ProductionPreflightTest(unittest.TestCase):
    def test_accepts_configured_production(self):
        result = preflight(VALID)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_sample_password(self):
        result = preflight(VALID.replace("senha-suficientemente-longa-123", "local-only-change-this"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("POSTGRES_PASSWORD", result.stderr)

    def test_rejects_http_and_example_domain(self):
        text = VALID.replace("loja.ferragens.com.br", "example.test").replace("https://", "http://")
        result = preflight(text)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("APP_DOMAIN", result.stderr)

    def test_rejects_missing_bootstrap_profile(self):
        result = preflight(VALID.replace("BOOTSTRAP_ADMIN_ENABLED=false",
                                         "BOOTSTRAP_ADMIN_ENABLED=true"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("prod,bootstrap", result.stderr)

    def test_accepts_first_owner_bootstrap(self):
        text = VALID.replace("BOOTSTRAP_ADMIN_ENABLED=false",
                             "BOOTSTRAP_ADMIN_ENABLED=true\nBOOTSTRAP_ADMIN_PASSWORD=senha-primeiro-dono-123")
        text = text.replace("SPRING_PROFILES_ACTIVE=prod", "SPRING_PROFILES_ACTIVE=prod,bootstrap")
        result = preflight(text)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_smtp_without_tls(self):
        result = preflight(VALID.replace("MAIL_STARTTLS=true", "MAIL_STARTTLS=false"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("MAIL_STARTTLS", result.stderr)

    def test_does_not_disclose_secrets_on_failure(self):
        text = VALID.replace("SESSION_COOKIE_SECURE=true", "SESSION_COOKIE_SECURE=false")
        result = preflight(text)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("senha-suficientemente-longa-123", result.stderr)
        self.assertNotIn("credencial-smtp-privada-1234", result.stderr)


if __name__ == "__main__":
    unittest.main()
