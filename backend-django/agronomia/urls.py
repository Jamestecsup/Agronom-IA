"""URL configuration for Agronom-IA backend Django."""
from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/admin/", include("core.panel_urls")),
]
