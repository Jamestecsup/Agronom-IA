"""API del panel de administración (consumida por web-admin).

Reemplaza el servicio Spring Boot admin: mismo contrato JSON/camelCase que
espera el frontend React, pero servido desde Django sobre la misma BD.
"""
import json

from django.contrib.auth import authenticate
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt

from core.authentication import issue_user_token, user_from_request
from core.services import admin_api as admin_service
from core.services.admin_api import AdminOperationError


def _error(status, message):
    return JsonResponse({"success": False, "error": message}, status=status)


def _body(request):
    try:
        return json.loads(request.body or "{}")
    except json.JSONDecodeError:
        return None


def _admin_user(request):
    """Devuelve el usuario autenticado solo si tiene rol admin activo."""
    user = user_from_request(request)
    if user is None:
        return None, _error(401, "Sesión no válida o caducada. Vuelve a entrar.")
    role = getattr(getattr(user, "profile", None), "role", "user")
    if not user.is_active:
        return None, _error(403, "La cuenta está desactivada.")
    if role != "admin":
        return None, _error(403, "Esta cuenta no tiene rol de administrador.")
    return user, None


@csrf_exempt
def login(request):
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    data = _body(request)
    if data is None:
        return _error(400, "El cuerpo de la petición no es JSON válido.")
    username = (data.get("username") or "").strip()
    password = data.get("password") or ""
    if not username or not password:
        return _error(400, "Faltan usuario o contraseña.")

    user = authenticate(username=username, password=password)
    if user is None:
        return _error(401, "Usuario o contraseña incorrectos.")
    if not user.is_active:
        return _error(403, "La cuenta está desactivada.")
    role = getattr(getattr(user, "profile", None), "role", "user")
    if role != "admin":
        return _error(403, "Esta cuenta no tiene rol de administrador.")

    return JsonResponse({
        "success": True,
        "token": issue_user_token(user),
        "username": user.username,
        "role": "admin",
        "expiresAt": admin_service.admin_login_expiration(),
    })


def me(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    user, err = _admin_user(request)
    if err:
        return err
    return JsonResponse({"username": user.username, "role": "admin"})


def health(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    try:
        db_ok = admin_service.database_is_available()
    except Exception:
        db_ok = False
    return JsonResponse({"status": "ok", "service": "admin", "database": db_ok})


def stats(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    _, err = _admin_user(request)
    if err:
        return err

    return JsonResponse(admin_service.dashboard_stats())


def users(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    _, err = _admin_user(request)
    if err:
        return err

    query = (request.GET.get("q") or "").strip()
    try:
        limit = max(1, min(int(request.GET.get("limit", 100)), 200))
    except ValueError:
        limit = 100

    return JsonResponse(admin_service.list_users(query, limit), safe=False)


@csrf_exempt
def update_user(request, user_id):
    if request.method != "PATCH":
        return _error(405, "Método no permitido; usa PATCH.")
    actor, err = _admin_user(request)
    if err:
        return err
    data = _body(request)
    if data is None:
        return _error(400, "El cuerpo de la petición no es JSON válido.")
    if data.get("role") is None and data.get("active") is None:
        return _error(400, "Envía role y/o active para actualizar la cuenta.")

    try:
        result = admin_service.update_user(actor, user_id, data)
    except AdminOperationError as exc:
        return _error(exc.status, exc.message)
    return JsonResponse(result)


def plants(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    _, err = _admin_user(request)
    if err:
        return err
    try:
        limit = max(1, min(int(request.GET.get("limit", 50)), 200))
    except ValueError:
        limit = 50

    return JsonResponse(admin_service.list_plants(limit), safe=False)


def chats(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    _, err = _admin_user(request)
    if err:
        return err
    try:
        limit = max(1, min(int(request.GET.get("limit", 50)), 200))
    except ValueError:
        limit = 50

    return JsonResponse(admin_service.list_chats(limit), safe=False)
