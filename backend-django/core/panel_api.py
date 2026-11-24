"""API del panel React. La identidad se obtiene solo de la cabecera Bearer."""

import json
import time
from datetime import timedelta, timezone as datetime_timezone
from functools import wraps

from django.contrib.auth import authenticate, get_user_model
from django.core import signing
from django.core.cache import cache
from django.db import DatabaseError, connection, transaction
from django.db.models import Count, Max, Q
from django.db.models.functions import TruncDate
from django.http import JsonResponse
from django.utils import timezone

from core.models import ChatMessage, PlantIdentification, PlantImage, Profile

User = get_user_model()
TOKEN_SALT = "agronomia.panel-admin"
SESSION_SECONDS = 8 * 60 * 60
LOGIN_WINDOW_SECONDS = 5 * 60


class PanelError(Exception):
    def __init__(self, status, message):
        self.status = status
        self.message = message


def endpoint(method, *, public=False):
    def configure(view):
        @wraps(view)
        def handler(request, **kwargs):
            try:
                if request.method != method:
                    response = error(405, "Método no permitido.")
                    response["Allow"] = method
                    return response
                actor = None if public else _actor(request)
                return view(request, actor, **kwargs)
            except PanelError as exc:
                return error(exc.status, exc.message)
            except DatabaseError:
                return error(503, "La base de datos no está disponible.")
        return handler
    return configure


def error(status, message):
    return JsonResponse({"success": False, "error": message}, status=status)


def _body(request):
    try:
        payload = json.loads(request.body or "{}")
    except (ValueError, UnicodeError) as exc:
        raise PanelError(400, "JSON inválido.") from exc
    if not isinstance(payload, dict):
        raise PanelError(400, "Envía un objeto JSON.")
    return payload


def _actor(request):
    scheme, _, token = request.headers.get("Authorization", "").partition(" ")
    if scheme.lower() == "bearer" and token and len(token) <= 512:
        try:
            payload = signing.loads(token.strip(), salt=TOKEN_SALT, max_age=SESSION_SECONDS)
            if isinstance(payload, dict) and type(payload.get("uid")) is int:
                actor = User.objects.filter(pk=payload["uid"], is_active=True, profile__role="admin").first()
                if actor is not None:
                    return actor
        except signing.BadSignature:
            pass
    raise PanelError(401, "Sesión no válida o caducada. Vuelve a entrar.")


def _reserve_attempt(account, address):
    # La implementación local usa el cache de Django. En varios procesos se
    # configura un cache compartido para que el límite se aplique al conjunto.
    from hashlib import sha256
    keys = [
        ("panel-account:" + sha256(account.encode()).hexdigest(), 5),
        ("panel-ip:" + sha256(address.encode()).hexdigest(), 30),
    ]
    for key, limit in keys:
        cache.add(key, 0, timeout=LOGIN_WINDOW_SECONDS)
        try:
            count = cache.incr(key)
        except ValueError:
            cache.add(key, 1, timeout=LOGIN_WINDOW_SECONDS)
            count = 1
        if count > limit:
            raise PanelError(429, "Demasiados intentos. Inténtalo en unos minutos.")
    return keys[0][0]


@endpoint("POST", public=True)
def login(request, _actor_unused):
    data = _body(request)
    username = data.get("username")
    password = data.get("password")
    if not isinstance(username, str) or not username.strip() or not isinstance(password, str) or not password:
        raise PanelError(400, "Faltan usuario o contraseña.")
    username = username.strip()
    if len(username) > 150 or len(password) > 1024:
        raise PanelError(401, "Usuario o contraseña incorrectos.")
    account_key = _reserve_attempt(username.casefold(), request.META.get("REMOTE_ADDR", ""))
    # ModelBackend verifica el hash y evita revelar por el tiempo si existe el usuario.
    user = authenticate(request, username=username, password=password)
    if user is None:
        inactive = User.objects.filter(username=username, is_active=False).first()
        if inactive is not None and inactive.check_password(password):
            raise PanelError(403, "La cuenta está desactivada.")
        raise PanelError(401, "Usuario o contraseña incorrectos.")
    if getattr(getattr(user, "profile", None), "role", None) != "admin":
        raise PanelError(403, "Esta cuenta no tiene rol de administrador.")
    cache.delete(account_key)
    return JsonResponse({
        "success": True,
        "token": signing.dumps({"uid": user.pk}, salt=TOKEN_SALT),
        "username": user.username,
        "role": "admin",
        "expiresAt": int(time.time()) + SESSION_SECONDS,
    })


