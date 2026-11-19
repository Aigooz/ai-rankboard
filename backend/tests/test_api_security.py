import importlib
import unittest
from unittest.mock import patch

from fastapi import HTTPException

from app import main


class ApiSecurityTests(unittest.TestCase):
    def test_admin_refresh_is_closed_without_a_valid_token(self):
        with patch.object(main, "ADMIN_TOKEN", "secret"), patch.object(
            main, "ALLOW_UNAUTHENTICATED_REFRESH", False
        ):
            with self.assertRaises(HTTPException) as captured:
                main.require_admin_token(None)
        self.assertEqual(captured.exception.status_code, 401)

    def test_cors_origins_require_explicit_configuration(self):
        from app import config

        # Importing the config must not create a wildcard CORS policy.
        self.assertNotIn("*", config.ALLOWED_ORIGINS)
        self.assertTrue(config.ADMIN_TOKEN)

    def test_explicit_development_escape_hatch_is_available(self):
        from app import config

        with patch.dict(
            "os.environ",
            {"ALLOW_UNAUTHENTICATED_REFRESH": "true", "ALLOWED_ORIGINS": "http://localhost:8080"},
            clear=True,
        ):
            reloaded = importlib.reload(config)
            self.assertTrue(reloaded.ALLOW_UNAUTHENTICATED_REFRESH)
            self.assertEqual(reloaded.ALLOWED_ORIGINS, ["http://localhost:8080"])


if __name__ == "__main__":
    unittest.main()
