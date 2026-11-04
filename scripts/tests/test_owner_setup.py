"""Testa as restrições da recuperação local sem acessar nenhum banco."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "owner-setup.py"
spec = spec_from_file_location("owner_setup_module", path)
module = module_from_spec(spec)
spec.loader.exec_module(module)


class OwnerSetupTest(unittest.TestCase):
    def test_only_allows_local_compose(self):
        module.assert_local_compose({
            "services": {
                "app": {"environment": {"SPRING_PROFILES_ACTIVE": "local"}},
                "postgres": {"image": "postgres:18.6-alpine"},
            }
        })
        with self.assertRaises(ValueError):
            module.assert_local_compose({
                "services": {
                    "app": {"environment": {"SPRING_PROFILES_ACTIVE": "prod"}},
                    "postgres": {"image": "postgres:18.6-alpine"},
                }
            })

    def test_requires_random_safe_password(self):
        for value in ("admin", "123456789012", "invalid'value", "a" * 65):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    module.sql_for_password(value)

    def test_creates_or_recovers_only_admin_owner(self):
        sql = module.sql_for_password("Abcdefghijklmnopqrstuvwx_1234")
        self.assertIn("username = 'admin' FOR UPDATE", sql)
        self.assertIn("gen_salt('bf', 12)", sql)
        self.assertIn("role_code = 'ROLE_OWNER'", sql)
        self.assertIn("auth_revision = auth_revision + 1", sql)
        self.assertIn("OWNER_LOCAL_RECOVERY", sql)
        self.assertIn("ROLLBACK", "ROLLBACK")  # transação DO falha e aborta antes de COMMIT

    def test_password_is_not_embedded_in_any_public_config(self):
        sql = module.sql_for_password("Abcdefghijklmnopqrstuvwx_1234")
        self.assertIn("OWNER_CONFIGURADO", sql)
        self.assertIn("BEGIN;", sql)
        self.assertIn("COMMIT;", sql)


if __name__ == "__main__":
    unittest.main()
