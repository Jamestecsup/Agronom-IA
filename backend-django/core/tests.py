"""Contrato y seguridad del panel Django, sin servicios externos."""

from secrets import token_urlsafe

from django.contrib.auth import get_user_model
from django.core import signing
from django.core.cache import cache
from django.test import Client, TestCase

from core.models import Profile
from core.panel_api import TOKEN_SALT


class PanelContractTests(TestCase):
    def setUp(self):
        cache.clear()
        self.client = Client(enforce_csrf_checks=True)
        self.password = token_urlsafe(24)
        model = get_user_model()
        self.admin = model.objects.create_user("panel_admin", password=self.password)
        Profile.objects.create(user=self.admin, role="admin")
        self.user = model.objects.create_user("usuario_prueba", password=self.password)
        Profile.objects.create(user=self.user)

    def login(self, username, password=None):
        return self.client.post("/api/admin/auth/login", {
            "username": username, "password": password or self.password,
        }, content_type="application/json")

    def authorization(self):
        login = self.login(self.admin.username)
        self.assertEqual(login.status_code, 200)
        return {"HTTP_AUTHORIZATION": "Bearer " + login.json()["token"]}

    def test_panel_preserves_json_and_token_without_csrf_cookie(self):
        bearer = self.authorization()
        self.assertNotIn("csrftoken", self.client.cookies)
        self.assertEqual(self.client.get("/api/admin/auth/me", **bearer).json(),
                         {"username": self.admin.username, "role": "admin"})
        stats = self.client.get("/api/admin/stats", **bearer)
        self.assertEqual(stats.status_code, 200)
        self.assertEqual(set(stats.json()), {"users", "plants", "chats", "images", "topSpecies", "daily"})
        users = self.client.get("/api/admin/users", {"q": self.user.username}, **bearer)
        self.assertEqual(users.json()[0]["id"], self.user.pk)
        updated = self.client.patch(f"/api/admin/users/{self.user.pk}", {"active": False},
                                    content_type="application/json", **bearer)
        self.assertEqual(updated.status_code, 200)
        self.assertFalse(updated.json()["active"])

    def test_panel_rejects_non_admin_and_revokes_disabled_admin(self):
        self.assertEqual(self.login(self.user.username).status_code, 403)
        bearer = self.authorization()
        self.admin.is_active = False
        self.admin.save(update_fields=["is_active"])
        self.assertEqual(self.client.get("/api/admin/auth/me", **bearer).status_code, 401)

    def test_expired_token_and_native_admin_csrf(self):
        from unittest.mock import patch
        with patch("django.core.signing.time.time", return_value=1):
            token = signing.dumps({"uid": self.admin.pk}, salt=TOKEN_SALT)
        self.assertEqual(self.client.get("/api/admin/auth/me", HTTP_AUTHORIZATION="Bearer " + token).status_code, 401)
        self.assertEqual(self.client.post("/admin/login/").status_code, 403)
        self.assertEqual(self.client.get("/agronomia-api/api/health").status_code, 404)

    def test_panel_login_temporarily_limits_attempts(self):
        incorrect = token_urlsafe(24)
        for _ in range(5):
            self.assertEqual(self.login(self.admin.username, incorrect).status_code, 401)
        self.assertEqual(self.login(self.admin.username).status_code, 429)
