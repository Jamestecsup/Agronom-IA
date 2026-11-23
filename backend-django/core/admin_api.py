"""API del panel de administración (consumida por web-admin).

Reemplaza el servicio Spring Boot admin: mismo contrato JSON/camelCase que
espera el frontend React, pero servido desde Django sobre la misma BD.
"""
import json
from datetime import timedelta

from django.contrib.auth import authenticate
from django.contrib.auth.models import User
from django.db.models import Count, Max, Q
from django.db.models.functions import TruncDate
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt

from core.authentication import issue_user_token, user_from_request
from core.models import ChatMessage, PlantIdentification, PlantImage, Profile

ROLES = {"user", "admin"}


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


def _dt(value):
    return value.isoformat() if value else ""


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
        "expiresAt": int((timezone.now() + timedelta(days=14)).timestamp()),
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
    db_ok = True
    try:
        User.objects.exists()
    except Exception:
        db_ok = False
    return JsonResponse({"status": "ok", "service": "admin", "database": db_ok})


def stats(request):
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    _, err = _admin_user(request)
    if err:
        return err

    top_species = list(
        PlantIdentification.objects.values("scientific_name")
        .annotate(common_name=Max("common_name"), count=Count("id"))
        .order_by("-count", "scientific_name")[:5]
    )
    top_species = [
        {
            "scientificName": row["scientific_name"],
            "commonName": row["common_name"] or "",
            "count": row["count"],
        }
        for row in top_species
    ]

    since = timezone.localdate() - timedelta(days=13)
    daily_rows = (
        PlantIdentification.objects.filter(created_at__date__gte=since)
        .annotate(day=TruncDate("created_at"))
        .values("day")
        .annotate(count=Count("id"))
        .order_by("day")
    )
    daily = [{"date": str(row["day"]), "count": row["count"]} for row in daily_rows]

    return JsonResponse({
        "users": User.objects.count(),
        "plants": PlantIdentification.objects.count(),
        "chats": ChatMessage.objects.count(),
        "images": PlantImage.objects.count(),
        "topSpecies": top_species,
        "daily": daily,
    })


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

    qs = User.objects.select_related("profile").order_by("id")
    if query:
        qs = qs.filter(Q(username__icontains=query) | Q(email__icontains=query))

    rows = []
    for user in qs[:limit]:
        profile = getattr(user, "profile", None)
        rows.append({
            "id": user.id,
            "username": user.username,
            "email": user.email or "",
            "role": getattr(profile, "role", "user"),
            "active": user.is_active,
            "createdAt": _dt(user.date_joined),
        })
    return JsonResponse(rows, safe=False)


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
        target = User.objects.select_related("profile").get(id=user_id)
    except User.DoesNotExist:
        return _error(404, "El usuario no existe.")

    profile, _ = Profile.objects.get_or_create(user=target, defaults={"role": "user"})
    next_role = (data.get("role") or profile.role).strip().lower()
    next_active = target.is_active if data.get("active") is None else bool(data.get("active"))

    if next_role not in ROLES:
        return _error(400, "Rol inválido (user|admin).")
    if actor.id == target.id and (next_role != "admin" or not next_active):
        return _error(400, "No puedes quitarte el rol de administrador ni desactivar tu cuenta.")

    target.is_active = next_active
    target.save(update_fields=["is_active"])
    profile.role = next_role
    profile.save(update_fields=["role"])

    return JsonResponse({
        "id": target.id,
        "username": target.username,
        "email": target.email or "",
        "role": profile.role,
        "active": target.is_active,
        "createdAt": _dt(target.date_joined),
    })


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

    rows = []
    qs = PlantIdentification.objects.select_related("user").order_by("-created_at")[:limit]
    for plant in qs:
        rows.append({
            "id": str(plant.id),
            "userId": plant.user_id,
            "username": plant.user.username if plant.user else "Sin usuario",
            "scientificName": plant.scientific_name,
            "commonName": plant.common_name or "",
            "family": plant.family or "",
            "confidence": float(plant.confidence),
            "createdAt": _dt(plant.created_at),
        })
    return JsonResponse(rows, safe=False)


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

    rows = []
    qs = ChatMessage.objects.select_related("user").order_by("-created_at")[:limit]
    for msg in qs:
        rows.append({
            "id": str(msg.id),
            "userId": msg.user_id,
            "username": msg.user.username if msg.user else "Sin usuario",
            "sessionId": msg.session_id or "",
            "role": msg.role,
            "content": msg.content,
            "createdAt": _dt(msg.created_at),
        })
    return JsonResponse(rows, safe=False)
