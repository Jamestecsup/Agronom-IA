"""URL configuration for Agronom-IA backend Django."""
from django.contrib import admin
from django.urls import path

from core import admin_api

urlpatterns = [
    path("admin/", admin.site.urls),
    # Panel web admin: Django toma esta responsabilidad (antes en Spring :8081).
    path("api/admin/auth/login", admin_api.login),
    path("api/admin/auth/me", admin_api.me),
    path("api/admin/health", admin_api.health),
    path("api/admin/stats", admin_api.stats),
    path("api/admin/users", admin_api.users),
    path("api/admin/users/<int:user_id>", admin_api.update_user),
    path("api/admin/plants", admin_api.plants),
    path("api/admin/chats", admin_api.chats),
]
