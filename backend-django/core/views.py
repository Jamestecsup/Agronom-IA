"""Vistas del backend Django (servicio de usuario).

Endpoints:
  GET  /agronomia-api/api/health      → estado del servicio y de la base
  POST /agronomia-api/api/images.php  → subir 1-5 fotos (contrato de Fernando)
  POST /agronomia-api/api/identify    → pipeline Pl@ntNet + Gemini (con skills)
  POST /agronomia-api/api/confirm     → ficha de la candidata elegida
  POST /agronomia-api/api/chat        → chat post-detección
  POST /agronomia-api/api/register    → autorregistro (rol user)
  POST /agronomia-api/api/login       → validación de credenciales
"""
import logging
import os
import uuid

from django.conf import settings
from django.http import FileResponse, JsonResponse
from PIL import Image, UnidentifiedImageError

from core.authentication import user_from_request
from core.models import ChatMessage, ChatProgressImage, IdentificationRun, PlantIdentification, PlantImage
from core.services import chat as chat_service
from core.services import gemini, pipeline

# Formatos aceptados (por contenido, no por extensión).
ALLOWED_MIME = {"image/jpeg", "image/png"}
MAX_IMAGES = settings.MAX_UPLOAD_IMAGES
MAX_BYTES = settings.MAX_IMAGE_BYTES
MIN_DIMENSION = 16
MAX_DIMENSION = 4096
logger = logging.getLogger(__name__)


def _error(status, message):
    return JsonResponse({"success": False, "error": message}, status=status)


def health(request):
    """GET /agronomia-api/api/health — comprueba servicio y conexión a la base.

    No llama a Pl@ntNet ni a la IA: sirve para saber si el backend está listo
    antes de una demo o para un chequeo de despliegue.
    """
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")

    from django.db import connection

    database_ok = True
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
    except Exception:  # noqa: BLE001 - cualquier fallo de base cuenta como caída
        database_ok = False

    return JsonResponse({
        "success": database_ok,
        "status": "ok" if database_ok else "degraded",
        "service": "agronomia-api",
        "database": database_ok,
    })


def _validate_image(uploaded):
    """Valida contenido, formato y dimensiones. Devuelve (bytes, mime, w, h)."""
    data = uploaded.read()
    if not data:
        raise ValueError("El archivo está vacío. Selecciona otra imagen.")
    if len(data) > MAX_BYTES:
        raise ValueError("La foto es demasiado pesada. Elige una imagen más ligera.")

    try:
        img = Image.open(__import__("io").BytesIO(data))
        img.verify()
    except (UnidentifiedImageError, Exception) as exc:
        raise ValueError("No se pudo leer la imagen. Elige una foto JPG o PNG válida.") from exc

    # Reabrir para dimensiones (verify() cierra el objeto).
    img = Image.open(__import__("io").BytesIO(data))
    width, height = img.size
    if width < MIN_DIMENSION or height < MIN_DIMENSION:
        raise ValueError("La imagen es muy pequeña. Usa una foto de mayor resolución.")
    if width > MAX_DIMENSION or height > MAX_DIMENSION:
        raise ValueError("La imagen supera el tamaño permitido. Elige una foto más pequeña.")

    fmt = (img.format or "").lower()
    mime = {"jpeg": "image/jpeg", "jpg": "image/jpeg", "png": "image/png", "webp": "image/webp"}.get(fmt)
    if mime not in ALLOWED_MIME:
        raise ValueError("Formato no admitido. Usa una imagen JPG o PNG.")
    return data, mime, width, height


