from django.urls import path
from django.views.decorators.csrf import csrf_exempt

from core import panel_api

# Solo la API del panel usa Bearer. El sitio /admin/ conserva CSRF.
urlpatterns = [
    path("auth/login", csrf_exempt(panel_api.login)),
    path("auth/me", csrf_exempt(panel_api.me)),
    path("health", csrf_exempt(panel_api.health)),
    path("stats", csrf_exempt(panel_api.stats)),
    path("users", csrf_exempt(panel_api.users)),
    path("users/<int:user_id>", csrf_exempt(panel_api.update_user)),
    path("plants", csrf_exempt(panel_api.plants)),
    path("chats", csrf_exempt(panel_api.chats)),
]