@endpoint("GET")
def me(_request, actor):
    return JsonResponse({"username": actor.username, "role": "admin"})


@endpoint("GET", public=True)
def health(_request, _actor_unused):
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            database = cursor.fetchone()[0] == 1
    except DatabaseError:
        database = False
    return JsonResponse({"status": "ok", "service": "admin", "database": database})


@endpoint("GET")
def stats(_request, _actor_unused):
    top = (
        PlantIdentification.objects.values("scientific_name")
        .annotate(common_name_max=Max("common_name"), count=Count("id"))
        .order_by("-count", "scientific_name")[:5]
    )
    since = timezone.now().replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=13)
    daily = (
        PlantIdentification.objects.filter(created_at__gte=since)
        .annotate(day=TruncDate("created_at", tzinfo=datetime_timezone.utc)).values("day")
        .annotate(count=Count("id")).order_by("day")
    )
    return JsonResponse({
        "users": User.objects.count(),
        "plants": PlantIdentification.objects.count(),
        "chats": ChatMessage.objects.count(),
        "images": PlantImage.objects.count(),
        "topSpecies": [{"scientificName": row["scientific_name"], "commonName": row["common_name_max"] or "",
                        "count": row["count"]} for row in top],
        "daily": [{"date": row["day"].isoformat(), "count": row["count"]} for row in daily],
    })


def _limit(request, default):
    try:
        return min(200, max(1, int(request.GET.get("limit", default))))
    except ValueError as exc:
        raise PanelError(400, "Límite inválido.") from exc


def _date(value):
    return value.replace(tzinfo=None).isoformat() if value else ""


def _user_row(user):
    return {"id": user.pk, "username": user.username, "email": user.email,
            "role": getattr(getattr(user, "profile", None), "role", "user"), "active": user.is_active,
            "createdAt": _date(user.date_joined)}


@endpoint("GET")
def users(request, _actor_unused):
    term = request.GET.get("q", "").strip()
    rows = User.objects.select_related("profile").order_by("id")
    if term:
        rows = rows.filter(Q(username__icontains=term) | Q(email__icontains=term))
    return JsonResponse([_user_row(row) for row in rows[:_limit(request, 100)]], safe=False)


@endpoint("PATCH")
def update_user(request, actor, user_id):
    data = _body(request)
    role = data.get("role")
    active = data.get("active")
    if role is None and active is None:
        raise PanelError(400, "Envía role y/o active para actualizar la cuenta.")
    if role is not None and (not isinstance(role, str) or role.strip().lower() not in {"user", "admin"}):
        raise PanelError(400, "Rol inválido (user|admin).")
    if active is not None and not isinstance(active, bool):
        raise PanelError(400, "Estado de cuenta inválido.")
    with transaction.atomic():
        user = User.objects.select_for_update().filter(pk=user_id).first()
        if user is None:
            raise PanelError(404, "El usuario no existe.")
        next_role = role.strip().lower() if role is not None else _user_row(user)["role"]
        next_active = user.is_active if active is None else active
        if actor.pk == user.pk and (next_role != "admin" or not next_active):
            raise PanelError(400, "No puedes quitarte el rol de administrador ni desactivar tu cuenta.")
        user.is_active = next_active
        user.save(update_fields=["is_active"])
        profile, _ = Profile.objects.update_or_create(user=user, defaults={"role": next_role})
        user.profile = profile
        return JsonResponse(_user_row(user))


def _activity_row(item):
    item_id = getattr(item.pk, "hex", str(item.pk))
    row = {"id": item_id, "username": item.user.username if item.user_id is not None else "Sin usuario",
           "createdAt": _date(item.created_at)}
    if item.user_id is not None:
        row["userId"] = item.user_id
    return row


@endpoint("GET")
def plants(request, _actor_unused):
    rows = PlantIdentification.objects.select_related("user").order_by("-created_at")[:_limit(request, 50)]
    return JsonResponse([_activity_row(row) | {"scientificName": row.scientific_name, "commonName": row.common_name,
           "family": row.family, "confidence": row.confidence} for row in rows], safe=False)


@endpoint("GET")
def chats(request, _actor_unused):
    rows = ChatMessage.objects.select_related("user").order_by("-created_at")[:_limit(request, 50)]
    return JsonResponse([_activity_row(row) | {"sessionId": row.session_id, "role": row.role,
            "content": row.content} for row in rows], safe=False)