def upload_images(request):
    """POST /agronomia-api/api/images.php — sube 1-5 fotos y las registra."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para subir fotos.")
    photos = request.FILES.getlist("photos[]")
    if not photos:
        return _error(400, "Falta el campo photos[].")
    if len(photos) > MAX_IMAGES:
        return _error(422, f"Máximo {MAX_IMAGES} fotos por lote.")

    batch_id = str(uuid.uuid4())
    upload_dir = os.path.join(settings.PLANT_UPLOAD_DIR, batch_id)
    os.makedirs(upload_dir, exist_ok=True)

    saved = []
    for uploaded in photos:
        try:
            data, mime, width, height = _validate_image(uploaded)
        except ValueError as exc:
            return _error(422, str(exc))

        image_id = str(uuid.uuid4())
        ext = {"image/jpeg": "jpg", "image/png": "png"}[mime]
        relative_path = os.path.join(batch_id, f"{image_id}.{ext}")
        with open(os.path.join(settings.PLANT_UPLOAD_DIR, relative_path), "wb") as fh:
            fh.write(data)

        PlantImage.objects.create(
            id=image_id, user=user, batch_id=batch_id, relative_path=relative_path,
            mime_type=mime, file_size=len(data), width=width, height=height,
            processing_status="pending",
        )
        saved.append({"id": image_id, "path": relative_path, "mime_type": mime, "size": len(data)})

    return JsonResponse({"success": True, "batch_id": batch_id, "images": saved}, status=201)


def identify(request):
    """POST /agronomia-api/api/identify — ejecuta el pipeline con skills."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para identificar una planta.")
    batch_id = (request.POST.get("batch_id") or "").strip()
    organ = (request.POST.get("organ") or "auto").strip()
    if not batch_id:
        return _error(400, "Falta batch_id.")

    images = list(
        PlantImage.objects.filter(batch_id=batch_id, user=user).order_by("created_at")
    )
    if not images:
        return _error(404, "No se encontraron imágenes para ese batch_id.")

    blobs = []
    for img in images:
        path = os.path.join(settings.PLANT_UPLOAD_DIR, img.relative_path)
        try:
            with open(path, "rb") as fh:
                blobs.append(fh.read())
        except OSError:
            return _error(500, "No se pudo leer una imagen almacenada.")

    result = pipeline.identify(blobs, organ=organ)

    # Guardar la corrida (permite resolver luego la candidata elegida sin
    # volver a llamar a Pl@ntNet).
    IdentificationRun.objects.create(
        user=user,
        batch_id=batch_id,
        organ=organ,
        status=result["status"],
        raw_json=result.get("raw") or {},
    )

    if result["status"] == "failed":
        logger.error(
            "Plant identification failed for user_id=%s batch_id=%s: %s",
            user.pk, batch_id, result.get("message", "unknown error"),
        )
        return _error(
            502,
            "No pudimos analizar la planta. Prueba con una foto JPG o PNG clara "
            "y vuelve a intentarlo.",
        )

    response = {"success": True, "status": result["status"]}
    if result["status"] == "identified":
        plant = result["plant"]
        PlantIdentification.objects.create(
            user=user,
            batch_id=batch_id,
            scientific_name=plant["scientific_name"],
            common_name=plant.get("common_name", ""),
            family=plant.get("family", ""),
            confidence=plant["confidence"],
            result_json=plant,
        )
        response["plant"] = plant
    elif result["status"] == "ambiguous":
        response["candidates"] = result["candidates"]
    else:
        response["message"] = result["message"]
        if result["status"] == "insufficient":
            response["plant_name"] = result.get("plant_name", "")
    # Las candidatas también acompañan a una identificación validada (útil para
    # el historial y para el panel web).
    if result.get("candidates") and "candidates" not in response:
        response["candidates"] = result["candidates"]
    return JsonResponse(response)


