"""Pruebas de integración para endpoints admin de Django."""

import json

from django.contrib.auth.models import User
from django.test import TestCase

from core.models import Profile


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
