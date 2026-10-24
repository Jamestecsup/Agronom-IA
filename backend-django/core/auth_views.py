"""Autenticación con roles (login + roles).

Endpoints:
  POST /agronomia-api/api/register  → crea usuario (siempre rol user)
  POST /agronomia-api/api/login     → valida credenciales y devuelve role
"""
import json

from django.contrib.auth import authenticate
from django.contrib.auth.models import User
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt

from core.models import Profile


def _error(status, message):
    return JsonResponse({"success": False, "error": message}, status=status)


@csrf_exempt
def register(request):
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    try:
        data = json.loads(request.body or "{}")
    except json.JSONDecodeError:
        return _error(400, "JSON inválido.")
    username = (data.get("username") or "").strip()
    password = (data.get("password") or "").strip()
    email = (data.get("email") or "").strip()
    # El autorregistro siempre crea cuentas de usuario. El rol admin se asigna
    # desde el servidor (seed_demo o un administrador existente), nunca desde
    # el cliente.
    role = "user"
    if not username or not password:
        return _error(400, "Faltan username o password.")
    if User.objects.filter(username=username).exists():
        return _error(409, "El usuario ya existe.")
    user = User.objects.create_user(username=username, password=password, email=email)
    Profile.objects.create(user=user, role=role)
    return JsonResponse({"success": True, "username": username, "role": role}, status=201)


@csrf_exempt
def login(request):
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    try:
        data = json.loads(request.body or "{}")
    except json.JSONDecodeError:
        return _error(400, "JSON inválido.")
    user = authenticate(
        username=(data.get("username") or "").strip(),
        password=(data.get("password") or "").strip(),
    )
    if user is None:
        return _error(401, "Credenciales inválidas.")
    role = getattr(user.profile, "role", "user")
    return JsonResponse({"success": True, "username": user.username, "role": role})
