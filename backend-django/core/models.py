"""Modelos del backend Django.

- PlantImage: reutiliza la tabla `imagenes_planta` creada originalmente por el backend de
  Fernando (no gestionada por Django para no alterar su DDL).
- PlantIdentification: resultado del pipeline (Pl@ntNet + Gemini con skills).
- ChatMessage: historial del chat con Gemini.
- Profile: rol del usuario (user/admin) para el login con roles.
"""
import uuid

from django.contrib.auth.models import User
from django.db import models


class PlantImage(models.Model):
    """Imagen subida por el usuario (tabla heredada `imagenes_planta`)."""

    id = models.CharField(max_length=36, primary_key=True)
    user = models.ForeignKey(
        User, null=True, blank=True, on_delete=models.SET_NULL,
        db_column="user_id", related_name="imagenes_planta",
    )
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
        db_table = "imagenes_planta"
        verbose_name = "imagen de planta"
        verbose_name_plural = "imágenes de plantas"

    def __str__(self):
        return self.relative_path


class PlantIdentification(models.Model):
    """Resultado del pipeline de identificación + información."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        User, null=True, blank=True, on_delete=models.SET_NULL,
        related_name="plant_identifications",
    )
    batch_id = models.CharField(max_length=36, db_index=True)
    scientific_name = models.CharField(max_length=255, db_index=True)
    common_name = models.CharField(max_length=255, blank=True)
    family = models.CharField(max_length=255, blank=True)
    confidence = models.FloatField()
    # Ficha completa generada por Gemini (JSON con las 8 categorías).
    result_json = models.JSONField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "identificaciones_planta"
        verbose_name = "identificación de planta"
        verbose_name_plural = "identificaciones de plantas"

    def __str__(self):
        return f"{self.scientific_name} ({self.confidence:.2f})"


class IdentificationRun(models.Model):
    """Corrida de Pl@ntNet de un lote (para resolver la candidata elegida).

    Guarda el JSON crudo de Pl@ntNet para que `/confirm` no tenga que volver a
    identificar cuando el agricultor elige una especie entre las candidatas.
    """

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        User, null=True, blank=True, on_delete=models.SET_NULL,
        related_name="identification_runs",
    )
    batch_id = models.CharField(max_length=36, db_index=True)
    organ = models.CharField(max_length=16, default="auto")
    status = models.CharField(max_length=16)
    raw_json = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "procesos_identificacion"
        verbose_name = "proceso de identificación"
        verbose_name_plural = "procesos de identificación"

    def __str__(self):
        return f"{self.batch_id} ({self.status})"


class ChatMessage(models.Model):
    """Historial del chat con Gemini."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        User, null=True, blank=True, on_delete=models.SET_NULL,
        related_name="chat_messages",
    )
    session_id = models.CharField(max_length=64, db_index=True)
    role = models.CharField(max_length=16)  # user | assistant
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "mensajes_chat"
        verbose_name = "mensaje de chat"
        verbose_name_plural = "mensajes de chat"

    def __str__(self):
        return f"{self.role}: {self.content[:40]}"


class ChatProgressImage(models.Model):
    """Imagen de progreso adjunta a un mensaje del chat de una planta."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="plant_progress_images")
    plant = models.ForeignKey(PlantIdentification, on_delete=models.CASCADE, related_name="progress_images")
    message = models.ForeignKey(ChatMessage, on_delete=models.CASCADE, related_name="progress_images")
    relative_path = models.CharField(max_length=512, unique=True)
    mime_type = models.CharField(max_length=32)
    file_size = models.BigIntegerField()
    caption = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "imagenes_progreso_chat"
        verbose_name = "imagen de progreso del chat"
        verbose_name_plural = "imágenes de progreso del chat"
        ordering = ("created_at",)

    def __str__(self):
        return f"Progreso de {self.plant_id} ({self.created_at:%Y-%m-%d})"


class PendingScheduleImport(models.Model):
    """Horario subido, analizado y pendiente de confirmación del estudiante."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="pending_schedule_imports")
    source_pdf = models.FileField(upload_to="schedules/pending/%Y/%m/")
    parsed_classes = models.JSONField(default=list)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "importaciones_horario_pendientes"
        verbose_name = "importación pendiente de horario"
        verbose_name_plural = "importaciones pendientes de horario"


class WeeklySchedule(models.Model):
    """Horario oficial semanal confirmado por el usuario."""

    user = models.OneToOneField(User, primary_key=True, on_delete=models.CASCADE, related_name="weekly_schedule")
    classes = models.JSONField(default=list)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "horarios_semanales"
        verbose_name = "horario semanal"
        verbose_name_plural = "horarios semanales"


class CarePlanTask(models.Model):
    """Acción de cuidado recurrente que el usuario confirmó en su plan semanal."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="care_plan_tasks")
    plant = models.ForeignKey(PlantIdentification, on_delete=models.CASCADE, related_name="scheduled_care")
    title = models.CharField(max_length=180)
    instructions = models.TextField(blank=True)
    day_of_week = models.PositiveSmallIntegerField()
    start_time = models.TimeField()
    duration_minutes = models.PositiveSmallIntegerField(default=20)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "tareas_cuidado"
        verbose_name = "tarea de cuidado"
        verbose_name_plural = "tareas de cuidado"
        ordering = ("day_of_week", "start_time")
        indexes = [models.Index(fields=("user", "day_of_week", "start_time"), name="core_carepl_user_id_43a280_idx")]


class Profile(models.Model):
    """Rol del usuario para el login con roles."""

    class Role(models.TextChoices):
        USER = "user", "Usuario"
        ADMIN = "admin", "Administrador"

    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="profile")
    role = models.CharField(max_length=16, choices=Role.choices, default=Role.USER)

    class Meta:
        db_table = "perfiles_usuario"
        verbose_name = "perfil de usuario"
        verbose_name_plural = "perfiles de usuario"

    def __str__(self):
        return f"{self.user.username} ({self.role})"
