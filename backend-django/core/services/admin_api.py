"""Consultas y operaciones de negocio para la API activa del panel admin."""
from datetime import timedelta

from django.contrib.auth.models import User
from django.db.models import Count, Max, Q
from django.db.models.functions import TruncDate
from django.utils import timezone

from core.models import ChatMessage, PlantIdentification, PlantImage, Profile

ROLES = {"user", "admin"}


class AdminOperationError(Exception):
    """Error de negocio que la vista traduce al contrato HTTP existente."""

    def __init__(self, status, message):
        self.status = status
        self.message = message
        super().__init__(message)


def _dt(value):
    return value.isoformat() if value else ""


def database_is_available():
    return User.objects.exists()


def admin_login_expiration():
    return int((timezone.now() + timedelta(days=14)).timestamp())


def dashboard_stats():
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

    return {
        "users": User.objects.count(),
        "plants": PlantIdentification.objects.count(),
        "chats": ChatMessage.objects.count(),
        "images": PlantImage.objects.count(),
        "topSpecies": top_species,
        "daily": daily,
    }


def list_users(query, limit):
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
    return rows


def update_user(actor, user_id, data):
    try:
        target = User.objects.select_related("profile").get(id=user_id)
    except User.DoesNotExist as exc:
        raise AdminOperationError(404, "El usuario no existe.") from exc

    profile, _ = Profile.objects.get_or_create(user=target, defaults={"role": "user"})
    next_role = (data.get("role") or profile.role).strip().lower()
    next_active = target.is_active if data.get("active") is None else bool(data.get("active"))

    if next_role not in ROLES:
        raise AdminOperationError(400, "Rol inválido (user|admin).")
    if actor.id == target.id and (next_role != "admin" or not next_active):
        raise AdminOperationError(400, "No puedes quitarte el rol de administrador ni desactivar tu cuenta.")

    target.is_active = next_active
    target.save(update_fields=["is_active"])
    profile.role = next_role
    profile.save(update_fields=["role"])

    return {
        "id": target.id,
        "username": target.username,
        "email": target.email or "",
        "role": profile.role,
        "active": target.is_active,
        "createdAt": _dt(target.date_joined),
    }


def list_plants(limit):
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
    return rows


def list_chats(limit):
    rows = []
    qs = ChatMessage.objects.select_related("user").order_by("-created_at")[:limit]
    for message in qs:
        rows.append({
            "id": str(message.id),
            "userId": message.user_id,
            "username": message.user.username if message.user else "Sin usuario",
            "sessionId": message.session_id or "",
            "role": message.role,
            "content": message.content,
            "createdAt": _dt(message.created_at),
        })
    return rows
