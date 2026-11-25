"""Pruebas de integración para endpoints admin de Django."""

import json

from django.contrib.auth.models import User
from django.test import TestCase

from core.authentication import issue_user_token
from core.models import ChatMessage, PlantIdentification, PlantImage, Profile


class AdminApiIntegrationTests(TestCase):
    def setUp(self):
        self.admin = User.objects.create_user(username="admin", password="Admin12345")
        Profile.objects.create(user=self.admin, role="admin")
        self.user = User.objects.create_user(username="user", password="User12345")
        Profile.objects.create(user=self.user, role="user")

    def test_admin_login_and_stats_contract(self):
        response = self.client.post(
            "/api/admin/auth/login",
            data=json.dumps({"username": "admin", "password": "Admin12345"}),
            content_type="application/json",
        )
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertTrue(body["success"])
        self.assertEqual(body["role"], "admin")
        self.assertIn("token", body)

        stats = self.client.get("/api/admin/stats", HTTP_AUTHORIZATION=f"Bearer {body['token']}")
        self.assertEqual(stats.status_code, 200)
        stats_body = stats.json()
        for key in ["users", "plants", "chats", "images", "topSpecies", "daily"]:
            self.assertIn(key, stats_body)

    def test_non_admin_cannot_access_admin_stats(self):
        login = self.client.post(
            "/api/admin/auth/login",
            data=json.dumps({"username": "user", "password": "User12345"}),
            content_type="application/json",
        )
        self.assertEqual(login.status_code, 403)

    def test_admin_users_requires_auth(self):
        response = self.client.get("/api/admin/users")
        self.assertEqual(response.status_code, 401)

    def admin_token(self):
        response = self.client.post(
            "/api/admin/auth/login",
            data=json.dumps({"username": "admin", "password": "Admin12345"}),
            content_type="application/json",
        )
        return response.json()["token"]

    def test_admin_login_rejects_bad_credentials_and_non_admin_role(self):
        bad_password = self.client.post(
            "/api/admin/auth/login",
            data=json.dumps({"username": "admin", "password": "wrong"}),
            content_type="application/json",
        )
        non_admin = self.client.post(
            "/api/admin/auth/login",
            data=json.dumps({"username": "user", "password": "User12345"}),
            content_type="application/json",
        )
        self.assertEqual(bad_password.status_code, 401)
        self.assertEqual(bad_password.json(), {
            "success": False, "error": "Usuario o contraseña incorrectos."
        })
        self.assertEqual(non_admin.status_code, 403)
        self.assertEqual(non_admin.json()["error"], "Esta cuenta no tiene rol de administrador.")

    def test_admin_me_requires_admin_role(self):
        response = self.client.get("/api/admin/auth/me", HTTP_AUTHORIZATION=f"Bearer {self.admin_token()}")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"username": "admin", "role": "admin"})

        user_token = self.client.post(
            "/api/admin/auth/login",
            data=json.dumps({"username": "user", "password": "User12345"}),
            content_type="application/json",
        )
        # A regular user cannot obtain an admin token through login.
        self.assertEqual(user_token.status_code, 403)

    def test_user_role_token_is_forbidden_on_admin_routes(self):
        token = issue_user_token(self.user)
        headers = {"HTTP_AUTHORIZATION": f"Bearer {token}"}
        requests = [
            self.client.get("/api/admin/auth/me", **headers),
            self.client.get("/api/admin/stats", **headers),
            self.client.get("/api/admin/users", **headers),
            self.client.get("/api/admin/plants", **headers),
            self.client.get("/api/admin/chats", **headers),
            self.client.patch(
                f"/api/admin/users/{self.user.id}",
                data=json.dumps({"active": True}),
                content_type="application/json",
                **headers,
            ),
        ]
        for response in requests:
            self.assertEqual(response.status_code, 403)
            self.assertEqual(
                response.json()["error"], "Esta cuenta no tiene rol de administrador."
            )

    def test_users_contract_filter_and_limit(self):
        token = self.admin_token()
        response = self.client.get(
            "/api/admin/users?q=us&limit=1", HTTP_AUTHORIZATION=f"Bearer {token}"
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.json()), 1)
        self.assertEqual(response.json()[0]["username"], "user")
        self.assertEqual(
            set(response.json()[0]), {"id", "username", "email", "role", "active", "createdAt"}
        )

    def test_patch_user_updates_role_and_active_and_protects_actor(self):
        token = self.admin_token()
        response = self.client.patch(
            f"/api/admin/users/{self.user.id}",
            data=json.dumps({"role": "admin", "active": False}),
            content_type="application/json",
            HTTP_AUTHORIZATION=f"Bearer {token}",
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["role"], "admin")
        self.assertFalse(response.json()["active"])

        self_response = self.client.patch(
            f"/api/admin/users/{self.admin.id}",
            data=json.dumps({"active": False}),
            content_type="application/json",
            HTTP_AUTHORIZATION=f"Bearer {token}",
        )
        self.assertEqual(self_response.status_code, 400)
        self.assertEqual(
            self_response.json()["error"],
            "No puedes quitarte el rol de administrador ni desactivar tu cuenta.",
        )

    def test_patch_user_invalid_role_and_missing_user(self):
        token = self.admin_token()
        invalid = self.client.patch(
            f"/api/admin/users/{self.user.id}", data=json.dumps({"role": "root"}),
            content_type="application/json", HTTP_AUTHORIZATION=f"Bearer {token}",
        )
        missing = self.client.patch(
            "/api/admin/users/99999", data=json.dumps({"active": True}),
            content_type="application/json", HTTP_AUTHORIZATION=f"Bearer {token}",
        )
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(invalid.json()["error"], "Rol inválido (user|admin).")
        self.assertEqual(missing.status_code, 404)
        self.assertEqual(missing.json()["error"], "El usuario no existe.")

    def test_plants_and_chats_contract(self):
        plant = PlantIdentification.objects.create(
            user=self.user, batch_id="batch-1", scientific_name="Solanum lycopersicum",
            common_name="Tomate", family="Solanaceae", confidence=0.91, result_json={},
        )
        ChatMessage.objects.create(
            user=self.user, session_id="session-1", role="user", content="¿Cómo está?"
        )
        PlantImage.objects.create(
            id="image-1", batch_id="batch-1", relative_path="batch-1/image.jpg",
            mime_type="image/jpeg", file_size=123, width=10, height=10,
        )
        response_plants = self.client.get(
            "/api/admin/plants", HTTP_AUTHORIZATION=f"Bearer {self.admin_token()}"
        )
        response_chats = self.client.get(
            "/api/admin/chats", HTTP_AUTHORIZATION=f"Bearer {self.admin_token()}"
        )
        self.assertEqual(response_plants.status_code, 200)
        self.assertEqual(response_plants.json()[0], {
            "id": str(plant.id), "userId": self.user.id, "username": "user",
            "scientificName": "Solanum lycopersicum", "commonName": "Tomate",
            "family": "Solanaceae", "confidence": 0.91,
            "createdAt": plant.created_at.isoformat(),
        })
        self.assertEqual(response_chats.status_code, 200)
        self.assertEqual(response_chats.json()[0]["username"], "user")
        self.assertEqual(response_chats.json()[0]["sessionId"], "session-1")
