"""URL configuration for Agronom-IA backend Django."""
from django.urls import include, path

urlpatterns = [
    path("agronomia-api/api/", include("core.urls")),
]