def confirm(request):
    """POST /agronomia-api/api/confirm — ficha de la candidata elegida."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para confirmar una candidata.")
    batch_id = (request.POST.get("batch_id") or "").strip()
    scientific_name = (request.POST.get("scientific_name") or "").strip()
    if not batch_id or not scientific_name:
        return _error(400, "Faltan batch_id o scientific_name.")

    run = (
        IdentificationRun.objects.filter(batch_id=batch_id, user=user)
        .order_by("-created_at")
        .first()
    )
    if run is None:
        return _error(404, "No hay una identificación previa para ese batch_id.")

    try:
        plant = pipeline.confirm(run.raw_json, scientific_name)
    except pipeline.InsufficientInfoError as exc:
        # La especie existe en la corrida, pero la IA no tiene sus cuidados.
        return JsonResponse({
            "success": True,
            "status": "insufficient",
            "message": str(exc),
            "plant_name": scientific_name,
        })
    except gemini.GeminiError as exc:
        logger.error(
            "Plant details generation failed for user_id=%s batch_id=%s: %s",
            user.pk, batch_id, exc,
        )
        return _error(
            502,
            "No pudimos preparar la ficha de esa planta. Inténtalo de nuevo o prueba con otra foto.",
        )
    if plant is None:
        return _error(404, "La especie elegida no está entre las candidatas de ese lote.")

    PlantIdentification.objects.create(
        user=user,
        batch_id=batch_id,
        scientific_name=plant["scientific_name"],
        common_name=plant.get("common_name", ""),
        family=plant.get("family", ""),
        confidence=plant["confidence"],
        result_json=plant,
    )
    return JsonResponse({"success": True, "status": "identified", "plant": plant})


def identified_plants(request):
    """GET /api/plants — identificaciones guardadas de la cuenta activa."""
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para ver tus plantas identificadas.")

    plants = PlantIdentification.objects.filter(user=user).order_by("-created_at")
    return JsonResponse({
        "success": True,
        "plants": [
            {
                "id": str(plant.pk),
                "scientific_name": plant.scientific_name,
                "common_name": plant.common_name,
                "family": plant.family,
                "confidence": plant.confidence,
                "created_at": plant.created_at.isoformat(),
                "image_url": f"agronomia-api/api/plants/{plant.pk}/cover",
            }
            for plant in plants
        ],
    })


def _plant_chat_session(plant):
    """Identificador estable y reservado para el chat de una identificación."""
    return f"plant_{plant.pk}"


def _find_owned_plant(user, plant_id):
    try:
        normalized_id = uuid.UUID(str(plant_id))
    except (ValueError, TypeError, AttributeError):
        return None
    return PlantIdentification.objects.filter(pk=normalized_id, user=user).first()


def _uploaded_file_response(relative_path, mime_type):
    """Sirve solo archivos dentro de PLANT_UPLOAD_DIR (rutas generadas por el servidor)."""
    root = os.path.realpath(settings.PLANT_UPLOAD_DIR)
    path = os.path.realpath(os.path.join(root, relative_path))
    try:
        if os.path.commonpath((root, path)) != root or not os.path.isfile(path):
            return _error(404, "No se encontró la imagen.")
        return FileResponse(open(path, "rb"), content_type=mime_type)
    except (OSError, ValueError):
        return _error(404, "No se encontró la imagen.")


def plant_cover(request, plant_id):
    """Devuelve la primera foto de identificación de una planta propia."""
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para ver esta imagen.")
    plant = _find_owned_plant(user, plant_id)
    if plant is None:
        return _error(404, "No se encontró esa planta en tu cuenta.")
    image = PlantImage.objects.filter(
        user=user, batch_id=plant.batch_id
    ).order_by("created_at").first()
    if image is None:
        return _error(404, "Esta planta no tiene foto de identificación.")
    return _uploaded_file_response(image.relative_path, image.mime_type)


def chat_progress_images(request):
    """Lista las fotos de progreso asociadas al chat de una planta propia."""
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para ver la galería.")
    plant_id = (request.GET.get("plant_id") or "").strip()
    plant = _find_owned_plant(user, plant_id)
    if plant is None:
        return _error(404, "No se encontró esa planta en tu cuenta.")
    images = ChatProgressImage.objects.filter(user=user, plant=plant).select_related("message")
    return JsonResponse({
        "success": True,
        "images": [
            {
                "id": str(image.pk),
                "image_url": f"agronomia-api/api/chat/images/{image.pk}",
                "caption": image.caption,
                "created_at": image.created_at.isoformat(),
            }
            for image in images
        ],
    })


def chat_progress_image(request, image_id):
    """Sirve una foto de progreso solo al usuario dueño de la planta."""
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para ver esta imagen.")
    image = ChatProgressImage.objects.filter(
        pk=image_id, user=user, plant__user=user
    ).first()
    if image is None:
        return _error(404, "No se encontró esa imagen en tu cuenta.")
    return _uploaded_file_response(image.relative_path, image.mime_type)


def chat_history(request):
    """GET /api/chat/history — mensajes guardados de una planta del usuario."""
    if request.method != "GET":
        return _error(405, "Método no permitido; usa GET.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para recuperar tus conversaciones.")
    plant_id = (request.GET.get("plant_id") or "").strip()
    if not plant_id:
        return _error(400, "Falta plant_id.")
    plant = _find_owned_plant(user, plant_id)
    if plant is None:
        return _error(404, "No se encontró esa planta en tu cuenta.")

    messages = list(
        ChatMessage.objects.filter(
            user=user, session_id=_plant_chat_session(plant)
        ).order_by("-created_at")[:100]
    )
    messages.reverse()
    return JsonResponse({
        "success": True,
        "messages": [
            {
                "id": str(message.pk),
                "role": message.role,
                "content": message.content,
                "created_at": message.created_at.isoformat(),
                "image_url": (
                    f"agronomia-api/api/chat/images/{message.progress_images.first().pk}"
                    if message.progress_images.exists() else None
                ),
            }
            for message in messages
        ],
    })


def chat(request):
    """POST /agronomia-api/api/chat — chat post-detección."""
    if request.method != "POST":
        return _error(405, "Método no permitido; usa POST.")
    user = user_from_request(request)
    if user is None:
        return _error(401, "Inicia sesión para conversar con el asistente.")
    message = (request.POST.get("message") or "").strip()
    image_caption = message
    uploaded_image = request.FILES.get("image")
    if not message and not uploaded_image:
        return _error(400, "Escribe un mensaje o adjunta una foto.")
    image_data = None
    image_mime = None
    if uploaded_image:
        if not (request.POST.get("plant_id") or "").strip():
            return _error(400, "Asocia la foto a una planta para guardarla en su chat.")
        try:
            image_data, image_mime, _, _ = _validate_image(uploaded_image)
        except ValueError as exc:
            return _error(422, str(exc))
    if not message:
        message = "Comparto una foto del progreso de mi planta. ¿Qué observas y qué cuidado me recomiendas?"

    plant_id = (request.POST.get("plant_id") or "").strip()
    if plant_id:
        plant = _find_owned_plant(user, plant_id)
        if plant is None:
            return _error(404, "No se encontró esa planta en tu cuenta.")
        session_id = _plant_chat_session(plant)
        context = (
            f"Nombre científico: {plant.scientific_name}\n"
            f"Nombre común: {plant.common_name}\n"
            f"Familia: {plant.family}\n"
            f"Similitud: {int(plant.confidence * 100)}%\n"
        )
    else:
        # Compatibilidad con versiones antiguas de la app y sesiones generales.
        session_id = (request.POST.get("session_id") or "default").strip()
        context_param = (request.POST.get("context") or "").strip()
        if context_param:
            context = context_param
        else:
            last = (
                PlantIdentification.objects.filter(user=user)
                .order_by("-created_at")
                .first()
            )
            if last:
                plant_data = last.result_json
                context = (
                    f"Nombre científico: {plant_data.get('scientific_name', '')}\n"
                    f"Nombre común: {plant_data.get('common_name', '')}\n"
                    f"Familia: {plant_data.get('family', '')}\n"
                    f"Similitud: {int(plant_data.get('confidence', 0) * 100)}%\n"
                )
            else:
                context = "Aún no se ha detectado ninguna planta. Sugiere usar el escáner."

    history = [
        (m.role, m.content)
        for m in ChatMessage.objects.filter(
            user=user, session_id=session_id
        ).order_by("-created_at")[:12]
    ]
    history.reverse()
    try:
        if image_data:
            reply = chat_service.reply(message, context, history, image_data, image_mime)
        else:
            reply = chat_service.reply(message, context, history)
    except Exception:  # noqa: BLE001
        logger.exception(
            "Botanical chat failed for user_id=%s plant_id=%s",
            user.pk, plant_id or "general",
        )
        return _error(
            502,
            "No pudimos responder en este momento. Inténtalo de nuevo.",
        )

    user_message = ChatMessage.objects.create(
        user=user, session_id=session_id, role="user", content=message
    )
    if image_data and plant_id:
        ext = "png" if image_mime == "image/png" else "jpg"
        relative_path = os.path.join("progress", str(plant.pk), f"{uuid.uuid4()}.{ext}")
        absolute_path = os.path.join(settings.PLANT_UPLOAD_DIR, relative_path)
        os.makedirs(os.path.dirname(absolute_path), exist_ok=True)
        with open(absolute_path, "wb") as fh:
            fh.write(image_data)
        ChatProgressImage.objects.create(
            user=user,
            plant=plant,
            message=user_message,
            relative_path=relative_path,
            mime_type=image_mime,
            file_size=len(image_data),
            caption=image_caption,
        )
    ChatMessage.objects.create(
        user=user, session_id=session_id, role="assistant", content=reply
    )
    return JsonResponse({"success": True, "reply": reply})
