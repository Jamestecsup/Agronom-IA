"""Modelos del backend Django.

- PlantImage: reutiliza la tabla `plant_images` creada por el backend de
  Fernando (no gestionada por Django para no alterar su DDL).
- PlantIdentification: resultado del pipeline (Pl@ntNet + Gemini con skills).
- ChatMessage: historial del chat con Gemini.
- Profile: rol del usuario (user/admin) para el login con roles.
"""
import uuid

from django.contrib.auth.models import User
from django.db import models


class PlantImage(models.Model):
    """Imagen subida por el usuario (tabla legada `plant_images`)."""

    id = models.CharField(max_length=36, primary_key=True)
    batch_id = models.CharField(max_length=36)
    relative_path = models.CharField(max_length=255, unique=True)
    mime_type = models.CharField(max_length=32)
    file_size = models.BigIntegerField()
    width = models.IntegerField()
    height = models.IntegerField()
    processing_status = models.CharField(max_length=16, default="pending")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        managed = False
        db_table = "plant_images"

    def __str__(self):
        return self.relative_path


class PlantIdentification(models.Model):
    """Resultado del pipeline de identificación + información."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    batch_id = models.CharField(max_length=36, db_index=True)
    scientific_name = models.CharField(max_length=255, db_index=True)
    common_name = models.CharField(max_length=255, blank=True)
    family = models.CharField(max_length=255, blank=True)
    confidence = models.FloatField()
    # Ficha completa generada por Gemini (JSON con las 8 categorías).
    result_json = models.JSONField()
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.scientific_name} ({self.confidence:.2f})"


class ChatMessage(models.Model):
    """Historial del chat con Gemini."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    session_id = models.CharField(max_length=64, db_index=True)
    role = models.CharField(max_length=16)  # user | assistant
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.role}: {self.content[:40]}"


class Profile(models.Model):
    """Rol del usuario para el login con roles."""

    class Role(models.TextChoices):
        USER = "user", "Usuario"
        ADMIN = "admin", "Administrador"

    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="profile")
    role = models.CharField(max_length=16, choices=Role.choices, default=Role.USER)

    def __str__(self):
        return f"{self.user.username} ({self.role})"
